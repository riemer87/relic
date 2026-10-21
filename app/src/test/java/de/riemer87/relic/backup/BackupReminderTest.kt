package de.riemer87.relic.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupReminderTest {

    private val today = LocalDate.parse("2026-10-21")

    private fun hint(
        autoBackupOn: Boolean = false,
        problem: BackupProblem? = null,
        lastSaved: LocalDate? = null,
        hasEntries: Boolean = true,
    ) = BackupReminder.hint(autoBackupOn, problem, lastSaved, hasEntries, today)

    @Test
    fun `recent export - no hint`() {
        assertNull(hint(lastSaved = today.minusDays(29)))
    }

    @Test
    fun `thirty days without backup - hint with the number of days`() {
        assertEquals(BackupHint.Overdue(30), hint(lastSaved = today.minusDays(30)))
        assertEquals(BackupHint.Overdue(34), hint(lastSaved = today.minusDays(34)))
    }

    @Test
    fun `never backed up - hint without days`() {
        assertEquals(BackupHint.Overdue(null), hint(lastSaved = null))
    }

    @Test
    fun `empty journal - nothing to back up, no hint`() {
        assertNull(hint(lastSaved = null, hasEntries = false))
    }

    @Test
    fun `automatic backup on and working - no hint, even if the last backup is old`() {
        // Ohne Änderungen entsteht absichtlich keine neue Sicherung; das ist kein Grund zur Sorge.
        assertNull(hint(autoBackupOn = true, lastSaved = today.minusDays(200)))
        assertNull(hint(autoBackupOn = true, lastSaved = null))
    }

    @Test
    fun `automatic backup on but broken - hint`() {
        assertEquals(BackupHint.NotWorking, hint(autoBackupOn = true, problem = BackupProblem.FOLDER_MISSING))
        assertEquals(BackupHint.NotWorking, hint(autoBackupOn = true, problem = BackupProblem.FAILED))
        assertEquals(BackupHint.NotWorking, hint(autoBackupOn = true, problem = BackupProblem.FOLDER_IN_JOURNAL))
    }

    @Test
    fun `journal folder missing - the folder screen shows that already`() {
        assertNull(hint(autoBackupOn = true, problem = BackupProblem.JOURNAL_MISSING))
    }

    @Test
    fun `old problem does not matter while automatic backup is off`() {
        assertNull(hint(autoBackupOn = false, problem = BackupProblem.FAILED, lastSaved = today.minusDays(3)))
    }
}
