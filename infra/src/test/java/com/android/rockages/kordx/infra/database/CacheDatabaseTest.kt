package com.android.rockages.kordx.infra.database

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.android.rockages.kordx.core.groove.RecentPlay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Room tests for [CacheDatabase] using Robolectric + an in-memory database.
 *
 * These tests exercise the real Room DAO surface without an emulator. They are
 * intentionally narrow (recent plays CRUD) to keep the test suite fast and stable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class CacheDatabaseTest {

    private lateinit var database: CacheDatabase
    private lateinit var recentPlays: com.android.rockages.kordx.infra.database.store.RecentPlaysStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recentPlays = database.recentPlays()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryRecentPlay(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))

        val all = recentPlays.all(limit = 10)

        assertEquals(1, all.size)
        assertEquals("song-A", all.single().songId)
        assertEquals(1000L, all.single().playedAt)
    }

    @Test
    fun replaceOnConflictUpdatesTimestamp(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-A", playedAt = 2000L))

        val all = recentPlays.all(limit = 10)

        assertEquals(1, all.size)
        assertEquals(2000L, all.single().playedAt)
    }

    @Test
    fun allOrdersByPlayedAtDescending(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-B", playedAt = 3000L))
        recentPlays.insert(RecentPlay("song-C", playedAt = 2000L))

        val ids = recentPlays.all(limit = 10).map { it.songId }

        assertEquals(listOf("song-B", "song-C", "song-A"), ids)
    }

    @Test
    fun allRespectsLimit(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-B", playedAt = 2000L))
        recentPlays.insert(RecentPlay("song-C", playedAt = 3000L))

        val all = recentPlays.all(limit = 2)

        assertEquals(2, all.size)
        assertEquals(listOf("song-C", "song-B"), all.map { it.songId })
    }

    @Test
    fun allSongIdsReturnsIdsOnly(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-B", playedAt = 2000L))

        val ids = recentPlays.allSongIds(limit = 10)

        assertEquals(listOf("song-B", "song-A"), ids)
    }

    @Test
    fun deleteRemovesRow(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-B", playedAt = 2000L))

        val deleted = recentPlays.delete("song-A")
        val remaining = recentPlays.all(limit = 10)

        assertEquals(1, deleted)
        assertEquals(listOf("song-B"), remaining.map { it.songId })
    }

    @Test
    fun clearDropsAllRows(): Unit = runBlocking {
        recentPlays.insert(RecentPlay("song-A", playedAt = 1000L))
        recentPlays.insert(RecentPlay("song-B", playedAt = 2000L))

        val cleared = recentPlays.clear()
        val remaining = recentPlays.all(limit = 10)

        assertEquals(2, cleared)
        assertEquals(emptyList<RecentPlay>(), remaining)
    }
}
