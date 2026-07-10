package com.android.rockages.kordx.services.groove.repositories

import android.net.Uri
import androidx.core.net.toUri
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.Assets
import com.android.rockages.kordx.ui.helpers.createHandyImageRequest
import com.android.rockages.kordx.core.utils.FuzzySearchOption
import com.android.rockages.kordx.core.utils.FuzzySearcher
import com.android.rockages.kordx.core.utils.KeyGenerator
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.core.utils.SimpleFileSystem
import com.android.rockages.kordx.core.utils.SimplePath
import com.android.rockages.kordx.core.utils.joinToStringIfNotEmpty
import com.android.rockages.kordx.core.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class SongRepository(private val kordx: KordX) {
 enum class SortBy {
 CUSTOM,
 TITLE,
 ARTIST,
 ALBUM,
 DURATION,
 DATE_MODIFIED,
 COMPOSER,
 ALBUM_ARTIST,
 YEAR,
 FILENAME,
 TRACK_NUMBER,
 }

 private val cache = ConcurrentHashMap<String, Song>()
 internal val pathCache = ConcurrentHashMap<String, String>()
 internal val idGenerator = KeyGenerator.TimeIncremental()
 private val searcher = FuzzySearcher<String>(
 options = listOf(
 FuzzySearchOption({ v -> get(v)?.title?.let { compareString(it) } }, 3),
 FuzzySearchOption({ v -> get(v)?.filename?.let { compareString(it) } }, 2),
 FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } }),
 FuzzySearchOption({ v -> get(v)?.album?.let { compareString(it) } })
 )
 )

 val isUpdating get() = kordx.groove.exposer.isUpdating
 private val _all = MutableStateFlow<List<String>>(emptyList())
 val all = _all.asStateFlow()
 private val _count = MutableStateFlow(0)
 val count = _count.asStateFlow()
 private val _id = MutableStateFlow(System.currentTimeMillis())
 val id = _id.asStateFlow()
 var explorer = SimpleFileSystem.Folder()

 private fun emitCount() = _count.update { cache.size }

 private fun emitIds() = _id.update {
 System.currentTimeMillis()
 }

 internal fun onSong(song: Song) {
 cache[song.id] = song
 pathCache[song.path] = song.id
 explorer.addChildFile(SimplePath(song.path)).data = song.id
 emitIds()
 _all.update {
 it + song.id
 }
 emitCount()
 }

 fun reset() {
 cache.clear()
 pathCache.clear()
 explorer = SimpleFileSystem.Folder()
 emitIds()
 _all.update {
 emptyList()
 }
 emitCount()
 }

 fun search(songIds: List<String>, terms: String, limit: Int = 7) = searcher
 .search(terms, songIds, maxLength = limit)

 fun sort(songIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
 val sensitive = kordx.settings.caseSensitiveSorting.value
 val sorted = when (by) {
 SortBy.CUSTOM -> songIds
 SortBy.TITLE -> songIds.sortedBy { get(it)?.title?.withCase(sensitive) }
 SortBy.ARTIST -> songIds.sortedBy { get(it)?.artists?.joinToStringIfNotEmpty(sensitive) }
 SortBy.ALBUM -> songIds.sortedBy { get(it)?.album?.withCase(sensitive) }
 SortBy.DURATION -> songIds.sortedBy { get(it)?.duration }
 SortBy.DATE_MODIFIED -> songIds.sortedBy { get(it)?.dateModified }
 SortBy.COMPOSER -> songIds.sortedBy {
 get(it)?.composers?.joinToStringIfNotEmpty(sensitive)
 }

 SortBy.ALBUM_ARTIST -> songIds.sortedBy {
 get(it)?.albumArtists?.joinToStringIfNotEmpty(sensitive)
 }

 SortBy.YEAR -> songIds.sortedBy { get(it)?.year }
 SortBy.FILENAME -> songIds.sortedBy { get(it)?.filename?.withCase(sensitive) }
 SortBy.TRACK_NUMBER -> songIds.sortedWith(
 compareBy({ get(it)?.discNumber }, { get(it)?.trackNumber }),
 )
 }
 return if (reverse) sorted.reversed() else sorted
 }

 fun count() = cache.size
 fun ids() = cache.keys.toList()
 fun values() = cache.values.toList()

 /**
 * — test-only setter for the public `count` [StateFlow].
 * Used by the [KordXMediaLibraryService] `DEBUG_ACTION_SCAN`
 * debug receiver to force a fake live count for the
 * scan-in-progress placeholder. Mirrors the
 * [MediaExposer.setIsUpdatingForTest] pattern. The setter is
 * `internal` so it's reachable from the service module but not
 * part of the public API.
 */
 internal fun setCountForTest(value: Int) = _count.update { value }

 /**
 * — test-only setter for the public `all` [StateFlow].
 * Used by the [KordXMediaLibraryService] `DEBUG_ACTION_SHUFFLE_ALL`
 * debug receiver (via the [RadioSession] shuffle handler) to force
 * a fake song-id list so the AVD validation gate can verify the
 * "shuffle all started" log line without going through the SAF
 * picker to populate the library. Mirrors the
 * [setCountForTest] pattern. The setter is `internal` so it's
 * reachable from the service module but not part of the public
 * API.
 */
 internal fun setAllForTest(value: List<String>) = _all.update { value }

 fun get(id: String) = cache[id]
 fun get(ids: List<String>) = ids.mapNotNull { get(it) }

 fun getArtworkUri(songId: String): Uri = get(songId)?.coverFile
 ?.let { kordx.database.artworkCache.get(it) }?.toUri()
 ?: getDefaultArtworkUri()

 fun getDefaultArtworkUri() = Assets.getPlaceholderUri(kordx)

 fun createArtworkImageRequest(songId: String) = createHandyImageRequest(
 kordx.applicationContext,
 image = getArtworkUri(songId),
 fallback = Assets.getPlaceholderId(kordx),
 )

 suspend fun getLyrics(song: Song): String? {
 try {
 val lrcPath = SimplePath(song.path).let {
 it.parent?.join(it.nameWithoutExtension + ".lrc")?.pathString
 }
 kordx.groove.exposer.uris[lrcPath]?.let { uri ->
 kordx.applicationContext.contentResolver.openInputStream(uri)?.use {
 return String(it.readBytes())
 }
 }
 return kordx.database.lyricsCache.get(song.id)
 } catch (err: Exception) {
 Logger.error("LyricsRepository", "fetch lyrics failed", err)
 }
 return null
 }
}
