package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import com.android.rockages.kordx.services.groove.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PriorityHigh
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.core.groove.AlbumArtist
import com.android.rockages.kordx.ui.components.AlbumArtistDropdownMenu
import com.android.rockages.kordx.ui.components.AlbumRow
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.GenericGrooveBanner
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
data class AlbumArtistViewRoute(val albumArtistName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumArtistView(context: ViewContext, route: AlbumArtistViewRoute) {
 val allAlbumArtistNames by context.kordx.groove.albumArtist.all.collectAsState()
 val allSongIds by context.kordx.groove.song.all.collectAsState()
 val allAlbumIds = context.kordx.groove.album.all
 val albumArtist by remember(allAlbumArtistNames) {
 derivedStateOf { context.kordx.groove.albumArtist.get(route.albumArtistName) }
 }
 val songIds by remember(albumArtist, allSongIds) {
 derivedStateOf { albumArtist?.getSongIds(context.kordx) ?: listOf() }
 }
 val albumIds by remember(albumArtist, allAlbumIds) {
 derivedStateOf { albumArtist?.getAlbumIds(context.kordx) ?: listOf() }
 }
 val isViable by remember(albumArtist) {
 derivedStateOf { albumArtist != null }
 }

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 navigationIcon = {
 IconButton(
 onClick = { context.navController.popBackStack() }
 ) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 title = {
 TopAppBarMinimalTitle {
 Text(
 context.kordx.t.AlbumArtist +
 (albumArtist?.let { " - ${it.name}" } ?: ""),
 maxLines = 2,
 overflow = TextOverflow.Ellipsis,
 )
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
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
 if (isViable) {
 SongList(
 context,
 songIds = songIds,
 leadingContent = {
 item {
 AlbumArtistHero(context, albumArtist!!)
 }
 if (albumIds.isNotEmpty()) {
 item {
 Spacer(modifier = Modifier.height(4.dp))
 AlbumRow(context, albumIds)
 Spacer(modifier = Modifier.height(4.dp))
 HorizontalDivider()
 }
 }
 }
 )
 } else UnknownAlbumArtist(context, route.albumArtistName)
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 }
 )
}

@Composable
private fun AlbumArtistHero(context: ViewContext, albumArtist: AlbumArtist) {
 GenericGrooveBanner(
 image = albumArtist.createArtworkImageRequest(context.kordx).build(),
 options = { expanded, onDismissRequest ->
 AlbumArtistDropdownMenu(
 context,
 albumArtist,
 expanded = expanded,
 onDismissRequest = onDismissRequest
 )
 },
 content = {
 Text(albumArtist.name)
 }
 )
}

@Composable
private fun UnknownAlbumArtist(context: ViewContext, artistName: String) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.PriorityHigh,
 null,
 modifier = modifier
 )
 },
 content = {
 Text(context.kordx.t.UnknownArtistX(artistName))
 }
 )
}
