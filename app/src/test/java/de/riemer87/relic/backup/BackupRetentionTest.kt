package de.riemer87.relic.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BackupRetentionTest {

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text + "T12:00")

    /** Wöchentliche Sicherungen (jeden Mittwoch) von [from] bis einschließlich [until]. */
    private fun weekly(from: String, until: String): List<LocalDateTime> =
        generateSequence(at(from)) { it.plusWeeks(1) }.takeWhile { !it.toLocalDate().isAfter(LocalDate.parse(until)) }.toList()

    @Test
    fun `names round-trip and only Relic backups are recognised`() {
        val time = LocalDateTime.of(2026, 10, 21, 14, 30)
        assertEquals("Relic-Backup-2026-10-21_1430.zip", BackupNames.name(time))
        assertEquals(time, BackupNames.parse("Relic-Backup-2026-10-21_1430.zip"))
        assertNull(BackupNames.parse("Relic-Backup-2026-10-21_1430.zip.part"))
        assertNull(BackupNames.parse("Relic-Backup-2026-13-45_9999.zip"))
        assertNull(BackupNames.parse("Relic-Export-2026-10-21.zip"))
        assertNull(BackupNames.parse("Urlaub.zip"))
        assertNull(BackupNames.parse("relic-backup-2026-10-21_1430.zip"))
        assertTrue(BackupNames.isLeftover("Relic-Backup-2026-10-21_1430.zip.part"))
        assertTrue(!BackupNames.isLeftover("Notizen.zip.part"))
    }

    @Test
    fun `first backup is kept`() {
        val one = at("2026-10-21")
        assertEquals(setOf(one), BackupRetention.keep(listOf(one), LocalDate.parse("2026-10-21")))
    }

    @Test
    fun `nothing to keep when there is nothing`() {
        assertEquals(emptySet<LocalDateTime>(), BackupRetention.keep(emptyList(), LocalDate.parse("2026-10-21")))
    }

    @Test
    fun `example from the plan - two newest, last of previous month, quarter and year`() {
        val backups = weekly("2025-06-04", "2026-10-21")
        val keep = BackupRetention.keep(backups, LocalDate.parse("2026-10-21"))
        assertEquals(
            setOf(
                at("2026-10-21"), at("2026-10-14"), // die 2 neuesten
                at("2026-09-30"), // letzte im September: Vormonat und zugleich Vorquartal (Jul–Sep)
                at("2025-12-31"), // letzte im Vorjahr
            ),
            keep,
        )
    }

    @Test
    fun `previous quarter is separate when the month is the first of a quarter`() {
        // Im November ist der Vormonat Oktober, das Vorquartal Jul–Sep.
        val backups = weekly("2025-06-04", "2026-11-18")
        val keep = BackupRetention.keep(backups, LocalDate.parse("2026-11-18"))
        assertEquals(
            setOf(at("2026-11-18"), at("2026-11-11"), at("2026-10-28"), at("2026-09-30"), at("2025-12-31")),
            keep,
        )
    }

    @Test
    fun `never more than five`() {
        var day = LocalDate.parse("2024-01-03")
        val backups = mutableListOf<LocalDateTime>()
        while (day.isBefore(LocalDate.parse("2027-03-01"))) {
            backups += day.atTime(12, 0)
            assertTrue("$day", BackupRetention.keep(backups, day).size <= 5)
            day = day.plusWeeks(1)
        }
    }

    /**
     * Simuliert drei Jahre wöchentlicher Sicherungen mit Aufräumen nach jeder neuen.
     * Die Staffel muss sich selbst tragen: Jederzeit gibt es (sobald möglich) eine Sicherung
     * aus dem Vormonat, dem Vorquartal und dem Vorjahr – obwohl nie mehr als 5 Dateien liegen.
     */
    @Test
    fun `the staircase carries itself forward over years`() {
        val kept = mutableSetOf<LocalDateTime>()
        var day = LocalDate.parse("2024-01-03")
        while (day.isBefore(LocalDate.parse("2027-01-01"))) {
            kept += day.atTime(12, 0)
            kept.retainAll(BackupRetention.keep(kept, day))
            assertTrue(kept.size <= 5)

            if (day.isAfter(LocalDate.parse("2025-02-01"))) {
                val lastYear = day.year - 1
                assertTrue("$day: Sicherung aus $lastYear fehlt", kept.any { it.year == lastYear })
                val previousMonth = day.minusMonths(1)
                assertTrue(
                    "$day: Sicherung aus dem Vormonat fehlt",
                    kept.any { it.year == previousMonth.year && it.month == previousMonth.month },
                )
                val quarterStart = LocalDate.of(day.year, (day.monthValue - 1) / 3 * 3 + 1, 1)
                val previousQuarterStart = quarterStart.minusMonths(3)
                assertTrue(
                    "$day: Sicherung aus dem Vorquartal fehlt",
                    kept.any { !it.toLocalDate().isBefore(previousQuarterStart) && it.toLocalDate().isBefore(quarterStart) },
                )
            }
            day = day.plusWeeks(1)
        }
    }

    @Test
    fun `gaps - long pause keeps the newest ones and the old year`() {
        // Ein halbes Jahr keine Sicherung (z. B. ausgeschaltet), dann wieder eine.
        val backups = listOf(at("2025-11-05"), at("2025-12-10"), at("2026-03-04"), at("2026-10-21"))
        val keep = BackupRetention.keep(backups, LocalDate.parse("2026-10-21"))
        // 2 neueste: 21.10. und 04.03.; Vormonat (Sep) und Vorquartal (Jul–Sep): keine; Vorjahr: 10.12.2025.
        assertEquals(setOf(at("2026-10-21"), at("2026-03-04"), at("2025-12-10")), keep)
    }

    @Test
    fun `several backups on one day count as separate ones`() {
        val morning = LocalDateTime.parse("2026-10-21T08:00")
        val noon = LocalDateTime.parse("2026-10-21T12:00")
        val evening = LocalDateTime.parse("2026-10-21T20:00")
        assertEquals(setOf(evening, noon), BackupRetention.keep(listOf(morning, noon, evening), LocalDate.parse("2026-10-21")))
    }

    @Test
    fun `year change - last December backup is previous year, month and quarter at once`() {
        val backups = weekly("2026-10-07", "2027-01-06")
        val keep = BackupRetention.keep(backups, LocalDate.parse("2027-01-06"))
        assertEquals(setOf(at("2027-01-06"), at("2026-12-30")), keep)
    }
}
