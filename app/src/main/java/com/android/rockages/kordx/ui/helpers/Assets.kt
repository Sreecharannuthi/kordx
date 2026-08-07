package com.android.rockages.kordx.ui.helpers

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import coil.request.ImageRequest
import com.android.rockages.kordx.R
import com.android.rockages.kordx.KordX
import com.android.rockages.kordx.ui.theme.isLight
import com.android.rockages.kordx.ui.theme.toColorSchemeMode

object Assets {
 val placeholderDarkId = R.raw.placeholder_dark
 val placeholderLightId = R.raw.placeholder_light

 private fun getPlaceholderId(isLight: Boolean = false) = when {
 isLight -> placeholderLightId
 else -> placeholderDarkId
 }

 fun getPlaceholderId(kordx: KordX) = getPlaceholderId(
 isLight = kordx.settings.themeMode.value.toColorSchemeMode(kordx)
 .isLight(),
 )

 fun getPlaceholderUri(kordx: KordX) = buildUriOfResource(
 kordx.applicationContext.resources,
 getPlaceholderId(kordx),
 )

 fun createPlaceholderImageRequest(kordx: KordX) =
 ImageRequest.Builder(kordx.applicationContext)
 .data(getPlaceholderUri(kordx))

 private fun buildUriOfResource(resources: Resources, resourceId: Int): Uri {
 return Uri.Builder()
 .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
 .authority(resources.getResourcePackageName(resourceId))
 .appendPath(resources.getResourceTypeName(resourceId))
 .appendPath(resources.getResourceEntryName(resourceId))
 .build()
 }
}
