package de.riemer87.relic.ui

import de.riemer87.relic.journal.Entry
import de.riemer87.relic.journal.EntryFormat
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.SaveResult
import java.io.IOException
import java.time.LocalDate

/** Tagebuch im Speicher, nur für Oberflächentests. */
class FakeJournalStore(initial: Map<LocalDate, String> = emptyMap()) : JournalStore {

    private val texts = initial.toMutableMap()

    @Volatile var saveCount = 0
        private set

    /** Lässt jedes Speichern scheitern, z. B. um Fehlermeldungen zu prüfen. */
    @Volatile var failSaving = false

    fun textOf(date: LocalDate): String? = synchronized(texts) { texts[date] }

    override fun load(date: LocalDate): Entry? = synchronized(texts) {
        texts[date]?.let { Entry(date, created = null, updated = null, text = it) }
    }

    override fun save(date: LocalDate, text: String): SaveResult {
        if (failSaving) throw IOException("Simulierter Fehler")
        saveCount++
        val normalized = EntryFormat.normalizeText(text)
        return synchronized(texts) {
            if (normalized.isEmpty()) {
                texts.remove(date)
                SaveResult.DELETED
            } else {
                texts[date] = normalized
                SaveResult.SAVED
            }
        }
    }

    override fun entryDates(): Set<LocalDate> = synchronized(texts) { texts.keys.toSet() }
}
