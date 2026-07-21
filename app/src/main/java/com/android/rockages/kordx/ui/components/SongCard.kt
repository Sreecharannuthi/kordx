package com.android.rockages.kordx.ui.components

import android.content.Intent
import com.android.rockages.kordx.services.groove.*
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.AlbumArtistViewRoute
import com.android.rockages.kordx.ui.view.AlbumViewRoute
import com.android.rockages.kordx.ui.view.ArtistViewRoute
import com.android.rockages.kordx.core.utils.Logger

@Composable
fun SongCard(
 context: ViewContext,
 song: Song,
 highlighted: Boolean = false,
 autoHighlight: Boolean = true,
 disableHeartIcon: Boolean = false,
 leading: @Composable () -> Unit = {},
 thumbnailLabel: (@Composable () -> Unit)? = null,
 thumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
 trailingOptionsContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
 onClick: () -> Unit,
) {
 val queue by context.kordx.radio.observatory.queue.collectAsState()
 val queueIndex by context.kordx.radio.observatory.queueIndex.collectAsState()
 val isCurrentPlaying by remember(autoHighlight, song, queue) {
 derivedStateOf { autoHighlight && song.id == queue.getOrNull(queueIndex) }
 }
 val favoriteSongIds by context.kordx.groove.playlist.favorites.collectAsState()
 val isFavorite by remember(favoriteSongIds, song) {
 derivedStateOf { favoriteSongIds.contains(song.id) }
 }

 Card(
 modifier = Modifier.fillMaxWidth(),
 colors = CardDefaults.cardColors(containerColor = Color.Transparent),
 onClick = onClick
 ) {
 Box(modifier = Modifier.padding(12.dp, 8.dp, 4.dp, 8.dp)) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 leading()
 Box {
 AsyncImage(
 song.createArtworkImageRequest(context.kordx).build(),
 null,
 modifier = Modifier
 .size(36.dp)
 .clip(RoundedCornerShape(8.dp)),
 )
 thumbnailLabel?.let { it ->
 val backgroundColor =
 thumbnailLabelStyle.backgroundColor(MaterialTheme.colorScheme)
 val contentColor =
 thumbnailLabelStyle.contentColor(MaterialTheme.colorScheme)

 Box(
 modifier = Modifier
 .offset(y = 8.dp)
 .align(Alignment.BottomCenter)
 ) {
 Box(
 modifier = Modifier
 .background(
 backgroundColor,
 RoundedCornerShape(4.dp)
 )
 .padding(3.dp, 0.dp)
 ) {
 ProvideTextStyle(
 MaterialTheme.typography.labelSmall.copy(
 color = contentColor )
 ) { it() }
 }
 }
 }
 }
 Spacer(modifier = Modifier.width(16.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
 song.title,
 style = MaterialTheme.typography.bodyMedium.copy(
 color = when {
 highlighted || isCurrentPlaying -> MaterialTheme.colorScheme.primary
 else -> LocalTextStyle.current.color
 }
 ),
 maxLines = 2,
 overflow = TextOverflow.Ellipsis,
 )
 if (song.artists.isNotEmpty()) {
 Text(
 song.artists.joinToString(),
 style = MaterialTheme.typography.bodySmall,
 maxLines = 2,
 overflow = TextOverflow.Ellipsis,
 )
 }
 }
 Spacer(modifier = Modifier.width(15.dp))

 Row {
 if (!disableHeartIcon && isFavorite) {
 IconButton(
 modifier = Modifier.offset(4.dp, 0.dp),
 onClick = {
 context.kordx.groove.playlist.unfavorite(song.id)
 }
 ) {
 Icon(
 Icons.Filled.Favorite,
 null,
 modifier = Modifier.size(24.dp),
 tint = MaterialTheme.colorScheme.primary,
 )
 }
 }

 var showOptionsMenu by remember { mutableStateOf(false) }
 IconButton(
 onClick = { showOptionsMenu = !showOptionsMenu }
 ) {
 Icon(
 Icons.Filled.MoreVert,
 null,
 modifier = Modifier.size(24.dp),
 )
 SongDropdownMenu(
 context,
 song,
 isFavorite = isFavorite,
 trailingContent = trailingOptionsContent,
 expanded = showOptionsMenu,
 onDismissRequest = {
 showOptionsMenu = false
 }
 )
 }
 }
 }
 }
 }
}

@Suppress("CyclomaticComplexMethod")
@Composable
fun SongDropdownMenu(
 context: ViewContext,
 song: Song,
 isFavorite: Boolean,
 trailingContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
 expanded: Boolean,
 onDismissRequest: () -> Unit,
) {
 var showInfoDialog by remember { mutableStateOf(false) }
 var showAddToPlaylistDialog by remember { mutableStateOf(false) }

 // Deduplicate: collect unique artist names across artists and albumArtists,
 // using normalized keys to avoid showing the same person twice.
 val navigateToArtists = remember(song.artists, song.albumArtists) {
 val seen = linkedSetOf<String>() // preserves insertion order
 val result = mutableListOf<Pair<String, String>>() // displayName -> route
 // Artists first
 for (name in song.artists) {
 val norm = Song.normalizeArtistKey(name)
 if (seen.add(norm)) {
 result.add(name to "artist")
 }
 }
 // Album artists that aren't already listed
 for (name in song.albumArtists) {
 val norm = Song.normalizeArtistKey(name)
 if (seen.add(norm)) {
 result.add(name to "albumArtist")
 }
 }
 result
 }
 val albumId = remember(song) {
 context.kordx.groove.album.getIdFromSong(song)
 }
 val hasNavigationItems = navigateToArtists.isNotEmpty() || albumId != null

 DropdownMenu(
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 containerColor = MaterialTheme.colorScheme.background,
 shape = RoundedCornerShape(12.dp),
 shadowElevation = 12.dp,
 offset = DpOffset((-4).dp, (-4).dp),
 ) {
 // --- Song actions ---
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Favorite, null)
 },
 text = {
 Text(
 if (isFavorite) context.kordx.t.Unfavorite
 else context.kordx.t.Favorite
 )
 },
 onClick = {
 onDismissRequest()
 context.kordx.groove.playlist.run {
 when {
 isFavorite -> unfavorite(song.id)
 else -> favorite(song.id)
 }
 }
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
 song.id,
 context.kordx.radio.queue.currentSongIndex + 1
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
 Text(context.kordx.t.AddToQueue)
 },
 onClick = {
 onDismissRequest()
 context.kordx.radio.queue.add(song.id)
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

 // --- Navigation section (deduplicated) ---
 if (hasNavigationItems) {
 HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
 }

 // Artists (each as a single row, plain name, no "View Artist:" prefix)
 navigateToArtists.forEach { (displayName, routeType) ->
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Person, null)
 },
 text = {
 Text(displayName)
 },
 onClick = {
 onDismissRequest()
 when (routeType) {
 "artist" -> context.navController.navigate(ArtistViewRoute(displayName))
 "albumArtist" -> context.navController.navigate(AlbumArtistViewRoute(displayName))
 }
 }
 )
 }

 // Album (single row)
 albumId?.let { id ->
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Album, null)
 },
 text = {
 Text(song.album ?: context.kordx.t.ViewAlbum)
 },
 onClick = {
 onDismissRequest()
 context.navController.navigate(AlbumViewRoute(id))
 }
 )
 }

 if (hasNavigationItems) {
 HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
 }

 // --- Utility actions ---
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Share, null)
 },
 text = {
 Text(context.kordx.t.ShareSong)
 },
 onClick = {
 onDismissRequest()
 try {
 val intent = Intent(Intent.ACTION_SEND).apply {
 addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 putExtra(Intent.EXTRA_STREAM, song.uri)
 type = context.activity.contentResolver.getType(song.uri)
 }
 context.activity.startActivity(intent)
 } catch (err: Exception) {
 Logger.error("SongCard", "share failed", err)
 Toast.makeText(
 context.activity,
 context.kordx.t.ShareFailedX(err.localizedMessage ?: err.toString()),
 Toast.LENGTH_SHORT,
 ).show()
 }
 }
 )
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Info, null)
 },
 text = {
 Text(context.kordx.t.Details)
 },
 onClick = {
 onDismissRequest()
 showInfoDialog = true
 }
 )
 trailingContent?.invoke(this, onDismissRequest)
 }

 if (showInfoDialog) {
 SongInformationDialog(
 context,
 song = song,
 onDismissRequest = {
 showInfoDialog = false
 }
 )
 }

 if (showAddToPlaylistDialog) {
 AddToPlaylistDialog(
 context,
 songIds = listOf(song.id),
 onDismissRequest = {
 showAddToPlaylistDialog = false
 }
 )
 }
}

enum class SongCardThumbnailLabelStyle {
 Default,
 Subtle,
}

private fun SongCardThumbnailLabelStyle.backgroundColor(colorScheme: ColorScheme) = when (this) {
 SongCardThumbnailLabelStyle.Default -> colorScheme.surfaceVariant
 SongCardThumbnailLabelStyle.Subtle -> colorScheme.surfaceVariant
}

private fun SongCardThumbnailLabelStyle.contentColor(colorScheme: ColorScheme) = when (this) {
 SongCardThumbnailLabelStyle.Default -> colorScheme.primary
 SongCardThumbnailLabelStyle.Subtle -> colorScheme.onSurfaceVariant
}
