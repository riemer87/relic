package de.riemer87.relic.ui.settings

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.reminder.ReminderReceiver
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.storage.FolderLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

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
