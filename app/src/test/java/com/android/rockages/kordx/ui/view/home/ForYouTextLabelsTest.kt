package com.android.rockages.kordx.ui.view.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** JVM unit test that confirms [ForYou.kt] no longer contains the standalone `Card { AsyncImage }` pattern under the `SuggestedAlbums` / `SuggestedArtists` / `SuggestedAlbumArtists` private composables. The old code rendered 6 anonymous tiles (artwork only, no album/artist name) because the inline `Card { AsyncImage }` had no `Text` composable inside. The fix replaces those 3 inline `Card { AsyncImage }` blocks with calls to the existing [AlbumTile] / [ArtistTile] / [AlbumArtistTile] composables, which already render title + artist text correctly. The Albums / Artists / Album Artists tabs already used these tile composables (and rendered correctly on the AVD), so reusing them here is the lowest-risk fix. The test greps the source file for the three old patterns (one per Suggested composable) and asserts: 1. None of the inline `Card { AsyncImage }` patterns exist under the `Suggested*` composables. 2. The new `AlbumTile` / `ArtistTile` / `AlbumArtistTile` call sites are present (regression guard against accidental reverts). Mirrors the source-level test pattern from 's `RadioQueueLoopShufflePersistenceTest`, 's `CustomBrowseActionTest`, and 's `IconRefinementTest`: the test reads the .kt file from disk and asserts the regression-critical structural changes are present. */
class ForYouTextLabelsTest {

 private val forYouFile = File(
 "src/main/java/com/android/rockages/kordx/ui/view/home/ForYou.kt"
 )

 @Test
 fun forYouFileExists() {

 // Sanity check: the test itself is wired to the right file.; If the path changes, this fails first and gives a clear; error message.
 assertTrue(
 forYouFile.exists(),
 "Expected ForYou.kt at ${forYouFile.absolutePath}",
 )
 }

 @Test
 fun noInlineCardAsyncImageUnderSuggestedAlbums() {
 val source = forYouFile.readText()

 // Match a `Card(` immediately followed (after whitespace) by an; `AsyncImage(` inside the `SuggestedAlbums` composable block.; The block ends at the closing `}` of the composable.
 val suggestedAlbumsBlock = extractSuggestedBlock(source, "SuggestedAlbums")
 val cardAsyncImageCount = countCardAsyncImagePattern(suggestedAlbumsBlock)
 assertEquals(
 0, cardAsyncImageCount,
 "SuggestedAlbums still has inline `Card { AsyncImage }` " +
 "pattern (count=$cardAsyncImageCount). The fix " +
 "replaces it with `AlbumTile(context, album)`."
 )
 }

 @Test
 fun noInlineCardAsyncImageUnderSuggestedArtists() {
 val source = forYouFile.readText()
 val suggestedArtistsBlock = extractSuggestedBlock(source, "SuggestedArtists")
 val cardAsyncImageCount = countCardAsyncImagePattern(suggestedArtistsBlock)
 assertEquals(
 0, cardAsyncImageCount,
 "SuggestedArtists still has inline `Card { AsyncImage }` " +
 "pattern (count=$cardAsyncImageCount). The fix " +
 "replaces it with `ArtistTile(context, artist)`."
 )
 }

 @Test
 fun noInlineCardAsyncImageUnderSuggestedAlbumArtists() {
 val source = forYouFile.readText()
 val suggestedAlbumArtistsBlock = extractSuggestedBlock(
 source, "SuggestedAlbumArtists"
 )
 val cardAsyncImageCount = countCardAsyncImagePattern(suggestedAlbumArtistsBlock)
 assertEquals(
 0, cardAsyncImageCount,
 "SuggestedAlbumArtists still has inline `Card { AsyncImage }` " +
 "pattern (count=$cardAsyncImageCount). The fix " +
 "replaces it with `AlbumArtistTile(context, albumArtist)`."
 )
 }

 @Test
 fun albumTileCallSiteIsPresent() {
 val source = forYouFile.readText()
 val suggestedAlbumsBlock = extractSuggestedBlock(source, "SuggestedAlbums")
 assertTrue(
 suggestedAlbumsBlock.contains("AlbumTile(context, album)"),
 "Expected `AlbumTile(context, album)` call site under " +
 "SuggestedAlbums ( fix)."
 )
 }

 @Test
 fun artistTileCallSiteIsPresent() {
 val source = forYouFile.readText()
 val suggestedArtistsBlock = extractSuggestedBlock(source, "SuggestedArtists")
 assertTrue(
 suggestedArtistsBlock.contains("ArtistTile(context, artist)"),
 "Expected `ArtistTile(context, artist)` call site under " +
 "SuggestedArtists ( fix)."
 )
 }

 @Test
 fun albumArtistTileCallSiteIsPresent() {
 val source = forYouFile.readText()
 val suggestedAlbumArtistsBlock = extractSuggestedBlock(
 source, "SuggestedAlbumArtists"
 )
 assertTrue(
 suggestedAlbumArtistsBlock.contains(
 "AlbumArtistTile(context, albumArtist)"
 ),
 "Expected `AlbumArtistTile(context, albumArtist)` call site " +
 "under SuggestedAlbumArtists ( fix)."
 )
 }

 /**
 * Extracts the source block for the `private fun <name>(...)`
 * composable. The block starts at the function signature and
 * ends at the matching closing brace. The brace counting handles
 * nested braces (lambdas, `when` expressions, etc.).
 */
 private fun extractSuggestedBlock(source: String, name: String): String {
 val signature = "private fun $name("
 val startIndex = source.indexOf(signature)
 assertTrue(
 startIndex >= 0,
 "Could not find `private fun $name(` in ForYou.kt"
 )
 val braceStart = source.indexOf('{', startIndex)
 var depth = 1
 var i = braceStart + 1
 while (i < source.length && depth > 0) {
 when (source[i]) {
 '{' -> depth++
 '}' -> depth--
 }
 i++
 }
 return source.substring(braceStart, i)
 }

 /**
 * Counts `Card(` immediately followed (within ~200 chars) by
 * `AsyncImage(` in the given source block. This matches the
 * pattern:
 * Card(
 * onClick = { ... }
 * ) {
 * AsyncImage(...)
 * }
 */
 private fun countCardAsyncImagePattern(block: String): Int {
 var count = 0
 var i = 0
 while (i < block.length) {
 val cardIndex = block.indexOf("Card(", i)
 if (cardIndex < 0) break
 val windowEnd = (cardIndex + 200).coerceAtMost(block.length)
 val window = block.substring(cardIndex, windowEnd)
 if (window.contains("AsyncImage(")) {
 count++
 }
 i = cardIndex + 1
 }
 return count
 }
}
