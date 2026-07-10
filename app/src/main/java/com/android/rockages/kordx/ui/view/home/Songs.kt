package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun SongsView(context: ViewContext) {
 val isUpdating by context.kordx.groove.song.isUpdating.collectAsState()
 val songIds by context.kordx.groove.song.all.collectAsState()
 val songsCount by context.kordx.groove.song.count.collectAsState()


 // During a library scan, show live progress (songs discovered so far); instead of a static "Loading..." label — better feedback for long scans.
 val loadingLabel = if (isUpdating && songsCount > 0) {
 context.kordx.t.XSongs(songsCount.toString())
 } else {
 null
 }

 LoaderScaffold(context, isLoading = isUpdating, loadingLabel = loadingLabel) {
 SongList(
 context,
 songIds = songIds,
 songsCount = songsCount,
 enableAddMediaFoldersHint = true,
 )
 }
}
