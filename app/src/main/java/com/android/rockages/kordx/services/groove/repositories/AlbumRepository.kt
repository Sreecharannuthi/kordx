package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Album
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.Assets
import com.android.rockages.kordx.ui.helpers.createHandyImageRequest
import com.android.rockages.kordx.core.utils.ConcurrentSet
import com.android.rockages.kordx.core.utils.FuzzySearchOption
import com.android.rockages.kordx.core.utils.FuzzySearcher
import com.android.rockages.kordx.core.utils.concurrentSetOf
import com.android.rockages.kordx.core.utils.joinToStringIfNotEmpty
import com.android.rockages.kordx.core.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class AlbumRepository(private val kordx: KordX) {
 enum class SortBy {
 CUSTOM,
 ALBUM_NAME,
 ARTIST_NAME,
 TRACKS_COUNT,
 YEAR,
 }

 private val cache = ConcurrentHashMap<String, Album>()
 private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
 private val searcher = FuzzySearcher<String>(
 options = listOf(
 FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }, 3),
 FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } })
 )
 )

 val isUpdating get() = kordx.groove.exposer.isUpdating
 private val _all = MutableStateFlow<List<String>>(emptyList())
 val all = _all.asStateFlow()
 private val _count = MutableStateFlow(0)
 val count = _count.asStateFlow()

 private fun emitCount() = _count.update {
 cache.size
 }

 internal fun onSong(song: Song) {
 val albumId = getIdFromSong(song) ?: return
 songIdsCache.compute(albumId) { _, value ->
 value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
 }
 cache.compute(albumId) { _, value ->
 value?.apply {
 artists.addAll(song.artists)
 song.year?.let {
 startYear = startYear?.let { old -> min(old, it) } ?: it
 endYear = endYear?.let { old -> max(old, it) } ?: it
 }
 numberOfTracks++
 duration += song.duration.milliseconds
 } ?: run {
 _all.update {
 it + albumId
 }
 emitCount()
 Album(
 id = albumId,
 name = song.album!!,
 artists = mutableSetOf<String>().apply {
 // ensure that album artists are first
 addAll(song.albumArtists)
 addAll(song.artists)
 },
 startYear = song.year,
 endYear = song.year,
 numberOfTracks = 1,
 duration = song.duration.milliseconds,
 )
 }
 }
 }

 fun reset() {
 cache.clear()
 songIdsCache.clear()
 _all.update {
 emptyList()
 }
 emitCount()
 }

 fun getIdFromSong(song: Song): String? {
 if (song.album == null) {
 return null
 }
 val artists = song.albumArtists.sorted().joinToString("-")
 return "${song.album}-${artists}"
 }

 fun getArtworkUri(albumId: String) = songIdsCache[albumId]?.firstOrNull()
 ?.let { kordx.groove.song.getArtworkUri(it) }
 ?: kordx.groove.song.getDefaultArtworkUri()

 fun createArtworkImageRequest(albumId: String) = createHandyImageRequest(
 kordx.applicationContext,
 image = getArtworkUri(albumId),
 fallback = Assets.placeholderDarkId,
 )

 fun search(albumIds: List<String>, terms: String, limit: Int = 7) = searcher
 .search(terms, albumIds, maxLength = limit)

 fun sort(albumIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
 val sensitive = kordx.settings.caseSensitiveSorting.value
 val sorted = when (by) {
 SortBy.CUSTOM -> albumIds
 SortBy.ALBUM_NAME -> albumIds.sortedBy { get(it)?.name?.withCase(sensitive) }
 SortBy.ARTIST_NAME -> albumIds.sortedBy {
 get(it)?.artists?.joinToStringIfNotEmpty(sensitive)
 }

 SortBy.TRACKS_COUNT -> albumIds.sortedBy { get(it)?.numberOfTracks }
 SortBy.YEAR -> albumIds.sortedBy { get(it)?.startYear }
 }
 return if (reverse) sorted.reversed() else sorted
 }

 fun count() = cache.size
 fun ids() = cache.keys.toList()
 fun values() = cache.values.toList()

 fun get(albumId: String) = cache[albumId]
 fun get(albumIds: List<String>) = albumIds.mapNotNull { get(it) }.toList()
 fun getSongIds(albumId: String) = songIdsCache[albumId]?.toList() ?: emptyList()
}
