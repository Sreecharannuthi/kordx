package com.android.rockages.kordx.metaphony

import com.android.rockages.kordx.metaphony.AudioMetadata.Picture
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.String

class AudioMetadataParser internal constructor() {
 // Tags keys can be found at https://taglib.org/api/p_propertymapping.html
 val tags = mutableMapOf<String, MutableList<String>>()
 val pictures = mutableListOf<Picture>()
 val audioProperties = mutableMapOf<String, Int>()

 fun putTag(key: String, value: String) {
 tags.compute(key) { _, it ->
 when (it) {
 null -> mutableListOf(value)
 else -> {
 it.add(value)
 it
 }
 }
 }
 }

 fun putPicture(pictureType: String, mimeType: String, data: ByteArray) {
 pictures.add(Picture(pictureType, mimeType, data))
 }

 fun putAudioProperty(key: String, value: Int) {
 audioProperties.put(key, value)
 }


 // Lazyloads the native library on first use so JVM unit tests can construct; this class without the .so on the classpath.
 fun readMetadata(filename: String, fd: Int): Boolean {
 ensureNativeLoaded()
 return readMetadataNative(filename, fd)
 }

 private external fun readMetadataNative(filename: String, fd: Int): Boolean

 fun toMetadata(): AudioMetadata {
 val (discNumber, discTotal) = parseSlashedNumber(tags["DISCNUMBER"]?.firstOrNull() ?: "")
 val (trackNumber, trackTotal) = parseSlashedNumber(tags["TRACKNUMBER"]?.firstOrNull() ?: "")
 return AudioMetadata(
 title = tags["TITLE"]?.firstOrNull(),
 album = tags["ALBUM"]?.firstOrNull(),
 artists = tags["ARTIST"]?.toSet() ?: emptySet(),
 albumArtists = tags["ALBUMARTIST"]?.toSet() ?: emptySet(),
 composers = tags["COMPOSER"]?.toSet() ?: emptySet(),
 genres = tags["GENRE"]?.toSet() ?: emptySet(),
 discNumber = discNumber,
 discTotal = discTotal,
 trackNumber = trackNumber,
 trackTotal = trackTotal ?: tags["TRACKTOTAL"]?.firstOrNull()?.toIntOrNull(),
 date = parseDateWithFallback(),
 lyrics = tags["LYRICS"]?.firstOrNull(),
 encoding = tags["ENCODING"]?.firstOrNull(),
 bitrate = audioProperties["BITRATE"],
 lengthInSeconds = audioProperties["LENGTH_SECONDS"],
 sampleRate = audioProperties["SAMPLE_RATE"],
 channels = audioProperties["CHANNELS"],
 pictures = pictures,
 )
 }


 // Falls back through DATE → YEAR → ORIGINALDATE → RELEASETIME → ORIGINALYEAR; because ID3v2.3 `TYER` maps to `YEAR`, not `DATE`.
 private fun parseDateWithFallback(): LocalDate? {
 tags["DATE"]?.firstOrNull()?.let { parseDate(it) }?.let { return it }
 tags["YEAR"]?.firstOrNull()?.toIntOrNull()?.let {
 return LocalDate.of(it, 1, 1)
 }
 tags["ORIGINALDATE"]?.firstOrNull()?.let { parseDate(it) }?.let { return it }
 tags["RELEASETIME"]?.firstOrNull()?.let { parseDate(it) }?.let { return it }
 tags["ORIGINALYEAR"]?.firstOrNull()?.toIntOrNull()?.let {
 return LocalDate.of(it, 1, 1)
 }
 return null
 }

 companion object {
 // Lazy-load guard so JVM unit tests can construct this class.
 @Volatile
 private var nativeLibLoaded = false
 private fun ensureNativeLoaded() {
 if (nativeLibLoaded) return
 synchronized(this) {
 if (nativeLibLoaded) return
 System.loadLibrary("metaphony")
 nativeLibLoaded = true
 }
 }

 fun parse(filename: String, fd: Int): AudioMetadata? {
 val parser = AudioMetadataParser()
 val success = parser.readMetadata(filename, fd)
 if (!success) {
 return null
 }
 return parser.toMetadata()
 }

 private fun parseSlashedNumber(text: String): Pair<Int?, Int?> {
 val split = text.split("/")
 if (split.size != 2) {
 return text.toIntOrNull() to null
 }
 return split[0].toIntOrNull() to split[1].toIntOrNull()
 }

 val DATE_YEAR = DateTimeFormatter.ofPattern("yyyy")
 val DATE_YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM")
 val DATE_YEAR_MONTH_DATE = DateTimeFormatter.ISO_LOCAL_DATE

 private fun parseDate(text: String): LocalDate? {
 runCatching {
 return Year.parse(text, DATE_YEAR).let { LocalDate.of(it.value, 1, 1) }
 }
 runCatching {
 return YearMonth.parse(text, DATE_YEAR_MONTH).let { LocalDate.of(it.year, it.monthValue, 1) }
 }
 runCatching {
 return LocalDate.parse(text, DATE_YEAR_MONTH_DATE)
 }
 return null
 }
 }
}
