package com.android.rockages.kordx.ui.view

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.android.rockages.kordx.MainActivity
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.utils.Logger
import com.android.rockages.kordx.ui.helpers.ScaleTransition
import com.android.rockages.kordx.ui.helpers.SlideTransition
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.theme.KordXTheme
import com.android.rockages.kordx.ui.view.settings.AppearanceSettingsView
import com.android.rockages.kordx.ui.view.settings.AppearanceSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.GrooveSettingsView
import com.android.rockages.kordx.ui.view.settings.GrooveSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.HomePageSettingsView
import com.android.rockages.kordx.ui.view.settings.HomePageSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.MiniPlayerSettingsView
import com.android.rockages.kordx.ui.view.settings.MiniPlayerSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.NowPlayingSettingsView
import com.android.rockages.kordx.ui.view.settings.NowPlayingSettingsViewRoute
import com.android.rockages.kordx.ui.view.settings.PlayerSettingsView
import com.android.rockages.kordx.ui.view.settings.PlayerSettingsViewRoute

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.serializer

@Composable
fun BaseView(kordx: KordX, activity: MainActivity) {
 val navController = rememberNavController()

 // Self-heal against an over-popped back stack: a rapid double
 // system-back can pop past the start destination (androidx.navigation
 // predictive-back race), leaving the back stack EMPTY — NavHost then
 // composes nothing, the ComposeView measures 0x0, and the window shows
 // a white screen while the process (and playback) stays alive. Detect
 // the empty state and restore Home instead of stranding the user.
 LaunchedEffect(navController) {
 var sawEntry = false
 navController.currentBackStackEntryFlow.collect { entry ->
 if (entry != null) {
 sawEntry = true
 } else if (sawEntry) {
 Logger.warn(
 "BaseView",
 "nav back stack empty (over-popped); restoring HomeViewRoute",
 )
 navController.navigate(HomeViewRoute)
 }
 }
 }
 val context = remember {
 ViewContext(
 kordx = kordx,
 activity = activity,
 navController = navController,
 )
 }

 KordXTheme(context) {
 Surface(color = MaterialTheme.colorScheme.background) {
 NavHost(
 navController = navController,
 startDestination = HomeViewRoute,
 ) {
 baseComposable<HomeViewRoute> {
 HomeView(context)
 }
 baseComposable<NowPlayingViewRoute> {
 NowPlayingView(context)
 }
 baseComposable<QueueViewRoute> {
 QueueView(context)
 }
 baseComposable<ArtistViewRoute> {
 ArtistView(context, it.toRoute())
 }
 baseComposable<AlbumViewRoute> {
 AlbumView(context, it.toRoute())
 }
 baseComposable<SearchViewRoute> {
 SearchView(context, it.toRoute())
 }
 baseComposable<AlbumArtistViewRoute> {
 AlbumArtistView(context, it.toRoute())
 }
 baseComposable<GenreViewRoute> {
 GenreView(context, it.toRoute())
 }
 baseComposable<PlaylistViewRoute> {
 PlaylistView(context, it.toRoute())
 }
 baseComposable<LyricsViewRoute> {
 LyricsView(context)
 }
 baseComposable<SettingsViewRoute> {
 SettingsView(context, it.toRoute())
 }
 baseComposable<AppearanceSettingsViewRoute> {
 AppearanceSettingsView(context)
 }
 baseComposable<GrooveSettingsViewRoute> {
 GrooveSettingsView(context, it.toRoute())
 }
 baseComposable<HomePageSettingsViewRoute> {
 HomePageSettingsView(context)
 }
 baseComposable<MiniPlayerSettingsViewRoute> {
 MiniPlayerSettingsView(context)
 }
 baseComposable<NowPlayingSettingsViewRoute> {
 NowPlayingSettingsView(context)
 }
 baseComposable<PlayerSettingsViewRoute> {
 PlayerSettingsView(context)
 }

 }
 }
 }
}

private inline fun <reified T : Any> NavGraphBuilder.baseComposable(
 noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
 composable<T>(
 popEnterTransition = {
 when {
 isInitialRoute<NowPlayingViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
 isInitialRoute<QueueViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
 isInitialRoute<LyricsViewRoute>() -> ScaleTransition.scaleUp.enterTransition()
 else -> SlideTransition.slideRight.enterTransition()
 }
 },
 popExitTransition = {
 when {
 isInitialRoute<NowPlayingViewRoute>() -> SlideTransition.slideDown.exitTransition()
 isInitialRoute<QueueViewRoute>() -> SlideTransition.slideDown.exitTransition()
 isInitialRoute<LyricsViewRoute>() -> SlideTransition.slideDown.exitTransition()
 else -> SlideTransition.slideRight.exitTransition()
 }
 },
 enterTransition = {
 when {
 isTargetRoute<NowPlayingViewRoute>() -> SlideTransition.slideUp.enterTransition()
 isTargetRoute<QueueViewRoute>() -> SlideTransition.slideUp.enterTransition()
 isTargetRoute<LyricsViewRoute>() -> SlideTransition.slideUp.enterTransition()
 else -> SlideTransition.slideLeft.enterTransition()
 }
 },
 exitTransition = {
 when {
 isTargetRoute<NowPlayingViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
 isTargetRoute<QueueViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
 isTargetRoute<LyricsViewRoute>() -> ScaleTransition.scaleDown.exitTransition()
 else -> SlideTransition.slideLeft.exitTransition()
 }
 },
 ) {
 content(it)
 }
}

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> NavDestination.isRoute() =
 route?.contains(serializer<T>().descriptor.serialName) == true

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> AnimatedContentTransitionScope<NavBackStackEntry>.isInitialRoute() =
 initialState.destination.isRoute<T>()

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> AnimatedContentTransitionScope<NavBackStackEntry>.isTargetRoute() =
 targetState.destination.isRoute<T>()
