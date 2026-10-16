package de.riemer87.relic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.riemer87.relic.R

/*
 * Schriften (Google Fonts, SIL Open Font License, Lizenzen in assets/licenses):
 * - Fraunces für Schriftzug, Überschriften und Tageszahlen
 * - DM Sans für Text und Bedienelemente
 * Beide sind „variable“ Schriften: eine Datei, Stärke und Form werden per Einstellung gewählt.
 */

private fun fraunces(weight: Int, opticalSize: Float, softness: Float) = Font(
    R.font.fraunces,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
        FontVariation.Setting("SOFT", softness),
    ),
)

private fun dmSans(weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    if (style == FontStyle.Italic) R.font.dm_sans_italic else R.font.dm_sans,
    FontWeight(weight),
    style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Fraunces für Überschriften in normaler Größe. */
val FrauncesText = FontFamily(
    fraunces(400, opticalSize = 72f, softness = 50f),
    fraunces(600, opticalSize = 72f, softness = 50f),
)

/** Fraunces für sehr große Schrift (Tageszahlen, Seitentitel): weicher und feiner gezeichnet. */
val FrauncesDisplay = FontFamily(
    fraunces(400, opticalSize = 144f, softness = 100f),
    fraunces(600, opticalSize = 144f, softness = 100f),
)

val DmSans = FontFamily(
    dmSans(400),
    dmSans(500),
    dmSans(400, FontStyle.Italic),
)

/** Kleine Zeile in Großbuchstaben, z. B. „HEUTE“ oder „FREITAG · 16. OKTOBER“. */
val KickerStyle = TextStyle(
    fontFamily = DmSans,
    fontSize = 11.sp,
    letterSpacing = 0.12.em,
    fontWeight = FontWeight.Medium,
)

val RelicTypography = Typography(
    displaySmall = TextStyle(fontFamily = FrauncesDisplay, fontSize = 31.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = FrauncesText, fontWeight = FontWeight.SemiBold, fontSize = 29.sp),
    headlineSmall = TextStyle(fontFamily = FrauncesDisplay, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = FrauncesText, fontSize = 19.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = DmSans, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = DmSans, fontSize = 14.5.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = DmSans, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = DmSans, fontSize = 12.sp),
)
