package com.android.rockages.kordx.services.i18n

import androidx.core.os.LocaleListCompat
import com.android.rockages.kordx.KordX

class Translator(private val kordx: KordX) {
 val translations = Translations(kordx)

 suspend fun onChange(fn: (Translation) -> Unit) {
 kordx.settings.language.flow.collect {
 fn(getCurrentTranslation())
 }
 }

 fun getCurrentTranslation() = kordx.settings.language.value
 ?.let { translations.parse(it) }
 ?: getDefaultTranslation()

 fun getDefaultTranslation(): Translation {
 val localeCode = getDefaultLocaleCode()
 return translations.parse(localeCode)
 }

 fun getLocaleDisplayName(localeCode: String) =
 translations.localeDisplayNames[localeCode]

 fun getLocaleNativeName(localeCode: String) =
 translations.localeNativeNames[localeCode]

 fun getDefaultLocaleDisplayName() = getLocaleDisplayName(getDefaultLocaleCode())!!
 fun getDefaultLocaleNativeName() = getLocaleNativeName(getDefaultLocaleCode())!!
 fun getDefaultLocaleCode() = LocaleListCompat.getDefault()[0]?.language
 ?.takeIf { translations.supports(it) }
 ?: translations.defaultLocaleCode
}
