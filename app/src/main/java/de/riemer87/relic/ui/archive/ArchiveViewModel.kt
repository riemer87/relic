package de.riemer87.relic.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import de.riemer87.relic.journal.Archive
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.JournalSearch
import de.riemer87.relic.journal.JournalTextCache
import de.riemer87.relic.journal.SearchHit
import de.riemer87.relic.journal.SearchableEntry
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.ui.DayPreview
import de.riemer87.relic.ui.DayPreviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
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

/** Suche im Archiv. */
data class SearchUiState(
    val open: Boolean = false,
    /** Was gerade im Suchfeld steht. */
    val query: String = "",
    /** Einträge werden zum ersten Mal gelesen. */
    val loading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    /** Der Begriff, zu dem [hits] gehören (hinkt beim Tippen kurz hinterher). */
    val resultQuery: String = "",
) {
    /** „Keine Einträge gefunden“ nur zeigen, wenn wirklich zu diesem Begriff gesucht wurde. */
    val showNoResults: Boolean get() = !loading && resultQuery.isNotEmpty() && resultQuery == query.trim() && hits.isEmpty()
}

/**
 * Zustand des Archivs. Startet im aktuellen Monat; der gewählte Monat überlebt das Öffnen eines
 * Eintrags, das Drehen des Handys und sogar, wenn Android die App im Hintergrund beendet.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ArchiveViewModel(
    private val store: JournalStore,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val textCache: JournalTextCache = JournalTextCache(store),
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
            if (_search.value.open) loadSearchEntries()
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

    // --- Suche ---

    private val _search = MutableStateFlow(
        SearchUiState(open = savedState[KEY_SEARCH_OPEN] ?: false, query = savedState[KEY_QUERY] ?: ""),
    )
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val query = MutableStateFlow(_search.value.query)

    /** Texte aller Einträge, `null` solange sie noch nicht gelesen sind. */
    private val searchEntries = MutableStateFlow<List<SearchableEntry>?>(null)

    init {
        if (_search.value.open) loadSearchEntries()
        viewModelScope.launch {
            // Kurz warten, bis die Nutzerin aufhört zu tippen; gesucht wird abseits der Bedienoberfläche.
            combine(query.debounce(SEARCH_DELAY_MS), searchEntries.filterNotNull()) { q, entries -> q to entries }
                .mapLatest { (q, entries) -> q.trim() to withContext(Dispatchers.Default) { JournalSearch.search(entries, q) } }
                .collect { (q, hits) -> _search.update { it.copy(hits = hits, resultQuery = q) } }
        }
    }

    fun openSearch() {
        savedState[KEY_SEARCH_OPEN] = true
        _search.update { it.copy(open = true) }
        loadSearchEntries()
    }

    fun closeSearch() {
        savedState[KEY_SEARCH_OPEN] = false
        savedState[KEY_QUERY] = ""
        query.value = ""
        _search.update { SearchUiState() }
    }

    fun setQuery(text: String) {
        savedState[KEY_QUERY] = text
        query.value = text
        _search.update { it.copy(query = text) }
    }

    private fun loadSearchEntries() {
        viewModelScope.launch {
            if (searchEntries.value == null) _search.update { it.copy(loading = true) }
            val entries = try {
                withContext(Dispatchers.IO) { textCache.sync(store.entryDates()) }
            } catch (e: IOException) {
                emptyList()
            }
            searchEntries.value = entries
            _search.update { it.copy(loading = false) }
        }
    }

    private companion object {
        const val KEY_MONTH = "month"
        const val KEY_SEARCH_OPEN = "search_open"
        const val KEY_QUERY = "search_query"
        const val SEARCH_DELAY_MS = 200L
    }
}
