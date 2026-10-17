package de.riemer87.relic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Die benannten Farben von Relic („Tinte auf Papier“, siehe design/papierlook.html).
 * Alle Bildschirme nehmen ihre Farben nur von hier. So lassen sich Dunkelmodus und
 * Akzentfarben (Meilenstein 12) später ergänzen, ohne Bildschirme umzubauen.
 */
@Immutable
data class RelicColors(
    /** Hintergrund der Seiten */
    val paper: Color,
    /** Blätter im Zeitstrahl und auf der Eintragsseite, etwas heller als das Papier */
    val sheet: Color,
    /** Haupttext */
    val ink: Color,
    /** Nebentext */
    val inkSoft: Color,
    /** Platzhalter, Hinweise, Kleingedrucktes */
    val inkFaint: Color,
    /** Zeitstrahl-Linie, gestrichelte Ränder, Linie unter dem Textfeld */
    val rule: Color,
    /** Punkt für heute, Zeile „Heute“, Speichern-Knopf. Standard: wie Tinte. */
    val accent: Color,
    /** Schrift auf dem Akzent */
    val onAccent: Color,
    /** Erfolgsmeldungen, z. B. „Export gespeichert“ (gedecktes Grün, gut lesbar auf Papier und Blatt) */
    val success: Color,
)

val PaperLight = RelicColors(
    paper = Color(0xFFEFE9DF),
    sheet = Color(0xFFF8F4EC),
    ink = Color(0xFF24221E),
    inkSoft = Color(0xFF5F5A51),
    inkFaint = Color(0xFF9B9488),
    rule = Color(0xFFC9C0B2),
    accent = Color(0xFF24221E),
    onAccent = Color(0xFFF8F4EC),
    success = Color(0xFF3A6B45),
)

/** Dunkelmodus: Tinte auf Papier umgekehrt (Werte aus design/papierlook.html). */
val PaperDark = RelicColors(
    paper = Color(0xFF1D1C1A),
    sheet = Color(0xFF272522),
    ink = Color(0xFFECE6DB),
    inkSoft = Color(0xFFB5AEA2),
    inkFaint = Color(0xFF7F796F),
    rule = Color(0xFF47433D),
    accent = Color(0xFFECE6DB),
    onAccent = Color(0xFF1D1C1A),
    success = Color(0xFF8FC29A),
)

/**
 * Die wählbaren Akzentfarben, je mit einer Variante für hell und dunkel.
 * `null` = Tinte (kein Farbakzent, Standard). Salbei und Ocker sind gegenüber der Vorlage
 * in Hell etwas dunkler, damit „HEUTE“ und der Speichern-Knopf gut lesbar bleiben.
 */
enum class AccentColor(internal val light: Color?, internal val dark: Color?) {
    INK(null, null),
    SAGE(Color(0xFF4E6A4B), Color(0xFF9FBB98)),
    PLUM(Color(0xFF7D4F6D), Color(0xFFCF9DBD)),
    OCHRE(Color(0xFF8A6118), Color(0xFFDCAE5A)),
}

/** Die Farben für Hell oder Dunkel mit der gewählten Akzentfarbe. */
fun relicColors(dark: Boolean, accent: AccentColor): RelicColors {
    val base = if (dark) PaperDark else PaperLight
    val color = (if (dark) accent.dark else accent.light) ?: return base
    return base.copy(accent = color)
}

val LocalRelicColors = staticCompositionLocalOf { PaperLight }
