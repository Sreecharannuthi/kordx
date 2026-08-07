package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.ui.components.IconButtonPlaceholderSize
import com.android.rockages.kordx.ui.components.NewPlaylistDialog
import com.android.rockages.kordx.ui.components.SongCard
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.nowPlaying.NothingPlayingBody
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object QueueViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueView(context: ViewContext) {
 val coroutineScope = rememberCoroutineScope()
 val queue by context.kordx.radio.observatory.queue.collectAsState()
 val queueIndex by context.kordx.radio.observatory.queueIndex.collectAsState()
 val selectedSongIds = remember { mutableStateListOf<String>() }
 val listState = rememberLazyListState(
 initialFirstVisibleItemIndex = queueIndex,
 )
 var showSaveDialog by remember { mutableStateOf(false) }

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 title = {
 TopAppBarMinimalTitle(
 modifier = Modifier.padding(start = IconButtonPlaceholderSize)
 ) {
 Text(context.kordx.t.Queue)
 }
 },
 colors = TopAppBarDefaults.mediumTopAppBarColors(
 containerColor = Color.Transparent
 ),
 navigationIcon = {
 IconButton(
 onClick = {
 context.navController.popBackStack()
 }
 ) {
 Icon(
 Icons.Filled.ExpandMore,
 null,
 modifier = Modifier.size(32.dp)
 )
 }
 },
 actions = {
 when {
 selectedSongIds.isNotEmpty() -> IconButton(
 onClick = {
 context.kordx.radio.queue.removeByIds(selectedSongIds.toList())
 selectedSongIds.clear()
 }
 ) {
 Icon(Icons.Filled.Delete, null)
 }

 else -> IconButton(
 onClick = {
 showSaveDialog = !showSaveDialog
 }
 ) {
 Icon(Icons.Default.Save, null)
 }
 }

 IconButton(
 onClick = {
 context.kordx.radio.stop(ended = true)
 selectedSongIds.clear()
 }
 ) {
 Icon(Icons.Filled.ClearAll, null)
 }
 }
 )
 },
 content = { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize()
 ) {
 if (queue.isEmpty()) {
 NothingPlayingBody(context)
 } else {
 LazyColumn(state = listState) {
 itemsIndexed(
 queue,
 key = { _, id -> id },
 contentType = { _, _ -> Groove.Kind.SONG },
 ) { i, songId ->
 context.kordx.groove.song.get(songId)?.let { song ->
 Box {
 SongCard(
 context,
 song,
 autoHighlight = false,
 highlighted = i == queueIndex,
 leading = {
 Checkbox(
 checked = selectedSongIds.contains(songId),
 onCheckedChange = {
 if (selectedSongIds.contains(songId)) {
 selectedSongIds.remove(songId)
 } else {
 selectedSongIds.add(songId)
 }
 },
 modifier = Modifier.offset((-4).dp)
 )
 Spacer(modifier = Modifier.width(8.dp))
 },
 thumbnailLabel = {
 Text((i + 1).toString())
 },
 onClick = {
 context.kordx.radio.jumpTo(i)
 coroutineScope.launch {
 listState.animateScrollToItem(i)
 }
 },
 )
 if (i < queueIndex) {
 Box(
 modifier = Modifier
 .matchParentSize()
 .background(
 MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
 )
 )
 }
 }
 }
 }
 }
 }
 }
 }
 )

 if (showSaveDialog) {
 NewPlaylistDialog(
 context,
 initialSongIds = queue.toList(),
 onDone = { playlist ->
 showSaveDialog = false
 context.kordx.groove.playlist.add(playlist)
 },
 onDismissRequest = {
 showSaveDialog = false
 }
 )
 }
}
