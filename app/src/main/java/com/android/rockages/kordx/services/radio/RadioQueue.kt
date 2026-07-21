package com.android.rockages.kordx.services.radio

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.concurrentListOf

class RadioQueue(private val kordx: KordX) : RadioQueueAdapterTarget {
 enum class LoopMode {
 None,
 Queue,
 Song;

 companion object {
 val values = enumValues<LoopMode>()
 }
 }

 val originalQueue = concurrentListOf<String>()

 // `currentQueue` is overridden from [RadioQueueAdapterTarget]; with a covariant return type (MutableList<String> is a; List<String>). The internal code continues to use the; MutableList<String> API (`add`, `clear`, etc.); the; adapterfacing surface sees it as List<String>.
 override val currentQueue: MutableList<String> = concurrentListOf()

 override var currentSongIndex = -1
 internal set(value) {
 field = value
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.IndexChanged)
 }

 override var currentShuffleMode = false
 private set(value) {
 field = value
 kordx.radio.onUpdate.dispatch(Radio.Events.QueueOption.ShuffleModeChanged)
 }

 override var currentLoopMode = LoopMode.None
 private set(value) {
 field = value
 kordx.radio.onUpdate.dispatch(Radio.Events.QueueOption.LoopModeChanged)
 }

 override val currentSongId: String?
 get() = getSongIdAt(currentSongIndex)

 fun hasSongAt(index: Int) = index > -1 && index < currentQueue.size
 fun getSongIdAt(index: Int) = if (hasSongAt(index)) currentQueue[index] else null

 fun reset() {
 originalQueue.clear()
 currentQueue.clear()
 currentSongIndex = -1
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Cleared)
 }

 /**
 * — Issue #843 (clearing queue stops playing current song).
 * Public alias for [reset]. Kept as a separate name so the public
 * "clear queue without stopping playback" intent is explicit at
 * the call site; semantically identical to [reset] (the player is
 * not touched — only the queue is cleared). Use this when the user
 * wants to empty the queue but keep the current song playing.
 * Compare to [Radio.stop] which stops both the player AND clears
 * the queue.
 */
 fun clear() = reset()

 fun add(
 songIds: List<String>,
 index: Int? = null,
 options: Radio.PlayOptions = Radio.PlayOptions(),
 ) {
 index?.let {
 originalQueue.addAll(it, songIds)
 currentQueue.addAll(it, songIds)
 if (it <= currentSongIndex) {
 currentSongIndex += songIds.size
 }
 } ?: run {
 originalQueue.addAll(songIds)
 currentQueue.addAll(songIds)
 }
 afterAdd(options)
 }

 fun add(
 songId: String,
 index: Int? = null,
 options: Radio.PlayOptions = Radio.PlayOptions(),
 ) = add(listOf(songId), index, options)

 private fun afterAdd(options: Radio.PlayOptions) {
 if (!kordx.radio.hasPlayer) {
 kordx.radio.play(options)
 }
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 }

 fun remove(index: Int) {
 val songId = currentQueue.getOrNull(index) ?: return
 currentQueue.removeAt(index)
 originalQueue.remove(songId)
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 if (currentSongIndex == index) {
 kordx.radio.play(Radio.PlayOptions(index = currentSongIndex))
 } else if (index < currentSongIndex) {
 currentSongIndex--
 }
 }

 fun remove(indices: List<Int>) {
 if (indices.isEmpty()) {
 return
 }
 val originalCurrentSongIndex = currentSongIndex
 var currentSongRemoved = false
 val sortedIndices = indices.sortedDescending()
 for (i in sortedIndices) {
 val songId = currentQueue.getOrNull(i) ?: continue
 currentQueue.removeAt(i)
 originalQueue.remove(songId)
 if (i == originalCurrentSongIndex) {
 currentSongRemoved = true
 }
 }
 val removedBeforeCurrent = sortedIndices.count { it < originalCurrentSongIndex }
 currentSongIndex = originalCurrentSongIndex - removedBeforeCurrent
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 if (currentSongRemoved) {
 kordx.radio.play(Radio.PlayOptions(index = currentSongIndex))
 }
 }

 /**
 * Removes songs by [songIds] (id-based removal). Looks up all
 * matching indices and delegates to [remove] by index.
 */
 fun removeByIds(songIds: List<String>) {
 val indices = songIds.flatMap { id ->
 currentQueue.indices.filter { currentQueue[it] == id }
 }.sortedDescending().distinct()
 if (indices.isNotEmpty()) {
 remove(indices)
 }
 }

 /**
 * Removes the song at [index] from both queues and adjusts
 * [currentSongIndex], but does NOT trigger playback. Used by
 * [Radio.play] when it discovers a stale song id at the requested
 * index, so the stale id is dropped before the error-recovery
 * auto-advance path is invoked.
 */
 internal fun removeAtSilently(index: Int) {
 val songId = currentQueue.getOrNull(index) ?: return
 currentQueue.removeAt(index)
 originalQueue.remove(songId)
 if (index < currentSongIndex) {
 currentSongIndex--
 }
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 }

 override fun setLoopMode(loopMode: LoopMode) {
 currentLoopMode = loopMode

 // Persist loop mode so the user's choice survives an app restart.; write) and is fired from the foreground UI / Media3; `setRepeatMode` adapter path, not on a hot loop.
 if (kordx.settings.lastLoopMode.value != loopMode) {
 kordx.settings.lastLoopMode.setValue(loopMode)
 }
 }

 fun toggleLoopMode() {
 val next = (currentLoopMode.ordinal + 1) % LoopMode.values.size
 setLoopMode(LoopMode.values[next])
 }

 fun toggleShuffleMode() = setShuffleMode(!currentShuffleMode)

 override fun setShuffleMode(enabled: Boolean) {
 currentShuffleMode = enabled

 // Issue #773 / #707: persist the new shuffle mode.; Same rationale as `setLoopMode` above.
 if (kordx.settings.lastShuffleMode.value != enabled) {
 kordx.settings.lastShuffleMode.setValue(enabled)
 }
 if (currentQueue.isNotEmpty()) {
 val currentSongId = getSongIdAt(currentSongIndex) ?: getSongIdAt(0)!!
 currentSongIndex = if (currentShuffleMode) {
 val newQueue = originalQueue.toMutableList()
 newQueue.removeAt(currentSongIndex)
 newQueue.shuffle()
 newQueue.add(0, currentSongId)
 currentQueue.clear()
 currentQueue.addAll(newQueue)
 0
 } else {
 currentQueue.clear()
 currentQueue.addAll(originalQueue)
 originalQueue.indexOfFirst { it == currentSongId }
 }
 }
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 }

 fun isEmpty() = originalQueue.isEmpty()

 data class Serialized(
 val currentSongIndex: Int,
 val playedDuration: Long,
 val originalQueue: List<String>,
 val currentQueue: List<String>,
 val shuffled: Boolean,
 ) {
 fun serialize() =
 listOf(
 currentSongIndex.toString(),
 playedDuration.toString(),
 originalQueue.joinToString(","),
 currentQueue.joinToString(","),
 shuffled.toString(),
 ).joinToString(";")

 companion object {
 fun create(queue: RadioQueue, playbackPosition: RadioPlayer.PlaybackPosition) =
 Serialized(
 currentSongIndex = queue.currentSongIndex,
 playedDuration = playbackPosition.played,
 originalQueue = queue.originalQueue.toList(),
 currentQueue = queue.currentQueue.toList(),
 shuffled = queue.currentShuffleMode,
 )

 fun parse(data: String): Serialized? {
 try {
 val semi = data.split(";")
 return Serialized(
 currentSongIndex = semi[0].toInt(),
 playedDuration = semi[1].toLong(),
 originalQueue = semi[2].split(","),
 currentQueue = semi[3].split(","),
 shuffled = semi[4].toBoolean(),
 )
 } catch (_: Exception) {
 }
 return null
 }
 }
 }

 fun restore(serialized: Serialized) {
 if (serialized.originalQueue.isNotEmpty()) {
 kordx.radio.stop(ended = false)
 originalQueue.clear()
 originalQueue.addAll(serialized.originalQueue)
 currentQueue.clear()
 currentQueue.addAll(serialized.currentQueue)
 kordx.radio.onUpdate.dispatch(Radio.Events.Queue.Modified)
 currentShuffleMode = serialized.shuffled
 afterAdd(
 Radio.PlayOptions(
 index = serialized.currentSongIndex,
 autostart = false,
 startPosition = serialized.playedDuration,
 )
 )
 }
 }
}
