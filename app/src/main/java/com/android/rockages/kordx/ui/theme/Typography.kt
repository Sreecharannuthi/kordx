package com.android.rockages.kordx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
            Font(R.font.inter_bold, FontWeight.Bold),
        ),
    )

    val Poppins = KordXFont.fromValue(
        fontName = "Poppins",
        fontFamily = FontFamily(
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium, FontWeight.Medium),
            Font(R.font.poppins_semibold, FontWeight.SemiBold),
            Font(R.font.poppins_bold, FontWeight.Bold),
        ),
    )

    val DMSans = KordXFont.fromValue(
        fontName = "DM Sans",
        fontFamily = FontFamily(
            Font(R.font.dmsans_regular, FontWeight.Normal),
            Font(R.font.dmsans_medium, FontWeight.Medium),
            Font(R.font.dmsans_semibold, FontWeight.SemiBold),
            Font(R.font.dmsans_bold, FontWeight.Bold),
        ),
    )

    val Roboto = KordXFont.fromValue(
        fontName = "Roboto",
        fontFamily = FontFamily(
            Font(R.font.roboto_regular, FontWeight.Normal),
            Font(R.font.roboto_medium, FontWeight.Medium),
            // Roboto SemiBold not bundled; Medium and Bold cover the range.
            Font(R.font.roboto_bold, FontWeight.Bold),
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
    )

    fun resolveFont(name: String?) = all[name] ?: defaultFont

    /**
     * Build a Material 3 [Typography] that:
     *  - uses the resolved [fontFamily] for every style
     *  - respects the RTL/LTR [textDirection]
     *  - assigns **real** font weights (Medium / SemiBold) to each role
     *    instead of relying on Compose faux-bold synthesis
     *
     * M3 type scale roles → weight mapping:
     *  - Display / Headline / Title Large: SemiBold (600)
     *  - Title Medium / Small: Medium (500)
     *  - Body: Normal (400) — keeps reading comfort
     *  - Label Large / Medium: Medium (500)
     *  - Label Small: Normal (400)
     */
    fun toTypography(font: KordXFont, textDirection: TextDirection): Typography {
        val ff = font.fontFamily()
        return Typography().run {
            copy(
                displayLarge = displayLarge.styled(ff, textDirection, FontWeight.SemiBold),
                displayMedium = displayMedium.styled(ff, textDirection, FontWeight.SemiBold),
                displaySmall = displaySmall.styled(ff, textDirection, FontWeight.SemiBold),
                headlineLarge = headlineLarge.styled(ff, textDirection, FontWeight.SemiBold),
                headlineMedium = headlineMedium.styled(ff, textDirection, FontWeight.SemiBold),
                headlineSmall = headlineSmall.styled(ff, textDirection, FontWeight.SemiBold),
                titleLarge = titleLarge.styled(ff, textDirection, FontWeight.SemiBold),
                titleMedium = titleMedium.styled(ff, textDirection, FontWeight.Medium),
                titleSmall = titleSmall.styled(ff, textDirection, FontWeight.Medium),
                bodyLarge = bodyLarge.styled(ff, textDirection, FontWeight.Normal),
                bodyMedium = bodyMedium.styled(ff, textDirection, FontWeight.Normal),
                bodySmall = bodySmall.styled(ff, textDirection, FontWeight.Normal),
                labelLarge = labelLarge.styled(ff, textDirection, FontWeight.Medium),
                labelMedium = labelMedium.styled(ff, textDirection, FontWeight.Medium),
                labelSmall = labelSmall.styled(ff, textDirection, FontWeight.Normal),
            )
        }
    }

    private fun TextStyle.styled(
        fontFamily: FontFamily,
        textDirection: TextDirection,
        fontWeight: FontWeight,
    ) = copy(fontFamily = fontFamily, textDirection = textDirection, fontWeight = fontWeight)
}
