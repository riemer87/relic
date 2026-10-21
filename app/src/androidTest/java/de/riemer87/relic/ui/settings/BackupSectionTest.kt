package de.riemer87.relic.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupFolderChoice
import de.riemer87.relic.backup.BackupInfo
import de.riemer87.relic.backup.BackupSettings
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BackupSectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    private val journal = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FRelic".toUri()

    private fun tree(id: String) = "content://com.android.externalstorage.documents/tree/${android.net.Uri.encode(id)}".toUri()

    /** Fehler aus Meilenstein 11: Der Tagebuch-Ordner selbst wurde (scheinbar) als Sicherungsordner angenommen. */
    @Test
    fun journalFolderAndFoldersInsideItAreRejected() {
        assertTrue(BackupSettings.isSameOrInside(tree("primary:Documents/Relic"), journal))
        assertTrue(BackupSettings.isSameOrInside(tree("primary:Documents/Relic/Backups"), journal))
        assertTrue(BackupSettings.isSameOrInside(tree("primary:Documents/Relic/2026/10"), journal))
    }

    @Test
    fun foldersNextToTheJournalAreFine() {
        assertFalse(BackupSettings.isSameOrInside(tree("primary:Documents/Relic-Backups"), journal))
        assertFalse(BackupSettings.isSameOrInside(tree("primary:Documents/Relic2"), journal))
        assertFalse(BackupSettings.isSameOrInside(tree("primary:Documents"), journal))
        val drive = "content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Dencoded%3Dabc".toUri()
        assertFalse(BackupSettings.isSameOrInside(drive, journal))
    }

    /**
     * Fehler aus Meilenstein 11: Die Ablehnung kam nur als kurze Meldung am unteren Rand und ging unter.
     * Jetzt ein Dialog, der bleibt, bis man reagiert – mit direktem neuen Versuch.
     */
    @Test
    fun rejectionIsAClearDialogWithRetry() {
        var retried = 0
        rule.setContent {
            RelicTheme { BackupFolderRejectedDialog(BackupFolderChoice.INSIDE_JOURNAL, onChooseOther = { retried++ }, onDismiss = {}) }
        }
        rule.onNode(hasText(str(R.string.backup_folder_inside_journal_title))).assertExists()
        rule.onNode(hasText(str(R.string.backup_folder_choose_other))).performClick()
        assertEquals(1, retried)
    }

    /** Fehler aus Meilenstein 11: Während Android die erste Sicherung noch nicht gestartet hatte, sah man fast nichts. */
    @Test
    fun waitingBackupIsShown() {
        rule.setContent {
            RelicTheme {
                BackupSection(
                    backup = BackupInfo(enabled = true, folder = tree("primary:Documents/Relic-Backups")),
                    folder = FolderLabel.Parts(FolderLabel.Location.INTERNAL, listOf("Documents", "Relic-Backups")),
                    waiting = true,
                    onEnabledChange = {},
                    onChooseFolder = {},
                    onBackUpNow = {},
                )
            }
        }
        rule.onNode(hasText(str(R.string.settings_backup_waiting))).assertExists()
    }

    @Test
    fun finishedBackupShowsCountAndSize() {
        rule.setContent {
            RelicTheme {
                BackupSection(
                    backup = BackupInfo(enabled = true, newest = LocalDateTime.of(2026, 10, 21, 14, 30), count = 3, totalBytes = 5_000_000),
                    folder = FolderLabel.Parts(FolderLabel.Location.OTHER, listOf("Drive", "Relic-Backups")),
                    waiting = false,
                    onEnabledChange = {},
                    onChooseFolder = {},
                    onBackUpNow = {},
                )
            }
        }
        rule.onNode(hasText("Drive › Relic-Backups")).assertExists()
        rule.onNode(hasText("3 ", substring = true)).assertExists()
    }
}
