package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MainActivityMediaSearchForwardingTest {

 private fun loadSource(relativePath: String): String {

 // Resolve relative to the project root. The gradle test; classpath runs from the project root (./gradlew :app:test).
 val candidates = listOf(
 File(relativePath),
 File("../$relativePath"),
 )
 for (candidate in candidates) {
 if (candidate.exists() && candidate.isFile) {
 return candidate.readText()
 }
 }
 throw IllegalStateException(
 "Could not locate source file $relativePath in ${System.getProperty("user.dir")}"
 )
 }

 private fun loadMainActivity(): String = loadSource(
 "app/src/main/java/com/android/rockages/kordx/MainActivity.kt"
 )

 // ---- Cold-start: MainActivity.onCreate forwards the intent.

 @Test
 fun mainActivityOnCreateForwardsTheLaunchIntent() {
 val source = loadMainActivity()

 // The `onCreate` body must call `forwardMediaSearchIntent(intent)`; so a coldstart alias launch (e.g. the AVD validation step; `am starta android.intent.action.MEDIA_PLAY_FROM_SEARCHes; query "Moth"`) actually reaches the radio session.; We check both the call is present AND the `intent` parameter; is the activity's `intent` (not a different variable).
 assertTrue(
 source.contains("forwardMediaSearchIntent(intent)"),
 "MainActivity must call `forwardMediaSearchIntent(intent)` " +
 "to route the alias's MEDIA_PLAY_FROM_SEARCH intent to the radio session."
 )
 }

 @Test
 fun mainActivityOnCreateForwardingIsAfterEmitActivityReady() {
 val source = loadMainActivity()

 // The forwarding call must come AFTER `kordx.emitActivityReady()`; so `KordX.instance` is populated when the forward runs (the; forward reads `KordX.instance` via the static `Volatile`; field; a forward before `emitActivityReady` would hit the; nullinstance defensive return and drop the search).
 val emitIdx = source.indexOf("kordx.emitActivityReady()")
 val forwardIdx = source.indexOf("forwardMediaSearchIntent(intent)")
 assertTrue(emitIdx >= 0, "kordx.emitActivityReady() not found in MainActivity")
 assertTrue(forwardIdx >= 0, "forwardMediaSearchIntent(intent) not found in MainActivity")
 assertTrue(
 emitIdx < forwardIdx,
 "forwardMediaSearchIntent(intent) must be called AFTER " +
 "kordx.emitActivityReady() in onCreate so KordX.instance is populated."
 )
 }

 // ---- Warm-start: MainActivity.onNewIntent is overridden.

 @Test
 fun mainActivityOverridesOnNewIntent() {
 val source = loadMainActivity()

 // The override must exist with the right signature; (Android's `ComponentActivity.onNewIntent(intent: Intent)`).
 assertTrue(
 source.contains("override fun onNewIntent(intent: Intent)"),
 "MainActivity must override `onNewIntent(intent: Intent)` " +
 "so warm-start alias launches re-trigger the forwarding."
 )
 }

 @Test
 fun mainActivityOnNewIntentSetsTheIntent() {
 val source = loadMainActivity()

 // The `setIntent(intent)` call must be present in `onNewIntent`; so any subsequent `getIntent()` call (e.g. from a composable; that inspects the launch intent) sees the new intent, not; the original coldstart one.
 val onNewIntentStart = source.indexOf("override fun onNewIntent(intent: Intent)")
 assertTrue(onNewIntentStart >= 0, "onNewIntent override not found")
 val body = source.substring(onNewIntentStart)
 assertTrue(
 body.contains("setIntent(intent)"),
 "MainActivity.onNewIntent must call `setIntent(intent)` " +
 "so subsequent getIntent() calls see the warm-start intent."
 )
 }

 @Test
 fun mainActivityOnNewIntentForwardsTheNewIntent() {
 val source = loadMainActivity()

 // The `forwardMediaSearchIntent(intent)` call must be present; in `onNewIntent` so warmstart alias launches retrigger; the forwarding (the plan "onNewIntent: extract ...; extras ... and forward to handlePlayFromSearch").
 val onNewIntentStart = source.indexOf("override fun onNewIntent(intent: Intent)")
 assertTrue(onNewIntentStart >= 0, "onNewIntent override not found")
 val body = source.substring(onNewIntentStart)
 assertTrue(
 body.contains("forwardMediaSearchIntent(intent)"),
 "MainActivity.onNewIntent must call `forwardMediaSearchIntent(intent)` " +
 "to re-trigger the forwarding on a warm-start alias launch."
 )
 }

 // ---- Forwarding helper: MainActivity.forwardMediaSearchIntent.

 @Test
 fun mainActivityHelperExists() {
 val source = loadMainActivity()

 // The helper must exist (private — not exposed in the public; surface; the call sites are `onCreate` and `onNewIntent`).
 assertTrue(
 source.contains("private fun forwardMediaSearchIntent(intent: Intent?)"),
 "MainActivity must declare `private fun forwardMediaSearchIntent(intent: Intent?)` " +
 "as the single routing helper for the alias's intent."
 )
 }

 @Test
 fun mainActivityHelperFiltersOnMediaPlayFromSearchAction() {
 val source = loadMainActivity()

 // The helper must earlyreturn for any nonMEDIA_PLAY_FROM_SEARCH; action so the standard `MAIN` / `LAUNCHER` launch intent and; any other intents are not touched. We assert both the action; check is present and the right constant is used (the public; `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` constant,; not a hardcoded string — keeps the test aligned with the; SDK's canonical action string).
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)
 assertTrue(
 body.contains("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH"),
 "forwardMediaSearchIntent must compare against " +
 "`MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` " +
 "(the public SDK constant for the AAOS voice-search action)."
 )

 // The noop path: the action comparison must be followed by; a `return` (the earlyreturn for nonmatching actions).
 val actionCheckIdx = body.indexOf("MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH")
 val nextReturnIdx = body.indexOf("return", actionCheckIdx)
 assertTrue(
 nextReturnIdx >= 0,
 "forwardMediaSearchIntent must `return` early when the " +
 "intent action is not MEDIA_PLAY_FROM_SEARCH."
 )
 }

 @Test
 fun mainActivityHelperReadsExtraMediaFocusBundle() {
 val source = loadMainActivity()

 // The helper must read `MediaStore.EXTRA_MEDIA_FOCUS` (a Bundle; holding the typedsearch fields — AAOS's "play the album X"; path). We assert the getBundleExtra call is present and is; routed to the typedsearch composition path.
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)
 assertTrue(
 body.contains("getBundleExtra(MediaStore.EXTRA_MEDIA_FOCUS)"),
 "forwardMediaSearchIntent must read " +
 "`MediaStore.EXTRA_MEDIA_FOCUS` (a Bundle) for typed searches."
 )

 // The composed query must include the typedsearch fields; AAOS sends. We assert the four main field keys are read; (title, album, artist, genre; the playlist key is; SoftDeprecated but still functional and is in the source).
 assertTrue(
 body.contains("MediaStore.EXTRA_MEDIA_TITLE"),
 "forwardMediaSearchIntent must read `MediaStore.EXTRA_MEDIA_TITLE` " +
 "from the FOCUS bundle for typed searches."
 )
 assertTrue(
 body.contains("MediaStore.EXTRA_MEDIA_ALBUM"),
 "forwardMediaSearchIntent must read `MediaStore.EXTRA_MEDIA_ALBUM` " +
 "from the FOCUS bundle for typed searches."
 )
 assertTrue(
 body.contains("MediaStore.EXTRA_MEDIA_ARTIST"),
 "forwardMediaSearchIntent must read `MediaStore.EXTRA_MEDIA_ARTIST` " +
 "from the FOCUS bundle for typed searches."
 )
 assertTrue(
 body.contains("MediaStore.EXTRA_MEDIA_GENRE"),
 "forwardMediaSearchIntent must read `MediaStore.EXTRA_MEDIA_GENRE` " +
 "from the FOCUS bundle for typed searches."
 )
 }

 @Test
 fun mainActivityHelperReadsSearchManagerQuery() {
 val source = loadMainActivity()

 // The helper must read `SearchManager.QUERY` (a String) for; raw text searches. This is the AAOS "play X" / "shuffle; some music" path.
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)
 assertTrue(
 body.contains("getStringExtra(SearchManager.QUERY)"),
 "forwardMediaSearchIntent must read `SearchManager.QUERY` " +
 "for raw text searches (the AAOS voice-search contract)."
 )
 }

 @Test
 fun mainActivityHelperCallsHandlePlayFromSearchOnRadioSession() {
 val source = loadMainActivity()

 // The helper must forward to; `KordX.instance.radio.session.handlePlayFromSearch(query)`.; The static `KordX.instance` access is the liveinstance; pattern the rest of the codebase uses (see; `KordXMediaLibraryService.kt` for the same pattern).
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)
 assertTrue(
 body.contains("KordX.instance"),
 "forwardMediaSearchIntent must read `KordX.instance` " +
 "to route to the live radio session."
 )
 assertTrue(
 body.contains(".radio.session.handlePlayFromSearch("),
 "forwardMediaSearchIntent must call " +
 "`KordX.instance.radio.session.handlePlayFromSearch(...)` " +
 "to route the search query to the existing radio session."
 )
 }

 @Test
 fun mainActivityHelperToleratesNullKordXInstance() {
 val source = loadMainActivity()

 // The helper must tolerate a `null` `KordX.instance` (e.g.; an `am start` race before `KordX` finishes initializing); by logging + returning without crashing. The activity is; the `singleTask` root, so a crash here would blackhole; the framework; the defensive return is intentional.
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)
 // The null-instance check must be present.
 assertTrue(
 body.contains("if (app == null)"),
 "forwardMediaSearchIntent must check `if (app == null)` " +
 "and early-return to tolerate a race with KordX.init."
 )

 // The nullinstance path must log (so the AVD validation gate; can confirm the defensive return fired if KordX is ever; null at the right time).
 val nullInstanceIdx = body.indexOf("if (app == null)")
 val logIdx = body.indexOf("Logger.warn(", nullInstanceIdx)
 assertTrue(
 logIdx >= 0,
 "forwardMediaSearchIntent must log via `Logger.warn(...)` " +
 "when KordX.instance is null so the AVD logcat gate " +
 "can confirm the defensive return fired."
 )
 }

 @Test
 fun mainActivityHelperLogsTheForwardedQuery() {
 val source = loadMainActivity()

 // The helper must log the forwarded query so the AVD; validation gate (`adb logcatd | grep "MainActivity.*MEDIA_PLAY_FROM_SEARCH"`); can confirm the forwarding fired. The checklist:; "AVD: `am starta android.intent.action.MEDIA_PLAY_FROM_SEARCH; es query "Moth"` → logcat shows `onPlayFromSearch: query='Moth'`"; — that log line comes from RadioSession.handlePlayFromSearch; MainActivity's own log line is the complementary gate that; confirms the routing happened.
 val helperStart = source.indexOf("private fun forwardMediaSearchIntent(intent: Intent?)")
 assertTrue(helperStart >= 0, "forwardMediaSearchIntent helper not found")
 val body = source.substring(helperStart)

 // Whitespacetolerant: a regex that matches `Logger.warn(`; followed by any whitespace, then `"MainActivity"`. A future; refactor that reindents the call site must not break this; assertion.
 assertTrue(
 Regex("""Logger\.warn\(\s*"MainActivity"""").containsMatchIn(body),
 "forwardMediaSearchIntent must log via `Logger.warn(\"MainActivity\", ...)` " +
 "the forwarded query so the AVD logcat gate is meaningful."
 )

 // The log must include the literal `MEDIA_PLAY_FROM_SEARCH`; tag so the AVD logcat grep is unambiguous.
 assertTrue(
 body.contains("MEDIA_PLAY_FROM_SEARCH"),
 "forwardMediaSearchIntent's log line must include the " +
 "literal `MEDIA_PLAY_FROM_SEARCH` tag."
 )
 }

 @Test
 fun mainActivityManifestAliasIsUnchanged() {

 // The manifest's `MediaSearchActivity` activityalias is the; trigger for the forwarding — verify the alias still targets; `MainActivity` and still declares the; `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent filter.; ( doesn't change the manifest; this is a regression; guard so manifest edit doesn't accidentally; remove the trigger and silently disable the forwarding.)
 val manifest = loadSource("app/src/main/AndroidManifest.xml")
 assertTrue(
 manifest.contains("android:name=\".MediaSearchActivity\""),
 "AndroidManifest.xml must still declare the `.MediaSearchActivity` activity-alias."
 )
 assertTrue(
 manifest.contains("android:targetActivity=\".MainActivity\""),
 "AndroidManifest.xml's MediaSearchActivity alias must still target `.MainActivity`."
 )
 assertTrue(
 manifest.contains("android.media.action.MEDIA_PLAY_FROM_SEARCH"),
 "AndroidManifest.xml's MediaSearchActivity alias must still " +
 "declare the `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent filter."
 )
 }

 @Test
 fun mainActivityDoesNotShadowTheRadioSessionCallback() {

 // Regression guard: the forwarding is an *additional* routing; surface for the alias, not a replacement for the; MediaSession `onPlayFromSearch` callback that; `RadioSession.start()` wires. The MediaSession callback; is the path AAOS uses when the user is actively in the; Auto UI and presses the voice button there; the alias is; the path used when Auto routes the voice search through; the system media search intent. Both must remain.
 val radioSession = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/RadioSession.kt"
 )
 assertTrue(
 radioSession.contains("override fun onPlayFromSearch("),
 "RadioSession's MediaSession callback `onPlayFromSearch` must remain " +
 "intact — the MainActivity forwarding is an additional surface, " +
 "not a replacement."
 )
 assertTrue(
 radioSession.contains("fun handlePlayFromSearch(query: String?)"),
 "RadioSession's `handlePlayFromSearch(query: String?)` must remain " +
 "intact — it's the shared routing target for both surfaces."
 )

 // The MainActivity forwarding must NOT introduce a duplicate; `handlePlayFromSearch` definition in MainActivity.kt; (the routing must stay in RadioSession, not in MainActivity).
 val mainActivity = loadMainActivity()
 assertFalse(
 mainActivity.contains("fun handlePlayFromSearch("),
 "MainActivity must NOT define a `handlePlayFromSearch` " +
 "function — theouting must stay in RadioSession."
 )
 }

 // ---- Cross-checks (sanity).

 @Test
 fun mainActivityImportForMediaSearchExtrasIsPresent() {
 val source = loadMainActivity()

 // The `android.provider.MediaStore` import must be present; so the `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` /; `MediaStore.EXTRA_MEDIA_FOCUS` / `MediaStore.EXTRA_MEDIA_TITLE`; references resolve. Regression guard: refactor; that drops the import will break the build, but this test; makes the failure mode obvious (the structural test fails; with a clear "import missing" message first).
 assertNotNull(source)
 assertTrue(
 source.contains("import android.provider.MediaStore"),
 "MainActivity must import `android.provider.MediaStore` for " +
 "the INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH / EXTRA_MEDIA_FOCUS constants."
 )
 assertTrue(
 source.contains("import android.app.SearchManager"),
 "MainActivity must import `android.app.SearchManager` for " +
 "the SearchManager.QUERY extra constant."
 )
 }
}
