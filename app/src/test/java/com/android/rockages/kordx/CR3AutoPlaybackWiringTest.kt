package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level regression tests for CR3 — Android Auto playback wiring.
 *
 * These tests pin the safety-critical implementation choices in source
 * text because the full Android Auto / Media3 integration requires a
 * real Android runtime (AAOS/DHU). The structural assertions here are
 * the cheapest way to ensure the wiring does not regress.
 */
class CR3AutoPlaybackWiringTest {

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

    @Test
    fun kordxApplicationExistsAndExtendsApplication() {
        val source = loadSource("app/src/main/java/com/android/rockages/kordx/KordXApplication.kt")
        assertTrue(
            source.contains("class KordXApplication : Application()"),
            "KordXApplication must extend android.app.Application"
        )
        assertTrue(
            source.contains("KordX(this)") && source.contains("emitReady()"),
            "KordXApplication.onCreate must build the KordX graph and emit ready"
        )
    }

    @Test
    fun manifestDeclaresKordxApplication() {
        val manifest = loadSource("app/src/main/AndroidManifest.xml")
        assertTrue(
            manifest.contains("android:name=\".KordXApplication\""),
            "AndroidManifest must declare KordXApplication as the Application class"
        )
    }

    @Test
    fun mainActivityUsesKordXInstance() {
        val source = loadSource("app/src/main/java/com/android/rockages/kordx/MainActivity.kt")
        assertTrue(
            source.contains("val kordx = KordX.instance"),
            "MainActivity must use KordX.instance instead of the Activity ViewModelStore"
        )
        assertFalse(
            source.contains("val kordx: KordX by viewModels()"),
            "MainActivity must not create a second KordX via viewModels()"
        )
        assertFalse(
            source.contains("private var gKordX: KordX? = null"),
            "MainActivity must not keep its own KordX reference"
        )
    }

    private fun extractMethodBody(source: String, methodName: String): String {
        val methodStart = source.indexOf("fun $methodName(")
        if (methodStart == -1) return ""
        val bodyStart = source.indexOf("{", methodStart)
        if (bodyStart == -1) return ""
        var braceDepth = 0
        for (i in bodyStart until source.length) {
            when (source[i]) {
                '{' -> braceDepth++
                '}' -> {
                    braceDepth--
                    if (braceDepth == 0) {
                        return source.substring(bodyStart, i + 1)
                    }
                }
            }
        }
        return source.substring(bodyStart)
    }

    @Test
    fun kordxOnClearedDoesNotDestroyProcessOwnedInstance() {
        val source = loadSource("app/src/main/java/com/android/rockages/kordx/KordX.kt")
        val onClearedBody = extractMethodBody(source, "onCleared")
        assertFalse(
            onClearedBody.contains("instance = null"),
            "KordX.onCleared must not clear the process-owned instance"
        )
        assertFalse(
            onClearedBody.contains("emitDestroy()"),
            "KordX.onCleared must not emit destroy — the Application owns the graph lifetime"
        )
    }

    @Test
    fun mediaLibraryServiceCreatePlayerUsesRealRadio() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/KordXMediaLibraryService.kt"
        )
        assertTrue(
            source.contains("radio = app.radio"),
            "KordXMediaLibraryService.createPlayer must wire the player to the real Radio"
        )
        assertTrue(
            source.contains("val app = KordX.instance"),
            "KordXMediaLibraryService.createPlayer must resolve KordX.instance"
        )
        val noOpOccurrences = source.split("radio = NoOpRadioAdapterTarget()").size - 1
        assertTrue(
            noOpOccurrences == 1,
            "NoOpRadioAdapterTarget must appear exactly once as the defensive fallback"
        )
    }

    @Test
    fun mediaLibraryServiceShuffleAllUsesPlayQueueApi() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/KordXMediaLibraryService.kt"
        )
        assertTrue(
            source.contains("app.radio.shorty.playQueue("),
            "handleShuffleAll must use RadioShorty.playQueue"
        )
        assertTrue(
            source.contains("Radio.PlayOptions(autostart = false)"),
            "handleShuffleAll must pass autostart = false"
        )
        assertTrue(
            source.contains("shuffle = true"),
            "handleShuffleAll must pass shuffle = true"
        )
        assertFalse(
            source.contains("app.radio.queue.originalQueue.addAll("),
            "handleShuffleAll must not write to originalQueue directly"
        )
        assertFalse(
            source.contains("app.radio.queue.currentQueue.addAll("),
            "handleShuffleAll must not write to currentQueue directly"
        )
    }

    @Test
    fun radioForwardingPlayerUsesListBasedEventFlags() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/RadioForwardingPlayer.kt"
        )
        assertTrue(
            source.contains("flagSetBuilder.addAll(*events.toIntArray())"),
            "RadioForwardingPlayer must use FlagSet.Builder.addAll with the event list"
        )
        assertFalse(
            source.contains("var remaining = events") && source.contains("remaining and -remaining"),
            "RadioForwardingPlayer must not decompose OR-ed bits manually"
        )
    }
}
