package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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
import com.android.rockages.kordx.services.groove.repositories.AlbumRepository
import com.android.rockages.kordx.ui.helpers.ViewContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGrid(
 context: ViewContext,
 albumIds: List<String>,
 albumsCount: Int? = null,
) {
 val sortBy by context.kordx.settings.lastUsedAlbumsSortBy.flow.collectAsState()
 val sortReverse by context.kordx.settings.lastUsedAlbumsSortReverse.flow.collectAsState()
 val sortedAlbumIds by remember(albumIds, sortBy, sortReverse) {
 derivedStateOf {
 context.kordx.groove.album.sort(albumIds, sortBy, sortReverse)
 }
 }
 val horizontalGridColumns by context.kordx.settings.lastUsedAlbumsHorizontalGridColumns.flow.collectAsState()
 val verticalGridColumns by context.kordx.settings.lastUsedAlbumsVerticalGridColumns.flow.collectAsState()
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
 context.kordx.settings.lastUsedAlbumsSortReverse.setValue(it)
 },
 sort = sortBy,
 sorts = AlbumRepository.SortBy.entries.associateWith { x ->
 ViewContext.parameterizedFn { x.label(it) }
 },
 onSortChange = {
 context.kordx.settings.lastUsedAlbumsSortBy.setValue(it)
 },
 label = {
 Text(context.kordx.t.XAlbums((albumsCount ?: albumIds.size).toString()))
 },
 onShowModifyLayout = {
 showModifyLayoutSheet = true
 },
 )
 },
 content = {
 when {
 albumIds.isEmpty() -> IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.Album,
 null,
 modifier = modifier,
 )
 },
 content = { Text(context.kordx.t.DamnThisIsSoEmpty) }
 )

 else -> ResponsiveGrid(gridColumns) {
 itemsIndexed(
 sortedAlbumIds,
 key = { _, x -> x },
 contentType = { _, _ -> Groove.Kind.ALBUM }
 ) { _, albumId ->
 context.kordx.groove.album.get(albumId)?.let { album ->
 AlbumTile(context, album)
 }
 }
 }
 }

 if (showModifyLayoutSheet) {
 ResponsiveGridSizeAdjustBottomSheet(
 context,
 columns = gridColumns,
 onColumnsChange = {
 context.kordx.settings.lastUsedAlbumsHorizontalGridColumns.setValue(
 it.horizontal
 )
 context.kordx.settings.lastUsedAlbumsVerticalGridColumns.setValue(
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

private fun AlbumRepository.SortBy.label(context: ViewContext) = when (this) {
 AlbumRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 AlbumRepository.SortBy.ALBUM_NAME -> context.kordx.t.Album
 AlbumRepository.SortBy.ARTIST_NAME -> context.kordx.t.Artist
 AlbumRepository.SortBy.TRACKS_COUNT -> context.kordx.t.TrackCount
 AlbumRepository.SortBy.YEAR -> context.kordx.t.Year
}
