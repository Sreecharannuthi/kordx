package com.android.rockages.kordx.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

@Suppress("ConstPropertyName")
object ThemeColorSchemes {
 private val LightBackgroundColor = ThemeColors.Neutral50
 private val LightSurfaceColor = ThemeColors.Neutral100
 private val LightSurfaceVariantColor = ThemeColors.Neutral200
 private val DarkBackgroundColor = ThemeColors.Neutral900
 private val DarkSurfaceColor = ThemeColors.Neutral900
 private val DarkSurfaceVariantColor = ThemeColors.Neutral800
 private val LightContrastColor = Color.White
 private val BlackContrastColor = Color.Black


 // fixed visible value for the light theme's textonlight; tokens (`onBackground` / `onSurface` / `onSurfaceVariant`). The; default `lightColorScheme()` uses `Color(0xFF1C1B1F)` (very dark; gray) which is visible on every light surface. We pin to that; same nearblack value (with a slight tint of the primary color; for brand consistency, matching the `onBackground` value the; Material3 default would produce for the default Purple primary).; Critically: this is INDEPENDENT of the user's primary color, so; a light primary (e.g. yellow) doesn't produce a nearinvisible; text color. This is the value that the [LocalContentColor]; CompositionLocalProvider in `KordXTheme` also pins, so both; paths agree.
 private val DarkOnBackgroundForLightTheme = Color(0xFF1C1B1F)


 // fixed visible value for the dark / black theme's; textondark tokens. The default `darkColorScheme()` uses; `Color(0xFFE6E1E5)` (offwhite) which is visible on every dark; surface. Pinning to that same value makes the surface text; deterministic.
 private val LightOnBackgroundForDarkTheme = Color(0xFFE6E1E5)

 private const val BackgroundBlendRatio = 0.03f
 private const val SurfaceBlendRatio = 0.02f
 private const val SurfaceVariantBlendRatio = 0.01f
 private const val BlackSurfaceBlendRatio = 0.05f
 private const val BlackSurfaceVariantBlendRatio = 0.06f
 private const val DarkOnPrimaryLightness = -0.3f
 private const val DarkOnSecondaryLightness = -0.4f
 private const val DarkOnTertiaryLightness = -0.5f
 private const val LightOnBackgroundLightness = -0.5f
 private const val LightOnSurfaceLightness = -0.5f
 private const val LightOnSurfaceVariantLightness = -0.45f
 private const val DarkToBlackBlendRatio = 0.4f

 fun createLightColorScheme(primaryColor: Color) = lightColorScheme(
 primary = primaryColor,
 onPrimary = LightContrastColor,
 primaryContainer = primaryColor,
 onPrimaryContainer = LightContrastColor,
 secondary = primaryColor,
 onSecondary = LightContrastColor,
 secondaryContainer = primaryColor,

 // `onSecondaryContainer` was previously unset, which; meant the framework default (`Color(0xFF4A4458)` for; `lightColorScheme()`) was used. The default is dark, so it; was visible on a light surface — but the AVD walkthrough; showed that `FilterChip` selected labels (which use; `onSecondaryContainer`) were still invisible. Pinning it; to the same fixed dark value (the same one used for; `onBackground` / `onSurface` / `onSurfaceVariant`) makes; the chip labels deterministic regardless of the active; primary color. The `DarkOnBackgroundForLightTheme` constant; is used directly (not via `adjustLightness`) because; `adjustLightness` calls `ColorUtils.colorToHSL`, which is; mocked to return 0 in the JVM unittest classpath (per; `unitTests.isReturnDefaultValues = true` in; `app/build.gradle.kts`).
 onSecondaryContainer = DarkOnBackgroundForLightTheme,
 tertiary = primaryColor,
 onTertiary = LightContrastColor,
 tertiaryContainer = primaryColor,
 onTertiaryContainer = LightContrastColor,
 background = blendColors(LightBackgroundColor, primaryColor, BackgroundBlendRatio),

 // `onBackground` is the value that the; `LocalContentColor` fallback reads from when no other; component overrides it. The previous `adjustLightness(; primaryColor,0.5f)` derives a verydarkpurple for the; default Purple primary, but the derivation depends on the; primary color: a light primary (e.g. pastel yellow) would; produce a darkyellow `onBackground`, which is OK; a; mediumlightness primary (e.g. cyan) would produce a; nearinvisible `onBackground`. The safer value is a fixed; nearblack that's visible on every surface.
 onBackground = DarkOnBackgroundForLightTheme,
 surface = blendColors(LightSurfaceColor, primaryColor, SurfaceBlendRatio),

 // same reasoning as `onBackground`: `onSurface` is; the value that Material3 `Surface` propagates into; `LocalContentColor` for any text inside the surface. A; fixed dark value is visible on every light surface.
 onSurface = DarkOnBackgroundForLightTheme,
 surfaceVariant = blendColors(LightSurfaceVariantColor, primaryColor, SurfaceBlendRatio),

 // `onSurfaceVariant` is what `FilterChip` (unselected); and `ListItem` (supporting text) read for their text color.; Pinning it to the same dark value (slightly lighter alpha; for mediumemphasis) keeps the chip labels visible.
 onSurfaceVariant = DarkOnBackgroundForLightTheme,
 )

 fun createDarkColorScheme(PrimaryColor: Color) = darkColorScheme(
 primary = PrimaryColor,
 onPrimary = adjustLightness(PrimaryColor, DarkOnPrimaryLightness),
 primaryContainer = PrimaryColor,
 onPrimaryContainer = LightContrastColor,
 secondary = PrimaryColor,
 onSecondary = adjustLightness(PrimaryColor, DarkOnSecondaryLightness),
 secondaryContainer = PrimaryColor,

 // `onSecondaryContainer` was previously `LightContrastColor`; (pure white). Pure white is the highestcontrast value on dark; surfaces, but it can be visually jarring for body text. Pin; to the offwhite value that matches the default `darkColorScheme()`; `onSurface` (`Color(0xFFE6E1E5)`) so the visual hierarchy is; consistent. The offwhite value is visible on every dark; surface and matches the `LocalContentColor` fallback value; the KordXTheme CompositionLocalProvider also pins.
 onSecondaryContainer = LightOnBackgroundForDarkTheme,
 tertiary = PrimaryColor,
 onTertiary = adjustLightness(PrimaryColor, DarkOnTertiaryLightness),
 tertiaryContainer = PrimaryColor,
 onTertiaryContainer = LightContrastColor,
 background = blendColors(DarkBackgroundColor, PrimaryColor, BackgroundBlendRatio),
 onBackground = LightOnBackgroundForDarkTheme,
 surface = blendColors(DarkSurfaceColor, PrimaryColor, SurfaceBlendRatio),
 onSurface = LightOnBackgroundForDarkTheme,
 surfaceVariant = blendColors(
 DarkSurfaceVariantColor,
 PrimaryColor,
 SurfaceVariantBlendRatio
 ),
 onSurfaceVariant = LightOnBackgroundForDarkTheme,
 )

 fun createBlackColorScheme(PrimaryColor: Color) = darkColorScheme(
 primary = PrimaryColor,
 onPrimary = adjustLightness(PrimaryColor, DarkOnPrimaryLightness),
 primaryContainer = PrimaryColor,
 onPrimaryContainer = LightContrastColor,
 secondary = PrimaryColor,
 onSecondary = adjustLightness(PrimaryColor, DarkOnSecondaryLightness),
 secondaryContainer = PrimaryColor,

 // same as `createDarkColorScheme`. The BLACK theme; uses pure black surfaces (`background = Color.Black`), so the; text color must be light. `LightOnBackgroundForDarkTheme`; (offwhite) is visible on pure black.
 onSecondaryContainer = LightOnBackgroundForDarkTheme,
 tertiary = PrimaryColor,
 onTertiary = adjustLightness(PrimaryColor, DarkOnTertiaryLightness),
 tertiaryContainer = PrimaryColor,
 onTertiaryContainer = LightContrastColor,
 background = BlackContrastColor,
 onBackground = LightOnBackgroundForDarkTheme,
 surface = blendColors(BlackContrastColor, PrimaryColor, BlackSurfaceBlendRatio),
 onSurface = LightOnBackgroundForDarkTheme,
 surfaceVariant = blendColors(
 BlackContrastColor,
 PrimaryColor,
 BlackSurfaceVariantBlendRatio
 ),
 onSurfaceVariant = LightOnBackgroundForDarkTheme,
 )

 fun toBlackColorScheme(colorScheme: ColorScheme) = colorScheme.copy(
 primaryContainer = convertDarkToBlack(colorScheme.primaryContainer),
 secondaryContainer = convertDarkToBlack(colorScheme.secondaryContainer),
 tertiaryContainer = convertDarkToBlack(colorScheme.tertiaryContainer),
 background = BlackContrastColor,
 surface = convertDarkToBlack(colorScheme.surface),
 surfaceContainerLowest = convertDarkToBlack(colorScheme.surfaceContainerLowest),
 surfaceContainerLow = convertDarkToBlack(colorScheme.surfaceContainerLow),
 surfaceContainer = convertDarkToBlack(colorScheme.surfaceContainer),
 surfaceContainerHigh = convertDarkToBlack(colorScheme.surfaceContainerHigh),
 surfaceContainerHighest = convertDarkToBlack(colorScheme.surfaceContainerHighest),
 surfaceVariant = convertDarkToBlack(colorScheme.surfaceVariant),
 surfaceTint = convertDarkToBlack(colorScheme.surfaceTint),
 )

 private fun convertDarkToBlack(color: Color): Color {
 val argb = ColorUtils.blendARGB(
 BlackContrastColor.toArgb(),
 color.toArgb(),
 DarkToBlackBlendRatio,
 )
 return Color(argb)
 }

 private fun blendColors(color1: Color, color2: Color, ratio: Float) =
 Color(ColorUtils.blendARGB(color1.toArgb(), color2.toArgb(), ratio))

 private fun adjustLightness(color: Color, threshold: Float): Color {
 val hsl = convertColorToHSL(color)
 hsl[2] = hsl[2] + threshold
 return convertHSLToColor(hsl)
 }

 private fun convertColorToHSL(color: Color): FloatArray {
 val out = FloatArray(3)
 ColorUtils.colorToHSL(color.toArgb(), out)
 return out
 }

 private fun convertHSLToColor(hsl: FloatArray) =
 Color(ColorUtils.HSLToColor(hsl))
}
