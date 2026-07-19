package com.android.rockages.kordx.services.radio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level regression tests for [RadioQueue.remove] correctness.
 *
 * The queue is hard to instantiate in a JVM test because it needs a [KordX]
 * instance, so we pin the safety-critical code shapes in source text — the
 * same style used by [RadioPlayRecursionTest] and
 * [RadioQueueLoopShufflePersistenceTest].
 */
class RadioQueueRemoveTest {

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
    fun removeSingleIndexUsesSongIdForOriginalQueue() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
        )
        assertTrue(
            source.contains("val songId = currentQueue.getOrNull(index) ?: return"),
            "RadioQueue.remove(index) must read the actual song id from currentQueue"
        )
        assertTrue(
            source.contains("originalQueue.remove(songId)"),
            "RadioQueue.remove(index) must remove by song id from originalQueue, " +
                "so shuffled queues delete the correct underlying song"
        )
        assertFalse(
            source.contains("originalQueue.removeAt(index)"),
            "RadioQueue must not removeAt(index) from originalQueue — " +
                "when shuffled that index points to the wrong song"
        )
    }

    @Test
    fun removeMultipleIndicesDoesNotDeflect() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
        )
        assertFalse(
            source.contains("deflection"),
            "RadioQueue.remove(indices) must not use a deflection counter — " +
                "sorted-descending removals need no compensation"
        )
        assertTrue(
            source.contains("sortedDescending()"),
            "RadioQueue.remove(indices) must sort indices descending before removing"
        )
    }

    @Test
    fun radioPlayUsesSilentRemoveForStaleIds() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
        )
        assertTrue(
            source.contains("queue.removeAtSilently(options.index)"),
            "Radio.play must drop the stale id via removeAtSilently before invoking error-recovery"
        )
    }

    @Test
    fun queueHasSilentRemoveHelper() {
        val source = loadSource(
            "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
        )
        assertTrue(
            source.contains("internal fun removeAtSilently(index: Int)"),
            "RadioQueue must expose an internal removeAtSilently helper for Radio.play stale-id cleanup"
        )
    }
}
