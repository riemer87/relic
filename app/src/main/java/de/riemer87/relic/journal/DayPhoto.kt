package de.riemer87.relic.journal

import java.io.File
import java.time.LocalDate

/** Höchstzahl an Fotos pro Tag. */
const val MAX_PHOTOS = 4

/** Ein Foto eines Tages, wie es die Eintragsseite beim Speichern übergibt. */
sealed interface DayPhoto

/** Liegt schon im Tagebuch-Ordner neben der Eintragsdatei. */
data class StoredPhoto(val fileName: String) : DayPhoto

/**
 * Gerade ausgewählt und schon als fertiges JPEG aufbereitet (verkleinert, ohne Standort),
 * aber noch nicht im Tagebuch-Ordner.
 */
data class NewPhoto(val file: File) : DayPhoto {
    fun jpeg(): ByteArray = file.readBytes()
}

object PhotoNames {

    private val PHOTO = Regex("""(\d{4}-\d{2}-\d{2})_(\d+)\.jpg""")

    /** Kleinste freie Nummer: `2026-10-16_1.jpg`, `_2` … Belegte Namen werden nie überschrieben. */
    fun nextFree(date: LocalDate, taken: Set<String>): String =
        generateSequence(1) { it + 1 }
            .map { "${date}_$it.jpg" }
            .first { it !in taken }

    /** `true` für `JJJJ-MM-TT_N.jpg` dieses Tages – nur solche Dateien löscht die App je. */
    fun belongsTo(fileName: String, date: LocalDate): Boolean =
        PHOTO.matchEntire(fileName)?.groupValues?.get(1) == date.toString()
}
