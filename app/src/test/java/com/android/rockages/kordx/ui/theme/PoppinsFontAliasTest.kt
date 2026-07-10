package com.android.rockages.kordx.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM unit test that pins the [KordXBuiltinFonts.Poppins] alias to the `poppins_*.ttf` font resources (not the `roboto_*.ttf` ones it was previously incorrectly referencing). The `KordXBuiltinFonts.Poppins` was a copy-paste bug from when the Poppins ttf files were added under `app/src/main/res/font/poppins_*.ttf` but the FontFamily reference in [KordXBuiltinFonts.Poppins] was never updated. Users who selected "Poppins" in `settings.fontFamily` got Roboto silently. The test asserts: 1. The Poppins and Roboto `FontFamily` instances are NOT the same object (i.e. the alias was actually updated, not just left pointing at Roboto). 2. The Poppins `FontFamily` resolves to the `R.font.poppins_*` resources (i.e. it's wired to the right font files). 3. The source file [Typography.kt] no longer contains `R.font.roboto_regular` / `R.font.roboto_bold` references inside the Poppins `KordXFont.fromValue(...)` block. The source-level check (#3) is the strongest regression guard: it catches both the original bug (Roboto reference) and regression (re-pasting Roboto) regardless of how the FontFamily instance is exposed. */
class PoppinsFontAliasTest {

 @Test
 fun poppinsAndRobotoFontFamiliesAreDistinct() {

 // Two FontFamily instances with different Font resources must; be distinct objects. If they were the same (the pre30b; bug), Poppins would silently render as Roboto.
 val poppinsFamily = KordXBuiltinFonts.Poppins.fontFamily()
 val robotoFamily = KordXBuiltinFonts.Roboto.fontFamily()
 assertNotEquals(
 poppinsFamily, robotoFamily,
 "Poppins and Roboto FontFamily must be distinct objects " +
 "(pre-30b bug: both were `R.font.roboto_*`)."
 )
 }

 @Test
 fun poppinsAndInterFontFamiliesAreDistinct() {
 // Sanity: Poppins must also differ from Inter (the default font).
 val poppinsFamily = KordXBuiltinFonts.Poppins.fontFamily()
 val interFamily = KordXBuiltinFonts.Inter.fontFamily()
 assertNotEquals(
 poppinsFamily, interFamily,
 "Poppins and Inter FontFamily must be distinct objects."
 )
 }

 @Test
 fun poppinsAliasDoesNotReferenceRoboto() {

 // Sourcelevel check: the Poppins block in Typography.kt must; not contain `R.font.roboto_regular` or `R.font.roboto_bold`; as ACTUAL FONT references (i.e. inside `Font(R.font.roboto_*`).; The block may contain `roboto_regular` in a comment that; explains the pre30b bug, which is fine.
 val source = readTypographySource()
 val poppinsBlock = extractPoppinsBlock(source)
 // Strip out block comments and line comments before checking.
 val codeOnly = poppinsBlock
 .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
 .replace(Regex("""//[^\n]*"""), "")
 assertTrue(
 !codeOnly.contains("R.font.roboto_regular"),
 "Poppins FontFamily still references `R.font.roboto_regular` " +
 "(pre-30b bug). The fix replaces it with `R.font.poppins_regular`."
 )
 assertTrue(
 !codeOnly.contains("R.font.roboto_bold"),
 "Poppins FontFamily still references `R.font.roboto_bold` " +
 "(pre-30b bug). The fix replaces it with `R.font.poppins_bold`."
 )
 }

 @Test
 fun poppinsAliasReferencesPoppinsFontResources() {

 // Sourcelevel check: the Poppins block must reference; `R.font.poppins_regular` and `R.font.poppins_bold`. Catches; an overcorrection (e.g. accidentally pointing at a different; font).
 val source = readTypographySource()
 val poppinsBlock = extractPoppinsBlock(source)
 assertTrue(
 poppinsBlock.contains("poppins_regular"),
 "Poppins FontFamily must reference `R.font.poppins_regular`."
 )
 assertTrue(
 poppinsBlock.contains("poppins_bold"),
 "Poppins FontFamily must reference `R.font.poppins_bold`."
 )
 }

 @Test
 fun robotoFontFamilyIsStillWired() {

 // Regression guard: fixing the Poppins alias must not break; the Roboto alias.
 val source = readTypographySource()
 val robotoBlock = extractFontBlock(source, "Roboto")
 assertTrue(
 robotoBlock.contains("roboto_regular"),
 "Roboto FontFamily must still reference `R.font.roboto_regular`."
 )
 assertTrue(
 robotoBlock.contains("roboto_bold"),
 "Roboto FontFamily must still reference `R.font.roboto_bold`."
 )
 }

 @Test
 fun allFontsListedInAllMap() {

 // The `KordXTypography.all` map must still include all 5; builtin fonts. If the Poppins entry was accidentally; removed during the fix, this fails.
 val all = KordXTypography.all
 assertEquals(5, all.size, "Expected 5 built-in fonts")
 assertTrue(KordXBuiltinFonts.Poppins.fontName in all)
 assertTrue(KordXBuiltinFonts.Roboto.fontName in all)
 assertTrue(KordXBuiltinFonts.Inter.fontName in all)
 assertTrue(KordXBuiltinFonts.DMSans.fontName in all)
 assertTrue(KordXBuiltinFonts.ProductSans.fontName in all)
 }

 @Test
 fun poppinsResolvesViaAllMap() {
 // The `resolveFont` helper must find Poppins by name.
 val resolved = KordXTypography.resolveFont("Poppins")
 assertEquals(
 KordXBuiltinFonts.Poppins.fontName, resolved.fontName
 )
 }

 @Test
 fun poppinsIsResolvedToItselfNotRoboto() {

 // Strongest assertion: resolving "Poppins" by name returns a; font whose FontFamily is the same instance as the Poppins; field (not the Roboto one). This catches a regression; where the `all` map is rebuilt with the wrong reference.
 val resolved = KordXTypography.resolveFont("Poppins")
 val poppinsFamily = KordXBuiltinFonts.Poppins.fontFamily()
 val robotoFamily = KordXBuiltinFonts.Roboto.fontFamily()
 val resolvedFamily = resolved.fontFamily()
 assertEquals(
 poppinsFamily, resolvedFamily,
 "Resolving 'Poppins' must return the Poppins FontFamily, " +
 "not the Roboto one."
 )
 assertNotEquals(
 robotoFamily, resolvedFamily,
 "Resolving 'Poppins' must NOT return the Roboto FontFamily."
 )
 }

 private fun readTypographySource(): String {
 val file = java.io.File(
 "src/main/java/com/android/rockages/kordx/ui/theme/Typography.kt"
 )
 require(file.exists()) {
 "Expected Typography.kt at ${file.absolutePath}"
 }
 return file.readText()
 }

 /**
 * Extracts the source block for the
 * `val <name> = KordXFont.fromValue(...)` declaration. The block
 * ends at the matching closing `)` of the `fromValue` call.
 */
 private fun extractFontBlock(source: String, name: String): String {
 val signature = "val $name = KordXFont.fromValue("
 val startIndex = source.indexOf(signature)
 require(startIndex >= 0) {
 "Could not find `val $name = KordXFont.fromValue(` in Typography.kt"
 }
 var depth = 1
 var i = startIndex + signature.length
 while (i < source.length && depth > 0) {
 when (source[i]) {
 '(' -> depth++
 ')' -> depth--
 }
 i++
 }
 return source.substring(startIndex, i)
 }

 private fun extractPoppinsBlock(source: String): String =
 extractFontBlock(source, "Poppins")
}
