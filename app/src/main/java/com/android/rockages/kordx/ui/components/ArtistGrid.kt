package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.services.groove.createArtworkImageRequest
import com.android.rockages.kordx.services.groove.getSortedSongIds
import com.android.rockages.kordx.services.groove.repositories.ArtistRepository
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.ArtistViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGrid(
 context: ViewContext,
 artistName: List<String>,
 artistsCount: Int? = null,
) {
 val sortBy by context.kordx.settings.lastUsedArtistsSortBy.flow.collectAsState()
 val sortReverse by context.kordx.settings.lastUsedArtistsSortReverse.flow.collectAsState()
 val sortedArtistNames by remember(artistName, sortBy, sortReverse) {
 derivedStateOf {
 context.kordx.groove.artist.sort(artistName, sortBy, sortReverse)
 }
 }
 val horizontalGridColumns by context.kordx.settings.lastUsedArtistsHorizontalGridColumns.flow.collectAsState()
 val verticalGridColumns by context.kordx.settings.lastUsedArtistsVerticalGridColumns.flow.collectAsState()
 val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
 derivedStateOf {
 ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
 }
 }
 var showModifyLayoutSheet by remember { mutableStateOf(false) }
 var listView by remember { mutableStateOf(false) }

 MediaSortBarScaffold(
 mediaSortBar = {
 MediaSortBar(
 context,
 reverse = sortReverse,
 onReverseChange = {
 context.kordx.settings.lastUsedArtistsSortReverse.setValue(it)
 },
 sort = sortBy,
 sorts = ArtistRepository.SortBy.entries
 .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
 onSortChange = {
 context.kordx.settings.lastUsedArtistsSortBy.setValue(it)
 },
 label = {
 Text(context.kordx.t.XArtists((artistsCount ?: artistName.size).toString()))
 },
 onShowModifyLayout = {
 showModifyLayoutSheet = true
 },
 onShowListView = {
 listView = !listView
 },
 listView = listView,
 )
 },
 content = {
 when {
 artistName.isEmpty() -> IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.Person,
 null,
 modifier = modifier,
 )
 },
 content = { Text(context.kordx.t.DamnThisIsSoEmpty) }
 )
 listView -> ArtistListContent(
 context = context,
 sortedArtistNames = sortedArtistNames,
 )
 else -> ResponsiveGrid(gridColumns) {
 items(
 sortedArtistNames.size,
 key = { idx -> sortedArtistNames[idx] },
 contentType = { _ -> Groove.Kind.ARTIST }
 ) { idx ->
 val name = sortedArtistNames[idx]
 context.kordx.groove.artist.get(name)?.let { artist ->
 ArtistTile(context, artist)
 }
 }
 }
 }

 if (showModifyLayoutSheet) {
 ResponsiveGridSizeAdjustBottomSheet(
 context,
 columns = gridColumns,
 onColumnsChange = {
 context.kordx.settings.lastUsedArtistsHorizontalGridColumns.setValue(
 it.horizontal
 )
 context.kordx.settings.lastUsedArtistsVerticalGridColumns.setValue(
 it.vertical
 )
 },
 onDismissRequest = {
 showModifyLayoutSheet = false
 }
 )
 }
 }
 )
}

@Composable
private fun ArtistListContent(
 context: ViewContext,
 sortedArtistNames: List<String>,
) {
 // Group artists by first letter for A–Z section headers.
 val grouped = remember(sortedArtistNames) {
 sortedArtistNames.groupBy { name ->
 name.firstOrNull()?.uppercase()?.firstOrNull() ?: '#'
 }.toSortedMap()
 }
 val listState = rememberLazyListState()

 LazyColumn(
 state = listState,
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
 verticalArrangement = Arrangement.spacedBy(4.dp),
 modifier = Modifier.drawScrollBar(listState),
 ) {
 grouped.forEach { (letter, artists) ->
 // Sticky letter header
 item(key = "header_$letter", contentType = "header") {
 Text(
 text = letter.toString(),
 style = MaterialTheme.typography.titleMedium.copy(
 fontWeight = FontWeight.SemiBold,
 color = MaterialTheme.colorScheme.primary,
 ),
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 8.dp, horizontal = 4.dp),
 )
 }
 // Artist rows
 items(
 artists.size,
 key = { idx -> artists[idx] },
 contentType = { _ -> "artist_row" }
 ) { idx ->
 val name = artists[idx]
 context.kordx.groove.artist.get(name)?.let { artist ->
 ArtistListRow(
 context = context,
 artist = artist,
 )
 }
 }
 }
 }
}

@Composable
private fun ArtistListRow(
 context: ViewContext,
 artist: Artist,
) {
 Card(
 modifier = Modifier
 .fillMaxWidth()
 .clickable {
 context.navController.navigate(ArtistViewRoute(artist.name))
 },
 colors = CardDefaults.cardColors(containerColor = Color.Transparent),
 ) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
 ) {
 AsyncImage(
 artist.createArtworkImageRequest(context.kordx).build(),
 null,
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .size(48.dp)
 .clip(CircleShape),
 )
 Spacer(modifier = Modifier.width(16.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
 artist.name,
 style = MaterialTheme.typography.bodyLarge,
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
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 }
 }
 IconButton(
 modifier = Modifier.size(40.dp),
 onClick = {
 context.kordx.radio.shorty.playQueue(artist.getSortedSongIds(context.kordx))
 }
 ) {
 Icon(
 Icons.AutoMirrored.Filled.PlaylistPlay,
 contentDescription = context.kordx.t.ShufflePlay,
 modifier = Modifier.size(24.dp),
 tint = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 }
 }
}

private fun ArtistRepository.SortBy.label(context: ViewContext) = when (this) {
 ArtistRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 ArtistRepository.SortBy.ARTIST_NAME -> context.kordx.t.Artist
 ArtistRepository.SortBy.ALBUMS_COUNT -> context.kordx.t.AlbumCount
 ArtistRepository.SortBy.TRACKS_COUNT -> context.kordx.t.TrackCount
}
