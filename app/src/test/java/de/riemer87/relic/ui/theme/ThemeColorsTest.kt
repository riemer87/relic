package de.riemer87.relic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {

    private val combinations = listOf(false, true).flatMap { dark -> AccentColor.entries.map { dark to it } }

    /** Kontrastverhältnis nach WCAG: 1 (gleich) bis 21 (Schwarz auf Weiß). */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(what: String, text: Color, background: Color, minimum: Double = 4.5) {
        val ratio = contrast(text, background)
        assertTrue("$what: Kontrast %.2f, nötig %.1f".format(ratio, minimum), ratio >= minimum)
    }

    @Test
    fun inkAccentMeansNoColourAccent() {
        assertEquals(PaperLight, relicColors(dark = false, accent = AccentColor.INK))
        assertEquals(PaperDark, relicColors(dark = true, accent = AccentColor.INK))
    }

    @Test
    fun accentChangesOnlyTheAccent() {
        for ((dark, accent) in combinations) {
            val base = if (dark) PaperDark else PaperLight
            assertEquals("$accent dark=$dark", base, relicColors(dark, accent).copy(accent = base.accent))
        }
    }

    @Test
    fun darkAccentsAreLighterThanLightOnes() {
        for (accent in AccentColor.entries) {
            assertTrue(accent.name, relicColors(true, accent).accent.luminance() > relicColors(false, accent).accent.luminance())
        }
    }

    /**
     * Lesbarkeit in allen 8 Kombinationen: Text und Akzent („HEUTE“, Tageszahl) auf Papier und Blatt,
     * Schrift auf dem Speichern-Knopf, die grüne Erfolgsmeldung und das Bernstein für Laufendes. Mindestens 4,5 : 1 wie für normalen Text üblich.
     */
    @Test
    fun allCombinationsAreReadable() {
        for ((dark, accent) in combinations) {
            val c = relicColors(dark, accent)
            val label = "${if (dark) "Dunkel" else "Hell"}/$accent"
            for ((name, background) in listOf("Papier" to c.paper, "Blatt" to c.sheet)) {
                assertReadable("$label Tinte auf $name", c.ink, background)
                assertReadable("$label Tinte weich auf $name", c.inkSoft, background)
                assertReadable("$label Akzent auf $name", c.accent, background)
                assertReadable("$label Erfolg auf $name", c.success, background)
                assertReadable("$label Läuft auf $name", c.progress, background)
            }
            assertReadable("$label Schrift auf Akzent", c.onAccent, c.accent)
        }
    }

    /** „Tinte blass“ ist absichtlich zart (Platzhalter, Kleingedrucktes), muss aber erkennbar bleiben. */
    @Test
    fun faintInkStaysVisible() {
        for (c in listOf(PaperLight, PaperDark)) {
            assertReadable("Tinte blass", c.inkFaint, c.paper, minimum = 2.3)
        }
    }

    @Test
    fun systemModeFollowsThePhone() {
        assertTrue(Appearance(ThemeMode.SYSTEM).isDark(systemDark = true))
        assertTrue(!Appearance(ThemeMode.SYSTEM).isDark(systemDark = false))
        assertTrue(!Appearance(ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(Appearance(ThemeMode.DARK).isDark(systemDark = false))
    }

    /** Bei der ersten Nutzung: wie das Handy, Tinte (Entscheidung Meilenstein 12). */
    @Test
    fun defaultIsLikeThePhoneWithInk() {
        assertEquals(Appearance(ThemeMode.SYSTEM, AccentColor.INK), Appearance())
    }
}
