package com.android.rockages.kordx.core.utils

fun String.withCase(sensitive: Boolean) = if (!sensitive) lowercase() else this
