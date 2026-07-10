package com.android.rockages.kordx.core.utils

fun Float.toSafeFinite() = if (!isFinite()) 0f else this
