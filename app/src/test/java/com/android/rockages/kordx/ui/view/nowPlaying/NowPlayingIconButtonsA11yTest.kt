package com.android.rockages.kordx.ui.view.nowPlaying

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** JVM unit test that scans the NowPlaying + Home view source files for any `IconButton { Icon(..., null, ...) }` pattern and asserts that every such pattern has been replaced with a non-null `contentDescription` argument. The code had 7 IconButton-only controls with `Icon(icon, null)` (no a11y label), which made the NowPlaying screen + the Home overflow menu unusable for TalkBack users: 1. NowPlaying BottomBar — lyrics (Icons.AutoMirrored.Outlined.Article) 2. NowPlaying BottomBar — repeat / repeat-one 3. NowPlaying BottomBar — shuffle (Icons.Filled.Shuffle) 4. NowPlaying BottomBar — more (Icons.Outlined.MoreHoriz) 5. NowPlaying AppBar (regular + landscape) — expand-more 6. NowPlaying BodyContent — favorite / favorite-border 7. NowPlaying BodyContent — more-vert 8. Home top bar — more-vert overflow menu The fix replaces every `Icon(icon, null, ...)` (inside an `IconButton { ... }` block) with `Icon(icon, context.kordx.t.<i18nKey>, ...)`, using the new 5 i18n keys added (`Lyrics`, `Repeat`, `Shuffle`, `More`, `CollapseNowPlaying`, `Favorite`). The test is a source-level scanner (mirrors the pattern from 's `RadioQueueLoopShufflePersistenceTest`, 's `CustomBrowseActionTest`, and 's `IconRefinementTest`). It reads the .kt file from disk and asserts the regression- critical structural changes are present. This is the right pattern because the Android resource system is not available on the JVM unit-test classpath. **Note:** the test only checks `Icon` calls inside `IconButton` blocks. `Icon` calls inside `ListItem { leadingContent = { Icon( ..., null) } }` are NOT audited — the `ListItem` provides its own a11y label via the `headlineContent` `Text` composable, so the leadingContent icon can safely be decorative. */
class NowPlayingIconButtonsA11yTest {

 private val filesToAudit = listOf(
 File("src/main/java/com/android/rockages/kordx/ui/view/nowPlaying/BottomBar.kt"),
 File("src/main/java/com/android/rockages/kordx/ui/view/nowPlaying/AppBar.kt"),
 File("src/main/java/com/android/rockages/kordx/ui/view/nowPlaying/BodyContent.kt"),
 File("src/main/java/com/android/rockages/kordx/ui/view/Home.kt"),
 )

 @Test
 fun allAuditedFilesExist() {
 for (file in filesToAudit) {
 assertTrue(
 file.exists(),
 "Expected audited file at ${file.absolutePath}",
 )
 }
 }

 @Test
 fun noIconButtonHasIconWithNullContentDescription() {
 for (file in filesToAudit) {
 val source = file.readText()
 val violations = findIconButtonNullContentDescriptionIcons(source)
 assertTrue(
 violations.isEmpty(),
 buildString {
 append("Found ${violations.size} IconButton { Icon(..., null) } pattern(s) in ${file.name}:\n")
 for (v in violations) {
 append(" line ${v.line}: ${v.snippet}\n")
 }
 append("Each Icon inside an IconButton must have a non-null " +
 "contentDescription ( a11y fix).")
 }
 )
 }
 }

 // ---- Helpers.

 /**
 * Finds `IconButton { ... Icon(..., null, ...) ... }` patterns.
 * The implementation: locate every `IconButton(` opening, find the
 * matching `IconButton { ... }` block via brace matching, and
 * check whether the block contains an `Icon(..., null, ...)` or
 * `Icon(..., null)` pattern.
 */
 private fun findIconButtonNullContentDescriptionIcons(
 source: String,
 ): List<Violation> {
 val violations = mutableListOf<Violation>()
 val lines = source.lines()

 // For each `IconButton(` opening, find the matching `}` and; check the block for null contentDescription Icons.
 val iconButtonRegex = Regex("""IconButton\s*\(""")
 for (match in iconButtonRegex.findAll(source)) {
 val startIndex = match.range.first

 // Find the opening `{` of the IconButton's trailing lambda; (the onClick=... may be the last named arg, then `) { ... }`).
 val blockStart = findOpeningBrace(source, match.range.last)
 if (blockStart < 0) continue
 val blockEnd = findMatchingCloseBrace(source, blockStart)
 if (blockEnd < 0) continue
 val block = source.substring(blockStart, blockEnd + 1)
 // Check for `Icon(..., null, ...)` or `Icon(..., null)` inside the block.
 val nullIconRegex = Regex("""Icon\s*\([^)]*,\s*null\b""")
 for (iconMatch in nullIconRegex.findAll(block)) {
 val absoluteLine = source.lineNumberForOffset(
 blockStart + iconMatch.range.first
 )
 val snippet = lines[absoluteLine - 1].trim().take(120)
 violations.add(Violation(line = absoluteLine, snippet = snippet))
 }
 }
 return violations
 }

 /**
 * Given a position just after `IconButton(`, finds the position of
 * the next opening `{` (the trailing lambda's opening brace),
 * skipping over `onClick = { ... }` lambdas and string literals.
 * Returns -1 if not found within ~1000 chars (the audit doesn't
 * scan arbitrarily long IconButton blocks; the existing audited
 * files have no such patterns).
 */
 private fun findOpeningBrace(source: String, fromIndex: Int): Int {
 var depth = 0
 var inString = false
 var stringChar = ' '
 val end = (fromIndex + 2000).coerceAtMost(source.length)
 var i = fromIndex
 while (i < end) {
 val c = source[i]
 if (inString) {
 if (c == stringChar && source[i - 1] != '\\') inString = false
 } else {
 when (c) {
 '"', '\'' -> {
 inString = true
 stringChar = c
 }

 '(' -> depth++
 ')' -> {
 if (depth == 0) {

 // End of IconButton's parameter list.; Now find the next opening brace (the trailing lambda).
 while (i < end) {
 when (source[i]) {
 '{' -> return i
 ' ', '\t', '\n', '\r' -> { /* skip */ }
 else -> return -1
 }
 i++
 }
 return -1
 }

 depth--
 }
 }
 }
 i++
 }
 return -1
 }

 /**
 * Given a position of an opening `{`, returns the position of the
 * matching closing `}` (using brace-counting). Returns -1 if not
 * found within ~5000 chars.
 */
 private fun findMatchingCloseBrace(source: String, openIndex: Int): Int {
 var depth = 0
 var inString = false
 var stringChar = ' '
 val end = (openIndex + 5000).coerceAtMost(source.length)
 for (i in openIndex until end) {
 val c = source[i]
 if (inString) {
 if (c == stringChar && source[i - 1] != '\\') inString = false
 } else {
 when (c) {
 '"', '\'' -> {
 inString = true
 stringChar = c
 }

 '{' -> depth++
 '}' -> {
 depth--
 if (depth == 0) return i
 }
 }
 }
 }
 return -1
 }

 private fun String.lineNumberForOffset(offset: Int): Int {
 // 1-based line number for the given character offset.
 var line = 1
 for (i in 0 until offset.coerceAtMost(this.length)) {
 if (this[i] == '\n') line++
 }
 return line
 }

 private data class Violation(val line: Int, val snippet: String)
}
