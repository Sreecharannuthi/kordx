package com.android.rockages.kordx.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lyrics
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
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.components.settings.SettingsOptionTile
import com.android.rockages.kordx.ui.components.settings.SettingsSideHeading
import com.android.rockages.kordx.ui.components.settings.SettingsSwitchTile
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.NowPlayingControlsLayout
import com.android.rockages.kordx.ui.view.NowPlayingLyricsLayout
import kotlinx.serialization.Serializable

@Serializable
object NowPlayingSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSettingsView(context: ViewContext) {
 val scrollState = rememberScrollState()
 val nowPlayingControlsLayout by context.kordx.settings.nowPlayingControlsLayout.flow.collectAsState()
 val nowPlayingAdditionalInfo by context.kordx.settings.nowPlayingAdditionalInfo.flow.collectAsState()
 val nowPlayingSeekControls by context.kordx.settings.nowPlayingSeekControls.flow.collectAsState()
 val nowPlayingLyricsLayout by context.kordx.settings.nowPlayingLyricsLayout.flow.collectAsState()
 val lyricsKeepScreenAwake by context.kordx.settings.lyricsKeepScreenAwake.flow.collectAsState()

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 title = {
 TopAppBarMinimalTitle {
 Text("${context.kordx.t.Settings} - ${context.kordx.t.NowPlaying}")
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
 SettingsSideHeading(context.kordx.t.NowPlaying)
 SettingsOptionTile(
 icon = {
 Icon(Icons.Filled.Dashboard, null)
 },
 title = {
 Text(context.kordx.t.ControlsLayout)
 },
 value = nowPlayingControlsLayout,
 values = NowPlayingControlsLayout.entries
 .associateWith { it.label(context) },
 onChange = { value ->
 context.kordx.settings.nowPlayingControlsLayout.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsOptionTile(
 icon = {
 Icon(Icons.AutoMirrored.Outlined.Article, null)
 },
 title = {
 Text(context.kordx.t.LyricsLayout)
 },
 value = nowPlayingLyricsLayout,
 values = NowPlayingLyricsLayout.entries
 .associateWith { it.label(context) },
 onChange = { value ->
 context.kordx.settings.nowPlayingLyricsLayout.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsSwitchTile(
 icon = {
 Icon(Icons.AutoMirrored.Filled.Wysiwyg, null)
 },
 title = {
 Text(context.kordx.t.ShowAudioInformation)
 },
 value = nowPlayingAdditionalInfo,
 onChange = { value ->
 context.kordx.settings.nowPlayingAdditionalInfo.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsSwitchTile(
 icon = {
 Icon(Icons.Filled.Forward30, null)
 },
 title = {
 Text(context.kordx.t.ShowSeekControls)
 },
 value = nowPlayingSeekControls,
 onChange = { value ->
 context.kordx.settings.nowPlayingSeekControls.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsSwitchTile(
 icon = {
 Icon(Icons.Filled.Lyrics, null)
 },
 title = {
 Text(context.kordx.t.KeepScreenAwakeOnLyrics)
 },
 value = lyricsKeepScreenAwake,
 onChange = { value ->
 context.kordx.settings.lyricsKeepScreenAwake.setValue(value)
 }
 )
 }
 }
 }
 )
}

fun NowPlayingControlsLayout.label(context: ViewContext) = when (this) {
 NowPlayingControlsLayout.CompactLeft -> context.kordx.t.CompactLeft
 NowPlayingControlsLayout.CompactRight -> context.kordx.t.CompactRight
 NowPlayingControlsLayout.Traditional -> context.kordx.t.Traditional
}

fun NowPlayingLyricsLayout.label(context: ViewContext) = when (this) {
 NowPlayingLyricsLayout.ReplaceArtwork -> context.kordx.t.ReplaceArtwork
 NowPlayingLyricsLayout.SeparatePage -> context.kordx.t.SeparatePage
}
