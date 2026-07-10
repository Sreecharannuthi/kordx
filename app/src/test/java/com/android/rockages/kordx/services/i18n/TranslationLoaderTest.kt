package com.android.rockages.kordx.services.i18n

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TranslationLoaderTest {

 private val buildGradle = File("build.gradle.kts")
 private val translationKt = File(
 "src/main/java/com/android/rockages/kordx/services/i18n/Translation.kt"
 )

 @Test
 fun kotlinOptionsEmitsJavaParameters() {
 assertTrue(buildGradle.exists(), "build.gradle.kts not found at ${buildGradle.absolutePath}")
 val source = buildGradle.readText()
 assertTrue(
 source.contains("-java-parameters"),
 "kotlinOptions must pass `-java-parameters` so kotlinc emits " +
 "the `MethodParameters` attribute and `Parameter.getName()` " +
 "returns real names in constructor order ( i18n " +
 "root-cause fix), instead of synthetic 'p0', 'p1', ... " +
 "that blank every translation."
 )
 }

 @Test
 fun buildKeysUsesConstructorParameterNamesAsPrimary() {
 assertTrue(translationKt.exists(), "Translation.kt not found at ${translationKt.absolutePath}")
 val source = translationKt.readText()

 // Primary fix: parameter names read from the constructor via; `Parameter.getName()`, gated on `isNamePresent()` so the real; (postflag) names are used. ctor.parameters is iterated in; constructor order — the CORRECT order for newInstance(*args).
 assertTrue(
 source.contains("isNamePresent"),
 "buildKeys must gate the parameter-name path on " +
 "`Parameter.isNamePresent()` so it only uses real names " +
 "(post `-java-parameters`) and falls back otherwise."
 )
 assertTrue(
 source.contains("keysCtorParamNames") || source.contains("ctor.parameters"),
 "buildKeys must derive names from the constructor parameters " +
 "(in constructor order) as the primary source."
 )
 }

 @Test
 fun buildKeysDoesNotUseDeclaredFieldOrderAsThePrimaryNameSource() {

 // Regression guard against the Mode B bug: an earlier draft; used `_Keys::class.java.declaredFields()` as the PRIMARY name; source, but getDeclaredFields() returns alphabeticallysorted; fields (not declaration / constructor order), which shuffled; every value into the wrong field. The fieldname list may; remain only as an emergency FALLBACK, never the primary.
 assertTrue(translationKt.exists())
 val source = translationKt.readText()
 assertTrue(
 source.contains("allCtorNamesPresent") || source.contains("all { it.isNamePresent }"),
 "buildKeys must prefer constructor parameter names when all " +
 "are present (`allCtorNamesPresent`), using declaredFields " +
 "only as a fallback — otherwise the alphabetically-sorted " +
 "field order shuffles values into wrong fields (Mode B)."
 )

 // Inspect the actual `buildKeys` function body (bracematched); so explanatory comments naming `declaredFields` don't fool the; ordering check. Within that body the `when` must list the; constructorname branch (`allCtorNamesPresent`) BEFORE the; fieldname fallback branch (`keysFieldNames.size`).
 val body = extractFunctionBody(source, "fun buildKeys(")
 assertTrue(body.isNotBlank(), "Could not locate `fun buildKeys(` in Translation.kt")
 val ctorBranch = body.indexOf("allCtorNamesPresent")
 val fieldBranch = body.indexOf("keysFieldNames.size")
 assertTrue(
 ctorBranch in 0 until fieldBranch,
 "In buildKeys the constructor-name branch (allCtorNamesPresent) " +
 "must come BEFORE the declaredFields fallback branch " +
 "(keysFieldNames.size), so ctor-order names are primary and " +
 "the alphabetically-sorted field order is only an emergency " +
 "fallback (prevents the Mode B label-shuffle regression)."
 )
 }

 /**
 * Returns the source region of the first function whose signature
 * starts with [signatureFragment], from the signature through the
 * matching closing brace. Handles nested braces (lambdas, `when`).
 */
 private fun extractFunctionBody(source: String, signatureFragment: String): String {
 val start = source.indexOf(signatureFragment)
 if (start < 0) return ""
 val braceStart = source.indexOf('{', start)
 if (braceStart < 0) return ""
 var depth = 1
 var i = braceStart + 1
 while (i < source.length && depth > 0) {
 when (source[i]) {
 '{' -> depth++
 '}' -> depth--
 }
 i++
 }
 return if (depth == 0) source.substring(start, i) else ""
 }

 @Test
 fun buildKeysStillToleratesMissingOptionalKeys() {
 assertTrue(translationKt.exists())
 val source = translationKt.readText()

 // The original tolerance: a key missing in the JSON resolves; to the empty string (never a crash). This must be preserved.
 assertTrue(
 source.contains("?: \"\""),
 "buildKeys must keep the `?: \"\"` fallback so a key missing " +
 "from the JSON locale file does not crash the loader."
 )
 }
}