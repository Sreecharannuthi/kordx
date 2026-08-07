package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Artist
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

class ArtistRepository(private val kordx: KordX) {
 enum class SortBy {
 CUSTOM,
 ARTIST_NAME,
 TRACKS_COUNT,
 ALBUMS_COUNT,
 }

 // Primary caches keyed by normalized key (Song.normalizeArtistKey).
 private val cache = ConcurrentHashMap<String, Artist>()
 private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
 private val albumIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()

 // Maps normalized key -> canonical display name (the first variant seen).
 // Ensures consistent UI: once "A.R. Rahman" is seen first, all later
 // variants ("a.r. rahman", "A.R.  Rahman") display as "A.R. Rahman".
 private val canonicalName = ConcurrentHashMap<String, String>()

 // Reverse lookup: raw display name -> normalized key, so
 // get("A.R. Rahman") and get("a.r. rahman") both resolve.
 private val nameToKey = ConcurrentHashMap<String, String>()

 /**
 * Cached user-defined artist merge map. Loaded lazily from settings
 * and kept in sync via [loadMergeMap] / [persistMergeMap].
 * Key = source raw artist name, Value = target raw artist name.
 */
 private val mergeMap = ConcurrentHashMap<String, String>()

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

 /**
 * Resolves a raw artist name to its normalized key.
 * If the name has never been seen, returns null (caller should use
 * [resolveOrCreateKey] during song registration instead).
 */
 private fun resolveKey(rawName: String): String? {
 return nameToKey[rawName] ?: Song.normalizeArtistKey(rawName).let { norm ->
 if (cache.containsKey(norm)) {
 nameToKey[rawName] = norm
 norm
 } else {
 null
 }
 }
 }

 /**
 * Looks up the user-defined merge target for [rawName].
 * Returns the target raw name if one exists, otherwise [rawName].
 */
 private fun resolveMergeTarget(rawName: String): String {
 return mergeMap[rawName] ?: rawName
 }

 /**
 * Resolves or creates a normalized key for [rawName].
 * On first encounter, registers the canonical display name.
 * On subsequent encounters, [rawName] is recorded in [nameToKey]
 * so future lookups by that exact string are O(1).
 *
 * User-defined merge mappings (see [merge]) are applied before
 * normalization so that songs by a source artist aggregate under
 * the target artist's profile.
 */
 private fun resolveOrCreateKey(rawName: String): String {
 val effectiveName = resolveMergeTarget(rawName)
 val norm = Song.normalizeArtistKey(effectiveName)
 nameToKey.putIfAbsent(rawName, norm)
 canonicalName.putIfAbsent(norm, effectiveName)
 return norm
 }

 /**
 * Loads the persisted artist merge map into [mergeMap].
 * Called on repository init and after any merge/unmerge change.
 */
 private fun loadMergeMap() {
 mergeMap.clear()
 kordx.settings.artistMergeMap.value.forEach { entry ->
 val parts = entry.split("|", limit = 2)
 if (parts.size == 2) {
 mergeMap[parts[0]] = parts[1]
 }
 }
 }

 /**
 * Persists the current in-memory [mergeMap] back to settings.
 */
 private fun persistMergeMap() {
 val entries = mergeMap.map { (source, target) -> "$source|$target" }.toSet()
 kordx.settings.artistMergeMap.setValue(entries)
 }

 internal fun onSong(song: Song) {
 if (mergeMap.isEmpty() && kordx.settings.artistMergeMap.value.isNotEmpty()) {
 loadMergeMap()
 }
 song.artists.forEach { artist ->
 val key = resolveOrCreateKey(artist)
 songIdsCache.compute(key) { _, value ->
 value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
 }
 var nNumberOfAlbums = 0
 kordx.groove.album.getIdFromSong(song)?.let { album ->
 albumIdsCache.compute(key) { _, value ->
 nNumberOfAlbums = (value?.size ?: 0) + 1
 value?.apply { add(album) } ?: concurrentSetOf(album)
 }
 }
 cache.compute(key) { _, value ->
 value?.apply {
 numberOfAlbums = nNumberOfAlbums
 numberOfTracks++
 } ?: run {
 _all.update {
 it + canonicalName.getValue(key)
 }
 emitCount()
 Artist(
 name = canonicalName.getValue(key),
 numberOfAlbums = 1,
 numberOfTracks = 1,
 )
 }
 }
 }
 }

 fun reset() {
 cache.clear()
 canonicalName.clear()
 nameToKey.clear()
 mergeMap.clear()
 _all.update {
 emptyList()
 }
 emitCount()
 }

 /**
 * Merges [sourceArtistName] into [targetArtistName]. All songs and
 * albums previously attributed to the source will appear under the
 * target artist. The mapping is persisted and survives rescans.
 *
 * To keep the in-memory caches consistent, this immediately migrates
 * the source's cached song/album IDs and counts to the target.
 */
 fun merge(sourceArtistName: String, targetArtistName: String) {
 require(sourceArtistName != targetArtistName) {
 "Cannot merge an artist with itself"
 }
 loadMergeMap()
 mergeMap[sourceArtistName] = targetArtistName
 persistMergeMap()

 // Immediate in-cache migration: move source IDs to target.
 val sourceKey = resolveKey(sourceArtistName)
 val targetKey = resolveKey(targetArtistName)
 if (sourceKey != null && targetKey != null && sourceKey != targetKey) {
 migrateCaches(sourceKey, targetKey)
 removeSourceFromAllList(sourceArtistName, sourceKey, targetKey)
 emitCount()
 }
 }

 /**
 * Moves the song/album caches and artist aggregate counts from
 * [sourceKey] to [targetKey]. The source cache entries are removed.
 */
 private fun migrateCaches(sourceKey: String, targetKey: String) {
 val sourceSongs = songIdsCache.remove(sourceKey)
 val sourceAlbums = albumIdsCache.remove(sourceKey)
 val sourceArtist = cache.remove(sourceKey)

 if (sourceSongs != null) {
 songIdsCache.compute(targetKey) { _, value ->
 value?.apply { addAll(sourceSongs) } ?: concurrentSetOf(sourceSongs)
 }
 }
 if (sourceAlbums != null) {
 albumIdsCache.compute(targetKey) { _, value ->
 value?.apply { addAll(sourceAlbums) } ?: concurrentSetOf(sourceAlbums)
 }
 }
 cache.compute(targetKey) { _, value ->
 value?.apply {
 numberOfAlbums = albumIdsCache[targetKey]?.size ?: numberOfAlbums
 numberOfTracks += sourceArtist?.numberOfTracks ?: 0
 } ?: sourceArtist ?: Artist(
 name = canonicalName.getValue(targetKey),
 numberOfAlbums = albumIdsCache[targetKey]?.size ?: 0,
 numberOfTracks = sourceArtist?.numberOfTracks ?: 0,
 )
 }
 }

 /**
 * Removes the source display name from the public artist list and
 * ensures the target display name is present.
 */
 private fun removeSourceFromAllList(
 sourceArtistName: String,
 sourceKey: String,
 targetKey: String,
 ) {
 _all.update { current ->
 current.filterNot { it == sourceArtistName || resolveKey(it) == sourceKey } +
 canonicalName.getValue(targetKey)
 }
 }

 /**
 * Removes any merge mapping where [artistName] is the source.
 * The source artist will reappear as its own profile on the next
 * library scan; in-memory caches are not rebuilt here.
 */
 fun unmerge(artistName: String) {
 loadMergeMap()
 if (mergeMap.remove(artistName) != null) {
 persistMergeMap()
 }
 }

 /**
 * Returns the target raw artist name if [artistName] is currently
 * merged into another artist, otherwise null.
 */
 fun getMergeTarget(artistName: String): String? {
 loadMergeMap()
 return mergeMap[artistName]
 }

 /**
 * Returns all source raw artist names that are currently merged into
 * [targetArtistName].
 */
 fun getMergedSources(targetArtistName: String): List<String> {
 loadMergeMap()
 return mergeMap.filterValues { it == targetArtistName }.keys.toList()
 }

 fun getArtworkUri(artistName: String) = resolveKey(artistName)
 ?.let { key -> songIdsCache[key]?.firstOrNull() }
 ?.let { kordx.groove.song.getArtworkUri(it) }
 ?: kordx.groove.song.getDefaultArtworkUri()

 fun createArtworkImageRequest(artistName: String) = createHandyImageRequest(
 kordx.applicationContext,
 image = getArtworkUri(artistName),
 fallback = Assets.placeholderDarkId,
 )

 fun search(artistNames: List<String>, terms: String, limit: Int = 7) = searcher
 .search(terms, artistNames, maxLength = limit)

 fun sort(artistNames: List<String>, by: SortBy, reverse: Boolean): List<String> {
 val sensitive = kordx.settings.caseSensitiveSorting.value
 val sorted = when (by) {
 SortBy.CUSTOM -> artistNames
 SortBy.ARTIST_NAME -> artistNames.sortedBy { get(it)?.name?.withCase(sensitive) }
 SortBy.TRACKS_COUNT -> artistNames.sortedBy { get(it)?.numberOfTracks }
 SortBy.ALBUMS_COUNT -> artistNames.sortedBy { get(it)?.numberOfAlbums }
 }
 return if (reverse) sorted.reversed() else sorted
 }

 fun count() = cache.size
 fun ids() = canonicalName.values.toList()
 fun values() = cache.values.toList()

 fun get(id: String): Artist? {
 val key = resolveKey(id)
 return key?.let { cache[it] }
 }
 fun get(ids: List<String>) = ids.mapNotNull { get(it) }
 fun getAlbumIds(artistName: String) = resolveKey(artistName)
 ?.let { albumIdsCache[it]?.toList() } ?: emptyList()
 fun getSongIds(artistName: String) = resolveKey(artistName)
 ?.let { songIdsCache[it]?.toList() } ?: emptyList()
}
