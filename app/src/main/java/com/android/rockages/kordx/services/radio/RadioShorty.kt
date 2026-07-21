package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.KordX
import kotlin.random.Random

class RadioShorty(private val kordx: KordX) : RadioShortyAdapterTarget {
 override fun playPause() {
 if (!kordx.radio.hasPlayer) {
 // No active player — but if songs are queued, start playback.
 // This handles the case where playQueue() set up the queue but
 // the async prepare() hasn't completed yet, or playback was stopped.
 if (kordx.radio.queue.currentSongIndex >= 0 && !kordx.radio.queue.isEmpty()) {
 kordx.radio.play(Radio.PlayOptions(index = kordx.radio.queue.currentSongIndex))
 }
 return
 }
 when {
 kordx.radio.isPlaying -> kordx.radio.pause()
 else -> kordx.radio.resume()
 }
 }

 override fun seekFromCurrent(offsetSecs: Int) {
 if (!kordx.radio.hasPlayer) {
 return
 }
 kordx.radio.currentPlaybackPosition?.run {
 val to = (played + (offsetSecs * 1000)).coerceIn(0..total)
 kordx.radio.seek(to)
 }
 }

 override fun previous(): Boolean {
 return when {
 !kordx.radio.hasPlayer -> false
 kordx.radio.currentPlaybackPosition!!.played <= 3000 && kordx.radio.canJumpToPrevious() -> {
 kordx.radio.jumpToPrevious()
 true
 }

 else -> {
 kordx.radio.seek(0)
 false
 }
 }
 }

 override fun skip(): Boolean {
 return when {
 // No active player but queue has songs — start playback from next (or first).
 !kordx.radio.hasPlayer -> {
 if (!kordx.radio.queue.isEmpty()) {
 val nextIndex = (kordx.radio.queue.currentSongIndex + 1)
 .coerceAtMost(kordx.radio.queue.currentQueue.size - 1)
 .coerceAtLeast(0)
 kordx.radio.play(Radio.PlayOptions(index = nextIndex))
 true
 } else {
 false
 }
 }
 kordx.radio.canJumpToNext() -> {
 kordx.radio.jumpToNext()
 true
 }

 else -> {
 kordx.radio.play(Radio.PlayOptions(index = 0, autostart = false))
 false
 }
 }
 }

 fun playQueue(
 songIds: List<String>,
 options: Radio.PlayOptions = Radio.PlayOptions(),
 shuffle: Boolean = false,
 ) {
 kordx.radio.stop(ended = false)
 if (songIds.isEmpty()) {
 return
 }
 kordx.radio.queue.add(
 songIds,
 options = options.run {
 copy(index = if (shuffle) Random.nextInt(songIds.size) else options.index)
 }
 )
 kordx.radio.queue.setShuffleMode(shuffle)
 }

 fun playQueue(
 songId: String,
 options: Radio.PlayOptions = Radio.PlayOptions(),
 shuffle: Boolean = false,
 ) = playQueue(listOf(songId), options = options, shuffle = shuffle)
}
