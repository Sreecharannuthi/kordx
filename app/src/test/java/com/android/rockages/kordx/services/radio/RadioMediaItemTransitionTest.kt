package com.android.rockages.kordx.services.radio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression guard for the GP4 startup ANR:
 *
 * `onMediaItemTransition` dispatched `Events.Queue.Modified`, which
 * `watchQueueUpdates` answered with `syncPlayerPlaylist()` →
 * `ExoPlayer.setMediaItems(...)` → `onMediaItemTransition` →
 * `Queue.Modified` → … — an infinite loop on the main thread that
 * surfaced as "KordX isn't responding" seconds after launch.
 *
 * The transition listener must only dispatch `Queue.IndexChanged`
 * (the queue contents did not change; only the index did), and the
 * playlist re-sync must run under the `isApplyingQueueChange` guard
 * so a re-entrant transition can never restart the loop.
 */
class RadioMediaItemTransitionTest {

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
        error("Could not locate source file $relativePath in ${System.getProperty("user.dir")}")
    }

    private fun loadRadio(): String = loadSource(
        "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
    )

    /** Extract the `onMediaItemTransition` override body. */
    private fun transitionBody(source: String): String {
        val start = source.indexOf("override fun onMediaItemTransition")
        assertTrue(start >= 0, "onMediaItemTransition not found in Radio.kt")
        val body = source.substring(start)
        // End at the next override declaration.
        val nextOverride = body.indexOf("\n        override fun ", startIndex = 1)
        return if (nextOverride >= 0) body.substring(0, nextOverride) else body
    }

    @Test
    fun transitionListenerDoesNotDispatchQueueModified() {
        val body = transitionBody(loadRadio())
        assertFalse(
            body.contains("Events.Queue.Modified"),
            "onMediaItemTransition must NOT dispatch Events.Queue.Modified — " +
                "watchQueueUpdates reacts to it with syncPlayerPlaylist(), whose " +
                "setMediaItems re-fires this listener (infinite loop → ANR). " +
                "Only the index changed; dispatch Events.Queue.IndexChanged only."
        )
    }

    @Test
    fun transitionListenerDispatchesIndexChanged() {
        val body = transitionBody(loadRadio())
        assertTrue(
            body.contains("Events.Queue.IndexChanged"),
            "onMediaItemTransition should dispatch Events.Queue.IndexChanged so the UI " +
                "follows the ExoPlayer-driven track change"
        )
    }

    @Test
    fun syncPlayerPlaylistRunsUnderApplyingQueueChangeGuard() {
        val source = loadRadio()
        val start = source.indexOf("private fun syncPlayerPlaylist()")
        assertTrue(start >= 0, "syncPlayerPlaylist() not found in Radio.kt")
        val body = source.substring(start)
        val nextFun = body.indexOf("\n    private fun ", startIndex = 1)
        val fn = if (nextFun >= 0) body.substring(0, nextFun) else body
        assertTrue(
            fn.contains("isApplyingQueueChange = true"),
            "syncPlayerPlaylist() must set isApplyingQueueChange = true around " +
                "syncPlaylist() so the transition fired by setMediaItems cannot " +
                "re-enter the queue→playlist sync"
        )
        assertTrue(
            fn.contains("isApplyingQueueChange = false"),
            "syncPlayerPlaylist() must reset isApplyingQueueChange (finally block) after syncPlaylist()"
        )
    }
}
