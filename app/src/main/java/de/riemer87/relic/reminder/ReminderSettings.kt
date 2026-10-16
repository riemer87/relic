package de.riemer87.relic.reminder

import android.content.Context
import androidx.core.content.edit
import java.time.LocalTime

/** Merkt sich die Erinnerungs-Einstellungen im eingebauten Einstellungsspeicher von Android. */
class ReminderSettings(context: Context) {

    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun load(): ReminderConfig = ReminderConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        time = LocalTime.of(
            prefs.getInt(KEY_HOUR, ReminderConfig.DEFAULT_TIME.hour),
            prefs.getInt(KEY_MINUTE, ReminderConfig.DEFAULT_TIME.minute),
        ),
    )

    fun save(config: ReminderConfig) = prefs.edit {
        putBoolean(KEY_ENABLED, config.enabled)
        putInt(KEY_HOUR, config.time.hour)
        putInt(KEY_MINUTE, config.time.minute)
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
