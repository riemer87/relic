package de.riemer87.relic.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.journal.Archive
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.ui.DayPreview
import de.riemer87.relic.ui.DayPreviews
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
import java.time.YearMonth

data class ArchiveUiState(
    val today: LocalDate,
    /** Der gerade gezeigte Monat. */
    val month: YearMonth,
    val entryDates: Set<LocalDate> = emptySet(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    val entries: List<LocalDate> get() = Archive.entriesIn(month, entryDates)
    val canGoForward: Boolean get() = Archive.canGoForward(month, today)
    val canGoBack: Boolean get() = Archive.canGoBack(month, entryDates, today)
}

/**
 * Zustand des Archivs. Startet im aktuellen Monat; der gewählte Monat überlebt das Öffnen eines
 * Eintrags, das Drehen des Handys und sogar, wenn Android die App im Hintergrund beendet.
 */
class ArchiveViewModel(
    private val store: JournalStore,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ArchiveUiState(
            today = localToday(clock),
            month = savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: YearMonth.from(localToday(clock)),
        ),
    )
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    private val dayPreviews = DayPreviews(store, viewModelScope)
    val previews: StateFlow<Map<LocalDate, DayPreview>> = dayPreviews.previews

    fun refresh() {
        viewModelScope.launch {
            val dates = try {
                withContext(Dispatchers.IO) { store.entryDates() }
            } catch (e: IOException) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            _state.update { it.copy(today = localToday(clock), entryDates = dates, isLoading = false, loadFailed = false) }
            dayPreviews.refresh(dates)
        }
    }

    fun showMonth(month: YearMonth) {
        val current = _state.value
        if (!Archive.isSelectable(month, current.entryDates, current.today)) return
        savedState[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month) }
    }

    fun previousMonth() = showMonth(_state.value.month.minusMonths(1))

    fun nextMonth() = showMonth(_state.value.month.plusMonths(1))

    fun requestPreview(date: LocalDate) = dayPreviews.request(date)

    private companion object {
        const val KEY_MONTH = "month"
    }
}
