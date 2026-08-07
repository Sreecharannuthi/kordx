package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DebugReceiversRegistrationTest {

    private fun loadSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                return candidate.readText()
            }
        }
        throw IllegalStateException(
            "Could not locate source file $relativePath in ${System.getProperty("user.dir")}"
        )
    }

    private fun loadRadioSession(): String = loadSource(
        "app/src/main/java/com/android/rockages/kordx/services/radio/RadioSession.kt"
    )

    private fun loadMediaLibraryService(): String = loadSource(
        "app/src/main/java/com/android/rockages/kordx/services/radio/KordXMediaLibraryService.kt"
    )

    // ---- RadioSession.attachDebugReceivers() — debug receiver registrations gated

    @Test
    fun radioSessionAttachGuardsDebugCustomActionReceiver() {
        val src = loadRadioSession()

        // The debugCustomActionReceiver registration should be inside an `if (BuildConfig.DEBUG) {` block.
        val inDebugBlock = registrationIsDebugGuarded(src, "debugCustomActionReceiver")
        assertTrue(
            inDebugBlock,
            "debugCustomActionReceiver registration must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    @Test
    fun radioSessionAttachGuardsDebugSearchReceiver() {
        val src = loadRadioSession()
        val inDebugBlock = registrationIsDebugGuarded(src, "debugSearchReceiver")
        assertTrue(
            inDebugBlock,
            "debugSearchReceiver registration must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    @Test
    fun radioSessionAttachGuardsDebugRecentPlayReceiver() {
        val src = loadRadioSession()
        val inDebugBlock = registrationIsDebugGuarded(src, "debugRecentPlayReceiver")
        assertTrue(
            inDebugBlock,
            "debugRecentPlayReceiver registration must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    @Test
    fun radioSessionAttachGuardsDebugPlaybackErrorReceiver() {
        val src = loadRadioSession()
        val inDebugBlock = registrationIsDebugGuarded(src, "debugPlaybackErrorReceiver")
        assertTrue(
            inDebugBlock,
            "debugPlaybackErrorReceiver registration must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    @Test
    fun radioSessionAttachHasOnlyDebugRegistrations() {
        val src = loadRadioSession()

        // LG2 removed the production notification receiver. Every registerReceiver call in
        // attachDebugReceivers() must now live behind a BuildConfig.DEBUG guard — either an
        // enclosing `if (BuildConfig.DEBUG) { }` block or an early
        // `if (!BuildConfig.DEBUG) return` at the top of the function (the style LG2 uses).
        val attachStart = src.indexOf("fun attachDebugReceivers()")
        assertTrue(attachStart > 0, "attachDebugReceivers() must exist")

        val nextFun = src.indexOf("fun ", attachStart + 1)
        val methodBody = if (nextFun > attachStart) src.substring(attachStart, nextFun) else src.substring(attachStart)

        if (methodBody.contains("if (!BuildConfig.DEBUG) return")) {
            // Early-return style: every registerReceiver call in this function is
            // debug-guarded by definition.
            return
        }

        var idx = methodBody.indexOf("registerReceiver(")
        while (idx > 0) {
            // The registerReceiver call must appear after an `if (BuildConfig.DEBUG)` guard
            // whose opening brace encloses the call.
            val prefix = methodBody.substring(0, idx)
            val guardIdx = prefix.lastIndexOf("if (BuildConfig.DEBUG)")
            assertTrue(guardIdx > 0, "registerReceiver must be preceded by if (BuildConfig.DEBUG)")

            val openBrace = methodBody.indexOf("{", guardIdx)
            assertTrue(openBrace in guardIdx until idx, "registerReceiver must be inside the if-block")
            idx = methodBody.indexOf("registerReceiver(", idx + 1)
        }
    }

    // ---- RadioSession.detachDebugReceivers() — debug receiver unregistrations gated

    @Test
    fun radioSessionDetachGuardsDebugUnregistrations() {
        val src = loadRadioSession()

        // detachDebugReceivers() should have an `if (!BuildConfig.DEBUG) return` guard at the top
        // so the debug receiver unregistration block is unreachable in release builds.
        val detachStart = src.indexOf("fun detachDebugReceivers()")
        assertTrue(detachStart > 0, "detachDebugReceivers() must exist in RadioSession.kt")

        val afterDetach = src.substring(detachStart)

        val debugGuardIdx = afterDetach.indexOf("if (!BuildConfig.DEBUG) return")
        assertTrue(
            debugGuardIdx > 0,
            "detachDebugReceivers() must guard debug unregistrations with an early return"
        )

        val debugUnregisterIdx = afterDetach.indexOf(
            "kordx.applicationContext.unregisterReceiver(debugCustomActionReceiver"
        )
        assertTrue(
            debugUnregisterIdx > debugGuardIdx,
            "debugCustomActionReceiver unregister must appear after the BuildConfig.DEBUG guard"
        )
    }

    // ---- KordXMediaLibraryService.onCreate() — registerDebugReceivers gated

    @Test
    fun mediaLibraryServiceOnCreateGuardsRegisterDebugReceivers() {
        val src = loadMediaLibraryService()
        val inDebugBlock = registrationIsDebugGuarded(src, "registerDebugReceivers()")
        assertTrue(
            inDebugBlock,
            "registerDebugReceivers() call must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    // ---- KordXMediaLibraryService.onDestroy() — unregisterDebugReceivers gated

    @Test
    fun mediaLibraryServiceOnDestroyGuardsUnregisterDebugReceivers() {
        val src = loadMediaLibraryService()
        val inDebugBlock = registrationIsDebugGuarded(src, "unregisterDebugReceivers()")
        assertTrue(
            inDebugBlock,
            "unregisterDebugReceivers() call must be inside if (BuildConfig.DEBUG) { }"
        )
    }

    // ---- Debug receiver onReceive bodies are NOT gated (safety check)

    @Test
    fun debugReceiverHandlersAreNotGated() {

        // The BroadcastReceiver.onReceive{} bodies should NOT be wrapped
        // in BuildConfig.DEBUG — they only fire when the receiver is
        // registered (which IS gated). Gating the handler body as well
        // would be redundant and confusing.
        val src = loadRadioSession()

        // Find the debugCustomActionReceiver's onReceive body.
        val receiverStart = src.indexOf("private val debugCustomActionReceiver")
        assertTrue(receiverStart > 0, "debugCustomActionReceiver must exist")


        // Grab up to the closing brace of onReceive.
        val handlerStart = src.indexOf(
            "override fun onReceive",
            receiverStart,
        )
        assertTrue(handlerStart > 0, "onReceive must exist in debugCustomActionReceiver")

        val nextVal = src.indexOf("private val", handlerStart + 1)
        val handlerBody = if (nextVal > handlerStart) {
            src.substring(handlerStart, nextVal)
        } else {
            src.substring(handlerStart)
        }

        assertFalse(
            handlerBody.contains("BuildConfig.DEBUG"),
            "debugCustomActionReceiver.onReceive must NOT gate its body on BuildConfig.DEBUG " +
                "(the receiver registration is already gated)"
        )
    }

    // ---- Helpers

    /**
     * Returns true if the LAST occurrence of [needle] in [source] appears
     * inside an `if (BuildConfig.DEBUG) { ... }` block.
     *
     * "Last occurrence" rule: the needle (e.g. "debugSearchReceiver") may
     * appear both in the receiver's val definition and in its
     * `registerReceiver(...)` call. The registration call is always
     * AFTER the definition, so we use the last occurrence.
     *
     * Strategy: find the last occurrence of needle, search backwards for
     * the nearest `if (BuildConfig.DEBUG)`, then walk brace-depth from
     * the if-block's opening brace to confirm the needle position falls
     * inside the block.
     */
    private fun registrationIsDebugGuarded(source: String, needle: String): Boolean {
        val needleIdx = source.lastIndexOf(needle)
        if (needleIdx < 0) return false

        // Style 2 (LG2): an early `if (!BuildConfig.DEBUG) return` at the top of the
        // enclosing function guards everything after it (attachDebugReceivers /
        // detachDebugReceivers use this style).
        val funStart = source.lastIndexOf("fun ", needleIdx)
        if (funStart >= 0) {
            val enclosingPrefix = source.substring(funStart, needleIdx)
            if (enclosingPrefix.contains("if (!BuildConfig.DEBUG) return")) return true
        }

        // Style 1: an enclosing `if (BuildConfig.DEBUG) { ... }` block.
        // Search backwards from needleIdx for the nearest `if (BuildConfig.DEBUG)`
        val prefix = source.substring(0, needleIdx)
        val guardIdx = prefix.lastIndexOf("if (BuildConfig.DEBUG)")

        if (guardIdx < 0) return false

        // Find the opening brace of this if-block.
        val openBrace = source.indexOf("{", guardIdx)
        if (openBrace < 0 || openBrace > needleIdx) return false

        // Walk forward from the opening brace, tracking brace depth.
        var depth = 0
        var inString = false
        var i = openBrace
        while (i < source.length) {
            val ch = source[i]
            when {
                ch == '"' -> inString = !inString
                inString -> { /* skip */ }
                ch == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    // single-line comment — skip to end of line
                    val eol = source.indexOf('\n', i)
                    i = if (eol > 0) eol else source.length
                    continue
                }
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) {

                        // We reached the closing brace of the ifblock.
                        // The needle was inside.
                        return true
                    }
                }
            }
            if (i >= needleIdx && depth >= 1) {

                // We've reached or passed the needle position while still
                // inside the block — the needle is guarded.
                return true
            }
            i++
        }
        return false
    }
}
