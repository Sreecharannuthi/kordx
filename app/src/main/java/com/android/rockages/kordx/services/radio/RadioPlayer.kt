package com.android.rockages.kordx.services.radio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger
import java.util.Timer

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
 private var playbackPositionUpdater: Timer? = null

 private val listener = object : Player.Listener {
 override fun onPlaybackStateChanged(playbackState: Int) {
 when (playbackState) {
 Player.STATE_READY -> {
 state = State.Prepared
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
 val audioSessionId get() = exoPlayer.audioSessionId
 val isPlaying get() = exoPlayer.isPlaying

 val playbackPosition
 get() = try {
 PlaybackPosition(
 played = exoPlayer.currentPosition,
 total = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
 )
 } catch (_: IllegalStateException) {
 null
 }

 fun prepare() {
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

 fun stop() = destroy()

 fun destroy() {
 state = State.Destroyed
 destroyDurationTimer()
 exoPlayer.removeListener(listener)
 exoPlayer.stop()
 }

 fun start() {
 exoPlayer.playWhenReady = true
 createDurationTimer()
 if (!hasPlayedOnce) {
 hasPlayedOnce = true
 changeSpeed(speed)
 changePitch(pitch)
 }
 }

 fun pause() {
 exoPlayer.playWhenReady = false
 destroyDurationTimer()
 }

 fun seek(to: Int) {
 exoPlayer.seekTo(to.toLong())
 emitPlaybackPosition()
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
 exoPlayer.volume = to
 }

 fun changeSpeed(to: Float) {
 if (!hasPlayedOnce) {
 speed = to
 return
 }
 val wasPlaying = exoPlayer.isPlaying
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

 fun changePitch(to: Float) {
 if (!hasPlayedOnce) {
 pitch = to
 return
 }
 val wasPlaying = exoPlayer.isPlaying
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

 private fun createDurationTimer() {
 playbackPositionUpdater?.cancel()
 playbackPositionUpdater = kotlin.concurrent.timer(period = 100L) {
 emitPlaybackPosition()
 }
 }

 private fun emitPlaybackPosition() {
 playbackPosition?.let {
 onPlaybackPosition?.invoke(it)
 }
 }

 private fun destroyDurationTimer() {
 playbackPositionUpdater?.cancel()
 playbackPositionUpdater = null
 }

 companion object {
 const val MIN_VOLUME = 0f
 const val MAX_VOLUME = 1f
 const val DUCK_VOLUME = 0.2f
 const val DEFAULT_SPEED = 1f
 const val DEFAULT_PITCH = 1f
 }
}
