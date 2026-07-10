package com.android.rockages.kordx.services.groove
import com.android.rockages.kordx.core.groove.Song

import android.net.Uri
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.ActivityUtils
import com.android.rockages.kordx.core.utils.ConcurrentSet
import com.android.rockages.kordx.core.utils.DocumentFileX
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.core.utils.SimpleFileSystem
import com.android.rockages.kordx.core.utils.SimplePath
import com.android.rockages.kordx.core.utils.concurrentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MediaExposer(private val kordx: KordX) {
 internal val uris = ConcurrentHashMap<String, Uri>()
 var explorer = SimpleFileSystem.Folder()
 private val _isUpdating = MutableStateFlow(false)
 val isUpdating = _isUpdating.asStateFlow()

 private fun emitUpdate(value: Boolean) = _isUpdating.update {
 value
 }

 /**
 * — test-only setter for [isUpdating]. Used by the
 * [KordXMediaLibraryService] `DEBUG_ACTION_SCAN` debug receiver
 * to flip the "scan in progress" flag without running an
 * actual [fetch]. Mirrors the `DEBUG_ACTION_*` debug-broadcast
 * pattern from 19 / 20 (RECEIVER_EXPORTED,
 * namespaced `com.android.rockages.kordx.radio.DEBUG_*`,
 * only registered while the radio session is alive). The
 * setter is `internal` so it's reachable from the service
 * module but not part of the public API.
 */
 internal fun setIsUpdatingForTest(value: Boolean) = emitUpdate(value)

 private data class ScanCycle(
 val songCache: ConcurrentHashMap<String, Song>,
 val songCacheUnused: ConcurrentSet<String>,
 val artworkCacheUnused: ConcurrentSet<String>,
 val lyricsCacheUnused: ConcurrentSet<String>,
 val filter: MediaFilter,
 val songParseOptions: SongParser.ParseOptions,
 ) {
 companion object {
 suspend fun create(kordx: KordX): ScanCycle {
 val songCache = ConcurrentHashMap(kordx.database.songCache.entriesPathMapped())
 val songCacheUnused = concurrentSetOf(songCache.map { it.value.id })
 val artworkCacheUnused = concurrentSetOf(kordx.database.artworkCache.all())
 val lyricsCacheUnused = concurrentSetOf(kordx.database.lyricsCache.keys())
 val filter = MediaFilter(
 kordx.settings.songsFilterPattern.value,
 kordx.settings.blacklistFolders.value.toSortedSet(),
 kordx.settings.whitelistFolders.value.toSortedSet()
 )
 return ScanCycle(
 songCache = songCache,
 songCacheUnused = songCacheUnused,
 artworkCacheUnused = artworkCacheUnused,
 lyricsCacheUnused = lyricsCacheUnused,
 filter = filter,
 songParseOptions = SongParser.ParseOptions.create(kordx),
 )
 }
 }
 }

 @OptIn(ExperimentalCoroutinesApi::class)
 suspend fun fetch() {
 emitUpdate(true)
 try {
 val context = kordx.applicationContext
 val folderUris = kordx.settings.mediaFolders.value
 val cycle = ScanCycle.create(kordx)
 folderUris.map { x ->
 ActivityUtils.makePersistableReadableUri(context, x)
 DocumentFileX.fromTreeUri(context, x)?.let {
 val path = SimplePath(DocumentFileX.getParentPathOfTreeUri(x) ?: it.name)
 with(Dispatchers.IO) {
 scanMediaTree(cycle, path, it)
 }
 }
 }
 trimCache(cycle)
 } catch (err: Exception) {
 Logger.error("MediaExposer", "fetch failed", err)
 }
 emitUpdate(false)
 emitFinish()
 }

 private suspend fun scanMediaTree(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
 try {
 if (!cycle.filter.isWhitelisted(path.pathString)) {
 return
 }
 coroutineScope {
 file.list().map {
 val childPath = path.join(it.name)
 async {
 when {
 it.isDirectory -> scanMediaTree(cycle, childPath, it)
 else -> scanMediaFile(cycle, childPath, it)
 }
 }
 }.awaitAll()
 }
 } catch (err: Exception) {
 Logger.error("MediaExposer", "scan media tree failed", err)
 }
 }

 private suspend fun scanMediaFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
 try {
 when {
 path.extension == "lrc" -> scanLrcFile(cycle, path, file)
 file.mimeType == MIMETYPE_M3U -> scanM3UFile(cycle, path, file)
 file.mimeType.startsWith("audio/") -> scanAudioFile(cycle, path, file)
 }
 } catch (err: Exception) {
 Logger.error("MediaExposer", "scan media file failed", err)
 }
 }

 private suspend fun scanAudioFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
 val pathString = path.pathString
 uris[pathString] = file.uri
 val lastModified = file.lastModified
 val cached = cycle.songCache[pathString]
 val cacheHit = cached != null
 && cached.dateModified == lastModified
 && (cached.coverFile?.let { cycle.artworkCacheUnused.contains(it) } != false)
 val song = when {
 cacheHit -> cached
 else -> SongParser.parse(path, file, cycle.songParseOptions)
 }
 if (song.duration.milliseconds < kordx.settings.minSongDuration.value.seconds) {
 return
 }
 if (!cacheHit) {
 kordx.database.songCache.insert(song)
 cached?.coverFile?.let {
 if (kordx.database.artworkCache.get(it).delete()) {
 cycle.artworkCacheUnused.remove(it)
 }
 }
 }
 cycle.songCacheUnused.remove(song.id)
 song.coverFile?.let {
 cycle.artworkCacheUnused.remove(it)
 }
 cycle.lyricsCacheUnused.remove(song.id)
 explorer.addChildFile(path)

 // Repositories use ConcurrentHashMap + MutableStateFlow (both threadsafe),; so songs can be emitted on the IO dispatcher without a mainthread hop.; This avoids thousands of mainthread context switches during a scan and; keeps the UI responsive while the library is being indexed.
 emitSong(song)
 }

 private fun scanLrcFile(
 @Suppress("Unused") cycle: ScanCycle,
 path: SimplePath,
 file: DocumentFileX,
 ) {
 uris[path.pathString] = file.uri
 explorer.addChildFile(path)
 }

 private fun scanM3UFile(
 @Suppress("Unused") cycle: ScanCycle,
 path: SimplePath,
 file: DocumentFileX,
 ) {
 uris[path.pathString] = file.uri
 explorer.addChildFile(path)
 }

 private suspend fun trimCache(cycle: ScanCycle) {
 try {
 kordx.database.songCache.delete(cycle.songCacheUnused)
 } catch (err: Exception) {
 Logger.warn("MediaExposer", "trim song cache failed", err)
 }
 for (x in cycle.artworkCacheUnused) {
 try {
 kordx.database.artworkCache.get(x).delete()
 } catch (err: Exception) {
 Logger.warn("MediaExposer", "delete artwork cache file failed", err)
 }
 }
 try {
 kordx.database.lyricsCache.delete(cycle.lyricsCacheUnused)
 } catch (err: Exception) {
 Logger.warn("MediaExposer", "trim lyrics cache failed", err)
 }
 }

 suspend fun reset() {
 emitUpdate(true)
 uris.clear()
 explorer = SimpleFileSystem.Folder()
 kordx.database.songCache.clear()
 emitUpdate(false)
 }

 private fun emitSong(song: Song) {
 kordx.groove.albumArtist.onSong(song)
 kordx.groove.album.onSong(song)
 kordx.groove.artist.onSong(song)
 kordx.groove.genre.onSong(song)
 kordx.groove.song.onSong(song)
 }

 private fun emitFinish() {
 kordx.groove.playlist.onScanFinish()
 }

 private class MediaFilter(
 pattern: String?,
 private val blacklisted: Set<String>,
 private val whitelisted: Set<String>,
 ) {
 private val regex = pattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

 fun isWhitelisted(path: String): Boolean {
 regex?.let {
 if (!it.containsMatchIn(path)) {
 return false
 }
 }
 val bFilter = blacklisted.findLast {
 path.startsWith(it)
 }
 if (bFilter == null) {
 return true
 }
 val wFilter = whitelisted.findLast {
 it.startsWith(bFilter) && path.startsWith(it)
 }
 return wFilter != null
 }
 }

 companion object {
 const val MIMETYPE_M3U = "audio/x-mpegurl"
 }
}
