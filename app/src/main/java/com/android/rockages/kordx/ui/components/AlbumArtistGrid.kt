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
import com.android.rockages.kordx.services.groove.repositories.AlbumArtistRepository
import com.android.rockages.kordx.ui.helpers.ViewContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumArtistGrid(
 context: ViewContext,
 albumArtistNames: List<String>,
 albumArtistsCount: Int? = null,
) {
 val sortBy by context.kordx.settings.lastUsedAlbumArtistsSortBy.flow.collectAsState()
 val sortReverse by context.kordx.settings.lastUsedAlbumArtistsSortReverse.flow.collectAsState()
 val sortedAlbumArtistNames by remember(albumArtistNames, sortBy, sortReverse) {
 derivedStateOf {
 context.kordx.groove.albumArtist.sort(albumArtistNames, sortBy, sortReverse)
 }
 }
 val horizontalGridColumns by context.kordx.settings.lastUsedAlbumArtistsHorizontalGridColumns.flow.collectAsState()
 val verticalGridColumns by context.kordx.settings.lastUsedAlbumArtistsVerticalGridColumns.flow.collectAsState()
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
 context.kordx.settings.lastUsedAlbumArtistsSortReverse.setValue(it)
 },
 sort = sortBy,
 sorts = AlbumArtistRepository.SortBy.entries
 .associateWith { x -> ViewContext.parameterizedFn { x.label(context) } },
 onSortChange = {
 context.kordx.settings.lastUsedAlbumArtistsSortBy.setValue(it)
 },
 label = {
 Text(
 context.kordx.t.XArtists(
 (albumArtistsCount ?: albumArtistNames.size).toString()
 )
 )
 },
 onShowModifyLayout = {
 showModifyLayoutSheet = true
 },
 )
 },
 content = {
 when {
 albumArtistNames.isEmpty() -> IconTextBody(
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
 sortedAlbumArtistNames,
 key = { i, x -> "$i-$x" },
 contentType = { _, _ -> Groove.Kind.ARTIST }
 ) { _, albumArtistName ->
 context.kordx.groove.albumArtist.get(albumArtistName)
 ?.let { albumArtist ->
 AlbumArtistTile(context, albumArtist)
 }
 }
 }
 }

 if (showModifyLayoutSheet) {
 ResponsiveGridSizeAdjustBottomSheet(
 context,
 columns = gridColumns,
 onColumnsChange = {
 context.kordx.settings.lastUsedAlbumArtistsHorizontalGridColumns.setValue(
 it.horizontal
 )
 context.kordx.settings.lastUsedAlbumArtistsVerticalGridColumns.setValue(
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

private fun AlbumArtistRepository.SortBy.label(context: ViewContext) = when (this) {
 AlbumArtistRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 AlbumArtistRepository.SortBy.ARTIST_NAME -> context.kordx.t.Artist
 AlbumArtistRepository.SortBy.ALBUMS_COUNT -> context.kordx.t.AlbumCount
 AlbumArtistRepository.SortBy.TRACKS_COUNT -> context.kordx.t.TrackCount
}
