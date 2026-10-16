package de.riemer87.relic.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.lock.Authenticator
import de.riemer87.relic.lock.canUseAppLock
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import java.time.LocalTime
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
    val context = LocalContext.current
    val deniedMessage = stringResource(R.string.settings_notifications_denied)
    val lockFailedMessage = stringResource(R.string.lock_enable_failed)
    val lockAvailable = remember { canUseAppLock(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshNotificationState() }

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
    )
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

            Section(title = stringResource(R.string.settings_folder)) {
                Text(folderText(state.folder), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                OutlinedButton(onClick = onChangeFolder, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.settings_folder_change), color = colors.ink)
                }
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

            Section(title = stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version, state.version), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
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

/** Schalter in Relic-Farben. */
@Composable
private fun RelicSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = Relic.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.sheet)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(currentLocale()), style = KickerStyle, color = colors.inkFaint)
        content()
    }
}

@Composable
private fun folderText(folder: FolderLabel.Parts?): String {
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
