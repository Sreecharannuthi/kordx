package com.android.rockages.kordx.ui.view

import androidx.compose.foundation.horizontalScroll
import com.android.rockages.kordx.services.groove.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.ui.components.AlbumArtistDropdownMenu
import com.android.rockages.kordx.ui.components.AlbumDropdownMenu
import com.android.rockages.kordx.ui.components.AnimatedNowPlayingBottomBar
import com.android.rockages.kordx.ui.components.ArtistDropdownMenu
import com.android.rockages.kordx.ui.components.GenericGrooveCard
import com.android.rockages.kordx.ui.components.IconTextBody
import com.android.rockages.kordx.ui.components.PlaylistDropdownMenu
import com.android.rockages.kordx.ui.components.SongCard
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.core.utils.joinToStringIfNotEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private data class SearchResult(
 val songIds: List<String>,
 val artistNames: List<String>,
 val albumIds: List<String>,
 val albumArtistNames: List<String>,
 val genreNames: List<String>,
 val playlistIds: List<String>,
)

@Serializable
data class SearchViewRoute(val initialChip: String?)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun SearchView(context: ViewContext, route: SearchViewRoute) {
 val coroutineScope = rememberCoroutineScope()
 var terms by rememberSaveable { mutableStateOf("") }
 var isSearching by remember { mutableStateOf(false) }
 var results by remember { mutableStateOf<SearchResult?>(null) }
 val initialChip = remember {
 route.initialChip?.let { enumValueOf<Groove.Kind>(it) }
 }
 var selectedChip by rememberSaveable {
 mutableStateOf(initialChip)
 }

 fun isChipSelected(kind: Groove.Kind) = selectedChip == null || selectedChip == kind

 fun setTerms(nTerms: String) {
 terms = nTerms
 if (nTerms.isNotEmpty()) {
 isSearching = true
 }
 }

 LaunchedEffect(Unit) {
 snapshotFlow { Pair(terms, selectedChip) }
 .debounce(250)
 .collectLatest { (currentTerms, _) ->
 if (currentTerms.isEmpty()) {
 results = null
 isSearching = false
 return@collectLatest
 }
 withContext(Dispatchers.Default) {
 val songIds = mutableListOf<String>()
 val artistNames = mutableListOf<String>()
 val albumIds = mutableListOf<String>()
 val albumArtistNames = mutableListOf<String>()
 val genreNames = mutableListOf<String>()
 val playlistIds = mutableListOf<String>()

 if (isChipSelected(Groove.Kind.SONG)) {
 songIds.addAll(
 context.kordx.groove.song
 .search(context.kordx.groove.song.ids(), currentTerms)
 .map { it.entity }
 )
 }
 if (isChipSelected(Groove.Kind.ARTIST)) {
 artistNames.addAll(
 context.kordx.groove.artist
 .search(context.kordx.groove.artist.ids(), currentTerms)
 .map { it.entity }
 )
 }
 if (isChipSelected(Groove.Kind.ALBUM)) {
 albumIds.addAll(
 context.kordx.groove.album
 .search(context.kordx.groove.album.ids(), currentTerms)
 .map { it.entity }
 )
 }
 if (isChipSelected(Groove.Kind.ALBUM_ARTIST)) {
 albumArtistNames.addAll(
 context.kordx.groove.albumArtist
 .search(context.kordx.groove.albumArtist.ids(), currentTerms)
 .map { it.entity }
 )
 }
 if (isChipSelected(Groove.Kind.GENRE)) {
 genreNames.addAll(
 context.kordx.groove.genre
 .search(context.kordx.groove.genre.ids(), currentTerms)
 .map { it.entity }
 )
 }
 if (isChipSelected(Groove.Kind.PLAYLIST)) {
 playlistIds.addAll(
 context.kordx.groove.playlist
 .search(context.kordx.groove.playlist.ids(), currentTerms)
 .map { it.entity }
 )
 }

 results = SearchResult(
 songIds = songIds,
 artistNames = artistNames,
 albumIds = albumIds,
 albumArtistNames = albumArtistNames,
 genreNames = genreNames,
 playlistIds = playlistIds,
 )
 }
 isSearching = false
 }
 }

 val configuration = LocalConfiguration.current
 val density = LocalDensity.current
 val textFieldFocusRequester = FocusRequester()
 val chipsScrollState = rememberScrollState()
 var initialScroll = remember { false }

 LaunchedEffect(Unit) {
 textFieldFocusRequester.requestFocus()
 }

 Scaffold(
 topBar = {
 Column(
 modifier = Modifier
 .windowInsetsPadding(TopAppBarDefaults.windowInsets)
 .clipToBounds()
 ) {
 TextField(
 modifier = Modifier
 .fillMaxWidth()
 .focusRequester(textFieldFocusRequester),
 keyboardOptions = KeyboardOptions(
 imeAction = ImeAction.Search,
 ),
 colors = TextFieldDefaults.colors(
 focusedContainerColor = Color.Transparent,
 unfocusedContainerColor = Color.Transparent,
 ),
 singleLine = true,
 value = terms,
 onValueChange = { setTerms(it) },
 placeholder = {
 Text(context.kordx.t.SearchYourMusic)
 },
 leadingIcon = {
 IconButton(
 onClick = {
 context.navController.popBackStack()
 }
 ) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 trailingIcon = {
 if (terms.isNotEmpty()) {
 IconButton(
 onClick = { setTerms("") }
 ) {
 Icon(Icons.Filled.Close, null)
 }
 }
 }
 )
 Spacer(modifier = Modifier.height(4.dp))
 Row(
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 modifier = Modifier.horizontalScroll(chipsScrollState)
 ) {
 Spacer(modifier = Modifier.width(4.dp))
 FilterChip(
 selected = selectedChip == null,
 label = {
 Text(context.kordx.t.All)
 },
 onClick = {
 selectedChip = null
 }
 )
 Groove.Kind.entries.map {
 FilterChip(
 selected = selectedChip == it,
 label = {
 Text(it.label(context))
 },
 modifier = Modifier.onGloballyPositioned { coordinates ->
 if (!initialScroll && initialChip == it) {
 val windowWidth = with(density) {
 configuration.screenWidthDp.dp.toPx()
 }
 val position = coordinates.positionInWindow()
 val start = position.x.toInt()
 val width = coordinates.size.width
 val end = start + width
 val scrollTo = when {
 width < windowWidth && end > windowWidth -> start + width
 start > windowWidth -> start
 else -> null
 }
 scrollTo?.let { v ->
 coroutineScope.launch {
 chipsScrollState.animateScrollTo(v)
 }
 }
 initialScroll = true
 }
 },
 onClick = {
 selectedChip = it
 }
 )
 }
 Spacer(modifier = Modifier.width(4.dp))
 }
 Spacer(modifier = Modifier.height(4.dp))
 }
 },
 content = { contentPadding ->
 results?.run {
 val hasSongs = isChipSelected(Groove.Kind.SONG) && songIds.isNotEmpty()
 val hasArtists = isChipSelected(Groove.Kind.ARTIST) && artistNames.isNotEmpty()
 val hasAlbums = isChipSelected(Groove.Kind.ALBUM) && albumIds.isNotEmpty()
 val hasAlbumArtists =
 isChipSelected(Groove.Kind.ALBUM_ARTIST) && albumArtistNames.isNotEmpty()
 val hasPlaylists =
 isChipSelected(Groove.Kind.PLAYLIST) && playlistIds.isNotEmpty()
 val hasGenres = isChipSelected(Groove.Kind.GENRE) && genreNames.isNotEmpty()
 val hasNoResults =
 !hasSongs && !hasArtists && !hasAlbums && !hasAlbumArtists && !hasPlaylists && !hasGenres

 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize(),
 ) {
 if (terms.isNotEmpty()) {
 when {
 isSearching -> {
 Box(modifier = Modifier.align(Alignment.Center)) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.Search,
 null,
 modifier = modifier
 )
 },
 content = {
 Text(context.kordx.t.FilteringResults)
 }
 )
 }
 }

 hasNoResults -> {
 Box(modifier = Modifier.align(Alignment.Center)) {
 IconTextBody(
 icon = { modifier ->
 Icon(
 Icons.Filled.PriorityHigh,
 null,
 modifier = modifier
 )
 },
 content = {
 Text(context.kordx.t.NoResultsFound)
 }
 )
 }
 }

 else -> {
 LazyColumn {
 if (hasSongs) {
 item { SideHeading(context, Groove.Kind.SONG) }
 items(songIds, key = { "song-$it" }) { songId ->
 context.kordx.groove.song.get(songId)?.let { song ->
 SongCard(context, song) {
 context.kordx.radio.shorty.playQueue(song.id)
 }
 }
 }
 }
 if (hasArtists) {
 item { SideHeading(context, Groove.Kind.ARTIST) }
 items(artistNames, key = { "artist-$it" }) { artistName ->
 context.kordx.groove.artist.get(artistName)
 ?.let { artist ->
 GenericGrooveCard(
 image = artist
 .createArtworkImageRequest(context.kordx)
 .build(),
 title = {
 Text(artist.name)
 },
 options = { expanded, onDismissRequest ->
 ArtistDropdownMenu(
 context,
 artist,
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 )
 },
 onClick = {
 context.navController.navigate(
 ArtistViewRoute(artist.name)
 )
 }
 )
 }
 }
 }
 if (hasAlbums) {
 item { SideHeading(context, Groove.Kind.ALBUM) }
 items(albumIds, key = { "album-$it" }) { albumId ->
 context.kordx.groove.album.get(albumId)
 ?.let { album ->
 GenericGrooveCard(
 image = album
 .createArtworkImageRequest(context.kordx)
 .build(),
 title = {
 Text(album.name)
 },
 subtitle = album.artists
 .joinToStringIfNotEmpty()
 ?.let { { Text(it) } },
 options = { expanded, onDismissRequest ->
 AlbumDropdownMenu(
 context,
 album,
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 )
 },
 onClick = {
 context.navController.navigate(
 AlbumViewRoute(album.id)
 )
 }
 )
 }
 }
 }
 if (hasAlbumArtists) {
 item { SideHeading(context, Groove.Kind.ALBUM_ARTIST) }
 items(albumArtistNames, key = { "albumartist-$it" }) { albumArtistName ->
 context.kordx.groove.albumArtist.get(albumArtistName)
 ?.let { albumArtist ->
 GenericGrooveCard(
 image = albumArtist
 .createArtworkImageRequest(context.kordx)
 .build(),
 title = {
 Text(albumArtist.name)
 },
 options = { expanded, onDismissRequest ->
 AlbumArtistDropdownMenu(
 context,
 albumArtist,
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 )
 },
 onClick = {
 context.navController.navigate(
 AlbumArtistViewRoute(albumArtist.name)
 )
 }
 )
 }
 }
 }
 if (hasPlaylists) {
 item { SideHeading(context, Groove.Kind.PLAYLIST) }
 items(playlistIds, key = { "playlist-$it" }) { playlistId ->
 context.kordx.groove.playlist.get(playlistId)
 ?.let { playlist ->
 GenericGrooveCard(
 image = playlist
 .createArtworkImageRequest(context.kordx)
 .build(),
 title = {
 Text(playlist.title)
 },
 options = { expanded, onDismissRequest ->
 PlaylistDropdownMenu(
 context,
 playlist,
 expanded = expanded,
 onDismissRequest = onDismissRequest,
 )
 },
 onClick = {
 context.navController.navigate(
 PlaylistViewRoute(playlist.id)
 )
 }
 )
 }
 }
 }
 if (hasGenres) {
 item { SideHeading(context, Groove.Kind.GENRE) }
 items(genreNames, key = { "genre-$it" }) { genreName ->
 context.kordx.groove.genre.get(genreName)
 ?.let { genre ->
 GenericGrooveCard(
 image = null,
 title = { Text(genre.name) },
 subtitle = {
 Text(
 context.kordx.t.XSongs(
 genre.numberOfTracks.toString()
 )
 )
 },
 options = null,
 onClick = {
 context.navController.navigate(
 GenreViewRoute(genre.name)
 )
 }
 )
 }
 }
 }
 item { Spacer(modifier = Modifier.height(12.dp)) }
 }
 }
 }
 }
 }
 }
 },
 bottomBar = {
 AnimatedNowPlayingBottomBar(context)
 }
 )
}

@Composable
private fun SideHeading(context: ViewContext, kind: Groove.Kind) {
 SideHeading(kind.label(context))
}

@Composable
private fun SideHeading(text: String) {
 Text(
 text,
 style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
 modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp)
 )
}

private fun Groove.Kind.label(context: ViewContext) = when (this) {
 Groove.Kind.SONG -> context.kordx.t.Songs
 Groove.Kind.ALBUM -> context.kordx.t.Albums
 Groove.Kind.ARTIST -> context.kordx.t.Artists
 Groove.Kind.ALBUM_ARTIST -> context.kordx.t.AlbumArtists
 Groove.Kind.GENRE -> context.kordx.t.Genres
 Groove.Kind.PLAYLIST -> context.kordx.t.Playlists
}
