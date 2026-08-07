package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.services.groove.repositories.SongRepository
import com.android.rockages.kordx.services.radio.Radio
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.SettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.GrooveSettingsViewRoute

enum class SongListType {
 Default,
 Playlist,
 Album,
}

@Composable
fun SongList(
 context: ViewContext,
 songIds: List<String>,
 songsCount: Int? = null,
 leadingContent: (LazyListScope.() -> Unit)? = null,
 trailingContent: (LazyListScope.() -> Unit)? = null,
 trailingOptionsContent: (@Composable ColumnScope.(Int, Song, () -> Unit) -> Unit)? = null,
 cardThumbnailLabel: (@Composable (Int, Song) -> Unit)? = null,
 cardThumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
 type: SongListType = SongListType.Default,
 disableHeartIcon: Boolean = false,
 enableAddMediaFoldersHint: Boolean = false,
) {
 val sortBy by type.getLastUsedSortBy(context).flow.collectAsState()
 val sortReverse by type.getLastUsedSortReverse(context).flow.collectAsState()
 val sortedSongIds by remember(songIds, sortBy, sortReverse) {
 derivedStateOf {
 context.kordx.groove.song.sort(songIds, sortBy, sortReverse)
 }
 }

 MediaSortBarScaffold(
 mediaSortBar = {
 MediaSortBar(
 context,
 reverse = sortReverse,
 onReverseChange = {
 type.setLastUsedSortReverse(context, it)
 },
 sort = sortBy,
 sorts = SongRepository.SortBy.entries
 .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
 onSortChange = {
 type.setLastUsedSortBy(context, it)
 },
 label = {
 Text(context.kordx.t.XSongs((songsCount ?: songIds.size).toString()))
 },
 onShufflePlay = {
 context.kordx.radio.shorty.playQueue(sortedSongIds, shuffle = true)
 }
 )
 },
 content = {
 when {
 songIds.isEmpty() -> IconTextBody(
 icon = { modifier ->
 Icon(Icons.Filled.MusicNote, null, modifier = modifier)
 },
 content = {
 Text(context.kordx.t.DamnThisIsSoEmpty)
 if (enableAddMediaFoldersHint) {
 Spacer(modifier = Modifier.height(4.dp))
 Text(
 context.kordx.t.HintAddMediaFolders,
 style = MaterialTheme.typography.labelMedium,
 textAlign = TextAlign.Center,
 modifier = Modifier
 .clickable {
 context.navController.navigate(
 GrooveSettingsViewRoute(SettingsViewRoute.ELEMENT_MEDIA_FOLDERS)
 )
 }
 .padding(2.dp),
 )
 }
 }
 )

 else -> {
 val lazyListState = rememberLazyListState()

 LazyColumn(
 state = lazyListState,
 modifier = Modifier.drawScrollBar(lazyListState)
 ) {
 leadingContent?.invoke(this)
 itemsIndexed(
 sortedSongIds,
 key = { _, x -> x },
 contentType = { _, _ -> Groove.Kind.SONG }
 ) { i, songId ->
 context.kordx.groove.song.get(songId)?.let { song ->
 Box(modifier = Modifier.animateItem()) {
 SongCard(
 context,
 song = song,
 thumbnailLabel = cardThumbnailLabel?.let {
 { it(i, song) }
 },
 thumbnailLabelStyle = cardThumbnailLabelStyle,
 disableHeartIcon = disableHeartIcon,
 trailingOptionsContent = trailingOptionsContent?.let {
 { onDismissRequest -> it(i, song, onDismissRequest) }
 },
 ) {
 context.kordx.radio.shorty.playQueue(
 sortedSongIds,
 Radio.PlayOptions(index = i)
 )
 }
 }
 }
 }
 trailingContent?.invoke(this)
 }
 }
 }
 }
 )
}

fun SongRepository.SortBy.label(context: ViewContext) = when (this) {
 SongRepository.SortBy.CUSTOM -> context.kordx.t.Custom
 SongRepository.SortBy.TITLE -> context.kordx.t.Title
 SongRepository.SortBy.ARTIST -> context.kordx.t.Artist
 SongRepository.SortBy.ALBUM -> context.kordx.t.Album
 SongRepository.SortBy.DURATION -> context.kordx.t.Duration
 SongRepository.SortBy.DATE_MODIFIED -> context.kordx.t.LastModified
 SongRepository.SortBy.COMPOSER -> context.kordx.t.Composer
 SongRepository.SortBy.ALBUM_ARTIST -> context.kordx.t.AlbumArtist
 SongRepository.SortBy.YEAR -> context.kordx.t.Year
 SongRepository.SortBy.FILENAME -> context.kordx.t.Filename
 SongRepository.SortBy.TRACK_NUMBER -> context.kordx.t.TrackNumber
}

fun SongListType.getLastUsedSortBy(context: ViewContext) = when (this) {
 SongListType.Default -> context.kordx.settings.lastUsedSongsSortBy
 SongListType.Album -> context.kordx.settings.lastUsedAlbumSongsSortBy
 SongListType.Playlist -> context.kordx.settings.lastUsedPlaylistSongsSortBy
}

fun SongListType.setLastUsedSortBy(context: ViewContext, sort: SongRepository.SortBy) =
 when (this) {
 SongListType.Default -> context.kordx.settings.lastUsedSongsSortBy.setValue(sort)
 SongListType.Playlist -> context.kordx.settings.lastUsedPlaylistSongsSortBy.setValue(sort)
 SongListType.Album -> context.kordx.settings.lastUsedAlbumSongsSortBy.setValue(sort)
 }

fun SongListType.getLastUsedSortReverse(context: ViewContext) = when (this) {
 SongListType.Default -> context.kordx.settings.lastUsedSongsSortReverse
 SongListType.Playlist -> context.kordx.settings.lastUsedPlaylistSongsSortReverse
 SongListType.Album -> context.kordx.settings.lastUsedAlbumSongsSortReverse
}

fun SongListType.setLastUsedSortReverse(context: ViewContext, reverse: Boolean) = when (this) {
 SongListType.Default -> context.kordx.settings.lastUsedSongsSortReverse.setValue(reverse)
 SongListType.Playlist -> context.kordx.settings.lastUsedPlaylistSongsSortReverse.setValue(
 reverse
 )

 SongListType.Album -> context.kordx.settings.lastUsedAlbumSongsSortReverse.setValue(reverse)
}
