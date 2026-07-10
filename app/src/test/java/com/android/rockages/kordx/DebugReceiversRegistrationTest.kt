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

 // ---- RadioSession.start() — debug receiver registrations gated

 @Test
 fun radioSessionStartGuardsDebugCustomActionReceiver() {
 val src = loadRadioSession()

 // The debugCustomActionReceiver registration should be inside a; `if (BuildConfig.DEBUG) {` block. Search for the registration; pattern, not just the receiver name (which also appears in the; val definition).
 val inDebugBlock = registrationIsDebugGuarded(src, "debugCustomActionReceiver")
 assertTrue(
 inDebugBlock,
 "debugCustomActionReceiver registration must be inside if (BuildConfig.DEBUG) { }"
 )
 }

 @Test
 fun radioSessionStartGuardsDebugSearchReceiver() {
 val src = loadRadioSession()
 val inDebugBlock = registrationIsDebugGuarded(src, "debugSearchReceiver")
 assertTrue(
 inDebugBlock,
 "debugSearchReceiver registration must be inside if (BuildConfig.DEBUG) { }"
 )
 }

 @Test
 fun radioSessionStartGuardsDebugRecentPlayReceiver() {
 val src = loadRadioSession()
 val inDebugBlock = registrationIsDebugGuarded(src, "debugRecentPlayReceiver")
 assertTrue(
 inDebugBlock,
 "debugRecentPlayReceiver registration must be inside if (BuildConfig.DEBUG) { }"
 )
 }

 @Test
 fun radioSessionStartGuardsDebugPlaybackErrorReceiver() {
 val src = loadRadioSession()
 val inDebugBlock = registrationIsDebugGuarded(src, "debugPlaybackErrorReceiver")
 assertTrue(
 inDebugBlock,
 "debugPlaybackErrorReceiver registration must be inside if (BuildConfig.DEBUG) { }"
 )
 }

 @Test
 fun radioSessionStartDoesNotGuardProductionReceiver() {
 val src = loadRadioSession()

 // Extract the `fun start()` body. The FIRST `registerReceiver(`; call in start() is the production `receiver`. It should NOT be; preceded by `if (BuildConfig.DEBUG)`.
 val startIdx = src.indexOf("fun start()")
 assertTrue(startIdx > 0, "start() must exist")

 // Find the next `fun ` after start() to bound the method.
 val nextFun = src.indexOf("fun ", startIdx + 1)
 val methodBody = if (nextFun > startIdx) src.substring(startIdx, nextFun) else src.substring(startIdx)

 // First registerReceiver call in start() is the production receiver.
 val firstRegIdx = methodBody.indexOf("registerReceiver(")
 assertTrue(firstRegIdx > 0, "registerReceiver must exist in start()")


 // There should be no `if (BuildConfig.DEBUG)` between the method start; and the first registerReceiver call.
 val beforeFirstReg = methodBody.substring(0, firstRegIdx)
 assertFalse(
 beforeFirstReg.contains("if (BuildConfig.DEBUG)"),
 "Production 'receiver' registration must NOT be inside if (BuildConfig.DEBUG) in start()"
 )
 }

 // ---- RadioSession.destroy() — debug receiver unregistrations gated

 @Test
 fun radioSessionDestroyGuardsDebugUnregistrations() {
 val src = loadRadioSession()

 // destroy() should have `if (BuildConfig.DEBUG) {` before the; debug receiver unregister blocks.
 val destroyStart = src.indexOf("fun destroy()")
 assertTrue(destroyStart > 0, "destroy() must exist in RadioSession.kt")

 val afterDestroy = src.substring(destroyStart)


 // debugCustomActionReceiver unregistration should be after; if (BuildConfig.DEBUG) within destroy()
 val debugGuardIdx = afterDestroy.indexOf("if (BuildConfig.DEBUG)")
 assertTrue(
 debugGuardIdx > 0,
 "destroy() must contain if (BuildConfig.DEBUG) guard for debug unregistrations"
 )

 val debugUnregisterIdx = afterDestroy.indexOf(
 "kordx.applicationContext.unregisterReceiver(debugCustomActionReceiver"
 )
 assertTrue(
 debugUnregisterIdx > debugGuardIdx,
 "debugCustomActionReceiver unregister must appear after if (BuildConfig.DEBUG)"
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

 // The BroadcastReceiver.onReceive{} bodies should NOT be wrapped; in BuildConfig.DEBUG — they only fire when the receiver is; registered (which IS gated). Gating the handler body as well; would be redundant and confusing.
 val src = loadRadioSession()

 // Find the debugCustomActionReceiver's onReceive body.
 val receiverStart = src.indexOf("private val debugCustomActionReceiver")
 assertTrue(receiverStart > 0, "debugCustomActionReceiver must exist")


 // The handler body should not contain the word "BuildConfig"; (if someone incorrectly added a guard inside the handler).
 val handlerStart = src.indexOf(
 "override fun onReceive",
 receiverStart,
 )
 assertTrue(handlerStart > 0, "onReceive must exist in debugCustomActionReceiver")

 // Grab up to the closing brace of onReceive.
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

 // We reached the closing brace of the ifblock.; The needle was inside.
 return true
 }
 }
 }
 if (i >= needleIdx && depth >= 1) {

 // We've reached or passed the needle position while still; inside the block — theeedle is guarded.
 return true
 }
 i++
 }
 return false
 }
}
