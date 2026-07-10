package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.AlbumArtistGrid
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun AlbumArtistsView(context: ViewContext) {
 val isUpdating by context.kordx.groove.albumArtist.isUpdating.collectAsState()
 val albumArtistNames by context.kordx.groove.albumArtist.all.collectAsState()
 val albumArtistsCount by context.kordx.groove.albumArtist.count.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 AlbumArtistGrid(
 context,
 albumArtistNames = albumArtistNames,
 albumArtistsCount = albumArtistsCount,
 )
 }
}
