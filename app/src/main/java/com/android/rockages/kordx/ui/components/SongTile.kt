package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.services.groove.createArtworkImageRequest
import com.android.rockages.kordx.ui.helpers.ViewContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongTile(
 context: ViewContext,
 song: Song,
 onClick: () -> Unit,
) {
 Card(
  modifier = Modifier
   .fillMaxWidth()
   .wrapContentHeight(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ),
  onClick = onClick,
 ) {
  Column(
   modifier = Modifier.padding(4.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
  ) {
   Box {
    AsyncImage(
     song.createArtworkImageRequest(context.kordx).build(),
     null,
     contentScale = ContentScale.Crop,
     modifier = Modifier
      .aspectRatio(1f)
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp)),
    )
    Box(
     modifier = Modifier
      .align(Alignment.BottomStart)
      .padding(2.dp)
    ) {
     IconButton(
      modifier = Modifier.size(32.dp),
      onClick = onClick,
     ) {
      Icon(
       Icons.Filled.PlayArrow,
       null,
       modifier = Modifier.size(16.dp),
       tint = MaterialTheme.colorScheme.onPrimary,
      )
     }
    }
   }
   Spacer(modifier = Modifier.height(8.dp))
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
  }
 }
}
