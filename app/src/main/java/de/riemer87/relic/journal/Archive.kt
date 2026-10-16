package de.riemer87.relic.journal

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Archiv-Logik ohne Android: welche Monate Einträge haben, welche Einträge in einen Monat gehören. */
object Archive {

    /** Einträge eines Monats, neueste zuerst. */
    fun entriesIn(month: YearMonth, entryDates: Set<LocalDate>): List<LocalDate> =
        entryDates.filter { YearMonth.from(it) == month }.sortedDescending()

    /** Anzahl Einträge je Monat eines Jahres (Monate ohne Einträge fehlen). */
    fun monthCounts(year: Int, entryDates: Set<LocalDate>): Map<Int, Int> =
        entryDates.filter { it.year == year }.groupingBy { it.monthValue }.eachCount()

    /** So viele Jahre vor dem aktuellen stehen immer in der Auswahl, auch ohne Einträge (zum Nachtragen). */
    const val YEARS_BACK = 5

    /**
     * Ältester erreichbarer Monat: Januar vor [YEARS_BACK] Jahren – oder früher, falls es ältere Einträge gibt.
     * Bis hierher reichen Jahresliste und Pfeile, damit beide immer zusammenpassen.
     */
    fun earliestMonth(entryDates: Set<LocalDate>, today: LocalDate): YearMonth {
        val oldestEntryYear = entryDates.filter { !it.isAfter(today) }.minOfOrNull { it.year } ?: today.year
        return YearMonth.of(minOf(oldestEntryYear, today.year - YEARS_BACK), 1)
    }

    /**
     * Jahre für die Auswahl: lückenlos vom aktuellen bis zum ältesten erreichbaren Jahr, neueste zuerst.
     * Die Liste hängt nicht davon ab, welcher Monat gerade gezeigt wird – sie springt also nicht.
     */
    fun years(entryDates: Set<LocalDate>, today: LocalDate): List<Int> =
        (today.year downTo earliestMonth(entryDates, today).year).toList()

    /** Vorwärts blättern nur bis zum aktuellen Monat, nie in die Zukunft. */
    fun canGoForward(month: YearMonth, today: LocalDate): Boolean = month.isBefore(YearMonth.from(today))

    /** Zurück blättern nur bis zum ältesten erreichbaren Monat (siehe [earliestMonth]). */
    fun canGoBack(month: YearMonth, entryDates: Set<LocalDate>, today: LocalDate): Boolean =
        month.isAfter(earliestMonth(entryDates, today))

    /** Ein Monat ist wählbar, wenn er weder in der Zukunft noch vor dem ältesten erreichbaren Monat liegt. */
    fun isSelectable(month: YearMonth, entryDates: Set<LocalDate>, today: LocalDate): Boolean =
        !month.isAfter(YearMonth.from(today)) && !month.isBefore(earliestMonth(entryDates, today))

    /**
     * Monatsraster für die Tagesauswahl: Wochen von Montag bis Sonntag,
     * `null` für leere Felder vor dem 1. und nach dem letzten Tag.
     */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val padded = cells + List((7 - cells.size % 7) % 7) { null }
        return padded.chunked(7)
    }
}
