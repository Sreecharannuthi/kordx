package com.android.rockages.kordx.ui.components

import androidx.compose.runtime.Composable
import com.android.rockages.kordx.core.groove.Playlist
import com.android.rockages.kordx.ui.helpers.ViewContext

@Composable
fun PlaylistInformationDialog(
 context: ViewContext,
 playlist: Playlist,
 onDismissRequest: () -> Unit,
) {
 InformationDialog(
 context,
 content = {
 InformationKeyValue(context.kordx.t.Id) {
 LongPressCopyableText(context, playlist.id)
 }
 InformationKeyValue(context.kordx.t.Title) {
 LongPressCopyableText(context, playlist.title)
 }
 InformationKeyValue(context.kordx.t.TrackCount) {
 LongPressCopyableText(context, playlist.numberOfTracks.toString())
 }
 InformationKeyValue(context.kordx.t.PlaylistStoreLocation) {
 LongPressCopyableText(
 context,
 when {
 playlist.isLocal -> context.kordx.t.LocalStorage
 else -> context.kordx.t.AppBuiltIn
 }
 )
 }
 playlist.path?.let {
 InformationKeyValue(context.kordx.t.Path) {
 LongPressCopyableText(context, it)
 }
 }
 },
 onDismissRequest = onDismissRequest,
 )
}
