@file:Suppress("CyclomaticComplexMethod", "ReturnCount", "UseCheckOrError")

package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level audit for every dynamic [BroadcastReceiver] registration in
 * `:app`. On Android 13+ (API 33), every [Context.registerReceiver] call must
 * pass either [Context.RECEIVER_EXPORTED] or [Context.RECEIVER_NOT_EXPORTED];
 * the unflagged overload throws [SecurityException] at runtime.
 *
 * This test scans the `:app` source tree for every `registerReceiver(` call and
 * asserts each one is either explicitly flagged, protected by a
 * `BuildConfig.DEBUG` guard, or lives in a legacy branch annotated with
 * `@SuppressLint("UnspecifiedRegisterReceiverFlag")`.
 */
class DynamicReceiverAuditTest {

    private fun appSources(): List<File> {
        val candidates = listOf(
            File("app/src/main/java"),
            File("../app/src/main/java"),
        )
        val root = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException(
                "Could not locate app sources under any of " +
                    candidates.joinToString { it.path }
            )
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    @Test
    fun everyRegisterReceiverCallIsExplicitlyFlaggedOrGuarded() {
        val failures = mutableListOf<String>()

        for (source in appSources()) {
            val text = source.readText()
            val regex = Regex("""\bregisterReceiver\s*\(""")
            for (match in regex.findAll(text)) {
                val callStart = match.range.first
                val lineNumber = text.substring(0, callStart).count { it == '\n' } + 1

                if (!isFlaggedOrGuarded(text, callStart)) {
                    failures.add("${source.name}:$lineNumber")
                }
            }
        }

        assertTrue(
            failures.isEmpty(),
            "Unflagged registerReceiver calls found at: ${failures.joinToString(", ")}. " +
                "Every call must pass RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED, " +
                "be inside a BuildConfig.DEBUG guard, or be in a " +
                "@SuppressLint(\"UnspecifiedRegisterReceiverFlag\") legacy branch.",
        )
    }

    /**
     * Returns true when the `registerReceiver(` call at [callStart] is:
     * - in a `BuildConfig.DEBUG` guarded block, or
     * - passes `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED`, or
     * - is inside a function/branch annotated with the legacy suppression.
     */
    private fun isFlaggedOrGuarded(source: String, callStart: Int): Boolean {
        // 1. Explicit flag argument on the same call.
        val callEnd = findMatchingParen(source, callStart + "registerReceiver".length)
        val callBody = source.substring(callStart, callEnd)
        if (callBody.contains("RECEIVER_EXPORTED") ||
            callBody.contains("RECEIVER_NOT_EXPORTED")
        ) {
            return true
        }

        // 2. BuildConfig.DEBUG guard encloses the call.
        if (enclosedByBuildConfigDebug(source, callStart)) {
            return true
        }

        // 3. Legacy suppression annotation is in scope.
        if (hasLegacySuppression(source, callStart)) {
            return true
        }

        return false
    }

    private fun findMatchingParen(source: String, openIdx: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var i = openIdx
        while (i < source.length) {
            val ch = source[i]
            when {
                escaped -> escaped = false
                ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                inString -> { /* skip */ }
                ch == '(' -> depth++
                ch == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        throw IllegalStateException("Unmatched parenthesis at index $openIdx")
    }

    private fun enclosedByBuildConfigDebug(source: String, callStart: Int): Boolean {
        // Search backwards for the nearest `if (BuildConfig.DEBUG)` and confirm the
        // call is inside its braces.
        val prefix = source.substring(0, callStart)
        val guardIdx = prefix.lastIndexOf("if (BuildConfig.DEBUG)")
        if (guardIdx < 0) return false

        val openBrace = source.indexOf("{", guardIdx)
        if (openBrace < 0 || openBrace > callStart) return false

        var depth = 0
        var inString = false
        var i = openBrace
        while (i < source.length) {
            val ch = source[i]
            when {
                ch == '"' -> inString = !inString
                inString -> { /* skip */ }
                ch == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    val eol = source.indexOf('\n', i)
                    i = if (eol > 0) eol else source.length
                    continue
                }
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return i > callStart
                }
            }
            if (i >= callStart && depth >= 1) return true
            i++
        }
        return false
    }

    private fun hasLegacySuppression(source: String, callStart: Int): Boolean {
        // Look back a few lines for the suppression annotation.
        val prefix = source.substring(0, callStart)
        val searchWindow = prefix.takeLast(500)
        return searchWindow.contains("@SuppressLint(\"UnspecifiedRegisterReceiverFlag\")") ||
            searchWindow.contains("@SuppressLint(\"UnspecifiedRegisterReceiverFlag\",")
    }
}
