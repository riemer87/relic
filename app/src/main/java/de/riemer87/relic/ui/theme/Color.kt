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
)

val LocalRelicColors = staticCompositionLocalOf { PaperLight }
