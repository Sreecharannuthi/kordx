package com.android.rockages.kordx.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

@Suppress("ConstPropertyName")
object ThemeColorSchemes {
    private val BlackContrastColor = Color.Black

    // Fixed visible value for the dark / black theme's text-on-dark tokens.
    // The default `darkColorScheme()` uses `Color(0xFFE6E1E5)` (off-white)
    // which is visible on every dark surface.
    private val LightOnBackgroundForDarkTheme = Color(0xFFE6E1E5)

    // Fixed visible value for the light theme's text-on-light tokens.
    private val DarkOnBackgroundForLightTheme = Color(0xFF1C1B1F)

    private const val DarkToBlackBlendRatio = 0.4f

    // ──────────────────────────────────────────────────────────────
    //  Tonal palette derivation
    // ──────────────────────────────────────────────────────────────

    /**
     * Derive a secondary colour from the primary by desaturating and
     * shifting the hue slightly toward blue (–15°).  This keeps it
     * harmonious with the primary while giving visible tonal hierarchy.
     */
    private fun deriveSecondary(primary: Color): Color {
        val hsl = colorToHSL(primary)
        hsl[0] = (hsl[0] - 15f + 360f) % 360f   // slight hue shift
        hsl[1] = (hsl[1] * 0.55f).coerceIn(0f, 1f) // desaturate ~45 %
        return hslToColor(hsl)
    }

    /**
     * Derive a tertiary colour by shifting the hue +60° and
     * desaturating moderately.  This gives a complementary accent
     * that's visually distinct from both primary and secondary.
     */
    private fun deriveTertiary(primary: Color): Color {
        val hsl = colorToHSL(primary)
        hsl[0] = (hsl[0] + 60f) % 360f            // analogous shift
        hsl[1] = (hsl[1] * 0.70f).coerceIn(0f, 1f) // moderate desaturation
        return hslToColor(hsl)
    }

    /**
     * Make the `onPrimary` colour luminance-aware so that light
     * accents (e.g. Yellow, Lime) do not produce white-on-yellow
     * buttons.  If the primary's relative luminance is above 0.4
     * (i.e. it's "light"), we return a dark text; otherwise white.
     */
    private fun luminanceAwareOnPrimary(primary: Color): Color {
        val luminance = ColorUtils.calculateLuminance(primary.toArgb())
        return if (luminance > 0.4) DarkOnBackgroundForLightTheme else Color.White
    }

    /**
     * Lighten [color] toward white by [amount] (0–1).
     */
    private fun lighten(color: Color, amount: Float): Color {
        val hsl = colorToHSL(color)
        hsl[2] = (hsl[2] + amount * (1f - hsl[2])).coerceIn(0f, 1f)
        return hslToColor(hsl)
    }

    /**
     * Darken [color] toward black by [amount] (0–1).
     */
    private fun darken(color: Color, amount: Float): Color {
        val hsl = colorToHSL(color)
        hsl[2] = (hsl[2] * (1f - amount)).coerceIn(0f, 1f)
        return hslToColor(hsl)
    }

    // ──────────────────────────────────────────────────────────────
    //  Light scheme
    // ──────────────────────────────────────────────────────────────

    fun createLightColorScheme(primaryColor: Color): ColorScheme {
        val secondary = deriveSecondary(primaryColor)
        val tertiary = deriveTertiary(primaryColor)

        // Containers: lighter versions of the accent colours
        val primaryContainer = lighten(primaryColor, 0.80f)
        val secondaryContainer = lighten(secondary, 0.80f)
        val tertiaryContainer = lighten(tertiary, 0.80f)

        // Surface hierarchy: neutrals with a subtle primary tint
        val surface = Color(0xFFFEF7FF)
        val surfaceVariant = Color(0xFFE7E0EC)
        val background = Color(0xFFFDF8FE)

        return lightColorScheme(
            primary = primaryColor,
            onPrimary = luminanceAwareOnPrimary(primaryColor),
            primaryContainer = primaryContainer,
            onPrimaryContainer = darken(primaryContainer, 0.70f),
            secondary = secondary,
            onSecondary = luminanceAwareOnPrimary(secondary),
            secondaryContainer = secondaryContainer,
            // Pin to fixed dark value so FilterChip labels are
            // visible regardless of primary colour.
            onSecondaryContainer = DarkOnBackgroundForLightTheme,
            tertiary = tertiary,
            onTertiary = luminanceAwareOnPrimary(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = darken(tertiaryContainer, 0.70f),
            background = background,
            onBackground = DarkOnBackgroundForLightTheme,
            surface = surface,
            onSurface = DarkOnBackgroundForLightTheme,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = DarkOnBackgroundForLightTheme,
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            inverseSurface = Color(0xFF313033),
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = lighten(primaryColor, 0.40f),
            surfaceTint = primaryColor,
        )
    }

    // ──────────────────────────────────────────────────────────────
    //  Dark scheme
    // ──────────────────────────────────────────────────────────────

    fun createDarkColorScheme(primaryColor: Color): ColorScheme {
        val secondary = deriveSecondary(primaryColor)
        val tertiary = deriveTertiary(primaryColor)

        // Containers: darker versions of the accent colours
        val primaryContainer = darken(primaryColor, 0.55f)
        val secondaryContainer = darken(secondary, 0.55f)
        val tertiaryContainer = darken(tertiary, 0.55f)

        // Surface hierarchy: dark neutrals with a subtle primary tint
        val surface = Color(0xFF141218)
        val surfaceVariant = Color(0xFF49454F)
        val background = Color(0xFF0E0E11)

        return darkColorScheme(
            primary = lighten(primaryColor, 0.25f),
            onPrimary = darken(primaryColor, 0.50f),
            primaryContainer = primaryContainer,
            onPrimaryContainer = lighten(primaryContainer, 0.70f),
            secondary = lighten(secondary, 0.25f),
            onSecondary = darken(secondary, 0.50f),
            secondaryContainer = secondaryContainer,
            // Same as light theme: pin to fixed off-white value.
            onSecondaryContainer = LightOnBackgroundForDarkTheme,
            tertiary = lighten(tertiary, 0.25f),
            onTertiary = darken(tertiary, 0.50f),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = lighten(tertiaryContainer, 0.70f),
            background = background,
            onBackground = LightOnBackgroundForDarkTheme,
            surface = surface,
            onSurface = LightOnBackgroundForDarkTheme,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = LightOnBackgroundForDarkTheme,
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            inverseSurface = LightOnBackgroundForDarkTheme,
            inverseOnSurface = Color(0xFF313033),
            inversePrimary = primaryColor,
            surfaceTint = lighten(primaryColor, 0.25f),
        )
    }

    // ──────────────────────────────────────────────────────────────
    //  Black (AMOLED) scheme
    // ──────────────────────────────────────────────────────────────

    fun createBlackColorScheme(primaryColor: Color): ColorScheme {
        val darkScheme = createDarkColorScheme(primaryColor)
        return toBlackColorScheme(darkScheme)
    }

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

    // ──────────────────────────────────────────────────────────────
    //  HSL helpers
    // ──────────────────────────────────────────────────────────────

    private fun colorToHSL(color: Color): FloatArray {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), out)
        return out
    }

    private fun hslToColor(hsl: FloatArray): Color =
        Color(ColorUtils.HSLToColor(hsl))
}
