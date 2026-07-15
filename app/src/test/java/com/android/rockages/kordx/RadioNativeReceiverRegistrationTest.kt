package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression test for the API 33+ `Context.registerReceiver` flag.
 *
 * **Background.** On API 33+ (Tiramisu), `Context.registerReceiver` requires
 * an explicit `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flag — the
 * unflagged overload throws `SecurityException` at runtime. The throw
 * propagates out of the `Radio` constructor → the `KordX` ViewModel
 * constructor → the framework's `ViewModelProvider` factory, surfacing as
 * "Cannot create an instance of class com.android.rockages.kordx.KordX"
 * (and the red "Something went horribly wrong!" crash screen at app start).
 *
 * `RadioSession` and `KordXMediaLibraryService` were already gated in the
 * prior "Debug receiver gating" slice. This test pins the contract for the
 * **production** headphone / audio-becoming-noisy receiver
 * (`RadioNativeReceiver`), which was missed in that rollout.
 *
 * Companion to [`DebugReceiversRegistrationTest`], which covers the
 * `BuildConfig.DEBUG` gating of the debug receivers.
 */
class RadioNativeReceiverRegistrationTest {

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

    private fun loadRadioNativeReceiver(): String = loadSource(
        "app/src/main/java/com/android/rockages/kordx/services/radio/RadioNativeReceiver.kt"
    )

    /**
     * The fix exists: the `start()` body must branch on
     * `Build.VERSION.SDK_INT >= TIRAMISU`. This is the structural check — the
     * other tests verify each branch has the right flag.
     */
    @Test
    fun startBranchesOnTiramisuSdkInt() {
        val src = loadRadioNativeReceiver()
        assertTrue(
            src.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU"),
            "RadioNativeReceiver.start() must branch on Build.VERSION_CODES.TIRAMISU " +
                "to gate the API 33+ registerReceiver flag requirement",
        )
    }

    /**
     * The API 33+ branch must call `registerReceiver` with
     * `RECEIVER_NOT_EXPORTED`. The two actions (`ACTION_AUDIO_BECOMING_NOISY`,
     * `ACTION_HEADSET_PLUG`) are protected system broadcasts — no external app
     * sends them, so `NOT_EXPORTED` is the more restrictive / correct choice.
     */
    @Test
    fun api33BranchUsesReceiverNotExported() {
        val src = loadRadioNativeReceiver()
        val api33Branch = extractApi33Branch(src)
        assertTrue(
            api33Branch.contains("Context.RECEIVER_NOT_EXPORTED"),
            "The API 33+ branch in RadioNativeReceiver.start() must pass " +
                "Context.RECEIVER_NOT_EXPORTED. The two actions " +
                "(ACTION_AUDIO_BECOMING_NOISY, ACTION_HEADSET_PLUG) are protected " +
                "system broadcasts; no external app sends them, so NOT_EXPORTED is the right flag.",
        )
    }

    /**
     * The API-<33 fallback must use the unflagged `registerReceiver` overload
     * (gated by `@SuppressLint("UnspecifiedRegisterReceiverFlag")` to silence
     * the lint warning). This matches the pattern used in `RadioSession` and
     * `KordXMediaLibraryService` for their legacy-API branches.
     */
    @Test
    fun legacyBranchSuppressesUnspecifiedRegisterReceiverFlag() {
        val src = loadRadioNativeReceiver()
        val legacyBranch = extractLegacyBranch(src)
        assertTrue(
            legacyBranch.contains("@SuppressLint(\"UnspecifiedRegisterReceiverFlag\")"),
            "The API-<33 branch in RadioNativeReceiver.start() must annotate the " +
                "unflagged registerReceiver with @SuppressLint(\"UnspecifiedRegisterReceiverFlag\")",
        )
    }

    /**
     * The API 33+ branch must register for the two system broadcasts the
     * receiver is supposed to handle. (Sanity check that the branch is actually
     * doing the work, not just a stub.)
     */
    @Test
    fun api33BranchRegistersBothSystemBroadcasts() {
        val src = loadRadioNativeReceiver()
        val api33Branch = extractApi33Branch(src)
        assertTrue(
            api33Branch.contains("AudioManager.ACTION_AUDIO_BECOMING_NOISY"),
            "API 33+ branch must subscribe to AudioManager.ACTION_AUDIO_BECOMING_NOISY",
        )
        assertTrue(
            api33Branch.contains("Intent.ACTION_HEADSET_PLUG"),
            "API 33+ branch must subscribe to Intent.ACTION_HEADSET_PLUG",
        )
    }

    /**
     * Belt-and-suspenders: the API 33+ branch must contain exactly one
     * `registerReceiver(` call, and that call must pass
     * `Context.RECEIVER_NOT_EXPORTED` as its flag argument. The legacy
     * `< TIRAMISU` branch is allowed to have its own unflagged call
     * (gated by `@SuppressLint("UnspecifiedRegisterReceiverFlag")`) because
     * pre-Tiramisu doesn't require the flag — but the API 33+ branch must
     * never have an unflagged call.
     */
    @Test
    fun api33BranchHasExactlyOneFlaggedRegisterReceiverCall() {
        val src = loadRadioNativeReceiver()
        val api33Branch = extractApi33Branch(src)

        val matches = Regex("""registerReceiver\s*\(""").findAll(api33Branch).toList()
        assertTrue(
            matches.size == 1,
            "API 33+ branch in RadioNativeReceiver.start() must have exactly one " +
                "registerReceiver call (found ${matches.size})",
        )

        assertTrue(
            api33Branch.contains("Context.RECEIVER_NOT_EXPORTED"),
            "The single registerReceiver call in the API 33+ branch must pass " +
                "Context.RECEIVER_NOT_EXPORTED as its flag argument",
        )
    }

    // ---- Helpers

    /**
     * Extracts the body of the `if (Build.VERSION.SDK_INT >= TIRAMISU) { ... }`
     * branch of `start()`. Returns the substring from the opening `{` to the
     * matching closing `}`. Stops at the `} else {` boundary so the legacy
     * branch is not included.
     */
    private fun extractApi33Branch(source: String): String {
        val guardIdx = source.indexOf("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU")
        assertTrue(guardIdx > 0, "TIRAMISU guard must exist in start()")
        val openBrace = source.indexOf("{", guardIdx)
        assertTrue(openBrace > 0, "TIRAMISU guard must have an opening brace")
        // matchBrace on the IF's opening brace stops at the `}` before `else`
        // (the IF's own closing brace, depth returns to 0 there).
        val closeBrace = matchBrace(source, openBrace)
        return source.substring(openBrace, closeBrace + 1)
    }

    /**
     * Extracts the body of the `else { ... }` branch of `start()`. Returns the
     * substring from the opening `{` to the matching closing `}`.
     */
    private fun extractLegacyBranch(source: String): String {
        val api33BranchStart = source.indexOf("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU")
        val elseIdx = source.indexOf("} else {", startIndex = api33BranchStart)
        assertTrue(elseIdx > 0, "legacy `else {` branch must exist after the TIRAMISU guard")
        val openBrace = source.indexOf("{", elseIdx)
        assertTrue(openBrace > 0, "legacy branch must have an opening brace")
        val closeBrace = matchBrace(source, openBrace)
        return source.substring(openBrace, closeBrace + 1)
    }

    /**
     * Given the index of an opening `{`, returns the index of the matching
     * closing `}`. Skips braces inside string literals and line comments.
     */
    private fun matchBrace(source: String, openIdx: Int): Int {
        var depth = 0
        var inString = false
        var i = openIdx
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
                    if (depth == 0) return i
                }
            }
            i++
        }
        throw IllegalStateException("Unmatched brace at index $openIdx")
    }
}
