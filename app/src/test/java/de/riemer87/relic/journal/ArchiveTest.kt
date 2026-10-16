package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ArchiveTest {

    private val today = LocalDate.of(2026, 10, 16)
    private fun d(iso: String) = LocalDate.parse(iso)

    private val entries = setOf(
        d("2026-10-12"), d("2026-10-13"), d("2026-10-16"),
        d("2026-09-01"), d("2026-09-29"), d("2026-09-27"),
        d("2025-12-24"), d("2024-02-29"),
    )

    // --- Einträge eines Monats ---

    @Test
    fun `entries of a month come newest first`() {
        assertEquals(
            listOf(d("2026-09-29"), d("2026-09-27"), d("2026-09-01")),
            Archive.entriesIn(YearMonth.of(2026, 9), entries),
        )
    }

    @Test
    fun `month without entries is empty`() {
        assertEquals(emptyList<LocalDate>(), Archive.entriesIn(YearMonth.of(2026, 8), entries))
    }

    @Test
    fun `same month of another year does not mix in`() {
        assertEquals(listOf(d("2025-12-24")), Archive.entriesIn(YearMonth.of(2025, 12), entries))
    }

    @Test
    fun `counts per month of a year`() {
        assertEquals(mapOf(10 to 3, 9 to 3), Archive.monthCounts(2026, entries))
        assertEquals(mapOf(2 to 1), Archive.monthCounts(2024, entries))
        assertEquals(emptyMap<Int, Int>(), Archive.monthCounts(2023, entries))
    }

    // --- Jahre und Blättern ---

    @Test
    fun `years reach five years back without gaps newest first`() {
        assertEquals(listOf(2026, 2025, 2024, 2023, 2022, 2021), Archive.years(entries, today))
        assertEquals(listOf(2026, 2025, 2024, 2023, 2022, 2021), Archive.years(emptySet(), today))
    }

    @Test
    fun `older entries extend the years further back`() {
        val withOld = entries + d("2015-06-01")
        assertEquals((2026 downTo 2015).toList(), Archive.years(withOld, today))
        assertEquals(YearMonth.of(2015, 1), Archive.earliestMonth(withOld, today))
    }

    @Test
    fun `future years never appear`() {
        assertEquals(2026, Archive.years(setOf(d("2027-01-01")), today).first())
    }

    /** Fehler aus Meilenstein 5: Das gerade gezeigte Jahr tauchte nur vorübergehend in der Liste auf. */
    @Test
    fun `years do not depend on the shown month`() {
        // Die Liste hat keinen Parameter für den gezeigten Monat mehr – sie ist für jeden Monat dieselbe.
        val before = Archive.years(entries, today)
        assertTrue(2025 in before)
        assertEquals(before, Archive.years(entries, today))
    }

    @Test
    fun `forward only up to the current month`() {
        assertTrue(Archive.canGoForward(YearMonth.of(2026, 9), today))
        assertFalse(Archive.canGoForward(YearMonth.of(2026, 10), today))
        assertTrue(Archive.canGoForward(YearMonth.of(2025, 12), today))
        assertTrue(Archive.isSelectable(YearMonth.of(2026, 10), entries, today))
        assertFalse(Archive.isSelectable(YearMonth.of(2026, 11), entries, today))
    }

    @Test
    fun `back only down to january of the oldest listed year`() {
        assertTrue(Archive.canGoBack(YearMonth.of(2026, 1), entries, today))
        assertTrue(Archive.canGoBack(YearMonth.of(2021, 2), entries, today))
        assertFalse(Archive.canGoBack(YearMonth.of(2021, 1), entries, today))
        assertTrue(Archive.isSelectable(YearMonth.of(2021, 1), entries, today))
        assertFalse(Archive.isSelectable(YearMonth.of(2020, 12), entries, today))
    }

    // --- Monatsraster der Tagesauswahl ---

    private fun allDays(weeks: List<List<LocalDate?>>) = weeks.flatten().filterNotNull()

    @Test
    fun `weeks start on monday and have seven fields`() {
        val weeks = Archive.weeks(YearMonth.of(2026, 10)) // 1. Oktober 2026 ist ein Donnerstag
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(listOf(null, null, null), weeks.first().take(3))
        assertEquals(d("2026-10-01"), weeks.first()[3])
        weeks.flatten().filterNotNull().forEachIndexed { i, day -> assertEquals(i + 1, day.dayOfMonth) }
        weeks.forEach { week ->
            week.forEachIndexed { col, day -> if (day != null) assertEquals(DayOfWeek.of(col + 1), day.dayOfWeek) }
        }
    }

    @Test
    fun `month lengths 28 to 31 days are complete`() {
        assertEquals(31, allDays(Archive.weeks(YearMonth.of(2026, 10))).size)
        assertEquals(30, allDays(Archive.weeks(YearMonth.of(2026, 9))).size)
        assertEquals(28, allDays(Archive.weeks(YearMonth.of(2026, 2))).size)
    }

    @Test
    fun `leap year february has 29 days`() {
        assertEquals(29, allDays(Archive.weeks(YearMonth.of(2028, 2))).size)
        assertEquals(d("2028-02-29"), allDays(Archive.weeks(YearMonth.of(2028, 2))).last())
    }

    @Test
    fun `february starting on monday fits exactly four weeks`() {
        val weeks = Archive.weeks(YearMonth.of(2027, 2)) // 1. Februar 2027 ist ein Montag
        assertEquals(4, weeks.size)
        assertEquals(d("2027-02-01"), weeks.first().first())
        assertEquals(d("2027-02-28"), weeks.last().last())
    }

    @Test
    fun `month starting on sunday has six leading blanks`() {
        val weeks = Archive.weeks(YearMonth.of(2026, 11)) // 1. November 2026 ist ein Sonntag
        assertEquals(6, weeks.first().count { it == null })
        assertEquals(d("2026-11-01"), weeks.first().last())
        assertNull(weeks.last().last())
    }
}
