package com.android.rockages.kordx

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Regression test for the i18n ProGuard / R8 keep rules.
 *
 * **Background.** `Translation.kt` builds the generated 251-field `_Keys`
 * data class via Java reflection (`_Keys::class.java.declaredConstructors`).
 * R8 in non-full mode does NOT follow reflection calls — it relies on
 * explicit `-keep` rules in `app/proguard-rules.pro` to preserve the
 * reflection target's members.
 *
 * v1.1.0/v1.1.1 shipped with rules that referenced the WRONG JVM class
 * names: e.g. `-keep class ...Translation$_Keys` (the `_Keys` is actually
 * nested in `_Translation`, so the JVM class is `_Translation$_Keys`).
 * The rule never matched anything; R8 stripped the primary constructor
 * of `_Keys`; at runtime the `keysConstructor` lazy delegate threw
 * `NoSuchElementException: Array contains no element matching the
 * predicate` on the `.first { parameterCount > 0 }` call; the exception
 * propagated out of `Translation.<init>` → the `KordX` ViewModel
 * constructor → the framework's `ViewModelProvider` factory, surfacing
 * as the red "Cannot create an instance of class com.android.rockages.kordx.KordX"
 * crash screen at app start. AVD validation missed this because the AVD
 * gate runs a DEBUG build (no R8) — only release builds manifest it.
 *
 * This test pins the correct FQNs so the typo can't reappear in the
 * proguard rules.
 */
class I18nProguardRulesTest {

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

    private fun loadProguardRules(): String = loadSource("app/proguard-rules.pro")

    // ---- The classes that MUST be kept (i18n reflection targets) ----

    @Test
    fun keepsMethodParametersAttribute() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains("MethodParameters"),
            "proguard-rules.pro must add `MethodParameters` to `-keepattributes`. " +
                "R8 in non-full mode strips the attribute by default; without it, " +
                "`Parameter.getName()` returns synthetic names (\"p0\", \"p1\", ...) on " +
                "every Android runtime, the i18n loader's `obj[name]` lookups all miss, " +
                "and every Compose `Text` sourced from i18n renders the wrong value " +
                "(the 'nonempty but WRONG labels' bug). The kotlinc flag " +
                "`-java-parameters` emits the attribute, but R8 strips it unless " +
                "explicitly kept.",
        )
    }

    @Test
    fun keepsTranslation() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains("-keep class com.android.rockages.kordx.services.i18n.Translation"),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n.Translation",
        )
    }

    @Test
    fun keepsUnderscoreTranslation() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains("-keep class com.android.rockages.kordx.services.i18n._Translation"),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n._Translation " +
                "(the parent class containing the nested _Keys / _Container / _Locale data classes)",
        )
    }

    @Test
    fun keepsUnderscoreTranslationUnderscoreKeys() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains(
                "-keep class com.android.rockages.kordx.services.i18n._Translation\$_Keys"
            ),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n._Translation\$_Keys " +
                "(the 251-field _Keys data class accessed via reflection in keysConstructor). " +
                "Note: _Keys is nested in _Translation (NOT in Translation), so the JVM FQN is " +
                "_Translation\$_Keys. The previous typo'd rule (Translation\$_Keys) was a no-op and " +
                "caused R8 to strip the primary constructor, crashing the app on release builds.",
        )
    }

    @Test
    fun keepsUnderscoreTranslationUnderscoreContainer() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains(
                "-keep class com.android.rockages.kordx.services.i18n._Translation\$_Container"
            ),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n._Translation\$_Container " +
                "(the nested data class used in Translation's constructor signature)",
        )
    }

    @Test
    fun keepsUnderscoreTranslationUnderscoreLocale() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains(
                "-keep class com.android.rockages.kordx.services.i18n._Translation\$_Locale"
            ),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n._Translation\$_Locale " +
                "(the nested data class used in _Container's constructor signature)",
        )
    }

    @Test
    fun keepsUnderscoreTranslations() {
        val rules = loadProguardRules()
        assertTrue(
            rules.contains("-keep class com.android.rockages.kordx.services.i18n._Translations"),
            "proguard-rules.pro must -keep com.android.rockages.kordx.services.i18n._Translations " +
                "(the sibling data source for localeCodes / localeDisplayNames / localeNativeNames). " +
                "Note: the class is _Translations (not Translation\$Translations) — top-level.",
        )
    }

    // ---- Negative tests: the WRONG FQNs must NOT appear in the keep rules ----
    //
    // These guard against a regression to the v1.1.0/v1.1.1 typo where the
    // rules referenced `Translation$_Keys` / `Translation$Translations` /
    // etc. — class names that don't exist in the JVM (the inner classes
    // are nested in `_Translation`, not `Translation`). The wrong-named
    // rules were silent no-ops; R8 stripped the reflection targets; the
    // app crashed on every release-build install.

    /** Extracts the lines that are actual keep rules (start with `-keep`). */
    private fun keepRuleLines(rules: String): List<String> =
        rules.lineSequence().filter { it.trimStart().startsWith("-keep") }.toList()

    @Test
    fun noKeepOnTranslationDollarKeys() {
        val rules = loadProguardRules()
        val keepLines = keepRuleLines(rules)
        // Match the wrong FQN `Translation$_Keys` exactly (not the substring
        // of the correct `_Translation$_Keys`). The wrong FQN is a `-keep`
        // class name with a leading space and a space after, so we use a
        // word-boundary check: `Translation$_Keys` not preceded by `_` or a
        // letter / digit.
        val typoPattern = Regex("""(?<![A-Za-z0-9_])Translation\$\w+""")
        val offending = keepLines.filter { line ->
            typoPattern.containsMatchIn(line) &&
                // Exclude the correct rule (which legitimately contains
                // `_Translation$_Keys` as a substring).
                !line.contains("_Translation\$")
        }
        assertTrue(
            offending.isEmpty(),
            "proguard-rules.pro must NOT contain a -keep rule for the non-existent class " +
                "`Translation\$_Keys`. The correct JVM FQN is `_Translation\$_Keys` (the " +
                "_Keys data class is nested in _Translation, not Translation). " +
                "The typo'd rule was a silent no-op and caused R8 to strip the constructor. " +
                "Offending rule lines: $offending",
        )
    }

    @Test
    fun noKeepOnTranslationDollarTranslations() {
        val rules = loadProguardRules()
        val keepLines = keepRuleLines(rules)
        val offending = keepLines.filter { line ->
            // Match `Translation$Translations` (wrong) but not the
            // substring of any correct rule.
            line.contains("Translation\$Translations") &&
                !line.contains("_Translation\$Translations")
        }
        assertTrue(
            offending.isEmpty(),
            "proguard-rules.pro must NOT contain a -keep rule for the non-existent class " +
                "`Translation\$Translations`. The correct JVM FQN is `_Translations` (the class " +
                "is a sibling of Translation, not a nested class). " +
                "Offending rule lines: $offending",
        )
    }
}
