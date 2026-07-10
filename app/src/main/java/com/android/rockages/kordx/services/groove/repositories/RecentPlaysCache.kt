package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.core.groove.RecentPlay

/** Pure in-memory cache of the most-recently-played songs. — the recently-played history kept by the app, capped at [limit] entries (default 20 per the plan, ordered by `playedAt DESC` (most recent first). Sits between the on-disk Room table (`recent_plays` in [com.android.rockages.kordx.infra.database.CacheDatabase]) and the readers in [com.android.rockages.kordx.services.radio.KordXMediaLibraryService.buildRecentPlaysTab] so the Auto tab can ask for the list without a DB round-trip on every browse. Decoupled from `KordX` / Room so the pure logic (add, replace, ordering, dedup, cap) is JVM-testable. The repository [RecentPlaysRepository] wraps this cache + the Room DAO. Threading: every mutating method takes a small private lock so a concurrent `add` (from `Radio.Events.Player.Started` on the radio event dispatcher) and `loadFromDatabase` (from `onKordXReady` on `groove.coroutineScope`) can't interleave and produce a torn list. Read access (`all`) takes the lock too so callers always see a consistent snapshot. */
internal class RecentPlaysCache(private val limit: Int) {
 private val lock = Any()
 private var entries: List<RecentPlay> = emptyList()

 /**
 * Current list, most-recent first, capped at [limit]. Returns a
 * defensive copy so callers can't mutate the cache's internal state
 * by holding a reference.
 */
 fun all(): List<RecentPlay> = synchronized(lock) { entries.toList() }

 /**
 * Record a play of [songId] at [playedAt] (millis since epoch).
 * If the song is already in the cache, the old entry is removed
 * first so re-playing a song surfaces as a single "most-recent"
 * entry (no duplicates). The list is then capped at [limit]:
 * the oldest entries beyond the cap are dropped.
 *
 * Returns the post-mutation list (same as [all] after the call)
 * so callers can log the new count without a second read.
 */
 fun add(songId: String, playedAt: Long): List<RecentPlay> {
 synchronized(lock) {
 val deduped = entries.filter { it.songId != songId }
 entries = (listOf(RecentPlay(songId, playedAt)) + deduped).take(limit)
 }
 return all()
 }

 /**
 * Replace the cache with [newEntries] (typically the result of
 * `dao.all(limit)` on app start). The new list is sorted by
 * `playedAt DESC` and capped at [limit] before it replaces the
 * previous list, so the cache invariant (ordered, capped) is
 * preserved even if the DAO's ordering drifts in Room
 * version.
 */
 fun replaceWith(newEntries: List<RecentPlay>) {
 synchronized(lock) {
 entries = newEntries
 .sortedByDescending { it.playedAt }
 .take(limit)
 }
 }

 /**
 * Drop every entry (used by [RecentPlaysRepository.clear] — the
 * "forget my history" affordance, if added ).
 */
 fun clear() {
 synchronized(lock) { entries = emptyList() }
 }
}
