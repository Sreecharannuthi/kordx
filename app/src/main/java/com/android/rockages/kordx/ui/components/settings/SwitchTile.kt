package com.android.rockages.kordx.ui.components.settings

import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSwitchTile(
 icon: @Composable () -> Unit,
 title: @Composable () -> Unit,
 value: Boolean,
 onChange: (Boolean) -> Unit,
) {
 val lastToggle = remember { mutableStateOf(0L) }
 val toggle = {
  val now = System.currentTimeMillis()
  if (now - lastToggle.value >= 100L) {
   lastToggle.value = now
   onChange(!value)
  }
 }
 Card(
  colors = SettingsTileDefaults.cardColors(),
  shape = SettingsTileDefaults.cardShape(),
  onClick = toggle,
 ) {
  ListItem(
   colors = SettingsTileDefaults.listItemColors(),
   leadingContent = { icon() },
   headlineContent = { title() },
   trailingContent = {
    Switch(
     checked = value,
     onCheckedChange = { toggle() }
    )
   }
  )
 }
}
