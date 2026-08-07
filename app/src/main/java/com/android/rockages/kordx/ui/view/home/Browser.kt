package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.components.SongExplorerList
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.core.utils.SimplePath

@Composable
fun BrowserView(context: ViewContext) {
 val isUpdating by context.kordx.groove.song.isUpdating.collectAsState()
 val id by context.kordx.groove.song.id.collectAsState()
 val explorer = context.kordx.groove.song.explorer
 val lastUsedFolderPath by context.kordx.settings.lastUsedBrowserPath.flow.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 SongExplorerList(
 context,
 initialPath = lastUsedFolderPath?.let { SimplePath(it) },
 key = id,
 explorer = explorer,
 onPathChange = { path ->
 context.kordx.settings.lastUsedBrowserPath.setValue(path.pathString)
 }
 )
 }
}
