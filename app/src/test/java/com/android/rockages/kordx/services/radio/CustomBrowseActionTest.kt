package com.android.rockages.kordx.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM unit tests for the root-level custom browse actions built by [RadioSessionState.shuffleAllAction], [RadioSessionState.searchAction], and [RadioSessionState.rootCustomActions]. The builders are pure data — no `KordX`, no `Radio`, no Android `Uri` / `Bundle` / `MediaSessionCompat` — so the whole surface is testable on the JVM without an emulator. The tests pin: - The 2 `ACTION_*` constants match the namespaced values. - The `shuffleAllAction()` builder returns the right `action` / icon / label. - The `searchAction()` builder returns the right `action` / icon / label. - The `rootCustomActions()` list is exactly `[shuffleAll, search]` in that order, and is deterministic (same input → same output). - The hide/show audit: no `KordXMediaLibraryService` source-file path contains the banned terms (Settings, About, Equalizer, Sleep timer, Crossfade, Discord, Reddit, GitHub issue, Update check, Onboarding, Theme picker, Language picker, Cast, Bluetooth picker). The test greps the source file for each banned term and asserts zero matches. */
class CustomBrowseActionTest {

 // ---- Action constant pinning.

 @Test
 fun shuffleAllActionConstantIsNamespaced() {

 // The action string is namespaced under; `com.android.rockages.kordx.radio.` so AAOS intent; filter clash is impossible.
 assertTrue(
 RadioSessionState.ACTION_SHUFFLE_ALL.startsWith("com.android.rockages.kordx.radio."),
 "ACTION_SHUFFLE_ALL should be namespaced, got '${RadioSessionState.ACTION_SHUFFLE_ALL}'",
 )
 }

 @Test
 fun searchActionConstantIsNamespaced() {
 assertTrue(
 RadioSessionState.ACTION_SEARCH.startsWith("com.android.rockages.kordx.radio."),
 "ACTION_SEARCH should be namespaced, got '${RadioSessionState.ACTION_SEARCH}'",
 )
 }

 @Test
 fun actionConstantsAreDistinct() {

 // The two root actions must have distinct action strings — Auto; routes by exact match, so a clash would silently drop one.
 assertNotEquals(
 RadioSessionState.ACTION_SHUFFLE_ALL,
 RadioSessionState.ACTION_SEARCH,
 )
 }

 // ---- Builder pinning.

 @Test
 fun shuffleAllActionBuilderReturnsRightData() {
 val ca = RadioSessionState.shuffleAllAction()
 assertEquals(RadioSessionState.ACTION_SHUFFLE_ALL, ca.action)
 assertEquals("ic_shuffle", ca.iconResourceName)
 assertEquals("Shuffle all songs", ca.label)
 }

 @Test
 fun searchActionBuilderReturnsRightData() {
 val ca = RadioSessionState.searchAction()
 assertEquals(RadioSessionState.ACTION_SEARCH, ca.action)
 assertEquals("ic_search", ca.iconResourceName)
 assertEquals("Search", ca.label)
 }

 // ---- rootCustomActions() pinning.

 @Test
 fun rootCustomActionsHasTwoEntriesInDisplayOrder() {
 val list = RadioSessionState.rootCustomActions()
 assertEquals(2, list.size)
 assertEquals(RadioSessionState.ACTION_SHUFFLE_ALL, list[0].action)
 assertEquals(RadioSessionState.ACTION_SEARCH, list[1].action)
 }

 @Test
 fun rootCustomActionsIsDeterministic() {

 // Two calls in a row produce the same list — important for; `MediaSession.setCustomLayout()` idempotency.
 val first = RadioSessionState.rootCustomActions()
 val second = RadioSessionState.rootCustomActions()
 assertEquals(first, second)
 }

 @Test
 fun rootCustomActionsDoesNotDependOnLiveState() {

 // Unlike `allCustomActions(shuffleOn, loopMode, isFavorite)`,; `rootCustomActions` takes no parameters. Verified by the; empty argument list below — if the signature ever changes to; require state, this test fails.
 val list = RadioSessionState.rootCustomActions()
 assertEquals(2, list.size)
 }

 // ---- Hide / show audit.

 @Test
 fun browserServiceSourceContainsNoBannedTerms() {

 // The plan: "No MediaItem in the browse tree exposes:; Settings, About, Equalizer, Sleep timer, Crossfade,; Community links (Discord / Reddit / GitHub reportissue),; Update check, Onboarding / help, Theme picker, Language; picker, Cast / Bluetooth picker." This test greps the; active mediabrowser service source for the banned; terms and asserts zero matches. As of 26i the active; service is the AndroidX Media3; `KordXMediaLibraryService` (the legacy; `KordXMediaBrowserService` was deleted in the 26i; cutover). The grep is casesensitive on the term; itself but tolerates surrounding word characters; (e.g. "SettingsActivity" is still a match — theanned; term is the substring "Settings").
 //

 // The test reads the source file from disk because the; JVM unittest classpath doesn't include the; `app/src/main/` source tree as compiled classes. JVM; tests run with the working directory as the module root; (i.e. `app/`), so the relative path is `src/main/...`.
 val relativePath = "src/main/java/com/android/rockages/kordx/services/radio/KordXMediaLibraryService.kt"
 val source = try {
 java.io.File(relativePath).readText()
 } catch (err: Exception) {

 // Fallback for when the test runs from the project root; (e.g. some CI configurations). The path is the same; path with the `app/` prefix.
 java.io.File("app/$relativePath").readText()
 }
 val bannedTerms = listOf(
 "Settings",
 "About",
 "Equalizer",
 "Sleep timer",
 "Crossfade",
 "Discord",
 "Reddit",
 "GitHub report-issue",
 "Update check",
 "Onboarding",
 "Theme picker",
 "Language picker",
 "Cast",
 "Bluetooth picker",
 )
 for (term in bannedTerms) {
 assertTrue(
 !source.contains(term),
 "KordXMediaLibraryService.kt contains the banned term '$term' — see the hide/show audit.",
 )
 }
 }
}
