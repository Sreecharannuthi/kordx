package com.android.rockages.kordx.services.radio

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

internal object RadioSessionState {

 const val ACTION_SHUFFLE = "com.android.rockages.kordx.radio.SHUFFLE"
 const val ACTION_REPEAT = "com.android.rockages.kordx.radio.REPEAT"
 const val ACTION_FAVORITE = "com.android.rockages.kordx.radio.FAVORITE"

 const val ACTION_SHUFFLE_ALL = "com.android.rockages.kordx.radio.SHUFFLE_ALL"
 const val ACTION_SEARCH = "com.android.rockages.kordx.radio.SEARCH"

 const val SKIP_BACK_MS = 30_000L
 const val SKIP_FORWARD_MS = 30_000L
 const val QUEUE_TITLE = "Up next"

 /**
 * A `MediaSessionCompat.QueueItem` worth of data, kept as plain data so
 * the queue builder is JVM-testable (no `Uri` / `Bundle` /
 * `MediaSessionCompat` dependency). The service layer converts the
 * resource-name string into a `R.drawable.*` id via
 * `Resources.getIdentifier`.
 */
 data class RadioQueueItemData(
 val mediaId: String,
 val title: String,
 val subtitle: String?,
 val description: String?,
 val iconResourceName: String?,
 val longExtras: Map<String, Long>,
 val intExtras: Map<String, Int>,
 val stringExtras: Map<String, String>,
 )

 /**
 * A snapshot of the song fields the Now Playing card and the queue list
 * actually need. The service layer produces one of these from a `Song`
 * (which has an Android `Uri`, so the data class itself is JVM-safe).
 */
 data class RadioSongSnapshot(
 val id: String,
 val title: String,
 val durationMs: Long,
 val trackNumber: Int?,
 val albumId: String?,
 val iconResourceName: String?,
 )

 /**
 * Build the queue item for a single song snapshot. Per the plan
 * the [RadioQueueItemData.longExtras] / `intExtras` / `stringExtras`
 * carry `DURATION_MS`, `TRACK_NUMBER`, and `ALBUM_ID` so Auto can
 * render duration / position / album context on the queue row.
 */
 fun radioQueueItemData(
 snapshot: RadioSongSnapshot,
 ): RadioQueueItemData {
 val longExtras = mutableMapOf("DURATION_MS" to snapshot.durationMs)
 val intExtras = mutableMapOf<String, Int>()
 val stringExtras = mutableMapOf<String, String>()
 snapshot.trackNumber?.let { intExtras["TRACK_NUMBER"] = it }
 snapshot.albumId?.let { stringExtras["ALBUM_ID"] = it }
 return RadioQueueItemData(
 mediaId = KordXMediaSessionConstants.PREFIX_SONG + snapshot.id,
 title = snapshot.title,
 subtitle = null,
 description = null,
 iconResourceName = snapshot.iconResourceName,
 longExtras = longExtras,
 intExtras = intExtras,
 stringExtras = stringExtras,
 )
 }

 /**
 * A custom action descriptor (one of shuffle / repeat / favorite) with
 * the `action` string Auto sends back via `onCustomAction`, the
 * drawable resource name (resolved to a `R.drawable.*` id by the
 * service layer), and the visible label.
 */
 data class RadioCustomAction(
 val action: String,
 val iconResourceName: String,
 val label: String,
 )

 fun shuffleAction(shuffleOn: Boolean): RadioCustomAction =
 RadioCustomAction(
 action = ACTION_SHUFFLE,
 iconResourceName = if (shuffleOn) "ic_shuffle_active" else "ic_shuffle",
 label = if (shuffleOn) "Shuffling" else "Shuffle",
 )

 fun repeatAction(loopMode: RadioQueue.LoopMode): RadioCustomAction = when (loopMode) {
 RadioQueue.LoopMode.None ->
 RadioCustomAction(ACTION_REPEAT, "ic_repeat", "Repeat off")
 RadioQueue.LoopMode.Queue ->
 RadioCustomAction(ACTION_REPEAT, "ic_repeat_active", "Repeat all")
 RadioQueue.LoopMode.Song ->
 RadioCustomAction(ACTION_REPEAT, "ic_repeat_one", "Repeat one")
 }

 fun favoriteAction(isFavorite: Boolean): RadioCustomAction =
 RadioCustomAction(
 action = ACTION_FAVORITE,
 iconResourceName = if (isFavorite) "ic_favorite_filled" else "ic_favorite_border",
 label = if (isFavorite) "Remove from favorites" else "Add to favorites",
 )

 /**
 * The three custom actions the Now Playing card exposes, in display
 * order. Each is a pure function of the current shuffle / loop /
 * favorite state — the [RadioSession] looks up the state from the
 * live `Radio` and `Groove` and calls this on every playback update.
 */
 fun allCustomActions(
 shuffleOn: Boolean,
 loopMode: RadioQueue.LoopMode,
 isFavorite: Boolean,
 ): List<RadioCustomAction> = listOf(
 shuffleAction(shuffleOn),
 repeatAction(loopMode),
 favoriteAction(isFavorite),
 )

 /**
 * — Media3-flavored port of [allCustomActions].
 *
 * Builds the 3 [CommandButton]s the new
 * [androidx.media3.session.MediaLibrarySession] publishes to
 * AAOS / Auto via
 * `MediaLibrarySession.setMediaButtonPreferences(List<CommandButton>)`
 * so the Now Playing card renders the same 3 actions (shuffle /
 * repeat / favorite) the legacy `KordXMediaBrowserService` shows
 * via `PlaybackStateCompat.CustomAction`.
 *
 * The [iconResolver] is a `(iconName: String) -> Int` lambda
 * that maps a drawable resource name (the same name strings
 * [shuffleAction] / [repeatAction] / [favoriteAction] return in
 * [RadioCustomAction.iconResourceName]) to a `@DrawableRes Int`
 * suitable for `CommandButton.Builder.setIconResId(Int)`. The
 * service layer wires this to
 * `Resources.getIdentifier(name, "drawable", packageName)` —
 * passing the resolver as a parameter keeps the builder pure
 * (no `Context` / `Resources` dependency) and JVM-testable.
 *
 * Returns the 3 buttons in the same order as [allCustomActions]:
 * shuffle, repeat, favorite. The `SessionCommand.customAction`
 * for each button is the same `ACTION_*` constant the legacy
 * builder uses, so the same `MediaLibrarySession.Callback.onCustomCommand`
 * dispatch handler in [KordXMediaLibraryService] routes both
 * button-paths uniformly.
 *
 * Marked `@UnstableApi` because [CommandButton] and
 * [SessionCommand] are part of Media3's unstable surface.
 */
 @UnstableApi
 fun nowPlayingCardCustomActions(
 shuffleOn: Boolean,
 loopMode: RadioQueue.LoopMode,
 isFavorite: Boolean,
 iconResolver: (iconResourceName: String) -> Int,
 ): List<CommandButton> {
 val shuffle = shuffleAction(shuffleOn)
 val repeat = repeatAction(loopMode)
 val favorite = favoriteAction(isFavorite)

 // Use `Bundle()` (not `Bundle.EMPTY`) for JVM testability: the; static `Bundle.EMPTY` field is `null` in the unittest; android.jar (the stub's static initializers don't run unless; something explicitly triggers them), and Media3's; `SessionCommand(String, Bundle)` constructor does; `new Bundle(checkNotNull(bundle))` which NPEs on a null; input. `Bundle()` is the noarg constructor that the stub; jar mocks to return a default empty Bundle.
 val emptyExtras = Bundle()
 return listOf(
 CommandButton.Builder()
 .setSessionCommand(SessionCommand(shuffle.action, emptyExtras))
 .setCustomIconResId(iconResolver(shuffle.iconResourceName))
 .setDisplayName(shuffle.label)
 .build(),
 CommandButton.Builder()
 .setSessionCommand(SessionCommand(repeat.action, emptyExtras))
 .setCustomIconResId(iconResolver(repeat.iconResourceName))
 .setDisplayName(repeat.label)
 .build(),
 CommandButton.Builder()
 .setSessionCommand(SessionCommand(favorite.action, emptyExtras))
 .setCustomIconResId(iconResolver(favorite.iconResourceName))
 .setDisplayName(favorite.label)
 .build(),
 )
 }

 // -----------------------------------------------------------------------

 /**
 * The "Shuffle all songs" root-level custom browse action. Plays the
 * full library in random order. The action handler is in
 * [RadioSession.handleCustomAction] and calls
 * `RadioShorty.playQueue(allSongIds, shuffle = true)`.
 */
 fun shuffleAllAction(): RadioCustomAction =
 RadioCustomAction(
 action = ACTION_SHUFFLE_ALL,
 iconResourceName = "ic_shuffle",
 label = "Shuffle all songs",
 )

 /**
 * The "Search" root-level custom browse action. AAOS handles the actual
 * voice / text input (this is what the plancalls "delegates to
 * the same code path as `onSearch`"); the action handler in
 * [RadioSession.handleCustomAction] just logs the receipt and lets AAOS
 * show its native search bar. We expose the action so the root browse
 * layout has a visible "Search" affordance, mirroring the standard
 * AAOS music-app pattern.
 */
 fun searchAction(): RadioCustomAction =
 RadioCustomAction(
 action = ACTION_SEARCH,
 iconResourceName = "ic_search",
 label = "Search",
 )

 /**
 * The two root-level custom browse actions, in display order. Unlike
 * [allCustomActions] these don't depend on any live state — they're a
 * fixed list the [RadioSession] publishes once at media-session
 * activation. The AVD validation gate's
 * `adb logcat -d | grep "KordXMediaBrowserService.*(custom|shuffle_all|search)"`
 * looks for the "custom browse actions: shuffle_all, search" log line
 * emitted right after the call to `MediaSession.setCustomLayout()`.
 */
 fun rootCustomActions(): List<RadioCustomAction> = listOf(
 shuffleAllAction(),
 searchAction(),
 )

 /**
 * — Media3-flavored port of [rootCustomActions].
 *
 * Builds the 2 [CommandButton]s the new
 * [androidx.media3.session.MediaLibrarySession] publishes to
 * AAOS / Auto via
 * `MediaLibrarySession.setCustomLayout(List<CommandButton>)` so
 * the root of the browse tree renders the same 2 actions
 * ("Shuffle all songs" + "Search") the legacy
 * `KordXMediaBrowserService` shows via
 * `MediaSessionCompat.setCustomLayout()`. The 2 buttons are
 * static (no live state — both "Shuffle all" and "Search" are
 * always available regardless of playback state).
 *
 * The [iconResolver] is a `(iconName: String) -> Int` lambda
 * that maps a drawable resource name (the same name strings
 * [shuffleAllAction] / [searchAction] return in
 * [RadioCustomAction.iconResourceName]) to a `@DrawableRes Int`
 * suitable for `CommandButton.Builder.setIconResId(Int)`. The
 * service layer wires this to
 * `Resources.getIdentifier(name, "drawable", packageName)` —
 * passing the resolver as a parameter keeps the builder pure
 * (no `Context` / `Resources` dependency) and JVM-testable.
 *
 * The 2 buttons are returned in the same order as
 * [rootCustomActions]: SHUFFLE_ALL first, SEARCH second. The
 * `SessionCommand.customAction` for each button is the same
 * `ACTION_*` constant the legacy builder uses, so the same
 * `MediaLibrarySession.Callback.onCustomCommand` dispatch
 * handler in [KordXMediaLibraryService] routes both button-paths
 * uniformly.
 *
 * Marked `@UnstableApi` because [CommandButton] and
 * [SessionCommand] are part of Media3's unstable surface.
 */
 @UnstableApi
 fun rootCustomButtons(
 iconResolver: (iconResourceName: String) -> Int,
 ): List<CommandButton> {
 val shuffleAll = shuffleAllAction()
 val search = searchAction()

 // Use `Bundle()` (not `Bundle.EMPTY`) for JVM testability; (the same reason as [nowPlayingCardCustomActions] — the; static `Bundle.EMPTY` is `null` in the unittest; android.jar, and the `SessionCommand(String, Bundle)`; constructor NPEs on a null input).
 val emptyExtras = Bundle()
 return listOf(
 CommandButton.Builder()
 .setSessionCommand(SessionCommand(shuffleAll.action, emptyExtras))
 .setCustomIconResId(iconResolver(shuffleAll.iconResourceName))
 .setDisplayName(shuffleAll.label)
 .build(),
 CommandButton.Builder()
 .setSessionCommand(SessionCommand(search.action, emptyExtras))
 .setCustomIconResId(iconResolver(search.iconResourceName))
 .setDisplayName(search.label)
 .build(),
 )
 }
}
