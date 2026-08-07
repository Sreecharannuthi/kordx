package com.android.rockages.kordx.services.i18n

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TranslationLoaderTest {

    private val translationKt = File(
        "src/main/java/com/android/rockages/kordx/services/i18n/Translation.kt"
    )
    private val translationGKt = File(
        "src/main/java/com/android/rockages/kordx/services/i18n/Translation.g.kt"
    )

    @Test
    fun buildKeysConstructsAMap() {
        assertTrue(translationKt.exists(), "Translation.kt not found")
        val source = translationKt.readText()
        assertTrue(
            source.contains("Map<String, String>") || source.contains("mapValues"),
            "buildKeys must build a Map<String, String> from the JSON keys object " +
                "so the translation set is no longer limited by the JVM method-descriptor limit."
        )
        assertTrue(
            source.contains("_Keys(map)"),
            "buildKeys must pass the constructed map to the _Keys constructor."
        )
    }

    @Test
    fun keysClassIsMapBackedAndNotAMassiveDataClass() {
        assertTrue(translationGKt.exists(), "Translation.g.kt not found")
        val source = translationGKt.readText()
        assertTrue(
            source.contains("class _Keys(private val values: Map<String, String>)"),
            "_Keys must be a map-backed class instead of a data class with hundreds " +
                "of constructor parameters, which trips the Dalvik verifier."
        )
    }

    @Test
    fun missingKeyFallsBackToEmptyString() {
        assertTrue(translationGKt.exists(), "Translation.g.kt not found")
        val source = translationGKt.readText()
        assertTrue(
            source.contains("?: \"\""),
            "_Keys property getters must fall back to the empty string when a key is " +
                "missing, preserving the previous missing-key tolerance."
        )
    }

    @Test
    fun suggestedSongsKeyLoadsNonEmpty() {
        val en = File("src/main/assets/i18n/en.json")
        assertTrue(en.exists(), "en.toml not found")
        val translation = Translation.fromInputStream(en.inputStream())
        assertTrue(
            translation.SuggestedSongs.isNotBlank(),
            "The SuggestedSongs translation must load as a non-empty string; " +
                "if blank, the new home section label will be invisible."
        )
    }
}
