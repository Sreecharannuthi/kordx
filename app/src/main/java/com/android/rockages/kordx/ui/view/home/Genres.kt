package com.android.rockages.kordx.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.rockages.kordx.ui.components.GenreGrid
import com.android.rockages.kordx.ui.components.LoaderScaffold
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun GenresView(context: ViewContext) {
 val isUpdating by context.kordx.groove.genre.isUpdating.collectAsState()
 val genreNames by context.kordx.groove.genre.all.collectAsState()
 val genresCount by context.kordx.groove.genre.count.collectAsState()

 LoaderScaffold(context, isLoading = isUpdating) {
 GenreGrid(
 context,
 genreNames = genreNames,
 genresCount = genresCount,
 )
 }
}
