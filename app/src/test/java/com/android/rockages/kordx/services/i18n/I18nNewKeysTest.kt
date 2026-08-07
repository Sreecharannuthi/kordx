package com.android.rockages.kordx.services.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression guard for the AX2/PB11 feature keys. Verifies that the
 * runtime English asset contains the new translation keys so the UI
 * never renders an empty label.
 */
class I18nNewKeysTest {

    @Test
    fun placeholderSubstitutionReplacesCurlyBraces() {
        assertEquals("Playing 3 of 19", "Playing {x} of {y}".substitutePlaceholders("3", "19"))
    }

    @Test
    fun placeholderSubstitutionSupportsReorderedPlaceholders() {
        // Hindi / Telugu / Japanese reorder {y} before {x}; the formatter must bind
        // by placeholder name, not by textual order.
        assertEquals("19 of 3", "{y} of {x}".substitutePlaceholders("3", "19"))
    }

    @Test
    fun placeholderSubstitutionLeavesUnknownPlaceholdersUnchanged() {
        assertEquals("Hello {name}", "Hello {name}".substitutePlaceholders("world"))
    }

    @Test
    fun newKeysExistInEnglishAsset() {
        val text = readAsset("app/src/main/assets/i18n/en.json")
        assertKeyPresent(text, "PauseOnAudioFocusDuck", "Pause on audio focus duck")
        assertKeyPresent(text, "MergeWith", "Merge with...")
        assertKeyPresent(text, "MergeArtists", "Merge artists")
        assertKeyPresent(text, "MergeArtistsConfirmation", "{source}")
        assertKeyPresent(text, "MergedArtists", "Merged artists")
        assertKeyPresent(text, "SuggestedMerges", "Suggested merges")
        assertKeyPresent(text, "AutoResumePlayback", "Auto-resume on launch")
    }

    private fun readAsset(relativePath: String): String {
        return File(relativePath).takeIf { it.exists() }?.readText()
            ?: File("../$relativePath").takeIf { it.exists() }?.readText()
            ?: error("Could not find $relativePath")
    }

    private fun assertKeyPresent(text: String, key: String, valueSnippet: String) {
        assertTrue(
            text.contains("\"$key\""),
            "Missing key $key in English i18n asset"
        )
        assertTrue(
            text.contains(valueSnippet),
            "Missing expected value snippet for $key"
        )
    }
}
