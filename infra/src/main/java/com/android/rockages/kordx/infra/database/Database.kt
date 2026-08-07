package com.android.rockages.kordx.infra.database

import android.content.Context
import com.android.rockages.kordx.infra.database.store.ArtworkCacheStore
import com.android.rockages.kordx.infra.database.store.LyricsCacheStore

class Database(context: Context) {
 private val cache = CacheDatabase.create(context)
 private val persistent = PersistentDatabase.create(context)

 val artworkCache = ArtworkCacheStore(context)
 val lyricsCache = LyricsCacheStore(context)
 val songCache get() = cache.songs()
 val recentPlays get() = cache.recentPlays()
 val playlists get() = persistent.playlists()
 val songFavorites get() = persistent.songFavorites()
}
