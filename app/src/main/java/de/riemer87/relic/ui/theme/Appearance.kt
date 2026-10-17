package de.riemer87.relic.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hell, Dunkel oder wie das Handy eingestellt ist. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Wie Relic aussieht. Standard: wie das Handy, Akzent Tinte. */
data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: AccentColor = AccentColor.INK) {
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * Merkt sich die Darstellung. Eine Instanz für die ganze App, damit eine Änderung in den
 * Einstellungen sofort überall wirkt.
 */
class AppearanceSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _appearance = MutableStateFlow(load())
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setMode(mode: ThemeMode) = save(_appearance.value.copy(mode = mode))

    fun setAccent(accent: AccentColor) = save(_appearance.value.copy(accent = accent))

    private fun save(appearance: Appearance) {
        prefs.edit {
            putString(KEY_MODE, appearance.mode.name)
            putString(KEY_ACCENT, appearance.accent.name)
        }
        _appearance.value = appearance
    }

    private fun load() = Appearance(
        mode = enumOrDefault(prefs.getString(KEY_MODE, null), ThemeMode.SYSTEM),
        accent = enumOrDefault(prefs.getString(KEY_ACCENT, null), AccentColor.INK),
    )

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_ACCENT = "accent"

        @Volatile
        private var instance: AppearanceSettings? = null

        fun get(context: Context): AppearanceSettings =
            instance ?: synchronized(this) { instance ?: AppearanceSettings(context.applicationContext).also { instance = it } }

        /** Unbekannter Wert (z. B. aus einer späteren Version): Standard nehmen statt abzustürzen. */
        private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
