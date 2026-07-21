package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.Canvas
import com.android.rockages.kordx.services.groove.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.core.groove.Album
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.SongCardThumbnailLabelStyle
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.SongListType
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
data class AlbumViewRoute(val albumId: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumView(context: ViewContext, route: AlbumViewRoute) {
 val allAlbumIds by context.kordx.groove.album.all.collectAsState()
 val allSongIds by context.kordx.groove.song.all.collectAsState()
 val album by remember(allAlbumIds) {
 derivedStateOf { context.kordx.groove.album.get(route.albumId) }
 }
 val songIds by remember(album, allSongIds) {
 derivedStateOf { album?.getSongIds(context.kordx) ?: emptyList() }
 }
 val isViable by remember(allAlbumIds) {
 derivedStateOf { allAlbumIds.contains(route.albumId) }
 }

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 navigationIcon = {
 IconButton(onClick = { context.navController.popBackStack() }) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 title = {
 TopAppBarMinimalTitle {
 Text(
 context.kordx.t.Album + (album?.let { " - ${it.name}" } ?: ""),
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
 type = SongListType.Album,
 leadingContent = {
 item {
 AlbumCompactHeader(context, album!!)
 HorizontalDivider()
 Spacer(modifier = Modifier.height(4.dp))
 }
 },
 cardThumbnailLabel = { _, song ->
 Text(song.trackNumber?.toString() ?: context.kordx.t.UnknownSymbol)
 },
 cardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Subtle,
 )
 } else UnknownAlbum(context, route.albumId)
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 },
 )
}

/**
 * Compact album header — small square artwork + name + artist + year/duration in a row.
 * Replaces the full-width [GenericGrooveBanner] to keep the focus on the song list.
 */
@Composable
private fun AlbumCompactHeader(context: ViewContext, album: Album) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier
 .fillMaxWidth()
 .padding(horizontal = 16.dp, vertical = 12.dp),
 ) {
 AsyncImage(
 album.createArtworkImageRequest(context.kordx).build(),
 null,
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .size(56.dp)
 .clip(RoundedCornerShape(8.dp)),
 )
 Spacer(modifier = Modifier.width(16.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
 album.name,
 style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 if (album.artists.isNotEmpty()) {
 Text(
 album.artists.joinToString(", "),
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 modifier = Modifier.pointerInput(Unit) {
 detectTapGestures { _ ->
 // Navigate to first artist on tap
 album.artists.firstOrNull()?.let {
 context.navController.navigate(ArtistViewRoute(it))
 }
 }
 },
 )
 }
 Row(
 horizontalArrangement = Arrangement.spacedBy(6.dp),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 album.startYear?.let { startYear ->
 val endYear = album.endYear
 Text(
 when {
 endYear == null || startYear == endYear -> startYear.toString()
 else -> "$startYear - $endYear"
 },
 style = MaterialTheme.typography.labelMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 CircleSeparator()
 }
 Text(
 album.duration.toString(),
 style = MaterialTheme.typography.labelMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 }
 }
}

@Composable
private fun UnknownAlbum(context: ViewContext, albumId: String) {
 IconTextBody(
 icon = { modifier ->
 Icon(Icons.Filled.Album, null, modifier = modifier)
 },
 content = {
 Text(context.kordx.t.UnknownAlbumX(albumId))
 }
 )
}

@Composable
private fun CircleSeparator() {
 val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

 Canvas(modifier = Modifier.size(4.dp)) {
 drawCircle(color)
 }
}
