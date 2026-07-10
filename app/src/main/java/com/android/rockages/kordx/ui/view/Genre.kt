package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.layout.Box
import com.android.rockages.kordx.services.groove.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.GenericSongListDropdown
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.SongList
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.helpers.ViewContext
import kotlinx.serialization.Serializable

@Serializable
data class GenreViewRoute(val genreName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreView(context: ViewContext, route: GenreViewRoute) {
 val allGenreNames by context.kordx.groove.genre.all.collectAsState()
 val allSongIds by context.kordx.groove.song.all.collectAsState()
 val genre by remember(allGenreNames) {
 derivedStateOf { context.kordx.groove.genre.get(route.genreName) }
 }
 val songIds by remember(genre, allSongIds) {
 derivedStateOf { genre?.getSongIds(context.kordx) ?: listOf() }
 }
 val isViable by remember(allGenreNames) {
 derivedStateOf { allGenreNames.contains(route.genreName) }
 }

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 navigationIcon = {
 IconButton(
 onClick = { context.navController.popBackStack() }
 ) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 title = {
 TopAppBarMinimalTitle {
 Text(context.kordx.t.Genre
 + (genre?.let { " - ${it.name}" } ?: ""))
 }
 },
 actions = {
 var showOptionsMenu by remember { mutableStateOf(false) }

 IconButton(
 onClick = {
 showOptionsMenu = !showOptionsMenu
 }
 ) {
 Icon(Icons.Filled.MoreVert, null)
 GenericSongListDropdown(
 context,
 songIds = songIds,
 expanded = showOptionsMenu,
 onDismissRequest = {
 showOptionsMenu = false
 }
 )
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 )
 },
 content = { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize()
 ) {
 when {
 isViable -> SongList(context, songIds = songIds)
 else -> UnknownGenre(context, route.genreName)
 }
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 }
 )
}

@Composable
private fun UnknownGenre(context: ViewContext, genre: String) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.Tune,
 null,
 modifier = modifier
 )
 },
 content = {
 Text(context.kordx.t.UnknownGenreX(genre))
 }
 )
}
