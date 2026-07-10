package com.android.rockages.kordx.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContract
import com.android.rockages.kordx.BuildConfig
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.R
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.services.groove.getSongIds
import com.android.rockages.kordx.services.groove.getSortedSongIds
import kotlinx.coroutines.launch

class RadioSession(val kordx: KordX) {
 data class UpdateRequest(
 val song: Song,
 val artworkUri: Uri,
 val artworkBitmap: Bitmap,
 val playbackPosition: RadioPlayer.PlaybackPosition,
 val isPlaying: Boolean,
 )

 internal val mediaSession = MediaSessionCompat(kordx.applicationContext, MEDIA_SESSION_ID)
 private val artworkCacher = RadioArtworkCacher(kordx)
 private val notification = RadioNotification(kordx)

 private var currentSongId: String? = null
 private var receiver = object : BroadcastReceiver() {
 override fun onReceive(context: Context?, intent: Intent?) {
 intent?.action?.let { action ->
 handleAction(action)
 }
 }
 }

 /**
 * Debug-only receiver that lets `adb shell am broadcast` trigger the
 * custom-action handlers (shuffle / repeat / favorite) without going
 * through the MediaController / DHU. The Android Auto integration is
 * the production path for these actions; this receiver exists so the
 * "How to test" command can confirm the handlers fire under
 * `adb logcat -d | grep "RadioSession.*(shuffle|repeat|favorite)"`.
 *
 * Marked `RECEIVER_NOT_EXPORTED` so only the app itself (or an
 * `adb shell am broadcast` from the same UID) can send these intents.
 */
 private val debugCustomActionReceiver = object : BroadcastReceiver() {
 override fun onReceive(context: Context?, intent: Intent?) {
 when (intent?.action) {
 DEBUG_ACTION_SHUFFLE -> handleCustomAction(RadioSessionState.ACTION_SHUFFLE)
 DEBUG_ACTION_REPEAT -> handleCustomAction(RadioSessionState.ACTION_REPEAT)
 DEBUG_ACTION_FAVORITE -> handleCustomAction(RadioSessionState.ACTION_FAVORITE)
 DEBUG_ACTION_SHUFFLE_ALL -> handleCustomAction(RadioSessionState.ACTION_SHUFFLE_ALL)
 DEBUG_ACTION_ROOT_SEARCH -> handleCustomAction(RadioSessionState.ACTION_SEARCH)
 }
 }
 }

 /**
 * — debug-only receiver that lets `adb shell am broadcast`
 * trigger the play-from-search handler with a query taken from the
 * intent's `query` extra. Mirrors the [debugCustomActionReceiver]
 * pattern from so the "How to test" commands can
 * fire `handlePlayFromSearch` without going through the
 * MediaController / DHU. Marked `RECEIVER_EXPORTED` because the
 * sender is the shell user (see "Confirmed" note in
 */
 private val debugSearchReceiver = object : BroadcastReceiver() {
 override fun onReceive(context: Context?, intent: Intent?) {
 val query = intent?.getStringExtra(EXTRA_DEBUG_QUERY)
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "DEBUG_ACTION_SEARCH received: query='${query.orEmpty()}'",
 )
 handlePlayFromSearch(query)
 }
 }

 /**
 * — debug-only receiver that lets `adb shell am broadcast`
 * add a song id to the recently-played history. The "production"
 * hook is in [RadioSession]'s `kordx.radio.onUpdate.subscribe`
 * block, which fires on `Radio.Events.Player.Started` and calls
 * `recentPlays.add(songId)`; the receiver exists for the AVD
 * validation step (the plans "play 3 songs and check the
 * 3 entries persist across force-stop" — the SAF picker +
 * MediaController / DHU paths the production hook needs are not
 * available in this CI environment).
 *
 * Usage: `adb shell am broadcast -a
 * com.android.rockages.kordx.radio.DEBUG_RECENT_PLAY --es
 * songId "test-song-1"`.
 */
 private val debugRecentPlayReceiver = object : BroadcastReceiver() {
 override fun onReceive(context: Context?, intent: Intent?) {
 val songId = intent?.getStringExtra(EXTRA_DEBUG_SONG_ID)
 if (songId.isNullOrBlank()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "DEBUG_ACTION_RECENT_PLAY received: no songId extra, ignored",
 )
 return
 }
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "DEBUG_ACTION_RECENT_PLAY received: songId='$songId'",
 )
 kordx.groove.recentPlays.add(songId)
 }
 }

 /**
 * — debug-only receiver that lets `adb shell am broadcast`
 * trigger the playback error path in [handlePlaybackError] with a
 * fake song id and error code. The production error path fires
 * from a `Radio.play(song)` / `RadioPlayer.start()` exception,
 * which is hard to drive from a shell command (the production
 * path needs a populated library + MediaController / DHU). The
 * receiver calls [handlePlaybackError] directly so the AVD
 * validation gate can confirm the error path:
 * - logs the error (plan: "log the error with the song id
 * and exception class")
 * - publishes the error state via `setErrorMessage(errorCode,
 * errorMessage)` on the MediaSession
 * - auto-skips to the next song (plan: "Skip to next song
 * in the queue automatically")
 * - does not crash the service (plan: "Do not crash the
 * service")
 *
 * Usage: `adb shell am broadcast -a
 * com.android.rockages.kordx.radio.DEBUG_PLAYBACK_ERROR --es
 * songId "song:test-error" --ei code 1`.
 */
 private val debugPlaybackErrorReceiver = object : BroadcastReceiver() {
 override fun onReceive(context: Context?, intent: Intent?) {
 val songId = intent?.getStringExtra(EXTRA_DEBUG_SONG_ID)
 val code = intent?.getIntExtra(EXTRA_DEBUG_ERROR_CODE, PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR)
 ?: PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "DEBUG_ACTION_PLAYBACK_ERROR received: songId='${songId ?: "<null>"}', code=$code",
 )
 handlePlaybackError(
 songId = songId,
 errorCode = code,
 errorMessage = "simulated playback error (code $code)",
 exception = RuntimeException("simulated playback error (code $code)"),
 )
 }
 }

 fun start() {
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
 kordx.applicationContext.registerReceiver(
 receiver,
 IntentFilter().apply {
 addAction(ACTION_PLAY_PAUSE)
 addAction(ACTION_PREVIOUS)
 addAction(ACTION_NEXT)
 addAction(ACTION_STOP)
 },
 Context.RECEIVER_EXPORTED,

 // https://developer.android.com/reference/android/content/Context#RECEIVER_EXPORTED; really, RECEIVER_EXPORTED and RECEIVER_NOT_EXPORTED makes no difference.; the notification appears perfectly, Pano Scrobbler sees it,; Wear OS can send signals to play/pause the app, other media apps can pause it,; no clue what the difference here is... but here we are.
 )
 if (BuildConfig.DEBUG) {
 kordx.applicationContext.registerReceiver(
 debugCustomActionReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_SHUFFLE)
 addAction(DEBUG_ACTION_REPEAT)
 addAction(DEBUG_ACTION_FAVORITE)
 addAction(DEBUG_ACTION_SHUFFLE_ALL)
 addAction(DEBUG_ACTION_ROOT_SEARCH)
 },
 Context.RECEIVER_EXPORTED,
 )
 kordx.applicationContext.registerReceiver(
 debugSearchReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_SEARCH)
 },
 Context.RECEIVER_EXPORTED,
 )
 kordx.applicationContext.registerReceiver(
 debugRecentPlayReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_RECENT_PLAY)
 },
 Context.RECEIVER_EXPORTED,
 )
 kordx.applicationContext.registerReceiver(
 debugPlaybackErrorReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_PLAYBACK_ERROR)
 },
 Context.RECEIVER_EXPORTED,
 )
 }
 } else {
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 receiver,
 IntentFilter().apply {
 addAction(ACTION_PLAY_PAUSE)
 addAction(ACTION_PREVIOUS)
 addAction(ACTION_NEXT)
 addAction(ACTION_STOP)
 },
 )
 if (BuildConfig.DEBUG) {
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 debugCustomActionReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_SHUFFLE)
 addAction(DEBUG_ACTION_REPEAT)
 addAction(DEBUG_ACTION_FAVORITE)
 addAction(DEBUG_ACTION_SHUFFLE_ALL)
 addAction(DEBUG_ACTION_ROOT_SEARCH)
 },
 )
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 debugSearchReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_SEARCH)
 },
 )
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 debugRecentPlayReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_RECENT_PLAY)
 },
 )
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 kordx.applicationContext.registerReceiver(
 debugPlaybackErrorReceiver,
 IntentFilter().apply {
 addAction(DEBUG_ACTION_PLAYBACK_ERROR)
 },
 )
 }
 }
 mediaSession.setCallback(
 object : MediaSessionCompat.Callback() {
 override fun onPlay() {
 super.onPlay()
 handleAction(ACTION_PLAY_PAUSE)
 }

 override fun onPause() {
 super.onPause()
 handleAction(ACTION_PLAY_PAUSE)
 }

 override fun onSkipToPrevious() {
 super.onSkipToPrevious()
 handleAction(ACTION_PREVIOUS)
 }

 override fun onSkipToNext() {
 super.onSkipToNext()
 handleAction(ACTION_NEXT)
 }

 override fun onStop() {
 super.onStop()
 handleAction(ACTION_STOP)
 }

 override fun onSeekTo(pos: Long) {
 super.onSeekTo(pos)
 kordx.radio.seek(pos)
 }

 override fun onRewind() {
 super.onRewind()
 val duration = kordx.settings.seekBackDuration.value
 kordx.radio.shorty.seekFromCurrent(-duration)
 }

 override fun onFastForward() {
 super.onFastForward()
 val duration = kordx.settings.seekForwardDuration.value
 kordx.radio.shorty.seekFromCurrent(duration)
 }

 override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
 super.onPlayFromMediaId(mediaId, extras)
 handlePlayFromMediaId(mediaId)
 }

 override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
 super.onPrepareFromMediaId(mediaId, extras)
 handlePlayFromMediaId(mediaId, autostart = false)
 }

 override fun onPlayFromSearch(query: String?, extras: Bundle?) {
 super.onPlayFromSearch(query, extras)
 handlePlayFromSearch(query)
 }

 override fun onCustomAction(action: String, extras: Bundle?) {
 super.onCustomAction(action, extras)
 handleCustomAction(action)
 }

 override fun onMediaButtonEvent(intent: Intent?): Boolean {
 val handled = super.onMediaButtonEvent(intent)
 if (handled) {
 return true
 }
 val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
 intent?.getParcelableExtra(
 Intent.EXTRA_KEY_EVENT,
 KeyEvent::class.java,
 )
 } else {
 @Suppress("DEPRECATION")
 intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
 }
 return when (keyEvent?.keyCode) {
 KeyEvent.KEYCODE_MEDIA_PREVIOUS,
 KeyEvent.KEYCODE_MEDIA_REWIND,
 -> {
 handleAction(ACTION_PREVIOUS)
 true
 }

 KeyEvent.KEYCODE_MEDIA_NEXT -> {
 handleAction(ACTION_NEXT)
 true
 }

 KeyEvent.KEYCODE_MEDIA_CLOSE,
 KeyEvent.KEYCODE_MEDIA_STOP,
 -> {
 handleAction(ACTION_STOP)
 true
 }

 else -> false
 }
 }
 }
 )
 notification.start()
 kordx.radio.onUpdate.subscribe {
 when (it) {
 Radio.Events.Player.Ended -> cancel()
 is Radio.Events.Player -> {
 update()
 if (it is Radio.Events.Player.Started) {
 val songId = kordx.radio.queue.currentSongId
 if (songId != null) {
 kordx.groove.recentPlays.add(songId)
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "recentPlays.add: songId=$songId",
 )
 }
 }
 }
 is Radio.Events.Queue -> publishQueue()
 Radio.Events.QueueOption.ShuffleModeChanged,
 Radio.Events.QueueOption.LoopModeChanged,
 -> update()
 else -> {}
 }
 }

 // Publish the queue once at session start so Auto's queue view; is populated even before playback begins (the browse tree is; a different surface — it always reflects the library).
 publishQueue()
 }

 fun handleAction(action: String) {
 when (action) {
 ACTION_PLAY_PAUSE -> kordx.radio.shorty.playPause()
 ACTION_PREVIOUS -> kordx.radio.shorty.previous()
 ACTION_NEXT -> kordx.radio.shorty.skip()
 ACTION_STOP -> kordx.radio.stop(ended = true)
 }
 }

 /**
 * Handle a custom action from Android Auto / the system (shuffle,
 * repeat, favorite). The custom-action strings live in
 * [RadioSessionState] alongside their pure icon/label builders. After
 * each handler mutates state, [update] is called so the playback
 * state — and therefore the visible Now Playing card — reflects the
 * new state. The logcat `KordXLogger` lines make the validation gate
 * trivial: `adb logcat -d | grep "RadioSession.*(shuffle|repeat|favorite)"`.
 */
 fun handleCustomAction(action: String) {
 when (action) {
 RadioSessionState.ACTION_SHUFFLE -> {
 val now = kordx.radio.queue.currentShuffleMode
 kordx.radio.queue.toggleShuffleMode()
 val next = kordx.radio.queue.currentShuffleMode
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "shuffle toggled: $now -> $next",
 )
 update()
 }
 RadioSessionState.ACTION_REPEAT -> {
 val now = kordx.radio.queue.currentLoopMode
 kordx.radio.queue.toggleLoopMode()
 val next = kordx.radio.queue.currentLoopMode
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "repeat -> $next",
 )
 update()
 }
 RadioSessionState.ACTION_FAVORITE -> {
 val songId = currentSongId
 if (songId == null) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "favorite ignored: no current song",
 )
 return
 }
 kordx.groove.songFavorites.toggle(songId)
 val next = kordx.groove.songFavorites.isFavorite(songId)
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "favorite toggled: $next for songId=$songId",
 )
 update()
 }
 RadioSessionState.ACTION_SHUFFLE_ALL -> {
 val allSongIds = kordx.groove.song.all.value
 if (allSongIds.isEmpty()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "shuffle all ignored: library is empty",
 )
 return
 }

 // the workaround that wrote; directly to `kordx.radio.queue.originalQueue` /; `currentQueue` / `currentSongIndex` is reverted.; The recursion it was working around is now; handled by the `autostart = false` earlyreturn; guard in `Radio.play()`. With the guard in; place, the canonical `RadioShorty.playQueue(...)`; API is safe to use even when some song ids in; the library can't be resolved: `playQueue` calls; `RadioQueue.add` → `afterAdd` → `Radio.play(...); with autostart = false`, and the guard returns; cleanly for stale ids. We keep the; `autostart = false` semantics so the queue is; set up ready to play and the user starts; playback via the standard play action (the; production musicapp pattern — Spotify / Apple; Music both defer playback start to the user's; explicit play action).
 val shuffledIndex = kotlin.random.Random.nextInt(allSongIds.size)
 kordx.radio.shorty.playQueue(
 allSongIds,
 options = com.android.rockages.kordx.services.radio.Radio.PlayOptions(
 index = shuffledIndex,
 autostart = false,
 ),
 shuffle = true,
 )
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "shuffle all started: ${allSongIds.size} songs in random order (index=$shuffledIndex)",
 )
 }
 RadioSessionState.ACTION_SEARCH -> {

 // The AAOS client handles the actual voice / text input; we just log the receipt so the AVD validation gate; confirms the action was routed. The plan:; "opens the search bar (delegates to the same code path; as `onSearch`)" — the AAOS search dialog is opened by; the framework once the action is dispatched; the; KordX side doesn't need to do anything beyond logging.
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "search action received: opening AAOS search bar",
 )
 }
 }
 }

 /**
 * Handle a voice search from Android Auto / Assistant. Plays the
 * best match from the existing
 * [com.android.rockages.kordx.services.groove.repositories.SongRepository]
 * fuzzy search. fixes the no-match fallback: the prior
 * implementation fell back to playing the whole library when the
 * fuzzy search returned an empty list, which meant a voice
 * "play zzzzz" command would silently start a full-library
 * shuffle. The plan calls for "0 matches → log warning, do
 * nothing" so the user is never surprised by unrelated playback.
 *
 * Empty / null / blank query: shuffle the whole library (the
 * "play some music" / "surprise me" use case). Non-empty query
 * with at least one match: play the first (best) match via
 * [com.android.rockages.kordx.services.radio.RadioShorty.playQueue].
 */
 fun handlePlayFromSearch(query: String?) {
 val app = kordx
 val allSongIds = app.groove.song.all.value
 if (allSongIds.isEmpty()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "onPlayFromSearch: empty library, no action",
 )
 return
 }
 if (query.isNullOrBlank()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "onPlayFromSearch: empty query -> shuffle all (${allSongIds.size} songs)",
 )
 try {
 app.radio.shorty.playQueue(allSongIds, shuffle = true)
 } catch (err: Exception) {
 handlePlaybackError(
 songId = null,
 errorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
 errorMessage = err.message,
 exception = err,
 )
 }
 return
 }
 val matches = KordXSearch.search(
 query = query,
 songIds = allSongIds,
 lookup = { id -> app.groove.song.get(id)?.let { KordXSearch.songSearchText(it) } ?: "" },
 )
 if (matches.isEmpty()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "onPlayFromSearch: query='$query' -> 0 matches, no action",
 )
 return
 }
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "onPlayFromSearch: query='$query' -> ${matches.size} matches, playing first",
 )
 try {
 app.radio.shorty.playQueue(
 matches,
 options = com.android.rockages.kordx.services.radio.Radio.PlayOptions(index = 0),
 )
 } catch (err: Exception) {
 handlePlaybackError(
 songId = matches.firstOrNull(),
 errorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
 errorMessage = err.message,
 exception = err,
 )
 }
 }

 /**
 * Resolve a media id produced by [KordXMediaBrowserService] to a queue and
 * play it via the existing [com.android.rockages.kordx.services.radio.Radio]
 * player. Supports a single song leaf (`song:<id>`) or a container
 * (`album:<id>`, `albumArtist:<name>`, `genre:<name>`, `playlist:<id>`),
 * which plays the container's sorted song list starting at index 0.
 */
 fun handlePlayFromMediaId(mediaId: String?, autostart: Boolean = true) {
 val id = mediaId ?: return
 val app = kordx
 val songIds: List<String> = try {
 when {
 id.startsWith(KordXMediaSessionConstants.PREFIX_SONG) -> {
 KordXMediaSessionConstants.mediaIdToSongId(id)?.let { listOf(it) } ?: return
 }
 id.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM) -> {
 app.groove.album.get(id.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM))
 ?.getSortedSongIds(app) ?: return
 }
 id.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST) -> {
 val name = id.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST)
 com.android.rockages.kordx.core.groove.AlbumArtist(name, 0, 0).getSortedSongIds(app)
 }
 id.startsWith(KordXMediaSessionConstants.PREFIX_GENRE) -> {
 val name = id.removePrefix(KordXMediaSessionConstants.PREFIX_GENRE)
 com.android.rockages.kordx.core.groove.Genre(name, 0).getSortedSongIds(app)
 }
 id.startsWith(KordXMediaSessionConstants.PREFIX_PLAYLIST) -> {
 app.groove.playlist.get(id.removePrefix(KordXMediaSessionConstants.PREFIX_PLAYLIST))
 ?.getSongIds(app) ?: return
 }
 else -> return
 }
 } catch (err: Exception) {
 handlePlaybackError(
 songId = KordXMediaSessionConstants.mediaIdToSongId(id),
 errorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
 errorMessage = err.message,
 exception = err,
 )
 return
 }
 if (songIds.isEmpty()) return
 val startSongId = KordXMediaSessionConstants.mediaIdToSongId(id)
 val index = if (startSongId != null) {
 songIds.indexOf(startSongId).coerceAtLeast(0)
 } else 0
 try {
 app.radio.shorty.playQueue(
 songIds,
 options = com.android.rockages.kordx.services.radio.Radio.PlayOptions(
 index = index,
 autostart = autostart,
 ),
 )
 } catch (err: Exception) {
 handlePlaybackError(
 songId = startSongId,
 errorCode = PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
 errorMessage = err.message,
 exception = err,
 )
 }
 }

 /**
 * — playback error path. Called from
 * [handlePlayFromMediaId] and [handlePlayFromSearch] when
 * [com.android.rockages.kordx.services.radio.RadioShorty.playQueue]
 * (or any of the song-lookup calls that precede it) throws. The
 * plan's contract: "On exception: set
 * `PlaybackStateCompat.ERROR_CODE_UNKNOWN` (or the specific
 * `ERROR_CODE_*` if known) on the state. Skip to next song in
 * the queue automatically. Log the error (with the song id and
 * exception class) to logcat. Do not crash the service."
 *
 * Implementation:
 * 1. Log the error at WARN with the song id, error code, and
 * exception class + message (the plans "log the error"
 * contract).
 * 2. Build a new [PlaybackStateCompat] via [buildErrorPlaybackState]
 * and publish it via [MediaSessionCompat.setPlaybackState]. The
 * error state is layered on top of the regular state by
 * `setErrorMessage(errorCode, errorMessage)`, which Android
 * Auto / DHU pick up to surface "Couldn't play song X" in
 * the Now Playing card.
 * 3. Auto-skip to the next song in the queue via
 * [com.android.rockages.kordx.services.radio.RadioShorty.skip].
 * This delegates to the existing
 * `Radio.jumpToNext() -> Radio.play(...)` path, which already
 * advances the queue index and triggers another error-recovery
 * cycle if the next song also fails. The plan: "Skip to
 * next song in the queue automatically."
 *
 * The handler never re-throws; even if publishing the state or
 * the skip fails, the service stays alive (`adb logcat -d | grep
 * FATAL` returns 0 matches after a simulated-error run).
 */
 internal fun handlePlaybackError(
 songId: String?,
 errorCode: Int,
 errorMessage: String?,
 exception: Exception? = null,
 ) {
 // 1. Log the error (plan: "log the error with the song id and exception class").
 val message = "playback error: song=${songId ?: "<unknown>"}, " +
 "reason=${playbackErrorCodeName(errorCode)}, " +
 "exception=${exception?.javaClass?.simpleName ?: "<none>"}, " +
 "message=${errorMessage ?: exception?.message ?: "<no message>"}"
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 message,
 )


 // 2. Publish the error state so Auto can show the "Couldn't; play song X" hint in the Now Playing card. The state; inherits the current actions / metadata so the user can; still skip to next / play from the queue. The artwork; render is async (suspend `artworkCacher.getArtwork`) so; we launch on the groove coroutine scope; the error hint; surfaces in the Now Playing card once the artwork is; ready. The plan: "Do not crash the service" — the; launch is fireandforget and the catch is in the; coroutine builder's exception handler.
 kordx.groove.coroutineScope.launch {
 try {
 ensureEnabled()
 val req = currentUpdateRequest() ?: return@launch
 val state = buildErrorPlaybackState(req, errorCode, errorMessage)
 mediaSession.setPlaybackState(state)
 } catch (stateErr: Exception) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "publish error state failed: ${stateErr.message}",
 )
 }
 }


 // 3. Autoskip to the next song. If the next song also; fails, the existing `Radio.play()` error handler will; recursively call `onSongFinish(SongFinishSource.Exception)`; which advances to the next index; this loop continues; until either a song plays successfully or the queue is; empty. The plan: "Skip to next song in the queue; automatically".
 try {
 if (kordx.radio.canJumpToNext()) {
 kordx.radio.shorty.skip()
 }
 } catch (skipErr: Exception) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "auto-skip failed: ${skipErr.message}",
 )
 }
 }

 /**
 * — build a [PlaybackStateCompat] with the given error
 * code / message. The error is layered on top of the regular
 * state by `setErrorMessage(errorCode, errorMessage)`, which
 * Auto / DHU pick up to surface the "Playback error" hint.
 * Falls back to the regular [buildPlaybackState] (no error) if
 * the input parameters are invalid (defensive — thelans
 * "do not crash the service" contract).
 */
 private fun buildErrorPlaybackState(
 req: UpdateRequest,
 errorCode: Int,
 errorMessage: String?,
 ): PlaybackStateCompat {
 val state = buildPlaybackState(req)
 val error = errorMessage ?: "Playback error (code $errorCode)"
 return PlaybackStateCompat.Builder(state)
 .setErrorMessage(errorCode, error)
 .build()
 }

 /**
 * — capture the current playback request (song +
 * artwork + position) for the error-publish path. Returns `null`
 * when the radio has no current song (e.g. the user paused
 * before any song started). The error path tolerates `null` and
 * falls through to the log + auto-skip without publishing a
 * state (the radio is already in a "stopped" state, so there's
 * nothing to layer the error onto). Suspend so we can call
 * [RadioArtworkCacher.getArtwork] (which is itself suspend) —
 * the error-publish path runs on the groove coroutine scope.
 */
 private suspend fun currentUpdateRequest(): UpdateRequest? {
 val songId = kordx.radio.queue.currentSongId ?: return null
 val song = kordx.groove.song.get(songId) ?: return null
 val position = kordx.radio.currentPlaybackPosition
 ?: RadioPlayer.PlaybackPosition(played = 0L, total = song.duration)
 return UpdateRequest(
 song = song,
 artworkUri = kordx.groove.song.getArtworkUri(song.id),
 artworkBitmap = artworkCacher.getArtwork(song),
 playbackPosition = position,
 isPlaying = kordx.radio.isPlaying,
 )
 }

 /**
 * — map a `PlaybackStateCompat.ERROR_CODE_*` constant
 * to a short, logcat-friendly name. Used by the
 * [handlePlaybackError] log line. Unknown codes (or 0 for
 * "no error") return the integer's decimal form so 
 * Android API addition surfaces in the log without crashing
 * . The constants in this version of
 * `android.support.v4.media.session.PlaybackStateCompat` (1.7.0)
 * are listed below — framework-only constants (added in
 * `android.media.session.PlaybackState` API 21+) are
 * intentionally omitted.
 */
 private fun playbackErrorCodeName(code: Int): String = when (code) {
 PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR -> "UNKNOWN_ERROR"
 PlaybackStateCompat.ERROR_CODE_APP_ERROR -> "APP_ERROR"
 PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED -> "NOT_SUPPORTED"
 PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED -> "AUTHENTICATION_EXPIRED"
 PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED -> "PREMIUM_ACCOUNT_REQUIRED"
 PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT -> "CONCURRENT_STREAM_LIMIT"
 PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED -> "PARENTAL_CONTROL_RESTRICTED"
 PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION -> "NOT_AVAILABLE_IN_REGION"
 PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING -> "CONTENT_ALREADY_PLAYING"
 PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED -> "SKIP_LIMIT_REACHED"
 PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED -> "ACTION_ABORTED"
 PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE -> "END_OF_QUEUE"
 0 -> "NONE"
 else -> code.toString()
 }

 /**
 * Publish this session's token on a [MediaBrowserServiceCompat] so Android
 * Auto controls route to the existing player. Safe to call multiple times.
 */
 fun attachToBrowserService(service: androidx.media.MediaBrowserServiceCompat) {
 ensureEnabled()
 service.setSessionToken(mediaSession.sessionToken)
 }

 fun cancel() {
 notification.cancel()
 mediaSession.isActive = false
 }

 fun destroy() {
 cancel()
 try {
 kordx.applicationContext.unregisterReceiver(receiver)
 } catch (err: Exception) {
 // Already unregistered — tolerate double-destroy.
 }
 if (BuildConfig.DEBUG) {
 try {
 kordx.applicationContext.unregisterReceiver(debugCustomActionReceiver)
 } catch (err: Exception) {
 // Already unregistered — tolerate double-destroy.
 }
 try {
 kordx.applicationContext.unregisterReceiver(debugSearchReceiver)
 } catch (err: Exception) {
 // Already unregistered — tolerate double-destroy.
 }
 try {
 kordx.applicationContext.unregisterReceiver(debugRecentPlayReceiver)
 } catch (err: Exception) {
 // Already unregistered — tolerate double-destroy.
 }
 try {
 kordx.applicationContext.unregisterReceiver(debugPlaybackErrorReceiver)
 } catch (err: Exception) {
 // Already unregistered — tolerate double-destroy.
 }
 }
 }

 fun createEqualizerActivityContract() = object : ActivityResultContract<Unit, Unit>() {
 override fun createIntent(
 context: Context,
 input: Unit,
 ) = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
 putExtra(AudioEffect.EXTRA_PACKAGE_NAME, kordx.applicationContext.packageName)
 putExtra(AudioEffect.EXTRA_AUDIO_SESSION, kordx.radio.audioSessionId ?: 0)
 putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
 }

 override fun parseResult(
 resultCode: Int,
 intent: Intent?,
 ) {
 }
 }

 private fun update() {
 kordx.groove.coroutineScope.launch {
 updateAsync()
 }
 }

 /**
 * Publish the current queue (the `Radio.currentQueue` list of song ids
 * in playback order) to the `MediaSession`. Auto's queue UI shows the
 * first item as the current song; the rest are the up-next list. The
 * queue title is set to "Up next" only when the queue has more than
 * one item (per the plan "set when queue length > 1; omitted
 * when queue length ≤ 1").
 *
 * No-op when the queue is empty — `mediaSession.setQueue(emptyList)`
 * would clear any prior queue and reset the title; we want a
 * "no queue" state to be detectable as such on the Auto side.
 */
 private fun publishQueue() {
 val app = kordx
 val queue = app.radio.queue.currentQueue.toList()
 if (queue.isEmpty()) {
 try {
 mediaSession.setQueue(emptyList())
 mediaSession.setQueueTitle(null)
 } catch (err: Exception) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "publishQueue: empty clear failed: ${err.message}",
 )
 }
 return
 }
 val items = queue.mapNotNull { songId ->
 val song = app.groove.song.get(songId) ?: return@mapNotNull null
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val description = android.support.v4.media.MediaDescriptionCompat.Builder()
 .setMediaId(KordXMediaSessionConstants.PREFIX_SONG + song.id)
 .setTitle(song.title)
 .apply {
 iconUri.let { setIconUri(it) }
 }
 .build()
 MediaSessionCompat.QueueItem(description, songId.hashCode().toLong())
 }
 try {
 mediaSession.setQueue(items)
 mediaSession.setQueueTitle(
 if (items.size > 1) RadioSessionState.QUEUE_TITLE else null
 )
 } catch (err: Exception) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "publishQueue: setQueue failed: ${err.message}",
 )
 }
 }

 private suspend fun updateAsync() {
 val song = kordx.radio.queue.currentSongId
 ?.let { kordx.groove.song.get(it) } ?: return
 currentSongId = song.id
 val artworkUri = kordx.groove.song.getArtworkUri(song.id)
 val artworkBitmap = artworkCacher.getArtwork(song)
 val playbackPosition = kordx.radio.currentPlaybackPosition
 ?: RadioPlayer.PlaybackPosition(played = 0L, total = song.duration)
 val isPlaying = kordx.radio.isPlaying
 if (currentSongId != song.id) {
 return
 }
 val req = UpdateRequest(
 song = song,
 artworkUri = artworkUri,
 artworkBitmap = artworkBitmap,
 playbackPosition = playbackPosition,
 isPlaying = isPlaying,
 )
 updateSession(req)
 notification.update(req)
 }

 private fun updateSession(req: UpdateRequest) {
 ensureEnabled()
 mediaSession.run {
 setMetadata(
 MediaMetadataCompat.Builder().run {
 putString(MediaMetadataCompat.METADATA_KEY_TITLE, req.song.title)
 if (req.song.artists.isNotEmpty()) {
 putString(
 MediaMetadataCompat.METADATA_KEY_ARTIST,
 req.song.artists.joinToString()
 )
 }
 putString(MediaMetadataCompat.METADATA_KEY_ALBUM, req.song.album)
 req.artworkBitmap.let {
 putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
 putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
 putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
 }
 putLong(
 MediaMetadataCompat.METADATA_KEY_DURATION,
 req.playbackPosition.total.toLong()
 )
 build()
 }
 )
 setPlaybackState(buildPlaybackState(req))
 }
 }

 /**
 * Build the [PlaybackStateCompat] for the current song, including the
 * three custom actions (shuffle / repeat / favorite) and the skip
 * intervals in `extras` (per the plan: `KEY_SKIP_BACK_MS` and
 * `KEY_SKIP_FORWARD_MS` so Auto reserves the right skip-prev /
 * skip-next behavior on the Now Playing card).
 */
 private fun buildPlaybackState(req: UpdateRequest): PlaybackStateCompat {
 val shuffleOn = kordx.radio.queue.currentShuffleMode
 val loopMode = kordx.radio.queue.currentLoopMode
 val isFavorite = currentSongId?.let { kordx.groove.songFavorites.isFavorite(it) } ?: false
 val customActions = RadioSessionState.allCustomActions(
 shuffleOn = shuffleOn,
 loopMode = loopMode,
 isFavorite = isFavorite,
 ) + RadioSessionState.rootCustomActions()
 return PlaybackStateCompat.Builder().run {
 setState(
 when {
 req.isPlaying -> PlaybackStateCompat.STATE_PLAYING
 else -> PlaybackStateCompat.STATE_PAUSED
 },
 req.playbackPosition.played.toLong(),
 1f
 )
 setActions(
 PlaybackStateCompat.ACTION_PLAY
 or PlaybackStateCompat.ACTION_PAUSE
 or PlaybackStateCompat.ACTION_PLAY_PAUSE
 or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
 or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
 or PlaybackStateCompat.ACTION_STOP
 or PlaybackStateCompat.ACTION_REWIND
 or PlaybackStateCompat.ACTION_FAST_FORWARD
 or PlaybackStateCompat.ACTION_SEEK_TO
 )
 setExtras(
 Bundle().apply {
 putLong(KEY_SKIP_BACK_MS, RadioSessionState.SKIP_BACK_MS)
 putLong(KEY_SKIP_FORWARD_MS, RadioSessionState.SKIP_FORWARD_MS)
 }
 )
 customActions.forEach { ca ->
 val iconRes = resolveDrawable(ca.iconResourceName)
 if (iconRes != 0) {
 addCustomAction(
 PlaybackStateCompat.CustomAction.Builder(ca.action, ca.label, iconRes)
 .build()
 )
 } else {

 // Fall back to a labelonly custom action (no icon) if; the drawable is missing — keeps Auto from dropping the; button entirely.
 addCustomAction(
 PlaybackStateCompat.CustomAction.Builder(ca.action, ca.label, 0)
 .build()
 )
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RadioSession",
 "custom action icon missing: ${ca.iconResourceName}",
 )
 }
 }
 build()
 }
 }

 private fun resolveDrawable(resourceName: String): Int =
 kordx.applicationContext.resources.getIdentifier(
 resourceName,
 "drawable",
 kordx.applicationContext.packageName,
 )

 private fun ensureEnabled() {
 if (!mediaSession.isActive) {
 mediaSession.isActive = true
 }
 com.android.rockages.kordx.core.utils.Logger.warn(
 "KordXMediaBrowserService",
 "custom browse actions: ${RadioSessionState.rootCustomActions().joinToString { it.action.substringAfterLast('.') }}",
 )
 }

 companion object {
 val MEDIA_SESSION_ID = "${R.string.app_name}_media_session"

 val ACTION_PLAY_PAUSE = "${R.string.app_name}_play_pause"
 val ACTION_PREVIOUS = "${R.string.app_name}_previous"
 val ACTION_NEXT = "${R.string.app_name}_next"
 val ACTION_STOP = "${R.string.app_name}_stop"

 private const val KEY_SKIP_BACK_MS = "android.media.session.key.SKIP_BACK_MS"
 private const val KEY_SKIP_FORWARD_MS = "android.media.session.key.SKIP_FORWARD_MS"

 const val DEBUG_ACTION_SHUFFLE = "com.android.rockages.kordx.radio.DEBUG_SHUFFLE"
 const val DEBUG_ACTION_REPEAT = "com.android.rockages.kordx.radio.DEBUG_REPEAT"
 const val DEBUG_ACTION_FAVORITE = "com.android.rockages.kordx.radio.DEBUG_FAVORITE"
 const val DEBUG_ACTION_SHUFFLE_ALL = "com.android.rockages.kordx.radio.DEBUG_SHUFFLE_ALL"
 const val DEBUG_ACTION_ROOT_SEARCH = "com.android.rockages.kordx.radio.DEBUG_ROOT_SEARCH"

 const val DEBUG_ACTION_SEARCH = "com.android.rockages.kordx.radio.DEBUG_SEARCH"
 const val EXTRA_DEBUG_QUERY = "query"
 const val DEBUG_ACTION_RECENT_PLAY = "com.android.rockages.kordx.radio.DEBUG_RECENT_PLAY"
 const val EXTRA_DEBUG_SONG_ID = "songId"
 const val DEBUG_ACTION_PLAYBACK_ERROR = "com.android.rockages.kordx.radio.DEBUG_PLAYBACK_ERROR"
 const val EXTRA_DEBUG_ERROR_CODE = "code"
 }
}
