package com.android.rockages.kordx.infra.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import android.content.Context
import com.android.rockages.kordx.infra.database.store.SongCacheStore
import com.android.rockages.kordx.infra.database.store.RecentPlaysStore
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.core.groove.RecentPlay
import com.android.rockages.kordx.core.utils.RoomConvertors

@Database(
 entities = [Song::class, RecentPlay::class],
 version = 3,
 autoMigrations = [
 AutoMigration(1, 2, CacheDatabase.Migration1To2::class),
 // v2 → v3: adds `recent_plays` table.
 AutoMigration(2, 3),
 ]
)
@TypeConverters(RoomConvertors::class)
abstract class CacheDatabase : RoomDatabase() {
 abstract fun songs(): SongCacheStore
 abstract fun recentPlays(): RecentPlaysStore

 companion object {
 fun create(context: Context) = Room
 .databaseBuilder(
 context,
 CacheDatabase::class.java,
 "cache"
 )
 .build()
 }

 @DeleteColumn("songs", "minBitrate")
 @DeleteColumn("songs", "maxBitrate")
 @DeleteColumn("songs", "bitsPerSample")
 @DeleteColumn("songs", "samples")
 @DeleteColumn("songs", "codec")
 class Migration1To2 : AutoMigrationSpec
}
