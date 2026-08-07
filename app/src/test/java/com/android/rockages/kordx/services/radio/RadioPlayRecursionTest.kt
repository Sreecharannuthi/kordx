package com.android.rockages.kordx.services.radio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RadioPlayRecursionTest {

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

 private fun loadRadio(): String = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/Radio.kt"
 )

 private fun loadRadioSession(): String = loadSource(
 "app/src/main/java/com/android/rockages/kordx/services/radio/RadioSession.kt"
 )

 /**
 * Extract the body of `Radio.play(options: PlayOptions)` as a
 * string for surgical assertions on the early-return guard.
 * The method is single-declaration in the current source
 * (`fun play(options: PlayOptions) { ... }`) so the substring
 * extraction is unambiguous.
 */
 private fun radioPlayBody(source: String): String {
 val start = source.indexOf("fun play(options: PlayOptions)")
 assertTrue(start >= 0, "Radio.play(options) not found in Radio.kt")
 val body = source.substring(start)

 // End at the next toplevel `fun ` declaration or end of; class. Conservative: stop at the next line beginning; with ` fun ` (4space indent = toplevel member).
 val nextFun = body.indexOf("\n fun ", startIndex = 1)
 return if (nextFun >= 0) body.substring(0, nextFun) else body
 }

 /**
 * Strip Kotlin line comments (the double-slash form) and
 * block comments (slash-star ... star-slash) from the
 * source. The source contains a comment that
 * references `onSongFinish(SongFinishSource.Exception)`
 * (the long block comment above the guard), which would
 * otherwise be matched by `String.indexOf` and confuse the
 * source-order assertions. We replace each comment with
 * spaces (one per character) so character positions in the
 * original string are preserved — important for the
 * `autostartIdx < onSongFinishIdx` style assertions.
 */
 private fun stripComments(source: String): String {
 val out = StringBuilder(source.length)
 var i = 0
 while (i < source.length) {
 val c = source[i]
 val n = if (i + 1 < source.length) source[i + 1] else ' '
 if (c == '/' && n == '/') {
 // Line comment: consume to end of line.
 while (i < source.length && source[i] != '\n') {
 out.append(' ')
 i++
 }
 } else if (c == '/' && n == '*') {
 // Block comment: consume to `*/`.
 out.append(' ')
 out.append(' ')
 i += 2
 while (i + 1 < source.length &&
 !(source[i] == '*' && source[i + 1] == '/')
 ) {
 out.append(if (source[i] == '\n') '\n' else ' ')
 i++
 }
 if (i + 1 < source.length) {
 out.append(' ')
 out.append(' ')
 i += 2
 }
 } else {
 out.append(c)
 i++
 }
 }
 return out.toString()
 }

 // ---- Fix 1: early-return guard when song == null && !autostart.

 @Test
 fun radioPlayChecksAutostartInNullSongBranch() {
 val source = loadRadio()
 val playBody = radioPlayBody(source)

 // The guard: inside the `if (song == null) { ... }` branch,; we must check `!options.autostart` and `return` BEFORE; the `onSongFinish(SongFinishSource.Exception)` call.
 assertTrue(
 playBody.contains("if (song == null)"),
 "Radio.play() should have a `if (song == null)` branch"
 )
 assertTrue(
 playBody.contains("options.autostart"),
 "Radio.play() should reference `options.autostart` (the early-return guard)"
 )

 // The guard must be inside the `if (song == null) { ... }`; block, NOT outside it. Assert by checking the relative; order: `if (song == null) {` appears before the autostart; check.
 val nullSongIdx = playBody.indexOf("if (song == null)")
 val autostartIdx = playBody.indexOf("options.autostart")
 assertTrue(
 autostartIdx > nullSongIdx,
 "options.autostart check must come AFTER the `if (song == null)` branch open"
 )
 }

 @Test
 fun radioPlayEarlyReturnsWhenSongNullAndNoAutostart() {
 val source = loadRadio()
 val playBody = radioPlayBody(source)

 // Extract the `if (song == null) { ... }` block. We use a; bracecounting approach to find the matching close brace.
 val nullSongStart = playBody.indexOf("if (song == null) {")
 assertTrue(nullSongStart >= 0, "Could not find `if (song == null) {`")
 val blockStart = playBody.indexOf('{', nullSongStart)
 assertTrue(blockStart >= 0)
 var depth = 0
 var blockEnd = -1
 for (i in blockStart until playBody.length) {
 when (playBody[i]) {
 '{' -> depth++
 '}' -> {
 depth--
 if (depth == 0) {
 blockEnd = i
 break
 }
 }
 }
 }
 assertTrue(blockEnd > blockStart, "Could not find matching close brace")
 val nullSongBlock = playBody.substring(blockStart, blockEnd + 1)

 // The block must contain both a `!options.autostart`; check AND a `return` statement before the; `onSongFinish(SongFinishSource.Exception)` call.
 assertTrue(
 nullSongBlock.contains("!options.autostart"),
 "`if (song == null)` block must check `!options.autostart` (the early-return guard)"
 )
 assertTrue(
 nullSongBlock.contains("return"),
 "`if (song == null)` block must `return` (the early-return guard)"
 )

 // The `onSongFinish(SongFinishSource.Exception)` call must; still be present (the `autostart == true` autoadvance; path is preserved).
 assertTrue(
 nullSongBlock.contains("onSongFinish(SongFinishSource.Exception)"),
 "`onSongFinish(SongFinishSource.Exception)` must remain for the autostart == true path"
 )
 }

 @Test
 fun radioPlayGuardPrecedesOnSongFinish() {
 val source = stripComments(loadRadio())
 val playBody = radioPlayBody(source)

 // Within the `if (song == null) { ... }` block, the; `!options.autostart` earlyreturn must come BEFORE the; `onSongFinish(SongFinishSource.Exception)` call so the; recursion is shortcircuited.
 val autostartIdx = playBody.indexOf("!options.autostart")
 val onSongFinishIdx = playBody.indexOf("onSongFinish(SongFinishSource.Exception)")
 assertTrue(autostartIdx >= 0, "autostart check not found")
 assertTrue(onSongFinishIdx >= 0, "onSongFinish call not found")
 assertTrue(
 autostartIdx < onSongFinishIdx,
 "The early-return guard (`!options.autostart`) must precede the `onSongFinish` call " +
 "inside the `if (song == null)` block, so the recursion is short-circuited."
 )
 }


 // Revert 1: workaround is GONE from; RadioSession's ACTION_SHUFFLE_ALL handler.

 @Test
 fun radioSessionShuffleAllNoLongerWritesOriginalQueueDirectly() {
 val source = loadRadioSession()
 // The workaround: `kordx.radio.queue.originalQueue.addAll(allSongIds)`
 assertFalse(
 source.contains("kordx.radio.queue.originalQueue.addAll"),
 "RadioSession's ACTION_SHUFFLE_ALL handler must NOT write to " +
 "`kordx.radio.queue.originalQueue` directly — use the safe " +
 "`RadioShorty.playQueue` API instead ( reversion)."
 )
 }

 @Test
 fun radioSessionShuffleAllNoLongerWritesCurrentQueueDirectly() {
 val source = loadRadioSession()
 // The workaround: `kordx.radio.queue.currentQueue.addAll(allSongIds)`
 assertFalse(
 source.contains("kordx.radio.queue.currentQueue.addAll"),
 "RadioSession's ACTION_SHUFFLE_ALL handler must NOT write to " +
 "`kordx.radio.queue.currentQueue` directly — use the safe " +
 "`RadioShorty.playQueue` API instead ( reversion)."
 )
 }

 @Test
 fun radioSessionShuffleAllNoLongerWritesCurrentSongIndexDirectly() {
 val source = loadRadioSession()

 // The workaround wrote the index as part of the; directfield block. passes the index through; `RadioShorty.playQueue(options = Radio.PlayOptions(index = ...))`.; We assert the LITERAL directwrite assignment pattern; (without going through `playQueue`) is gone.
 val directWritePattern = "kordx.radio.queue.currentSongIndex ="
 val occurrences = source.windowed(directWritePattern.length, 1, false)
 .count { it == directWritePattern }

 // The pattern `kordx.radio.queue.currentSongIndex =` should; no longer appear in the ACTION_SHUFFLE_ALL handler. We; accept occurrences elsewhere (e.g. `onSongFinish`,; `Radio.play`); specifically removes the one; inside the `ACTION_SHUFFLE_ALL` branch.; We assert the count of direct writes in; body is reduced. The source had 1 in the; shuffleall handler. should remove it. Other; call sites in RadioSession keep it (none in the current; source, but be defensive).; For now, assert that the shuffleall branch in; particular does not contain the direct write. We extract; the ACTION_SHUFFLE_ALL branch body.
 val branchStart = source.indexOf("RadioSessionState.ACTION_SHUFFLE_ALL ->")
 assertTrue(branchStart >= 0, "ACTION_SHUFFLE_ALL branch not found")

 // Find the next `RadioSessionState.ACTION_` (the next branch); or the next `}` at the actionblock level. The actions are; sibling `when` branches inside a `fun handleCustomAction`,; so the next branch starts at the same indentation.
 val nextBranchIdx = source.indexOf("RadioSessionState.ACTION_", branchStart + 1)
 val branchEnd = if (nextBranchIdx >= 0) nextBranchIdx else source.length
 val shuffleAllBranch = source.substring(branchStart, branchEnd)
 assertFalse(
 shuffleAllBranch.contains(directWritePattern),
 "ACTION_SHUFFLE_ALL branch must NOT contain direct assignment to " +
 "`kordx.radio.queue.currentSongIndex` — pass it via " +
 "`RadioShorty.playQueue(options = Radio.PlayOptions(index = ...))` instead."
 )
 }


 // Revert 2: the ACTION_SHUFFLE_ALL handler now uses the; safe playQueue API.

 @Test
 fun radioSessionShuffleAllUsesPlayQueue() {
 val source = loadRadioSession()
 val branchStart = source.indexOf("RadioSessionState.ACTION_SHUFFLE_ALL ->")
 assertTrue(branchStart >= 0, "ACTION_SHUFFLE_ALL branch not found")
 val nextBranchIdx = source.indexOf("RadioSessionState.ACTION_", branchStart + 1)
 val branchEnd = if (nextBranchIdx >= 0) nextBranchIdx else source.length
 val shuffleAllBranch = source.substring(branchStart, branchEnd)
 assertTrue(
 shuffleAllBranch.contains("kordx.radio.shorty.playQueue("),
 "ACTION_SHUFFLE_ALL branch must call `kordx.radio.shorty.playQueue(...)` " +
 "with the allSongIds list ( reversion)."
 )
 assertTrue(
 shuffleAllBranch.contains("shuffle = true"),
 "ACTION_SHUFFLE_ALL branch must call `playQueue(... shuffle = true)` " +
 "so the queue is set up shuffled ( reversion)."
 )
 assertTrue(
 shuffleAllBranch.contains("autostart = false"),
 "ACTION_SHUFFLE_ALL branch must pass `autostart = false` to " +
 "`playQueue(...)` so the user starts playback via the standard " +
 "play action (no autoplay on shuffle-all)."
 )
 }

 @Test
 fun radioSessionShuffleAllLogMessagePreserved() {
 val source = loadRadioSession()
 val branchStart = source.indexOf("RadioSessionState.ACTION_SHUFFLE_ALL ->")
 assertTrue(branchStart >= 0, "ACTION_SHUFFLE_ALL branch not found")
 val nextBranchIdx = source.indexOf("RadioSessionState.ACTION_", branchStart + 1)
 val branchEnd = if (nextBranchIdx >= 0) nextBranchIdx else source.length
 val shuffleAllBranch = source.substring(branchStart, branchEnd)

 // The AVD validation gate greps logcat for "shuffle all; started" — that log line must remain in the reverted; handler so the gate is still meaningful.
 assertTrue(
 shuffleAllBranch.contains("shuffle all started"),
 "ACTION_SHUFFLE_ALL branch must log \"shuffle all started\" for the AVD validation gate"
 )
 }


 // Crosscheck: the Radio.play() recursion guard is reachable; from the playQueue> queue.add> afterAdd> play; chain. (The earlyreturn is what makes the; ACTION_SHUFFLE_ALL reversion safe.)

 @Test
 fun radioPlayGuardIsBeforeOnSongFinishInSourceOrder() {
 val source = stripComments(loadRadio())
 val playBody = radioPlayBody(source)
 val guardIdx = playBody.indexOf("if (!options.autostart)")
 val onSongFinishIdx = playBody.indexOf("onSongFinish(SongFinishSource.Exception)")
 assertTrue(guardIdx >= 0, "Early-return guard `if (!options.autostart)` not found in Radio.play()")
 assertTrue(onSongFinishIdx >= 0, "`onSongFinish` call not found in Radio.play()")
 assertTrue(
 guardIdx < onSongFinishIdx,
 "The early-return guard must appear BEFORE the `onSongFinish` call in the source."
 )
 }

 @Test
 fun radioPlayBodyHasExpectedShape() {

 // Sanity check: the play body still has the right shape; after the edit. If refactor breaks; the body, this fails first and gives a clear; diagnostic.
 val source = loadRadio()
 val playBody = radioPlayBody(source)
 assertNotNull(playBody)
 assertTrue(
 playBody.contains("stopCurrentSong()"),
 "Radio.play() should still call `stopCurrentSong()` at the top"
 )
 assertTrue(
 playBody.contains("queue.getSongIdAt(options.index)"),
 "Radio.play() should still resolve the song id from the queue at the requested index"
 )
 assertTrue(
 playBody.contains("kordx.groove.song.get("),
 "Radio.play() should still look up the song by id via `kordx.groove.song.get(...)`"
 )
 }
}
