package de.riemer87.relic.backup

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Dateinamen der automatischen Sicherungen: `Relic-Backup-2026-10-21_1430.zip`.
 * Relic fasst im Sicherungsordner nur Dateien an, die genau so heißen (oder eine halbe Sicherung
 * `….zip.part` sind) – nie etwas anderes.
 */
object BackupNames {

    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    private val BACKUP = Regex("""Relic-Backup-(\d{4}-\d{2}-\d{2}_\d{4})\.zip""")
    private val PART = Regex("""Relic-Backup-\d{4}-\d{2}-\d{2}_\d{4}\.zip\.part""")

    fun name(at: LocalDateTime): String = "Relic-Backup-${FORMAT.format(at)}.zip"

    /** Name, unter dem eine Sicherung entsteht, bis sie geprüft ist. */
    fun partName(at: LocalDateTime): String = name(at) + ".part"

    /** Zeitpunkt einer fertigen Sicherung, oder `null`, wenn der Name nicht von Relic stammt. */
    fun parse(name: String): LocalDateTime? {
        val match = BACKUP.matchEntire(name) ?: return null
        return try {
            LocalDateTime.parse(match.groupValues[1], FORMAT)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** Übrig gebliebene halbe Sicherung (abgebrochen, z. B. Akku leer). */
    fun isLeftover(name: String): Boolean = PART.matches(name)
}

/**
 * Welche Sicherungen aufgehoben werden („Großvater-Vater-Sohn“), höchstens 5:
 * die 2 neuesten, die letzte aus dem Vormonat, die letzte aus dem Vorquartal und die letzte
 * aus dem Vorjahr – jeweils gemessen am Kalender von [today].
 *
 * Die Staffel rückt von selbst weiter: Die letzte Sicherung eines Monats ist beim Monatswechsel
 * eine der neuesten und wird so zur Vormonats-Sicherung; genauso bei Quartal und Jahr.
 */
object BackupRetention {

    const val NEWEST = 2

    fun keep(backups: Collection<LocalDateTime>, today: LocalDate): Set<LocalDateTime> {
        val sorted = backups.sortedDescending()
        val thisMonth = YearMonth.from(today)
        val quarterStart = thisMonth.withMonth((today.monthValue - 1) / 3 * 3 + 1)

        val keep = sorted.take(NEWEST).toMutableSet()
        sorted.newestIn(thisMonth.minusMonths(1), thisMonth)?.let(keep::add)
        sorted.newestIn(quarterStart.minusMonths(3), quarterStart)?.let(keep::add)
        sorted.newestIn(YearMonth.of(today.year - 1, 1), YearMonth.of(today.year, 1))?.let(keep::add)
        return keep
    }

    /** Die neueste Sicherung ab Anfang von [from] bis vor Anfang von [until]. */
    private fun List<LocalDateTime>.newestIn(from: YearMonth, until: YearMonth): LocalDateTime? {
        val start = from.atDay(1).atStartOfDay()
        val end = until.atDay(1).atStartOfDay()
        return firstOrNull { !it.isBefore(start) && it.isBefore(end) }
    }
}
