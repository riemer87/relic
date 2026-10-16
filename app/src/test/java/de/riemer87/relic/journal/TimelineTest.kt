package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class TimelineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val today = LocalDate.of(2026, 10, 16)

    private fun d(iso: String) = LocalDate.parse(iso)

    // --- Welche Tage erscheinen ---

    @Test
    fun `timeline shows today and the six days before`() {
        val days = Timeline.days(today, emptySet())
        assertEquals(7, days.size)
        assertEquals(today, days.first().date)
        assertEquals(d("2026-10-10"), days.last().date)
        assertTrue(days.none { it.hasEntry })
    }

    @Test
    fun `newest day comes first`() {
        val days = Timeline.days(today, setOf(d("2026-09-01"), d("2026-10-14"), d("2025-12-24")))
        assertEquals(days.map { it.date }.sortedDescending(), days.map { it.date })
    }

    @Test
    fun `older entries are left to the archive`() {
        val days = Timeline.days(today, setOf(d("2026-09-01"), d("2026-10-09")))
        assertEquals(7, days.size)
        assertFalse(days.any { it.date == d("2026-09-01") || it.date == d("2026-10-09") })
    }

    @Test
    fun `recent days are marked as written or empty`() {
        val days = Timeline.days(today, setOf(d("2026-10-14")))
        assertTrue(days.single { it.date == d("2026-10-14") }.hasEntry)
        assertFalse(days.single { it.date == d("2026-10-15") }.hasEntry)
    }

    @Test
    fun `future entries never appear`() {
        val days = Timeline.days(today, setOf(d("2026-10-17"), d("2027-01-01")))
        assertEquals(today, days.first().date)
        assertEquals(7, days.size)
    }

    @Test
    fun `the week works across the turn of the year`() {
        val days = Timeline.days(d("2026-01-03"), setOf(d("2025-12-30")))
        assertEquals(
            listOf("2026-01-03", "2026-01-02", "2026-01-01", "2025-12-31", "2025-12-30", "2025-12-29", "2025-12-28"),
            days.map { it.date.toString() },
        )
        assertTrue(days.single { it.date == d("2025-12-30") }.hasEntry)
    }

    @Test
    fun `leap day is handled`() {
        val days = Timeline.days(d("2028-03-02"), emptySet())
        assertTrue(days.any { it.date == d("2028-02-29") })
    }

    // --- Beschriftung ---

    @Test
    fun `labels today yesterday and weekdays`() {
        assertEquals(DayLabel.TODAY, Timeline.label(today, today))
        assertEquals(DayLabel.YESTERDAY, Timeline.label(d("2026-10-15"), today))
        assertEquals(DayLabel.WEEKDAY, Timeline.label(d("2026-10-14"), today))
        assertEquals(DayLabel.YESTERDAY, Timeline.label(d("2025-12-31"), d("2026-01-01")))
    }

    // --- Vorschau ---

    @Test
    fun `preview drops blank lines and surrounding spaces`() {
        assertEquals(
            "Erster Absatz\nZweiter Absatz\n- Punkt",
            Timeline.preview("\n  Erster Absatz  \n\n\nZweiter Absatz\n\n- Punkt\n"),
        )
    }

    @Test
    fun `preview of empty text is empty`() {
        assertEquals("", Timeline.preview("  \n\n "))
    }

    // --- Einträge im Ordner finden ---

    private fun file(path: String) = File(folder.root, path).apply { parentFile!!.mkdirs(); writeText("x") }

    private fun entryDates() = TreeJournalStore(FileDocumentTree(folder.root)).entryDates()

    @Test
    fun `finds entries in year and month folders`() {
        file("2026/10/2026-10-14.md")
        file("2026/09/2026-09-01.md")
        file("2025/12/2025-12-24.md")
        assertEquals(setOf(d("2026-10-14"), d("2026-09-01"), d("2025-12-24")), entryDates())
    }

    @Test
    fun `ignores files that are not entries`() {
        file("LIES MICH.txt")
        file("2026/10/notizen.md")
        file("2026/10/2026-10-14_1.jpg")
        file("2026/10/2026-10-14.txt")
        file("2026/Urlaub/2026-10-14.md")
        file("Archiv/2026/10/2026-10-14.md")
        assertEquals(emptySet<LocalDate>(), entryDates())
    }

    @Test
    fun `ignores entries in the wrong folder and impossible dates`() {
        file("2026/11/2026-10-14.md")
        file("2026/02/2026-02-30.md")
        assertEquals(emptySet<LocalDate>(), entryDates())
    }

    @Test
    fun `leftover temporary file counts as entry`() {
        file("2026/10/2026-10-15.md.tmp")
        assertEquals(setOf(d("2026-10-15")), entryDates())
    }

    @Test
    fun `empty or missing journal has no entries`() {
        assertEquals(emptySet<LocalDate>(), entryDates())
    }
}
