package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.rockages.kordx.ui.helpers.ViewContext

/** Introductory dialog shown on first launch. Update-check tiles were removed. */
@Composable
fun IntroductoryDialog(
 context: ViewContext,
 onDismissRequest: () -> Unit,
) {
 ScaffoldDialog(
 onDismissRequest = onDismissRequest,
 title = {
 Text("\uD83D\uDC4B " + context.kordx.t.HelloThere)
 },
 titleTrailing = {
 IconButton(onClick = onDismissRequest) {
 Icon(Icons.Filled.Close, contentDescription = context.kordx.t.Cancel)
 }
 },
 content = {
 Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
 Text(
 text = context.kordx.t.HintAddMediaFolders,
 modifier = Modifier.padding(16.dp, 12.dp),
 )
 Box(modifier = Modifier.height(8.dp))
 }
 }
 )
}
