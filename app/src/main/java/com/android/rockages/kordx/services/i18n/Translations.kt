package com.android.rockages.kordx.services.i18n

import com.android.rockages.kordx.KordX

class Translations(private val kordx: KordX) : _Translations() {
 val defaultLocaleCode = "en"

 fun supports(locale: String) = localeCodes.contains(locale)

 fun parse(locale: String) = kordx.applicationContext.assets.open("i18n/${locale}.json").use {
 Translation.fromInputStream(it)
 }
}
