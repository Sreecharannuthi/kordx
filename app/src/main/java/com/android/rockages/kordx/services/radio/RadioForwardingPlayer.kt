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
import androidx.media3.common.DeviceInfo
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AndroidX Media3 [Player] adapter for KordX's custom [Radio] engine.
 *
 * This is the bridge between KordX's playback engine ([Radio], [RadioQueue],
 * [RadioShorty], [RadioPlayer]) and Media3's [Player] interface. Media3's
 * `MediaLibraryService` / `MediaLibrarySession` only understand the [Player] surface, so
 * this adapter translates KordX's events into Media3 events.
 *
 * The class extends [BasePlayer], which provides final default implementations for the
 * ~50 media-item / navigation methods. Those defaults delegate to a small set of
 * abstract getters (`getCurrentTimeline()`, `getMediaItemCount()`, `getMediaItemAt(i)`,
 * `getCurrentMediaItemIndex()`) and the single protected abstract [seekTo] (wired to
 * [Radio.seek]). The remaining [Player] methods are implemented below to map cleanly to
 * the [Radio] / [RadioShorty] / [RadioQueue] public surface.
 *
 * The constructor takes [RadioAdapterTarget] (and the smaller [RadioQueueAdapterTarget] /
 * [RadioShortyAdapterTarget]) rather than a concrete [Radio]. The concrete classes
 * implement these interfaces, so tests can provide hand-rolled fakes without an `Application`
 * / `ViewModel` / `Room` database.
 *
 * The adapter subscribes to [Radio.onUpdate] lazily on the first listener add and
 * unsubscribes in [release]. Android's lock-screen media state extrapolates progress from
 * the last published position, playback speed, and update timestamp. Some OEM notification
 * media cards do not extrapolate that state, so playback ticks publish a throttled typed
 * playback-state callback once per elapsed second. The callback intentionally omits an
 * aggregate playback-state event to avoid rebuilding the notification on every refresh.
 * Other events are dispatched via [playerListenerHandler] (the main [Looper] by default),
 * so listeners see the same threading model as a regular Media3 [Player]. The listener
 * list is a [CopyOnWriteArrayList] so it is safe to iterate while listeners are added/removed.
 *
 * Marked `@UnstableApi` because [BasePlayer] is part of Media3's unstable surface.
 */
@Suppress("MaxLineLength", "SpreadOperator")
@UnstableApi
class RadioForwardingPlayer(
    private val radio: RadioAdapterTarget,
    private val songMediaItemResolver: (String) -> MediaItem?,
    private val seekBackDurationMs: Long,
    private val seekForwardDurationMs: Long,
    private val playerListenerHandler: Handler? = null,
    private val realExoPlayer: ExoPlayer? = null,
    private val audioManager: android.media.AudioManager? = null,
) : BasePlayer() {


    // Resolved handler used to dispatch `Player.Listener.onEvents` calls.
    // Defaults to the main `Looper` for production (where the
    // [KordXMediaLibraryService] creates the adapter from the main thread);
    // tests pass a handler on a non-main looper to avoid the
    // `Looper.getMainLooper` "not mocked" runtime error on the JVM
    // unit-test classpath.
    private val handler: Handler = playerListenerHandler
        ?: Handler(Looper.getMainLooper())

    // ---- Listener bookkeeping (addListener / removeListener contract).

    private val listeners = CopyOnWriteArrayList<Player.Listener>()

    @Volatile
    private var radioUnsubscribe: EventUnsubscribeFn? = null

    @Volatile
    private var positionUnsubscribe: EventUnsubscribeFn? = null

    private val platformPositionRefreshGate = PlatformPositionRefreshGate()
    private var hasPublishedInitialPosition = false


    // Player: addListener / removeListener (Player interface, not
    // provided by BasePlayer).

    override fun addListener(listener: Player.Listener) {
        if (realExoPlayer != null) {
            realExoPlayer.addListener(listener)
            return
        }
        if (!listeners.addIfAbsent(listener)) return

        // Lazy subscription on the first listener.
        if (listeners.size == 1) {
            subscribeToRadio()
            subscribeToPositionUpdates()
        }

        // Sync the newly-added listener with the current state. Media3's
        // PlayerWrapper / MediaLibrarySession attach their listeners after
        // playback has already started; without this initial event they may
        // observe a stale IDLE/PAUSED state until the next radio event fires.
        dispatchPlayerEvents(
            listOf(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
        )
    }

    override fun removeListener(listener: Player.Listener) {
        if (realExoPlayer != null) {
            realExoPlayer.removeListener(listener)
            return
        }
        if (listeners.remove(listener) && listeners.isEmpty()) {
            unsubscribeFromRadio()
            unsubscribeFromPositionUpdates()
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

    private fun subscribeToPositionUpdates() {
        if (positionUnsubscribe != null) return
        positionUnsubscribe = radio.onPlaybackPositionUpdate.subscribe { position ->
            if (!hasPublishedInitialPosition && position.played > 0L) {
                hasPublishedInitialPosition = true
                dispatchInitialPositionSnapshot()
            }
            if (platformPositionRefreshGate.shouldRefresh(position.played, radio.isPlaying)) {
                dispatchPlatformPlaybackStateRefresh()
            }
        }
    }

    private fun unsubscribeFromPositionUpdates() {
        positionUnsubscribe?.invoke()
        positionUnsubscribe = null
        platformPositionRefreshGate.reset()
        hasPublishedInitialPosition = false
    }

    private fun dispatchInitialPositionSnapshot() {
        if (listeners.isEmpty()) return
        handler.post {
            for (listener in listeners) {
                listener.onTimelineChanged(
                    getCurrentTimeline(),
                    Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE,
                )
            }
        }
    }

    private fun dispatchPlatformPlaybackStateRefresh() {
        if (listeners.isEmpty()) return
        handler.post {
            for (listener in listeners) {
                listener.onPlaybackStateChanged(getPlaybackState())
            }
        }
    }


    // Player: transport controls. Note: `play()` and `pause()`
    // are `final` on BasePlayer and dispatch to
    // `setPlayWhenReady(true)` / `setPlayWhenReady(false)`.
    // We implement [setPlayWhenReady] below; the `play` /
    // `pause` dispatch chain handles the rest.

    override fun prepare() {

        // Noop: Radio has no separate prepare phase. RadioPlayer.prepare()
        // is internal; the player is staged by `Radio.play(PlayOptions)`.
        // Auto will issue `play()` once a queue exists.
    }


    // The Player interface declares `stop()` (no args); the
    // [RadioAdapterTarget] interface declares `stop()` (no args,
    // defaults to `ended = true` at the radio side). The no-arg
    // override is the Media3 contract; the radio's
    // `stop(ended: Boolean)` is a separate method on the
    // concrete class, not on the interface.
    override fun stop() {
        if (realExoPlayer != null) {
            realExoPlayer.stop()
        } else {
            radio.stop()
        }
    }

    override fun release() {
        // Real ExoPlayer lifecycle is managed by Radio; just
        // stop listening. Don't release the shared instance.
        if (realExoPlayer == null) {
            unsubscribeFromRadio()
            unsubscribeFromPositionUpdates()
            listeners.clear()
        }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekParameters: Int, forcePosition: Boolean) {
        if (realExoPlayer != null) {
            realExoPlayer.seekTo(positionMs)
        } else {
            // BasePlayer routes next/previous commands through this method.
            // Handle adjacent media-item navigation separately from seeking;
            // the old adapter ignored mediaItemIndex and treated it as a
            // position, so notification next/previous controls stayed on the
            // current track.
            val currentIndex = getCurrentMediaItemIndex()
            when {
                mediaItemIndex > currentIndex && mediaItemIndex == currentIndex + 1 -> {
                    radio.shorty.skip()
                }
                mediaItemIndex < currentIndex && mediaItemIndex == currentIndex - 1 -> {
                    radio.shorty.previous()
                }
                mediaItemIndex == currentIndex && positionMs != C.TIME_UNSET -> {
                    if (radio.hasPlayer) radio.seek(positionMs)
                }
                else -> return
            }
        }
    }


    // [setPlayWhenReady] is abstract on BasePlayer. BasePlayer's
    // `play()` calls this with `true`; its `pause()` calls it with
    // `false`. We delegate to the radio's playPause() in both
    // cases — the radio is the source of truth for play state.
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (realExoPlayer != null) {
            realExoPlayer.playWhenReady = playWhenReady
        } else {
            // Mirror `isPlaying` to `playWhenReady`. We don't filter
            // by the current state — Radio is the source of truth.
            if (playWhenReady && !radio.isPlaying) {
                radio.shorty.playPause()
            } else if (!playWhenReady && radio.isPlaying) {
                radio.shorty.playPause()
            }
        }
    }

    // ---- Player: state queries.


    // `isPlaying()` is `final` on BasePlayer; it returns
    // `getPlaybackState() == STATE_READY && getPlayWhenReady()`. So
    // we don't override it directly — we just implement
    // `getPlaybackState()` (READY whenever a song is staged in the
    // queue) and `getPlayWhenReady()` (returns `radio.isPlaying`).

    override fun isLoading(): Boolean = false

    // Media3's MediaNotificationManager only shows the media notification (and keeps
    // the service in the foreground) when the playback state is NOT STATE_IDLE.
    // `radio.hasPlayer` is false while RadioPlayer is still preparing — and
    // `radio.isPlaying` can already be true in that window — which made the session
    // report STATE_IDLE during real playback: the notification never posted, the
    // service dropped out of the foreground, and the next startForegroundService()
    // crashed with ForegroundServiceDidNotStartInTimeException. A staged queue
    // (currentSongId != null) is READY from the session's point of view; only an
    // empty / stopped queue is IDLE. This also matches what Auto expects on the Now
    // Playing card (PAUSED vs PLAYING is driven by getPlayWhenReady()).
    override fun getPlaybackState(): Int = when {
        radio.queue.currentSongId != null -> Player.STATE_READY
        else -> Player.STATE_IDLE
    }

    override fun getPlaybackSuppressionReason(): Int =
        Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getPlayerError(): PlaybackException? = null

    override fun getPlayWhenReady(): Boolean = radio.isPlaying

    // ---- Player: timeline / media items.

    override fun getCurrentTimeline(): Timeline = RadioTimeline(radio, songMediaItemResolver)

    // Media3's PlayerWrapper.createPositionInfo() requires these indices to be
    // non-negative when the player is attached to a MediaSession. currentSongIndex
    // is -1 when the queue is empty or no song has been selected; fall back to 0
    // in that case. The period index must match the media-item index because
    // RadioTimeline maps one period per window.
    override fun getCurrentMediaItemIndex(): Int {
        val index = radio.queue.currentSongIndex
        val queueSize = radio.queue.currentQueue.size
        return if (index in 0 until queueSize) index else 0
    }

    override fun getCurrentPeriodIndex(): Int = getCurrentMediaItemIndex()

    override fun getMediaMetadata(): MediaMetadata {
        val songId = radio.queue.currentSongId
        val mediaItem = songId?.let { songMediaItemResolver(it) }
        val metadata = mediaItem?.mediaMetadata
        val liveDuration = radio.currentPlaybackPosition?.total?.takeIf { it > 0L }
        if (metadata != null && metadata !== MediaMetadata.EMPTY) {
            // Browse items carry duration in the extras for display, but the
            // Media3 notification/legacy platform bridge reads the canonical
            // MediaMetadata.durationMs field. Keep the item metadata and the
            // live decoder duration aligned so the seek bar has a non-zero max.
            return if (liveDuration != null && metadata.durationMs != liveDuration) {
                metadata.buildUpon().setDurationMs(liveDuration).build()
            } else {
                metadata
            }
        }

        // Fallback: surface the live duration even when the resolver returns
        // null. Auto and the phone media notification use this as the seek-bar
        // range.
        return MediaMetadata.Builder()
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setDurationMs(liveDuration)
            .build()
    }

    override fun getPlaylistMetadata(): MediaMetadata =
        MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setIsBrowsable(false)
            .setIsPlayable(false)
            .build()

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {

        // Noop: the radio manages its own playlist metadata via
        // the queue.
    }

    override fun getCurrentTracks(): Tracks = Tracks.EMPTY

    override fun getTrackSelectionParameters() =
        androidx.media3.common.TrackSelectionParameters.DEFAULT

    override fun setTrackSelectionParameters(parameters: androidx.media3.common.TrackSelectionParameters) {

        // Noop: track selection is owned by the underlying
        // MediaPlayer; the adapter does not surface it.
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

    override fun getDuration(): Long =
        radio.currentPlaybackPosition?.total
            ?: radio.queue.currentSongId
                ?.let(songMediaItemResolver)
                ?.mediaMetadata
                ?.durationMs
            ?: 0L

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

        // The Radio's fade / ducking logic is internal to
        // RadioPlayer. The Media3 setVolume contract is that 1.0
        // = full and 0.0 = muted. We do not propagate to the
        // player because the radio manages its own volume
        // envelope.
    }

    override fun getVolume(): Float = 1f

    override fun mute() {
        // KordX's internal fade/ducking logic lives in RadioPlayer; Media3's mute
        // command is intentionally a no-op here (the radio handles audio envelope).
    }

    override fun unmute() {
        // See mute() — no-op because RadioPlayer owns the real audio envelope.
    }

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


    // Player: device volume (noop — KordX doesn't surface
    // system volume through the player).

    override fun getDeviceInfo(): DeviceInfo =
        if (audioManager != null) {
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
                .setMaxVolume(audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC))
                .build()
        } else {
            DeviceInfo.UNKNOWN
        }

    override fun getDeviceVolume(): Int =
        audioManager?.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) ?: 0

    override fun isDeviceMuted(): Boolean =
        audioManager?.isStreamMute(android.media.AudioManager.STREAM_MUSIC) ?: false

    override fun setDeviceVolume(volume: Int) {
        audioManager?.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            volume,
            0,
        )
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        audioManager?.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            volume,
            flags,
        )
    }

    override fun increaseDeviceVolume() {
        audioManager?.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_RAISE,
            0,
        )
    }

    override fun increaseDeviceVolume(flags: Int) {
        audioManager?.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_RAISE,
            flags,
        )
    }

    override fun decreaseDeviceVolume() {
        audioManager?.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_LOWER,
            0,
        )
    }

    override fun decreaseDeviceVolume(flags: Int) {
        audioManager?.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.ADJUST_LOWER,
            flags,
        )
    }

    override fun setDeviceMuted(muted: Boolean) {
        val direction = if (muted) {
            android.media.AudioManager.ADJUST_MUTE
        } else {
            android.media.AudioManager.ADJUST_UNMUTE
        }
        audioManager?.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            direction,
            0,
        )
    }
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


    // Player: application looper (used by Media3 session to
    // dispatch listener events). Returns the looper of the
    // handler we use to dispatch events, so all listener
    // callbacks land on the same thread.

    override fun getApplicationLooper(): android.os.Looper =
        handler.looper


    // Player: playlist bulk operations. These are abstract on BasePlayer because its
    // defaults dispatch to `setMediaItems(List, boolean)` / `setMediaItems(List, int, long)`.
    // We implement them as no-ops on the radio (the radio manages its own queue via
    // `RadioShorty.playQueue` and `RadioQueue`); the Media3 session uses `Radio.onCustomCommand`
    // to translate Auto intents into radio operations.

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {

        // Noop: the radio's queue is managed by [RadioShorty.playQueue]
        // and [RadioQueue.add]. Auto does not surface
        // `setMediaItems` directly.
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

        // Noop: the radio's queue is managed by [RadioShorty.playQueue]
        // and [RadioQueue.add]. Auto does not surface
        // `addMediaItems` directly.
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
        val eventFlags = when (event) {
            Radio.Events.Player.Started,
            Radio.Events.Player.Resumed,
            Radio.Events.Player.Staged ->
                listOf(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            Radio.Events.Player.Paused ->
                listOf(
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            Radio.Events.Player.Stopped ->
                listOf(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            Radio.Events.Player.Ended ->
                listOf(Player.EVENT_PLAYBACK_STATE_CHANGED)
            Radio.Events.Player.Seeked ->
                // The typed position callback updates MediaSession's cached
                // PositionInfo. A state/timeline refresh here rebuilds the
                // notification using that new position without resetting the
                // playing chronometer during ordinary timer ticks.
                listOf(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_TIMELINE_CHANGED)
            is Radio.Events.Queue ->
                listOf(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            Radio.Events.QueueOption.ShuffleModeChanged ->
                listOf(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            Radio.Events.QueueOption.LoopModeChanged ->
                listOf(Player.EVENT_REPEAT_MODE_CHANGED)
            Radio.Events.QueueOption.SpeedChanged,
            Radio.Events.QueueOption.PitchChanged ->
                listOf(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
            Radio.Events.QueueOption.SleepTimerChanged,
            Radio.Events.QueueOption.PauseOnCurrentSongEndChanged ->
                // No-op events: not surfaced through the Player API.
                emptyList()
        }
        if (eventFlags.isEmpty()) return
        dispatchPlayerEvents(eventFlags)
    }

    private fun dispatchPlayerEvents(events: List<Int>) {
        if (listeners.isEmpty()) return

        // Media3 listeners have two notification paths: the aggregate
        // onEvents callback and the individual typed callbacks. MediaSession's
        // internal PlayerListener consumes the typed callbacks; sending only
        // onEvents leaves its PlayerInfo/platform PlaybackState snapshot stale.
        val flagSetBuilder = androidx.media3.common.FlagSet.Builder()
        flagSetBuilder.addAll(*events.toIntArray())
        val playerEvents = Player.Events(flagSetBuilder.build())
        handler.post {
            // Read the position on the listener looper, not before posting this
            // callback. RadioPlayer.seek() is also posted to the main looper;
            // capturing here used the pre-seek position and caused the system
            // media slider to be rebuilt at 00:00. Reading immediately before
            // the typed callback makes MediaSession and the notification see
            // the position that RadioPlayer has actually applied.
            val positionInfo = if (Player.EVENT_POSITION_DISCONTINUITY in events) {
                createCurrentPositionInfo()
            } else {
                null
            }
            for (listener in listeners) {
                // Update MediaSession's cached position before any notification
                // refresh event. Media3 coalesces PlayerInfo updates, so the
                // position callback must run first or a rebuild can observe 0 ms.
                if (positionInfo != null) {
                    listener.onPositionDiscontinuity(
                        positionInfo,
                        positionInfo,
                        Player.DISCONTINUITY_REASON_SEEK,
                    )
                }
                if (Player.EVENT_TIMELINE_CHANGED in events) {
                    listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                }
                if (Player.EVENT_MEDIA_ITEM_TRANSITION in events) {
                    listener.onMediaItemTransition(
                        getCurrentMediaItem(),
                        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                    )
                }
                if (Player.EVENT_MEDIA_METADATA_CHANGED in events) {
                    listener.onMediaMetadataChanged(getMediaMetadata())
                }
                if (Player.EVENT_PLAYBACK_STATE_CHANGED in events) {
                    listener.onPlaybackStateChanged(getPlaybackState())
                }
                if (Player.EVENT_PLAY_WHEN_READY_CHANGED in events) {
                    listener.onPlayWhenReadyChanged(
                        getPlayWhenReady(),
                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                    )
                }
                if (Player.EVENT_IS_PLAYING_CHANGED in events) {
                    listener.onIsPlayingChanged(isPlaying)
                }
                if (Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED in events) {
                    listener.onShuffleModeEnabledChanged(getShuffleModeEnabled())
                }
                if (Player.EVENT_REPEAT_MODE_CHANGED in events) {
                    listener.onRepeatModeChanged(getRepeatMode())
                }
                if (Player.EVENT_PLAYBACK_PARAMETERS_CHANGED in events) {
                    listener.onPlaybackParametersChanged(getPlaybackParameters())
                }
                // Keep the aggregate callback too. Client listeners commonly
                // use it, and Media3's event contract promises both forms.
                listener.onEvents(this, playerEvents)
            }
        }
    }

    private fun createCurrentPositionInfo(): Player.PositionInfo {
        val mediaItem = getCurrentMediaItem()
        val index = getCurrentMediaItemIndex()
        val position = getCurrentPosition()
        return Player.PositionInfo(
            /* windowUid = */ index,
            /* windowIndex = */ index,
            /* mediaItem = */ mediaItem,
            /* periodUid = */ index,
            /* periodIndex = */ getCurrentPeriodIndex(),
            /* positionMs = */ position,
            /* contentPositionMs = */ position,
            /* adGroupIndex = */ C.INDEX_UNSET,
            /* adIndexInAdGroup = */ C.INDEX_UNSET,
        )
    }

    companion object {

        // The Media3 Player contract: the maximum position for
        // which `seekToPrevious` will seek back within the current
        // item rather than skipping to the previous one. Matches
        // `RadioShorty.previous()` which uses 3000ms as the
        // "rewind from the start of the current song" threshold.
        const val MAX_SEEK_TO_PREVIOUS_POSITION_MS: Long = 3_000L


        private val AVAILABLE_COMMANDS: Player.Commands =
            Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_SET_SHUFFLE_MODE,
                    Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_VOLUME,
                    Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                    Player.COMMAND_GET_DEVICE_VOLUME,
                )
                .build()
    }
}

internal class PlatformPositionRefreshGate {
    private var lastPublishedSecond: Long? = null

    fun shouldRefresh(positionMs: Long, isPlaying: Boolean): Boolean {
        val shouldRefresh = if (isPlaying) {
            val currentSecond = positionMs.coerceAtLeast(0L) / MILLIS_PER_SECOND
            (currentSecond != lastPublishedSecond).also { isNewSecond ->
                if (isNewSecond) lastPublishedSecond = currentSecond
            }
        } else {
            reset()
            false
        }
        return shouldRefresh
    }

    fun reset() {
        lastPublishedSecond = null
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
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
            /* liveConfiguration = */ null,
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
        val mediaItem = songId?.let(songMediaItemResolver)
        val durationUs = mediaItem?.mediaMetadata?.durationMs?.let(C::msToUs) ?: C.TIME_UNSET
        return period.set(
            /* id = */ songId ?: "empty-$periodIndex",
            /* uid = */ songId ?: "empty-$periodIndex",
            /* windowIndex = */ periodIndex,
            /* durationUs = */ durationUs,
            /* positionInWindowUs = */ 0L,
        )
    }

    override fun getIndexOfPeriod(uid: Any): Int =
        radio.queue.currentQueue.indexOfFirst { it == uid }

    override fun getUidOfPeriod(periodIndex: Int): Any =
        radio.queue.currentQueue.getOrNull(periodIndex) ?: "empty-$periodIndex"
}

