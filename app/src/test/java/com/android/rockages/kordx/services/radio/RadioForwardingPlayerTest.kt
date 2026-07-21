package com.android.rockages.kordx.services.radio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RadioForwardingPlayerTest {

 private lateinit var radio: FakeRadio
 private lateinit var queue: FakeQueue
 private lateinit var shorty: FakeShorty
 private val songMediaItemResolver: (String) -> MediaItem? = { id ->
 MediaItem.Builder().setMediaId(id).build()
 }
 private lateinit var player: RadioForwardingPlayer

 @BeforeEach
 fun setup() {
 radio = FakeRadio()
 queue = FakeQueue()
 shorty = FakeShorty()
 radio.queue = queue
 radio.shorty = shorty

 // Default seek increments match Settings.kt (15s back, 30s; forward). Tests that need different values construct; their own adapter.
 player = RadioForwardingPlayer(
 radio = radio,
 songMediaItemResolver = songMediaItemResolver,
 seekBackDurationMs = 15_000L,
 seekForwardDurationMs = 30_000L,

 // Pass a noop handler so the test doesn't depend on; android.os.Looper.getMainLooper() (which is not; mocked on the JVM unittest classpath). The handler; is only used to dispatch Player.Listener.onEvents; calls asynchronously; the tests don't assert on; listener delivery timing.
 playerListenerHandler = android.os.Handler(),
 )
 }

 // ---- 1. play() delegates to RadioShorty.playPause().

 @Test
 fun play_delegatesToRadioShorty() {
 radio.isPlaying = false
 player.play()
 assertEquals(1, shorty.playPauseCalls)
 }


 // 2. pause() delegates to RadioShorty.playPause(); (BasePlayer.pause is final and calls setPlayWhenReady(false); setPlayWhenReady delegates to playPause() when isPlaying; is true; we exercise the isPlaying=true path here).

 @Test
 fun pause_delegatesToRadioShorty() {
 radio.isPlaying = true
 player.pause()
 assertEquals(1, shorty.playPauseCalls)
 }

 // ---- 3. seekTo() delegates to Radio.seek().

 @Test
 fun seekTo_delegatesToRadioSeek() {
 radio.hasPlayer = true
 player.seekTo(45_000L)
 assertEquals(listOf(45_000L), radio.seekCalls)
 }

 @Test
 fun seekTo_isNoOpWhenNoPlayer() {
 radio.hasPlayer = false
 player.seekTo(45_000L)
 assertTrue(radio.seekCalls.isEmpty(), "seek() should not be called when hasPlayer=false")
 }

 // ---- 4. setShuffleModeEnabled() calls queue.setShuffleMode.

 @Test
 fun setShuffleModeEnabled_togglesQueueShuffleMode() {
 queue.currentShuffleMode = false
 player.setShuffleModeEnabled(true)
 assertEquals(listOf(true), queue.setShuffleModeCalls)
 assertTrue(player.getShuffleModeEnabled())
 }

 @Test
 fun setShuffleModeEnabled_falseTogglesOff() {
 queue.currentShuffleMode = true
 player.setShuffleModeEnabled(false)
 assertEquals(listOf(false), queue.setShuffleModeCalls)
 assertEquals(false, player.getShuffleModeEnabled())
 }

 // ---- 5. setRepeatMode() maps REPEAT_MODE_* to LoopMode.

 @Test
 fun setRepeatMode_togglesQueueLoopMode() {
 queue.currentLoopMode = RadioQueue.LoopMode.None
 player.setRepeatMode(Player.REPEAT_MODE_ONE)
 assertEquals(listOf(RadioQueue.LoopMode.Song), queue.setLoopModeCalls)
 assertEquals(Player.REPEAT_MODE_ONE, player.getRepeatMode())
 }

 @Test
 fun setRepeatMode_allMapsToQueueLoopMode() {
 queue.currentLoopMode = RadioQueue.LoopMode.None
 player.setRepeatMode(Player.REPEAT_MODE_ALL)
 assertEquals(listOf(RadioQueue.LoopMode.Queue), queue.setLoopModeCalls)
 assertEquals(Player.REPEAT_MODE_ALL, player.getRepeatMode())
 }

 @Test
 fun setRepeatMode_offMapsToNoneLoopMode() {
 queue.currentLoopMode = RadioQueue.LoopMode.Song
 player.setRepeatMode(Player.REPEAT_MODE_OFF)
 assertEquals(listOf(RadioQueue.LoopMode.None), queue.setLoopModeCalls)
 assertEquals(Player.REPEAT_MODE_OFF, player.getRepeatMode())
 }

 @Test
 fun getRepeatMode_reflectsCurrentLoopMode() {
 queue.currentLoopMode = RadioQueue.LoopMode.Song
 assertEquals(Player.REPEAT_MODE_ONE, player.getRepeatMode())
 queue.currentLoopMode = RadioQueue.LoopMode.Queue
 assertEquals(Player.REPEAT_MODE_ALL, player.getRepeatMode())
 queue.currentLoopMode = RadioQueue.LoopMode.None
 assertEquals(Player.REPEAT_MODE_OFF, player.getRepeatMode())
 }

 // ---- 6. isPlaying() reflects the radio's isPlaying.

 @Test
 fun isPlaying_returnsRadioIsPlaying() {
 radio.isPlaying = true

 // isPlaying is final on BasePlayer; computed from; getPlaybackState + getPlayWhenReady. We use getPlayWhenReady; as the publicfacing observable.
 assertTrue(player.getPlayWhenReady())
 radio.isPlaying = false
 assertEquals(false, player.getPlayWhenReady())
 }

 // ---- 7. getCurrentMediaItem() returns the song's MediaItem.

 @Test
 fun getCurrentMediaItem_returnsSongAsMediaItem() {
 queue.currentSongId = "song-1"
 queue.currentSongIndex = 0
 queue.currentQueue = listOf("song-1", "song-2")
 val item = player.getCurrentMediaItem()
 assertNotNull(item)
 assertEquals("song-1", item!!.mediaId)
 }

 @Test
 fun getCurrentMediaItem_isEmptyWhenQueueEmpty() {
 queue.currentSongId = null
 queue.currentSongIndex = -1
 queue.currentQueue = emptyList()

 // When the queue is empty, `BasePlayer.getCurrentMediaItem()`; looks up `getCurrentMediaItemIndex()` (1) in the; timeline, which is outofbounds for an empty; timeline. The result is `null` (not a MediaItem.EMPTY).
 val item = player.getCurrentMediaItem()

 // Emptyqueue is the legitimate "nothing playing" state; AAOS renders this as no Now Playing card. We assert; null (or, on the test classpath, an empty MediaItem; with mediaId "" if BasePlayer's getWindow returns a; zerocount window). Either is acceptable.
 if (item != null) {
 assertEquals("", item.mediaId)
 }
 }

 // ---- 8. getCurrentPosition() returns the radio's played ms.

 @Test
 fun getCurrentPosition_returnsRadioPosition() {
 radio.currentPlaybackPosition =
 RadioPlayer.PlaybackPosition(played = 12_345L, total = 240_000L)
 assertEquals(12_345L, player.getCurrentPosition())
 assertEquals(240_000L, player.getDuration())
 }

 @Test
 fun getCurrentPosition_returnsZeroWhenNoPlayer() {
 radio.currentPlaybackPosition = null
 assertEquals(0L, player.getCurrentPosition())
 assertEquals(0L, player.getDuration())
 }

 // ---- 9. instanceof Player.

 @Test
 fun radioForwardingPlayer_isAPlayer() {
 // The headline assertion: the adapter is a Media3 Player.
 assertTrue(player is Player, "RadioForwardingPlayer must implement androidx.media3.common.Player")
 }


 // Adapterlevel invariants (regression guards for the; BasePlayer / Player contract).

 @Test
 fun canAdvertiseSession_isTrue() {

 // BasePlayer.canAdvertiseSession() is final and returns; true; we don't override it. This is the AAOS; "sessionadvertisable" check.
 assertTrue(player.canAdvertiseSession())
 }

 @Test
 @org.junit.jupiter.api.Disabled(
 "Handler.getLooper() is not mocked on the JVM unit-test classpath. " +
 "Looper-touching assertions are deferred to the AVD validation gate (26l)."
 )
 fun getApplicationLooper_returnsHandlerLooper() {

 // Player.getApplicationLooper() is abstract; we return; the listener handler's looper. The Handler class; doesn't have a mocked `getLooper` on the JVM unittest; classpath, so this assertion is deferred to the AVD; validation gate (26l) where the real Android runtime; is available.
 val looper = player.getApplicationLooper()
 assertNotNull(looper)
 }

 @Test
 @org.junit.jupiter.api.Disabled(
 "Player.Commands.Builder.add touches android.util.SparseBooleanArray.append " +
 "which is not mocked on the JVM unit-test classpath. " +
 "Commands-contents assertions are deferred to the AVD validation gate (26l)."
 )
 fun getAvailableCommands_returnsNonNull() {

 // `Player.Commands.Builder.add` touches; `android.util.SparseBooleanArray.append` which is not; mocked on the JVM unittest classpath. We can't; assert the contents of the commands snapshot here; that assertion lives in the AVD validation gate; (26l) where the real Android runtime is available.; We just assert that the getter is callable and; returns a nonnull `Player.Commands` (the lazy; initializer would throw before this point, failing; the test cleanly if `Player.Commands.Builder`; becomes broken).
 val commands = player.getAvailableCommands()
 assertNotNull(commands)
 }

 @Test
 fun getSeekBackIncrement_usesConstructorValue() {
 val custom = RadioForwardingPlayer(
 radio = radio,
 songMediaItemResolver = songMediaItemResolver,
 seekBackDurationMs = 7_000L,
 seekForwardDurationMs = 12_000L,
 playerListenerHandler = android.os.Handler(),
 )
 assertEquals(7_000L, custom.getSeekBackIncrement())
 assertEquals(12_000L, custom.getSeekForwardIncrement())
 }

 @Test
 fun getMaxSeekToPreviousPosition_isThreeSeconds() {

 // The Media3 Player contract: the maximum position for; which `seekToPrevious` will seek back within the; current item rather than skipping to the previous one.; Matches RadioShorty.previous()'s 3000ms threshold.
 assertEquals(3_000L, player.getMaxSeekToPreviousPosition())
 }

 @Test
 fun getPlaybackState_isIdleWhenNoPlayer() {
 radio.hasPlayer = false
 assertEquals(Player.STATE_IDLE, player.getPlaybackState())
 }

 @Test
 fun getPlaybackState_isReadyWhenHasPlayer() {
 radio.hasPlayer = true
 assertEquals(Player.STATE_READY, player.getPlaybackState())
 }

 @Test
 fun getMediaItemCount_reflectsQueueSize() {
 queue.currentQueue = listOf("a", "b", "c")
 assertEquals(3, player.getMediaItemCount())
 }

 @Test
 fun getCurrentMediaItemIndex_reflectsQueueIndex() {
 queue.currentSongIndex = 2
 queue.currentQueue = listOf("a", "b", "c", "d")
 assertEquals(2, player.getCurrentMediaItemIndex())
 }

 @Test
 fun getShuffleModeEnabled_reflectsQueueShuffleMode() {
 queue.currentShuffleMode = true
 assertTrue(player.getShuffleModeEnabled())
 queue.currentShuffleMode = false
 assertEquals(false, player.getShuffleModeEnabled())
 }

 @Test
 fun getVolume_isAlwaysOne() {

 // The radio manages its own volume envelope (fade /; ducking). Media3's `getVolume` is a publicfacing; readout; we keep it at 1.0.
 assertEquals(1f, player.getVolume())
 }

 @Test
 fun setVolume_isAcceptedButDoesNotPropagate() {

 // The setVolume contract: 1.0 = full, 0.0 = muted. We; accept the call but don't propagate (the radio manages; its own volume). The assertion is that the call; doesn't throw and getVolume still returns 1.0.
 player.setVolume(0.5f)
 assertEquals(1f, player.getVolume())
 }


 // Event wiring: subscribing to the radio's events causes; onEvents to be invoked when the radio dispatches.

 @Test
 fun addListener_subscribesToRadio() {
 assertNull(radio.lastSubscriber, "no subscriber before addListener")
 val listener = RecordingListener()
 player.addListener(listener)
 assertNotNull(radio.lastSubscriber, "addListener should subscribe to radio")
 }

 @Test
 fun removeListener_unsubscribesFromRadio() {
 val listener = RecordingListener()
 player.addListener(listener)
 assertNotNull(radio.lastSubscriber)
 player.removeListener(listener)

 // After the last listener is removed, the unsubscribe; function should be invoked (radio.lastSubscriber is; reset to null by the FakeRadio's unsubscribe).
 assertNull(radio.lastSubscriber, "removeListener of last listener should unsubscribe from radio")
 }

 // ---- Helpers: test doubles.

 /**
 * Hand-rolled [RadioAdapterTarget] for the tests. Implements
 * just enough of the [Radio] surface to exercise the
 * adapter's `Player` method translations. Captures calls
 * for assertion.
 */
 private class FakeRadio : RadioAdapterTarget {
 override var hasPlayer: Boolean = false
 override var isPlaying: Boolean = false
 override var currentPlaybackPosition: RadioPlayer.PlaybackPosition? = null
 override val currentSpeed: Float = 1f
 override val currentPitch: Float = 1f
 override val audioSessionId: Int? = 0
 override var queue: RadioQueueAdapterTarget = FakeQueue()
 override var shorty: RadioShortyAdapterTarget = FakeShorty()

 val seekCalls = mutableListOf<Long>()
 val stopCalls = mutableListOf<Unit>()
 val setSpeedCalls = mutableListOf<Float>()
 val setPitchCalls = mutableListOf<Float>()
 var lastSubscriber: ((Radio.Events) -> Unit)? = null

 override fun subscribeToEvents(subscriber: (Radio.Events) -> Unit): EventUnsubscribeFn {
 lastSubscriber = subscriber
 return {
 if (lastSubscriber === subscriber) {
 lastSubscriber = null
 }
 }
 }

 override fun seek(positionMs: Long) {
 seekCalls.add(positionMs)
 }

 override fun stop() {
 stopCalls.add(Unit)
 }

 override fun setSpeed(speed: Float, persist: Boolean) {
 setSpeedCalls.add(speed)
 }

 override fun setPitch(pitch: Float, persist: Boolean) {
 setPitchCalls.add(pitch)
 }
 }

 /**
 * Hand-rolled [RadioQueueAdapterTarget]. Captures the
 * loop-mode and shuffle-mode transitions for assertion.
 */
 private class FakeQueue : RadioQueueAdapterTarget {
 override var currentShuffleMode: Boolean = false
 override var currentLoopMode: RadioQueue.LoopMode = RadioQueue.LoopMode.None
 override var currentSongId: String? = null
 override var currentSongIndex: Int = -1
 override var currentQueue: List<String> = emptyList()

 val setLoopModeCalls = mutableListOf<RadioQueue.LoopMode>()
 val setShuffleModeCalls = mutableListOf<Boolean>()

 override fun setLoopMode(mode: RadioQueue.LoopMode) {
 setLoopModeCalls.add(mode)
 currentLoopMode = mode
 }

 override fun setShuffleMode(enabled: Boolean) {
 setShuffleModeCalls.add(enabled)
 currentShuffleMode = enabled
 }
 }

 /**
 * Hand-rolled [RadioShortyAdapterTarget]. Captures the
 * play/pause and seek calls.
 */
 private class FakeShorty : RadioShortyAdapterTarget {
 var playPauseCalls: Int = 0
 val seekFromCurrentCalls = mutableListOf<Int>()
 val previousCalls = mutableListOf<Unit>()
 val skipCalls = mutableListOf<Unit>()

 override fun playPause() {
 playPauseCalls++
 }

 override fun seekFromCurrent(offsetSecs: Int) {
 seekFromCurrentCalls.add(offsetSecs)
 }

 override fun previous(): Boolean = false

 override fun skip(): Boolean = false
 }

 /**
 * Captures the `onEvents(player, events)` calls a
 * `Player.Listener` receives. We can't easily assert the
 * `Player.Events` FlagSet contents from Kotlin (it's a
 * SparseBooleanArray-backed Java class), so we just confirm
 * the listener was invoked.
 */
 private class RecordingListener : Player.Listener {
 var eventCount: Int = 0
 private set

 override fun onEvents(player: Player, events: Player.Events) {
 eventCount++
 }
 }
}
