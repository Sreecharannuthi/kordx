package com.android.rockages.kordx.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.ui.helpers.ViewContext

enum class ThemeMode {
 SYSTEM,
 SYSTEM_BLACK,
 LIGHT,
 DARK,
 BLACK,
}

enum class ColorSchemeMode {
 LIGHT,
 DARK,
 BLACK
}

@Composable
fun KordXTheme(
 context: ViewContext,
 content: @Composable () -> Unit,
) {
 val themeMode by context.kordx.settings.themeMode.flow.collectAsState()
 val useMaterialYou by context.kordx.settings.useMaterialYou.flow.collectAsState()
 val primaryColorName by context.kordx.settings.primaryColor.flow.collectAsState()
 val fontName by context.kordx.settings.fontFamily.flow.collectAsState()
 val fontScale by context.kordx.settings.fontScale.flow.collectAsState()
 val contentScale by context.kordx.settings.contentScale.flow.collectAsState()

 val colorSchemeMode = themeMode.toColorSchemeMode(isSystemInDarkTheme())
 val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useMaterialYou) {
 val currentContext = LocalContext.current
 when (colorSchemeMode) {
 ColorSchemeMode.LIGHT -> dynamicLightColorScheme(currentContext)
 ColorSchemeMode.DARK -> dynamicDarkColorScheme(currentContext)
 ColorSchemeMode.BLACK -> ThemeColorSchemes.toBlackColorScheme(
 dynamicDarkColorScheme(currentContext)
 )
 }
 } else {
 val primaryColor = ThemeColors.resolvePrimaryColor(primaryColorName)
 when (colorSchemeMode) {
 ColorSchemeMode.LIGHT -> ThemeColorSchemes.createLightColorScheme(primaryColor)
 ColorSchemeMode.DARK -> ThemeColorSchemes.createDarkColorScheme(primaryColor)
 ColorSchemeMode.BLACK -> ThemeColorSchemes.createBlackColorScheme(primaryColor)
 }
 }
 val view = LocalView.current
 if (!view.isInEditMode) {
 val activity = view.context as Activity
 SideEffect {
 WindowCompat.getInsetsController(activity.window, view)
 .isAppearanceLightStatusBars = colorSchemeMode == ColorSchemeMode.LIGHT
 }
 }

 val textDirection = when (context.kordx.t.LocaleDirection) {
 "ltr" -> TextDirection.Ltr
 "rtl" -> TextDirection.Rtl
 else -> TextDirection.Unspecified
 }
 val typography = KordXTypography.toTypography(
 KordXTypography.resolveFont(fontName),
 textDirection,
 )

 MaterialTheme(
 colorScheme = colorScheme,
 typography = typography,
 content = {

 // the MaterialTheme's [MaterialTheme.colorScheme]; holds the right `onBackground` / `onSurface` tokens, but; [androidx.compose.material3.MaterialTheme] itself does NOT; propagate those into [LocalContentColor]. The framework's; default `LocalContentColor` is the system text color, which; does not track the active colorscheme. That mismatch is the; root cause of the "missing text" bug: every `Text`; composable that doesn't explicitly set a `color` or a; `style` (with a nonUnspecified color) renders with the; system default, which is invisible against the custom; theme's background in many places.
 //

 // The fix: wrap the [MaterialTheme] content in a; [CompositionLocalProvider] that pins [LocalContentColor]; to the active scheme's `onBackground`. This is the; lowerrisk of the two candidate fixes (the other; being: rewrite [ThemeColorSchemes] to use fixedcolor; tokens for `onBackground` / `onSurface` / `onSurfaceVariant`).; The CompositionLocalProvider is a single point of fix; and doesn't change the color tokens themselves; downstream; composables that *do* override `LocalContentColor` (e.g.; `Surface` / `Card` / `ListItem`) still take effect; because they sit inside this provider and override it.
 CompositionLocalProvider(
 LocalContentColor provides colorScheme.onBackground,
 LocalDensity provides Density(
 LocalDensity.current.density * contentScale,
 LocalDensity.current.fontScale * fontScale,
 )
 ) {
 content()
 }
 }
 )
}

fun ThemeMode.toColorSchemeMode(kordx: KordX): ColorSchemeMode {
 val isSystemInDarkTheme = kordx.applicationContext.resources.configuration.uiMode.let {
 (it and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
 }
 return toColorSchemeMode(isSystemInDarkTheme)
}

fun ThemeMode.toColorSchemeMode(isSystemInDarkTheme: Boolean) = when (this) {
 ThemeMode.SYSTEM -> if (isSystemInDarkTheme) ColorSchemeMode.DARK else ColorSchemeMode.LIGHT
 ThemeMode.SYSTEM_BLACK -> if (isSystemInDarkTheme) ColorSchemeMode.BLACK else ColorSchemeMode.LIGHT
 ThemeMode.LIGHT -> ColorSchemeMode.LIGHT
 ThemeMode.DARK -> ColorSchemeMode.DARK
 ThemeMode.BLACK -> ColorSchemeMode.BLACK
}

fun ColorSchemeMode.isLight() = this == ColorSchemeMode.LIGHT
