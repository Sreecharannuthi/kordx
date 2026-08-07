package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.ArtistGrid
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun ArtistsView(context: ViewContext) {
 val isUpdating by context.kordx.groove.artist.isUpdating.collectAsState()
 val artistNames by context.kordx.groove.artist.all.collectAsState()
 val artistsCount by context.kordx.groove.artist.count.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 ArtistGrid(
 context,
 artistName = artistNames,
 artistsCount = artistsCount,
 )
 }
}
