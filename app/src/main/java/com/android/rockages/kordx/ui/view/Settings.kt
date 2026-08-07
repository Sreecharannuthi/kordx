package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.R
import com.android.rockages.kordx.services.AppMeta
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.components.settings.SettingsSimpleTile
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.settings.AppearanceSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.GrooveSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.HomePageSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.MiniPlayerSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.NowPlayingSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.PlayerSettingsViewRoute

import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.serialization.Serializable

@Serializable
data class SettingsViewRoute(val initialElement: String? = null) {
 companion object {
 const val ELEMENT_MEDIA_FOLDERS = "media_folders"
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(context: ViewContext, route: SettingsViewRoute) {
 val scrollState = rememberScrollState()

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 title = {
 TopAppBarMinimalTitle {
 Text(context.kordx.t.Settings)
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
 val isLight = !isSystemInDarkTheme()
 val logoRes = if (isLight) R.drawable.ic_launcher_foreground_light else R.drawable.ic_launcher_foreground
 Row(verticalAlignment = Alignment.CenterVertically) {
 AsyncImage(
 model = logoRes,
 contentDescription = AppMeta.appName,
 modifier = Modifier
 .padding(16.dp)
 .size(64.dp)
 .clip(RoundedCornerShape(16.dp))
 )
 Column(modifier = Modifier.padding(start = 4.dp)) {
 Text(AppMeta.appName, style = MaterialTheme.typography.titleMedium)
 Spacer(modifier = Modifier.height(2.dp))
 Text(AppMeta.version, style = MaterialTheme.typography.labelMedium)
 }
 }

 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.LibraryMusic, null)
 },
 title = {
 Text(context.kordx.t.Groove)
 },
 onClick = {
 context.navController.navigate(
 GrooveSettingsViewRoute(route.initialElement)
 )
 },
 )
 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.Radio, null)
 },
 title = {
 Text(context.kordx.t.Player)
 },
 onClick = {
 context.navController.navigate(PlayerSettingsViewRoute)
 },
 )
 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.Palette, null)
 },
 title = {
 Text(context.kordx.t.Appearance)
 },
 onClick = {
 context.navController.navigate(AppearanceSettingsViewRoute)
 },
 )
 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.Home, null)
 },
 title = {
 Text(context.kordx.t.Home)
 },
 onClick = {
 context.navController.navigate(HomePageSettingsViewRoute)
 },
 )
 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.MusicNote, null)
 },
 title = {
 Text(context.kordx.t.MiniPlayer)
 },
 onClick = {
 context.navController.navigate(MiniPlayerSettingsViewRoute)
 },
 )
 HorizontalDivider()
 SettingsSimpleTile(
 icon = {
 Icon(Icons.Filled.MusicNote, null)
 },
 title = {
 Text(context.kordx.t.NowPlaying)
 },
 onClick = {
 context.navController.navigate(NowPlayingSettingsViewRoute)
 },
 )

 }
 }
 }
 )
}
