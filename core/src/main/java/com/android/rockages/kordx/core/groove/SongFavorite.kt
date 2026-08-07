package com.android.rockages.kordx.core.groove

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent flag for a song the user has favorited. — drives the "Add to favorites" / "Remove from favorites" custom action on the Android Auto Now Playing card. The primary key is the song id so toggling is a single insert or delete and a single `exists` query answers the state. Kept in `:core` (alongside the rest of the groove data models) so both `:infra` (Room) and `:app` (RadioSession custom action) can depend on it without circular module wiring. */
@Entity("song_favorites")
data class SongFavorite(
 @PrimaryKey
 val songId: String,
 val favoritedAt: Long,
)
