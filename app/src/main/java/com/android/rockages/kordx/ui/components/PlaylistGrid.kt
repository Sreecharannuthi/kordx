package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import com.android.rockages.kordx.services.groove.repositories.PlaylistRepository
import com.android.rockages.kordx.ui.helpers.ViewContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistGrid(
 context: ViewContext,
 playlistIds: List<String>,
 playlistsCount: Int? = null,
 leadingContent: @Composable () -> Unit = {},
) {
 val sortBy by context.kordx.settings.lastUsedPlaylistsSortBy.flow.collectAsState()
 val sortReverse by context.kordx.settings.lastUsedPlaylistsSortReverse.flow.collectAsState()
 val sortedPlaylistIds by remember(playlistIds, sortBy, sortReverse) {
 derivedStateOf {
 context.kordx.groove.playlist.sort(playlistIds, sortBy, sortReverse)
 }
 }
 val horizontalGridColumns by context.kordx.settings.lastUsedPlaylistsHorizontalGridColumns.flow.collectAsState()
 val verticalGridColumns by context.kordx.settings.lastUsedPlaylistsVerticalGridColumns.flow.collectAsState()
 val gridColumns by remember(horizontalGridColumns, verticalGridColumns) {
 derivedStateOf {
 ResponsiveGridColumns(horizontalGridColumns, verticalGridColumns)
 }
 }
 var showModifyLayoutSheet by remember { mutableStateOf(false) }

 MediaSortBarScaffold(
 mediaSortBar = {
 Column {
 leadingContent()
 MediaSortBar(
 context,
 reverse = sortReverse,
 onReverseChange = {
 context.kordx.settings.lastUsedPlaylistsSortReverse.setValue(it)
 },
 sort = sortBy,
 sorts = PlaylistRepository.SortBy.entries
 .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
 onSortChange = {
 context.kordx.settings.lastUsedPlaylistsSortBy.setValue(it)
 },
 label = {
 Text(
 context.kordx.t.XPlaylists(
 (playlistsCount ?: playlistIds.size).toString()
 )
 )
 },
 onShowModifyLayout = {
 showModifyLayoutSheet = true
 },
 )
 }
 },
 content = {
 when {
 playlistIds.isEmpty() -> IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.AutoMirrored.Filled.QueueMusic,
 null,
 modifier = modifier,
 )
 },
 content = {
 Text(context.kordx.t.DamnThisIsSoEmpty)
 }
 )

 else -> ResponsiveGrid(gridColumns) {
 itemsIndexed(
 sortedPlaylistIds,
 key = { _, x -> x },
 contentType = { _, _ -> Groove.Kind.PLAYLIST }
 ) { _, playlistId ->
 context.kordx.groove.playlist.get(playlistId)?.let { playlist ->
 PlaylistTile(context, playlist)
 }
 }
 }
 }

 if (showModifyLayoutSheet) {
 ResponsiveGridSizeAdjustBottomSheet(
 context,
 columns = gridColumns,
 onColumnsChange = {
 context.kordx.settings.lastUsedPlaylistsHorizontalGridColumns.setValue(
 it.horizontal
 )
 context.kordx.settings.lastUsedPlaylistsVerticalGridColumns.setValue(
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

private fun PlaylistRepository.SortBy.label(context: ViewContext) = when (this) {
 PlaylistRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 PlaylistRepository.SortBy.TITLE -> context.kordx.t.Title
 PlaylistRepository.SortBy.TRACKS_COUNT -> context.kordx.t.TrackCount
}
