package de.riemer87.relic.ui.entry

import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.journal.EntryFormat
import java.time.LocalDate

/** Höchstzahl an Zeichen pro Eintrag. */
const val MAX_ENTRY_LENGTH = 5000

enum class EntryMode { EDIT, READ }

/** Regeln der Eintragsseite, ohne Android – dadurch auf dem PC testbar. */
object EntryRules {

    /**
     * Heute ist immer direkt bearbeitbar, ein leerer vergangener Tag auch (zum Nachtragen).
     * Ein vergangener Tag mit Text oder Fotos öffnet zuerst nur zum Lesen.
     */
    fun initialMode(date: LocalDate, today: LocalDate, savedText: String, hasPhotos: Boolean = false): EntryMode =
        if (date == today || (savedText.isBlank() && !hasPhotos)) EntryMode.EDIT else EntryMode.READ

    /** Zählt nur, was auch wirklich anders in der Datei landen würde. */
    fun hasChanges(current: String, saved: String): Boolean =
        EntryFormat.normalizeText(current) != EntryFormat.normalizeText(saved)

    fun hasChanges(text: String, savedText: String, photos: List<DayPhoto>, savedPhotos: List<DayPhoto>): Boolean =
        hasChanges(text, savedText) || photos != savedPhotos
}
