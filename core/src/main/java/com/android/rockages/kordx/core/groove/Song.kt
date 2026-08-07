package com.android.rockages.kordx.core.groove

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.android.rockages.kordx.core.utils.SimplePath
import java.math.RoundingMode
import java.time.LocalDate
import java.util.regex.Pattern

@Entity("songs")
data class Song(
 @PrimaryKey
 val id: String,
 val title: String,
 val album: String?,
 val artists: Set<String>,
 val composers: Set<String>,
 val albumArtists: Set<String>,
 val genres: Set<String>,
 val trackNumber: Int?,
 val trackTotal: Int?,
 val discNumber: Int?,
 val discTotal: Int?,
 val date: LocalDate?,
 val year: Int?,
 val duration: Long,
 val bitrate: Long?,
 val samplingRate: Long?,
 val channels: Int?,
 val encoder: String?,
 val dateModified: Long,
 val size: Long,
 val coverFile: String?,
 val uri: Uri,
 val path: String,
) {
 val bitrateK: Long? get() = bitrate?.let { it / 1000 }
 val samplingRateK: Float?
 get() = samplingRate?.let {
 (it.toFloat() / 1000)
 .toBigDecimal()
 .setScale(1, RoundingMode.CEILING)
 .toFloat()
 }

 val filename get() = SimplePath(path).name

 companion object {
 fun makeSeparatorsRegex(separators: Set<String>): Regex {
 val partial = separators.joinToString("|") { Pattern.quote(it) }
 return Regex("""(?<!\\)($partial)""")
 }

 fun parseMultiValue(value: String?, regex: Regex) = value?.let {
 parseMultiValue(setOf(it), regex)
 } ?: emptySet()

 fun parseMultiValue(values: Set<String>, regex: Regex): Set<String> {
 val result = mutableSetOf<String>()
 for (x in values) {
 for (y in x.trim().split(regex)) {
 val trimmed = y.trim()
 if (trimmed.isEmpty()) {
 continue
 }
 result.add(trimmed)
 }
 }
 return result
 }

 /**
 * Produces a canonical key for artist identity matching.
 * Lowercases, trims, and collapses all whitespace runs to a single space.
 * "A.R.  Rahman" -> "a.r. rahman", "A.R. Rahman" -> "a.r. rahman".
 * Used by [ArtistRepository] and [AlbumArtistRepository] so that
 * spelling variants of the same artist merge into one profile.
 */
 fun normalizeArtistKey(raw: String): String {
 return raw.trim().lowercase().replace(Regex("\\s+"), " ")
 }
 }
}
