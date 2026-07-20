package com.android.rockages.kordx.ui.components

import android.widget.Toast
import com.android.rockages.kordx.services.groove.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.services.groove.MediaExposer
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.theme.ThemeColors
import com.android.rockages.kordx.ui.view.PlaylistViewRoute
import com.android.rockages.kordx.core.utils.Logger

@Composable
fun PlaylistTile(context: ViewContext, playlist: Playlist) {
 val updateId by context.kordx.groove.playlist.updateId.collectAsState()

 Card(
 modifier = Modifier
 .fillMaxWidth()
 .wrapContentHeight(),
 colors = CardDefaults.cardColors(containerColor = Color.Transparent),
 onClick = {
 context.navController.navigate(PlaylistViewRoute(playlist.id))
 }
 ) {
 Box(modifier = Modifier.padding(4.dp)) {
 Column(horizontalAlignment = Alignment.CenterHorizontally) {
 Box {
 AsyncImage(
 // TODO: remove this hack after moving to reactive objects
 remember(updateId, playlist) {
 playlist.createArtworkImageRequest(context.kordx).build()
 },
 null,
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .aspectRatio(1f)
 .fillMaxWidth()
 .clip(RoundedCornerShape(12.dp)),
 )
 Box(
 modifier = Modifier
 .align(Alignment.TopEnd)
 .padding(top = 4.dp)
 ) {
 var showOptionsMenu by remember { mutableStateOf(false) }
 IconButton(
 onClick = { showOptionsMenu = !showOptionsMenu }
 ) {
 Icon(Icons.Filled.MoreVert, null)
 PlaylistDropdownMenu(
 context,
 playlist,
 expanded = showOptionsMenu,
 onDismissRequest = {
 showOptionsMenu = false
 }
 )
 }
 }
 Box(
 modifier = Modifier
 .align(Alignment.BottomStart)
 .padding(8.dp)
 ) {
 IconButton(
 onClick = {
 context.kordx.radio.shorty.playQueue(
 playlist.getSortedSongIds(context.kordx)
 )
 }
 ) {
 Box(
 modifier = Modifier
 .size(28.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(MaterialTheme.colorScheme.surface),
 contentAlignment = Alignment.Center,
 ) {
 Icon(
 Icons.Filled.PlayArrow,
 null,
 modifier = Modifier.size(18.dp),
 )
 }
 }
 }
 }
 Spacer(modifier = Modifier.height(8.dp))
 Text(
 playlist.title,
 style = MaterialTheme.typography.bodyMedium,
 textAlign = TextAlign.Center,
 )
 }
 }
 }
}

@Composable
fun PlaylistDropdownMenu(
 context: ViewContext,
 playlist: Playlist,
 expanded: Boolean,
 onSongsChanged: (() -> Unit) = {},
 onRename: (() -> Unit) = {},
 onDelete: (() -> Unit) = {},
 onDismissRequest: () -> Unit,
) {
 val savePlaylistLauncher = rememberLauncherForActivityResult(
 ActivityResultContracts.CreateDocument(MediaExposer.MIMETYPE_M3U)
 ) { uri ->
 uri?.let { _ ->
 try {
 context.kordx.groove.playlist.savePlaylistToUri(playlist, uri)
 Toast.makeText(
 context.activity,
 context.kordx.t.ExportedX(playlist.title),
 Toast.LENGTH_SHORT,
 ).show()
 } catch (err: Exception) {
 Logger.error("PlaylistTile", "export failed (activity result)", err)
 Toast.makeText(
 context.activity,
 context.kordx.t.ExportFailedX(
 err.localizedMessage ?: err.toString()
 ),
 Toast.LENGTH_SHORT,
 ).show()
 }
 }
 }

 var showSongsPicker by remember { mutableStateOf(false) }
 var showInfoDialog by remember { mutableStateOf(false) }
 var showDeleteDialog by remember { mutableStateOf(false) }
 var showAddToPlaylistDialog by remember { mutableStateOf(false) }
 var showRenameDialog by remember { mutableStateOf(false) }

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
 playlist.getSortedSongIds(context.kordx),
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
 playlist.getSortedSongIds(context.kordx),
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
 context.kordx.radio.queue.add(playlist.getSongIds(context.kordx))
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
 HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
 if (playlist.isNotLocal) {
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
 },
 text = {
 Text(context.kordx.t.ManageSongs)
 },
 onClick = {
 onDismissRequest()
 showSongsPicker = true
 }
 )
 }
 if (playlist.isNotLocal) {
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Save, null)
 },
 text = {
 Text(context.kordx.t.Export)
 },
 onClick = {
 onDismissRequest()
 try {
 savePlaylistLauncher.launch("${playlist.title}.m3u")
 } catch (err: Exception) {
 Logger.error("PlaylistTile", "export failed", err)
 Toast.makeText(
 context.activity,
 context.kordx.t.ExportFailedX(
 err.localizedMessage ?: err.toString()
 ),
 Toast.LENGTH_SHORT
 ).show()
 }
 }
 )
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(Icons.Filled.Edit, null)
 },
 text = {
 Text(context.kordx.t.Rename)
 },
 onClick = {
 onDismissRequest()
 showRenameDialog = true
 }
 )
 }
 if (!context.kordx.groove.playlist.isBuiltInPlaylist(playlist)) {
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(
 Icons.Filled.DeleteForever,
 null,
 tint = ThemeColors.Red,
 )
 },
 text = {
 Text(context.kordx.t.Delete)
 },
 onClick = {
 onDismissRequest()
 showDeleteDialog = true
 }
 )
 }
 HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
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
 }

 if (showInfoDialog) {
 PlaylistInformationDialog(
 context,
 playlist = playlist,
 onDismissRequest = {
 showInfoDialog = false
 }
 )
 }

 if (showSongsPicker) {
 PlaylistManageSongsDialog(
 context,
 selectedSongIds = playlist.getSongIds(context.kordx),
 onDone = {
 context.kordx.groove.playlist.update(playlist.id, it)
 onSongsChanged()
 showSongsPicker = false
 }
 )
 }

 if (showDeleteDialog) {
 ConfirmationDialog(
 context,
 title = {
 Text(context.kordx.t.DeletePlaylist)
 },
 description = {
 Text(context.kordx.t.AreYouSureThatYouWantToDeleteThisPlaylist)
 },
 onResult = { result ->
 showDeleteDialog = false
 if (result) {
 onDelete()
 context.kordx.groove.playlist.delete(playlist.id)
 }
 }
 )
 }

 if (showAddToPlaylistDialog) {
 AddToPlaylistDialog(
 context,
 songIds = playlist.getSongIds(context.kordx),
 onDismissRequest = {
 showAddToPlaylistDialog = false
 }
 )
 }

 if (showRenameDialog) {
 RenamePlaylistDialog(
 context,
 playlist = playlist,
 onRename = onRename,
 onDismissRequest = {
 showRenameDialog = false
 }
 )
 }
}
