package de.riemer87.relic.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryRulesTest {

    private val today = LocalDate.of(2026, 10, 16)
    private val yesterday = today.minusDays(1)

    @Test
    fun `today is always editable`() {
        assertEquals(EntryMode.EDIT, EntryRules.initialMode(today, today, savedText = ""))
        assertEquals(EntryMode.EDIT, EntryRules.initialMode(today, today, savedText = "Schon was da"))
    }

    @Test
    fun `past day with text opens read only`() {
        assertEquals(EntryMode.READ, EntryRules.initialMode(yesterday, today, savedText = "Spaziergang"))
    }

    @Test
    fun `empty past day opens for writing`() {
        assertEquals(EntryMode.EDIT, EntryRules.initialMode(yesterday, today, savedText = ""))
        assertEquals(EntryMode.EDIT, EntryRules.initialMode(yesterday, today, savedText = "  \n "))
    }

    @Test
    fun `unchanged text is not a change`() {
        assertFalse(EntryRules.hasChanges("Text", "Text"))
    }

    @Test
    fun `only whitespace at the edges is not a change`() {
        assertFalse(EntryRules.hasChanges("Text\n\n", "Text"))
        assertFalse(EntryRules.hasChanges("  Text", "Text"))
        assertFalse(EntryRules.hasChanges("Zeile 1\r\nZeile 2", "Zeile 1\nZeile 2"))
    }

    @Test
    fun `real edits are changes`() {
        assertTrue(EntryRules.hasChanges("Text!", "Text"))
        assertTrue(EntryRules.hasChanges("", "Text"))
        assertTrue(EntryRules.hasChanges("Neu", ""))
    }
}
