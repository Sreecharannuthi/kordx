package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.core.utils.ActivityUtils

@Composable
fun LongPressCopyableText(context: ViewContext, text: String) {
 Text(
 text,
 modifier = Modifier.pointerInput(Unit) {
 detectTapGestures(onLongPress = {
 ActivityUtils.copyToClipboardAndNotify(
 context.kordx.applicationContext,
 text,
 context.kordx.t.CopiedXToClipboard(text),
 )
 })
 }
 )
}
