package com.android.rockages.kordx.core.groove

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent record that the user has played a song. — drives the "Recently played" root tab in Android Auto / AAOS. The primary key is the song id so re-playing a song is a single `OnConflictStrategy.REPLACE` insert that updates the `playedAt` timestamp (no duplicate rows, no need for an explicit "has it been played before?" check). Schema: identical shape to [SongFavorite] (`songId: String`, `playedAt: Long`) but a different table (`recent_plays`) and a different semantics ("this song was just played" vs "this song is favorited"). Both entities live in `:infra` `CacheDatabase` and are capped / queried by separate repositories. The DAO is [com.android.rockages.kordx.infra.database.store.RecentPlaysStore]. Kept in `:core` (alongside the rest of the groove data models) so both `:infra` (Room) and `:app` (RadioSession play-history hook, KordXMediaBrowserService recently-played tab) can depend on it without circular module wiring. */
@Entity("recent_plays")
data class RecentPlay(
 @PrimaryKey
 val songId: String,
 val playedAt: Long,
)
