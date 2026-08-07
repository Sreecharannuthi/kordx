package com.android.rockages.kordx

import com.android.rockages.kordx.core.groove.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Pinning tests for [Song.normalizeArtistKey].
 *
 * Verifies that spelling variants of the same artist produce identical
 * normalized keys, enabling [ArtistRepository] and [AlbumArtistRepository]
 * to merge duplicate profiles caused by casing / whitespace differences.
 */
class ArtistNormalizationTest {

 // --- Basic equivalence ---

 @Test
 fun identicalStringsProduceSameKey() {
 val a = Song.normalizeArtistKey("A.R. Rahman")
 val b = Song.normalizeArtistKey("A.R. Rahman")
 assertEquals(a, b)
 }

 @Test
 fun caseDifferenceMerges() {
 val a = Song.normalizeArtistKey("A.R. Rahman")
 val b = Song.normalizeArtistKey("a.r. rahman")
 assertEquals(a, b)
 }

 @Test
 fun leadingTrailingWhitespaceIgnored() {
 val a = Song.normalizeArtistKey("A.R. Rahman")
 val b = Song.normalizeArtistKey("  A.R. Rahman  ")
 assertEquals(a, b)
 }

 @Test
 fun multipleSpacesCollapse() {
 val a = Song.normalizeArtistKey("A.R. Rahman")
 val b = Song.normalizeArtistKey("A.R.  Rahman")
 assertEquals(a, b)
 }

 @Test
 fun tabsCollapseLikeSpaces() {
 val a = Song.normalizeArtistKey("A.R. Rahman")
 val b = Song.normalizeArtistKey("A.R.\tRahman")
 assertEquals(a, b)
 }

 @Test
 fun allVariantsCollapseToSameKey() {
 val variants = listOf(
 "A.R. Rahman",
 "a.r. rahman",
 "A.R.  Rahman",
 "  A.R. Rahman ",
 "A.R.\tRahman",
 )
 val keys = variants.map { Song.normalizeArtistKey(it) }.toSet()
 assertEquals(1, keys.size, "All variants must produce the same key: $keys")
 }

 // --- Different artists stay different ---

 @Test
 fun differentArtistsStayDifferent() {
 val a = Song.normalizeArtistKey("The Beatles")
 val b = Song.normalizeArtistKey("Beatles, The")
 assertNotEquals(a, b, "These are genuinely different tags — not auto-merged")
 }

 @Test
 fun similarButDifferentNamesDiffer() {
 val a = Song.normalizeArtistKey("Kendrick Lamar")
 val b = Song.normalizeArtistKey("Kendrick Lamar feat. SZA")
 assertNotEquals(a, b)
 }

 // --- Edge cases ---

 @Test
 fun emptyStringProducesEmptyKey() {
 assertEquals("", Song.normalizeArtistKey(""))
 }

 @Test
 fun blankStringProducesEmptyKey() {
 assertEquals("", Song.normalizeArtistKey("   "))
 }

 @Test
 fun singleCharacter() {
 assertEquals("x", Song.normalizeArtistKey(" X "))
 }

 @Test
 fun unicodePreserved() {
 val key = Song.normalizeArtistKey("Beyoncé")
 assertEquals("beyoncé", key)
 }

 @Test
 fun numbersPreserved() {
 val key = Song.normalizeArtistKey("50 Cent")
 assertEquals("50 cent", key)
 }
}
