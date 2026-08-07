package com.android.rockages.kordx.ui.view.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.ui.components.AddToPlaylistDialog
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.components.MediaSortBar
import com.android.rockages.kordx.ui.components.MediaSortBarScaffold
import com.android.rockages.kordx.ui.components.ResponsiveGrid
import com.android.rockages.kordx.ui.components.ResponsiveGridColumns
import com.android.rockages.kordx.ui.components.ResponsiveGridSizeAdjustBottomSheet
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.SquareGrooveTile
import com.android.rockages.kordx.ui.components.label
import com.android.rockages.kordx.ui.helpers.Assets
import com.android.rockages.kordx.ui.helpers.FadeTransition
import com.android.rockages.kordx.ui.helpers.SlideTransition
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.nowPlaying.defaultHorizontalPadding
import com.android.rockages.kordx.core.utils.SimpleFileSystem
import com.android.rockages.kordx.core.utils.StringListUtils
import java.util.Stack

@Composable
fun FoldersView(context: ViewContext) {
 val isUpdating by context.kordx.groove.song.isUpdating.collectAsState()
 val id by context.kordx.groove.song.id.collectAsState()
 val explorer = context.kordx.groove.song.explorer

 val folders = remember(id) {
 val entities = mutableMapOf<String, SimpleFileSystem.Folder>()
 val stack = Stack<SimpleFileSystem.Folder>()
 stack.add(explorer)
 while (stack.isNotEmpty()) {
 val current = stack.pop()
 if (current.isEmpty) continue
 var hasSongs = false
 current.children.values.forEach {
 when (it) {
 is SimpleFileSystem.Folder -> stack.push(it)
 is SimpleFileSystem.File -> {
 hasSongs = true
 }
 }
 }
 if (hasSongs) {
 entities[current.fullPath.pathString] = current
 }
 }
 entities.toMap()
 }
 var currentFolder by remember(id) {
 mutableStateOf<SimpleFileSystem.Folder?>(null)
 }

 BackHandler(currentFolder != null) {
 currentFolder = null
 }

 LoaderScaffold(context, isLoading = isUpdating) {
 AnimatedContent(
 label = "folders-view-content",
 targetState = currentFolder,
 transitionSpec = {
 val enter = when {
 targetState != null -> SlideTransition.slideUp.enterTransition()
 else -> FadeTransition.enterTransition()
 }
 enter.togetherWith(FadeTransition.exitTransition())
 },
 ) { folder ->
 if (folder != null) {
 val songIds by remember(folder) {
 derivedStateOf {
 folder.children.values.mapNotNull {
 when (it) {
 is SimpleFileSystem.File -> it.data as String
 else -> null
 }
 }
 }
 }

 Column {
 Column(
 modifier = Modifier.padding(
 start = defaultHorizontalPadding,
 end = defaultHorizontalPadding,
 top = 4.dp,
 bottom = 12.dp,
 ),
 ) {
 folder.parent?.let { parent ->
 Text(
 "${parent.fullPath}/",
 style = MaterialTheme.typography.bodyMedium.copy(
 color = LocalContentColor.current.copy(alpha = 0.7f),
 ),
 )
 }
 Text(folder.name, style = MaterialTheme.typography.bodyLarge)
 }
 HorizontalDivider()
 SongList(context, songIds = songIds, songsCount = songIds.size)
 }
 } else {
 FoldersGrid(
 context,
 folders = folders,
 onClick = {
 currentFolder = it
 }
 )
 }
 }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldersGrid(
 context: ViewContext,
 folders: Map<String, SimpleFileSystem.Folder>,
 onClick: (SimpleFileSystem.Folder) -> Unit,
) {
 val sortBy by context.kordx.settings.lastUsedFoldersSortBy.flow.collectAsState()
 val sortReverse by context.kordx.settings.lastUsedFoldersSortReverse.flow.collectAsState()
 val sortedFolderNames by remember(folders, sortBy, sortReverse) {
 derivedStateOf {
 StringListUtils.sort(folders.keys.toList(), sortBy, sortReverse)
 }
 }
 val horizontalGridColumns by context.kordx.settings.lastUsedFoldersHorizontalGridColumns.flow.collectAsState()
 val verticalGridColumns by context.kordx.settings.lastUsedFoldersVerticalGridColumns.flow.collectAsState()
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
 context.kordx.settings.lastUsedFoldersSortReverse.setValue(it)
 },
 sort = sortBy,
 sorts = StringListUtils.SortBy.entries
 .associateWith { x -> ViewContext.parameterizedFn { x.label(context) } },
 onSortChange = {
 context.kordx.settings.lastUsedFoldersSortBy.setValue(it)
 },
 label = {
 Text(context.kordx.t.XFolders(folders.size.toString()))
 },
 onShowModifyLayout = {
 showModifyLayoutSheet = true
 }
 )
 },
 content = {
 when {
 sortedFolderNames.isEmpty() -> IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.FolderCopy,
 null,
 modifier = modifier,
 )
 },
 content = { Text(context.kordx.t.DamnThisIsSoEmpty) }
 )

 else -> ResponsiveGrid(gridColumns) {
 itemsIndexed(
 sortedFolderNames,
 key = { _, x -> x },
 contentType = { _, _ -> Groove.Kind.ARTIST }
 ) { _, folderName ->
 folders[folderName]?.let { folder ->
 FolderTile(
 context, folder = folder,
 onClick = { onClick(folder) },
 )
 }
 }
 }
 }

 if (showModifyLayoutSheet) {
 ResponsiveGridSizeAdjustBottomSheet(
 context,
 columns = gridColumns,
 onColumnsChange = {
 context.kordx.settings.lastUsedFoldersHorizontalGridColumns.setValue(
 it.horizontal
 )
 context.kordx.settings.lastUsedFoldersVerticalGridColumns.setValue(
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
private fun FolderTile(
 context: ViewContext,
 folder: SimpleFileSystem.Folder,
 onClick: () -> Unit,
) {
 SquareGrooveTile(
 image = folder.createArtworkImageRequest(context).build(),
 options = { expanded, onDismissRequest ->
 var showAddToPlaylistDialog by remember { mutableStateOf(false) }

 DropdownMenu(
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 containerColor = MaterialTheme.colorScheme.background,
 shape = RoundedCornerShape(12.dp),
 shadowElevation = 12.dp,
 offset = DpOffset((-4).dp, (-4).dp),
 ) {
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
 },
 text = {
 Text(context.kordx.t.ShufflePlay)
 },
 onClick = {
 onDismissRequest()
 context.kordx.radio.shorty.playQueue(
 folder.getSortedSongIds(context),
 shuffle = true,
 )
 }
 )
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
 },
 text = {
 Text(context.kordx.t.PlayNext)
 },
 onClick = {
 onDismissRequest()
 context.kordx.radio.queue.add(
 folder.getSortedSongIds(context),
 context.kordx.radio.queue.currentSongIndex + 1
 )
 }
 )
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
 },
 text = {
 Text(context.kordx.t.AddToPlaylist)
 },
 onClick = {
 onDismissRequest()
 showAddToPlaylistDialog = true
 }
 )
 }

 if (showAddToPlaylistDialog) {
 AddToPlaylistDialog(
 context,
 songIds = folder.getSortedSongIds(context),
 onDismissRequest = {
 showAddToPlaylistDialog = false
 }
 )
 }
 },
 content = {
 Text(
 folder.name,
 style = MaterialTheme.typography.bodyMedium,
 textAlign = TextAlign.Center,
 maxLines = 2,
 overflow = TextOverflow.Ellipsis,
 )
 },
 onPlay = {
 val sortedSongIds = folder.getSortedSongIds(context)
 context.kordx.radio.shorty.playQueue(sortedSongIds)
 },
 onClick = onClick,
 )
}

private fun SimpleFileSystem.Folder.createArtworkImageRequest(context: ViewContext) =
 children.values
 .find { it is SimpleFileSystem.File }
 ?.let {
 val songId = (it as SimpleFileSystem.File).data as String
 context.kordx.groove.song.createArtworkImageRequest(songId)
 }
 ?: Assets.createPlaceholderImageRequest(context.kordx)

private fun SimpleFileSystem.Folder.getSortedSongIds(context: ViewContext): List<String> {
 val songIds = children.values.mapNotNull {
 when (it) {
 is SimpleFileSystem.File -> it.data as String
 else -> null
 }
 }
 return context.kordx.groove.song.sort(
 songIds,
 context.kordx.settings.lastUsedSongsSortBy.value,
 context.kordx.settings.lastUsedSongsSortReverse.value,
 )
}
