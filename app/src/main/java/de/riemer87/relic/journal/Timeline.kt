package de.riemer87.relic.journal

import java.time.LocalDate

/** Wie viele Tage vor heute immer im Zeitstrahl stehen, auch ohne Eintrag. */
const val ALWAYS_VISIBLE_PAST_DAYS = 7L

/** Ein Tag im Zeitstrahl. */
data class TimelineDay(val date: LocalDate, val hasEntry: Boolean)

/** Wie die kleine Zeile über einem Tag heißt. */
enum class DayLabel { TODAY, YESTERDAY, WEEKDAY }

object Timeline {

    /**
     * Die Tage des Zeitstrahls, neueste zuerst:
     * heute und die [ALWAYS_VISIBLE_PAST_DAYS] Tage davor immer, ältere nur mit Eintrag,
     * zukünftige nie.
     */
    fun days(today: LocalDate, entryDates: Set<LocalDate>): List<TimelineDay> {
        val recent = (0..ALWAYS_VISIBLE_PAST_DAYS).map { today.minusDays(it) }
        return (recent + entryDates.filter { !it.isAfter(today) })
            .distinct()
            .sortedDescending()
            .map { TimelineDay(it, hasEntry = it in entryDates) }
    }

    fun label(date: LocalDate, today: LocalDate): DayLabel = when (date) {
        today -> DayLabel.TODAY
        today.minusDays(1) -> DayLabel.YESTERDAY
        else -> DayLabel.WEEKDAY
    }

    /** Text für die Vorschau: ohne Leerzeilen, damit möglichst viel Inhalt in wenige Zeilen passt. */
    fun preview(text: String): String =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
