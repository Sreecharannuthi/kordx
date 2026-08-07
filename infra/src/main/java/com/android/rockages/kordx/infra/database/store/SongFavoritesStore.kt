package com.android.rockages.kordx.infra.database.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.rockages.kordx.core.groove.SongFavorite

@Dao
interface SongFavoritesStore {
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insert(favorite: SongFavorite): Long

 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insertAll(favorites: List<SongFavorite>): List<Long>

 @Query("DELETE FROM song_favorites WHERE songId = :songId")
 suspend fun delete(songId: String): Int

 @Query("SELECT EXISTS(SELECT 1 FROM song_favorites WHERE songId = :songId)")
 suspend fun exists(songId: String): Boolean

 @Query("SELECT songId FROM song_favorites ORDER BY favoritedAt DESC")
 suspend fun allSongIds(): List<String>
}
