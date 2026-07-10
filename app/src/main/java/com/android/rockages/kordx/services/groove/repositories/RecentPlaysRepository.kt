package com.android.rockages.kordx.services.groove.repositories

import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.RecentPlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecentPlaysRepository(private val kordx: KordX) {

 // Note: do NOT capture `kordx.groove.coroutineScope` eagerly in; the constructor — `Groove` is still being constructed when; its `val recentPlays = RecentPlaysRepository(kordx)` line; runs (in `Groove.<init>`), so `kordx.groove.coroutineScope`; is null at this point and eager capture would NPE (caught; by the AVD validation: `Caused by: NullPointerException; ... at RecentPlaysRepository.<init>`). Access lazily through; `kordx.groove.coroutineScope` at call time instead — by then; the `Groove` class has finished construction and the scope; is nonnull. The same pattern is used by; [SongFavoritesRepository], which also defers all; `coroutineScope.launch { ... }` calls into the method bodies; rather than capturing the scope upfront.
 private val dao get() = kordx.database.recentPlays
 private val cache = RecentPlaysCache(limit = DEFAULT_LIMIT)

 private val _ids = MutableStateFlow<List<String>>(emptyList())
 /** Most-recent-first list of recently-played song ids. */
 val ids = _ids.asStateFlow()

 private val _entries = MutableStateFlow<List<RecentPlay>>(emptyList())
 /**
 * Most-recent-first list of the full [RecentPlay] entries
 * (id + timestamp). Mirrors the cache; updated synchronously by
 * [add] and [loadFromDatabase] so readers see a consistent
 * snapshot of the in-memory state. The `KordXMediaLibraryService`
 * `buildRecentPlaysTab` uses this flow to look up the
 * `playedAt` for the `PLAYED_AT` extra on each row.
 */
 val entries = _entries.asStateFlow()

 /** Snapshot of the most-recent [limit] entries (default [DEFAULT_LIMIT]). */
 fun recentSongIds(limit: Int = DEFAULT_LIMIT): List<String> =
 cache.all().take(limit).map { it.songId }

 /**
 * Prime the in-memory cache from the on-disk DB. Safe to call
 * from any thread; the DB read happens on the app's background
 * scope and the cache is replaced atomically when the read
 * returns. Idempotent: calling twice just re-reads the same
 * (small) table.
 */
 fun loadFromDatabase() {
 kordx.groove.coroutineScope.launch {
 val rows = dao.all(DEFAULT_LIMIT)
 cache.replaceWith(rows)
 _ids.update { rows.map { it.songId } }
 _entries.update { rows }
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RecentPlaysRepository",
 "loadFromDatabase: loaded ${rows.size} entries (ids=${rows.map { it.songId }})",
 )
 }
 }

 /**
 * Record a play of [songId] (called from [com.android.rockages.kordx.services.radio.RadioSession]
 * on `Events.Player.Started` — thelan "when a song starts
 * playing, not on every state change"). Updates the in-memory
 * cache synchronously so the next Auto browse shows the new play;
 * the DB write happens on the background scope.
 */
 fun add(songId: String) {
 val now = System.currentTimeMillis()
 val updated = cache.add(songId, now)
 _ids.update { updated.map { it.songId } }
 _entries.update { updated }
 com.android.rockages.kordx.core.utils.Logger.warn(
 "RecentPlaysRepository",
 "add: songId='$songId' (now $updated.size entries)",
 )
 kordx.groove.coroutineScope.launch {
 dao.insert(RecentPlay(songId, now))
 }
 }

 /** Drop every entry from both the cache and the DB. */
 fun clear() {
 cache.clear()
 _ids.update { emptyList() }
 _entries.update { emptyList() }
 kordx.groove.coroutineScope.launch {
 dao.clear()
 }
 }

 companion object {
 const val DEFAULT_LIMIT = 20
 }
}
