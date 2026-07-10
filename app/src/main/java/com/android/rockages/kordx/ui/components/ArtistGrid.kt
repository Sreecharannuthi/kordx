package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.services.groove.repositories.ArtistRepository
import com.android.rockages.kordx.ui.helpers.ViewContext

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

 else -> ResponsiveGrid(gridColumns) {
 itemsIndexed(
 sortedArtistNames,
 key = { i, x -> "$i-$x" },
 contentType = { _, _ -> Groove.Kind.ARTIST }
 ) { _, artistName ->
 context.kordx.groove.artist.get(artistName)?.let { artist ->
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

private fun ArtistRepository.SortBy.label(context: ViewContext) = when (this) {
 ArtistRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 ArtistRepository.SortBy.ARTIST_NAME -> context.kordx.t.Artist
 ArtistRepository.SortBy.ALBUMS_COUNT -> context.kordx.t.AlbumCount
 ArtistRepository.SortBy.TRACKS_COUNT -> context.kordx.t.TrackCount
}
