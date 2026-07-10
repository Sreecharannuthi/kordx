package com.android.rockages.kordx.services.groove

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.utils.DocumentFileX
import com.android.rockages.kordx.core.utils.ImagePreserver
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.core.utils.SimplePath
import com.android.rockages.kordx.metaphony.AudioMetadataParser
import java.io.FileOutputStream
import java.time.LocalDate

/** Service-layer parser that turns a SAF document file into a [Song] entity, keeping the :core data class app-shell-free. */
object SongParser {

 data class ParseOptions(
 val kordx: KordX,
 val artistSeparatorRegex: Regex,
 val genreSeparatorRegex: Regex,
 ) {
 companion object {
 fun create(kordx: KordX) = ParseOptions(
 kordx = kordx,
 artistSeparatorRegex = Song.makeSeparatorsRegex(kordx.settings.artistTagSeparators.value),
 genreSeparatorRegex = Song.makeSeparatorsRegex(kordx.settings.genreTagSeparators.value),
 )
 }
 }

 fun parse(
 path: SimplePath,
 file: DocumentFileX,
 options: ParseOptions,
 ): Song {
 if (options.kordx.settings.useMetaphony.value) {
 try {
 val song = parseUsingMetaphony(path, file, options)
 if (song != null) {
 return song
 }
 } catch (err: Exception) {
 Logger.error("Song", "could not parse using metaphony", err)
 }
 }
 return parseUsingMediaMetadataRetriever(path, file, options)
 }

 private fun parseUsingMetaphony(
 path: SimplePath,
 file: DocumentFileX,
 options: ParseOptions,
 ): Song? {
 val kordx = options.kordx
 val metadata = kordx.applicationContext.contentResolver
 .openFileDescriptor(file.uri, "r")
 ?.use { AudioMetadataParser.parse(file.name, it.detachFd()) }
 ?: return null
 val id = kordx.groove.song.idGenerator.next()
 val coverFile = metadata.pictures.firstOrNull()?.let {
 val extension = when (it.mimeType) {
 "image/jpg", "image/jpeg" -> "jpg"
 "image/png" -> "png"

 // Issue #853 (WebP art not displayed). WebP is; increasingly common in modern MP4 / FLAC containers and; taglib reports the correct mime type. `BitmapFactory`; decodes WebP unconditionally on `minSdk = 31` (Android 12,; which is KordX's min per the cutover; the; pre31 path used a separate `WebpFactory` API which is; also a free sideeffect of the minsdk bump).
 "image/webp" -> "webp"
 else -> null
 }
 if (extension == null) {
 return@let null
 }
 val quality = kordx.settings.artworkQuality.value
 if (quality.maxSide == null) {
 val name = "$id.$extension"
 kordx.database.artworkCache.get(name).writeBytes(it.data)
 return@let name
 }
 val bitmap = BitmapFactory.decodeByteArray(it.data, 0, it.data.size)
 val name = "$id.jpg"
 FileOutputStream(kordx.database.artworkCache.get(name)).use { writer ->
 ImagePreserver
 .resize(bitmap, quality)
 .compress(Bitmap.CompressFormat.JPEG, 100, writer)
 }
 name
 }
 metadata.lyrics?.let {
 kordx.database.lyricsCache.put(id, it)
 }
 return Song(
 id = id,
 title = metadata.title ?: path.nameWithoutExtension,
 album = metadata.album,
 artists = Song.parseMultiValue(metadata.artists, options.artistSeparatorRegex),
 composers = Song.parseMultiValue(metadata.composers, options.artistSeparatorRegex),
 albumArtists = Song.parseMultiValue(metadata.albumArtists, options.artistSeparatorRegex),
 genres = Song.parseMultiValue(metadata.genres, options.genreSeparatorRegex),
 trackNumber = metadata.trackNumber,
 trackTotal = metadata.trackTotal,
 discNumber = metadata.discNumber,
 discTotal = metadata.discTotal,
 date = metadata.date,
 year = metadata.date?.year,
 duration = metadata.lengthInSeconds?.let { it * 1000L } ?: 0,
 bitrate = metadata.bitrate?.let { it * 1000L },
 samplingRate = metadata.sampleRate?.toLong(),
 channels = metadata.channels,
 encoder = metadata.encoding,
 dateModified = file.lastModified,
 size = file.size,
 coverFile = coverFile,
 uri = file.uri,
 path = path.pathString,
 )
 }

 fun parseUsingMediaMetadataRetriever(
 path: SimplePath,
 file: DocumentFileX,
 options: ParseOptions,
 ): Song {
 val kordx = options.kordx
 val retriever = MediaMetadataRetriever()
 try {
 retriever.setDataSource(kordx.applicationContext, file.uri)
 val id = kordx.groove.song.idGenerator.next() + ".mr"
 val coverFile = retriever.embeddedPicture?.let {
 val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
 val quality = kordx.settings.artworkQuality.value
 val name = "$id.jpg"
 FileOutputStream(kordx.database.artworkCache.get(name)).use { writer ->
 ImagePreserver
 .resize(bitmap, quality)
 .compress(Bitmap.CompressFormat.JPEG, 100, writer)
 }
 name
 }
 val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
 val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
 val artists = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
 val composers = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
 val albumArtists =
 retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
 val genres = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
 val trackNumber = retriever
 .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
 ?.toIntOrNull()
 val trackTotal = retriever
 .extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
 ?.toIntOrNull()
 val discNumber = retriever
 .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
 ?.toIntOrNull()
 val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
 val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
 ?.toIntOrNull()
 val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
 ?.toLongOrNull()
 val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
 ?.toLongOrNull()
 var samplingRate: Long? = null
 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
 samplingRate = retriever
 .extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
 ?.toLongOrNull()
 }
 return Song(
 id = id,
 title = title ?: path.nameWithoutExtension,
 album = album,
 artists = Song.parseMultiValue(artists, options.artistSeparatorRegex),
 composers = Song.parseMultiValue(composers, options.artistSeparatorRegex),
 albumArtists = Song.parseMultiValue(albumArtists, options.artistSeparatorRegex),
 genres = Song.parseMultiValue(genres, options.genreSeparatorRegex),
 trackNumber = trackNumber,
 trackTotal = trackTotal,
 discNumber = discNumber,
 discTotal = null,
 date = runCatching { LocalDate.parse(date) }.getOrNull(),
 year = year,
 duration = duration ?: 0,
 bitrate = bitrate,
 samplingRate = samplingRate,
 channels = null,
 encoder = null,
 dateModified = file.lastModified,
 size = file.size,
 coverFile = coverFile,
 uri = file.uri,
 path = path.pathString,
 )
 } finally {

 // Issue #804 (OOM scan): the `MediaMetadataRetriever`; holds a native handle; without an explicit `release()` call it; leaks persong and can OOM on large libraries. The retriever; is also backed by a `ContentProvider` connection that should; be released promptly. `release()` is idempotent and safe to; call from `finally`. The catch is defensive: an exception; during release shouldn't override the real result.
 try {
 retriever.release()
 } catch (err: Exception) {
 Logger.warn("SongParser", "MediaMetadataRetriever release failed", err)
 }
 }
 }
}
