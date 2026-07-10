package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.AlbumArtist
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.Assets
import com.android.rockages.kordx.ui.helpers.createHandyImageRequest
import com.android.rockages.kordx.core.utils.ConcurrentSet
import com.android.rockages.kordx.core.utils.FuzzySearchOption
import com.android.rockages.kordx.core.utils.FuzzySearcher
import com.android.rockages.kordx.core.utils.concurrentSetOf
import com.android.rockages.kordx.core.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class AlbumArtistRepository(private val kordx: KordX) {
 enum class SortBy {
 CUSTOM,
 ARTIST_NAME,
 TRACKS_COUNT,
 ALBUMS_COUNT,
 }

 private val cache = ConcurrentHashMap<String, AlbumArtist>()
 private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
 private val albumIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
 private val searcher = FuzzySearcher<String>(
 options = listOf(FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }))
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
 song.albumArtists.forEach { albumArtist ->
 songIdsCache.compute(albumArtist) { _, value ->
 value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
 }
 var nNumberOfAlbums = 0
 kordx.groove.album.getIdFromSong(song)?.let { albumId ->
 albumIdsCache.compute(albumArtist) { _, value ->
 nNumberOfAlbums = (value?.size ?: 0) + 1
 value?.apply { add(albumId) } ?: concurrentSetOf(albumId)
 }
 }
 cache.compute(albumArtist) { _, value ->
 value?.apply {
 numberOfAlbums = nNumberOfAlbums
 numberOfTracks++
 } ?: run {
 _all.update {
 it + albumArtist
 }
 emitCount()
 AlbumArtist(
 name = albumArtist,
 numberOfAlbums = 1,
 numberOfTracks = 1,
 )
 }
 }
 }
 }

 fun reset() {
 cache.clear()
 _all.update {
 emptyList()
 }
 emitCount()
 }

 fun getArtworkUri(albumArtistName: String) = songIdsCache[albumArtistName]?.firstOrNull()
 ?.let { kordx.groove.song.getArtworkUri(it) }
 ?: kordx.groove.song.getDefaultArtworkUri()

 fun createArtworkImageRequest(albumArtistName: String) = createHandyImageRequest(
 kordx.applicationContext,
 image = getArtworkUri(albumArtistName),
 fallback = Assets.placeholderDarkId,
 )

 fun search(albumArtistNames: List<String>, terms: String, limit: Int = 7) = searcher
 .search(terms, albumArtistNames, maxLength = limit)

 fun sort(albumArtistNames: List<String>, by: SortBy, reverse: Boolean): List<String> {
 val sensitive = kordx.settings.caseSensitiveSorting.value
 val sorted = when (by) {
 SortBy.CUSTOM -> albumArtistNames
 SortBy.ARTIST_NAME -> albumArtistNames.sortedBy { get(it)?.name?.withCase(sensitive) }
 SortBy.TRACKS_COUNT -> albumArtistNames.sortedBy { get(it)?.numberOfTracks }
 SortBy.ALBUMS_COUNT -> albumArtistNames.sortedBy { get(it)?.numberOfTracks }
 }
 return if (reverse) sorted.reversed() else sorted
 }

 fun count() = cache.size
 fun ids() = cache.keys.toList()
 fun values() = cache.values.toList()

 fun get(albumArtistName: String) = cache[albumArtistName]
 fun get(albumArtistNames: List<String>) = albumArtistNames.mapNotNull { get(it) }
 fun getAlbumIds(albumArtistName: String) =
 albumIdsCache[albumArtistName]?.toList() ?: emptyList()

 fun getSongIds(albumArtistName: String) = songIdsCache[albumArtistName]?.toList() ?: emptyList()
}
