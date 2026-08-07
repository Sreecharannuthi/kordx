package com.android.rockages.kordx.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.services.groove.createArtworkImageRequest
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun SongTile(
 context: ViewContext,
 song: Song,
 onClick: () -> Unit,
) {
 val favoriteSongIds by context.kordx.groove.playlist.favorites.collectAsState()
 val isFavorite by remember(favoriteSongIds, song) {
  derivedStateOf { favoriteSongIds.contains(song.id) }
 }

 SquareGrooveTile(
  image = song.createArtworkImageRequest(context.kordx).build(),
  options = { expanded, onDismissRequest ->
   SongDropdownMenu(
    context,
    song,
    isFavorite = isFavorite,
    expanded = expanded,
    onDismissRequest = onDismissRequest,
   )
  },
  content = {
   Text(
    song.title,
    style = MaterialTheme.typography.bodyMedium,
    textAlign = TextAlign.Center,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
   )
   if (song.artists.isNotEmpty()) {
    Text(
     song.artists.joinToString(),
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant,
     textAlign = TextAlign.Center,
     maxLines = 1,
     overflow = TextOverflow.Ellipsis,
    )
   }
  },
  onPlay = onClick,
  onClick = onClick,
 )
}
