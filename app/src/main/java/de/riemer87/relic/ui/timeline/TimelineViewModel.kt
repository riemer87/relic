package de.riemer87.relic.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.Timeline
import de.riemer87.relic.journal.TimelineDay
import de.riemer87.relic.journal.localToday
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
    /** Vorschautexte, nur für Tage, die schon sichtbar waren. */
    val previews: Map<LocalDate, String> = emptyMap(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * Zustand des Zeitstrahls. Liest zuerst nur, welche Tage Einträge haben; den Text eines Tages
 * erst, wenn er beim Scrollen sichtbar wird.
 */
class TimelineViewModel(
    private val store: JournalStore,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState(today = localToday(clock)))
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private val requested = mutableSetOf<LocalDate>()

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
            val days = Timeline.days(today, dates)
            _state.update { it.copy(today = today, days = days, isLoading = false, loadFailed = false) }

            // Schon angezeigte Vorschauen auffrischen (Text kann sich geändert haben), andere vergessen.
            val visibleBefore = requested.toSet()
            requested.clear()
            _state.update { s -> s.copy(previews = s.previews.filterKeys { it in dates }) }
            visibleBefore.filter { it in dates }.forEach(::requestPreview)
        }
    }

    /** Vom Zeitstrahl aufgerufen, sobald ein Tag mit Eintrag sichtbar wird. */
    fun requestPreview(date: LocalDate) {
        if (!requested.add(date)) return
        viewModelScope.launch {
            val preview = try {
                withContext(Dispatchers.IO) { store.load(date)?.text?.let(Timeline::preview).orEmpty() }
            } catch (e: IOException) {
                ""
            }
            _state.update { it.copy(previews = it.previews + (date to preview)) }
        }
    }

    private fun untilNextMidnight(): Duration {
        val now = ZonedDateTime.now(clock)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, midnight)
    }
}
