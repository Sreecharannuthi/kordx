package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.SongFavorite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Persistent favorite-songs store. — backs the Android Auto Now Playing card's `ACTION_FAVORITE` custom action. Backed by a Room table (`song_favorites` in `:infra` `PersistentDatabase`) and cached in a [MutableStateFlow] so callers can ask the in-memory state without a DB round-trip on every playback update. All DB calls go through `kordx.groove.coroutineScope` (the app's background scope) so Room's main-thread guard is never tripped. */
class SongFavoritesRepository(private val kordx: KordX) {
 private val _ids = MutableStateFlow<Set<String>>(emptySet())
 val ids = _ids.asStateFlow()

 fun isFavorite(songId: String): Boolean = _ids.value.contains(songId)

 fun favoriteSongIds(): Set<String> = _ids.value

 /**
 * Prime the in-memory cache from the DB. Safe to call from any thread;
 * the actual DB read happens on `kordx.groove.coroutineScope`.
 */
 fun loadFromDatabase() {
 kordx.groove.coroutineScope.launch {
 val rows = kordx.database.songFavorites.allSongIds()
 _ids.update { rows.toSet() }
 }
 }

 /**
 * Persist the favorite flag for [songId] (insert or delete). Returns the
 * new favorite state synchronously by updating the in-memory cache
 * optimistically; the DB write happens on the background scope.
 */
 fun toggle(songId: String) {
 val nowFavorite = !isFavorite(songId)
 _ids.update { current ->
 if (nowFavorite) current + songId else current - songId
 }
 kordx.groove.coroutineScope.launch {
 if (nowFavorite) {
 kordx.database.songFavorites.insert(
 SongFavorite(songId = songId, favoritedAt = System.currentTimeMillis())
 )
 } else {
 kordx.database.songFavorites.delete(songId)
 }
 }
 }
}
