package de.riemer87.relic.journal

import java.io.IOException
import java.time.LocalDate

/** Ein Eintrag, wie die Suche ihn braucht: Vorschautext (ohne Leerzeilen) und Fotos. */
data class SearchableEntry(val date: LocalDate, val text: String, val photos: List<String>)

/**
 * Ein Treffer: der Vorschautext, so zugeschnitten, dass die Fundstelle zu sehen ist,
 * und wo darin der Suchbegriff vorkommt (zum Hervorheben).
 */
data class SearchHit(
    val date: LocalDate,
    val snippet: String,
    val matches: List<IntRange>,
    val photos: List<String>,
)

/** Einfache Textsuche: ohne Rücksicht auf Groß- und Kleinschreibung, neueste Einträge zuerst. */
object JournalSearch {

    /** Steht die Fundstelle weiter hinten, beginnt die Vorschau kurz davor (mit „…“). */
    private const val MAX_LEAD_CHARS = 60
    private const val CONTEXT_CHARS = 30
    private const val MAX_LEAD_LINES = 2
    const val ELLIPSIS = "…"

    fun search(entries: Collection<SearchableEntry>, query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return entries
            .filter { it.text.contains(needle, ignoreCase = true) }
            .sortedByDescending { it.date }
            .map { entry ->
                val snippet = snippet(entry.text, needle)
                SearchHit(entry.date, snippet, occurrences(snippet, needle), entry.photos)
            }
    }

    /**
     * Schneidet den Text so zu, dass die erste Fundstelle in den ersten Zeilen steht.
     * Steht sie schon vorn, bleibt der Text unverändert.
     */
    fun snippet(text: String, query: String): String {
        val first = text.indexOf(query, ignoreCase = true)
        if (first < 0) return text
        val linesBefore = text.substring(0, first).count { it == '\n' }
        if (first <= MAX_LEAD_CHARS && linesBefore < MAX_LEAD_LINES) return text

        val lineStart = text.lastIndexOf('\n', first - 1) + 1
        var start = lineStart
        if (first - lineStart > CONTEXT_CHARS) {
            // Kurz vor der Fundstelle an einer Wortgrenze beginnen.
            val space = text.lastIndexOf(' ', first - CONTEXT_CHARS)
            start = if (space >= lineStart) space + 1 else first - CONTEXT_CHARS
        }
        return if (start == 0) text else ELLIPSIS + text.substring(start)
    }

    /** Alle Stellen, an denen der Begriff vorkommt (ohne Groß-/Kleinschreibung). */
    fun occurrences(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            ranges += index until index + query.length
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
        return ranges
    }
}

/**
 * Hält die Texte aller Einträge für die Suche im Arbeitsspeicher. Beim ersten Mal werden alle
 * Dateien gelesen, danach nur noch neue; geänderte Tage meldet die App mit [forget].
 * Die Dateien bleiben die Quelle – dieser Speicher lässt sich jederzeit neu aufbauen.
 */
class JournalTextCache(private val store: JournalStore) {

    private val entries = mutableMapOf<LocalDate, SearchableEntry>()

    /**
     * Bringt den Speicher auf den Stand dieser Tage: fehlende werden gelesen, verschwundene vergessen.
     * Nicht lesbare Dateien werden still übersprungen (beim nächsten Mal erneut versucht).
     * Liest Dateien – nicht im Hauptthread aufrufen.
     */
    @Synchronized
    fun sync(dates: Set<LocalDate>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<SearchableEntry> {
        entries.keys.retainAll(dates)
        val missing = dates.filter { it !in entries }
        missing.forEachIndexed { index, date ->
            onProgress(index, missing.size)
            try {
                store.load(date)?.let { entry ->
                    entries[date] = SearchableEntry(date, Timeline.preview(entry.text), entry.photos)
                }
            } catch (e: IOException) {
                // Kaputte oder gerade nicht lesbare Datei: nie abstürzen.
            }
        }
        return entries.values.toList()
    }

    /** Alles vergessen, z. B. wenn die App in den Hintergrund geht (Dateien könnten anderswo geändert werden). */
    @Synchronized
    fun clear() {
        entries.clear()
    }

    /** Ein Tag wurde in der App geändert: beim nächsten Suchen neu lesen. */
    @Synchronized
    fun forget(date: LocalDate) {
        entries.remove(date)
    }
}
