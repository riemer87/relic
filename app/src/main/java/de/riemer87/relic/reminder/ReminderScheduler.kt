package de.riemer87.relic.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Stellt den „Wecker“ für die nächste Erinnerung. Bewusst etwas ungenau (bis zu 2 Minuten Spielraum):
 * Dafür braucht die App keine Sonder-Berechtigung, und Android kann Akku sparen.
 * Nach jeder Erinnerung wird die nächste neu geplant – so stimmt die Uhrzeit auch nach Zeitumstellung.
 */
class ReminderScheduler(private val context: Context, private val clock: Clock = Clock.systemDefaultZone()) {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminder_schedule", Context.MODE_PRIVATE)

    /**
     * Plant die nächste Erinnerung neu – oder entfernt sie, wenn ausgeschaltet.
     * Für Änderungen in den Einstellungen, nach einer Erinnerung und nach Zeit-/Zeitzonenwechsel.
     */
    fun apply(config: ReminderConfig) {
        if (!config.enabled) {
            pendingIntent().let {
                alarms.cancel(it)
                it.cancel()
            }
            prefs.edit { remove(KEY_NEXT) }
            return
        }
        val next = ReminderSchedule.nextTrigger(ZonedDateTime.now(clock), config.time).toInstant()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), WINDOW_MILLIS, pendingIntent())
        prefs.edit { putLong(KEY_NEXT, next.toEpochMilli()) }
    }

    /**
     * Sorgt nur dafür, dass überhaupt eine Erinnerung geplant ist (z. B. beim Öffnen der App).
     * Eine schon geplante bleibt unangetastet – sonst würde ein noch offener Wecker von heute,
     * dessen Uhrzeit gerade vorbei ist, auf morgen verschoben (Fehler aus Meilenstein 6).
     */
    fun ensureScheduled(config: ReminderConfig) {
        if (config.enabled && isScheduled()) return
        apply(config)
    }

    /** `true`, wenn gerade eine Erinnerung geplant ist. */
    fun isScheduled(): Boolean = PendingIntent.getBroadcast(
        context, REQUEST_CODE, intent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    /** Zeitpunkt der zuletzt geplanten Erinnerung (zur Kontrolle und für Tests). */
    fun plannedFor(): Instant? = prefs.getLong(KEY_NEXT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    private fun intent() = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 21
        const val WINDOW_MILLIS = 2 * 60 * 1000L
        const val KEY_NEXT = "next"
    }
}
