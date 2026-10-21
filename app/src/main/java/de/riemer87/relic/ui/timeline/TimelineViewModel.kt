package de.riemer87.relic.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.Timeline
import de.riemer87.relic.journal.TimelineDay
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.ui.DayPreview
import de.riemer87.relic.ui.DayPreviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

data class TimelineUiState(
    val today: LocalDate,
    val days: List<TimelineDay> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Gibt es überhaupt Einträge (auch ältere)? Erst dann lohnt eine Erinnerung ans Sichern. */
    val hasAnyEntry: Boolean = false,
)

/**
 * Zustand des Zeitstrahls (heute und die 6 Tage davor). Liest zuerst nur, welche Tage Einträge
 * haben; den Inhalt eines Tages erst, wenn er sichtbar wird.
 */
class TimelineViewModel(
    private val store: JournalStore,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState(today = localToday(clock)))
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private val dayPreviews = DayPreviews(store, viewModelScope)
    val previews: StateFlow<Map<LocalDate, DayPreview>> = dayPreviews.previews

    init {
        // Um Mitternacht rückt „heute“ weiter, auch wenn die App gerade offen ist.
        viewModelScope.launch {
            while (true) {
                delay(untilNextMidnight().toMillis() + 1_000)
                refresh()
            }
        }
    }

    /** Neu einlesen: beim Öffnen, nach dem Speichern, beim Zurückkehren in die App. */
    fun refresh() {
        viewModelScope.launch {
            val today = localToday(clock)
            val dates = try {
                withContext(Dispatchers.IO) { store.entryDates() }
            } catch (e: IOException) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            _state.update {
                it.copy(today = today, days = Timeline.days(today, dates), isLoading = false, loadFailed = false, hasAnyEntry = dates.isNotEmpty())
            }
            dayPreviews.refresh(dates)
        }
    }

    fun requestPreview(date: LocalDate) = dayPreviews.request(date)

    private fun untilNextMidnight(): Duration {
        val now = ZonedDateTime.now(clock)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, midnight)
    }
}
