package com.android.rockages.kordx.services.radio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger

typealias RadioPlayerOnPreparedListener = () -> Unit
typealias RadioPlayerOnPlaybackPositionListener = (RadioPlayer.PlaybackPosition) -> Unit
typealias RadioPlayerOnFinishListener = () -> Unit
typealias RadioPlayerOnErrorListener = (Int, Int) -> Unit

@Suppress("UnsafeOptInUsageError")
class RadioPlayer(val kordx: KordX, private val exoPlayer: ExoPlayer) {
    data class PlaybackPosition(val played: Long, val total: Long) {
        val ratio: Float
            get() = (played.toFloat() / total).takeIf { it.isFinite() } ?: 0f

        companion object {
            val zero = PlaybackPosition(0L, 0L)
        }
    }

    enum class State {
        Unprepared,
        Preparing,
        Prepared,
        Finished,
        Destroyed,
    }

    private var onPrepared: RadioPlayerOnPreparedListener? = null
    private var onPlaybackPosition: RadioPlayerOnPlaybackPositionListener? = null
    private var onFinish: RadioPlayerOnFinishListener? = null
    private var onError: RadioPlayerOnErrorListener? = null
    private var fader: RadioEffects.Fader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDurationTimerRunning = false
    // Dedicated Runnable token for the duration timer so
    // destroyDurationTimer() only removes timer callbacks
    // rather than scorching every pending runOnMain post.
    private val durationTick = Runnable { tickDurationTimer() }

    // Cached from Player.Listener (main thread) so background
    // threads (RadioSession.updateAsync, duration timer) never
    // touch ExoPlayer directly. ExoPlayer enforces main-thread
    // access and throws IllegalStateException otherwise.
    @Volatile private var _isPlaying = false
    @Volatile private var _playbackPosition = PlaybackPosition.zero

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    state = State.Prepared
                    val dur = try { exoPlayer.duration } catch (_: IllegalStateException) { C.TIME_UNSET }
                    // Seed the position from the player itself: with
                    // setMediaItems(..., startIndex, startPositionMs) the initial
                    // position is the restore point, so the UI shows the
                    // resumed position even before playback starts (the
                    // staged autostart=false restore path never ticks the
                    // duration timer until play).
                    val pos = try { exoPlayer.currentPosition } catch (_: IllegalStateException) { 0L }
                    _playbackPosition = PlaybackPosition(
                        played = pos.coerceAtLeast(0L),
                        total = if (dur > 0) dur else 0L,
                    )
                    createDurationTimer()
                    onPlaybackPosition?.invoke(_playbackPosition)
                    onPrepared?.invoke()
                }
                Player.STATE_ENDED -> {
                    state = State.Finished
                    destroyDurationTimer()
                    onFinish?.invoke()
                }
                else -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            state = State.Destroyed
            destroyDurationTimer()
            onError?.invoke(error.errorCode, 0)
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    var state = State.Unprepared
        private set
    var hasPlayedOnce = false
        private set
    var volume = MAX_VOLUME
        private set
    var speed = DEFAULT_SPEED
        private set
    var pitch = DEFAULT_PITCH
        private set

    val usable get() = state == State.Prepared
    val fadePlayback get() = kordx.settings.fadePlayback.value

    // audioSessionId: safe to read from any thread once
    // the player is in STATE_READY (session ID is cached
    // internally by ExoPlayer after prepare).
    val audioSessionId get() = try { exoPlayer.audioSessionId } catch (_: IllegalStateException) { 0 }
    val isPlaying get() = _isPlaying
    val playbackPosition get() = _playbackPosition

    /**
     * Runs [block] on the main thread. If already on main,
     * executes immediately. Otherwise posts to the main handler.
     * All ExoPlayer access MUST go through this wrapper.
     */
    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post { block() }
        }
    }

    /**
     * Prepares the shared [ExoPlayer] with a playlist starting at
     * [startIndex] and [startPositionMs]. The start position is
     * applied at playlist construction time (Media3-idiomatic),
     * so no post-prepare seek is needed.
     */
    fun preparePlaylist(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        runOnMain {
            exoPlayer.setMediaItems(
                mediaItems,
                startIndex,
                startPositionMs.coerceAtLeast(0L),
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
            state = State.Preparing
        }
    }

    /**
     * Replaces the current playlist while preserving playback state. Used when
     * the user edits the queue (add / remove / shuffle) while the player is
     * already prepared.
     */
    fun syncPlaylist(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        runOnMain {
            exoPlayer.setMediaItems(
                mediaItems,
                startIndex,
                startPositionMs.coerceAtLeast(0L),
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady
            state = State.Preparing
        }
    }

    fun setRepeatMode(mode: Int) {
        runOnMain { exoPlayer.repeatMode = mode }
    }

    fun stop() = destroy()

    fun destroy() {
        runOnMain {
            state = State.Destroyed
            destroyDurationTimer()
            // Stop any running fader before releasing — the fader
            // runs on a background Timer thread and keeps writing
            // exoPlayer.volume; if not stopped here it can mute
            // the next playback that reuses the shared ExoPlayer.
            fader?.stop()
            fader = null
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
        }
    }

    fun start() {
        // Write the volatile flag synchronously on the calling thread:
        // Radio.start() dispatches Events.Player.Started/Resumed
        // immediately after this returns, and RadioObservatory reads
        // `isPlaying` at dispatch time. When start() is invoked off the
        // main thread (auto-resume-on-launch coroutine), deferring the
        // flag write to the posted main block would leave the UI
        // showing the play button while audio is already playing.
        _isPlaying = true
        runOnMain {
            createDurationTimer()
            if (!hasPlayedOnce) {
                hasPlayedOnce = true
                applySpeed(speed)
                applyPitch(pitch)
            }
            exoPlayer.playWhenReady = true
            _isPlaying = true
        }
    }

    fun pause() {
        // See start(): write the flag synchronously so the Paused event
        // dispatched right after observes the correct state even when
        // pause() runs off the main thread (fade-out completion callback).
        _isPlaying = false
        runOnMain {
            exoPlayer.playWhenReady = false
            destroyDurationTimer()
            _isPlaying = false
        }
    }

    fun next() {
        runOnMain {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
            }
        }
    }

    fun previous() {
        runOnMain {
            if (exoPlayer.hasPreviousMediaItem()) {
                exoPlayer.seekToPreviousMediaItem()
            }
        }
    }

    fun seek(to: Int) {
        runOnMain {
            exoPlayer.seekTo(to.toLong())
            try {
                val played = exoPlayer.currentPosition
                val total = exoPlayer.duration.takeIf { it > 0 } ?: _playbackPosition.total
                _playbackPosition = PlaybackPosition(played, total)
                onPlaybackPosition?.invoke(_playbackPosition)
            } catch (_: IllegalStateException) {}
        }
    }

    fun changeVolume(
        to: Float,
        forceFade: Boolean = false,
        onFinish: (Boolean) -> Unit,
    ) {
        val previousFader = fader
        previousFader?.stop()
        when {
            to == volume -> onFinish(true)
            forceFade || fadePlayback -> {
                val duration = (kordx.settings.fadePlaybackDuration.value * 1000).toInt()
                var newFader: RadioEffects.Fader? = null
                newFader = RadioEffects.Fader(
                    RadioEffects.Fader.Options(volume, to, duration),
                    onUpdate = {
                        changeVolumeInstant(it)
                    },
                    onFinish = {
                        if (fader === newFader) {
                            fader = null
                        }
                        onFinish(it)
                    }
                )
                fader = newFader
                newFader.start()
            }

            else -> {
                changeVolumeInstant(to)
                onFinish(true)
            }
        }
    }

    fun changeVolumeInstant(to: Float) {
        volume = to
        runOnMain { exoPlayer.volume = to }
    }

    fun changeSpeed(to: Float) {
        if (!hasPlayedOnce) {
            speed = to
            return
        }
        runOnMain {
            applySpeed(to)
        }
    }

    fun changePitch(to: Float) {
        if (!hasPlayedOnce) {
            pitch = to
            return
        }
        runOnMain {
            applyPitch(to)
        }
    }

    fun setOnPreparedListener(listener: RadioPlayerOnPreparedListener?) {
        onPrepared = listener
    }

    fun setOnPlaybackPositionListener(listener: RadioPlayerOnPlaybackPositionListener?) {
        onPlaybackPosition = listener
    }

    fun setOnFinishListener(listener: RadioPlayerOnFinishListener?) {
        onFinish = listener
    }

    fun setOnErrorListener(listener: RadioPlayerOnErrorListener?) {
        onError = listener
    }

    // ---- Internal helpers (always called on main thread) ----

    private fun applySpeed(to: Float) {
        try {
            exoPlayer.setPlaybackSpeed(to)
            speed = to
        } catch (err: Exception) {
            Logger.error("RadioPlayer", "changing speed failed", err)
        }
    }

    private fun applyPitch(to: Float) {
        try {
            exoPlayer.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(speed, to)
            )
            pitch = to
        } catch (err: Exception) {
            Logger.error("RadioPlayer", "changing pitch failed", err)
        }
    }

    private fun createDurationTimer() {
        if (isDurationTimerRunning) return
        isDurationTimerRunning = true
        handler.post(durationTick)
    }

    private fun tickDurationTimer() {
        if (!isDurationTimerRunning) return
        try {
            val played = exoPlayer.currentPosition
            val total = exoPlayer.duration.takeIf { it > 0 } ?: _playbackPosition.total
            _playbackPosition = PlaybackPosition(played, total)
            onPlaybackPosition?.invoke(_playbackPosition)
        } catch (_: IllegalStateException) {}
        if (isDurationTimerRunning) {
            handler.postDelayed(durationTick, 100L)
        }
    }

    private fun destroyDurationTimer() {
        isDurationTimerRunning = false
        // Only remove the duration timer callbacks, NOT all
        // pending handler posts. removeCallbacksAndMessages(null)
        // was scorching pending prepare/start/seek/volume
        // operations posted via runOnMain, which caused the
        // "UI says playing but no audio" symptom.
        handler.removeCallbacks(durationTick)
    }

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f
        const val DUCK_VOLUME = 0.2f
        const val DEFAULT_SPEED = 1f
        const val DEFAULT_PITCH = 1f
    }
}
