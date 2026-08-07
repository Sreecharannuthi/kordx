package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import com.android.rockages.kordx.services.groove.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.PlaylistDropdownMenu
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.SongListType
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.theme.ThemeColors
import com.android.rockages.kordx.core.utils.mutate
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistViewRoute(val playlistId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(context: ViewContext, route: PlaylistViewRoute) {
 val allPlaylistIds by context.kordx.groove.playlist.all.collectAsState()
 val updateId by context.kordx.groove.playlist.updateId.collectAsState()
 var updateCounter by remember { mutableIntStateOf(0) }
 val playlist by remember(route.playlistId, updateId) {
 derivedStateOf { context.kordx.groove.playlist.get(route.playlistId) }
 }
 val songIds by remember(playlist) {
 derivedStateOf { playlist?.getSongIds(context.kordx) ?: emptyList() }
 }
 val isViable by remember(allPlaylistIds, route.playlistId) {
 derivedStateOf { allPlaylistIds.contains(route.playlistId) }
 }
 var showOptionsMenu by remember { mutableStateOf(false) }
 val isFavoritesPlaylist by remember(playlist) {
 derivedStateOf {
 playlist?.let { context.kordx.groove.playlist.isFavoritesPlaylist(it) } == true
 }
 }

 val incrementUpdateCounter = {
 updateCounter = if (updateCounter > 25) 0 else updateCounter + 1
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
 context.kordx.t.Playlist
 + (playlist?.let { " - ${it.title}" } ?: "")
 )
 }
 },
 actions = {
 if (isViable) {
 IconButton(
 onClick = {
 showOptionsMenu = true
 }
 ) {
 Icon(Icons.Filled.MoreVert, null)
 PlaylistDropdownMenu(
 context,
 playlist!!,
 expanded = showOptionsMenu,
 onSongsChanged = {
 incrementUpdateCounter()
 },
 onRename = {
 incrementUpdateCounter()
 },
 onDelete = {
 context.navController.popBackStack()
 },
 onDismissRequest = {
 showOptionsMenu = false
 }
 )
 }
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 )
 },
 content = { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize()
 ) {
 when {
 isViable -> SongList(
 context,
 songIds = songIds,
 type = SongListType.Playlist,
 disableHeartIcon = isFavoritesPlaylist,
 trailingOptionsContent = { _, song, onDismissRequest ->
 playlist?.takeIf { it.isNotLocal }?.let {
 DropdownMenuItem(
 leadingIcon = {
 Icon(
 Icons.Filled.DeleteForever,
 null,
 tint = ThemeColors.Red,
 )
 },
 text = {
 Text(context.kordx.t.RemoveFromPlaylist)
 },
 onClick = {
 onDismissRequest()
 context.kordx.groove.playlist.update(
 it.id,
 songIds.mutate { remove(song.id) },
 )
 }
 )
 }
 },
 )

 else -> UnknownPlaylist(context, route.playlistId)
 }
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 }
 )
}

@Composable
private fun UnknownPlaylist(context: ViewContext, playlistId: String) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.AutoMirrored.Filled.QueueMusic,
 null,
 modifier = modifier
 )
 },
 content = {
 Text(context.kordx.t.UnknownPlaylistX(playlistId))
 }
 )
}
