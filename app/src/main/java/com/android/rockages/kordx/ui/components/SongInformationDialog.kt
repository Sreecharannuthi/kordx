package com.android.rockages.kordx.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.ViewContext
import com.android.rockages.kordx.ui.view.AlbumArtistViewRoute
import com.android.rockages.kordx.ui.view.AlbumViewRoute
import com.android.rockages.kordx.ui.view.ArtistViewRoute
import com.android.rockages.kordx.ui.view.GenreViewRoute
import com.android.rockages.kordx.core.utils.ActivityUtils
import com.android.rockages.kordx.core.utils.DurationUtils
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.round

@Composable
fun SongInformationDialog(context: ViewContext, song: Song, onDismissRequest: () -> Unit) {
 InformationDialog(
 context,
 content = {
 InformationKeyValue(context.kordx.t.Id) {
 LongPressCopyableText(context, song.id)
 }
 InformationKeyValue(context.kordx.t.TrackName) {
 LongPressCopyableText(context, song.title)
 }
 if (song.artists.isNotEmpty()) {
 InformationKeyValue(context.kordx.t.Artist) {
 LongPressCopyableAndTappableText(context, song.artists) {
 onDismissRequest()
 context.navController.navigate(ArtistViewRoute(it))
 }
 }
 }
 if (song.albumArtists.isNotEmpty()) {
 InformationKeyValue(context.kordx.t.AlbumArtist) {
 LongPressCopyableAndTappableText(context, song.albumArtists) {
 onDismissRequest()
 context.navController.navigate(AlbumArtistViewRoute(it))
 }
 }
 }
 if (song.composers.isNotEmpty()) {
 InformationKeyValue(context.kordx.t.Composer) {
 // TODO composers page maybe?
 LongPressCopyableAndTappableText(context, song.composers) {
 onDismissRequest()
 context.navController.navigate(ArtistViewRoute(it))
 }
 }
 }
 context.kordx.groove.album.getIdFromSong(song)?.let { albumId ->
 InformationKeyValue(context.kordx.t.Album) {
 LongPressCopyableAndTappableText(context, setOf(song.album!!)) {
 onDismissRequest()
 context.navController.navigate(AlbumViewRoute(albumId))
 }
 }
 }
 if (song.genres.isNotEmpty()) {
 InformationKeyValue(context.kordx.t.Genre) {
 LongPressCopyableAndTappableText(context, song.genres) {
 onDismissRequest()
 context.navController.navigate(GenreViewRoute(it))
 }
 }
 }
 song.date?.let {
 InformationKeyValue(context.kordx.t.Date) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.year?.let {
 InformationKeyValue(context.kordx.t.Year) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.trackNumber?.let {
 InformationKeyValue(context.kordx.t.TrackNumber) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.trackTotal?.let {
 InformationKeyValue(context.kordx.t.TrackCount) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.discNumber?.let {
 InformationKeyValue(context.kordx.t.DiscNumber) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.discTotal?.let {
 InformationKeyValue(context.kordx.t.DiscTotal) {
 LongPressCopyableText(context, it.toString())
 }
 }
 InformationKeyValue(context.kordx.t.Duration) {
 LongPressCopyableText(context, DurationUtils.formatMs(song.duration))
 }
 song.encoder?.let {
 InformationKeyValue(context.kordx.t.Encoder) {
 LongPressCopyableText(context, it)
 }
 }
 song.channels?.let {
 InformationKeyValue(context.kordx.t.AudioChannels) {
 LongPressCopyableText(context, it.toString())
 }
 }
 song.bitrateK?.let {
 InformationKeyValue(context.kordx.t.Bitrate) {
 val text = buildString {
 append(context.kordx.t.XKbps(it.toString()))
 }
 LongPressCopyableText(context, text)
 }
 }
 song.samplingRateK?.let {
 InformationKeyValue(context.kordx.t.SamplingRate) {
 LongPressCopyableText(context, context.kordx.t.XKHz(it.toString()))
 }
 }
 InformationKeyValue(context.kordx.t.Filename) {
 LongPressCopyableText(context, song.filename)
 }
 InformationKeyValue(context.kordx.t.Path) {
 LongPressCopyableText(context, song.path)
 }
 InformationKeyValue(context.kordx.t.Size) {
 LongPressCopyableText(context, "${round((song.size / 1024 / 1024).toDouble())} MB")
 }
 InformationKeyValue(context.kordx.t.LastModified) {
 LongPressCopyableText(
 context,
 SimpleDateFormat.getInstance().format(Date(song.dateModified * 1000)),
 )
 }
 },
 onDismissRequest = onDismissRequest,
 )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LongPressCopyableAndTappableText(
 context: ViewContext,
 values: Set<String>,
 onTap: (String) -> Unit,
) {
 val textStyle = LocalTextStyle.current.copy(
 textDecoration = TextDecoration.Underline,
 )

 FlowRow {
 values.forEachIndexed { i, it ->
 Text(
 it,
 style = textStyle,
 modifier = Modifier.pointerInput(Unit) {
 detectTapGestures(
 onLongPress = { _ ->
 ActivityUtils.copyToClipboardAndNotify(
 context.kordx.applicationContext,
 it,
 context.kordx.t.CopiedXToClipboard(it),
 )
 },
 onTap = { _ ->
 onTap(it)
 },
 )
 },
 )
 if (i != values.size - 1) {
 Text(", ")
 }
 }
 }
}
