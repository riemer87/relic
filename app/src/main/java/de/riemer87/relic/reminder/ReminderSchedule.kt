package de.riemer87.relic.reminder

import java.time.LocalTime
import java.time.ZonedDateTime

/** Einstellungen der täglichen Erinnerung. */
data class ReminderConfig(
    val enabled: Boolean = false,
    val time: LocalTime = DEFAULT_TIME,
) {
    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(21, 0)
    }
}

/** Zeitberechnung der Erinnerung, ohne Android – auf dem PC testbar. */
object ReminderSchedule {

    /**
     * Nächster Zeitpunkt nach [now] zur Uhrzeit [time] in der Zeitzone von [now].
     * Ist die Uhrzeit heute schon vorbei, morgen. Fällt sie in die Lücke der Zeitumstellung
     * (z. B. 2:30 im März), nimmt Java automatisch die nächste gültige Zeit.
     */
    fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    /** Erinnern nur, wenn heute noch nichts geschrieben wurde. */
    fun shouldNotify(hasEntryToday: Boolean): Boolean = !hasEntryToday

    /** Wie auf ein Systemereignis reagiert wird. */
    enum class Reaction {
        /** Nur planen, wenn gar keine Erinnerung geplant ist; eine geplante bleibt. */
        KEEP_IF_PLANNED,

        /** Neu berechnen, weil sich die Uhrzeit verschoben hat. */
        RECOMPUTE,
    }

    /**
     * Neustart, App-Update – und der „Neustart“, den Android meldet, wenn eine zwangsweise beendete
     * App wieder startet – dürfen einen offenen Wecker von heute nie auf morgen verschieben
     * (Fehler aus Meilenstein 6). Nur Zeit- oder Zeitzonenwechsel erfordern eine Neuberechnung.
     */
    fun reactionTo(action: String?): Reaction = when (action) {
        "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED" -> Reaction.RECOMPUTE
        else -> Reaction.KEEP_IF_PLANNED
    }
}
