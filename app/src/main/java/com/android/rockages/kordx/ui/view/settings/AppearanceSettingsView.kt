package com.android.rockages.kordx.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.rockages.kordx.services.i18n.CommonTranslation
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.components.settings.SettingsFloatInputTile
import com.android.rockages.kordx.ui.components.settings.SettingsOptionTile
import com.android.rockages.kordx.ui.components.settings.SettingsSideHeading
import com.android.rockages.kordx.ui.components.settings.SettingsSwitchTile
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.theme.PrimaryThemeColor
import com.android.rockages.kordx.ui.theme.KordXTypography
import com.android.rockages.kordx.ui.theme.ThemeColors
import com.android.rockages.kordx.ui.theme.ThemeMode
import kotlinx.serialization.Serializable

private val scalingPresets = listOf(
 0.25f, 0.5f, 0.75f, 0.9f, 1f,
 1.1f, 1.25f, 1.5f, 1.75f, 2f,
 2.25f, 2.5f, 2.75f, 3f,
)

@Serializable
object AppearanceSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsView(context: ViewContext) {
 val scrollState = rememberScrollState()
 val language by context.kordx.settings.language.flow.collectAsState()
 val fontFamily by context.kordx.settings.fontFamily.flow.collectAsState()
 val themeMode by context.kordx.settings.themeMode.flow.collectAsState()
 val useMaterialYou by context.kordx.settings.useMaterialYou.flow.collectAsState()
 val primaryColor by context.kordx.settings.primaryColor.flow.collectAsState()
 val fontScale by context.kordx.settings.fontScale.flow.collectAsState()
 val contentScale by context.kordx.settings.contentScale.flow.collectAsState()

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 title = {
 TopAppBarMinimalTitle {
 Text("${context.kordx.t.Settings} - ${context.kordx.t.Appearance}")
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 navigationIcon = {
 IconButton(
 onClick = {
 context.navController.popBackStack()
 }
 ) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 actions = {
 IconButtonPlaceholder()
 },
 )
 },
 content = { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize()
 ) {
 Column(modifier = Modifier.verticalScroll(scrollState)) {
 SettingsSideHeading(context.kordx.t.Appearance)
 SettingsOptionTile(
 icon = {
 Icon(Icons.Filled.Language, null)
 },
 title = {
 Text(context.kordx.t.Language_)
 },
 value = language ?: "",
 values = run {
 val defaultLocaleNativeName =
 context.kordx.translator.getDefaultLocaleNativeName()
 mapOf(
 "" to "${context.kordx.t.System} (${defaultLocaleNativeName})"
 ) + context.kordx.translator.translations.localeNativeNames
 },
 captions = run {
 val defaultLocaleDisplayName =
 context.kordx.translator.getDefaultLocaleDisplayName()
 mapOf(
 "" to "${CommonTranslation.System} (${defaultLocaleDisplayName})"
 ) + context.kordx.translator.translations.localeDisplayNames
 },
 onChange = { value ->
 context.kordx.settings.language.setValue(value.takeUnless { it == "" })
 }
 )
 HorizontalDivider()
 SettingsOptionTile(
 icon = {
 Icon(Icons.Filled.TextFormat, null)
 },
 title = {
 Text(context.kordx.t.Font)
 },
 value = KordXTypography.resolveFont(fontFamily).fontName,
 values = KordXTypography.all.keys.associateWith { it },
 onChange = { value ->
 context.kordx.settings.fontFamily.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsFloatInputTile(
 context,
 icon = {
 Icon(Icons.Filled.TextIncrease, null)
 },
 title = {
 Text(context.kordx.t.FontScale)
 },
 value = fontScale,
 presets = scalingPresets,
 labelText = { "x$it" },
 onReset = {
 context.kordx.settings.fontScale.setValue(
 context.kordx.settings.fontScale.defaultValue,
 )
 },
 onChange = { value ->
 context.kordx.settings.fontScale.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsFloatInputTile(
 context,
 icon = {
 Icon(Icons.Filled.PhotoSizeSelectLarge, null)
 },
 title = {
 Text(context.kordx.t.ContentScale)
 },
 value = contentScale,
 presets = scalingPresets,
 labelText = { "x$it" },
 onReset = {
 context.kordx.settings.contentScale.setValue(
 context.kordx.settings.contentScale.defaultValue,
 )
 },
 onChange = { value ->
 context.kordx.settings.contentScale.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsOptionTile(
 icon = {
 Icon(Icons.Filled.Palette, null)
 },
 title = {
 Text(context.kordx.t.Theme)
 },
 value = themeMode,
 values = mapOf(
 ThemeMode.SYSTEM to context.kordx.t.SystemLightDark,
 ThemeMode.SYSTEM_BLACK to context.kordx.t.SystemLightBlack,
 ThemeMode.LIGHT to context.kordx.t.Light,
 ThemeMode.DARK to context.kordx.t.Dark,
 ThemeMode.BLACK to context.kordx.t.Black,
 ),
 onChange = { value ->
 context.kordx.settings.themeMode.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsSwitchTile(
 icon = {
 Icon(Icons.Filled.Face, null)
 },
 title = {
 Text(context.kordx.t.MaterialYou)
 },
 value = useMaterialYou,
 onChange = { value ->
 context.kordx.settings.useMaterialYou.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsOptionTile(
 icon = {
 Icon(Icons.Filled.Colorize, null)
 },
 title = {
 Text(context.kordx.t.PrimaryColor)
 },
 value = ThemeColors.resolvePrimaryColorKey(primaryColor),
 values = PrimaryThemeColor.entries.associateWith { it.label(context) },
 enabled = !useMaterialYou,
 onChange = { value ->
 context.kordx.settings.primaryColor.setValue(value.name)
 }
 )
 }
 }
 }
 )
}

fun PrimaryThemeColor.label(context: ViewContext) = when (this) {
 PrimaryThemeColor.Red -> context.kordx.t.Red
 PrimaryThemeColor.Orange -> context.kordx.t.Orange
 PrimaryThemeColor.Amber -> context.kordx.t.Amber
 PrimaryThemeColor.Yellow -> context.kordx.t.Yellow
 PrimaryThemeColor.Lime -> context.kordx.t.Lime
 PrimaryThemeColor.Green -> context.kordx.t.Green
 PrimaryThemeColor.Emerald -> context.kordx.t.Emerald
 PrimaryThemeColor.Teal -> context.kordx.t.Teal
 PrimaryThemeColor.Cyan -> context.kordx.t.Cyan
 PrimaryThemeColor.Sky -> context.kordx.t.Sky
 PrimaryThemeColor.Blue -> context.kordx.t.Blue
 PrimaryThemeColor.Indigo -> context.kordx.t.Indigo
 PrimaryThemeColor.Violet -> context.kordx.t.Violet
 PrimaryThemeColor.Purple -> context.kordx.t.Purple
 PrimaryThemeColor.Fuchsia -> context.kordx.t.Fuchsia
 PrimaryThemeColor.Pink -> context.kordx.t.Pink
 PrimaryThemeColor.Rose -> context.kordx.t.Rose
}
