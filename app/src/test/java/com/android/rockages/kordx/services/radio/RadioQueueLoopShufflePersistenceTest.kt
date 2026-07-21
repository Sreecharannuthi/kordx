package com.android.rockages.kordx.services.radio

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RadioQueueLoopShufflePersistenceTest {

 private fun loadSource(relativePath: String): String {

 // Resolve relative to the project root. The gradle test; classpath runs from the project root (./gradlew :app:test).
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

 // ---- Fix 4: Settings entries exist.

 @Test
 fun settingsLastLoopModeIsDeclared() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/Settings.kt"
 )
 assertTrue(
 source.contains("val lastLoopMode = EnumEntry("),
 "Settings.kt should declare `lastLoopMode` as an EnumEntry"
 )
 assertTrue(
 source.contains("enumEntries<RadioQueue.LoopMode>()"),
 "Settings.lastLoopMode should be typed as RadioQueue.LoopMode"
 )
 assertTrue(
 source.contains("RadioQueue.LoopMode.None"),
 "Settings.lastLoopMode default should be RadioQueue.LoopMode.None"
 )
 }

 @Test
 fun settingsLastShuffleModeIsDeclared() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/Settings.kt"
 )
 assertTrue(
 source.contains("val lastShuffleMode = BooleanEntry("),
 "Settings.kt should declare `lastShuffleMode` as a BooleanEntry"
 )
 assertTrue(
 source.contains("\"last_shuffle_mode\", false"),
 "Settings.lastShuffleMode should default to false"
 )
 }

 // ---- Fix 4: RadioQueue writes through to settings.

 @Test
 fun radioQueueSetLoopModePersistsToSettings() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
 )
 // The new `setLoopMode` body writes through to settings.
 assertTrue(
 source.contains("kordx.settings.lastLoopMode.setValue("),
 "RadioQueue.setLoopMode should persist via kordx.settings.lastLoopMode.setValue"
 )
 }

 @Test
 fun radioQueueSetShuffleModePersistsToSettings() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
 )
 assertTrue(
 source.contains("kordx.settings.lastShuffleMode.setValue("),
 "RadioQueue.setShuffleMode should persist via kordx.settings.lastShuffleMode.setValue"
 )
 }

 // ---- Fix 4: Radio restores on app start.

 @Test
 fun radioReadyRestoresLoopAndShuffle() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
 )
 assertTrue(
 source.contains("queue.setLoopMode(kordx.settings.lastLoopMode.value)"),
 "Radio.ready() should restore lastLoopMode on app start"
 )
 assertTrue(
 source.contains("queue.setShuffleMode(kordx.settings.lastShuffleMode.value)"),
 "Radio.ready() should restore lastShuffleMode on app start"
 )
 }

 // ---- Fix 5: clearQueue() public API.

 @Test
 fun radioHasClearQueueMethod() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
 )
 assertTrue(
 source.contains("fun clearQueue()"),
 "Radio should expose a `fun clearQueue()` public method (Fix for Issue #843)"
 )
 assertTrue(
 source.contains("queue.clear()"),
 "Radio.clearQueue() should delegate to queue.clear()"
 )
 }

 @Test
 fun radioQueueHasClearMethod() {
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/RadioQueue.kt"
 )
 assertTrue(
 source.contains("fun clear() = reset()"),
 "RadioQueue should expose a public `fun clear()` that is an alias for `reset()`"
 )

 // The two should be distinct methods (clear() is the; intentrevealing public API; reset() is the implementation).
 val clearIndex = source.indexOf("fun clear()")
 val resetIndex = source.indexOf("fun reset()")
 assertNotNull(clearIndex)
 assertNotNull(resetIndex)
 assertNotEquals(-1, clearIndex)
 assertNotEquals(-1, resetIndex)
 }

 @Test
 fun radioStopAndClearQueueAreSeparate() {

 // Regression guard: `Radio.stop(ended)` is the "stop; everything" path; `Radio.clearQueue()` is the; "clear queue only" path. They must not be merged.
 val source = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
 )
 // `Radio.stop` should still call `stopCurrentSong()`.
 val stopFn = source.substringAfter("fun stop(ended: Boolean)").substringBefore("\n }")
 assertTrue(
 stopFn.contains("stopCurrentSong()"),
 "Radio.stop(ended) must still call stopCurrentSong()"
 )
 // `Radio.clearQueue` must NOT call `stopCurrentSong()`.
 val clearQueueFn = source.substringAfter("fun clearQueue()").substringBefore("\n }")
 assertTrue(
 !clearQueueFn.contains("stopCurrentSong()"),
 "Radio.clearQueue() must NOT stop the current song (Fix for Issue #843)"
 )
 }
}
