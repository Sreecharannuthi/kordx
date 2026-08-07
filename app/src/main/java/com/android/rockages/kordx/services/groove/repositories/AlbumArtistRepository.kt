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

    // Primary caches keyed by normalized key (Song.normalizeArtistKey).
    private val cache = ConcurrentHashMap<String, AlbumArtist>()
    private val songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    private val albumIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()

    // Maps normalized key -> canonical display name (the first variant seen).
    private val canonicalName = ConcurrentHashMap<String, String>()

    // Reverse lookup: raw display name -> normalized key.
    private val nameToKey = ConcurrentHashMap<String, String>()

    /**
     * Cached user-defined album-artist merge map. Loaded lazily from settings
     * and kept in sync via [loadMergeMap] / [persistMergeMap].
     * Key = source raw album-artist name, Value = target raw album-artist name.
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
     * Resolves a raw album-artist name to its normalized key.
     * If the name has never been seen, returns null.
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
     *
     * User-defined merge mappings (see [merge]) are applied before
     * normalization so that songs by a source album artist aggregate under
     * the target album artist's profile.
     */
    private fun resolveOrCreateKey(rawName: String): String {
        val effectiveName = resolveMergeTarget(rawName)
        val norm = Song.normalizeArtistKey(effectiveName)
        nameToKey.putIfAbsent(rawName, norm)
        canonicalName.putIfAbsent(norm, effectiveName)
        return norm
    }

    /**
     * Loads the persisted album-artist merge map into [mergeMap].
     * Called on repository init and after any merge/unmerge change.
     */
    private fun loadMergeMap() {
        mergeMap.clear()
        kordx.settings.albumArtistMergeMap.value.forEach { entry ->
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
        kordx.settings.albumArtistMergeMap.setValue(entries)
    }

    internal fun onSong(song: Song) {
        if (mergeMap.isEmpty() && kordx.settings.albumArtistMergeMap.value.isNotEmpty()) {
            loadMergeMap()
        }
        song.albumArtists.forEach { albumArtist ->
            val key = resolveOrCreateKey(albumArtist)
            songIdsCache.compute(key) { _, value ->
                value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
            }
            var nNumberOfAlbums = 0
            kordx.groove.album.getIdFromSong(song)?.let { albumId ->
                albumIdsCache.compute(key) { _, value ->
                    nNumberOfAlbums = (value?.size ?: 0) + 1
                    value?.apply { add(albumId) } ?: concurrentSetOf(albumId)
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
                    AlbumArtist(
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
     * Merges [sourceAlbumArtistName] into [targetAlbumArtistName]. All songs and
     * albums previously attributed to the source will appear under the target
     * album artist. The mapping is persisted and survives rescans.
     *
     * To keep the in-memory caches consistent, this immediately migrates
     * the source's cached song/album IDs and counts to the target.
     */
    fun merge(sourceAlbumArtistName: String, targetAlbumArtistName: String) {
        require(sourceAlbumArtistName != targetAlbumArtistName) {
            "Cannot merge an album artist with itself"
        }
        loadMergeMap()
        mergeMap[sourceAlbumArtistName] = targetAlbumArtistName
        persistMergeMap()

        // Immediate in-cache migration: move source IDs to target.
        val sourceKey = resolveKey(sourceAlbumArtistName)
        val targetKey = resolveKey(targetAlbumArtistName)
        if (sourceKey != null && targetKey != null && sourceKey != targetKey) {
            migrateCaches(sourceKey, targetKey)
            removeSourceFromAllList(sourceAlbumArtistName, sourceKey, targetKey)
            emitCount()
        }
    }

    /**
     * Moves the song/album caches and aggregate counts from
     * [sourceKey] to [targetKey]. The source cache entries are removed.
     */
    private fun migrateCaches(sourceKey: String, targetKey: String) {
        val sourceSongs = songIdsCache.remove(sourceKey)
        val sourceAlbums = albumIdsCache.remove(sourceKey)
        val sourceAlbumArtist = cache.remove(sourceKey)

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
                numberOfTracks += sourceAlbumArtist?.numberOfTracks ?: 0
            } ?: sourceAlbumArtist ?: AlbumArtist(
                name = canonicalName.getValue(targetKey),
                numberOfAlbums = albumIdsCache[targetKey]?.size ?: 0,
                numberOfTracks = sourceAlbumArtist?.numberOfTracks ?: 0,
            )
        }
    }

    /**
     * Removes the source display name from the public album-artist list and
     * ensures the target display name is present.
     */
    private fun removeSourceFromAllList(
        sourceAlbumArtistName: String,
        sourceKey: String,
        targetKey: String,
    ) {
        _all.update { current ->
            current.filterNot { it == sourceAlbumArtistName || resolveKey(it) == sourceKey } +
                canonicalName.getValue(targetKey)
        }
    }

    /**
     * Removes any merge mapping where [albumArtistName] is the source.
     * The source album artist will reappear as its own profile on the next
     * library scan; in-memory caches are not rebuilt here.
     */
    fun unmerge(albumArtistName: String) {
        loadMergeMap()
        if (mergeMap.remove(albumArtistName) != null) {
            persistMergeMap()
        }
    }

    /**
     * Returns the target raw album-artist name if [albumArtistName] is
     * currently merged into another album artist, otherwise null.
     */
    fun getMergeTarget(albumArtistName: String): String? {
        loadMergeMap()
        return mergeMap[albumArtistName]
    }

    /**
     * Returns all source raw album-artist names that are currently merged into
     * [targetAlbumArtistName].
     */
    fun getMergedSources(targetAlbumArtistName: String): List<String> {
        loadMergeMap()
        return mergeMap.filterValues { it == targetAlbumArtistName }.keys.toList()
    }

    fun getArtworkUri(albumArtistName: String) = resolveKey(albumArtistName)
        ?.let { key -> songIdsCache[key]?.firstOrNull() }
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
            SortBy.ALBUMS_COUNT -> albumArtistNames.sortedBy { get(it)?.numberOfAlbums }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = canonicalName.values.toList()
    fun values() = cache.values.toList()

    fun get(albumArtistName: String): AlbumArtist? {
        val key = resolveKey(albumArtistName)
        return key?.let { cache[it] }
    }
    fun get(albumArtistNames: List<String>) = albumArtistNames.mapNotNull { get(it) }
    fun getAlbumIds(albumArtistName: String) = resolveKey(albumArtistName)
        ?.let { albumIdsCache[it]?.toList() } ?: emptyList()
    fun getSongIds(albumArtistName: String) = resolveKey(albumArtistName)
        ?.let { songIdsCache[it]?.toList() } ?: emptyList()
}
