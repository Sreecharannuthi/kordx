package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Genre
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.utils.ConcurrentSet
import com.android.rockages.kordx.core.utils.FuzzySearchOption
import com.android.rockages.kordx.core.utils.FuzzySearcher
import com.android.rockages.kordx.core.utils.concurrentSetOf
import com.android.rockages.kordx.core.utils.withCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class GenreRepository(private val kordx: KordX) {
 enum class SortBy {
 CUSTOM,
 GENRE,
 TRACKS_COUNT,
 }

 private val cache = ConcurrentHashMap<String, Genre>()
 private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
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
 song.genres.forEach { genre ->
 songIdsCache.compute(genre) { _, value ->
 value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
 }
 cache.compute(genre) { _, value ->
 value?.apply {
 numberOfTracks++
 } ?: run {
 _all.update {
 it + genre
 }
 emitCount()
 Genre(
 name = genre,
 numberOfTracks = 1,
 )
 }
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

 fun search(genreNames: List<String>, terms: String, limit: Int = 7) = searcher
 .search(terms, genreNames, maxLength = limit)

 fun sort(genreNames: List<String>, by: SortBy, reverse: Boolean): List<String> {
 val sensitive = kordx.settings.caseSensitiveSorting.value
 val sorted = when (by) {
 SortBy.CUSTOM -> genreNames
 SortBy.GENRE -> genreNames.sortedBy { get(it)?.name?.withCase(sensitive) }
 SortBy.TRACKS_COUNT -> genreNames.sortedBy { get(it)?.numberOfTracks }
 }
 return if (reverse) sorted.reversed() else sorted
 }

 fun count() = cache.size
 fun ids() = cache.keys.toList()
 fun values() = cache.values.toList()

 fun get(id: String) = cache[id]
 fun getSongIds(genre: String) = songIdsCache[genre]?.toList() ?: emptyList()
}
