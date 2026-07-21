package com.android.rockages.kordx.services.groove

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Album
import com.android.rockages.kordx.core.groove.AlbumArtist
import com.android.rockages.kordx.core.groove.Artist
import com.android.rockages.kordx.core.groove.Genre
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.utils.SimplePath
import com.android.rockages.kordx.ui.helpers.Assets


// Extension functions that bridge the pure data models in :core with the; KordX service layer. Kept here so :core stays free of appshell dependencies.

fun Album.createArtworkImageRequest(kordx: KordX) =
 kordx.groove.album.createArtworkImageRequest(id)

fun Album.getSongIds(kordx: KordX) = kordx.groove.album.getSongIds(id)

fun Album.getSortedSongIds(kordx: KordX) = kordx.groove.song.sort(
 getSongIds(kordx),
 kordx.settings.lastUsedAlbumSongsSortBy.value,
 kordx.settings.lastUsedAlbumSongsSortReverse.value,
)

fun Artist.createArtworkImageRequest(kordx: KordX) =
 kordx.groove.artist.createArtworkImageRequest(name)

fun Artist.getSongIds(kordx: KordX) = kordx.groove.artist.getSongIds(name)

fun Artist.getSortedSongIds(kordx: KordX) = kordx.groove.song.sort(
 getSongIds(kordx),
 kordx.settings.lastUsedSongsSortBy.value,
 kordx.settings.lastUsedSongsSortReverse.value,
)

fun Artist.getAlbumIds(kordx: KordX) = kordx.groove.artist.getAlbumIds(name)

fun AlbumArtist.createArtworkImageRequest(kordx: KordX) =
 kordx.groove.albumArtist.createArtworkImageRequest(name)

fun AlbumArtist.getSongIds(kordx: KordX) = kordx.groove.albumArtist.getSongIds(name)

fun AlbumArtist.getSortedSongIds(kordx: KordX) = kordx.groove.song.sort(
 getSongIds(kordx),
 kordx.settings.lastUsedSongsSortBy.value,
 kordx.settings.lastUsedSongsSortReverse.value,
)

fun AlbumArtist.getAlbumIds(kordx: KordX) = kordx.groove.albumArtist.getAlbumIds(name)

fun Genre.getSongIds(kordx: KordX) = kordx.groove.genre.getSongIds(name)

fun Genre.getSortedSongIds(kordx: KordX) = kordx.groove.song.sort(
 getSongIds(kordx),
 kordx.settings.lastUsedSongsSortBy.value,
 kordx.settings.lastUsedSongsSortReverse.value,
)

fun Song.createArtworkImageRequest(kordx: KordX) =
 kordx.groove.song.createArtworkImageRequest(id)

private val MUXER_TAG_PATTERNS = listOf(
    Regex("^Lavf", RegexOption.IGNORE_CASE),
    Regex("^LAME", RegexOption.IGNORE_CASE),
    Regex("^iTunes", RegexOption.IGNORE_CASE),
    Regex("^Nero", RegexOption.IGNORE_CASE),
    Regex("^Xiph", RegexOption.IGNORE_CASE),
    Regex("^FLAC", RegexOption.IGNORE_CASE),
    Regex("^Lavc", RegexOption.IGNORE_CASE),
    Regex("^Helix", RegexOption.IGNORE_CASE),
)

private fun isMuxerTag(value: String): Boolean =
    MUXER_TAG_PATTERNS.any { it.containsMatchIn(value) }

fun Song.toSamplingInfoString(kordx: KordX): String? {

    // + 30k — there30e version used; val values = mutableListOf<String>(); encoder?.let { values.add(it) }; channels?.let { values.add(kordx.t.XChannels(it.toString())) }; bitrateK?.let { values.add(buildString { append(kordx.t.XKbps(it.toString())) }) }; samplingRateK?.let { values.add(kordx.t.XKHz(it.toString())) }; return values.takeIf { it.isNotEmpty() }?.joinToString(", ")
 //

 //

 // The fix uses `listOfNotNull(...)` (declarative, no mutable; accumulator) AND wraps each `?.let { ... }` in `.takeIf {; it.isNotBlank() }` so any empty / blank formatted string is; also filtered out. The result is a stable `Lavf57.82.101`only; string for the FLAC case (instead of `Lavf57.82.101, , ,`).
 val parts = listOfNotNull(
 encoder?.takeIf { it.isNotBlank() && !isMuxerTag(it) },
 channels?.let { kordx.t.XChannels(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 bitrateK?.let { kordx.t.XKbps(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 samplingRateK?.let { kordx.t.XKHz(it.toString()) }
 ?.takeIf { it.isNotBlank() },
 )
 return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

fun Playlist.createArtworkImageRequest(kordx: KordX) =
 getSongIds(kordx).firstOrNull()
 ?.let { kordx.groove.song.get(it)?.createArtworkImageRequest(kordx) }
 ?: Assets.createPlaceholderImageRequest(kordx)

fun Playlist.getSongIds(kordx: KordX): List<String> {
 val parentPath = path?.let { SimplePath(it) }?.parent
 val primaryPath = SimplePath(Playlist.PRIMARY_STORAGE)
 return songPaths.mapNotNull { x ->
 kordx.groove.song.pathCache[x]
 ?: x.takeIf { x[0] == '/' }?.let {
 kordx.groove.song.pathCache[it.substring(1).replaceFirst("/", ":")]
 }
 ?: parentPath?.let { kordx.groove.song.pathCache[it.join(x).pathString] }
 ?: kordx.groove.song.pathCache[primaryPath.join(x).pathString]
 }
}

fun Playlist.getSortedSongIds(kordx: KordX) = kordx.groove.song.sort(
 getSongIds(kordx),
 kordx.settings.lastUsedPlaylistSongsSortBy.value,
 kordx.settings.lastUsedPlaylistSongsSortReverse.value,
)
