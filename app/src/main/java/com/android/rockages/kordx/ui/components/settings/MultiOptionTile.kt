package com.android.rockages.kordx.ui.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.ui.components.ScaffoldDialog
import com.android.rockages.kordx.ui.helpers.ViewContext
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsMultiOptionTile(
 context: ViewContext,
 icon: @Composable () -> Unit,
 title: @Composable () -> Unit,
 note: (@Composable () -> Unit)? = null,
 value: List<T>,
 values: Map<T, String>,
 satisfies: (List<T>) -> Boolean = { true },
 onChange: (List<T>) -> Unit,
) {
 var isOpen by rememberDebounceState()

 Card(
  colors = SettingsTileDefaults.cardColors(),
  shape = SettingsTileDefaults.cardShape(),
  onClick = {
   isOpen = !isOpen
  }
 ) {
  ListItem(
   colors = SettingsTileDefaults.listItemColors(),
   leadingContent = { icon() },
   headlineContent = { title() },
   supportingContent = { Text(value.joinToString { values[it]!! }) },
  )
 }

 if (isOpen) {
  val nValue = remember { value.toMutableStateList() }
  val orderedKeys by remember(nValue, values) {
   derivedStateOf {
    // selected items in their current order, followed by the unselected ones.
    (nValue + (values.keys - nValue.toSet())).distinct()
   }
  }
  val satisfied by remember(nValue) {
   derivedStateOf { satisfies(nValue.toList()) }
  }
  val modified by remember(nValue, value) {
   derivedStateOf { nValue.toList() != value }
  }

  ScaffoldDialog(
   onDismissRequest = {
    if (!modified) {
     isOpen = false
    }
   },
   title = title,
   topBar = {
    note?.let {
     Box(
      modifier = Modifier
       .padding(start = 24.dp, end = 24.dp, top = 16.dp)
       .alpha(0.7f)
     ) {
      ProvideTextStyle(MaterialTheme.typography.labelMedium) {
       it()
      }
     }
    }
   },
   content = {
    Column(
     modifier = Modifier
      .padding(0.dp, 12.dp)
      .verticalScroll(rememberScrollState())
    ) {
     orderedKeys.mapIndexed { i, key ->
      val selected = nValue.contains(key)
      val selectedIndex = nValue.indexOf(key)
      val toggleEntry: () -> Unit = {
       if (nValue.contains(key)) {
        nValue.remove(key)
       } else {
        nValue.add(key)
       }
      }
      Card(
       colors = SettingsTileDefaults.cardColors(),
       shape = MaterialTheme.shapes.small,
       modifier = Modifier.fillMaxWidth(),
       onClick = toggleEntry,
      ) {
       Row(
        modifier = Modifier.padding(12.dp, 0.dp),
        verticalAlignment = Alignment.CenterVertically
       ) {
        Row(
         modifier = Modifier.weight(1f),
         verticalAlignment = Alignment.CenterVertically
        ) {
         Checkbox(
          checked = selected,
          onCheckedChange = { toggleEntry() },
         )
         Spacer(modifier = Modifier.width(8.dp))
         Text(values[key]!!)
        }
        if (selected) {
         Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
           enabled = selectedIndex - 1 >= 0,
           onClick = {
            Collections.swap(nValue, selectedIndex - 1, selectedIndex)
           }
          ) {
           Icon(Icons.Filled.ArrowUpward, null)
          }
          IconButton(
           enabled = selectedIndex + 1 < nValue.size,
           onClick = {
            Collections.swap(nValue, selectedIndex + 1, selectedIndex)
           }
          ) {
           Icon(Icons.Filled.ArrowDownward, null)
          }
         }
        }
       }
      }
     }
    }
   },
   removeActionsVerticalPadding = true,
   actions = {
    TextButton(
     onClick = {
      isOpen = false
     }
    ) {
     Text(context.kordx.t.Cancel)
    }
    TextButton(
     enabled = modified && satisfied,
     onClick = {
      onChange(nValue.toList())
      isOpen = false
     }
    ) {
     Text(context.kordx.t.Done)
    }
   },
  )
 }
}
