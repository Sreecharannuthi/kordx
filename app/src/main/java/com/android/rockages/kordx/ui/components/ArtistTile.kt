package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.android.rockages.kordx.services.groove.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.core.groove.Artist
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.ArtistViewRoute

@Composable
fun ArtistTile(context: ViewContext, artist: Artist) {
 SquareGrooveTile(
 image = artist.createArtworkImageRequest(context.kordx).build(),
 options = { expanded, onDismissRequest ->
 ArtistDropdownMenu(
 context,
 artist,
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 )
 },
 content = {
 Text(
 artist.name,
 style = MaterialTheme.typography.bodyMedium,
 textAlign = TextAlign.Center,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 // Subtitle: album and track counts
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
 textAlign = TextAlign.Center,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 }
 },
 onPlay = {
 context.kordx.radio.shorty.playQueue(artist.getSortedSongIds(context.kordx))
 },
 onClick = {
 context.navController.navigate(ArtistViewRoute(artist.name))
 }
 )
}

@Composable
fun ArtistDropdownMenu(
 context: ViewContext,
 artist: Artist,
 expanded: Boolean,
 onDismissRequest: () -> Unit,
) {
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
 artist.getSortedSongIds(context.kordx),
 shuffle = true
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
 artist.getSortedSongIds(context.kordx),
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
 context.kordx.radio.queue.add(artist.getSortedSongIds(context.kordx))
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
 songIds = artist.getSongIds(context.kordx),
 onDismissRequest = {
 showAddToPlaylistDialog = false
 }
 )
 }
}
