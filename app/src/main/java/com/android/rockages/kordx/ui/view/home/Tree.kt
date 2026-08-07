package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.components.SongTreeList
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun TreeView(context: ViewContext) {
 val isUpdating by context.kordx.groove.song.isUpdating.collectAsState()
 val songIds by context.kordx.groove.song.all.collectAsState()
 val songsCount by context.kordx.groove.song.count.collectAsState()
 val disabledTreePaths by context.kordx.settings.lastDisabledTreePaths.flow.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 SongTreeList(
 context,
 songIds = songIds,
 songsCount = songsCount,
 initialDisabled = disabledTreePaths.toList(),
 onDisable = { paths ->
 context.kordx.settings.lastDisabledTreePaths.setValue(paths.toSet())
 },
 )
 }
}
