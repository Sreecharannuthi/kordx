package com.android.rockages.kordx.ui.components.settings

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

object SettingsTileDefaults {
 @Composable
 fun cardColors() = CardDefaults.cardColors(
 containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
 disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.38f),
 )

 @Composable
 fun cardShape() = MaterialTheme.shapes.small

 @Composable
 fun listItemColors(enabled: Boolean = true) = when {
 enabled -> ListItemDefaults.colors(containerColor = Color.Transparent)
 else -> ListItemDefaults.colors(
 containerColor = Color.Transparent,
 leadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
 trailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
 headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
 supportingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
 )
 }
}

@Composable
fun rememberDebounceState(default: Boolean = false, debounceMs: Long = 250): MutableState<Boolean> {
 val state = remember { mutableStateOf(default) }
 val lastChange = remember { mutableStateOf(0L) }
 return remember {
  object : MutableState<Boolean> {
   override var value: Boolean
    get() = state.value
    set(value) {
     val now = System.currentTimeMillis()
     if (value != state.value && now - lastChange.value >= debounceMs) {
      lastChange.value = now
      state.value = value
     }
    }
   override operator fun component1(): Boolean = state.value
   override operator fun component2(): (Boolean) -> Unit = { this.value = it }
  }
 }
}
