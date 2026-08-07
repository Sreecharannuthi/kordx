package com.android.rockages.kordx.services.radio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.android.rockages.kordx.core.groove.Album
import com.android.rockages.kordx.core.groove.AlbumArtist
import com.android.rockages.kordx.core.groove.Genre
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.utils.DurationUtils

/** + 26i — Builders for the AndroidX Media3 browse tree of the new [KordXMediaLibraryService]. The 26i cutover inlines the per-entity display-contract data (the `descriptionForSong` / `songSubtitle` / `songExtras` / `albumSubtitle` / etc. helpers) from the deleted legacy `MediaItemFactory` so the new service has a single self-contained Media3 `MediaItem` factory. The `Extras` data class + the constants + the placeholder helper live in the new shared [KordXMediaSessionConstants] file (also a 26i extraction).  Why one factory (not two) Before 26i the codebase had two parallel factories: `MediaItemFactory` (producing the legacy `MediaBrowserCompat.MediaItem` for the -22 `KordXMediaBrowserService`) and `Media3ItemFactory` (producing the Media3 `MediaItem` for the new service). They shared the same data layer (the per-entity display contract is framework-agnostic). After 26i the legacy service is gone, so the legacy factory is gone too — theer-entity data is inlined here, and the new factory produces Media3 `MediaItem` instances directly.  Media3 MediaItem shape A Media3 `MediaItem` carries its display fields (`title` / `subtitle` / `description` / `iconUri` / `extras` / `isBrowsable` / `isPlayable` / `mediaType`) on the inner `MediaMetadata`, not on the `MediaItem` itself. The factory below always sets both: - `MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata)` — thetem-level mediaId is what the framework routes on. - `MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)… .setIsBrowsable(b).setIsPlayable(p).setMediaType(t).setExtras(b)` — theetadata-level fields are what the framework renders. The `setUri(...)` builder method is intentionally **not** called — the songs are routed through `KordXMediaSessionConstants.mediaIdToSongId` + the live `KordX.groove.song.get(id)` lookup, not through an `ExoPlayer.setMediaItem(uri)` path.  Unstable API Marked `@UnstableApi` because `MediaItem` and `MediaMetadata` are part of Media3's unstable surface (subject to API breakage between minor versions). The plan (26a-26m) commits to the 1.7.1 API; Media3 upgrade (1.10.x requires `compileSdk = 36`) will be handled */
@UnstableApi
internal object Media3ItemFactory {


 // Content style enum mirrored from MediaConstants to keep builders; JVMtestable (no android.media.utils import in the data class).

 enum class ContentStyle {
 LIST_ITEM,
 GRID_ITEM,
 }


 // Perentity extras: a pure data class that the service converts to a; real android.os.Bundle via [toBundle] at the call site.

 data class Extras(
 val stringEntries: Map<String, String> = emptyMap(),
 val longEntries: Map<String, Long> = emptyMap(),
 val intEntries: Map<String, Int> = emptyMap(),
 ) {
 fun toBundle(): Bundle = Bundle().apply {
 stringEntries.forEach { (k, v) -> putString(k, v) }
 longEntries.forEach { (k, v) -> putLong(k, v) }
 intEntries.forEach { (k, v) -> putInt(k, v) }
 }

 /**
 * — additive helper for the recently-played tab. Returns
 * a copy of the receiver with [key] -> [value] appended to the
 * `longEntries` map. Used to layer the `PLAYED_AT` timestamp
 * extra on top of the standard [songExtras] (so the
 * recently-played row carries the per-entity display contract
 * *plus* the play-time hint for the "X minutes ago" style label
 * AAOS can render).
 *
 * 26i — was a top-level extension function
 * `internal fun Extras.withLong(...)` in the legacy
 * `MediaItemFactory.kt`; the 26i inlining promoted it to a
 * member function so the JVM tests can call it as a regular
 * method (`base.withLong(...)` works inside the
 * `Media3ItemFactory` companion).
 */
 fun withLong(key: String, value: Long): Extras =
 copy(longEntries = longEntries + (key to value))

 companion object {
 val EMPTY = Extras()
 }
 }

 // ---- Tab-level content-style + searchability hints.

 fun songsTabExtras() = Extras()
 fun albumsTabExtras() = Extras(
 intEntries = mapOf(
 MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to
 MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
 ),
 )
 fun artistsTabExtras() = Extras()
 fun genresTabExtras() = Extras()
 fun playlistsTabExtras() = Extras()
 /**
 * — the "Recently played" root tab carries the
 * searchability hint at the description-extras level. The
 * Media3 `MediaItem` does not expose `setSearchable(true)`
 * directly (that lives on the framework
 * `android.media.browse.MediaItem` since API 27), so the AAOS
 * browse contract is approximated by the int-extra convention
 * (`"android.media.browse.SEARCH_SUPPORTED" → 1`). On the
 * framework `android.media.browse.MediaItem` the same intent
 * is expressed via `setSearchable(true)`; the int-extra
 * convention is the documented fallback.
 */
 fun recentTabExtras() = Extras(
 intEntries = mapOf("android.media.browse.SEARCH_SUPPORTED" to 1),
 )
 fun recentEmptyExtras() = Extras(
 intEntries = mapOf("android.media.browse.SEARCH_SUPPORTED" to 1),
 )

 /**
 * Pure helper that produces the per-entity [Extras] for a placeholder /
 * empty-state / error MediaItem. Three reasons are surfaced
 * (per the plan+ the legacy
 * `KordXMediaBrowserService.placeholderItem` contract):
 *
 * - [KordXMediaSessionConstants.EMPTY_REASON_NO_SONGS]
 * (`"no_songs"`): the library has zero songs.
 * - [KordXMediaSessionConstants.EMPTY_REASON_SCANNING]
 * (`"scanning"`): a scan is in progress. The `count`
 * argument is layered onto the extras as an `Int` under
 * [KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT].
 * - [KordXMediaSessionConstants.EMPTY_REASON_ERROR]
 * (`"error"`): a playback error happened. The `count`
 * argument is ignored for this reason.
 *
 * The function is a pure builder (no Android / KordX / Room dep), so
 * the JVM unit tests in [Media3ItemFactoryTest] exercise the bundle
 * shape end-to-end.
 */
 fun placeholderExtras(reason: String, count: Int? = null): Extras {
 val intEntries = mutableMapOf<String, Int>()
 if (reason == KordXMediaSessionConstants.EMPTY_REASON_SCANNING && count != null) {
 intEntries[KordXMediaSessionConstants.EXTRA_KEY_SCAN_COUNT] = count
 }
 return Extras(
 stringEntries = mapOf(KordXMediaSessionConstants.EXTRA_KEY_EMPTY_REASON to reason),
 intEntries = intEntries,
 )
 }

 // ---- Per-entity display contract: descriptions (pure strings).

 fun descriptionForSong(song: Song): String =
 DurationUtils.formatMs(song.duration)

 fun descriptionForAlbum(album: Album): String {
 val year = album.startYear ?: album.endYear
 val yearPart = year?.toString() ?: ""
 val trackPart = "${album.numberOfTracks} tracks"
 val durationPart = DurationUtils.formatMs(album.duration.inWholeMilliseconds)
 return listOf(yearPart, trackPart, durationPart)
 .filter { it.isNotEmpty() }
 .joinToString(separator = " • ")
 }

 fun descriptionForAlbumArtist(albumArtist: AlbumArtist): String =
 "${albumArtist.numberOfAlbums} albums • ${albumArtist.numberOfTracks} songs"

 fun descriptionForGenre(genre: Genre): String = ""

 fun descriptionForPlaylist(playlist: Playlist, createdAt: Long? = null): String {
 if (createdAt == null) return ""
 val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
 .format(java.util.Date(createdAt))
 return "Created $date"
 }

 // ---- Per-entity display contract: extras (Bundle-ready data classes).

 fun songExtras(song: Song, albumId: String?): Extras {
 val artist = song.artists.joinToStringIfNotEmpty() ?: ""
 val longEntries = mutableMapOf<String, Long>(
 "DURATION_MS" to song.duration,
 )
 val intEntries = mutableMapOf<String, Int>()
 song.trackNumber?.let { intEntries["TRACK_NUMBER"] = it }
 song.year?.let { intEntries["YEAR"] = it }
 val stringEntries = mutableMapOf<String, String>(
 "ARTIST" to artist,
 )
 if (albumId != null) {
 stringEntries["ALBUM_ID"] = albumId
 }
 return Extras(
 stringEntries = stringEntries,
 longEntries = longEntries,
 intEntries = intEntries,
 )
 }

 fun albumExtras(album: Album): Extras {
 val albumArtist = album.artists.firstOrNull() ?: ""
 val intEntries = mutableMapOf<String, Int>(
 "TRACK_COUNT" to album.numberOfTracks,
 )
 (album.startYear ?: album.endYear)?.let { intEntries["YEAR"] = it }
 return Extras(
 stringEntries = mapOf(
 "ALBUM_ARTIST" to albumArtist,
 "CONTENT_STYLE_GRID_ITEM" to "GRID_ITEM",
 ),
 intEntries = intEntries,
 )
 }

 fun albumArtistExtras(albumArtist: AlbumArtist): Extras = Extras(
 intEntries = mapOf(
 "ALBUM_COUNT" to albumArtist.numberOfAlbums,
 "SONG_COUNT" to albumArtist.numberOfTracks,
 ),
 )

 fun genreExtras(genre: Genre): Extras = Extras(
 intEntries = mapOf("SONG_COUNT" to genre.numberOfTracks),
 )

 fun playlistExtras(playlist: Playlist, createdAt: Long? = null): Extras {
 val intEntries = mutableMapOf<String, Int>(
 "SONG_COUNT" to playlist.numberOfTracks,
 )
 val longEntries = mutableMapOf<String, Long>()
 createdAt?.let { longEntries["CREATED_AT"] = it }
 return Extras(
 longEntries = longEntries,
 intEntries = intEntries,
 )
 }

 // ---- Subtitle builders: handle missing fields gracefully (no " • " dangling).

 fun songSubtitle(song: Song): String {
 val artist = song.artists.joinToStringIfNotEmpty()
 val album = song.album?.takeIf { it.isNotBlank() }
 return when {
 artist != null && album != null -> "$artist • $album"
 artist != null -> artist
 album != null -> album
 else -> ""
 }
 }

 fun albumSubtitle(album: Album): String =
 album.artists.firstOrNull() ?: ""

 fun albumArtistSubtitle(albumArtist: AlbumArtist): String =
 "${albumArtist.numberOfAlbums} albums"

 fun genreSubtitle(genre: Genre): String =
 "${genre.numberOfTracks} songs"

 fun playlistSubtitle(playlist: Playlist): String =
 "${playlist.numberOfTracks} songs"

 /**
 * Convenience wrapper for [Extras.withLong] used by the
 * recently-played tab to layer the `PLAYED_AT` timestamp
 * extra on top of the standard [songExtras]. Mirrors the
 * legacy `MediaItemFactory.songExtrasWithPlayedAt` helper
 * (26i inlined the legacy `MediaItemFactory` here).
 */
 fun songExtrasWithPlayedAt(
 song: Song,
 albumId: String?,
 playedAt: Long,
 ): Extras = songExtras(song, albumId).withLong(PLAYED_AT_EXTRA_KEY, playedAt)

 /** Bundle-key for the per-row play timestamp on the "Recently played" tab. */
 const val PLAYED_AT_EXTRA_KEY = "PLAYED_AT"

 // ---- Media3 MediaItem builders.

 /**
 * Browsable folder MediaItem. The framework renders it as a
 * navigable row in the AAOS browse surface; the row's children
 * come from `onGetChildren(item.mediaId, ...)`. The `mediaType`
 * defaults to [MediaMetadata.MEDIA_TYPE_FOLDER_MIXED] for
 * generic folders (the root) but callers should pass
 * [MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS] /
 * [MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS] /
 * [MediaMetadata.MEDIA_TYPE_FOLDER_GENRES] /
 * [MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS] for the
 * 4 corresponding tabs so AAOS can pick the right icon and
 * content style.
 */
 fun browsable(
 id: String,
 title: CharSequence,
 subtitle: CharSequence? = null,
 description: CharSequence? = null,
 iconUri: Uri? = null,
 mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras: Extras = Extras.EMPTY,
 ): MediaItem {
 val metadata = MediaMetadata.Builder()
 .setTitle(title)
 .setIsBrowsable(true)
 .setIsPlayable(false)
 .setMediaType(mediaType)
 .also { builder ->
 subtitle?.let { builder.setSubtitle(it) }
 description?.let { builder.setDescription(it) }
 iconUri?.let { builder.setArtworkUri(it) }
 if (extras != Extras.EMPTY) {
 builder.setExtras(extras.toBundle())
 }
 }
 .build()
 return MediaItem.Builder()
 .setMediaId(id)
 .setMediaMetadata(metadata)
 .build()
 }

 /**
 * Playable leaf MediaItem. The framework renders it as a tappable
 * row that starts playback when activated. The `mediaType`
 * defaults to [MediaMetadata.MEDIA_TYPE_MUSIC] for songs but
 * callers can override for albums / artists / genres /
 * playlists that should be playable on their own
 * (`MEDIA_TYPE_ALBUM` / `MEDIA_TYPE_ARTIST` / `MEDIA_TYPE_GENRE` /
 * `MEDIA_TYPE_PLAYLIST`).
 */
 fun playable(
 id: String,
 title: CharSequence,
 subtitle: CharSequence? = null,
 description: CharSequence? = null,
 iconUri: Uri? = null,
 mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
 extras: Extras = Extras.EMPTY,
 ): MediaItem {
 val metadata = MediaMetadata.Builder()
 .setTitle(title)
 .setIsBrowsable(false)
 .setIsPlayable(true)
 .setMediaType(mediaType)
 .also { builder ->
 subtitle?.let { builder.setSubtitle(it) }
 description?.let { builder.setDescription(it) }
 iconUri?.let { builder.setArtworkUri(it) }
 if (extras != Extras.EMPTY) {
 builder.setExtras(extras.toBundle())
 }
 }
 .build()
 return MediaItem.Builder()
 .setMediaId(id)
 .setMediaMetadata(metadata)
 .build()
 }

 /**
 * Info-only (non-browsable / non-playable) MediaItem. The
 * framework renders it as a non-actionable list row — theser
 * can't tap it to drill in and tapping it doesn't start
 * playback. This is the right shape for the "No music yet" /
 * "Scanning…" / "Nothing played yet" placeholders.
 */
 fun nonPlayable(
 id: String,
 title: CharSequence,
 subtitle: CharSequence? = null,
 description: CharSequence? = null,
 iconUri: Uri? = null,
 mediaType: Int? = null,
 extras: Extras = Extras.EMPTY,
 ): MediaItem {
 val metadata = MediaMetadata.Builder()
 .setTitle(title)
 .setIsBrowsable(false)
 .setIsPlayable(false)
 .also { builder ->
 if (mediaType != null) builder.setMediaType(mediaType)
 subtitle?.let { builder.setSubtitle(it) }
 description?.let { builder.setDescription(it) }
 iconUri?.let { builder.setArtworkUri(it) }
 if (extras != Extras.EMPTY) {
 builder.setExtras(extras.toBundle())
 }
 }
 .build()
 return MediaItem.Builder()
 .setMediaId(id)
 .setMediaMetadata(metadata)
 .build()
 }


 // Perentity adapters: take a live Song/Album/etc. and produce; the corresponding Media3 MediaItem with the full display; contract. These are the entry points the new; KordXMediaLibraryService.MediaLibrarySession.Callback uses; to build the browse tree.

 /**
 * Browsable tab MediaItem for the 6 root tabs (Songs / Albums /
 * Artists / Genres / Playlists / Recently played). The
 * `mediaType` and `extras` are tab-specific so AAOS can pick
 * the right icon + content style.
 */
 fun browsableTab(
 id: String,
 title: CharSequence,
 subtitle: CharSequence? = null,
 mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
 extras: Extras = Extras.EMPTY,
 ): MediaItem = browsable(
 id = id,
 title = title,
 subtitle = subtitle,
 mediaType = mediaType,
 extras = extras,
 )

 /**
 * Browsable MediaItem for a single album. The drill-down
 * (`onGetChildren("album:<id>", ...)`) returns the album's songs
 * via the parallel [playableSongItem] builder.
 */
 fun browsableAlbum(
 id: String,
 album: Album,
 iconUri: Uri?,
 ): MediaItem {
 val subtitle = albumSubtitle(album).ifEmpty { null }
 val description = descriptionForAlbum(album).ifEmpty { null }
 return browsable(
 id = id,
 title = album.name,
 subtitle = subtitle,
 description = description,
 iconUri = iconUri,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
 extras = albumExtras(album),
 )
 }

 /**
 * Browsable MediaItem for a single albumArtist (the "Artists"
 * tab is album-artists, not song-artists — matches the legacy
 * `KordXMediaBrowserService` contract).
 */
 fun browsableAlbumArtist(
 id: String,
 albumArtist: AlbumArtist,
 iconUri: Uri?,
 ): MediaItem {
 val description = descriptionForAlbumArtist(albumArtist)
 return browsable(
 id = id,
 title = albumArtist.name,
 subtitle = albumArtistSubtitle(albumArtist),
 description = description,
 iconUri = iconUri,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
 extras = albumArtistExtras(albumArtist),
 )
 }

 /**
 * Browsable MediaItem for a single genre. The genre description
 * is empty (the legacy contract surfaces the song count in the
 * subtitle, not the description — see
 * `descriptionForGenre`).
 */
 fun browsableGenre(
 id: String,
 genre: Genre,
 ): MediaItem {
 val description = descriptionForGenre(genre).ifEmpty { null }
 return browsable(
 id = id,
 title = genre.name,
 subtitle = genreSubtitle(genre),
 description = description,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
 extras = genreExtras(genre),
 )
 }

 /**
 * Browsable MediaItem for a single playlist. The
 * `createdAt` is plumbed through to the display contract if
 * the model ever gains a creation timestamp — KordX's
 * `Playlist` model doesn't carry one today, so the description
 * is always empty (matches the legacy contract).
 */
 fun browsablePlaylist(
 id: String,
 playlist: Playlist,
 createdAt: Long? = null,
 ): MediaItem {
 val description = descriptionForPlaylist(playlist, createdAt).ifEmpty { null }
 return browsable(
 id = id,
 title = playlist.title,
 subtitle = playlistSubtitle(playlist),
 description = description,
 mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
 extras = playlistExtras(playlist, createdAt),
 )
 }

 /**
 * Playable song MediaItem. The drill-down
 * (`onGetChildren("album:<id>" / "albumArtist:<name>" / "genre:<name>" /
 * "playlist:<id>" / "tab_songs" / "tab_recent", ...)`) maps each
 * song id to a [playableSongItem] using the Song display
 * contract (title / subtitle / description / iconUri / extras).
 *
 * For the "Recently played" tab the caller layers the
 * `PLAYED_AT` extra onto the standard [songExtras] via
 * [songExtrasWithPlayedAt] before calling this builder.
 */
 fun playableSongItem(
 song: Song,
 iconUri: Uri?,
 albumId: String?,
 extras: Extras? = null,
 ): MediaItem {
 val resolvedExtras = extras ?: songExtras(song, albumId)
 val subtitle = songSubtitle(song).ifEmpty { null }
 val description = descriptionForSong(song).ifEmpty { null }
 return playable(
 id = KordXMediaSessionConstants.PREFIX_SONG + song.id,
 title = song.title,
 subtitle = subtitle,
 description = description,
 iconUri = iconUri,
 mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
 extras = resolvedExtras,
 )
 }
}

private fun Set<String>.joinToStringIfNotEmpty(): String? =
 takeIf { isNotEmpty() }?.joinToString()
