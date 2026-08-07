package com.android.rockages.kordx.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.rockages.kordx.ui.components.IconButtonPlaceholder
import com.android.rockages.kordx.ui.components.TopAppBarMinimalTitle
import com.android.rockages.kordx.ui.components.settings.SettingsMultiOptionTile
import com.android.rockages.kordx.ui.components.settings.SettingsOptionTile
import com.android.rockages.kordx.ui.components.settings.SettingsSideHeading
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.HomePage
import com.android.rockages.kordx.ui.view.HomePageBottomBarLabelVisibility
import com.android.rockages.kordx.ui.view.home.ForYou
import kotlinx.serialization.Serializable

@Serializable
object HomePageSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageSettingsView(context: ViewContext) {
 val scrollState = rememberScrollState()
 val homeTabs by context.kordx.settings.homeTabs.flow.collectAsState()
 val forYouContents by context.kordx.settings.forYouContents.flow.collectAsState()
 val homePageBottomBarLabelVisibility by context.kordx.settings.homePageBottomBarLabelVisibility.flow.collectAsState()

 Scaffold(
 modifier = Modifier.fillMaxSize(),
 topBar = {
 CenterAlignedTopAppBar(
 title = {
 TopAppBarMinimalTitle {
 Text("${context.kordx.t.Settings} - ${context.kordx.t.Home}")
 }
 },
 colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
 containerColor = Color.Transparent
 ),
 navigationIcon = {
 IconButton(
 onClick = {
 context.navController.popBackStack()
 }
 ) {
 Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
 }
 },
 actions = {
 IconButtonPlaceholder()
 },
 )
 },
 content = { contentPadding ->
 Box(
 modifier = Modifier
 .padding(contentPadding)
 .fillMaxSize()
 ) {
 Column(modifier = Modifier.verticalScroll(scrollState)) {
 SettingsSideHeading(context.kordx.t.Home)
 SettingsMultiOptionTile(
 context,
 icon = {
 Icon(Icons.Filled.Home, null)
 },
 title = {
 Text(context.kordx.t.HomeTabs)
 },
 note = {
 Text(context.kordx.t.SelectAtleast2orAtmost5Tabs)
 },
 value = homeTabs,
 values = HomePage.entries.associateWith { it.label(context) },
 satisfies = { it.size in 2..5 },
 onChange = { value ->
 context.kordx.settings.homeTabs.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsMultiOptionTile(
 context,
 icon = {
 Icon(Icons.Filled.Recommend, null)
 },
 title = {
 Text(context.kordx.t.ForYou)
 },
 value = forYouContents,
 values = ForYou.entries.associateWith { it.label(context) },
 onChange = { value ->
 context.kordx.settings.forYouContents.setValue(value)
 }
 )
 HorizontalDivider()
 SettingsOptionTile(
 icon = {
 Icon(Icons.AutoMirrored.Filled.Label, null)
 },
 title = {
 Text(context.kordx.t.BottomBarLabelVisibility)
 },
 value = homePageBottomBarLabelVisibility,
 values = HomePageBottomBarLabelVisibility.entries
 .associateWith { it.label(context) },
 onChange = { value ->
 context.kordx.settings.homePageBottomBarLabelVisibility.setValue(
 value,
 )
 }
 )
 }
 }
 }
 )
}

fun HomePageBottomBarLabelVisibility.label(context: ViewContext) = when (this) {
 HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE -> context.kordx.t.AlwaysVisible
 HomePageBottomBarLabelVisibility.VISIBLE_WHEN_ACTIVE -> context.kordx.t.VisibleWhenActive
 HomePageBottomBarLabelVisibility.INVISIBLE -> context.kordx.t.Invisible
}