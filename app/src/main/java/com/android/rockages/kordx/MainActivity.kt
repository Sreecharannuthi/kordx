package com.android.rockages.kordx

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.android.rockages.kordx.ui.view.BaseView
import com.android.rockages.kordx.core.utils.Logger

class MainActivity : ComponentActivity() {
 private var gKordX: KordX? = null

 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)

 val ignition: ActivityIgnition by viewModels()
 if (savedInstanceState == null) {
 installSplashScreen().apply {
 setKeepOnScreenCondition { !ignition.ready.value }
 }
 }

 Thread.setDefaultUncaughtExceptionHandler { _, err ->
 Logger.error("MainActivity", "uncaught exception", err)
 ErrorActivity.start(this, err)
 finish()
 }

 val kordx: KordX by viewModels()
 kordx.permission.handle(this)
 gKordX = kordx
 kordx.emitActivityReady()
 attachHandlers()

 enableEdgeToEdge()

 // forward the `MediaSearchActivity` activityalias; extras (the alias targets this activity; its intentfilter; declares `android.media.action.MEDIA_PLAY_FROM_SEARCH`). The; alias is the production entry point for AAOS's voicesearch; button; without this forwarding the framework delivers the; intent to this activity, the activity opens the Compose UI,; and the query is dropped on the floor (the search runs; through `RadioSession.handlePlayFromSearch`, which is only; wired from `MediaSessionCompat.Callback.onPlayFromSearch` in; the Auto path).
 forwardMediaSearchIntent(intent)
 setContent {
 LaunchedEffect(LocalContext.current) {
 ignition.emitReady()
 }
 BaseView(kordx = kordx, activity = this)
 }
 }

 /**
 * — handle an `onNewIntent` delivery from the
 * `MediaSearchActivity` alias when this activity is already
 * running (`launchMode="singleTask"` means a second voice-search
 * press reuses this activity instead of starting a new one).
 * Mirrors the `onCreate` forwarding so warm-start and cold-start
 * alias launches behave identically.
 */
 override fun onNewIntent(intent: Intent) {
 super.onNewIntent(intent)

 // setIntent so any subsequent getIntent() call (e.g. from; a composable that inspects the launch intent) sees the new; intent, not the original coldstart one.
 setIntent(intent)
 forwardMediaSearchIntent(intent)
 }

 /**
 * — forward a `MEDIA_PLAY_FROM_SEARCH` intent's extras
 * to the [com.android.rockages.kordx.services.radio.RadioSession]
 * so the AAOS voice-search button actually plays the requested
 * song. The `MediaSearchActivity` activity-alias targets this
 * activity, so without this forwarding the framework delivers
 * the intent, the activity opens the Compose UI, and the query
 * is dropped on the floor.
 *
 * The AAOS voice-search contract (per the `MediaStore` docs at
 * <https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH>):
 * - `intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`
 * (`"android.media.action.MEDIA_PLAY_FROM_SEARCH"`).
 * - `SearchManager.QUERY` extra holds the raw search text
 * (the AAOS "play X" or "shuffle some music" cases).
 * - `MediaStore.EXTRA_MEDIA_FOCUS` extra (a `Bundle`) holds
 * the typed-search fields (e.g. `artist:Beatles`,
 * `album:Help!`) with keys `EXTRA_MEDIA_TITLE`,
 * `EXTRA_MEDIA_ALBUM`, `EXTRA_MEDIA_ARTIST`,
 * `EXTRA_MEDIA_GENRE`, `EXTRA_MEDIA_PLAYLIST`. The AAOS
 * voice-search action is **one-of** QUERY / FOCUS / neither;
 * the framework contract is "deliver at most one of the
 * two".
 *
 * Behavior:
 * - FOCUS bundle present → compose a `"<title> <album>
 * <artist> <genre> <playlist>"` query from the populated
 * fields (skip blanks so the fuzzy search isn't penalized
 * for empty fields) and forward via `handlePlayFromSearch`.
 * This is the AAOS typed-search path (the user said
 * "play the album X" and AAOS resolved it to `album:X`).
 * - QUERY present → forward as-is. The fuzzy search inside
 * `RadioSession.handlePlayFromSearch` handles the rest.
 * - Neither present (the AAOS "play some music" / "shuffle
 * all" case) → forward `null` (or empty string) which
 * `handlePlayFromSearch` treats as "shuffle all" per the
 * contract.
 * - Any other action → no-op. The standard
 * `android.intent.action.MAIN` / category `LAUNCHER` launch
 * intent and any non-search intents are not touched.
 *
 * Tolerates a `null` `KordX.instance` (e.g. an `am start` race
 * before `KordX` finishes initializing) by logging + returning
 * without crashing the activity. The activity is the
 * `singleTask` root, so a crash here would black-hole the
 * framework; the defensive return is intentional.
 */
 private fun forwardMediaSearchIntent(intent: Intent?) {
 if (intent == null) return
 val action = intent.action ?: return
 if (action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
 return
 }
 val app = KordX.instance
 if (app == null) {
 Logger.warn(
 "MainActivity",
 "forwardMediaSearchIntent: KordX.instance is null, " +
 "dropping MEDIA_PLAY_FROM_SEARCH (action='$action')",
 )
 return
 }

 // Typedsearch path: a Bundle with the artist / album /; title / genre / playlist fields AAOS resolved from a; "play the album X" voice command. Compose a single; search query from the populated fields; the fuzzy; search in `KordXSearch` ranks by combined text.
 val focus = intent.getBundleExtra(MediaStore.EXTRA_MEDIA_FOCUS)
 if (focus != null) {
 val typed = buildList {
 focus.getString(MediaStore.EXTRA_MEDIA_TITLE)?.takeIf { it.isNotBlank() }?.let(::add)
 focus.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.takeIf { it.isNotBlank() }?.let(::add)
 focus.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.takeIf { it.isNotBlank() }?.let(::add)
 focus.getString(MediaStore.EXTRA_MEDIA_GENRE)?.takeIf { it.isNotBlank() }?.let(::add)
 focus.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.takeIf { it.isNotBlank() }?.let(::add)
 }.joinToString(" ")
 Logger.warn(
 "MainActivity",
 "MEDIA_PLAY_FROM_SEARCH: FOCUS -> query='$typed' -> handlePlayFromSearch",
 )
 app.radio.session.handlePlayFromSearch(typed.takeIf { it.isNotEmpty() })
 return
 }
 val query = intent.getStringExtra(SearchManager.QUERY)
 Logger.warn(
 "MainActivity",
 "MEDIA_PLAY_FROM_SEARCH: query='${query.orEmpty()}' -> handlePlayFromSearch",
 )
 app.radio.session.handlePlayFromSearch(query)
 }

 override fun onPause() {
 super.onPause()
 gKordX?.emitActivityPause()
 }

 override fun onDestroy() {
 super.onDestroy()
 gKordX?.emitActivityDestroy()
 }

 private fun attachHandlers() {
 gKordX?.closeApp = {
 finish()
 }
 }
}
