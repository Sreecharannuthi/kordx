package com.android.rockages.kordx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.android.rockages.kordx.R

class KordXFont(
 val fontName: String,
 val fontFamily: () -> FontFamily,
) {
 companion object {
 fun fromValue(fontName: String, fontFamily: FontFamily) = KordXFont(
 fontName = fontName,
 fontFamily = { fontFamily }
 )
 }
}

object KordXBuiltinFonts {
 val Inter = KordXFont.fromValue(
 fontName = "Inter",
 fontFamily = FontFamily(
 Font(R.font.inter_regular, FontWeight.Normal),
 Font(R.font.inter_bold, FontWeight.Bold),
 ),
 )

 val Poppins = KordXFont.fromValue(
 fontName = "Poppins",
 fontFamily = FontFamily(

 // was `R.font.roboto_regular` / `R.font.roboto_bold`; (a copypaste bug from when the Poppins ttf files; were added under `app/src/main/res/font/poppins_*.ttf` but; this FontFamily reference was never updated). Verified; 20260708: both `poppins_regular.ttf` and `poppins_bold.ttf`; exist on disk.
 Font(R.font.poppins_regular, FontWeight.Normal),
 Font(R.font.poppins_bold, FontWeight.Bold)
 ),
 )

 val DMSans = KordXFont.fromValue(
 fontName = "DM Sans",
 fontFamily = FontFamily(
 Font(R.font.dmsans_regular, FontWeight.Normal),
 Font(R.font.dmsans_bold, FontWeight.Bold)
 ),
 )

 val Roboto = KordXFont.fromValue(
 fontName = "Roboto",
 fontFamily = FontFamily(
 Font(R.font.roboto_regular, FontWeight.Normal),
 Font(R.font.roboto_bold, FontWeight.Bold)
 ),
 )

 val ProductSans = KordXFont.fromValue(
 fontName = "Product Sans",
 fontFamily = FontFamily(
 Font(R.font.productsans_regular, FontWeight.Normal),
 Font(R.font.productsans_bold, FontWeight.Bold)
 ),
 )
}

object KordXTypography {
 val defaultFont = KordXBuiltinFonts.Inter
 val all = mapOf(
 KordXBuiltinFonts.Inter.fontName to KordXBuiltinFonts.Inter,
 KordXBuiltinFonts.Poppins.fontName to KordXBuiltinFonts.Poppins,
 KordXBuiltinFonts.DMSans.fontName to KordXBuiltinFonts.DMSans,
 KordXBuiltinFonts.Roboto.fontName to KordXBuiltinFonts.Roboto,
 KordXBuiltinFonts.ProductSans.fontName to KordXBuiltinFonts.ProductSans,
 )

 fun resolveFont(name: String?) = all[name] ?: defaultFont

 fun toTypography(font: KordXFont, textDirection: TextDirection): Typography {
 val fontFamily = font.fontFamily()
 return Typography().run {
 copy(
 displayLarge = displayLarge.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 displayMedium = displayMedium.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 displaySmall = displaySmall.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 headlineLarge = headlineLarge.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 headlineMedium = headlineMedium.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 headlineSmall = headlineSmall.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 titleLarge = titleLarge.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 titleMedium = titleMedium.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 titleSmall = titleSmall.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 bodyLarge = bodyLarge.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 bodyMedium = bodyMedium.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 bodySmall = bodySmall.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 labelLarge = labelLarge.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 labelMedium = labelMedium.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 labelSmall = labelSmall.copy(
 fontFamily = fontFamily,
 textDirection = textDirection,
 ),
 )
 }
 }
}
