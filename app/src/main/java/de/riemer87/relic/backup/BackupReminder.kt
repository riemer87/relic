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
 *   [OVERDUE_DAYS] Tage her ist, oder wenn es noch keine gibt und der früheste Eintrag mindestens
 *   [FIRST_HINT_AFTER_DAYS] Tage alt ist (wer neu anfängt, wird nicht gleich am ersten Tag gemahnt).
 */
object BackupReminder {

    const val OVERDUE_DAYS = 30L
    const val FIRST_HINT_AFTER_DAYS = 7L

    fun hint(
        autoBackupOn: Boolean,
        problem: BackupProblem?,
        lastSaved: LocalDate?,
        firstEntry: LocalDate?,
        today: LocalDate,
    ): BackupHint? {
        if (autoBackupOn) {
            // Tagebuch-Ordner weg: Dafür zeigt Relic ohnehin den Ordner-Bildschirm.
            val broken = problem != null && problem != BackupProblem.JOURNAL_MISSING
            return if (broken) BackupHint.NotWorking else null
        }
        if (firstEntry == null) return null // Noch nichts geschrieben: nichts zu sichern.
        if (lastSaved == null) {
            val journalAge = ChronoUnit.DAYS.between(firstEntry, today)
            return if (journalAge >= FIRST_HINT_AFTER_DAYS) BackupHint.Overdue(days = null) else null
        }
        val days = ChronoUnit.DAYS.between(lastSaved, today)
        return if (days >= OVERDUE_DAYS) BackupHint.Overdue(days) else null
    }
}
