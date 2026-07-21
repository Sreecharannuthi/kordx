package com.android.rockages.kordx.services.radio

import android.net.Uri
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
class RadioPlayer(val kordx: KordX, val id: String, val uri: Uri, private val exoPlayer: ExoPlayer) {
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
 _playbackPosition = PlaybackPosition(
 played = _playbackPosition.played,
 total = if (dur > 0) dur else 0L,
 )
 createDurationTimer()
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

 fun prepare() {
 runOnMain {
 when (state) {
 State.Unprepared -> {
 exoPlayer.addListener(listener)
 exoPlayer.setMediaItem(MediaItem.fromUri(uri))
 exoPlayer.prepare()
 exoPlayer.playWhenReady = false
 state = State.Preparing
 }
 State.Prepared -> onPrepared?.invoke()
 else -> {}
 }
 }
 }

 fun stop() = destroy()

 fun destroy() {
 runOnMain {
 state = State.Destroyed
 destroyDurationTimer()
 exoPlayer.removeListener(listener)
 exoPlayer.stop()
 }
 }

 fun start() {
 runOnMain {
 exoPlayer.playWhenReady = true
 createDurationTimer()
 if (!hasPlayedOnce) {
 hasPlayedOnce = true
 applySpeed(speed)
 applyPitch(pitch)
 }
 _isPlaying = true
 }
 }

 fun pause() {
 runOnMain {
 exoPlayer.playWhenReady = false
 destroyDurationTimer()
 _isPlaying = false
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
 fader?.stop()
 when {
 to == volume -> onFinish(true)
 forceFade || fadePlayback -> {
 val duration = (kordx.settings.fadePlaybackDuration.value * 1000).toInt()
 fader = RadioEffects.Fader(
 RadioEffects.Fader.Options(volume, to, duration),
 onUpdate = {
 changeVolumeInstant(it)
 },
 onFinish = {
 onFinish(it)
 fader = null
 }
 )
 fader?.start()
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
 val wasPlaying = _isPlaying
 try {
 exoPlayer.setPlaybackSpeed(to)
 speed = to
 } catch (err: Exception) {
 Logger.error("RadioPlayer", "changing speed failed", err)
 }
 if (!wasPlaying) {
 exoPlayer.playWhenReady = false
 }
 }

 private fun applyPitch(to: Float) {
 val wasPlaying = _isPlaying
 try {
 exoPlayer.setPlaybackSpeed(to)
 pitch = to
 } catch (err: Exception) {
 Logger.error("RadioPlayer", "changing pitch failed", err)
 }
 if (!wasPlaying) {
 exoPlayer.playWhenReady = false
 }
 }

 private fun createDurationTimer() {
 if (isDurationTimerRunning) return
 isDurationTimerRunning = true
 handler.post(::tickDurationTimer)
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
 handler.postDelayed(::tickDurationTimer, 100L)
 }
 }

 private fun destroyDurationTimer() {
 isDurationTimerRunning = false
 handler.removeCallbacksAndMessages(null)
 }

 companion object {
 const val MIN_VOLUME = 0f
 const val MAX_VOLUME = 1f
 const val DUCK_VOLUME = 0.2f
 const val DEFAULT_SPEED = 1f
 const val DEFAULT_PITCH = 1f
 }
}
