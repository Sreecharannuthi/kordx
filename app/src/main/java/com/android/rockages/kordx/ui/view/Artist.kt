package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.core.groove.Artist
import com.android.rockages.kordx.services.groove.*
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.ArtistMergeDialog
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
data class ArtistViewRoute(val artistName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistView(context: ViewContext, route: ArtistViewRoute) {
 val allArtistNames by context.kordx.groove.artist.all.collectAsState()
 val allSongIds by context.kordx.groove.song.all.collectAsState()
 val artist by remember(allArtistNames) {
 derivedStateOf { context.kordx.groove.artist.get(route.artistName) }
 }
 val songIds by remember(artist, allSongIds) {
 derivedStateOf { artist?.getSongIds(context.kordx) ?: emptyList() }
 }
 val isViable by remember(allArtistNames) {
 derivedStateOf { allArtistNames.contains(route.artistName) }
 }
 var showMergeDialog by remember { mutableStateOf(false) }
 var menuExpanded by remember { mutableStateOf(false) }
 val mergedSources by remember(route.artistName) {
 derivedStateOf {
 context.kordx.groove.artist.getMergedSources(route.artistName)
 }
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
 context.kordx.t.Artist + (artist?.let { " - ${it.name}" } ?: ""),
 maxLines = 2,
 overflow = TextOverflow.Ellipsis,
 )
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 actions = {
 IconButton(onClick = { menuExpanded = true }) {
 Icon(Icons.Filled.MoreVert, null)
 }
 DropdownMenu(
 expanded = menuExpanded,
 onDismissRequest = { menuExpanded = false },
 ) {
 DropdownMenuItem(
 text = { Text(context.kordx.t.MergeWith) },
 leadingIcon = {
 Icon(Icons.Filled.Merge, null)
 },
 onClick = {
 menuExpanded = false
 showMergeDialog = true
 },
 )
 }
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
 ArtistCompactHeader(context, artist!!, mergedSources) {
 context.kordx.groove.artist.unmerge(it)
 }
 HorizontalDivider()
 Spacer(modifier = Modifier.height(4.dp))
 }
 },
 )
 } else UnknownArtist(context, route.artistName)
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 }
 )

 if (showMergeDialog) {
 ArtistMergeDialog(
 context = context,
 sourceName = route.artistName,
 onDismissRequest = { showMergeDialog = false },
 )
 }
}

/**
 * Compact artist header — small circular artwork + name + stats in a single row.
 * Replaces the full-width [GenericGrooveBanner] to keep the focus on the song list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistCompactHeader(
 context: ViewContext,
 artist: Artist,
 mergedSources: List<String> = emptyList(),
 onUnmerge: ((String) -> Unit)? = null,
) {
 Column(
 modifier = Modifier
 .fillMaxWidth()
 .padding(horizontal = 16.dp, vertical = 12.dp),
 ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 AsyncImage(
 artist.createArtworkImageRequest(context.kordx).build(),
 null,
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .size(56.dp)
 .clip(CircleShape),
 )
 Spacer(modifier = Modifier.width(16.dp))
 Column {
 Text(
 artist.name,
 style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 val parts = mutableListOf<String>()
 if (artist.numberOfAlbums > 0) {
 parts.add(context.kordx.t.XAlbums(artist.numberOfAlbums.toString()))
 }
 if (artist.numberOfTracks > 0) {
 parts.add(context.kordx.t.XSongs(artist.numberOfTracks.toString()))
 }
 if (parts.isNotEmpty()) {
 Text(
 parts.joinToString(" · "),
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 }
 }
 }
 if (mergedSources.isNotEmpty()) {
 Spacer(modifier = Modifier.height(8.dp))
 Text(
 context.kordx.t.MergedArtists,
 style = MaterialTheme.typography.labelMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 FlowRow {
 mergedSources.forEach { source ->
 val chipText = "${source} ×"
 TextButton(
 onClick = { onUnmerge?.invoke(source) },
 ) {
 Text(chipText)
 }
 }
 }
 }
 }
}

@Composable
private fun UnknownArtist(context: ViewContext, artistName: String) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.PriorityHigh,
 null,
 modifier = modifier,
 )
 },
 content = {
 Text(context.kordx.t.UnknownArtistX(artistName))
 }
 )
}
