package com.android.rockages.kordx.ui.components

import androidx.compose.animation.AnimatedContent
import com.android.rockages.kordx.services.groove.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.FadeTransition
import com.android.rockages.kordx.ui.helpers.TransitionDurations
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.NowPlayingViewRoute
import com.android.rockages.kordx.core.utils.runIfOrThis
import kotlin.math.absoluteValue

@Composable
fun AnimatedNowPlayingBottomBar(context: ViewContext, insetPadding: Boolean = true) {
 val visible = remember {
 MutableTransitionState(false).apply {
 // Start the animation immediately.
 targetState = true
 }
 }

 AnimatedVisibility(
 visibleState = visible,
 enter = slideInVertically(
 animationSpec = nowPlayingBottomBarEnterAnimationSpec(),
 initialOffsetY = { it / 2 },
 ) + fadeIn(animationSpec = nowPlayingBottomBarEnterAnimationSpec()),
 exit = fadeOut(),
 ) {
 NowPlayingBottomBar(context, insetPadding)
 }
}

private fun <T> nowPlayingBottomBarEnterAnimationSpec() = TransitionDurations.Normal.asTween<T>(
 delayMillis = TransitionDurations.Fast.milliseconds,
)

@Composable
fun NowPlayingBottomBar(context: ViewContext, insetPadding: Boolean = true) {
 val queue by context.kordx.radio.observatory.queue.collectAsState()
 val queueIndex by context.kordx.radio.observatory.queueIndex.collectAsState()
 val currentPlayingSong by remember(queue, queueIndex) {
 derivedStateOf {
 queue.getOrNull(queueIndex)?.let { context.kordx.groove.song.get(it) }
 }
 }
 val isPlaying by context.kordx.radio.observatory.isPlaying.collectAsState()
 val playbackPosition by context.kordx.radio.observatory.playbackPosition.collectAsState()
 val showTrackControls by context.kordx.settings.miniPlayerTrackControls.flow.collectAsState()
 val showSeekControls by context.kordx.settings.miniPlayerSeekControls.flow.collectAsState()
 val seekBackDuration by context.kordx.settings.seekBackDuration.flow.collectAsState()
 val seekForwardDuration by context.kordx.settings.seekForwardDuration.flow.collectAsState()

 AnimatedContent(
 modifier = Modifier.fillMaxWidth(),
 label = "c-now-playing-container",
 targetState = currentPlayingSong,
 contentKey = { it != null },
 transitionSpec = {
 val from = slideInVertically() + fadeIn()
 val to = slideOutVertically() + fadeOut()
 from togetherWith to
 }
 ) { currentPlayingSongTarget ->
 currentPlayingSongTarget?.let { currentSong ->
 Column {
     BoxWithConstraints(
         modifier = Modifier
             .fillMaxWidth()
             .height(4.dp)
             .padding(horizontal = 12.dp)
             .clip(RoundedCornerShape(2.dp))
             .background(MaterialTheme.colorScheme.surfaceVariant)
     ) {
         val progressWidth = maxWidth * playbackPosition.ratio.coerceIn(0f, 1f)
         Box(
             modifier = Modifier
                 .width(progressWidth)
                 .fillMaxHeight()
                 .background(MaterialTheme.colorScheme.primary)
         )
         if (playbackPosition.ratio > 0.01f) {
             Box(
                 modifier = Modifier
                     .offset(x = progressWidth - 4.dp)
                     .size(8.dp)
                     .clip(CircleShape)
                     .background(MaterialTheme.colorScheme.primary)
                     .align(Alignment.CenterStart)
             )
         }
     }
 ElevatedCard(
 modifier = Modifier
 .fillMaxWidth()
 .wrapContentHeight()
 .swipeable(
 onSwipeUp = {
 context.navController.navigate(NowPlayingViewRoute)
 },
 onSwipeDown = {
 context.kordx.radio.stop(ended = true)
 },
 ),
 shape = RectangleShape,
 onClick = {
 context.navController.navigate(NowPlayingViewRoute)
 }
 ) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier.padding(0.dp, 8.dp),
 ) {
 Spacer(modifier = Modifier.width(12.dp))
 AnimatedContent(
 label = "c-now-playing-card-image",
 targetState = currentSong,
 contentKey = { it.id },
 transitionSpec = {
 val from = fadeIn(
 animationSpec = TransitionDurations.Normal.asTween(
 delayMillis = TransitionDurations.Normal.milliseconds,
 ),
 )
 val to = fadeOut(TransitionDurations.Normal.asTween())
 from togetherWith to
 },
 ) { song ->
 AsyncImage(
 song.createArtworkImageRequest(context.kordx).build(),
 null,
 modifier = Modifier
 .size(45.dp)
 .clip(RoundedCornerShape(12.dp))
 )
 }
 Spacer(modifier = Modifier.width(15.dp))
 AnimatedContent(
 label = "c-now-playing-card-content",
 modifier = Modifier.weight(1f),
 targetState = currentSong,
 contentKey = { it.id },
 transitionSpec = {
 val from = fadeIn(
 animationSpec = TransitionDurations.Normal.asTween(
 delayMillis = TransitionDurations.Normal.milliseconds,
 ),
 )
 val to = fadeOut(TransitionDurations.Normal.asTween())
 from togetherWith to
 },
 ) { song ->
 NowPlayingBottomBarContent(context, song = song)
 }
 Spacer(modifier = Modifier.width(15.dp))
 if (showTrackControls) {
 IconButton(
 onClick = { context.kordx.radio.shorty.previous() }
 ) {
 Icon(Icons.Filled.SkipPrevious, null)
 }
 }
 if (showSeekControls) {
 IconButton(
 onClick = {
 context.kordx.radio.shorty.seekFromCurrent(-seekBackDuration)
 }
 ) {
 Icon(Icons.Filled.FastRewind, null)
 }
 }
 IconButton(
 onClick = { context.kordx.radio.shorty.playPause() }
 ) {
 Icon(
 when {
 !isPlaying -> Icons.Filled.PlayArrow
 else -> Icons.Filled.Pause
 },
 null
 )
 }
 if (showSeekControls) {
 IconButton(
 onClick = {
 context.kordx.radio.shorty.seekFromCurrent(
 seekForwardDuration
 )
 }
 ) {
 Icon(Icons.Filled.FastForward, null)
 }
 }
 if (showTrackControls) {
 IconButton(
 onClick = { context.kordx.radio.shorty.skip() }
 ) {
 Icon(Icons.Filled.SkipNext, null)
 }
 }
 Spacer(modifier = Modifier.width(8.dp))
 }
 }
 if (insetPadding) {
 Spacer(modifier = Modifier.navigationBarsPadding())
 }
 }
 } ?: Box {}
 }
}

@Composable
private fun NowPlayingBottomBarContent(context: ViewContext, song: Song) {
 BoxWithConstraints(modifier = Modifier.clipToBounds()) {
 val cardWidthPx = this@BoxWithConstraints.constraints.maxWidth
 var offsetX by remember { mutableFloatStateOf(0f) }
 val cardOffsetX by animateFloatAsState(
 offsetX / 2,
 label = "c-now-playing-card-offset-x",
 )
 val cardOpacity by animateFloatAsState(
 if (offsetX != 0f) 0.7f else 1f,
 label = "c-now-playing-card-opacity",
 )

 Box(
 modifier = Modifier
 .graphicsLayer(alpha = cardOpacity, translationX = cardOffsetX)
 .pointerInput(Unit) {
 detectHorizontalDragGestures(
 onDragEnd = {
 val thresh = cardWidthPx / 4
 val affected = when {
 -offsetX > thresh -> context.kordx.radio.shorty.skip()
 offsetX > thresh -> context.kordx.radio.shorty.previous()
 else -> false
 }
 if (!affected) {
 offsetX = 0f
 }
 },
 onDragCancel = {
 offsetX = 0f
 },
 onHorizontalDrag = { _, dragAmount ->
 offsetX += dragAmount
 },
 )
 },
 ) {
 Column(modifier = Modifier.fillMaxWidth()) {
 NowPlayingBottomBarContentText(
 context,
 song.title,
 style = MaterialTheme.typography.bodyMedium,
 )
 if (song.artists.isNotEmpty()) {
 NowPlayingBottomBarContentText(
 context,
 song.artists.joinToString(),
 style = MaterialTheme.typography.bodySmall,
 )
 }
 }
 }
 }
}

@Composable
private fun NowPlayingBottomBarContentText(
 context: ViewContext,
 text: String,
 style: TextStyle,
) {
 val textMarquee by context.kordx.settings.miniPlayerTextMarquee.flow.collectAsState()
 var showOverlay by remember { mutableStateOf(false) }

 Box {
 Text(
 text,
 style = style,
 maxLines = 1,
 overflow = when {
 textMarquee -> TextOverflow.Clip
 else -> TextOverflow.Ellipsis
 },
 modifier = Modifier
 .runIfOrThis<Modifier>(textMarquee) {
 basicMarquee(iterations = Int.MAX_VALUE)
 }
 .onGloballyPositioned {
 val offsetX = it.boundsInParent().centerLeft.x
 showOverlay = offsetX.absoluteValue != 0f
 },
 )
 AnimatedVisibility(
 visible = showOverlay,
 modifier = Modifier.matchParentSize(),
 enter = FadeTransition.enterTransition(),
 exit = FadeTransition.exitTransition(),
 ) {
 val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

 Row {
 Box(
 modifier = Modifier
 .width(12.dp)
 .fillMaxHeight()
 .background(
 brush = Brush.horizontalGradient(
 colors = listOf(backgroundColor, Color.Transparent)
 )
 )
 )
 Spacer(modifier = Modifier.weight(1f))
 Box(
 modifier = Modifier
 .width(12.dp)
 .fillMaxHeight()
 .background(
 brush = Brush.horizontalGradient(
 colors = listOf(Color.Transparent, backgroundColor)
 )
 )
 )
 }
 }
 }
}
