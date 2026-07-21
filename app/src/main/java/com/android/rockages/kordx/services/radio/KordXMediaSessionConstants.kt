package com.android.rockages.kordx.services.radio

/** Shared media-id, debug-action, placeholder-reason and bundle-key constants for [KordXMediaLibraryService].
 *
 * Media-id convention:
 * - [ID_ROOT] root
 * - [ID_TAB_SONGS] / [ID_TAB_ALBUMS] / [ID_TAB_ARTISTS] / [ID_TAB_GENRES] / [ID_TAB_PLAYLISTS] / [ID_TAB_RECENT] tabs
 * - [PREFIX_ALBUM] + id songs in an album
 * - [PREFIX_ARTIST] + name songs by an artist (alias of albumArtist)
 * - [PREFIX_ALBUM_ARTIST] + name songs by an album artist
 * - [PREFIX_GENRE] + name songs in a genre
 * - [PREFIX_PLAYLIST] + id songs in a playlist
 * - [PREFIX_SONG] + id a single playable song (used by onPlayFromMediaId)
 */
internal object KordXMediaSessionConstants {
 const val ID_ROOT = "root"
 const val ID_TAB_SONGS = "tab_songs"
 const val ID_TAB_ALBUMS = "tab_albums"
 const val ID_TAB_ARTISTS = "tab_artists"
 const val ID_TAB_GENRES = "tab_genres"
 const val ID_TAB_PLAYLISTS = "tab_playlists"
 const val ID_TAB_RECENT = "tab_recent"

 const val PREFIX_SONG = "song:"
 const val PREFIX_ALBUM = "album:"
 const val PREFIX_ARTIST = "artist:"
 const val PREFIX_ALBUM_ARTIST = "albumArtist:"
 const val PREFIX_GENRE = "genre:"
 const val PREFIX_PLAYLIST = "playlist:"


 // Debug actions used by the `KordXMediaLibraryService` debug receivers registered in `onCreate`.

 const val DEBUG_ACTION_SCAN = "com.android.rockages.kordx.radio.DEBUG_SCAN"
 const val EXTRA_DEBUG_UPDATING = "updating"
 const val EXTRA_DEBUG_COUNT = "count"
 const val EXTRA_DEBUG_SONG_IDS = "song_ids"
 const val DEBUG_ACTION_SONG_LIST = "com.android.rockages.kordx.radio.DEBUG_SONG_LIST"


 // Placeholder reason + bundle-key constants, framework-agnostic — used by both the new Media3 service and the JVM
 // unit tests for `Media3ItemFactory.placeholderExtras`. Pinning the values in this shared file means a typo surfaces
 // as a test failure rather than a silent logcat-filter miss.

 const val EMPTY_REASON_NO_SONGS = "no_songs"
 const val EMPTY_REASON_SCANNING = "scanning"
 const val EMPTY_REASON_ERROR = "error"

 const val EXTRA_KEY_EMPTY_REASON = "com.android.rockages.kordx.EMPTY_REASON"
 const val EXTRA_KEY_SCAN_COUNT = "com.android.rockages.kordx.SCAN_COUNT"

 /**
 * Resolve a playable media id (as delivered to `onPlayFromMediaId`)
 * to a concrete song id, or `null` if it isn't a song leaf.
 * Returns the id with the [PREFIX_SONG] prefix stripped.
 */
 fun mediaIdToSongId(mediaId: String): String? =
 mediaId.takeIf { it.startsWith(PREFIX_SONG) }?.removePrefix(PREFIX_SONG)
}
