package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** JVM unit tests for the icon refinement. Verifies that all 10 icons referenced by the -22 custom action paths + the /8 launcher icon set exist as valid vector drawable XML files in the resource directory, and that the refinements (RTL auto-mirroring, brand-consistent coloring) are present. The 10 icons audited: 1. `ic_shuffle` — Now Playing card custom action 2. `ic_shuffle_active` — Now Playing card custom action 3. `ic_repeat` — Now Playing card custom action 4. `ic_repeat_active` — Now Playing card custom action 5. `ic_repeat_one` — Now Playing card custom action 6. `ic_favorite_border` — Now Playing card custom action 7. `ic_favorite_filled` — Now Playing card custom action 8. `ic_search` — root browse action 9. `ic_launcher_foreground` — /8 adaptive icon foreground 10. `ic_launcher_monochrome` — Android 13+ themed icon The tests are source-level (read the .xml from disk) because the Android resource system is not available on the JVM unit-test classpath (the same constraint documented in the `KordXMediaLibraryServiceTest` + the hardening notes). The runtime behavior is verified on the AVD via the existing -22 `DEBUG_ACTION_*` debug-broadcast receivers, which exercise the same `Resources.getIdentifier(name, "drawable", ...)` code path that the Now Playing card + root browse actions use. */
class IconRefinementTest {

 private fun loadDrawable(name: String): String {

 // The drawable directory is `app/src/main/res/drawable/` for; the default density. Higherdensity variants would live in; `drawablehdpi/`, `drawablexhdpi/`, etc.; for vector; drawables (which is what all 10 icons are), the default; `drawable/` directory is sufficient — vector drawables; scale automatically at all densities.
 val candidates = listOf(
 File("app/src/main/res/drawable/$name.xml"),
 File("../app/src/main/res/drawable/$name.xml"),
 )
 for (candidate in candidates) {
 if (candidate.exists() && candidate.isFile) {
 return candidate.readText()
 }
 }
 throw IllegalStateException(
 "Could not locate drawable $name.xml in app/src/main/res/drawable/"
 )
 }

 private val ACTION_ICONS = listOf(
 "ic_shuffle",
 "ic_shuffle_active",
 "ic_repeat",
 "ic_repeat_active",
 "ic_repeat_one",
 "ic_favorite_border",
 "ic_favorite_filled",
 "ic_search",
 )

 private val LAUNCHER_ICONS = listOf(
 "ic_launcher_foreground",
 "ic_launcher_foreground_light",
 "ic_launcher_monochrome",
 )

 @Test
 fun allActionIconsExist() {
 for (name in ACTION_ICONS) {
 val source = loadDrawable(name)
 assertTrue(
 source.startsWith("<vector"),
 "$name.xml should be a vector drawable (start with <vector)"
 )
 assertTrue(
 source.contains("android:width=\"24dp\""),
 "$name.xml should use the standard 24dp viewport"
 )
 assertTrue(
 source.contains("android:viewportWidth=\"24\""),
 "$name.xml should use the standard 24x24 viewport"
 )
 assertTrue(
 source.contains("<path"),
 "$name.xml should contain at least one <path> element"
 )
 }
 }

 @Test
 fun allActionIconsAreAutoMirrored() {

 // refinement: all action icons set; `android:autoMirrored="true"` so the icon mirrors correctly; for RTL languages (Arabic, Hebrew, etc.). The; `autoMirrored` attribute is a noop for bilaterallysymmetric; shapes (heart, search) but kept for consistency.
 for (name in ACTION_ICONS) {
 val source = loadDrawable(name)
 assertTrue(
 source.contains("android:autoMirrored=\"true\""),
 "$name.xml should declare autoMirrored=\"true\" ( RTL refinement)"
 )
 }
 }

 @Test
 fun allActionIconsUseThemeTint() {

 // refinement: every action icon declares; `android:tint="?attr/colorControlNormal"` so the icon; inherits the theme's `colorControlNormal` (which is; automatically darkonlight or lightondark per the; system theme). The hardcoded `fillColor` is the path's; intrinsic color, which the tint multiplies.
 for (name in ACTION_ICONS) {
 val source = loadDrawable(name)
 assertTrue(
 source.contains("android:tint=\"?attr/colorControlNormal\""),
 "$name.xml should use ?attr/colorControlNormal tint ( theming)"
 )
 }
 }

 @Test
 fun activeStateIconsUseKordXBlue() {

 // refinement: the "active" state icons (shuffle on,; repeat on / repeat one) use the KordX brand blue #FF4285F4
 // (matched to icon.svg's vertical bar) instead of the off-state white #FFFFFFFF.
 // This is the visual signal for "this toggle is on".
 val activeIcons = listOf(
 "ic_shuffle_active",
 "ic_repeat_active",
 "ic_repeat_one",
 )
 for (name in activeIcons) {
 val source = loadDrawable(name)
 assertTrue(
 source.contains("#FF4285F4") || source.contains("#4285F4"),
 "$name.xml should use the KordX brand blue #FF4285F4 for the active state"
 )
 assertTrue(
 !source.contains("#FFFFFFFF") || source.contains("fillColor=\"#FF4285F4\""),
 "$name.xml should NOT use white fill (that's the off-state color)"
 )
 }
 }

 @Test
 fun favoriteFilledUsesMaterialPink() {

 // the filled heart uses Material Design's standard; pink #FFE91E63 (matches the Material 3 "error" color; family) instead of the brand cyan. This is the standard; Material Design convention for "favorited" — theser; expects a pink filled heart universally.
 val source = loadDrawable("ic_favorite_filled")
 assertTrue(
 source.contains("#FFE91E63") || source.contains("#E91E63"),
 "ic_favorite_filled.xml should use the Material Design pink #FFE91E63"
 )
 }

 @Test
 fun offStateIconsUseWhiteFill() {

 // the offstate icons (shuffle off, repeat off,; favorite border, search) use white #FFFFFFFF as the; intrinsic fillColor, which the `?attr/colorControlNormal`; tint multiplies to the theme color.
 val offStateIcons = listOf(
 "ic_shuffle",
 "ic_repeat",
 "ic_favorite_border",
 "ic_search",
 )
 for (name in offStateIcons) {
 val source = loadDrawable(name)
 assertTrue(
 source.contains("#FFFFFFFF") || source.contains("#FFFFFF"),
 "$name.xml should use white #FFFFFFFF as the intrinsic fillColor (off-state)"
 )
 }
 }

 @Test
 fun launcherForegroundHasNewIconGeometry() {

 // The launcher foreground now matches icon.svg: a vertical bar
 // plus a play triangle. Both are drawn with 12dp strokes and
 // scaled 70 % around the viewport center (100,100).
 val source = loadDrawable("ic_launcher_foreground")
 // Vertical bar at x=60 from y=50 to y=150.
 assertTrue(
 source.contains("M60,50L60,150"),
 "ic_launcher_foreground.xml should have the vertical bar (icon.svg geometry)"
 )
 // Play triangle from (60,100) to (140,50) to (140,150).
 assertTrue(
 source.contains("M60,100L140,50L140,150Z"),
 "ic_launcher_foreground.xml should have the play triangle (icon.svg geometry)"
 )
 // Old K geometry is removed.
 assertTrue(
 !source.contains("M90,85"),
 "ic_launcher_foreground.xml should NOT have the old K accent block"
 )
 assertTrue(
 !source.contains("M96,96L104,104"),
 "ic_launcher_foreground.xml should NOT have the old X anchor mark"
 )
 }

 @Test
 fun launcherForegroundLightHasWhiteBackgroundAndBrandColors() {

 // The light variant (ic_launcher_foreground_light) uses a white
 // #FFFFFF background with the same icon.svg geometry as the dark
 // variant. It is used in the Settings page and other light-theme
 // contexts.
 val source = loadDrawable("ic_launcher_foreground_light")
 // Should have the same icon.svg geometry as the dark variant.
 assertTrue(
 source.contains("M60,50L60,150"),
 "ic_launcher_foreground_light.xml should have the vertical bar (icon.svg geometry)"
 )
 assertTrue(
 source.contains("M60,100L140,50L140,150Z"),
 "ic_launcher_foreground_light.xml should have the play triangle (icon.svg geometry)"
 )
 // Should have a white background fill.
 assertTrue(
 source.contains("#FFFFFF"),
 "ic_launcher_foreground_light.xml should use white #FFFFFF background"
 )
 // Should use the new brand blue for the vertical bar.
 assertTrue(
 source.contains("strokeColor=\"#4285F4\""),
 "ic_launcher_foreground_light.xml should use brand blue #4285F4 for the vertical bar"
 )
 // Should use the new brand gold for the play triangle.
 assertTrue(
 source.contains("strokeColor=\"#FBBC05\""),
 "ic_launcher_foreground_light.xml should use brand gold #FBBC05 for the play triangle"
 )
 }

 @Test
 fun launcherMonochromeHasNoHardCodedCyan() {

 // refinement: the monochrome variant is intended; for Android 13+ themed icons, so the path colors are; removed (the system applies the theme color via the; `<adaptiveicon>` background/foreground layer).
 val source = loadDrawable("ic_launcher_monochrome")
 // The K strokes should NOT have hard-coded strokeColor.
 val hasCyanStroke = source.contains("strokeColor=\"#00F0FF\"") ||
 source.contains("strokeColor=\"#FF00F0FF\"")
 assertTrue(
 !hasCyanStroke,
 "ic_launcher_monochrome.xml should NOT have hard-coded cyan strokeColor (themed icon)"
 )
 }

 @Test
 fun iconsReferenceCorrectFileNames() {

 // Regression guard: the icons referenced by the RadioSession; custom actions + root browse actions must; match the file names. If refactor renames a file,; the `RadioSessionStateTest.iconResolution_*` tests will; catch the runtime failure; this test catches the buildtime; failure (the file is missing from the drawable directory).
 val expectedFiles = (ACTION_ICONS + LAUNCHER_ICONS).map { "$it.xml" }
 val drawableDir = File("app/src/main/res/drawable/")
 if (!drawableDir.exists()) {
 // Try the relative-from-test path
 val alt = File("../app/src/main/res/drawable/")
 assertTrue(alt.exists() && alt.isDirectory, "Could not find app/src/main/res/drawable/ directory")
 for (fileName in expectedFiles) {
 val f = File(alt, fileName)
 assertTrue(f.exists(), "Missing drawable file: ${f.absolutePath}")
 }
 } else {
 for (fileName in expectedFiles) {
 val f = File(drawableDir, fileName)
 assertTrue(f.exists(), "Missing drawable file: ${f.absolutePath}")
 }
 }
 }
}
