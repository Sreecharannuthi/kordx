package com.android.rockages.kordx.ui.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.services.radio.RadioQueue
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.nowPlaying.NothingPlaying
import com.android.rockages.kordx.ui.view.nowPlaying.NowPlayingBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Immutable
data class NowPlayingData(
 val song: Song,
 val isPlaying: Boolean,
 val currentSongIndex: Int,
 val queueSize: Int,
 val currentLoopMode: RadioQueue.LoopMode,
 val currentShuffleMode: Boolean,
 val currentSpeed: Float,
 val currentPitch: Float,
 val persistedSpeed: Float,
 val persistedPitch: Float,
 val hasSleepTimer: Boolean,
 val pauseOnCurrentSongEnd: Boolean,
 val showSongAdditionalInfo: Boolean,
 val enableSeekControls: Boolean,
 val seekBackDuration: Int,
 val seekForwardDuration: Int,
 val controlsLayout: NowPlayingControlsLayout,
 val lyricsLayout: NowPlayingLyricsLayout,
)

data class NowPlayingStates(
 val showLyrics: MutableStateFlow<Boolean>,
)

enum class NowPlayingControlsLayout {
 CompactLeft,
 CompactRight,
 Traditional,
}

enum class NowPlayingLyricsLayout {
 ReplaceArtwork,
 SeparatePage,
}

@Serializable
object NowPlayingViewRoute

@Composable
fun NowPlayingView(context: ViewContext) {
 NowPlayingObserver(context) { data ->
 when {
 data != null -> NowPlayingBody(context, data = data)
 else -> NothingPlaying(context)
 }
 }
}

@Composable
fun NowPlayingObserver(
 context: ViewContext,
 content: @Composable (NowPlayingData?) -> Unit,
) {
 val queue by context.kordx.radio.observatory.queue.collectAsState()
 val queueIndex by context.kordx.radio.observatory.queueIndex.collectAsState()
 val song by remember(queue, queueIndex) {
 derivedStateOf {
 queue.getOrNull(queueIndex)?.let { context.kordx.groove.song.get(it) }
 }
 }
 val isViable by remember(song) {
 derivedStateOf { song != null }
 }

 val isPlaying by context.kordx.radio.observatory.isPlaying.collectAsState()
 val currentLoopMode by context.kordx.radio.observatory.loopMode.collectAsState()
 val currentShuffleMode by context.kordx.radio.observatory.shuffleMode.collectAsState()
 val currentSpeed by context.kordx.radio.observatory.speed.collectAsState()
 val currentPitch by context.kordx.radio.observatory.pitch.collectAsState()
 val persistedSpeed by context.kordx.radio.observatory.persistedSpeed.collectAsState()
 val persistedPitch by context.kordx.radio.observatory.persistedPitch.collectAsState()
 val sleepTimer by context.kordx.radio.observatory.sleepTimer.collectAsState()
 val pauseOnCurrentSongEnd by context.kordx.radio.observatory.pauseOnCurrentSongEnd.collectAsState()
 val showSongAdditionalInfo by context.kordx.settings.nowPlayingAdditionalInfo.flow.collectAsState()
 val enableSeekControls by context.kordx.settings.nowPlayingSeekControls.flow.collectAsState()
 val seekBackDuration by context.kordx.settings.seekBackDuration.flow.collectAsState()
 val seekForwardDuration by context.kordx.settings.seekForwardDuration.flow.collectAsState()
 val controlsLayout by context.kordx.settings.nowPlayingControlsLayout.flow.collectAsState()
 val lyricsLayout by context.kordx.settings.nowPlayingLyricsLayout.flow.collectAsState()

 val data = when {
 isViable -> NowPlayingData(
 song = song!!,
 isPlaying = isPlaying,
 currentSongIndex = queueIndex,
 queueSize = queue.size,
 currentLoopMode = currentLoopMode,
 currentShuffleMode = currentShuffleMode,
 currentSpeed = currentSpeed,
 currentPitch = currentPitch,
 persistedSpeed = persistedSpeed,
 persistedPitch = persistedPitch,
 hasSleepTimer = sleepTimer != null,
 pauseOnCurrentSongEnd = pauseOnCurrentSongEnd,
 showSongAdditionalInfo = showSongAdditionalInfo,
 enableSeekControls = enableSeekControls,
 seekBackDuration = seekBackDuration,
 seekForwardDuration = seekForwardDuration,
 controlsLayout = controlsLayout,
 lyricsLayout = lyricsLayout,
 )

 else -> null
 }
 content(data)
}
