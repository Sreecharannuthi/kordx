package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import com.android.rockages.kordx.core.utils.Eventer
import com.android.rockages.kordx.core.utils.Logger
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Date
import java.util.Timer

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

 private val focus = RadioFocus(kordx)
 private val nativeReceiver = RadioNativeReceiver(kordx)
 private var player: RadioPlayer? = null
 private var nextPlayer: RadioPlayer? = null

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

 init {
 nativeReceiver.start()
 onUpdate.subscribe(this::watchQueueUpdates)
 }

 fun ready() {

 // Issue #773 / #707: restore the persisted; loop mode and shuffle mode on app start. The; code held these in memory only, so the user's "Repeat; all" / "Shuffle on" choice was lost on every app restart.; The settings are read here (before `attachGrooveListener`; so the order is deterministic), then the queue's; `setLoopMode` / `setShuffleMode` are called with; `persist = false` semantics by way of the `if (existing !=; value) persist` guard inside those methods (a noop write; doesn't repersist the same value).
 queue.setLoopMode(kordx.settings.lastLoopMode.value)
 queue.setShuffleMode(kordx.settings.lastShuffleMode.value)
 attachGrooveListener()
 session.start()
 observatory.start()
 }

 fun destroy() {
 stop(ended = false)
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
 stopCurrentSong()
 val song = queue.getSongIdAt(options.index)?.let { kordx.groove.song.get(it) }
 if (song == null) {

 // recursion guard. When the requested index; does not resolve to a song AND the caller is not; autoplaying (e.g. "set up the queue, the user will; press play"), we earlyreturn here. The previous; behavior unconditionally called; `onSongFinish(SongFinishSource.Exception)`, which; reenters `play(nextIndex)`. If the queue contains; only stale song ids (e.g. after a library rescan; removed them, but the queue's `currentQueue` still; references the dead ids), this loop recurses; forever → `StackOverflowError` in the radio; service. With this guard, the userdriven "skip to; a nonexistent song" / "playQueue with autostart =; false" paths return cleanly. The `autostart == true`; path (the errorrecovery autoadvance) is preserved; so the existing exceptionflow still works.
 if (!options.autostart) {
 return
 }
 // Drop the stale id before invoking error-recovery; otherwise `loopMode == Queue` returns
 // the same index and recurses synchronously.
 queue.removeAtSilently(options.index)
 if (queue.isEmpty()) {
 queue.currentSongIndex = -1
 return
 }
 onSongFinish(SongFinishSource.Exception)
 return
 }
 try {
 queue.currentSongIndex = options.index
 player = nextPlayer?.takeIf {
 when {
 it.id == song.id -> true
 else -> {
 it.destroy()
 false
 }
 }
 } ?: RadioPlayer(kordx, song.id, song.uri)
 nextPlayer = null
 player!!.setOnPreparedListener {
 options.startPosition?.let {
 if (it > 0L) {
 seek(it)
 }
 }
 setSpeed(persistedSpeed, true)
 setPitch(persistedPitch, true)
 if (options.autostart) {
 start()
 }
 }
 player!!.setOnPlaybackPositionListener {
 onPlaybackPositionUpdate.dispatch(it)
 }
 player!!.setOnFinishListener {
 onSongFinish(SongFinishSource.Finish)
 }
 player!!.setOnErrorListener { what, extra ->
 Logger.warn(
 "Radio",
 "skipping song ${queue.currentSongId} (${queue.currentSongIndex}) due to $what + $extra"
 )
 when {
 // happens when change playback params fail, we skip it since its non-critical
 what == 1 && extra == -22 -> onSongFinish(SongFinishSource.Finish)
 else -> {
 queue.remove(queue.currentSongIndex)
 onSongFinish(SongFinishSource.Exception)
 }
 }
 }
 player!!.prepare()
 prepareNextPlayer()
 onUpdate.dispatch(Events.Player.Staged)
 } catch (err: Exception) {
 Logger.warn(
 "Radio",
 "skipping song ${queue.currentSongId} (${queue.currentSongIndex})",
 err,
 )
 queue.remove(queue.currentSongIndex)
 }
 }

 private fun prepareNextPlayer() {
 if (!kordx.settings.gaplessPlayback.value) {
 return
 }
 val (nextSongIndex) = getNextSong(SongFinishSource.Finish)
 val song = queue.getSongIdAt(nextSongIndex)?.let { kordx.groove.song.get(it) } ?: return
 if (song.id == nextPlayer?.id) {
 return
 }
 try {
 nextPlayer?.destroy()
 nextPlayer = RadioPlayer(kordx, song.id, song.uri).also {
 it.prepare()
 }
 } catch (err: Exception) {
 Logger.warn(
 "Radio",
 "unable to prepare next player ${song.id} (${nextSongIndex})",
 err,
 )
 }
 }

 fun resume() = start()

 private fun start() {
 player?.let {
 val hasFocus = focus.requestFocus()
 if (kordx.settings.requireAudioFocus.value && !hasFocus) {
 return
 }
 if (it.fadePlayback) {
 it.changeVolumeInstant(RadioPlayer.MIN_VOLUME)
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
 it.changeVolume(
 to = RadioPlayer.MIN_VOLUME,
 forceFade = forceFade,
 ) { _ ->
 it.pause()
 focus.abandonFocus()
 onFinish()
 onUpdate.dispatch(Events.Player.Paused)
 }
 }
 }

 fun pauseInstant() {
 player?.let {
 it.pause()
 onUpdate.dispatch(Events.Player.Paused)
 }
 }

 override fun stop() {
 stop(ended = true)
 }

 fun stop(ended: Boolean) {
 stopCurrentSong()
 queue.reset()
 clearSleepTimer()
 persistedSpeed = RadioPlayer.DEFAULT_SPEED
 persistedPitch = RadioPlayer.DEFAULT_PITCH
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
 fun jumpToPrevious() = jumpTo(queue.currentSongIndex - 1)
 fun jumpToNext() = jumpTo(queue.currentSongIndex + 1)
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

 private fun stopCurrentSong() {
 player?.let {
 player = null
 it.setOnPlaybackPositionListener {}
 it.changeVolume(RadioPlayer.MIN_VOLUME) { _ ->
 it.stop()
 onUpdate.dispatch(Events.Player.Stopped)
 }
 }
 }

 private enum class SongFinishSource {
 Finish,
 Exception,
 }

 private fun onSongFinish(source: SongFinishSource) {
 stopCurrentSong()
 if (queue.isEmpty()) {
 queue.currentSongIndex = -1
 return
 }
 var (nextSongIndex, autostart) = getNextSong(source)
 if (pauseOnCurrentSongEnd) {
 autostart = false
 setPauseOnCurrentSongEnd(false)
 }
 play(PlayOptions(nextSongIndex, autostart = autostart))
 }

 private fun getNextSong(source: SongFinishSource): Pair<Int, Boolean> {
 if (queue.isEmpty()) {
 return -1 to false
 }
 var autostart: Boolean
 var nextSongIndex: Int
 when (queue.currentLoopMode) {
 RadioQueue.LoopMode.Song -> {
 nextSongIndex = queue.currentSongIndex
 autostart = source == SongFinishSource.Finish
 if (!queue.hasSongAt(nextSongIndex)) {
 nextSongIndex = 0
 autostart = false
 }
 }

 else -> {
 nextSongIndex = when (source) {
 SongFinishSource.Finish -> queue.currentSongIndex + 1
 SongFinishSource.Exception -> queue.currentSongIndex
 }
 autostart = true
 if (!queue.hasSongAt(nextSongIndex)) {
 nextSongIndex = 0
 autostart = queue.currentLoopMode == RadioQueue.LoopMode.Queue
 }
 }
 }
 return nextSongIndex to autostart
 }

 private fun attachGrooveListener() {
 kordx.groove.coroutineScope.launch {
 kordx.groove.readyDeferred.await()
 restorePreviousQueue()
 }
 }

 private fun restorePreviousQueue() {
 if (!queue.isEmpty()) {
 return
 }
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
 }
 }

 internal fun watchQueueUpdates(event: Events) {
 if (event !is Events.Queue) {
 return
 }
 prepareNextPlayer()
 }

 override fun onKordXReady() {
 ready()
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
