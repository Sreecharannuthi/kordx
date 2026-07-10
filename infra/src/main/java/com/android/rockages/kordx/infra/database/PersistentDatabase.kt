package com.android.rockages.kordx.infra.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.android.rockages.kordx.infra.database.store.PlaylistStore
import com.android.rockages.kordx.infra.database.store.SongFavoritesStore
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.core.groove.SongFavorite
import com.android.rockages.kordx.core.utils.RoomConvertors

@Database(
 entities = [Playlist::class, SongFavorite::class],
 version = 2,
 autoMigrations = [AutoMigration(1, 2)],
)
@TypeConverters(RoomConvertors::class)
abstract class PersistentDatabase : RoomDatabase() {
 abstract fun playlists(): PlaylistStore
 abstract fun songFavorites(): SongFavoritesStore

 companion object {
 fun create(context: Context) = Room
 .databaseBuilder(
 context,
 PersistentDatabase::class.java,
 "persistent"
 )
 .build()
 }
}
