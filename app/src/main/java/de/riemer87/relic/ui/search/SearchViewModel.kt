package de.riemer87.relic.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.riemer87.relic.journal.JournalSearch
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.JournalTextCache
import de.riemer87.relic.journal.SearchHit
import de.riemer87.relic.journal.SearchableEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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

data class SearchUiState(
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
 * Die Suche als eigene Seite, erreichbar aus beiden Reitern. Der Suchbegriff überlebt das Öffnen
 * eines Treffers und das Drehen des Handys.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val store: JournalStore,
    private val textCache: JournalTextCache,
    private val savedState: SavedStateHandle,
    /** Erst so spät „Einträge werden durchsucht …“ zeigen – schnelles Einlesen soll nicht aufblitzen. */
    private val loadingHintDelayMs: Long = LOADING_HINT_DELAY_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(query = savedState[KEY_QUERY] ?: ""))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val query = MutableStateFlow(_state.value.query)

    /** Texte aller Einträge, `null` solange sie noch nicht gelesen sind. */
    private val entries = MutableStateFlow<List<SearchableEntry>?>(null)

    init {
        viewModelScope.launch {
            // Kurz warten, bis das Tippen pausiert; gesucht wird abseits der Bedienoberfläche.
            combine(query.debounce(SEARCH_DELAY_MS), entries.filterNotNull()) { q, all -> q to all }
                .mapLatest { (q, all) -> q.trim() to withContext(Dispatchers.Default) { JournalSearch.search(all, q) } }
                .collect { (q, hits) -> _state.update { it.copy(hits = hits, resultQuery = q) } }
        }
    }

    fun setQuery(text: String) {
        savedState[KEY_QUERY] = text
        query.value = text
        _state.update { it.copy(query = text) }
    }

    /** Beim Öffnen und beim Zurückkommen (z. B. nach dem Bearbeiten eines Treffers): Texte auf Stand bringen. */
    fun refresh() {
        viewModelScope.launch {
            // Fehler aus Meilenstein 13: Der Hinweis blitzte bei jedem Öffnen kurz auf, obwohl die Texte
            // meist schon im Zwischenspeicher lagen. Jetzt erscheint er nur, wenn das Einlesen spürbar dauert.
            val hint = launch {
                delay(loadingHintDelayMs)
                if (entries.value == null) _state.update { it.copy(loading = true) }
            }
            val loaded = try {
                withContext(Dispatchers.IO) { textCache.sync(store.entryDates()) }
            } catch (e: IOException) {
                emptyList()
            }
            hint.cancel()
            entries.value = loaded
            _state.update { it.copy(loading = false) }
        }
    }

    private companion object {
        const val KEY_QUERY = "search_query"
        const val SEARCH_DELAY_MS = 200L
        const val LOADING_HINT_DELAY_MS = 300L
    }
}
