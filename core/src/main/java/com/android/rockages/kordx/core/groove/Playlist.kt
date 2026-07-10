package com.android.rockages.kordx.core.groove

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("playlists")
data class Playlist(
 @PrimaryKey
 val id: String,
 val title: String,
 val songPaths: List<String>,
 val uri: Uri?,
 val path: String?,
) {
 val numberOfTracks: Int get() = songPaths.size
 val isLocal get() = uri != null
 val isNotLocal get() = uri == null

 fun withTitle(title: String) = Playlist(
 id = id,
 title = title,
 songPaths = songPaths,
 uri = uri,
 path = path,
 )

 companion object {
 const val PRIMARY_STORAGE = "primary:"
 }
}
