package com.android.rockages.kordx.services.groove

import com.android.rockages.kordx.core.groove.RecentPlay
import com.android.rockages.kordx.services.groove.repositories.RecentPlaysCache
import com.android.rockages.kordx.services.groove.repositories.RecentPlaysRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** \u2014 JVM unit tests for [RecentPlaysCache], the pure in-memory store of the most-recently-played song ids. The cache is JVM-testable because it has no `KordX` / Room / Android dependency \u2014 the repository wraps it with the on-disk DAO and is exercised by the AVD validation in the plans "How to test" section. The four behaviors under test (the plans "add / all / clear / ordering by `playedAt DESC`"): 1. `add` puts the new entry at the head of the list. 2. `add` deduplicates by songId (re-playing moves the entry to the head, no duplicate row). 3. `add` caps the list at the configured limit (the oldest entries fall off). 4. `replaceWith` + `all` / `clear` produce the expected state. The companion [RecentPlaysRepository.DEFAULT_LIMIT] constant is pinned by [defaultLimitIsTwenty] so tweak of the cap surfaces as a test failure (matching the `KordXSearch.minMatchScoreIsSixty` test in ). */
class RecentPlaysCacheTest {

 @Test
 fun emptyCacheReturnsEmptyList() {
 val cache = RecentPlaysCache(limit = 3)
 assertEquals(emptyList<RecentPlay>(), cache.all())
 }

 @Test
 fun addPutsNewEntryAtHead() {
 val cache = RecentPlaysCache(limit = 5)
 cache.add(songId = "song-A", playedAt = 100L)
 cache.add(songId = "song-B", playedAt = 200L)
 cache.add(songId = "song-C", playedAt = 300L)
 val ids = cache.all().map { it.songId }
 assertEquals(listOf("song-C", "song-B", "song-A"), ids)
 }

 @Test
 fun addPreservesPlayedAtTimestamp() {
 val cache = RecentPlaysCache(limit = 5)
 cache.add(songId = "song-A", playedAt = 1_700_000_000_000L)
 assertEquals(1_700_000_000_000L, cache.all().single().playedAt)
 }

 @Test
 fun addDedupesBySongId() {
 val cache = RecentPlaysCache(limit = 5)
 cache.add(songId = "song-A", playedAt = 100L)
 cache.add(songId = "song-B", playedAt = 200L)
 cache.add(songId = "song-A", playedAt = 300L)
 val entries = cache.all()
 assertEquals(2, entries.size, "re-playing 'song-A' should not duplicate")
 assertEquals(listOf("song-A", "song-B"), entries.map { it.songId })
 // The deduped 'song-A' carries the new timestamp (most recent play wins).
 assertEquals(300L, entries.first().playedAt)
 }

 @Test
 fun addCapsAtLimit() {
 val cache = RecentPlaysCache(limit = 3)
 cache.add(songId = "song-1", playedAt = 1L)
 cache.add(songId = "song-2", playedAt = 2L)
 cache.add(songId = "song-3", playedAt = 3L)
 cache.add(songId = "song-4", playedAt = 4L)
 val ids = cache.all().map { it.songId }
 assertEquals(3, ids.size, "cache must cap at limit=3")
 assertEquals(listOf("song-4", "song-3", "song-2"), ids)
 // song-1 is the oldest and was pushed off the end.
 assertTrue("song-1" !in ids)
 }

 @Test
 fun addBelowLimitKeepsAllEntries() {
 val cache = RecentPlaysCache(limit = 10)
 for (i in 1..5) {
 cache.add(songId = "song-$i", playedAt = i.toLong())
 }
 assertEquals(5, cache.all().size)
 }

 @Test
 fun addAtLimitDoesNotEvictOnReadd() {
 val cache = RecentPlaysCache(limit = 3)
 cache.add(songId = "song-1", playedAt = 1L)
 cache.add(songId = "song-2", playedAt = 2L)
 cache.add(songId = "song-3", playedAt = 3L)

 // Readding 'song1' deduplicates and moves to head; the; list stays at 3 entries (no eviction required).
 cache.add(songId = "song-1", playedAt = 4L)
 assertEquals(3, cache.all().size)
 assertEquals(listOf("song-1", "song-3", "song-2"), cache.all().map { it.songId })
 }

 @Test
 fun allReturnsDefensiveCopy() {
 val cache = RecentPlaysCache(limit = 3)
 cache.add(songId = "song-A", playedAt = 100L)
 val snapshot = cache.all()
 // Mutating the returned list must not affect the cache.
 val original = snapshot.toList()
 val mutable = snapshot.toMutableList()
 mutable.add(RecentPlay("rogue", 999L))
 assertEquals(original, cache.all(), "cache state must be unchanged")
 }

 @Test
 fun clearDropsEveryEntry() {
 val cache = RecentPlaysCache(limit = 5)
 cache.add(songId = "song-A", playedAt = 100L)
 cache.add(songId = "song-B", playedAt = 200L)
 cache.clear()
 assertEquals(emptyList<RecentPlay>(), cache.all())
 }

 @Test
 fun replaceWithSortsByPlayedAtDesc() {
 val cache = RecentPlaysCache(limit = 5)
 cache.replaceWith(
 listOf(
 RecentPlay("song-A", 100L),
 RecentPlay("song-C", 300L),
 RecentPlay("song-B", 200L),
 )
 )
 // The incoming list is sorted by playedAt DESC and capped.
 assertEquals(
 listOf("song-C", "song-B", "song-A"),
 cache.all().map { it.songId },
 )
 }

 @Test
 fun replaceWithCapsAtLimit() {
 val cache = RecentPlaysCache(limit = 2)
 cache.replaceWith(
 listOf(
 RecentPlay("song-A", 100L),
 RecentPlay("song-B", 200L),
 RecentPlay("song-C", 300L),
 )
 )
 // Only the 2 most recent survive the cap.
 assertEquals(listOf("song-C", "song-B"), cache.all().map { it.songId })
 }

 @Test
 fun replaceWithEmptyListClearsCache() {
 val cache = RecentPlaysCache(limit = 5)
 cache.add(songId = "song-A", playedAt = 100L)
 cache.replaceWith(emptyList())
 assertEquals(emptyList<RecentPlay>(), cache.all())
 }

 @Test
 fun defaultLimitIsTwenty() {

 // Pin the cap so tweak is caught by; the test rather than silently changing uservisible; history depth. Mirrors `KordXSearch.minMatchScoreIsSixty`; from .
 assertEquals(20, RecentPlaysRepository.DEFAULT_LIMIT)
 }

 @Test
 fun limitIsRespectedForReplaceWith() {
 // A limit of 1 means only the most-recent entry survives.
 val cache = RecentPlaysCache(limit = 1)
 cache.replaceWith(
 listOf(
 RecentPlay("song-A", 100L),
 RecentPlay("song-B", 200L),
 )
 )
 assertEquals(listOf("song-B"), cache.all().map { it.songId })
 }
}
