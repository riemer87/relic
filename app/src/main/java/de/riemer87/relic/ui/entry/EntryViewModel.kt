package de.riemer87.relic.ui.entry

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.R
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.SaveResult
import de.riemer87.relic.journal.localToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

data class EntryUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val text: String = "",
    val mode: EntryMode = EntryMode.READ,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Fehlermeldung auf der Seite; nach dem Anzeigen über [EntryViewModel.errorShown] gelöscht. */
    @StringRes val error: Int? = null,
    /** Gesetzt, sobald die Seite geschlossen werden soll; enthält die Meldung für den Zeitstrahl (oder 0). */
    @StringRes val closeWith: Int? = null,
)

/** Zustand einer Eintragsseite. Speichern beim Schließen, beim Zurückgehen und beim Verlassen der App. */
class EntryViewModel(
    private val store: JournalStore,
    date: LocalDate,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryUiState(date = date, isToday = date == localToday(clock)))
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    /** Was zuletzt in der Datei stand bzw. gespeichert wurde. */
    private var savedText = ""

    /** Immer nur ein Speichervorgang gleichzeitig, sonst könnten zwei in dieselbe Zwischendatei schreiben. */
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { store.load(date)?.text.orEmpty() }
                savedText = text
                val mode = EntryRules.initialMode(date, localToday(clock), text)
                _state.update { it.copy(text = text, mode = mode, isLoading = false) }
            } catch (e: IOException) {
                // Bleibt gesperrt, damit eine vorhandene Datei nicht versehentlich überschrieben wird.
                _state.update { it.copy(error = R.string.entry_load_failed) }
            }
        }
    }

    fun onTextChange(text: String) {
        if (text.length <= MAX_ENTRY_LENGTH) _state.update { it.copy(text = text) }
    }

    fun edit() = _state.update { it.copy(mode = EntryMode.EDIT) }

    /** „Speichern“ und „Zurück“: Änderungen sichern, dann schließen. Schlägt das fehl, bleibt die Seite offen. */
    fun close() {
        val current = _state.value
        if (current.isSaving || current.closeWith != null) return
        if (current.isLoading || !EntryRules.hasChanges(current.text, savedText)) {
            _state.update { it.copy(closeWith = 0) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val message = save(current.text)
            _state.update {
                if (message == null) it.copy(isSaving = false, error = R.string.entry_save_failed)
                else it.copy(isSaving = false, closeWith = message)
            }
        }
    }

    /** App geht in den Hintergrund: still sichern, damit nichts verloren geht, falls Android sie beendet. */
    fun saveInBackground() {
        val current = _state.value
        if (current.isLoading || current.isSaving || current.closeWith != null) return
        if (!EntryRules.hasChanges(current.text, savedText)) return
        viewModelScope.launch { save(current.text) }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    /** Gibt die Meldung für die Nutzerin zurück, oder `null`, wenn das Speichern fehlschlug. */
    private suspend fun save(text: String): Int? = withContext(Dispatchers.IO + NonCancellable) {
        saveLock.withLock { saveLocked(text) }
    }

    private fun saveLocked(text: String): Int? {
        return try {
            val result = store.save(_state.value.date, text)
            savedText = text
            when (result) {
                SaveResult.SAVED -> R.string.entry_saved
                SaveResult.DELETED -> R.string.entry_removed
            }
        } catch (e: IOException) {
            null
        }
    }
}
