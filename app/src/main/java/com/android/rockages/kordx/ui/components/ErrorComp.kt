package com.android.rockages.kordx.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.android.rockages.kordx.MainActivity
import com.android.rockages.kordx.services.i18n.CommonTranslation

private const val IssuesUrl = "https://github.com/Sreecharannuthi/kordx/issues"

@Composable
fun ErrorComp(message: String, stackTrace: String) {
 val context = LocalContext.current

 // ErrorActivity has no KordX/ViewContext, so the app theme is unavailable;
 // fall back to platform dynamic color (minSdk 31) in the system dark/light
 // mode so the crash screen still looks like KordX instead of raw red.
 val dark = androidx.compose.foundation.isSystemInDarkTheme()
 val colorScheme = runCatching {
 if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
 }.getOrElse { if (dark) darkColorScheme() else lightColorScheme() }

 MaterialTheme(colorScheme = colorScheme) {
 Surface(
 modifier = Modifier.fillMaxSize(),
 color = MaterialTheme.colorScheme.background,
 ) {
 Column(
 modifier = Modifier
 .fillMaxSize()
 .verticalScroll(rememberScrollState())
 .padding(24.dp),
 horizontalAlignment = Alignment.CenterHorizontally,
 verticalArrangement = Arrangement.Center,
 ) {
 Icon(
 Icons.Filled.ErrorOutline,
 contentDescription = null,
 modifier = Modifier.size(72.dp),
 tint = MaterialTheme.colorScheme.error,
 )
 Spacer(modifier = Modifier.height(16.dp))
 Text(
 CommonTranslation.SomethingWentHorriblyWrong,
 style = MaterialTheme.typography.headlineSmall,
 fontWeight = FontWeight.Bold,
 textAlign = TextAlign.Center,
 )
 Spacer(modifier = Modifier.height(8.dp))
 Text(
 CommonTranslation.ErrorX(message),
 style = MaterialTheme.typography.bodyMedium,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 textAlign = TextAlign.Center,
 )
 Spacer(modifier = Modifier.height(24.dp))
 Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
 Button(
 onClick = {
 // Relaunch clean and close the crash screen so the user is
 // never stranded on it.
 val relaunch = Intent(context, MainActivity::class.java)
 .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
 context.startActivity(relaunch)
 (context as? android.app.Activity)?.finish()
 },
 ) {
 Icon(Icons.Filled.Home, contentDescription = null)
 Spacer(modifier = Modifier.width(8.dp))
 Text(CommonTranslation.Home)
 }
 TextButton(
 onClick = {
 context.startActivity(Intent(Intent.ACTION_VIEW, IssuesUrl.toUri()))
 },
 ) {
 Text(CommonTranslation.ReportAnIssue)
 }
 }
 Spacer(modifier = Modifier.height(16.dp))
 var showDetails by remember { mutableStateOf(false) }
 TextButton(onClick = { showDetails = !showDetails }) {
 Text(CommonTranslation.Details)
 Icon(
 if (showDetails) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
 contentDescription = null,
 )
 }
 if (showDetails) {
 Card(
 colors = CardDefaults.cardColors(
 containerColor = MaterialTheme.colorScheme.surfaceVariant,
 ),
 modifier = Modifier.fillMaxWidth(),
 ) {
 Text(
 stackTrace,
 style = MaterialTheme.typography.bodySmall,
 fontFamily = FontFamily.Monospace,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 modifier = Modifier
 .heightIn(max = 280.dp)
 .verticalScroll(rememberScrollState())
 .padding(16.dp),
 )
 }
 }
 }
 }
 }
}
