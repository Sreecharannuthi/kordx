package com.android.rockages.kordx.services.radio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import java.util.concurrent.CopyOnWriteArrayList
import androidx.media3.common.util.UnstableApi

/** AndroidX Media3 [Player] adapter for KordX's custom [Radio] engine. This is the "heart" of the [androidx.media3.session.MediaLibraryService] migration: every other  a [Player] that the new `MediaLibrarySession` can wrap.  Why this class exists KordX has a custom playback engine ([Radio], [RadioQueue], [RadioShorty], [RadioPlayer]) with a non-Media3 event surface ([Radio.Events.Player.*], [Radio.Events.Queue.*], [Radio.Events.QueueOption.*]). AndroidX Media3's `MediaLibraryService` / `MediaLibrarySession` only know about the [Player] interface and [Player.Listener] events. This adapter translates one surface to the other.  Design The class extends [BasePlayer] rather than implementing [Player] directly because [BasePlayer] provides the `final` default-implementations of the ~50 media-item / navigation methods (e.g. `setMediaItem`, `addMediaItem`, `removeMediaItem`, `clearMediaItems`, `next`, `previous`, `seekToDefaultPosition`, `seekBack`, `seekForward`, `hasPreviousMediaItem`, etc.). Those defaults delegate to a small set of abstract getters (`getCurrentTimeline()`, `getMediaItemCount()`, `getMediaItemAt(i)`, `getCurrentMediaItemIndex()`) and the single protected abstract [seekTo] (which we wire to [Radio.seek]). The remaining ~30 [Player] methods are implemented below to map cleanly to the [Radio] / [RadioShorty] / [RadioQueue] public surface.  Concurrency The adapter does not own playback threads. The [Radio] engine is driven by the `KordXMediaLibraryService`   which calls `addListener` / `removeListener` from the Media3 session's callback thread. The adapter subscribes to [Radio.onUpdate] in [subscribeToRadio] (lazy, on first listener add) and unsubscribes in [unsubscribeFromRadio] (called from [release]); events are dispatched to listeners via [playerListenerHandler] (the main [Looper] by default) so the listener sees the same threading model as a regular Media3 [Player].  Interface-driven testability The constructor takes [RadioAdapterTarget] (and the smaller [RadioQueueAdapterTarget] / [RadioShortyAdapterTarget]) rather than a concrete [Radio] / `KordX`. The concrete [Radio], [RadioQueue], and [RadioShorty] classes explicitly implement these interfaces in their class headers. Tests provide hand-rolled fakes. This keeps the adapter JVM-testable without instantiating an `Application` / `ViewModel` / `Room` database.  Event mapping Each [Radio.Events] subevent is translated to the corresponding [Player] event-flag(s) and a `Player.Events` is built (the `onEvents(player, events)` batched-listener callback is the preferred listener entry point in Media3 1.4+): | [Radio.Events] | [Player] event flags | |---------------------------------------------|---------------------------------------------------------------| | `Player.Staged` / `Started` / `Resumed` | `EVENT_PLAY_WHEN_READY_CHANGED \| EVENT_IS_PLAYING_CHANGED` | | `Player.Paused` | `EVENT_PLAY_WHEN_READY_CHANGED \| EVENT_IS_PLAYING_CHANGED` | | `Player.Stopped` | `EVENT_PLAYBACK_STATE_CHANGED \| EVENT_IS_PLAYING_CHANGED` | | `Player.Ended` | `EVENT_PLAYBACK_STATE_CHANGED` (state = `STATE_ENDED`) | | `Player.Seeked` | `EVENT_POSITION_DISCONTINUITY` | | `Queue.Modified` / `Cleared` / `IndexChanged` | `EVENT_TIMELINE_CHANGED` | | `QueueOption.ShuffleModeChanged` | `EVENT_SHUFFLE_MODE_ENABLED_CHANGED` | | `QueueOption.LoopModeChanged` | `EVENT_REPEAT_MODE_CHANGED` | See [handleRadioEvent] for the actual mapping.  Thread-safety The listener list is a [CopyOnWriteArrayList] (safe to iterate while listeners are being added/removed). The unsubscribe function is stored in [radioUnsubscribe] and called from [release]. The [Radio] engine itself is not thread-safe — all access goes through the Media3 session's main thread callback.  UnstableApi Marked `@UnstableApi` because [BasePlayer] is part of Media3's unstable surface (subject to API breakage between minor versions). The plan (26a-26m) commits to the 1.7.1 API; Media3 upgrade will be handled */
@UnstableApi
class RadioForwardingPlayer(
 private val radio: RadioAdapterTarget,
 private val songMediaItemResolver: (String) -> MediaItem?,
 private val seekBackDurationMs: Long,
 private val seekForwardDurationMs: Long,
 private val playerListenerHandler: Handler? = null,
) : BasePlayer() {


 // Resolved handler used to dispatch `Player.Listener.onEvents`; calls. Defaults to the main `Looper` for production; (where the [KordXMediaLibraryService] ( ); creates the adapter from the main thread); tests pass; a handler on a nonmain looper to avoid the; `Looper.getMainLooper` "not mocked" runtime error on the; JVM unittest classpath.
 private val handler: Handler = playerListenerHandler
 ?: Handler(Looper.getMainLooper())

 // ---- Listener bookkeeping (addListener / removeListener contract).

 private val listeners = CopyOnWriteArrayList<Player.Listener>()

 @Volatile
 private var radioUnsubscribe: EventUnsubscribeFn? = null


 // Player: addListener / removeListener (Player interface, not; provided by BasePlayer).

 override fun addListener(listener: Player.Listener) {
 if (listeners.addIfAbsent(listener) && listeners.size == 1) {
 subscribeToRadio()
 }
 }

 override fun removeListener(listener: Player.Listener) {
 if (listeners.remove(listener) && listeners.isEmpty()) {
 unsubscribeFromRadio()
 }
 }

 private fun subscribeToRadio() {
 if (radioUnsubscribe != null) return
 radioUnsubscribe = radio.subscribeToEvents(::handleRadioEvent)
 }

 private fun unsubscribeFromRadio() {
 radioUnsubscribe?.invoke()
 radioUnsubscribe = null
 }


 // Player: transport controls. Note: `play()` and `pause()`; are `final` on BasePlayer and dispatch to; `setPlayWhenReady(true)` / `setPlayWhenReady(false)`.; We implement [setPlayWhenReady] below; the `play` /; `pause` dispatch chain handles the rest.

 override fun prepare() {

 // Noop: Radio has no separate prepare phase.; RadioPlayer.prepare() is internal; the player is staged; by `Radio.play(PlayOptions)`. Auto will issue `play()`; once a queue exists.
 }


 // The Player interface declares `stop()` (no args); the; [RadioAdapterTarget] interface declares `stop()` (no args,; defaults to `ended = true` at the radio side). The noarg; override is the Media3 contract; the radio's; `stop(ended: Boolean)` is a separate method on the; concrete class, not on the interface.
 override fun stop() {
 radio.stop()
 }

 override fun release() {

 // The Player contract: after release() the player should; not emit any more events. Unsubscribe from the radio so; we stop forwarding.
 unsubscribeFromRadio()
 listeners.clear()
 }

 override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekParameters: Int, forcePosition: Boolean) {

 // The Media3 seekParameters + forcePosition arguments; don't map onto the Radio's seek (which is an exact; `MediaPlayer.seekTo(position)` under the hood). We just; forward the position.
 if (!radio.hasPlayer) {
 return
 }
 radio.seek(positionMs)
 }


 // [setPlayWhenReady] is abstract on BasePlayer. BasePlayer's; `play()` calls this with `true`; its `pause()` calls it with; `false`. We delegate to the radio's playPause() in both; cases — theadio is the source of truth for play state.
 override fun setPlayWhenReady(playWhenReady: Boolean) {

 // Mirror `isPlaying` to `playWhenReady`. We don't filter; by the current state — Radio is the source of truth.
 if (playWhenReady && !radio.isPlaying) {
 radio.shorty.playPause()
 } else if (!playWhenReady && radio.isPlaying) {
 radio.shorty.playPause()
 }
 }

 // ---- Player: state queries.


 // `isPlaying()` is `final` on BasePlayer; it returns; `getPlaybackState() == STATE_READY && getPlayWhenReady()`. So; we don't override it directly — we just implement; `getPlaybackState()` (returns STATE_READY when there's a; player) and `getPlayWhenReady()` (returns `radio.isPlaying`).

 override fun isLoading(): Boolean = false

 override fun getPlaybackState(): Int = when {
 !radio.hasPlayer -> Player.STATE_IDLE
 else -> Player.STATE_READY
 }

 override fun getPlaybackSuppressionReason(): Int =
 Player.PLAYBACK_SUPPRESSION_REASON_NONE

 override fun getPlayerError(): PlaybackException? = null

 override fun getPlayWhenReady(): Boolean = radio.isPlaying

 // ---- Player: timeline / media items.

 override fun getCurrentTimeline(): Timeline = RadioTimeline(radio, songMediaItemResolver)

 override fun getCurrentMediaItemIndex(): Int = radio.queue.currentSongIndex


 // [RadioQueue.currentSongIndex] is exposed through; [RadioQueueAdapterTarget.currentSongIndex]. The interface; declaration lives in the same file as the adapter.

 override fun getCurrentPeriodIndex(): Int = 0

 override fun getMediaMetadata(): MediaMetadata {
 val songId = radio.queue.currentSongId
 val mediaItem = songId?.let { songMediaItemResolver(it) }
 val metadata = mediaItem?.mediaMetadata
 if (metadata != null && metadata !== MediaMetadata.EMPTY) {
 return metadata
 }

 // Fallback: surface the live duration even when the; resolver returns null. AAOS uses this to render the; progress bar on the Now Playing card.
 val position = radio.currentPlaybackPosition
 val builder = MediaMetadata.Builder()
 .setIsPlayable(true)
 .setIsBrowsable(false)
 .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
 if (position != null && position.total > 0) {
 builder.setDurationMs(position.total)
 }
 return builder.build()
 }

 override fun getPlaylistMetadata(): MediaMetadata =
 MediaMetadata.Builder()
 .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
 .setIsBrowsable(false)
 .setIsPlayable(false)
 .build()

 override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {

 // Noop: the radio manages its own playlist metadata via; the queue.
 }

 override fun getCurrentTracks(): Tracks = Tracks.EMPTY

 override fun getTrackSelectionParameters() =
 androidx.media3.common.TrackSelectionParameters.DEFAULT

 override fun setTrackSelectionParameters(parameters: androidx.media3.common.TrackSelectionParameters) {

 // Noop: track selection is owned by the underlying; MediaPlayer; the adapter does not surface it.
 }

 // ---- Player: repeat / shuffle.

 override fun setRepeatMode(repeatMode: Int) {
 val loopMode = when (repeatMode) {
 Player.REPEAT_MODE_ONE -> RadioQueue.LoopMode.Song
 Player.REPEAT_MODE_ALL -> RadioQueue.LoopMode.Queue
 else -> RadioQueue.LoopMode.None
 }
 radio.queue.setLoopMode(loopMode)
 }

 override fun getRepeatMode(): Int = when (radio.queue.currentLoopMode) {
 RadioQueue.LoopMode.Song -> Player.REPEAT_MODE_ONE
 RadioQueue.LoopMode.Queue -> Player.REPEAT_MODE_ALL
 RadioQueue.LoopMode.None -> Player.REPEAT_MODE_OFF
 }

 override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
 radio.queue.setShuffleMode(shuffleModeEnabled)
 }

 override fun getShuffleModeEnabled(): Boolean = radio.queue.currentShuffleMode

 // ---- Player: position / duration.

 override fun getCurrentPosition(): Long = radio.currentPlaybackPosition?.played ?: 0L

 override fun getDuration(): Long = radio.currentPlaybackPosition?.total ?: 0L

 override fun getBufferedPosition(): Long = getCurrentPosition()

 override fun getTotalBufferedDuration(): Long {
 val total = getDuration()
 val played = getCurrentPosition()
 return (total - played).coerceAtLeast(0L)
 }

 override fun getContentPosition(): Long = getCurrentPosition()

 override fun getContentBufferedPosition(): Long = getBufferedPosition()

 // ---- Player: seek-back / seek-forward increments.

 override fun getSeekBackIncrement(): Long = seekBackDurationMs

 override fun getSeekForwardIncrement(): Long = seekForwardDurationMs

 override fun getMaxSeekToPreviousPosition(): Long = MAX_SEEK_TO_PREVIOUS_POSITION_MS

 // ---- Player: playback parameters (speed / pitch).

 override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
 val previous = getPlaybackParameters()
 if (playbackParameters.speed != previous.speed) {
 radio.setSpeed(playbackParameters.speed, persist = true)
 }
 if (playbackParameters.pitch != previous.pitch) {
 radio.setPitch(playbackParameters.pitch, persist = true)
 }
 }

 override fun getPlaybackParameters(): PlaybackParameters =
 PlaybackParameters(radio.currentSpeed, radio.currentPitch)

 // ---- Player: volume (managed internally by RadioPlayer).

 override fun setVolume(volume: Float) {

 // The Radio's fade / ducking logic is internal to; RadioPlayer. The Media3 setVolume contract is that 1.0; = full and 0.0 = muted. We do not propagate to the; player because the radio manages its own volume; envelope.
 }

 override fun getVolume(): Float = 1f

 // ---- Player: video surface (no-op — KordX is audio-only).

 override fun clearVideoSurface() {}
 override fun clearVideoSurface(surface: android.view.Surface?) {}
 override fun setVideoSurface(surface: android.view.Surface?) {}
 override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
 override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
 override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
 override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
 override fun setVideoTextureView(textureView: android.view.TextureView?) {}
 override fun clearVideoTextureView(textureView: android.view.TextureView?) {}

 override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN

 override fun getSurfaceSize(): androidx.media3.common.util.Size =
 androidx.media3.common.util.Size.UNKNOWN

 // ---- Player: cues / metadata (no-op for audio-only).

 override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO


 // Player: device volume (noop — KordX doesn't surface; system volume through the player).

 override fun getDeviceInfo() = androidx.media3.common.DeviceInfo.UNKNOWN
 override fun getDeviceVolume(): Int = 0
 override fun isDeviceMuted(): Boolean = false
 override fun setDeviceVolume(volume: Int) {}
 override fun setDeviceVolume(volume: Int, flags: Int) {}
 override fun increaseDeviceVolume() {}
 override fun increaseDeviceVolume(flags: Int) {}
 override fun decreaseDeviceVolume() {}
 override fun decreaseDeviceVolume(flags: Int) {}
 override fun setDeviceMuted(muted: Boolean) {}
 override fun setDeviceMuted(muted: Boolean, flags: Int) {}

 // ---- Player: audio attributes (KordX always uses music playback).

 override fun getAudioAttributes(): androidx.media3.common.AudioAttributes =
 androidx.media3.common.AudioAttributes.DEFAULT

 override fun setAudioAttributes(
 audioAttributes: androidx.media3.common.AudioAttributes,
 handleAudioFocus: Boolean,
 ) {
 // No-op: KordX's audio focus is managed by `RadioFocus`.
 }

 // ---- Player: ad playback (KordX doesn't play ads).

 override fun isPlayingAd(): Boolean = false
 override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET
 override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

 // ---- Player: available commands.

 override fun getAvailableCommands(): Player.Commands = AVAILABLE_COMMANDS


 // `canAdvertiseSession()` is `final` on BasePlayer and returns; `true` by default. We don't override it.


 // Player: application looper (used by Media3 session to; dispatch listener events). Returns the looper of the; handler we use to dispatch events, so all listener; callbacks land on the same thread.

 override fun getApplicationLooper(): android.os.Looper =
 handler.looper


 // Player: playlist bulk operations. These are abstract on; BasePlayer (inherited from Player) because BasePlayer's; default `setMediaItem` / `addMediaItem` / `removeMediaItem`; etc. dispatch to `setMediaItems(List, boolean)` /; `setMediaItems(List, int, long)`. We implement them as; noops on the radio (the radio manages its own queue via; `RadioShorty.playQueue` and `RadioQueue`); the Media3; session will use `Radio.onCustomCommand` (in 26c) to; translate AAOS intents into radio operations.

 override fun setMediaItems(
 mediaItems: List<MediaItem>,
 resetPosition: Boolean,
 ) {

 // Noop: the radio's queue is managed by [RadioShorty.playQueue]; and [RadioQueue.add]. AAOS does not surface; `setMediaItems` directly.
 }

 override fun setMediaItems(
 mediaItems: List<MediaItem>,
 startIndex: Int,
 startPositionMs: Long,
 ) {
 // No-op: see [setMediaItems] above.
 }

 override fun addMediaItems(
 index: Int,
 mediaItems: List<MediaItem>,
 ) {

 // Noop: the radio's queue is managed by [RadioShorty.playQueue]; and [RadioQueue.add]. AAOS does not surface; `addMediaItems` directly.
 }

 override fun moveMediaItems(
 fromIndex: Int,
 toIndex: Int,
 newIndex: Int,
 ) {
 // No-op: see [addMediaItems] above.
 }

 override fun replaceMediaItems(
 fromIndex: Int,
 toIndex: Int,
 mediaItems: List<MediaItem>,
 ) {
 // No-op: see [addMediaItems] above.
 }

 override fun removeMediaItems(
 fromIndex: Int,
 toIndex: Int,
 ) {
 // No-op: see [addMediaItems] above.
 }

 // ---- Event mapping: Radio.Events -> Player.Listener events.

 private fun handleRadioEvent(event: Radio.Events) {
 val playerEvents = when (event) {
 Radio.Events.Player.Started,
 Radio.Events.Player.Resumed,
 Radio.Events.Player.Staged ->
 Player.EVENT_PLAY_WHEN_READY_CHANGED or
 Player.EVENT_IS_PLAYING_CHANGED or
 Player.EVENT_PLAYBACK_STATE_CHANGED
 Radio.Events.Player.Paused ->
 Player.EVENT_PLAY_WHEN_READY_CHANGED or
 Player.EVENT_IS_PLAYING_CHANGED or
 Player.EVENT_PLAYBACK_STATE_CHANGED
 Radio.Events.Player.Stopped ->
 Player.EVENT_IS_PLAYING_CHANGED or
 Player.EVENT_PLAYBACK_STATE_CHANGED
 Radio.Events.Player.Ended ->
 Player.EVENT_PLAYBACK_STATE_CHANGED
 Radio.Events.Player.Seeked ->
 Player.EVENT_POSITION_DISCONTINUITY
 is Radio.Events.Queue ->
 Player.EVENT_TIMELINE_CHANGED or
 Player.EVENT_MEDIA_ITEM_TRANSITION
 Radio.Events.QueueOption.ShuffleModeChanged ->
 Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
 Radio.Events.QueueOption.LoopModeChanged ->
 Player.EVENT_REPEAT_MODE_CHANGED
 Radio.Events.QueueOption.SpeedChanged,
 Radio.Events.QueueOption.PitchChanged ->
 Player.EVENT_PLAYBACK_PARAMETERS_CHANGED
 Radio.Events.QueueOption.SleepTimerChanged,
 Radio.Events.QueueOption.PauseOnCurrentSongEndChanged ->
 // No-op events: not surfaced through the Player API.
 0
 }
 if (playerEvents == 0) return
 dispatchPlayerEvents(playerEvents)
 }

 private fun dispatchPlayerEvents(events: Int) {
 if (listeners.isEmpty()) return

 // Build a Player.Events from the individual flag bits. The; Player.Events constructor takes a FlagSet, so we use; FlagSet.Builder to construct one. The Int values are the; Player.EVENT_* constants.
 val flagSetBuilder = androidx.media3.common.FlagSet.Builder()
 var remaining = events
 while (remaining != 0) {
 val lowest = remaining and -remaining
 flagSetBuilder.add(lowest)
 remaining = remaining and (lowest - 1)
 }
 val playerEvents = Player.Events(flagSetBuilder.build())
 handler.post {
 for (listener in listeners) {
 listener.onEvents(this, playerEvents)
 }
 }
 }

 companion object {

 // The Media3 Player contract: the maximum position for; which `seekToPrevious` will seek back within the current; item rather than skipping to the previous one. Matches; `RadioShorty.previous()` which uses 3000ms as the; "rewind from the start of the current song" threshold.
 const val MAX_SEEK_TO_PREVIOUS_POSITION_MS: Long = 3_000L


 private val AVAILABLE_COMMANDS: Player.Commands by lazy {
 Player.Commands.Builder()
 .add(Player.COMMAND_PLAY_PAUSE)
 .add(Player.COMMAND_PREPARE)
 .add(Player.COMMAND_STOP)
 .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
 .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
 .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
 .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
 .add(Player.COMMAND_SEEK_TO_PREVIOUS)
 .add(Player.COMMAND_SEEK_TO_NEXT)
 .add(Player.COMMAND_SEEK_BACK)
 .add(Player.COMMAND_SEEK_FORWARD)
 .add(Player.COMMAND_SET_SPEED_AND_PITCH)
 .add(Player.COMMAND_SET_SHUFFLE_MODE)
 .add(Player.COMMAND_SET_REPEAT_MODE)
 .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
 .add(Player.COMMAND_GET_TIMELINE)
 .add(Player.COMMAND_GET_MEDIA_ITEMS_METADATA)
 .add(Player.COMMAND_GET_METADATA)
 .add(Player.COMMAND_GET_VOLUME)
 .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
 .add(Player.COMMAND_GET_DEVICE_VOLUME)
 .build()
 }
 }
}

/** Internal [Timeline] implementation for [RadioForwardingPlayer]. One window per entry in `RadioQueue.currentQueue` (so the `Player.seekToNext*` / `seekToPrevious*` defaults work as expected), with each window's [Timeline.Window.mediaItem] resolved through the [songMediaItemResolver] callback. Implementing [Timeline] inline avoids the cost of a `Bundle` round-trip and keeps the adapter self-contained. */
@UnstableApi
private class RadioTimeline(
 private val radio: RadioAdapterTarget,
 private val songMediaItemResolver: (String) -> MediaItem?,
) : Timeline() {

 override fun getWindowCount(): Int = radio.queue.currentQueue.size

 override fun getWindow(
 windowIndex: Int,
 window: Timeline.Window,
 defaultPositionProjectionUs: Long,
 ): Timeline.Window {
 val songId = radio.queue.currentQueue.getOrNull(windowIndex)
 val mediaItem = songId?.let { songMediaItemResolver(it) } ?: MediaItem.EMPTY
 val durationUs = mediaItem.mediaMetadata.durationMs?.let { C.msToUs(it) } ?: C.TIME_UNSET
 return window.set(
 /* uid = */ songId ?: "empty-$windowIndex",
 /* mediaItem = */ mediaItem,
 /* manifest = */ null,
 /* presentationStartTimeMs = */ C.TIME_UNSET,
 /* windowStartTimeMs = */ C.TIME_UNSET,
 /* elapsedRealtimeEpochOffsetMs = */ C.TIME_UNSET,
 /* isSeekable = */ true,
 /* isDynamic = */ false,
 /* liveConfiguration = */ mediaItem.liveConfiguration,
 /* defaultPositionUs = */ 0L,
 /* durationUs = */ durationUs,
 /* firstPeriodIndex = */ windowIndex,
 /* lastPeriodIndex = */ windowIndex,
 /* positionInFirstPeriodUs = */ 0L,
 )
 }

 override fun getPeriodCount(): Int = radio.queue.currentQueue.size

 override fun getPeriod(periodIndex: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period {
 val songId = radio.queue.currentQueue.getOrNull(periodIndex)
 return period.set(
 /* id = */ songId ?: "empty-$periodIndex",
 /* uid = */ songId ?: "empty-$periodIndex",
 /* windowIndex = */ periodIndex,
 /* durationUs = */ C.TIME_UNSET,
 /* positionInWindowUs = */ 0L,
 )
 }

 override fun getIndexOfPeriod(uid: Any): Int =
 radio.queue.currentQueue.indexOfFirst { it == uid }

 override fun getUidOfPeriod(periodIndex: Int): Any =
 radio.queue.currentQueue.getOrNull(periodIndex) ?: "empty-$periodIndex"
}


// The adapter target interfaces ([RadioAdapterTarget],; [RadioQueueAdapterTarget], [RadioShortyAdapterTarget]) live in; a separate file ([RadioAdapterTarget.kt]) so the compilation; order doesn't matter — Kotlin compiles files in; alphabetical order, and `Radio.kt` is compiled before; `RadioForwardingPlayer.kt`.
