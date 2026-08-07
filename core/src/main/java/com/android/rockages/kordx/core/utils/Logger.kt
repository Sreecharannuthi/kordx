package com.android.rockages.kordx.core.utils

import android.util.Log

object Logger {
 private const val TAG = "KordXLogger"

 fun warn(mod: String, text: String) = Log.w(TAG, "$mod: $text")
 fun warn(mod: String, text: String, throwable: Throwable) =
 warn(mod, joinTextThrowable(text, throwable))

 fun error(mod: String, text: String) = Log.e(TAG, "$mod: $text")
 fun error(mod: String, text: String, throwable: Throwable) =
 error(mod, joinTextThrowable(text, throwable))

 fun joinTextThrowable(text: String, throwable: Throwable) = StringBuilder().apply {
 append(text)
 append("\nError: ${throwable.message}")
 append("\nStack trace: ${throwable.stackTraceToString()}")
 }.toString()
}
