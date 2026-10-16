package de.riemer87.relic.ui

import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** Was ein Tag in einer Liste zeigt: Text ohne Leerzeilen und die Fotos. */
data class DayPreview(val text: String, val photos: List<String>)

/**
 * Lädt Vorschauen erst, wenn ein Tag sichtbar wird, und merkt sie sich.
 * Gemeinsam genutzt von Zeitstrahl und Archiv.
 */
class DayPreviews(private val store: JournalStore, private val scope: CoroutineScope) {

    private val _previews = MutableStateFlow<Map<LocalDate, DayPreview>>(emptyMap())
    val previews: StateFlow<Map<LocalDate, DayPreview>> = _previews.asStateFlow()

    private val requested = mutableSetOf<LocalDate>()

    /** Von der Liste aufgerufen, sobald ein Tag mit Eintrag sichtbar wird. */
    fun request(date: LocalDate) {
        if (!requested.add(date)) return
        scope.launch {
            val preview = try {
                withContext(Dispatchers.IO) {
                    val entry = store.load(date)
                    DayPreview(entry?.text?.let(Timeline::preview).orEmpty(), entry?.photos.orEmpty())
                }
            } catch (e: IOException) {
                DayPreview("", emptyList())
            }
            _previews.update { it + (date to preview) }
        }
    }

    /** Nach Änderungen: schon gezeigte Vorschauen neu laden, Tage ohne Eintrag vergessen. */
    fun refresh(entryDates: Set<LocalDate>) {
        val shown = requested.toSet()
        requested.clear()
        _previews.update { map -> map.filterKeys { it in entryDates } }
        shown.filter { it in entryDates }.forEach(::request)
    }
}
