package com.android.rockages.kordx.services.groove

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.services.groove.repositories.AlbumArtistRepository
import com.android.rockages.kordx.services.groove.repositories.AlbumRepository
import com.android.rockages.kordx.services.groove.repositories.ArtistRepository
import com.android.rockages.kordx.services.groove.repositories.GenreRepository
import com.android.rockages.kordx.services.groove.repositories.PlaylistRepository
import com.android.rockages.kordx.services.groove.repositories.RecentPlaysRepository
import com.android.rockages.kordx.services.groove.repositories.SongFavoritesRepository
import com.android.rockages.kordx.services.groove.repositories.SongRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class Groove(private val kordx: KordX) : KordX.Hooks {
 enum class Kind {
 SONG,
 ALBUM,
 ARTIST,
 ALBUM_ARTIST,
 GENRE,
 PLAYLIST,
 }

 val coroutineScope = CoroutineScope(Dispatchers.Default)
 var readyDeferred = CompletableDeferred<Boolean>()

 val exposer = MediaExposer(kordx)
 val song = SongRepository(kordx)
 val album = AlbumRepository(kordx)
 val artist = ArtistRepository(kordx)
 val albumArtist = AlbumArtistRepository(kordx)
 val genre = GenreRepository(kordx)
 val playlist = PlaylistRepository(kordx)
 val songFavorites = SongFavoritesRepository(kordx)
 val recentPlays = RecentPlaysRepository(kordx)

 private suspend fun fetch() {
 coroutineScope.launch {
 awaitAll(
 async { exposer.fetch() },
 async { playlist.fetch() },
 )
 }.join()
 }

 private suspend fun reset() {
 coroutineScope.launch {
 awaitAll(
 async { exposer.reset() },
 async { albumArtist.reset() },
 async { album.reset() },
 async { artist.reset() },
 async { genre.reset() },
 async { playlist.reset() },
 async { song.reset() },
 )
 }.join()
 }

 private suspend fun clearCache() {
 kordx.database.songCache.clear()
 kordx.database.artworkCache.clear()
 kordx.database.lyricsCache.clear()
 }

 data class FetchOptions(
 val resetInMemoryCache: Boolean = false,
 val resetPersistentCache: Boolean = false,
 )

 fun fetch(options: FetchOptions) {
 coroutineScope.launch {
 if (options.resetInMemoryCache) {
 reset()
 }
 if (options.resetPersistentCache) {
 clearCache()
 }
 fetch()
 }
 }

 override fun onKordXReady() {
 coroutineScope.launch {
 fetch()
 songFavorites.loadFromDatabase()
 recentPlays.loadFromDatabase()
 readyDeferred.complete(true)
 }
 }
}
