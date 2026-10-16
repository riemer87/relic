package de.riemer87.relic.ui

import android.graphics.Bitmap
import android.graphics.Color
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.journal.Entry
import de.riemer87.relic.journal.EntryFormat
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.NewPhoto
import de.riemer87.relic.journal.PhotoNames
import de.riemer87.relic.journal.SaveResult
import de.riemer87.relic.journal.StoredPhoto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

/** Tagebuch im Speicher, nur für Oberflächentests. */
class FakeJournalStore(
    initial: Map<LocalDate, String> = emptyMap(),
    initialPhotos: Map<LocalDate, Int> = emptyMap(),
) : JournalStore {

    private val texts = initial.toMutableMap()
    private val photos = mutableMapOf<LocalDate, List<String>>()
    private val photoBytes = mutableMapOf<String, ByteArray>()

    init {
        initialPhotos.forEach { (date, count) ->
            photos[date] = (1..count).map { n -> "${date}_$n.jpg".also { photoBytes["$date/$it"] = testJpeg() } }
        }
    }

    @Volatile var saveCount = 0
        private set

    /** Lässt jedes Speichern scheitern, z. B. um Fehlermeldungen zu prüfen. */
    @Volatile var failSaving = false

    fun textOf(date: LocalDate): String? = synchronized(this) { texts[date] }

    fun photosOf(date: LocalDate): List<String> = synchronized(this) { photos[date].orEmpty() }

    override fun load(date: LocalDate): Entry? = synchronized(this) {
        if (date !in texts && date !in photos) return null
        Entry(date, created = null, updated = null, text = texts[date].orEmpty(), photos = photos[date].orEmpty())
    }

    override fun save(date: LocalDate, text: String, photos: List<DayPhoto>?): SaveResult = synchronized(this) {
        if (failSaving) throw IOException("Simulierter Fehler")
        saveCount++
        val normalized = EntryFormat.normalizeText(text)
        val old = this.photos[date].orEmpty()
        val names = photos?.let { list ->
            val taken = old.toMutableSet()
            list.map { photo ->
                when (photo) {
                    is StoredPhoto -> photo.fileName
                    is NewPhoto -> PhotoNames.nextFree(date, taken).also {
                        taken += it
                        photoBytes["$date/$it"] = photo.jpeg()
                    }
                }
            }
        } ?: old
        (old - names.toSet()).forEach { photoBytes.remove("$date/$it") }

        if (normalized.isEmpty() && names.isEmpty()) {
            texts.remove(date)
            this.photos.remove(date)
            SaveResult.DELETED
        } else {
            texts[date] = normalized
            if (names.isEmpty()) this.photos.remove(date) else this.photos[date] = names
            SaveResult.SAVED
        }
    }

    override fun entryDates(): Set<LocalDate> = synchronized(this) { texts.keys + photos.keys }

    override fun openPhoto(date: LocalDate, fileName: String): InputStream? = synchronized(this) {
        photoBytes["$date/$fileName"]?.let(::ByteArrayInputStream)
    }

    companion object {
        /** Kleines, echtes JPEG. */
        fun testJpeg(color: Int = Color.rgb(180, 140, 90)): ByteArray {
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }
    }
}
