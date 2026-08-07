package com.android.rockages.kordx.services.groove

import android.net.Uri
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.core.utils.DocumentFileX
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

/** Service-layer parser that reads an M3U playlist file into a [Playlist] entity, keeping the :core data class app-shell-free. */
object PlaylistParser {

 fun parse(kordx: KordX, playlistId: String?, uri: Uri): Playlist {
 val file = DocumentFileX.fromSingleUri(kordx.applicationContext, uri)!!
 val content = kordx.applicationContext.contentResolver.openInputStream(uri)
 ?.use { String(it.readBytes()) } ?: ""
 val songPaths = content.lineSequence()
 .map { it.trim() }
 .filter { it.isNotEmpty() && it[0] != '#' }
 .toList()
 val id = playlistId ?: kordx.groove.playlist.idGenerator.next()
 val path = DocumentFileX.getParentPathOfSingleUri(file.uri) ?: file.name
 return Playlist(
 id = id,
 title = Path(path).nameWithoutExtension,
 songPaths = songPaths,
 uri = uri,
 path = path,
 )
 }
}
