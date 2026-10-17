package de.riemer87.relic.ui.settings

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import de.riemer87.relic.export.ExportRecord
import de.riemer87.relic.export.ExportService
import de.riemer87.relic.export.ExportState
import de.riemer87.relic.export.ExportStatus
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.reminder.ReminderReceiver
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.storage.FolderLabel
import de.riemer87.relic.ui.theme.AccentColor
import de.riemer87.relic.ui.theme.Appearance
import de.riemer87.relic.ui.theme.AppearanceSettings
import de.riemer87.relic.ui.theme.ThemeMode
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/**
 * @param justNow gerade eben fertig geworden, während die Seite offen war („Export gespeichert“);
 *   sonst ein früherer Export („Letzter Export“)
 */
data class LastExport(val done: ExportState.Done, val justNow: Boolean)

data class SettingsUiState(
    val folder: FolderLabel.Parts?,
    val reminder: ReminderConfig,
    /** Erinnerung an, aber Android lässt keine Benachrichtigungen zu. */
    val notificationsBlocked: Boolean,
    val version: String,
)

class SettingsViewModel(application: Application, journalUri: Uri?) : AndroidViewModel(application) {

    private val reminderSettings = ReminderSettings(application)
    private val scheduler = ReminderScheduler(application)

    private val _state = MutableStateFlow(
        SettingsUiState(
            folder = journalUri?.let(::describe),
            reminder = reminderSettings.load(),
            notificationsBlocked = false,
            version = versionName(application),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshNotificationState()
    }

    /** Stand von „Alles exportieren“ (läuft als eigener Dienst weiter, auch ohne diese Seite). */
    val export: StateFlow<ExportState> = ExportStatus.state

    private val exportRecord = ExportRecord(application)

    /** So stand der Export beim Öffnen der Seite – ein schon fertiger gilt dann nicht mehr als „gerade eben“. */
    private val exportAtOpen = ExportStatus.state.value

    /** Letzter erfolgreicher Export: gerade eben auf dieser Seite beendet, oder gespeichert von früher. */
    val lastExport: StateFlow<LastExport?> = ExportStatus.state
        .map(::lastExportFor)
        .stateIn(viewModelScope, SharingStarted.Eagerly, lastExportFor(exportAtOpen))

    private fun lastExportFor(state: ExportState): LastExport? =
        if (state is ExportState.Done && state != exportAtOpen) {
            LastExport(state, justNow = true)
        } else {
            exportRecord.lastSuccess()?.let { LastExport(it, justNow = false) }
        }

    private val appearanceSettings = AppearanceSettings.get(application)

    /** Hell/Dunkel und Akzentfarbe; Änderungen wirken sofort in der ganzen App. */
    val appearance: StateFlow<Appearance> = appearanceSettings.appearance

    fun setThemeMode(mode: ThemeMode) = appearanceSettings.setMode(mode)

    fun setAccent(accent: AccentColor) = appearanceSettings.setAccent(accent)

    fun startExport(target: Uri) = ExportService.start(getApplication(), target)

    fun setReminderEnabled(enabled: Boolean) = update(_state.value.reminder.copy(enabled = enabled))

    fun setReminderTime(time: LocalTime) = update(_state.value.reminder.copy(time = time))

    /** Nach Rückkehr aus den Android-Einstellungen: hat sich an der Erlaubnis etwas geändert? */
    fun refreshNotificationState() {
        val blocked = _state.value.reminder.enabled && !ReminderReceiver.canPostNotifications(getApplication())
        _state.update { it.copy(notificationsBlocked = blocked) }
    }

    private fun update(config: ReminderConfig) {
        reminderSettings.save(config)
        scheduler.apply(config)
        _state.update { it.copy(reminder = config) }
        refreshNotificationState()
    }

    private companion object {
        fun describe(uri: Uri): FolderLabel.Parts? = try {
            FolderLabel.parse(uri.authority.orEmpty(), DocumentsContract.getTreeDocumentId(uri))
        } catch (e: IllegalArgumentException) {
            null
        }

        fun versionName(app: Application): String =
            app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }
}
