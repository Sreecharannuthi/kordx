package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.AlbumGrid
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun AlbumsView(context: ViewContext) {
 val isUpdating by context.kordx.groove.album.isUpdating.collectAsState()
 val albumIds by context.kordx.groove.album.all.collectAsState()
 val albumsCount by context.kordx.groove.album.count.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 AlbumGrid(
 context,
 albumIds = albumIds,
 albumsCount = albumsCount,
 )
 }
}
