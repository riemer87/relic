package de.riemer87.relic.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupInfo
import de.riemer87.relic.backup.BackupProblem
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.Relic
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Einstellungen › Automatische Sicherung: Schalter, Ordner, Stand der letzten Sicherung,
 * „Jetzt sichern“ und „Ordner ändern“.
 */
@Composable
internal fun BackupSection(
    backup: BackupInfo,
    folder: FolderLabel.Parts?,
    waiting: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onBackUpNow: () -> Unit,
) {
    val colors = Relic.colors
    Section(title = stringResource(R.string.settings_backup)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_backup_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(6.dp))
            RelicSwitch(checked = backup.enabled, onCheckedChange = onEnabledChange)
        }
        if (!backup.enabled) return@Section

        Text(folderText(folder), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
        val problem = backup.problem
        when {
            backup.running -> InProgress(stringResource(R.string.settings_backup_running))
            waiting -> InProgress(stringResource(R.string.settings_backup_waiting))
            problem != null -> Text(problemText(problem), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            else -> BackupStatus(backup)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackUpNow, enabled = !backup.running, shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.settings_backup_now), color = if (backup.running) colors.inkFaint else colors.ink)
            }
            TextButton(onClick = onChooseFolder) {
                Text(stringResource(R.string.settings_backup_change_folder), color = colors.ink)
            }
        }
        Text(stringResource(R.string.settings_backup_keep), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
    }
}

/** Läuft gerade oder startet gleich: Bernstein-Punkt und Text in der Farbe „Läuft“. */
@Composable
private fun InProgress(text: String) {
    val colors = Relic.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(horizontal = 5.dp).size(8.dp).background(colors.progress, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.progress)
    }
}

/** Fertig: grüner Haken, „Letzte Sicherung: 21. Oktober 2026, 14:30“ und „5 Sicherungen · 1,2 GB“ in Grün. */
@Composable
private fun BackupStatus(backup: BackupInfo) {
    val colors = Relic.colors
    val newest = backup.newest
    if (newest == null) {
        Text(stringResource(R.string.settings_backup_none), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
        return
    }
    val locale = currentLocale()
    val date = remember(newest, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(newest) }
    val context = LocalContext.current
    val size = remember(backup.totalBytes) { Formatter.formatShortFileSize(context, backup.totalBytes) }
    Row {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                stringResource(R.string.settings_backup_last, date, formatTime(newest.toLocalTime())),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.success,
            )
            Text(
                pluralStringResource(R.plurals.settings_backup_count, backup.count, backup.count, size),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.success,
            )
        }
    }
}

@Composable
private fun problemText(problem: BackupProblem): String = stringResource(
    when (problem) {
        BackupProblem.FOLDER_MISSING -> R.string.settings_backup_problem_folder
        BackupProblem.JOURNAL_MISSING -> R.string.settings_backup_problem_journal
        BackupProblem.FOLDER_IN_JOURNAL -> R.string.settings_backup_problem_inside
        BackupProblem.FAILED -> R.string.settings_backup_problem_failed
    },
)

/** Der gewählte Ordner geht nicht als Sicherungsordner – deutlich als Dialog, mit direktem neuen Versuch. */
@Composable
internal fun BackupFolderRejectedDialog(choice: BackupFolderChoice, onChooseOther: () -> Unit, onDismiss: () -> Unit) {
    val colors = Relic.colors
    val inside = choice == BackupFolderChoice.INSIDE_JOURNAL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (inside) R.string.backup_folder_inside_journal_title else R.string.backup_folder_no_access_title))
        },
        text = {
            Text(
                stringResource(if (inside) R.string.backup_folder_inside_journal else R.string.backup_folder_no_access),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onChooseOther) { Text(stringResource(R.string.backup_folder_choose_other), color = colors.ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.ink) }
        },
        containerColor = colors.sheet,
    )
}
