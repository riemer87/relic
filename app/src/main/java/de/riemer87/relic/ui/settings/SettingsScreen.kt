package de.riemer87.relic.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupInfo
import de.riemer87.relic.export.ExportService
import de.riemer87.relic.export.ExportState
import de.riemer87.relic.lock.Authenticator
import de.riemer87.relic.lock.canUseAppLock
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.SheetShape
import de.riemer87.relic.ui.theme.AccentColor
import de.riemer87.relic.ui.theme.Appearance
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.ThemeMode
import de.riemer87.relic.ui.theme.relicColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onChangeFolder: () -> Unit,
    showMessage: (String) -> Unit,
    lockEnabled: Boolean = false,
    authenticator: Authenticator = Authenticator { it(false) },
    onLockEnabledChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by viewModel.export.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExport.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    var backupHintOpen by rememberSaveable { mutableStateOf(false) }
    val backupFolder by viewModel.backupFolder.collectAsStateWithLifecycle()
    val backupWaiting by viewModel.backupWaiting.collectAsStateWithLifecycle()
    // Abgelehnter Ordner: als Dialog, damit man es nicht übersieht (eine kurze Meldung unten ging unter).
    var rejectedFolder by rememberSaveable { mutableStateOf<BackupFolderChoice?>(null) }
    val context = LocalContext.current
    val deniedMessage = stringResource(R.string.settings_notifications_denied)
    val lockFailedMessage = stringResource(R.string.lock_enable_failed)
    val lockAvailable = remember { canUseAppLock(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshNotificationState() }

    // „Speichern unter“ von Android: Ort und Namen der ZIP-Datei wählen.
    val chooseExportTarget = rememberLauncherForActivityResult(CreateZipInDownloads()) { uri ->
        uri?.let(viewModel::startExport)
    }

    // Ordnerauswahl von Android für den Sicherungsordner, beginnend in „Dokumente“.
    val chooseBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val choice = viewModel.chooseBackupFolder(uri)
        if (choice != BackupFolderChoice.OK) rejectedFolder = choice
    }

    // Android 13+: Erlaubnis für Benachrichtigungen erst beim Einschalten erfragen.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setReminderEnabled(true) else showMessage(deniedMessage)
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onChangeFolder = onChangeFolder,
        onReminderChange = { on ->
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setReminderEnabled(on)
            }
        },
        onTimeChange = viewModel::setReminderTime,
        onOpenNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        readLicense = { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } },
        lockEnabled = lockEnabled,
        lockAvailable = lockAvailable,
        onLockChange = { on ->
            if (on) {
                // Erst einmal erfolgreich entsperren – so kann man sich nicht aussperren.
                authenticator.authenticate { ok -> if (ok) onLockEnabledChange(true) else showMessage(lockFailedMessage) }
            } else {
                onLockEnabledChange(false)
            }
        },
        exportState = exportState,
        lastExport = lastExport,
        appearance = appearance,
        onThemeModeChange = viewModel::setThemeMode,
        onAccentChange = viewModel::setAccent,
        onExport = { chooseExportTarget.launch("Relic-Export-${LocalDate.now()}.zip") },
        backup = backup,
        backupFolder = backupFolder,
        backupWaiting = backupWaiting,
        onBackupChange = { on ->
            // Ohne Ordner erst einen wählen; eingeschaltet wird dann nach der Wahl.
            if (on && backup.folder == null) backupHintOpen = true else viewModel.setBackupEnabled(on)
        },
        onChooseBackupFolder = { backupHintOpen = true },
        onBackUpNow = viewModel::backUpNow,
    )

    rejectedFolder?.let { choice ->
        BackupFolderRejectedDialog(
            choice = choice,
            onChooseOther = {
                rejectedFolder = null
                chooseBackupFolder.launch(DOCUMENTS)
            },
            onDismiss = { rejectedFolder = null },
        )
    }

    if (backupHintOpen) {
        val colors = Relic.colors
        AlertDialog(
            onDismissRequest = { backupHintOpen = false },
            title = { Text(stringResource(R.string.backup_folder_hint_title)) },
            text = { Text(stringResource(R.string.backup_folder_hint), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    backupHintOpen = false
                    chooseBackupFolder.launch(DOCUMENTS)
                }) { Text(stringResource(R.string.backup_folder_continue), color = colors.ink) }
            },
            dismissButton = {
                TextButton(onClick = { backupHintOpen = false }) { Text(stringResource(R.string.cancel), color = colors.ink) }
            },
            containerColor = colors.sheet,
        )
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onChangeFolder: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    readLicense: (String) -> String,
    lockEnabled: Boolean = false,
    lockAvailable: Boolean = true,
    onLockChange: (Boolean) -> Unit = {},
    exportState: ExportState = ExportState.Idle,
    lastExport: LastExport? = null,
    appearance: Appearance = Appearance(),
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAccentChange: (AccentColor) -> Unit = {},
    onExport: () -> Unit = {},
    backup: BackupInfo = BackupInfo(),
    backupFolder: FolderLabel.Parts? = null,
    backupWaiting: Boolean = false,
    onBackupChange: (Boolean) -> Unit = {},
    onChooseBackupFolder: () -> Unit = {},
    onBackUpNow: () -> Unit = {},
) {
    val colors = Relic.colors
    var timeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var licensesOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = colors.ink)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Section(title = stringResource(R.string.settings_appearance)) {
                ThemeModeChoice(selected = appearance.mode, onSelect = onThemeModeChange)
                Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                AccentChoice(selected = appearance.accent, onSelect = onAccentChange)
            }

            Section(title = stringResource(R.string.settings_reminder)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_reminder_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(6.dp))
                    RelicSwitch(checked = state.reminder.enabled, onCheckedChange = onReminderChange)
                }
                if (state.reminder.enabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { timeDialogOpen = true }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_reminder_time), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(state.reminder.time), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    }
                    if (state.notificationsBlocked) {
                        Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.settings_open_android), color = colors.ink)
                        }
                    }
                }
            }

            Section(title = stringResource(R.string.settings_lock)) {
                if (lockAvailable || lockEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_lock_explain),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.padding(6.dp))
                        RelicSwitch(checked = lockEnabled, onCheckedChange = onLockChange)
                    }
                } else {
                    Text(stringResource(R.string.settings_lock_unavailable), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                }
                Text(stringResource(R.string.settings_lock_files_note), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
            }

            Section(title = stringResource(R.string.settings_folder)) {
                Text(stringResource(R.string.settings_folder_explain), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                Text(folderText(state.folder), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                OutlinedButton(onClick = onChangeFolder, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.settings_folder_change), color = colors.ink)
                }
            }

            BackupSection(
                backup = backup,
                folder = backupFolder,
                waiting = backupWaiting,
                onEnabledChange = onBackupChange,
                onChooseFolder = onChooseBackupFolder,
                onBackUpNow = onBackUpNow,
            )

            Section(title = stringResource(R.string.settings_export)) {
                Text(stringResource(R.string.settings_export_explain), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                OutlinedButton(onClick = onExport, enabled = !exportState.isRunning, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.settings_export_button), color = if (exportState.isRunning) colors.inkFaint else colors.ink)
                }
                if (exportState.isRunning || exportState is ExportState.Failed) {
                    val context = LocalContext.current
                    Text(
                        text = ExportService.progressText(context, exportState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (exportState is ExportState.Failed) colors.ink else colors.inkSoft,
                    )
                }
                if (!exportState.isRunning) lastExport?.let { ExportSuccess(it) }
            }

            Section(title = stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, state.version), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                val uriHandler = LocalUriHandler.current
                val websiteUrl = stringResource(R.string.website_url)
                Text(
                    text = stringResource(R.string.website_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri(websiteUrl) }.padding(vertical = 6.dp),
                )
                Text(
                    text = stringResource(R.string.settings_licenses),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                    modifier = Modifier.clickable(role = Role.Button) { licensesOpen = true }.padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.padding(8.dp))
        }
    }

    if (timeDialogOpen) {
        TimeDialog(
            initial = state.reminder.time,
            onConfirm = {
                timeDialogOpen = false
                onTimeChange(it)
            },
            onDismiss = { timeDialogOpen = false },
        )
    }
    if (licensesOpen) {
        val text = remember { LICENSE_FILES.joinToString("\n\n— — —\n\n") { readLicense(it) } }
        AlertDialog(
            onDismissRequest = { licensesOpen = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_licenses_intro), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(6.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licensesOpen = false }) { Text(stringResource(R.string.ok), color = colors.ink) } },
            containerColor = colors.sheet,
        )
    }
}

/**
 * „Speichern unter“ für die ZIP-Datei, beginnend in „Downloads“. Sonst öffnet Android den zuletzt
 * benutzten Ort – oft den Tagebuch-Ordner selbst, und dort gehört der Export nicht hin.
 */
/** Startpunkt der Ordnerauswahl für Sicherungen: „Dokumente“. */
private val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

private class CreateZipInDownloads : ActivityResultContracts.CreateDocument("application/zip") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS)

    private companion object {
        val DOWNLOADS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
    }
}

/** Hell · Dunkel · Wie das Handy – drei Felder nebeneinander, das gewählte in Tinte gefüllt. */
@Composable
private fun ThemeModeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = Relic.colors
    val options = listOf(
        ThemeMode.LIGHT to R.string.appearance_light,
        ThemeMode.DARK to R.string.appearance_dark,
        ThemeMode.SYSTEM to R.string.appearance_system,
    )
    Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.rule, RoundedCornerShape(8.dp)),
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(mode) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.paper else colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Vier Farbtupfer mit Namen darunter; der gewählte bekommt einen Ring. */
@Composable
private fun AccentChoice(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val colors = Relic.colors
    val dark = Relic.isDark
    val names = mapOf(
        AccentColor.INK to R.string.accent_ink,
        AccentColor.SAGE to R.string.accent_sage,
        AccentColor.PLUM to R.string.accent_plum,
        AccentColor.OCHRE to R.string.accent_ochre,
    )
    Row(modifier = Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        AccentColor.entries.forEach { accent ->
            val isSelected = accent == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(accent) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(2.dp, if (isSelected) colors.ink else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(relicColors(dark, accent).accent),
                )
                Text(
                    stringResource(names.getValue(accent)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.ink else colors.inkSoft,
                )
            }
        }
    }
}

/** Der letzte erfolgreiche Export: grüner Haken, Anzahl Dateien, Datum und Uhrzeit. */
@Composable
private fun ExportSuccess(last: LastExport) {
    val colors = Relic.colors
    val done = last.done
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
                text = pluralStringResource(if (last.justNow) R.plurals.export_done else R.plurals.export_last, done.files, done.files),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.success,
            )
            done.finishedAt?.let { Text(exportedAt(it), style = MaterialTheme.typography.bodyMedium, color = colors.success) }
        }
    }
    if (done.skipped > 0) {
        Text(
            pluralStringResource(R.plurals.export_skipped, done.skipped, done.skipped),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
        )
    }
}

/** „Am 17. Oktober 2026 um 01:40 Uhr“ in der Sprache und Zeitzone des Handys. */
@Composable
private fun exportedAt(at: Instant): String {
    val locale = currentLocale()
    val local = at.atZone(ZoneId.systemDefault())
    val date = remember(at, locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(local) }
    return stringResource(R.string.export_done_at, date, formatTime(local.toLocalTime()))
}

/** Schalter in Relic-Farben. */
@Composable
internal fun RelicSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = Relic.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.accent,
            checkedThumbColor = colors.onAccent,
            uncheckedTrackColor = colors.paper,
            uncheckedThumbColor = colors.inkFaint,
            uncheckedBorderColor = colors.rule,
        ),
    )
}

private val LICENSE_FILES = listOf("Fraunces-OFL.txt", "DMSans-OFL.txt")

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = Relic.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(colors.sheet)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(currentLocale()), style = KickerStyle, color = colors.inkFaint)
        content()
    }
}

@Composable
internal fun folderText(folder: FolderLabel.Parts?): String {
    if (folder == null) return stringResource(R.string.settings_folder_unknown)
    val start = when (folder.location) {
        FolderLabel.Location.INTERNAL -> stringResource(R.string.settings_folder_internal)
        FolderLabel.Location.SD_CARD -> stringResource(R.string.settings_folder_sd)
        FolderLabel.Location.OTHER -> null
    }
    val parts = listOfNotNull(start) + folder.segments
    return parts.joinToString(" › ").ifEmpty { stringResource(R.string.settings_folder_unknown) }
}

/** Uhrzeit in der Schreibweise des Handys, z. B. „21:00“ oder „9:00 PM“. */
@Composable
internal fun formatTime(time: LocalTime): String {
    val locale = currentLocale()
    return remember(time, locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val colors = Relic.colors
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = {
            TimePicker(
                state = pickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.paper,
                    selectorColor = colors.accent,
                    clockDialSelectedContentColor = colors.onAccent,
                    clockDialUnselectedContentColor = colors.ink,
                    timeSelectorSelectedContainerColor = colors.ink,
                    timeSelectorSelectedContentColor = colors.sheet,
                    timeSelectorUnselectedContainerColor = colors.paper,
                    timeSelectorUnselectedContentColor = colors.ink,
                    periodSelectorSelectedContainerColor = colors.ink,
                    periodSelectorSelectedContentColor = colors.sheet,
                    periodSelectorUnselectedContentColor = colors.ink,
                    periodSelectorBorderColor = colors.rule,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.ok), color = colors.ink)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.ink) } },
        containerColor = colors.sheet,
    )
}
