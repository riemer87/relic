package de.riemer87.relic.journal

import java.time.LocalDate

/** Wie viele Tage vor heute immer auf „Diese Woche“ stehen, auch ohne Eintrag. */
const val ALWAYS_VISIBLE_PAST_DAYS = 6L

/** Ein Tag auf „Diese Woche“. */
data class TimelineDay(val date: LocalDate, val hasEntry: Boolean)

/** Wie die kleine Zeile über einem Tag heißt. */
enum class DayLabel { TODAY, YESTERDAY, WEEKDAY }

object Timeline {

    /**
     * Die Tage von „Diese Woche“, neueste zuerst: heute und die [ALWAYS_VISIBLE_PAST_DAYS] Tage davor,
     * mit oder ohne Eintrag. Ältere Tage stehen im Archiv.
     */
    fun days(today: LocalDate, entryDates: Set<LocalDate>): List<TimelineDay> =
        (0..ALWAYS_VISIBLE_PAST_DAYS).map { today.minusDays(it) }
            .map { TimelineDay(it, hasEntry = it in entryDates) }

    fun label(date: LocalDate, today: LocalDate): DayLabel = when (date) {
        today -> DayLabel.TODAY
        today.minusDays(1) -> DayLabel.YESTERDAY
        else -> DayLabel.WEEKDAY
    }

    /** Text für die Vorschau: ohne Leerzeilen, damit möglichst viel Inhalt in wenige Zeilen passt. */
    fun preview(text: String): String =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
