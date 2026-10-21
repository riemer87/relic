package de.riemer87.relic.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupFolderChoice
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.theme.Relic

/** Startpunkt der Ordnerauswahl für Sicherungen: „Dokumente“. */
private val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri(FolderLabel.EXTERNAL_STORAGE_AUTHORITY, "primary:Documents")

/**
 * Sicherungsordner wählen – gleich in Einstellungen und Einführung: erst ein kurzer Hinweis, was in der
 * Android-Auswahl anzutippen ist, dann die Ordnerauswahl, dann die Prüfung. Passt der Ordner nicht
 * (z. B. der Tagebuch-Ordner), erscheint ein Dialog mit direktem neuen Versuch.
 *
 * @param choose übernimmt den gewählten Ordner (siehe [de.riemer87.relic.backup.BackupSetup.chooseFolder])
 * @param onChosen der Ordner wurde übernommen
 * @return startet die Wahl
 */
@Composable
fun rememberBackupFolderChooser(choose: (Uri) -> BackupFolderChoice, onChosen: () -> Unit = {}): () -> Unit {
    var hintOpen by rememberSaveable { mutableStateOf(false) }
    // Abgelehnter Ordner: als Dialog, damit man es nicht übersieht (eine kurze Meldung unten ging unter).
    var rejected by rememberSaveable { mutableStateOf<BackupFolderChoice?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val choice = choose(uri)
        if (choice == BackupFolderChoice.OK) onChosen() else rejected = choice
    }

    if (hintOpen) {
        BackupFolderHintDialog(
            onContinue = {
                hintOpen = false
                picker.launch(DOCUMENTS)
            },
            onDismiss = { hintOpen = false },
        )
    }
    rejected?.let { choice ->
        BackupFolderRejectedDialog(
            choice = choice,
            onChooseOther = {
                rejected = null
                picker.launch(DOCUMENTS)
            },
            onDismiss = { rejected = null },
        )
    }
    return { hintOpen = true }
}

/** Kurzer Hinweis vor der Android-Ordnerauswahl. */
@Composable
private fun BackupFolderHintDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val colors = Relic.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_folder_hint_title)) },
        text = { Text(stringResource(R.string.backup_folder_hint), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.backup_folder_continue), color = colors.ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.ink) }
        },
        containerColor = colors.sheet,
    )
}
