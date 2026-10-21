package de.riemer87.relic.backup

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Ruhiger Hinweis auf „Diese Woche“, dass das Tagebuch gesichert werden sollte. */
sealed interface BackupHint {
    /** Automatische Sicherung aus, und lange (oder noch nie) gesichert. @param days `null`: noch nie */
    data class Overdue(val days: Long?) : BackupHint

    /** Automatische Sicherung an, klappt aber gerade nicht (Ordner weg, wiederholter Fehler). */
    data object NotWorking : BackupHint
}

/**
 * Wann Relic an eine Sicherung erinnert. Ist alles in Ordnung, gibt es keinen Hinweis.
 *
 * - Automatische Sicherung **an**: nur, wenn sie nicht klappt. Ihr Alter zählt nicht – ohne Änderungen
 *   entsteht absichtlich keine neue Sicherung.
 * - Automatische Sicherung **aus**: wenn die letzte Sicherung (Export oder frühere automatische) mindestens
 *   [OVERDUE_DAYS] Tage her ist oder es noch keine gibt – aber nur, wenn es überhaupt Einträge gibt.
 */
object BackupReminder {

    const val OVERDUE_DAYS = 30L

    fun hint(
        autoBackupOn: Boolean,
        problem: BackupProblem?,
        lastSaved: LocalDate?,
        hasEntries: Boolean,
        today: LocalDate,
    ): BackupHint? {
        if (autoBackupOn) {
            // Tagebuch-Ordner weg: Dafür zeigt Relic ohnehin den Ordner-Bildschirm.
            val broken = problem != null && problem != BackupProblem.JOURNAL_MISSING
            return if (broken) BackupHint.NotWorking else null
        }
        if (!hasEntries) return null
        if (lastSaved == null) return BackupHint.Overdue(days = null)
        val days = ChronoUnit.DAYS.between(lastSaved, today)
        return if (days >= OVERDUE_DAYS) BackupHint.Overdue(days) else null
    }
}
