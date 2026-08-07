package com.android.rockages.kordx.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.rockages.kordx.services.radio.RadioPlayer
import com.android.rockages.kordx.ui.helpers.FadeTransition
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.core.utils.TimedContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.timer

@Composable
fun LyricsText(
 context: ViewContext,
 padding: PaddingValues,
 style: TimedContentTextStyle,
) {
 val coroutineScope = rememberCoroutineScope()
 var playbackPosition by remember {
 mutableStateOf(
 context.kordx.radio.currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
 )
 }
 var playbackPositionTimer: Timer? = remember { null }
 val queue by context.kordx.radio.observatory.queue.collectAsState()
 val queueIndex by context.kordx.radio.observatory.queueIndex.collectAsState()
 val song by remember(queue, queueIndex) {
 derivedStateOf {
 queue.getOrNull(queueIndex)?.let { context.kordx.groove.song.get(it) }
 }
 }
 var lyricsState by remember { mutableIntStateOf(0) }
 var lyricsSongId by remember { mutableStateOf<String?>(null) }
 var lyrics by remember { mutableStateOf<TimedContent?>(null) }

 LaunchedEffect(LocalContext.current) {
 awaitAll(
 async {
 playbackPositionTimer = timer(period = 50L) {
 playbackPosition = context.kordx.radio.currentPlaybackPosition
 ?: RadioPlayer.PlaybackPosition.zero
 }
 },
 async {
 snapshotFlow { song }
 .distinctUntilChanged()
 .collect { song ->
 lyricsState = 1
 lyricsSongId = song?.id
 coroutineScope.launch {
 lyrics = song?.let { song ->
 context.kordx.groove.song.getLyrics(song)?.let {
 TimedContent.fromLyrics(it)
 }
 }
 lyricsState = 2
 }
 }
 }
 )
 }

 DisposableEffect(LocalContext.current) {
 onDispose {
 playbackPositionTimer?.cancel()
 }
 }

 AnimatedContent(
 label = "lyrics-text",
 targetState = lyricsState to lyrics,
 transitionSpec = {
 FadeTransition.enterTransition()
 .togetherWith(FadeTransition.exitTransition())
 },
 ) { targetState ->
 val targetLyricsState = targetState.first
 val targetLyrics = targetState.second

 when {
 targetLyricsState == 2 && targetLyrics != null -> TimedContentText(
 content = targetLyrics,
 duration = playbackPosition.played,
 padding = padding,
 style = style,
 onSeek = {
 targetLyrics.pairs.getOrNull(it)?.first?.let { to ->
 context.kordx.radio.seek(to)
 }
 }
 )

 else -> Box(
 modifier = Modifier.fillMaxSize(),
 contentAlignment = Alignment.Center,
 ) {
 Text(
 if (targetLyricsState == 1) context.kordx.t.Loading
 else context.kordx.t.NoLyrics
 )
 }
 }
 }
}
