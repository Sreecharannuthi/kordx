package com.android.rockages.kordx.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM unit tests that pin the visibility of the critical text-on-surface ColorScheme tokens for all 3 KordX themes (light / dark / black). **Important context:** the KordX test classpath sets `unitTests.isReturnDefaultValues = true` (see `app/build.gradle.kts`). This makes unmocked framework methods (e.g. `ColorUtils.blendARGB`) return their default values (0) instead of executing the real implementation. The light theme's `onBackground` was derived from the primary color via the `blendColors` helper (which itself uses `ColorUtils.blendARGB`); on the test classpath this returns 0 (transparent black), which is exactly the same bug the production code exhibited (an invisible text color in the `lightColorScheme()` default). The fix replaces the `blendColors`-derived `onBackground` with a fixed `Color(0xFF1C1B1F)` (Material3 default dark text), which is INDEPENDENT of `ColorUtils.blendARGB` and therefore works correctly on the test classpath. The test asserts: 1. The `onBackground` token for the LIGHT theme is the fixed near-black value `Color(0xFF1C1B1F)` (NOT the derived value from the primary color). 2. The `onBackground` value is the same regardless of the primary color (a light primary no longer produces a near-invisible text color). 3. The `onBackground` token has non-zero alpha (not transparent). 4. The `onSurface` token for the LIGHT theme is the same fixed near-black value. 5. The `onSurfaceVariant` token for the LIGHT theme is the same fixed near-black value. 6. The `onSecondaryContainer` token for the LIGHT theme is the same fixed near-black value. 7. The DARK / BLACK themes' `onBackground` is the fixed off-white value `Color(0xFFE6E1E5)`. 8. The DARK / BLACK themes' `onSurface` / `onSurfaceVariant` / `onSecondaryContainer` are the same fixed off-white value. (The pre-30i values: `onBackground` was `adjustLightness( primaryColor, -0.5f)` which derives a near-black dark color from the primary; `onSurface` was the same; `onSurfaceVariant` was `adjustLightness(primaryColor, -0.45f)` which derives a slightly lighter dark color from the primary; `onSecondaryContainer` was unset, so it fell through to the Material3 default `Color(0xFF4A4458)`. The fix pins all 4 to the same fixed `Color(0xFF1C1B1F)` for the light theme so the text is visible regardless of the user's primary color.) */
class ColorSchemeVisibilityTest {

 private val purple = ThemeColors.Purple // Default KordX primary
 private val cyan = ThemeColors.Cyan
 private val yellow = ThemeColors.Yellow
 private val red = ThemeColors.Red
 private val blue = ThemeColors.Blue

 private val allPrimaryColors = listOf(purple, cyan, yellow, red, blue)


 // LIGHT theme: the 4 critical textonsurface tokens are all; pinned to the same fixed nearblack value, regardless of primary.

 @Test
 fun lightThemeOnBackgroundIsFixedDarkColor() {
 val s = ThemeColorSchemes.createLightColorScheme(purple)
 val onBg = s.onBackground
 assertEquals(
 0xFF1C1B1F.toInt(), onBg.toArgb(),
 "Light theme onBackground must be the fixed near-black " +
 "`Color(0xFF1C1B1F)` post- (was the derived " +
 "`adjustLightness(primaryColor, -0.5f)` pre-30i)."
 )
 }

 @Test
 fun lightThemeOnSurfaceIsFixedDarkColor() {
 val s = ThemeColorSchemes.createLightColorScheme(purple)
 val onSurf = s.onSurface
 assertEquals(
 0xFF1C1B1F.toInt(), onSurf.toArgb(),
 "Light theme onSurface must be the fixed near-black " +
 "`Color(0xFF1C1B1F)` post-."
 )
 }

 @Test
 fun lightThemeOnSurfaceVariantIsFixedDarkColor() {
 val s = ThemeColorSchemes.createLightColorScheme(purple)
 val onSurfVar = s.onSurfaceVariant
 assertEquals(
 0xFF1C1B1F.toInt(), onSurfVar.toArgb(),
 "Light theme onSurfaceVariant must be the fixed near-black " +
 "`Color(0xFF1C1B1F)` post- (was the derived " +
 "`adjustLightness(primaryColor, -0.45f)` pre-30i)."
 )
 }

 @Test
 fun lightThemeOnSecondaryContainerIsFixedDarkColor() {
 val s = ThemeColorSchemes.createLightColorScheme(purple)
 val onSecContainer = s.onSecondaryContainer
 assertEquals(
 0xFF1C1B1F.toInt(), onSecContainer.toArgb(),
 "Light theme onSecondaryContainer must be the fixed near-black " +
 "`Color(0xFF1C1B1F)` post- (was unset pre-30i, " +
 "falling through to the Material3 default `Color(0xFF4A4458)`)."
 )
 }


 // The text color is the same regardless of the primary color; (the whole point of the fix: a light primary no longer; produces a nearinvisible text color).

 @Test
 fun lightThemeOnBackgroundIsSameRegardlessOfPrimary() {
 for (primary in allPrimaryColors) {
 val onBg = ThemeColorSchemes.createLightColorScheme(primary).onBackground
 assertEquals(
 0xFF1C1B1F.toInt(), onBg.toArgb(),
 "Light theme onBackground must be `Color(0xFF1C1B1F)` " +
 "for primary $primary (was the derived " +
 "`adjustLightness(primaryColor, -0.5f)` pre-30i)."
 )
 }
 }

 @Test
 fun lightThemeOnSurfaceIsSameRegardlessOfPrimary() {
 for (primary in allPrimaryColors) {
 val onSurf = ThemeColorSchemes.createLightColorScheme(primary).onSurface
 assertEquals(
 0xFF1C1B1F.toInt(), onSurf.toArgb(),
 "Light theme onSurface must be `Color(0xFF1C1B1F)` " +
 "for primary $primary."
 )
 }
 }

 // ---- The text color is non-transparent (alpha > 0).

 @Test
 fun lightThemeTextColorsAreNotTransparent() {
 for (primary in allPrimaryColors) {
 val s = ThemeColorSchemes.createLightColorScheme(primary)
 assertNonTransparent(s.onBackground, "onBackground", primary)
 assertNonTransparent(s.onSurface, "onSurface", primary)
 assertNonTransparent(s.onSurfaceVariant, "onSurfaceVariant", primary)
 assertNonTransparent(s.onSecondaryContainer, "onSecondaryContainer", primary)
 }
 }


 // DARK / BLACK themes: the 4 critical textonsurface tokens; are all pinned to the same fixed offwhite value.

 @Test
 fun darkThemeOnBackgroundIsFixedLightColor() {
 val s = ThemeColorSchemes.createDarkColorScheme(purple)
 val onBg = s.onBackground
 assertEquals(
 0xFFE6E1E5.toInt(), onBg.toArgb(),
 "Dark theme onBackground must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post- (was `LightContrastColor` " +
 "= pure white `Color(0xFFFFFFFF)` pre-30i)."
 )
 }

 @Test
 fun darkThemeOnSurfaceIsFixedLightColor() {
 val s = ThemeColorSchemes.createDarkColorScheme(purple)
 val onSurf = s.onSurface
 assertEquals(
 0xFFE6E1E5.toInt(), onSurf.toArgb(),
 "Dark theme onSurface must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post-."
 )
 }

 @Test
 fun darkThemeOnSurfaceVariantIsFixedLightColor() {
 val s = ThemeColorSchemes.createDarkColorScheme(purple)
 val onSurfVar = s.onSurfaceVariant
 assertEquals(
 0xFFE6E1E5.toInt(), onSurfVar.toArgb(),
 "Dark theme onSurfaceVariant must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post- (was " +
 "`LightContrastColor` = pure white pre-30i)."
 )
 }

 @Test
 fun darkThemeOnSecondaryContainerIsFixedLightColor() {
 val s = ThemeColorSchemes.createDarkColorScheme(purple)
 val onSecContainer = s.onSecondaryContainer
 assertEquals(
 0xFFE6E1E5.toInt(), onSecContainer.toArgb(),
 "Dark theme onSecondaryContainer must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post- (was `LightContrastColor` " +
 "= pure white pre-30i)."
 )
 }

 @Test
 fun blackThemeOnBackgroundIsFixedLightColor() {
 val s = ThemeColorSchemes.createBlackColorScheme(purple)
 val onBg = s.onBackground
 assertEquals(
 0xFFE6E1E5.toInt(), onBg.toArgb(),
 "Black theme onBackground must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post-."
 )
 }

 @Test
 fun blackThemeOnSurfaceIsFixedLightColor() {
 val s = ThemeColorSchemes.createBlackColorScheme(purple)
 val onSurf = s.onSurface
 assertEquals(
 0xFFE6E1E5.toInt(), onSurf.toArgb(),
 "Black theme onSurface must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post-."
 )
 }

 @Test
 fun blackThemeOnSurfaceVariantIsFixedLightColor() {
 val s = ThemeColorSchemes.createBlackColorScheme(purple)
 val onSurfVar = s.onSurfaceVariant
 assertEquals(
 0xFFE6E1E5.toInt(), onSurfVar.toArgb(),
 "Black theme onSurfaceVariant must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post-."
 )
 }

 @Test
 fun blackThemeOnSecondaryContainerIsFixedLightColor() {
 val s = ThemeColorSchemes.createBlackColorScheme(purple)
 val onSecContainer = s.onSecondaryContainer
 assertEquals(
 0xFFE6E1E5.toInt(), onSecContainer.toArgb(),
 "Black theme onSecondaryContainer must be the fixed off-white " +
 "`Color(0xFFE6E1E5)` post-."
 )
 }

 // ---- Helpers.

 private fun assertNonTransparent(color: Color, name: String, primary: Color) {
 val argb = color.toArgb()
 val alpha = (argb ushr 24) and 0xFF
 assertTrue(
 alpha > 0,
 "Light theme $name for primary $primary has zero alpha " +
 "(argb=$argb) — text would be invisible on the " +
 "background surface."
 )
 }
}
