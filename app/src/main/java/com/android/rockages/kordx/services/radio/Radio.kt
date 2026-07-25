package com.android.rockages.kordx.services.radio

import android.os.PowerManager
import android.os.PowerManager.WakeLock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import com.android.rockages.kordx.core.utils.Eventer
import com.android.rockages.kordx.core.utils.Logger
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Date
import java.util.Timer

@Suppress("UnsafeOptInUsageError")
class Radio(private val kordx: KordX) : KordX.Hooks, RadioAdapterTarget {
    sealed class Events {
        sealed class Player : Events() {
            object Staged : Player()
            object Started : Player()
            object Stopped : Player()
            object Paused : Player()
            object Resumed : Player()
            object Seeked : Player()
            object Ended : Player()
        }

        sealed class Queue : Events() {
            object Modified : Queue()
            object IndexChanged : Queue()
            object Cleared : Queue()
        }

        sealed class QueueOption : Events() {
            object LoopModeChanged : QueueOption()
            object ShuffleModeChanged : QueueOption()
            object SleepTimerChanged : QueueOption()
            object SpeedChanged : QueueOption()
            object PitchChanged : QueueOption()
            object PauseOnCurrentSongEndChanged : QueueOption()
        }
    }

    data class SleepTimer(
        val duration: Long,
        val endsAt: Long,
        val timer: Timer,
        var quitOnEnd: Boolean,
    )

    val onUpdate = Eventer<Events>()

    /**
     * — adapter-facing subscribe helper. Returns an
     * unsubscribe function that the [RadioForwardingPlayer] stores
     * in [RadioForwardingPlayer.release]. Equivalent to
     * `onUpdate.subscribe(subscriber)`; exists as a method (not
     * exposing the [Eventer] directly) so the
     * [RadioAdapterTarget] interface stays minimal.
     */
    override fun subscribeToEvents(subscriber: (Events) -> Unit): EventUnsubscribeFn =
        onUpdate.subscribe(subscriber)


    // The override uses the concrete [RadioQueue] / [RadioShorty]; types (which implement [RadioQueueAdapterTarget] /; [RadioShortyAdapterTarget]) so existing call sites that; depend on the concrete types — `radio.queue.add(...)`,; `radio.shorty.previous()` — keep compiling. Kotlin permits; covariant override of `val` getters.
    override val queue: RadioQueue = RadioQueue(kordx)
    override val shorty: RadioShorty = RadioShorty(kordx)
    val session = RadioSession(kordx)
    var observatory = RadioObservatory(kordx)

    lateinit var exoPlayer: ExoPlayer
    private val focus = RadioFocus(kordx)
    private val nativeReceiver = RadioNativeReceiver(kordx)
    private var player: RadioPlayer? = null

    override val hasPlayer get() = player?.usable == true
    override val isPlaying get() = player?.isPlaying == true
    override val currentPlaybackPosition get() = player?.playbackPosition
    override val currentSpeed get() = player?.speed ?: RadioPlayer.DEFAULT_SPEED
    override val currentPitch get() = player?.pitch ?: RadioPlayer.DEFAULT_PITCH
    override val audioSessionId get() = player?.audioSessionId
    val onPlaybackPositionUpdate = Eventer<RadioPlayer.PlaybackPosition>()

    var persistedSpeed = RadioPlayer.DEFAULT_SPEED
    var persistedPitch = RadioPlayer.DEFAULT_PITCH
    var sleepTimer: SleepTimer? = null
    var pauseOnCurrentSongEnd = false
    private var hasAutoResumed = false
    private val restoreMutex = Mutex()
    private var isApplyingQueueChange = false

    private val exoPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = exoPlayer.currentMediaItemIndex
            if (index in 0 until queue.currentQueue.size) {
                queue.currentSongIndex = index
            }
            // Only the index changed here — the queue contents did
            // not. Dispatching `Queue.Modified` would make
            // `watchQueueUpdates` re-sync the ExoPlayer playlist,
            // whose `setMediaItems` fires this very listener again:
            // an infinite main-thread loop (startup ANR).
            onUpdate.dispatch(Events.Queue.IndexChanged)
            if (pauseOnCurrentSongEnd && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                setPauseOnCurrentSongEnd(false)
                pauseInstant()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                stop(ended = true)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Logger.warn(
                "Radio",
                "ExoPlayer error at index ${queue.currentSongIndex}",
                error,
            )
            val index = queue.currentSongIndex
            queue.removeAtSilently(index)
            if (queue.isEmpty()) {
                queue.currentSongIndex = -1
                stop(ended = false)
                return
            }
            play(PlayOptions(index = queue.currentSongIndex.coerceAtLeast(0), autostart = true))
        }
    }

    private val wakeLock: WakeLock by lazy {
        val powerManager = kordx.applicationContext.getSystemService(PowerManager::class.java)
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KordX:RadioPlayback")
            .apply { setReferenceCounted(false) }
    }

    private fun acquireWakeLock() {
        try {
            wakeLock.takeIf { !it.isHeld }?.acquire(10 * 60 * 1000L)
        } catch (err: Exception) {
            Logger.warn("Radio", "failed to acquire wake lock", err)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) wakeLock.release()
        } catch (err: Exception) {
            Logger.warn("Radio", "failed to release wake lock", err)
        }
    }

    init {
        nativeReceiver.start()
        onUpdate.subscribe(this::watchQueueUpdates)
    }

    fun ready() {

        // Issue #773 / #707: restore the persisted; loop mode and shuffle mode on app start. The; code held these in memory only, so the user's "Repeat; all" / "Shuffle on" choice was lost on every app restart.; The settings are read here (before `attachGrooveListener`; so the order is deterministic), then the queue's; `setLoopMode` / `setShuffleMode` are called with; `persist = false` semantics by way of the `if (existing !=; value) persist` guard inside those methods (a noop write; doesn't repersist the same value).
        exoPlayer = ExoPlayer.Builder(kordx.applicationContext)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 5_000,
                        /* maxBufferMs = */ 15_000,
                        /* bufferForPlaybackMs = */ 1_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 2_000,
                    )
                    .build()
            )
            .build()
        exoPlayer.addListener(exoPlayerListener)
        player = RadioPlayer(kordx, exoPlayer)
        queue.setLoopMode(kordx.settings.lastLoopMode.value)
        queue.setShuffleMode(kordx.settings.lastShuffleMode.value)
        attachGrooveListener()
        session.start()
        observatory.start()
    }

    fun destroy() {
        stop(ended = false)
        exoPlayer.removeListener(exoPlayerListener)
        exoPlayer.release()
        releaseWakeLock()
        observatory.destroy()
        session.destroy()
        nativeReceiver.destroy()
    }

    data class PlayOptions(
        val index: Int = 0,
        val autostart: Boolean = true,
        val startPosition: Long? = null,
    )

    fun play(options: PlayOptions) {
        val targetId = queue.getSongIdAt(options.index)
        val targetSong = targetId?.let { kordx.groove.song.get(it) }
        if (targetSong == null) {
            // Recursion guard: when the requested index does not
            // resolve to a song and the caller is not autoplaying
            // (e.g. "set up the queue, the user will press play"),
            // early-return. The autostart == true path drops the
            // stale id and reenters play() at the next valid index.
            if (!options.autostart) {
                return
            }
            queue.removeAtSilently(options.index)
            if (queue.isEmpty()) {
                queue.currentSongIndex = -1
                return
            }
            return play(options.copy(index = options.index.coerceAtMost(queue.currentQueue.size - 1)))
        }

        val startIndex = purgeStaleSongsAndFindIndex(targetId)
        playAtIndex(
            startIndex = startIndex,
            startPositionMs = options.startPosition,
            autostart = options.autostart,
        )
    }

    private fun purgeStaleSongsAndFindIndex(targetId: String): Int {
        isApplyingQueueChange = true
        val staleIndices = queue.currentQueue.mapIndexedNotNull { i, songId ->
            if (kordx.groove.song.get(songId) == null) i else null
        }
        try {
            staleIndices.sortedDescending().forEach { queue.removeAtSilently(it) }
        } finally {
            isApplyingQueueChange = false
        }
        return queue.currentQueue.indexOf(targetId).coerceAtLeast(0)
    }

    private fun playAtIndex(
        startIndex: Int,
        startPositionMs: Long?,
        autostart: Boolean,
    ) {
        queue.currentSongIndex = startIndex
        updateExoPlayerRepeatMode()

        val mediaItems = queue.currentQueue.mapNotNull { songId ->
            kordx.groove.song.get(songId)?.let { MediaItem.fromUri(it.uri) }
        }
        if (mediaItems.isEmpty()) {
            queue.currentSongIndex = -1
            return
        }

        val currentPlayer = player ?: RadioPlayer(kordx, exoPlayer).also { player = it }
        attachPlayListeners(currentPlayer, autostart)
        currentPlayer.preparePlaylist(
            mediaItems = mediaItems,
            startIndex = startIndex,
            startPositionMs = startPositionMs ?: 0L,
        )
        onUpdate.dispatch(Events.Player.Staged)
    }

    private fun attachPlayListeners(currentPlayer: RadioPlayer, autostart: Boolean) {
        currentPlayer.setOnPreparedListener {
            setSpeed(persistedSpeed, true)
            setPitch(persistedPitch, true)
            if (autostart) {
                start()
            }
        }
        currentPlayer.setOnPlaybackPositionListener {
            onPlaybackPositionUpdate.dispatch(it)
        }
        currentPlayer.setOnFinishListener {
            stop(ended = true)
        }
        currentPlayer.setOnErrorListener { what, extra ->
            Logger.warn(
                "Radio",
                "skipping song ${queue.currentSongId} (${queue.currentSongIndex}) due to $what + $extra"
            )
            when {
                // Non-critical playback-parameter failure.
                what == 1 && extra == -22 -> stop(ended = true)
                else -> {
                    queue.remove(queue.currentSongIndex)
                    if (queue.isEmpty()) {
                        queue.currentSongIndex = -1
                        stop(ended = false)
                    } else {
                        play(PlayOptions(index = queue.currentSongIndex.coerceAtLeast(0), autostart = true))
                    }
                }
            }
        }
    }

    fun resume() = start()

    private fun start() {
        player?.let {
            val hasFocus = focus.requestFocus()
            if (kordx.settings.requireAudioFocus.value && !hasFocus) {
                Logger.warn(
                    "Radio",
                    "audio focus request denied; playback not started (requireAudioFocus=true)"
                )
                return
            }
            acquireWakeLock()
            if (it.fadePlayback) {
                it.changeVolumeInstant(RadioPlayer.MIN_VOLUME)
            } else {
                // Defensive: the shared ExoPlayer instance could have been
                // left muted by a previous player's fader (e.g. pause()
                // fade-out interrupted by process death). Ensure we start
                // from full volume when no fade-in is requested.
                it.changeVolumeInstant(RadioPlayer.MAX_VOLUME)
            }
            it.changeVolume(RadioPlayer.MAX_VOLUME) {}
            it.start()
            onUpdate.dispatch(
                when {
                    !it.hasPlayedOnce -> Events.Player.Started
                    else -> Events.Player.Resumed
                }
            )
        }
    }

    fun pause() = pause {}

    private fun pause(forceFade: Boolean = false, onFinish: () -> Unit) {
        player?.let {
            if (!it.isPlaying) {
                return@let
            }
            // A pause that reaches here is user/app initiated (the
            // focus-loss path re-arms the flag in RadioFocus AFTER
            // calling pause), so any pending focus-gain resume is
            // cancelled — a manual pause must not auto-resume later.
            focus.cancelPendingFocusResume()
            it.changeVolume(
                to = RadioPlayer.MIN_VOLUME,
                forceFade = forceFade,
            ) { _ ->
                it.pause()
                releaseWakeLock()
                focus.abandonFocus()
                onFinish()
                onUpdate.dispatch(Events.Player.Paused)
            }
        }
    }

    fun pauseInstant() {
        player?.let {
            focus.cancelPendingFocusResume()
            it.pause()
            onUpdate.dispatch(Events.Player.Paused)
        }
    }

    override fun stop() {
        stop(ended = true)
    }

    fun stop(ended: Boolean) {
        player?.let {
            it.pause()
            it.destroy()
            player = null
        }
        queue.reset()
        clearSleepTimer()
        persistedSpeed = RadioPlayer.DEFAULT_SPEED
        persistedPitch = RadioPlayer.DEFAULT_PITCH
        // Abandon audio focus when playback fully stops.
        // pause() already abandons; stop() must too so the
        // system and other apps can reclaim the audio output.
        focus.cancelPendingFocusResume()
        releaseWakeLock()
        focus.abandonFocus()
        if (ended) onUpdate.dispatch(Events.Player.Ended)
    }

    /**
     * Public API for "clear the queue but keep playing the current song."
     * Compare to [stop] (which stops both the player and clears the
     * queue). The workaround was to call
     * `queue.reset()` directly from the call site, but the player
     * state was not modified so a current song kept playing — the
     * only thing the user wanted was the queue cleared. This method
     * makes that intent explicit at the call site.
     */
    fun clearQueue() {
        queue.clear()
    }

    fun jumpTo(index: Int) = play(PlayOptions(index = index))

    fun jumpToPrevious() {
        if (hasPlayer && queue.hasSongAt(queue.currentSongIndex - 1)) {
            player?.previous()
        } else {
            jumpTo(queue.currentSongIndex - 1)
        }
    }

    fun jumpToNext() {
        if (hasPlayer && queue.hasSongAt(queue.currentSongIndex + 1)) {
            player?.next()
        } else {
            jumpTo(queue.currentSongIndex + 1)
        }
    }

    fun canJumpToPrevious() = queue.hasSongAt(queue.currentSongIndex - 1)
    fun canJumpToNext() = queue.hasSongAt(queue.currentSongIndex + 1)

    override fun seek(positionMs: Long) {
        player?.let {
            it.seek(positionMs.toInt())
            onUpdate.dispatch(Events.Player.Seeked)
        }
    }

    fun duck() {
        player?.let {
            it.changeVolume(RadioPlayer.DUCK_VOLUME) {}
        }
    }

    fun restoreVolume() {
        player?.let {
            it.changeVolume(RadioPlayer.MAX_VOLUME) {}
        }
    }

    override fun setSpeed(speed: Float, persist: Boolean) {
        player?.let {
            it.changeSpeed(speed)
            if (persist) {
                persistedSpeed = speed
            }
            onUpdate.dispatch(Events.QueueOption.SpeedChanged)
        }
    }

    override fun setPitch(pitch: Float, persist: Boolean) {
        player?.let {
            it.changePitch(pitch)
            if (persist) {
                persistedPitch = pitch
            }
            onUpdate.dispatch(Events.QueueOption.PitchChanged)
        }
    }

    fun setSleepTimer(
        duration: Long,
        quitOnEnd: Boolean,
    ) {
        val endsAt = System.currentTimeMillis() + duration
        val timer = Timer()
        timer.schedule(
            kotlin.concurrent.timerTask {
                val shouldQuit = sleepTimer?.quitOnEnd ?: quitOnEnd
                clearSleepTimer()
                pause(forceFade = true) {
                    if (shouldQuit) {
                        kordx.closeApp?.invoke()
                    }
                }
            },
            Date.from(Instant.ofEpochMilli(endsAt)),
        )
        clearSleepTimer()
        sleepTimer = SleepTimer(
            duration = duration,
            endsAt = endsAt,
            timer = timer,
            quitOnEnd = quitOnEnd,
        )
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    fun clearSleepTimer() {
        sleepTimer?.timer?.cancel()
        sleepTimer = null
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    @JvmName("setPauseOnCurrentSongEndTo")
    fun setPauseOnCurrentSongEnd(value: Boolean) {
        pauseOnCurrentSongEnd = value
        onUpdate.dispatch(Events.QueueOption.PauseOnCurrentSongEndChanged)
    }

    private fun updateExoPlayerRepeatMode() {
        val mode = when (queue.currentLoopMode) {
            RadioQueue.LoopMode.Song -> Player.REPEAT_MODE_ONE
            RadioQueue.LoopMode.Queue -> Player.REPEAT_MODE_ALL
            RadioQueue.LoopMode.None -> Player.REPEAT_MODE_OFF
        }
        player?.setRepeatMode(mode)
    }

    private fun syncPlayerPlaylist() {
        val currentPlayer = player ?: return
        val mediaItems = queue.currentQueue.mapNotNull { songId ->
            kordx.groove.song.get(songId)?.let { MediaItem.fromUri(it.uri) }
        }
        if (mediaItems.isEmpty()) {
            stop(ended = false)
            return
        }
        val index = queue.currentSongIndex.coerceIn(0, mediaItems.size - 1)
        val wasPlaying = currentPlayer.isPlaying
        val position = currentPlayer.playbackPosition.played.coerceAtLeast(0L)
        // Guard against re-entrancy: `syncPlaylist` calls
        // `setMediaItems`, which fires `onMediaItemTransition`.
        isApplyingQueueChange = true
        try {
            currentPlayer.syncPlaylist(
                mediaItems = mediaItems,
                startIndex = index,
                startPositionMs = position,
                playWhenReady = wasPlaying,
            )
        } finally {
            isApplyingQueueChange = false
        }
        updateExoPlayerRepeatMode()
    }

    private fun attachGrooveListener() {
        kordx.groove.coroutineScope.launch {
            kordx.groove.readyDeferred.await()
            restoreMutex.withLock { restorePreviousQueue() }
        }
    }

    private fun restorePreviousQueue(): Long {
        if (!queue.isEmpty()) {
            return -1L
        }
        var restoredPosition = -1L
        kordx.settings.previousSongQueue.value?.let { previous ->
            var currentSongIndex = previous.currentSongIndex
            var playedDuration = previous.playedDuration
            val originalQueue = mutableListOf<String>()
            val currentQueue = mutableListOf<String>()
            previous.originalQueue.forEach { songId ->
                if (kordx.groove.song.get(songId) != null) {
                    originalQueue.add(songId)
                }
            }
            previous.currentQueue.forEachIndexed { i, songId ->
                if (kordx.groove.song.get(songId) != null) {
                    currentQueue.add(songId)
                } else {
                    if (i < currentSongIndex) currentSongIndex--
                }
            }
            if (originalQueue.isEmpty() || hasPlayer) {
                return@let
            }
            if (currentSongIndex >= originalQueue.size) {
                currentSongIndex = 0
                playedDuration = 0
            }
            queue.restore(
                RadioQueue.Serialized(
                    currentSongIndex = currentSongIndex,
                    playedDuration = playedDuration,
                    originalQueue = originalQueue,
                    currentQueue = currentQueue,
                    shuffled = previous.shuffled,
                )
            )
            restoredPosition = playedDuration
        }
        return restoredPosition
    }

    private fun autoResumeIfEnabled() {
        if (!kordx.settings.autoResumeOnLaunch.value) {
            return
        }
        kordx.groove.coroutineScope.launch {
            kordx.groove.readyDeferred.await()
            restoreMutex.withLock {
                restorePreviousQueue()
                if (queue.isEmpty()) {
                    return@withLock
                }
                // Whichever coroutine wins the restore race, RadioQueue.restore()
                // stages a paused RadioPlayer at the persisted position via
                // afterAdd(autostart = false, startPosition = playedDuration).
                // Starting that staged player is all that is required here. The
                // previous implementation called play() again, which destroyed the
                // already-staged player and, when this coroutine lost the race
                // (restorePreviousQueue() returned -1), restarted the song from 0
                // instead of resuming at the saved position.
                if (!isPlaying) {
                    resume()
                }
            }
        }
    }

    internal fun watchQueueUpdates(event: Events) {
        when (event) {
            is Events.Queue.IndexChanged -> Unit // driven by ExoPlayer media-item transition
            is Events.Queue.Modified -> if (!isApplyingQueueChange) syncPlayerPlaylist()
            is Events.QueueOption.LoopModeChanged -> updateExoPlayerRepeatMode()
            else -> Unit
        }
    }

    override fun onKordXReady() {
        ready()
    }

    override fun onKordXActivityReady() {
        if (!hasAutoResumed) {
            hasAutoResumed = true
            autoResumeIfEnabled()
        }
    }

    override fun onKordXDestroy() {
        saveCurrentQueue()
        destroy()
    }

    override fun onKordXActivityPause() {
        saveCurrentQueue()
    }

    override fun onKordXActivityDestroy() {
        saveCurrentQueue()
    }

    private fun saveCurrentQueue() {
        if (queue.isEmpty()) {
            return
        }
        kordx.settings.previousSongQueue.setValue(
            RadioQueue.Serialized.create(
                queue = queue,
                playbackPosition = currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
            )
        )
    }
}
