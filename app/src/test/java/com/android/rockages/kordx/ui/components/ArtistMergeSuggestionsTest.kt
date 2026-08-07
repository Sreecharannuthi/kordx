package com.android.rockages.kordx.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for the auto-merge suggestion heuristic in
 * [ArtistMergeDialog] and [AlbumArtistMergeDialog]. The heuristic must
 * flag whole-word prefix/suffix duplicates (e.g. "Tanvi" → "Tanvi Shah")
 * without matching unrelated names or very short tokens.
 */
class ArtistMergeSuggestionsTest {

    @Test
    fun prefixMatchReturnsLongerNameAsTarget() {
        val names = listOf("Tanvi", "Tanvi Shah", "Other")
        val result = computeMergeSuggestions("Tanvi", names)
        assertEquals(listOf("Tanvi Shah"), result)
    }

    @Test
    fun suffixMatchReturnsLongerNameAsTarget() {
        val names = listOf("Shah", "Tanvi Shah", "Other")
        val result = computeMergeSuggestions("Shah", names)
        assertEquals(listOf("Tanvi Shah"), result)
    }

    @Test
    fun shortSourceNamesProduceNoSuggestions() {
        val names = listOf("AB", "AB CD")
        assertEquals(emptyList<String>(), computeMergeSuggestions("AB", names))
    }

    @Test
    fun noSubstringMatchesReturnEmptyList() {
        val names = listOf("Tanvi", "Krish")
        assertEquals(emptyList<String>(), computeMergeSuggestions("Tanvi", names))
    }

    @Test
    fun embeddedWordDoesNotMatch() {
        // "Anvi" is embedded in "Tanvi" but not a whole word, so no suggestion.
        val names = listOf("Anvi", "Tanvi")
        assertEquals(emptyList<String>(), computeMergeSuggestions("Anvi", names))
    }
}
