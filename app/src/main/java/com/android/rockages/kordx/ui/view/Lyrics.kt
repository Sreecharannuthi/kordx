package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.KeepScreenAwake
import com.android.rockages.kordx.ui.components.LyricsText
import com.android.rockages.kordx.ui.components.TimedContentTextStyle
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.nowPlaying.NothingPlaying
import com.android.rockages.kordx.ui.view.nowPlaying.NowPlayingSeekBar
import com.android.rockages.kordx.ui.view.nowPlaying.NowPlayingTraditionalControls
import com.android.rockages.kordx.ui.view.nowPlaying.defaultHorizontalPadding
import kotlinx.serialization.Serializable

@Serializable
object LyricsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsView(context: ViewContext) {
 val keepScreenAwake by context.kordx.settings.lyricsKeepScreenAwake.flow.collectAsState()

 if (keepScreenAwake) {
 KeepScreenAwake()
 }

 NowPlayingObserver(context) { data ->
 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
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
 title = {
 TopAppBarMinimalTitle {
 Text(
 context.kordx.t.Lyrics +
 (data?.song?.title?.let { " - $it" } ?: "")
 )
 }
 },
 actions = {
 IconButtonPlaceholder()
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 )
 },
 ) { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize(),
 ) {
 when {
 data != null -> Column {
 Box(modifier = Modifier.weight(1f)) {
 LyricsText(
 context,
 style = TimedContentTextStyle(
 highlighted = MaterialTheme.typography.titleMedium.copy(
 color = LocalContentColor.current,
 ),
 active = MaterialTheme.typography.titleLarge.copy(
 fontWeight = FontWeight.Bold,
 color = MaterialTheme.colorScheme.primary,
 ),
 inactive = MaterialTheme.typography.titleMedium.copy(
 color = LocalContentColor.current.copy(alpha = 0.5f),
 ),
 spacing = 8.dp,
 ),
 padding = PaddingValues(
 horizontal = defaultHorizontalPadding,
 vertical = 12.dp,
 ),
 )
 }
 Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
 NowPlayingSeekBar(context)
 Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
 NowPlayingTraditionalControls(context, data = data)
 Spacer(modifier = Modifier.height(defaultHorizontalPadding + 8.dp))
 }

 else -> NothingPlaying(context)
 }
 }
 }
 }
}
