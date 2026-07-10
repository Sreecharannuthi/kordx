package com.android.rockages.kordx.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.services.groove.Groove
import com.android.rockages.kordx.ui.components.IntroductoryDialog
import com.android.rockages.kordx.ui.components.NowPlayingBottomBar
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.components.swipeable
import com.android.rockages.kordx.ui.helpers.ScaleTransition
import com.android.rockages.kordx.ui.helpers.SlideTransition
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.home.AlbumArtistsView
import com.android.rockages.kordx.ui.view.home.AlbumsView
import com.android.rockages.kordx.ui.view.home.ArtistsView
import com.android.rockages.kordx.ui.view.home.BrowserView
import com.android.rockages.kordx.ui.view.home.FoldersView
import com.android.rockages.kordx.ui.view.home.ForYouView
import com.android.rockages.kordx.ui.view.home.GenresView
import com.android.rockages.kordx.ui.view.home.PlaylistsView
import com.android.rockages.kordx.ui.view.home.SongsView
import com.android.rockages.kordx.ui.view.home.TreeView
import kotlinx.serialization.Serializable

enum class HomePage(
 val kind: Groove.Kind? = null,
 val label: (context: ViewContext) -> String,
 val selectedIcon: @Composable () -> ImageVector,
 val unselectedIcon: @Composable () -> ImageVector,
) {
 ForYou(
 label = { it.kordx.t.ForYou },
 selectedIcon = { Icons.Filled.Face },
 unselectedIcon = { Icons.Outlined.Face }
 ),
 Songs(
 kind = Groove.Kind.SONG,
 label = { it.kordx.t.Songs },
 selectedIcon = { Icons.Filled.MusicNote },
 unselectedIcon = { Icons.Outlined.MusicNote }
 ),
 Artists(
 kind = Groove.Kind.ARTIST,
 label = { it.kordx.t.Artists },
 selectedIcon = { Icons.Filled.Group },
 unselectedIcon = { Icons.Outlined.Group }
 ),
 Albums(
 kind = Groove.Kind.ALBUM,
 label = { it.kordx.t.Albums },
 selectedIcon = { Icons.Filled.Album },
 unselectedIcon = { Icons.Outlined.Album }
 ),
 AlbumArtists(
 kind = Groove.Kind.ALBUM_ARTIST,
 label = { it.kordx.t.AlbumArtists },
 selectedIcon = { Icons.Filled.SupervisorAccount },
 unselectedIcon = { Icons.Outlined.SupervisorAccount }
 ),
 Genres(
 kind = Groove.Kind.GENRE,
 label = { it.kordx.t.Genres },
 selectedIcon = { Icons.Filled.Tune },
 unselectedIcon = { Icons.Outlined.Tune }
 ),
 Playlists(
 kind = Groove.Kind.PLAYLIST,
 label = { it.kordx.t.Playlists },
 selectedIcon = { Icons.AutoMirrored.Filled.QueueMusic },
 unselectedIcon = { Icons.AutoMirrored.Outlined.QueueMusic }
 ),
 Browser(
 label = { it.kordx.t.Browser },
 selectedIcon = { Icons.Filled.Folder },
 unselectedIcon = { Icons.Outlined.Folder }
 ),
 Folders(
 label = { it.kordx.t.Folders },
 selectedIcon = { Icons.Filled.FolderOpen },
 unselectedIcon = { Icons.Outlined.FolderOpen }
 ),
 Tree(
 label = { it.kordx.t.Tree },
 selectedIcon = { Icons.Filled.AccountTree },
 unselectedIcon = { Icons.Outlined.AccountTree }
 );
}

enum class HomePageBottomBarLabelVisibility {
 ALWAYS_VISIBLE,
 VISIBLE_WHEN_ACTIVE,
 INVISIBLE,
}

@Serializable
object HomeViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(context: ViewContext) {
 val coroutineScope = rememberCoroutineScope()
 val readIntroductoryMessage by context.kordx.settings.readIntroductoryMessage.flow.collectAsState()
 val tabs by context.kordx.settings.homeTabs.flow.collectAsState()
 val labelVisibility by context.kordx.settings.homePageBottomBarLabelVisibility.flow.collectAsState()
 val currentTab by context.kordx.settings.lastHomeTab.flow.collectAsState()
 var showOptionsDropdown by remember { mutableStateOf(false) }
 var showTabsSheet by remember { mutableStateOf(false) }

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 navigationIcon = {
 IconButton(
 content = {
 Icon(Icons.Filled.Search, null)
 },
 onClick = {
 context.navController.navigate(SearchViewRoute(currentTab.kind?.name))
 }
 )
 },
 title = {
 Crossfade(
 label = "home-title",
 targetState = currentTab.label(context),
 ) {
 Box(
 modifier = Modifier.fillMaxSize(),
 contentAlignment = Alignment.Center
 ) {
 TopAppBarMinimalTitle { Text(it) }
 }
 }
 },
 actions = {
 IconButton(
 content = {
 Icon(
 Icons.Filled.MoreVert,

 // `contentDescription` so; TalkBack / the a11y tree can identify; this overflow menu button.
 context.kordx.t.More,
 )
 DropdownMenu(
 expanded = showOptionsDropdown,
 onDismissRequest = { showOptionsDropdown = false },
 containerColor = MaterialTheme.colorScheme.background,
 shape = RoundedCornerShape(12.dp),
 shadowElevation = 12.dp,
 offset = DpOffset((-4).dp, (-4).dp),
 ) {
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(
 Icons.Filled.Refresh,
 context.kordx.t.Rescan,
 )
 },
 text = {
 Text(context.kordx.t.Rescan)
 },
 onClick = {
 showOptionsDropdown = false
 context.kordx.radio.stop(ended = true)
 context.kordx.groove.fetch(
 Groove.FetchOptions(resetInMemoryCache = true),
 )
 }
 )
 DropdownMenuItem(
 modifier = Modifier.height(48.dp),
 contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
 leadingIcon = {
 Icon(
 Icons.Filled.Settings,
 context.kordx.t.Settings,
 )
 },
 text = {
 Text(context.kordx.t.Settings)
 },
 onClick = {
 showOptionsDropdown = false
 context.navController.navigate(SettingsViewRoute())
 }
 )
 }
 },
 onClick = {
 showOptionsDropdown = !showOptionsDropdown
 }
 )
 }
 )
 },
 content = { contentPadding ->
 AnimatedContent(
 label = "home-content",
 targetState = currentTab,
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize(),
 transitionSpec = {
 SlideTransition.slideUp.enterTransition()
 .togetherWith(ScaleTransition.scaleDown.exitTransition())
 },
 ) { page ->
 when (page) {
 HomePage.ForYou -> ForYouView(context)
 HomePage.Songs -> SongsView(context)
 HomePage.Albums -> AlbumsView(context)
 HomePage.Artists -> ArtistsView(context)
 HomePage.AlbumArtists -> AlbumArtistsView(context)
 HomePage.Genres -> GenresView(context)
 HomePage.Browser -> BrowserView(context)
 HomePage.Folders -> FoldersView(context)
 HomePage.Playlists -> PlaylistsView(context)
 HomePage.Tree -> TreeView(context)
 }
 }
 },
 bottomBar = {
 Column {
 NowPlayingBottomBar(context, false)
 NavigationBar(
 modifier = Modifier
 .pointerInput(Unit) {
 detectTapGestures {
 showTabsSheet = true
 }
 }
 .swipeable(onSwipeUp = {
 showTabsSheet = true
 })
 ) {
 Spacer(modifier = Modifier.width(2.dp))
 tabs.map { x ->
 val isSelected = currentTab == x
 val label = x.label(context)

 NavigationBarItem(
 selected = isSelected,
 alwaysShowLabel = labelVisibility == HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE,
 icon = {
 Crossfade(
 label = "home-bottom-bar",
 targetState = isSelected,
 ) {
 Icon(
 if (it) x.selectedIcon() else x.unselectedIcon(),
 label,
 )
 }
 },
 label = when (labelVisibility) {
 HomePageBottomBarLabelVisibility.INVISIBLE -> null
 else -> ({
 Text(
 label,
 style = MaterialTheme.typography.labelSmall,
 textAlign = TextAlign.Center,
 overflow = TextOverflow.Ellipsis,
 softWrap = false,
 )
 })
 },
 onClick = {
 when {
 isSelected -> {
 showTabsSheet = true
 }

 else -> context.kordx.settings.lastHomeTab.setValue(x)
 }
 }
 )
 }
 Spacer(modifier = Modifier.width(2.dp))
 }
 }
 }
 )

 if (showTabsSheet) {
 val sheetState = rememberModalBottomSheetState()
 val orderedTabs = remember {
 setOf<HomePage>(*tabs.toTypedArray(), *HomePage.entries.toTypedArray())
 }

 ModalBottomSheet(
 sheetState = sheetState,
 containerColor = MaterialTheme.colorScheme.surface,
 onDismissRequest = {
 showTabsSheet = false
 },
 ) {
 LazyVerticalGrid(
 modifier = Modifier.padding(6.dp),
 columns = GridCells.Fixed(tabs.size),
 horizontalArrangement = Arrangement.SpaceBetween,
 verticalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 items(orderedTabs.toList(), key = { it.ordinal }) { x ->
 val isSelected = x == currentTab
 val label = x.label(context)

 val containerColor = when {
 isSelected -> MaterialTheme.colorScheme.secondaryContainer
 else -> Color.Unspecified
 }
 val contentColor = when {
 isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
 else -> Color.Unspecified
 }

 Column(
 modifier = Modifier
 .weight(1f)
 .padding(2.dp, 0.dp)
 .clip(RoundedCornerShape(12.dp))
 .clickable {
 context.kordx.settings.lastHomeTab.setValue(x)
 showTabsSheet = false
 }
 .background(containerColor)
 .padding(0.dp, 8.dp),
 horizontalAlignment = Alignment.CenterHorizontally,
 ) {
 when {
 isSelected -> Icon(x.selectedIcon(), label, tint = contentColor)
 else -> Icon(x.unselectedIcon(), label)
 }
 Spacer(modifier = Modifier.height(8.dp))
 Text(
 label,
 style = MaterialTheme.typography.bodySmall.copy(color = contentColor),
 modifier = Modifier.padding(8.dp, 0.dp),
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 )
 }
 }
 }
 Spacer(modifier = Modifier.height(12.dp))
 }
 }

 if (!readIntroductoryMessage) {
 IntroductoryDialog(
 context,
 onDismissRequest = {
 context.kordx.settings.readIntroductoryMessage.setValue(true)
 },
 )
 }
}
