package de.riemer87.relic.ui.today

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.R
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.SaveResult
import de.riemer87.relic.journal.localToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/** Höchstzahl an Zeichen pro Eintrag. */
const val MAX_ENTRY_LENGTH = 5000

data class TodayUiState(
    val date: LocalDate,
    val text: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Kurze Meldung für die Nutzerin; wird nach dem Anzeigen über [TodayViewModel.messageShown] gelöscht. */
    @StringRes val message: Int? = null,
)

/**
 * Hält den Zustand der Heute-Seite. Überlebt das Drehen des Bildschirms, damit
 * getippter Text nicht verloren geht. Dateizugriffe laufen im Hintergrund.
 */
class TodayViewModel(
    private val store: JournalStore,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState(date = localToday(clock)))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val date = _state.value.date
            try {
                val text = withContext(Dispatchers.IO) { store.load(date)?.text.orEmpty() }
                _state.update { it.copy(text = text, isLoading = false) }
            } catch (e: IOException) {
                // Eingabe bleibt gesperrt, damit eine vorhandene Datei nicht versehentlich überschrieben wird.
                _state.update { it.copy(message = R.string.entry_load_failed) }
            }
        }
    }

    fun onTextChange(text: String) {
        if (text.length <= MAX_ENTRY_LENGTH) _state.update { it.copy(text = text) }
    }

    fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val message = try {
                when (withContext(Dispatchers.IO) { store.save(current.date, current.text) }) {
                    SaveResult.SAVED -> R.string.entry_saved
                    SaveResult.DELETED -> R.string.entry_removed
                }
            } catch (e: IOException) {
                R.string.entry_save_failed
            }
            _state.update { it.copy(isSaving = false, message = message) }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
