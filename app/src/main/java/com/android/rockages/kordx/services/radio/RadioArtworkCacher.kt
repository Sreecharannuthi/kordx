package com.android.rockages.kordx.services.radio
import com.android.rockages.kordx.services.groove.createArtworkImageRequest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.core.groove.Song
import com.android.rockages.kordx.ui.helpers.Assets

class RadioArtworkCacher(val kordx: KordX) {
 private var default: Bitmap? = null
 private var cached = mutableMapOf<String, Bitmap>()
 private val cacheLimit = 3

 suspend fun getArtwork(song: Song): Bitmap {
 return cached[song.id] ?: kotlin.run {
 val result = kordx.applicationContext.imageLoader
 .execute(song.createArtworkImageRequest(kordx).build())
 val bitmap = result.drawable?.toBitmap() ?: getDefaultArtwork()
 updateCache(song.id, bitmap)
 bitmap
 }
 }

 private fun getDefaultArtwork(): Bitmap {
 return default ?: run {
 val bitmap = BitmapFactory.decodeResource(
 kordx.applicationContext.resources,
 Assets.placeholderDarkId,
 )
 default = bitmap
 bitmap
 }
 }

 private fun updateCache(key: String, value: Bitmap) {
 if (!cached.containsKey(key) && cached.size >= cacheLimit) {
 cached.remove(cached.keys.first())
 }
 cached[key] = value
 }
}
