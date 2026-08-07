package com.android.rockages.kordx.infra.database.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.rockages.kordx.core.groove.RecentPlay

/** Room DAO for the "Recently played" history, backing the Android Auto root
 * tab and the in-memory `RecentPlaysRepository` cache. Uses `REPLACE` on insert
 * so re-plays update the timestamp; queries are ordered by `playedAt DESC`. */
@Dao
interface RecentPlaysStore {
 @Insert(onConflict = OnConflictStrategy.REPLACE)
 suspend fun insert(recentPlay: RecentPlay): Long

 @Query("DELETE FROM recent_plays WHERE songId = :songId")
 suspend fun delete(songId: String): Int

 @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT :limit")
 suspend fun all(limit: Int): List<RecentPlay>

 @Query("SELECT songId FROM recent_plays ORDER BY playedAt DESC LIMIT :limit")
 suspend fun allSongIds(limit: Int): List<String>

 @Query("DELETE FROM recent_plays")
 suspend fun clear(): Int
}
