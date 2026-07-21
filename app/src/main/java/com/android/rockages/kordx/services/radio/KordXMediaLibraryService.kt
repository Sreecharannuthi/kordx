package com.android.rockages.kordx.services.radio

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.android.rockages.kordx.BuildConfig
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.EventUnsubscribeFn
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.services.groove.getSongIds
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.launch

/** AndroidX Media3 [MediaLibraryService] for the KordX browse tree. */
@UnstableApi
class KordXMediaLibraryService : MediaLibraryService() {

 /**
 * The [MediaLibrarySession] built in [onCreate] and released in
 * [onDestroy]. Exposed to the AAOS / Auto framework via
 * [onGetSession] (which returns it). `null` between construction
 * (e.g. before [onCreate] has run) and [onDestroy] (after release).
 */
 private var mediaSession: MediaLibrarySession? = null


 // Debug receivers for AVD validation placeholders (scanning / no_songs / nothing_recent).
 private val debugScanReceiver = object : android.content.BroadcastReceiver() {
 override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
 val updating = intent?.getBooleanExtra(KordXMediaSessionConstants.EXTRA_DEBUG_UPDATING, true) ?: true
 val count = intent?.getIntExtra(KordXMediaSessionConstants.EXTRA_DEBUG_COUNT, -1) ?: -1
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "DEBUG_ACTION_SCAN received: updating=$updating count=$count",
 )
 val app = KordX.instance ?: return
 app.groove.exposer.setIsUpdatingForTest(updating)
 if (count >= 0) {
 app.groove.song.setCountForTest(count)
 }
 }
 }

 private val debugSongListReceiver = object : android.content.BroadcastReceiver() {
 override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
 val csv = intent?.getStringExtra(KordXMediaSessionConstants.EXTRA_DEBUG_SONG_IDS) ?: ""
 val songIds = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "DEBUG_ACTION_SONG_LIST received: ${songIds.size} ids",
 )
 val app = KordX.instance ?: return
 app.groove.song.setAllForTest(songIds)
 }
 }

 override fun onCreate() {
 super.onCreate()
 Log.i(LOG_TAG, "onCreate: building MediaLibrarySession (browse tree + playback)")
 val player = createPlayer()
 val callback = createCallback()

 // `Builder(this, ...)` is the MediaLibraryService-nested builder
 // constructor that takes the owning service (instead of a bare
 // Context). It wires the session into the service's lifecycle so
 // `onGetSession` will be called by the framework on bind.
 mediaSession = MediaLibrarySession.Builder(this, player, callback).build()
 Log.i(
 LOG_TAG,
 "onCreate: MediaLibrarySession built (player=$player, " +
 "callback=${callback::class.java.simpleName}, session=$mediaSession)"
 )

 // Publish the 2 root-level custom browse actions (SHUFFLE_ALL + SEARCH)
 // via `setCustomLayout`. AAOS surfaces these buttons at the root of the browse tree
 // (vs. the Now Playing card, which uses `setMediaButtonPreferences`). The Now Playing
 // card actions are refreshed by [BrowseTreeCallback.publishCurrentPlaybackButtons] in
 // response to player events and custom commands, so no hardcoded initial publish is needed.
 mediaSession?.setCustomLayout(
 RadioSessionState.rootCustomButtons(iconResolver = ::resolveDrawable)
 )
 Log.i(
 LOG_TAG,
 "onCreate: published 2 root custom actions (SHUFFLE_ALL, SEARCH)"
 )

 // Register the 2 debug receivers (DEBUG_ACTION_SCAN + DEBUG_ACTION_SONG_LIST) so the AVD
 // validation gate can exercise the placeholders under the new service.
 if (BuildConfig.DEBUG) {
 registerDebugReceivers()
 }
 }

 override fun onDestroy() {
 Log.i(LOG_TAG, "onDestroy: releasing MediaLibrarySession")
 if (BuildConfig.DEBUG) {
 unregisterDebugReceivers()
 }
 mediaSession?.release()
 mediaSession = null
 super.onDestroy()
 }

 /**
 * Register the 2 debug receivers that drive the AVD validation gate for the
 * placeholders. Uses the `RECEIVER_EXPORTED` flag on API 33+ (Tiramisu) per the
 * platform requirement for non-system broadcasts; falls back to the legacy
 * unspecified flag on older APIs.
 */
 private fun registerDebugReceivers() {
 val ctx = applicationContext
 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
 ctx.registerReceiver(
 debugScanReceiver,
 android.content.IntentFilter().apply {
 addAction(KordXMediaSessionConstants.DEBUG_ACTION_SCAN)
 },
 android.content.Context.RECEIVER_EXPORTED,
 )
 ctx.registerReceiver(
 debugSongListReceiver,
 android.content.IntentFilter().apply {
 addAction(KordXMediaSessionConstants.DEBUG_ACTION_SONG_LIST)
 },
 android.content.Context.RECEIVER_EXPORTED,
 )
 } else {
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 ctx.registerReceiver(
 debugScanReceiver,
 android.content.IntentFilter().apply {
 addAction(KordXMediaSessionConstants.DEBUG_ACTION_SCAN)
 },
 )
 @SuppressLint("UnspecifiedRegisterReceiverFlag")
 ctx.registerReceiver(
 debugSongListReceiver,
 android.content.IntentFilter().apply {
 addAction(KordXMediaSessionConstants.DEBUG_ACTION_SONG_LIST)
 },
 )
 }
 }

 private fun unregisterDebugReceivers() {
 try {
 applicationContext.unregisterReceiver(debugScanReceiver)
 } catch (_: Exception) { /* already unregistered — tolerate */ }
 try {
 applicationContext.unregisterReceiver(debugSongListReceiver)
 } catch (_: Exception) { /* already unregistered — tolerate */ }
 }

 /**
 * Rebuild + republish the 3 Now Playing card custom actions (shuffle / repeat / favorite)
 * via `MediaLibrarySession.setMediaButtonPreferences(List<CommandButton>)`. Called once in
 * [onCreate] (initial publish) and on every relevant `Player.Listener` event. Safe to call when
 * [mediaSession] is `null` (no-op).
 */
 private fun refreshNowPlayingButtons(
 shuffleOn: Boolean,
 loopMode: RadioQueue.LoopMode,
 isFavorite: Boolean,
 ) {
 val session = mediaSession ?: return
 val buttons = RadioSessionState.nowPlayingCardCustomActions(
 shuffleOn = shuffleOn,
 loopMode = loopMode,
 isFavorite = isFavorite,
 iconResolver = ::resolveDrawable,
 )
 session.setMediaButtonPreferences(buttons)
 Log.i(
 LOG_TAG,
 "refreshNowPlayingButtons: published ${buttons.size} buttons " +
 "(shuffleOn=$shuffleOn, loopMode=$loopMode, isFavorite=$isFavorite)"
 )
 }

 /**
 * Resolve a drawable resource name (e.g. `"ic_shuffle"`) to a `@DrawableRes Int` via
 * `Resources.getIdentifier(name, "drawable", packageName)`. Returns `0` (the "no resource"
 * sentinel) if the name doesn't map to a drawable, mirroring the legacy
 * `RadioSession.resolveDrawable` behavior. The icon resolver is passed to
 * [RadioSessionState.nowPlayingCardCustomActions] so the builder stays pure (no `Context`
 * dependency).
 */
 private fun resolveDrawable(name: String): Int =
 resources.getIdentifier(name, "drawable", packageName)

 override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
 Log.i(LOG_TAG, "onGetSession: controllerInfo=$controllerInfo, returning mediaSession=$mediaSession")
 return mediaSession
 }


 /**
 * Build the Media3 [Player] that the new [MediaLibrarySession] wraps.
 *
 * CR3 wires the player directly to the real `KordX.radio`
 * instance so that Android Auto transport controls (play,
 * pause, skip, seek, shuffle, repeat) drive the same playback
 * engine used by the phone UI. The [RadioForwardingPlayer]
 * translates Radio events into Media3 [Player] events and
 * exposes the queue as a Media3 timeline.
 *
 * A defensive no-op fallback is kept for the theoretical case
 * where [KordX.instance] is null at service creation time; in
 * practice [KordXApplication] creates the graph during
 * `Application.onCreate`, so this path should never be hit.
 */
 internal fun createPlayer(): Player {
 val app = KordX.instance
 if (app == null) {
 Log.w(
 LOG_TAG,
 "createPlayer: KordX.instance is null, falling back to no-op player"
 )
 return RadioForwardingPlayer(
 radio = NoOpRadioAdapterTarget(),
 songMediaItemResolver = { _ -> null },
 seekBackDurationMs = SEEK_BACK_MS,
 seekForwardDurationMs = SEEK_FORWARD_MS,
 )
 }
 return RadioForwardingPlayer(
 radio = app.radio,
 songMediaItemResolver = { songId ->
 app.groove.song.get(songId)?.let { song ->
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val albumId = app.groove.album.getIdFromSong(song)
 Media3ItemFactory.playableSongItem(
 song = song,
 iconUri = iconUri,
 albumId = albumId,
 )
 }
 },
 seekBackDurationMs = app.settings.seekBackDuration.value?.toLong() ?: SEEK_BACK_MS,
 seekForwardDurationMs = app.settings.seekForwardDuration.value?.toLong() ?: SEEK_FORWARD_MS,
 )
 }

 /**
 * Build the [MediaLibrarySession.Callback] with real
 * implementations of the 4 browse-tree callbacks
 * ([BrowseTreeCallback.onConnect] / [BrowseTreeCallback.onGetLibraryRoot] /
 * [BrowseTreeCallback.onGetItem] / [BrowseTreeCallback.onGetChildren])
 * and stub defaults for [BrowseTreeCallback.onSearch]
 * and [BrowseTreeCallback.onCustomCommand]. Falls back to a
 * minimal error-only callback if [KordX.instance] is `null` at
 * `onCreate` time (defensive guard — the framework should bind
 * to the service only after the Application is created and
 * `KordX.instance` is populated, so this path is theoretical).
 */
 internal fun createCallback(): MediaLibrarySession.Callback {
 val app = KordX.instance
 if (app == null) {
 Log.w(
 LOG_TAG,
 "createCallback: KordX.instance is null, returning error-only callback " +
 "(the framework should bind to this service only after KordX is initialized)"
 )
 return ErrorOnlyCallback()
 }
 return BrowseTreeCallback(app)
 }

 /**
 * Test seam: set the [mediaSession] field directly. Used by
 * `KordXMediaLibraryServiceTest` to verify the `onGetSession` ↔
 * `mediaSession` field roundtrip without instantiating a real
 * [MediaLibrarySession] (which requires a real `Context` and isn't
 * JVM-testable). Annotated [VisibleForTesting] so production callers
 * don't accidentally swap the session out from under the framework.
 */
 @VisibleForTesting
 internal fun setMediaSessionForTest(session: MediaLibrarySession?) {
 this.mediaSession = session
 }


 // ====================================================================
 // BrowseTreeCallback — the real MediaLibrarySession.Callback implementation.
 // Owns the 4 browse-tree callbacks + 2 stubs (onSearch + onCustomCommand) + the
 // 6 tab builders + 5 drilldown builders + the mediaItemForId lookup.
 // ====================================================================

 /**
 * The real [MediaLibrarySession.Callback]. Implements the 4 browse-tree callbacks
 * by porting the legacy [KordXMediaBrowserService] tab / drill-down builders to Media3
 * `MediaItem` instances via [Media3ItemFactory]. The 2 remaining callbacks
 * ([onSearch] and [onCustomCommand]) keep their skeleton defaults for now.
 *
 * The class is `internal` so the test can instantiate it and assert on its behavior.
 * The class is `public` via the `internal` modifier — `MediaLibrarySession.Builder` is in
 * the same module so it accepts `internal` callbacks.
 */
 internal inner class BrowseTreeCallback(
 private val app: KordX,
 ) : MediaLibrarySession.Callback {


 // Store the most recent search query per controller so the framework's later
 // `onGetSearchResult` calls can look up which query to execute. AAOS issues one
 // `onSearch` per "search initiated by the user" gesture, then any number of
 // `onGetSearchResult` calls (for paging, refresh, etc.). One inflight search per
 // controller is the Media3 contract.

 private val pendingSearches: MutableMap<MediaSession.ControllerInfo, String> =
 java.util.concurrent.ConcurrentHashMap()

 // ---- Real implementations of the 4 browse-tree callbacks.

 /**
 * Accept every connection. The default
 * [MediaSession.ConnectionResult.accept] uses the
 * [MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS]
 * command set, which includes `SESSION_COMMAND_SEARCH` — so
 * the AAOS surface shows the search affordance on the root
 * without the new service having to opt in explicitly. The
 * `onSearch` implementation is the actual handler.
 */
 override fun onConnect(
 session: MediaSession,
 controllerInfo: MediaSession.ControllerInfo,
 ): MediaSession.ConnectionResult {
 Log.i(LOG_TAG, "BrowseTreeCallback.onConnect: controllerInfo=$controllerInfo")
 return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
 }

 /**
 * Return the root [MediaItem] of the library tree. The root
 * is a non-content placeholder (no title — AAOS renders the
 * root as a special "browse" surface, not as a row); its only
 * purpose is to give the framework a `mediaId` ("root") to
 * use as the `parentId` argument of [onGetChildren] when it
 * fetches the top-level tabs.
 *
 * The root is returned synchronously via
 * [Futures.immediateFuture] — theookup is a constant
 * `MediaItem` (no async I/O needed).
 */
 override fun onGetLibraryRoot(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<MediaItem>> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onGetLibraryRoot: controllerInfo=$controllerInfo, params=$params"
 )
 return Futures.immediateFuture(LibraryResult.ofItem(buildRootItem(), /* params = */ null))
 }

 /**
 * Look up a single [MediaItem] by its `mediaId`. Routes to
 * the per-entity builder for `song:` / `album:` /
 * `albumArtist:` / `artist:` (alias) / `genre:` / `playlist:`
 * prefixed ids, plus the 6 root-tab ids. Unknown ids return
 * [LibraryResult.ofError] so the framework surfaces a "not
 * found" error to the controller.
 *
 * Returned synchronously via [Futures.immediateFuture] — the
 * lookup is a pure in-memory map check (no DB I/O).
 */
 override fun onGetItem(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 mediaId: String,
 ): ListenableFuture<LibraryResult<MediaItem>> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onGetItem: controllerInfo=$controllerInfo, mediaId=$mediaId"
 )
 val item = mediaItemForId(mediaId)
 return if (item != null) {
 Futures.immediateFuture(LibraryResult.ofItem(item, /* params = */ null))
 } else {
 Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
 }
 }

 /**
 * Return the children of a browsable `parentId`. The
 * implementation matches the legacy [KordXMediaBrowserService]'s
 * `onLoadChildren` 1:1 — the 6 root tabs under
 * [KordXMediaSessionConstants.ID_ROOT] + the 5 drill-downs
 * (album / albumArtist / artist / genre / playlist → songs)
 * via the parallel [Media3ItemFactory] builders.
 *
 * The work is dispatched on [KordX.groove]'s
 * `coroutineScope` (which is `Dispatchers.Default` per
 * `Groove.kt`) and the result is delivered via a
 * [SettableFuture] — the caller returns immediately
 * (the AAOS framework awaits the future on the controller
 * side). Empty / scan / error placeholders are deferred to the
 * placeholder builders.
 */
 override fun onGetChildren(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 parentId: String,
 page: Int,
 pageSize: Int,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onGetChildren (browse tree): " +
 "controllerInfo=$controllerInfo, parentId=$parentId, " +
 "page=$page, pageSize=$pageSize, params=$params"
 )
 val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
 app.groove.coroutineScope.launch {
 val items = try {
 buildChildren(parentId)
 } catch (err: Exception) {
 Logger.error(LOG_TAG, "onGetChildren failed for $parentId", err)
 emptyList<MediaItem>()
 }
 Logger.warn(
 LOG_TAG,
 "onGetChildren: parentId=$parentId count=${items.size} " +
 "ex=[${items.take(3).joinToString { it.mediaId }}...]"
 )
 future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
 }
 return future
 }

 // ---- Real onSearch + onGetSearchResult.

 /**
 * Media3 1.7.1's `onSearch` is the "acknowledge the query" callback. It returns
 * `LibraryResult<Void>` (NOT a list of results; the results come from
 * [onGetSearchResult] below). The implementation records the in-flight search in
 * [pendingSearches] so the framework's later `onGetSearchResult` calls can look up
 * the query to execute.
 */
 override fun onSearch(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 query: String,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<Void>> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onSearch: controllerInfo=$controllerInfo, " +
 "query='$query', params=$params"
 )

 // Atomically replace any prior query from the same; controller (the framework guarantees one inflight; search per controller at a time).
 pendingSearches[controllerInfo] = query
 return Futures.immediateFuture(LibraryResult.ofVoid())
 }

 /**
 * The actual search results callback. The framework calls this after [onSearch] has
 * acknowledged the query, and may call it again for paging / refresh. The matching
 * logic is delegated to the pure [KordXSearch.search] helper so the routing logic
 * (empty → random sample, non-empty → fuzzy top-N, no-match → empty list) is the same
 * as the legacy [KordXMediaBrowserService.onSearch] path.
 *
 * Returned via [SettableFuture] resolved on [KordX.groove]'s coroutine scope. The plan:
 * "Get `app.groove.song.all.value`. Call [KordXSearch.search] with the lookup callback.
 * Map each result id to a [MediaItem] via [Media3ItemFactory.playableSongItem]."
 *
 * The pending query is looked up from [pendingSearches]; if AAOS calls
 * `onGetSearchResult` without a prior `onSearch` (the framework should never do this,
 * but the contract is loose), we fall back to the query passed in via the `query`
 * parameter.
 */
 override fun onGetSearchResult(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 query: String,
 page: Int,
 pageSize: Int,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onGetSearchResult: " +
 "controllerInfo=$controllerInfo, query='$query', " +
 "page=$page, pageSize=$pageSize, params=$params"
 )
 val effectiveQuery = pendingSearches[controllerInfo] ?: query
 val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
 app.groove.coroutineScope.launch {
 val items = try {
 val allSongIds = app.groove.song.all.value
 val matchingIds = KordXSearch.search(
 query = effectiveQuery,
 songIds = allSongIds,
 lookup = { id ->
 app.groove.song.get(id)?.let { KordXSearch.songSearchText(it) } ?: ""
 },
 )
 Logger.warn(
 LOG_TAG,
 "onGetSearchResult: query='$effectiveQuery' -> " +
 "${matchingIds.size} results (of ${allSongIds.size} songs)"
 )
 matchingIds.mapNotNull { id -> buildSearchResultItem(id) }
 } catch (err: Exception) {
 Logger.error(
 LOG_TAG,
 "onGetSearchResult failed for query='$effectiveQuery'",
 err,
 )
 emptyList<MediaItem>()
 }
 future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
 }
 return future
 }

 /**
 * Build a Media3 playable [MediaItem] for a single song id returned by [KordXSearch.search].
 * Mirrors the legacy [KordXMediaBrowserService.buildSearchResultItem] helper: subtitle is
 * the per-entity `songSubtitle`, the description is the per-entity `descriptionForSong`,
 * the iconUri comes from the artwork cache, and the extras follow the Song display contract
 * (`DURATION_MS` / `TRACK_NUMBER` / `YEAR` / `ALBUM_ID` / `ARTIST`).
 *
 * Returns `null` when the id no longer maps to a Song (race against a parallel library
 * update); the caller filters via `mapNotNull`.
 */
 private fun buildSearchResultItem(id: String): MediaItem? {
 val song = app.groove.song.get(id) ?: return null
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val albumId = app.groove.album.getIdFromSong(song)
 return Media3ItemFactory.playableSongItem(
 song = song,
 iconUri = iconUri,
 albumId = albumId,
 )
 }


 // onCustomCommand — the real SHUFFLE_ALL + SEARCH custom-command dispatch (plus
 // SHUFFLE / REPEAT / FAVORITE dispatched from the Now Playing card actions wired above).
 // The action strings are the same `RadioSessionState.ACTION_*` constants the legacy
 // `RadioSession.handleCustomAction` uses; the dispatch logic mirrors the legacy handler
 // 1:1, with the radio state mutation delegated to the live `KordX.radio` instance.

 override fun onCustomCommand(
 session: MediaSession,
 controllerInfo: MediaSession.ControllerInfo,
 customCommand: SessionCommand,
 args: android.os.Bundle,
 ): ListenableFuture<SessionResult> {
 val action = customCommand.customAction
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onCustomCommand: " +
 "controllerInfo=$controllerInfo, action='$action'"
 )
 when (action) {
 RadioSessionState.ACTION_SHUFFLE_ALL -> {
 handleShuffleAll()
 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
 }
 RadioSessionState.ACTION_SEARCH -> {
 Log.i(
 LOG_TAG,
 "BrowseTreeCallback.onCustomCommand: SEARCH received, " +
 "letting AAOS show its native search bar"
 )
 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
 }

 // The 3 Now Playing card actions are also routed here in the Media3 model — the
 // `setMediaButtonPreferences` buttons dispatch through the same `onCustomCommand` path.
 // The handlers match the legacy `RadioSession.handleCustomAction` contract 1:1.
 RadioSessionState.ACTION_SHUFFLE -> {
 handleShuffleToggle()
 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
 }
 RadioSessionState.ACTION_REPEAT -> {
 handleRepeatToggle()
 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
 }
 RadioSessionState.ACTION_FAVORITE -> {
 handleFavoriteToggle()
 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
 }
 else -> {
 Log.w(
 LOG_TAG,
 "BrowseTreeCallback.onCustomCommand: " +
 "unhandled action='$action', returning ERROR_NOT_SUPPORTED"
 )
 return Futures.immediateFuture(
 SessionResult(SessionError.ERROR_NOT_SUPPORTED)
 )
 }
 }
 }


 // The 3 Now Playing card action handlers mirror the legacy `RadioSession.handleCustomAction`
 // contract for `ACTION_SHUFFLE` / `ACTION_REPEAT` / `ACTION_FAVORITE`. Each handler
 // delegates to the live `app.radio` / `app.groove` state and republishes the Now Playing
 // card buttons via `refreshNowPlayingButtons` so the AAOS surface reflects the new state immediately.

 private fun handleShuffleToggle() {
 val before = app.radio.queue.currentShuffleMode
 app.radio.queue.toggleShuffleMode()
 val after = app.radio.queue.currentShuffleMode
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleShuffleToggle: shuffle $before -> $after"
 )
 publishCurrentPlaybackButtons()
 }

 private fun handleRepeatToggle() {
 val before = app.radio.queue.currentLoopMode
 app.radio.queue.toggleLoopMode()
 val after = app.radio.queue.currentLoopMode
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleRepeatToggle: loop $before -> $after"
 )
 publishCurrentPlaybackButtons()
 }

 private fun handleFavoriteToggle() {
 val songId = app.radio.queue.currentSongId
 if (songId == null) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleFavoriteToggle: ignored — no current song"
 )
 return
 }
 app.groove.songFavorites.toggle(songId)
 val after = app.groove.songFavorites.isFavorite(songId)
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleFavoriteToggle: favorite $after for songId=$songId"
 )
 publishCurrentPlaybackButtons()
 }

 /**
 * CR3 — the `SHUFFLE_ALL` action handler. Uses the public
 * [RadioShorty.playQueue] API (with `shuffle = true` and
 * `autostart = false`) instead of writing directly to the
 * queue's internal fields. This avoids the `Radio.play()` stale-id
 * recursion bug and keeps the queue invariants consistent.
 * Playback starts on the user's explicit play action, which is
 * the standard music-app pattern for "shuffle all".
 */
 private fun handleShuffleAll() {
 val allSongIds = app.groove.song.all.value
 if (allSongIds.isEmpty()) {
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleShuffleAll: ignored — library is empty"
 )
 return
 }
 app.radio.shorty.playQueue(
 songIds = allSongIds,
 options = Radio.PlayOptions(autostart = false),
 shuffle = true,
 )
 com.android.rockages.kordx.core.utils.Logger.warn(
 LOG_TAG,
 "handleShuffleAll: ${allSongIds.size} songs queued in random order"
 )
 publishCurrentPlaybackButtons()
 }

 /**
 * Re-publish the 3 Now Playing card custom actions using the *current* `app.radio` /
 * `app.groove` state. Called by the 5 custom-action handlers after they mutate the state,
 * so AAOS / Auto sees the updated shuffle / loop / favorite icons immediately. Inlined
 * here (vs. delegating to the outer's [refreshNowPlayingButtons]) because [BrowseTreeCallback]
 * is a nested class (not an `inner` class) — it has no implicit reference to the outer
 * `KordXMediaLibraryService` instance. The `mediaSession` reference is the one field that
 * the inner class needs but the outer's `mediaSession` is private; the
 * [KordXMediaLibraryService.getMediaSessionForCallback] helper exposes it (read-only).
 */
 private fun publishCurrentPlaybackButtons() {
 val songId = app.radio.queue.currentSongId
 val isFavorite = songId?.let { app.groove.songFavorites.isFavorite(it) } ?: false
 refreshNowPlayingButtons(
 shuffleOn = app.radio.queue.currentShuffleMode,
 loopMode = app.radio.queue.currentLoopMode,
 isFavorite = isFavorite,
 )
 }


 // ====================================================================; Browse tree builders — ported from KordXMediaBrowserService.; ====================================================================

 /**
 * Build the root [MediaItem] (synchronous — used by
 * [onGetLibraryRoot]). The root is a non-content placeholder
 * with `mediaId = "root"`, `isBrowsable = true`, no title /
 * subtitle / icon. The framework treats the root as the
 * "browse top" — its children (the 6 tabs) come from
 * [buildChildren] when the framework calls
 * [onGetChildren] with `parentId = "root"`.
 */
 private fun buildRootItem(): MediaItem = Media3ItemFactory.browsable(
 id = KordXMediaSessionConstants.ID_ROOT,
 title = "",
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 )

 /**
 * Dispatch the 6 root tabs + 5 drill-downs based on [parentId]. Empty / scan / error
 * placeholders are handled by the placeholder helper below.
 */
 private fun buildChildren(parentId: String): List<MediaItem> {
 return when {
 parentId == KordXMediaSessionConstants.ID_ROOT -> buildRootTabs()
 parentId == KordXMediaSessionConstants.ID_TAB_SONGS -> buildSongsTab()
 parentId == KordXMediaSessionConstants.ID_TAB_ALBUMS -> buildAlbumsTab()
 parentId == KordXMediaSessionConstants.ID_TAB_ARTISTS -> buildArtistsTab()
 parentId == KordXMediaSessionConstants.ID_TAB_GENRES -> buildGenresTab()
 parentId == KordXMediaSessionConstants.ID_TAB_PLAYLISTS -> buildPlaylistsTab()
 parentId == KordXMediaSessionConstants.ID_TAB_RECENT -> buildRecentPlaysTab()
 parentId.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM) ->
 buildSongsForAlbum(parentId.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM))
 parentId.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST) ->
 buildSongsForAlbumArtist(
 parentId.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST)
 )
 parentId.startsWith(KordXMediaSessionConstants.PREFIX_ARTIST) ->
 // `artist:` is a deprecated alias for `albumArtist:`.
 buildSongsForAlbumArtist(
 parentId.removePrefix(KordXMediaSessionConstants.PREFIX_ARTIST)
 )
 parentId.startsWith(KordXMediaSessionConstants.PREFIX_GENRE) ->
 buildSongsForGenre(parentId.removePrefix(KordXMediaSessionConstants.PREFIX_GENRE))
 parentId.startsWith(KordXMediaSessionConstants.PREFIX_PLAYLIST) ->
 buildSongsForPlaylist(
 parentId.removePrefix(KordXMediaSessionConstants.PREFIX_PLAYLIST)
 )
 else -> emptyList()
 }
 }

 // ---------- Root tabs (≤4 browsable per Android Auto root hints) ----------

 private fun buildRootTabs(): List<MediaItem> {

 // Short-circuit to a single `nonPlayableItem(EMPTY_REASON_NO_SONGS, ...)` when the
 // library has zero songs. Mirrors the legacy [KordXMediaBrowserService.buildRootTabs] behavior.
 if (app.groove.song.count() == 0) {
 Logger.warn(
 LOG_TAG,
 "buildRootTabs: empty library (no_songs placeholder)",
 )
 return listOf(
 placeholderItem(
 reason = KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS,
 title = app.t.NoMusicYet,
 subtitle = app.t.AddMediaFolders,
 )
 )
 }
 return listOf(
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_SONGS,
 title = app.t.Songs,
 subtitle = app.t.Songs,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras = Media3ItemFactory.songsTabExtras(),
 ),
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_ALBUMS,
 title = app.t.Albums,
 subtitle = app.t.Albums,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
 extras = Media3ItemFactory.albumsTabExtras(),
 ),
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_ARTISTS,
 title = app.t.AlbumArtists,
 subtitle = app.t.AlbumArtists,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
 extras = Media3ItemFactory.artistsTabExtras(),
 ),
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_GENRES,
 title = app.t.Genres,
 subtitle = app.t.Genres,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
 extras = Media3ItemFactory.genresTabExtras(),
 ),
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_PLAYLISTS,
 title = app.t.Playlists,
 subtitle = app.t.Playlists,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
 extras = Media3ItemFactory.playlistsTabExtras(),
 ),
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_RECENT,
 title = app.t.RecentlyPlayed,
 subtitle = app.t.RecentlyPlayed,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras = Media3ItemFactory.recentTabExtras(),
 ),
 )
 }

 // ---------- Tab content ----------

 private fun buildSongsTab(): List<MediaItem> {

 // Short-circuit to a single `nonPlayableItem(EMPTY_REASON_SCANNING, ...)` when a scan is in
 // progress (`app.groove.song.isUpdating.value` is true). AAOS shows the live count via the
 // `EXTRA_KEY_SCAN_COUNT` int on the placeholder's extras. Mirrors the legacy
 // [KordXMediaBrowserService.buildSongsTab] behavior.
 if (app.groove.song.isUpdating.value) {
 val count = app.groove.song.count.value
 Logger.warn(
 LOG_TAG,
 "buildSongsTab: scanning placeholder (count=$count)",
 )
 return listOf(
 placeholderItem(
 reason = KordXMediaSessionConstants.EMPTY_REASON_SCANNING,
 count = count,
 title = app.t.Scanning,
 subtitle = app.t.XSongs(count.toString()),
 )
 )
 }
 val songIds = app.groove.song.all.value
 return songIdsToPlayableItems(songIds)
 }

 private fun buildAlbumsTab(): List<MediaItem> {
 val albumIds = app.groove.album.all.value
 val albums = app.groove.album.get(albumIds)
 return albums.map { album ->
 val iconUri = app.groove.album.getArtworkUri(album.id)
 Media3ItemFactory.browsableAlbum(
 id = KordXMediaSessionConstants.PREFIX_ALBUM + album.id,
 album = album,
 iconUri = iconUri,
 )
 }
 }

 private fun buildArtistsTab(): List<MediaItem> {
 val artistNames = app.groove.albumArtist.all.value
 val artists = app.groove.albumArtist.get(artistNames)
 return artists.map { albumArtist ->
 val iconUri = app.groove.albumArtist.getArtworkUri(albumArtist.name)
 Media3ItemFactory.browsableAlbumArtist(
 id = KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST + albumArtist.name,
 albumArtist = albumArtist,
 iconUri = iconUri,
 )
 }
 }

 private fun buildGenresTab(): List<MediaItem> {
 val genreNames = app.groove.genre.all.value
 return genreNames.mapNotNull { name ->
 app.groove.genre.get(name)?.let { genre ->
 Media3ItemFactory.browsableGenre(
 id = KordXMediaSessionConstants.PREFIX_GENRE + genre.name,
 genre = genre,
 )
 }
 }
 }

 private fun buildPlaylistsTab(): List<MediaItem> {
 val playlistIds = app.groove.playlist.all.value
 val playlists = app.groove.playlist.get(playlistIds)
 return playlists.map { playlist ->
 Media3ItemFactory.browsablePlaylist(
 id = KordXMediaSessionConstants.PREFIX_PLAYLIST + playlist.id,
 playlist = playlist,
 )
 }
 }

 /**
 * The "Recently played" root tab. Mirrors the legacy
 * [KordXMediaBrowserService.buildRecentPlaysTab] .
 * Reads the in-memory cache from
 * [com.android.rockages.kordx.services.groove.repositories.RecentPlaysRepository]
 * (most-recent-first, capped at the repository's default
 * limit). Each surviving song id is mapped to a playable
 * `MediaItem` with the Song display contract plus
 * a `PLAYED_AT` extra carrying the per-row timestamp so
 * AAOS can render an "X minutes ago" hint.
 *
 * The empty-cache path (no plays yet) is handled by the placeholder helper below.
 */
 private fun buildRecentPlaysTab(): List<MediaItem> {

 // Short-circuit to a single `nonPlayableItem("Nothing played yet", ...)` when the recently-played
 // cache is empty (no plays yet, or post `pm clear`). Mirrors the legacy
 // [KordXMediaBrowserService.buildRecentPlaysTab] behavior.
 if (app.groove.song.count() == 0) {
 Logger.warn(
 LOG_TAG,
 "buildRecentPlaysTab: empty library (no_songs placeholder)",
 )
 return listOf(
 placeholderItem(
 reason = KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS,
 title = app.t.NoMusicYet,
 subtitle = app.t.AddMediaFolders,
 )
 )
 }
 val songIds = app.groove.recentPlays.recentSongIds()
 if (songIds.isEmpty()) {
 Logger.warn(
 LOG_TAG,
 "buildRecentPlaysTab: empty (no plays yet)",
 )
 return listOf(
 placeholderItem(
 reason = "nothing_recent",
 title = app.t.NothingPlayedYet,
 subtitle = app.t.NothingPlayedYetSubtitle,
 extras = Media3ItemFactory.recentEmptyExtras(),
 )
 )
 }
 val playedAtBySongId = app.groove.recentPlays.entries.value
 .associate { it.songId to it.playedAt }
 val items = songIds.mapNotNull { id ->
 val song = app.groove.song.get(id) ?: return@mapNotNull null
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val albumId = app.groove.album.getIdFromSong(song)
 val extras = Media3ItemFactory.songExtrasWithPlayedAt(
 song = song,
 albumId = albumId,
 playedAt = playedAtBySongId[id] ?: 0L,
 )
 Media3ItemFactory.playableSongItem(
 song = song,
 iconUri = iconUri,
 albumId = albumId,
 extras = extras,
 )
 }
 Logger.warn(
 LOG_TAG,
 "buildRecentPlaysTab: ${items.size} entries (ids=${songIds.take(3)})",
 )
 return items
 }

 // ---------- Drill-down (browsable item -> playable songs) ----------

 private fun buildSongsForAlbum(albumId: String): List<MediaItem> {
 val songIds = app.groove.album.getSongIds(albumId)
 return songIdsToPlayableItems(songIds)
 }

 private fun buildSongsForAlbumArtist(name: String): List<MediaItem> {
 val songIds = app.groove.albumArtist.getSongIds(name)
 return songIdsToPlayableItems(songIds)
 }

 private fun buildSongsForGenre(name: String): List<MediaItem> {
 val songIds = app.groove.genre.getSongIds(name)
 return songIdsToPlayableItems(songIds)
 }

 private fun buildSongsForPlaylist(playlistId: String): List<MediaItem> {
 val songIds = app.groove.playlist.get(playlistId)?.let { it.getSongIds(app) }
 ?: emptyList()
 return songIdsToPlayableItems(songIds)
 }

 // ---------- Placeholder helper ----------

 /**
 * Build a non-browsable / non-playable placeholder [MediaItem] (info-only — neither browsable
 * nor playable) whose [title] / [subtitle] / [extras] describe a transient state of the library
 * or the playback. Three reasons are surfaced (per the legacy
 * [KordXMediaBrowserService.placeholderItem] contract):
 *
 * - [KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS] (`"no_songs"`): the library has zero songs.
 *   Subtitle is the "Add media folders in KordX app settings" hint. Used by [buildRootTabs] when
 *   `app.groove.song.count() == 0` and by [buildRecentPlaysTab] when the library is empty.
 * - [KordXMediaSessionConstants.EMPTY_REASON_SCANNING] (`"scanning"`): a scan is in progress. Subtitle
 *   is the "X songs so far" live count. Used by [buildSongsTab] when `app.groove.song.isUpdating` is
 *   `true`. The `count` argument is layered onto the extras as an `Int` under
 *   [KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT].
 * - [KordXMediaSessionConstants.EMPTY_REASON_ERROR] (`"error"`): a playback error happened. The `count`
 *   argument is ignored for this reason.
 * - `"nothing_recent"`: a custom reason used by
 * [buildRecentPlaysTab] for the "Nothing played yet"
 * hint (no plays yet, or post `pm clear`). Not in the
 * [KordXMediaSessionConstants.EMPTY_REASON_*] constant set
 * because the legacy service treats it as a string
 * literal; the [MediaItemFactory.placeholderExtras]
 * helper accepts any reason string and layers it onto
 * the [KordXMediaSessionConstants.EXTRA_KEY_EMPTY_REASON] extras
 * entry. The 4th reason is consistent with the legacy
 * `KordXMediaBrowserService` behavior.
 *
 * The Media3 `MediaItem` is non-browsable / non-playable
 * (info-only — see [Media3ItemFactory.nonPlayable]) so the
 * user can't tap it to drill in, and tapping it doesn't
 * start playback. AAOS renders it as a non-actionable
 * list row, with the [subtitle] providing the
 * explanation.
 */
 private fun placeholderItem(
 reason: String,
 count: Int? = null,
 title: String? = null,
 subtitle: String? = null,
 extras: Media3ItemFactory.Extras? = null,
 ): MediaItem {
 val resolvedExtras = extras
 ?: Media3ItemFactory.placeholderExtras(reason, count)
 val resolvedTitle = title ?: when (reason) {
 KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS -> "No music yet"
 KordXMediaSessionConstants.EMPTY_REASON_SCANNING -> "Scanning\u2026"
 KordXMediaSessionConstants.EMPTY_REASON_ERROR -> "Playback error"
 "nothing_recent" -> "Nothing played yet"
 else -> "No music yet"
 }
 return Media3ItemFactory.nonPlayable(
 id = "placeholder:$reason",
 title = resolvedTitle,
 subtitle = subtitle,
 extras = resolvedExtras,
 )
 }


 // Shared helper (port of the legacy; KordXMediaBrowserService.songIdsToPlayableItems,; but producing Media3 MediaItem instances).

 /**
 * Map a list of song ids to the corresponding playable
 * Media3 `MediaItem` list, following the Song
 * display contract (title / subtitle / description /
 * iconUri / extras). Used by [buildSongsTab] and the 4
 * drill-down builders (album / albumArtist / genre /
 * playlist → songs). Each song id is looked up in the
 * `app.groove.song` cache; missing ids are silently
 * dropped via [mapNotNull] (the plan
 * "drill-down returns playable songs; songs that no longer
 * map are filtered out").
 */
 private fun songIdsToPlayableItems(songIds: List<String>): List<MediaItem> {
 return songIds.mapNotNull { id ->
 val song = app.groove.song.get(id) ?: return@mapNotNull null
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val albumId = app.groove.album.getIdFromSong(song)
 Media3ItemFactory.playableSongItem(
 song = song,
 iconUri = iconUri,
 albumId = albumId,
 )
 }
 }

 // ---------- onGetItem lookup ----------

 /**
 * Resolve a [mediaId] to a single [MediaItem] (used by
 * [onGetItem]). Returns `null` for unknown ids (the caller
 * then returns [LibraryResult.ofError]). The lookup handles
 * the 5 entity prefixes (`song:` / `album:` /
 * `albumArtist:` / `artist:` (alias) / `genre:` /
 * `playlist:`) + the 6 root-tab ids.
 *
 * The lookup is synchronous (in-memory cache only); the
 * caller wraps the result in [Futures.immediateFuture].
 */
 private fun mediaItemForId(mediaId: String): MediaItem? {
 return when {
 mediaId == KordXMediaSessionConstants.ID_TAB_SONGS ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_SONGS,
 title = app.t.Songs,
 subtitle = app.t.Songs,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras = Media3ItemFactory.songsTabExtras(),
 )
 mediaId == KordXMediaSessionConstants.ID_TAB_ALBUMS ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_ALBUMS,
 title = app.t.Albums,
 subtitle = app.t.Albums,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
 extras = Media3ItemFactory.albumsTabExtras(),
 )
 mediaId == KordXMediaSessionConstants.ID_TAB_ARTISTS ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_ARTISTS,
 title = app.t.AlbumArtists,
 subtitle = app.t.AlbumArtists,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
 extras = Media3ItemFactory.artistsTabExtras(),
 )
 mediaId == KordXMediaSessionConstants.ID_TAB_GENRES ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_GENRES,
 title = app.t.Genres,
 subtitle = app.t.Genres,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
 extras = Media3ItemFactory.genresTabExtras(),
 )
 mediaId == KordXMediaSessionConstants.ID_TAB_PLAYLISTS ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_PLAYLISTS,
 title = app.t.Playlists,
 subtitle = app.t.Playlists,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
 extras = Media3ItemFactory.playlistsTabExtras(),
 )
 mediaId == KordXMediaSessionConstants.ID_TAB_RECENT ->
 Media3ItemFactory.browsableTab(
 id = KordXMediaSessionConstants.ID_TAB_RECENT,
 title = app.t.RecentlyPlayed,
 subtitle = app.t.RecentlyPlayed,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras = Media3ItemFactory.recentTabExtras(),
 )

 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_SONG) -> {
 val songId = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_SONG)
 val song = app.groove.song.get(songId) ?: return null
 val iconUri = app.groove.song.getArtworkUri(song.id)
 val albumId = app.groove.album.getIdFromSong(song)
 Media3ItemFactory.playableSongItem(
 song = song,
 iconUri = iconUri,
 albumId = albumId,
 )
 }

 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM) -> {
 val albumId = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM)
 val album = app.groove.album.get(albumId) ?: return null
 val iconUri = app.groove.album.getArtworkUri(album.id)
 Media3ItemFactory.browsableAlbum(
 id = KordXMediaSessionConstants.PREFIX_ALBUM + album.id,
 album = album,
 iconUri = iconUri,
 )
 }

 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST) -> {
 val name = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST)
 val albumArtist = app.groove.albumArtist.get(name) ?: return null
 val iconUri = app.groove.albumArtist.getArtworkUri(albumArtist.name)
 Media3ItemFactory.browsableAlbumArtist(
 id = KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST + albumArtist.name,
 albumArtist = albumArtist,
 iconUri = iconUri,
 )
 }

 // `artist:` is a deprecated alias for `albumArtist:` .
 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_ARTIST) -> {
 val name = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_ARTIST)
 val albumArtist = app.groove.albumArtist.get(name) ?: return null
 val iconUri = app.groove.albumArtist.getArtworkUri(albumArtist.name)
 Media3ItemFactory.browsableAlbumArtist(
 id = KordXMediaSessionConstants.PREFIX_ALBUM_ARTIST + albumArtist.name,
 albumArtist = albumArtist,
 iconUri = iconUri,
 )
 }

 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_GENRE) -> {
 val name = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_GENRE)
 val genre = app.groove.genre.get(name) ?: return null
 Media3ItemFactory.browsableGenre(
 id = KordXMediaSessionConstants.PREFIX_GENRE + genre.name,
 genre = genre,
 )
 }

 mediaId.startsWith(KordXMediaSessionConstants.PREFIX_PLAYLIST) -> {
 val playlistId = mediaId.removePrefix(KordXMediaSessionConstants.PREFIX_PLAYLIST)
 val playlist = app.groove.playlist.get(playlistId) ?: return null
 Media3ItemFactory.browsablePlaylist(
 id = KordXMediaSessionConstants.PREFIX_PLAYLIST + playlist.id,
 playlist = playlist,
 )
 }

 else -> null
 }
 }
 }


 // ====================================================================
 // ErrorOnlyCallback — defensive fallback for the (theoretical) case where
 // KordX.instance is null at onCreate time. The browse-tree builder depends on KordX
 // state; this callback returns errors for every method so the framework can still bind
 // to the service (it just sees "not supported" responses for everything).
 // ====================================================================

 /**
 * Defensive fallback callback used by [createCallback] when
 * [KordX.instance] is `null` at `onCreate` time. Returns
 * `ERROR_NOT_SUPPORTED` for every browse + custom-command
 * method. This path is theoretical (the framework binds to the
 * service only after the Application is created, by which time
 * `KordX.instance` is populated) but the fallback is kept so
 * the service never crashes during a hot-reload / process-restart
 * race.
 */
 internal class ErrorOnlyCallback : MediaLibrarySession.Callback {
 override fun onConnect(
 session: MediaSession,
 controllerInfo: MediaSession.ControllerInfo,
 ): MediaSession.ConnectionResult {
 return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
 }

 override fun onGetLibraryRoot(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<MediaItem>> =
 Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))

 override fun onGetItem(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 mediaId: String,
 ): ListenableFuture<LibraryResult<MediaItem>> =
 Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))

 override fun onGetChildren(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 parentId: String,
 page: Int,
 pageSize: Int,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
 Futures.immediateFuture(
 LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)
 )

 override fun onSearch(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 query: String,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<Void>> =
 Futures.immediateFuture(LibraryResult.ofVoid())

 override fun onGetSearchResult(
 session: MediaLibrarySession,
 controllerInfo: MediaSession.ControllerInfo,
 query: String,
 page: Int,
 pageSize: Int,
 params: MediaLibraryService.LibraryParams?,
 ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
 Futures.immediateFuture(
 LibraryResult.ofItemList(ImmutableList.of<MediaItem>(), params)
 )

 override fun onCustomCommand(
 session: MediaSession,
 controllerInfo: MediaSession.ControllerInfo,
 customCommand: SessionCommand,
 args: android.os.Bundle,
 ): ListenableFuture<SessionResult> =
 Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
 }


 // ====================================================================
 // NoOpRadioAdapterTarget + NoOpRadioQueueAdapterTarget + NoOpRadioShortyAdapterTarget.
 // Kept as private inner classes so the toplevel createPlayer() can reference them
 // without going through SkeletonCallback (the old skeleton callback is removed in this
 // implementation).
 // ====================================================================

 /**
 * No-op [RadioAdapterTarget] used to construct a real [RadioForwardingPlayer] for the
 * browse-tree [MediaLibraryService].
 *
 * This implementation does not wire the new service into `KordX.radio`; it returns empty /
 * default values for every read and ignores every write — the player will report
 * `STATE_IDLE` and an empty queue. The real [Radio] instance (the same one `RadioSession`
 * uses) is wired in later.
 */
 private class NoOpRadioAdapterTarget : RadioAdapterTarget {
 override val hasPlayer: Boolean = false
 override val isPlaying: Boolean = false
 override val currentPlaybackPosition: RadioPlayer.PlaybackPosition? = null
 override val currentSpeed: Float = 1f
 override val currentPitch: Float = 1f
 override val audioSessionId: Int? = null
 override val queue: RadioQueueAdapterTarget = NoOpRadioQueueAdapterTarget()
 override val shorty: RadioShortyAdapterTarget = NoOpRadioShortyAdapterTarget()

 override fun subscribeToEvents(subscriber: (Radio.Events) -> Unit): EventUnsubscribeFn =

 // Noop unsubscribe. The player never fires an event because; the underlying "radio" is a noop.
 { /* no-op */ }

 override fun seek(positionMs: Long) { /* no-op */ }
 override fun stop() { /* no-op */ }
 override fun setSpeed(speed: Float, persist: Boolean) { /* no-op */ }
 override fun setPitch(pitch: Float, persist: Boolean) { /* no-op */ }
 }

 /**
 * No-op [RadioQueueAdapterTarget] for the skeleton. Returns "no queue, no shuffle, no
 * loop" — the player reports an empty timeline and a `STATE_IDLE` playback state.
 */
 private class NoOpRadioQueueAdapterTarget : RadioQueueAdapterTarget {
 override val currentShuffleMode: Boolean = false
 override val currentLoopMode: RadioQueue.LoopMode = RadioQueue.LoopMode.None
 override val currentSongId: String? = null
 override val currentSongIndex: Int = -1
 override val currentQueue: List<String> = emptyList()
 override fun setLoopMode(mode: RadioQueue.LoopMode) { /* no-op */ }
 override fun setShuffleMode(enabled: Boolean) { /* no-op */ }
 }

 /**
 * No-op [RadioShortyAdapterTarget] for the skeleton. Every transport command is a
 * no-op — the player never advances, never pauses, never seeks.
 */
 private class NoOpRadioShortyAdapterTarget : RadioShortyAdapterTarget {
 override fun playPause() { /* no-op */ }
 override fun seekFromCurrent(offsetSecs: Int) { /* no-op */ }
 override fun previous(): Boolean = false
 override fun skip(): Boolean = false
 }

 companion object {
 /**
 * Logcat tag for the browse-tree [MediaLibraryService]. Matches the 24-char convention
 * used by [KordXMediaBrowserService] and the old skeleton. The pre-API-24 23-char logcat
 * tag limit doesn't apply to this project (the `minSdk` is 29), but staying within the
 * convention makes the logcat output homogeneous.
 */
 private const val LOG_TAG = "KordXMediaLibraryService"

 /**
 * Default seek-back / seek-forward increments passed to the [RadioForwardingPlayer].
 * These match the legacy `kordx.settings.seekBackDuration` (15s) /
 * `kordx.settings.seekForwardDuration` (30s) defaults — the real user values from
 * `KordX.radio.settings` are read and passed through at runtime.
 */
 private const val SEEK_BACK_MS = 15_000L
 private const val SEEK_FORWARD_MS = 30_000L
 }
}
