package de.riemer87.relic.journal

import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Ergebnis von [JournalStore.save]. */
enum class SaveResult {
    /** Die Datei wurde geschrieben. */
    SAVED,

    /** Kein Text und keine Fotos: Die Datei wurde gelöscht (oder gab es nie). */
    DELETED,
}

/** Liest und schreibt Einträge. */
interface JournalStore {
    /** Lädt den Eintrag eines Tages, oder `null`, wenn es keine Datei gibt. */
    @Throws(IOException::class)
    fun load(date: LocalDate): Entry?

    /**
     * Speichert Text und Fotos eines Tages. Leerer Text ohne Fotos löscht die Datei.
     * @param photos die Fotos in gewünschter Reihenfolge; `null` = vorhandene Fotos unverändert lassen.
     */
    @Throws(IOException::class)
    fun save(date: LocalDate, text: String, photos: List<DayPhoto>? = null): SaveResult

    /** Alle Tage, für die es eine Eintragsdatei gibt. */
    @Throws(IOException::class)
    fun entryDates(): Set<LocalDate>

    /** Öffnet ein Foto eines Tages zum Lesen, oder `null`, wenn es fehlt. Aufrufer muss schließen. */
    @Throws(IOException::class)
    fun openPhoto(date: LocalDate, fileName: String): InputStream?
}

/** Das heutige Datum nach Ortszeit – nie über UTC, sonst landen späte Einträge beim falschen Tag. */
fun localToday(clock: Clock): LocalDate = LocalDate.now(clock)

/** Pfad einer Eintragsdatei relativ zum Journal-Ordner, z. B. `2026/10/2026-10-09.md`. */
fun entryPath(date: LocalDate): String =
    String.format(Locale.ROOT, "%04d/%02d/%s.md", date.year, date.monthValue, date)

/**
 * Pfad der Zwischendatei, in die vor dem eigentlichen Speichern geschrieben wird.
 * Bewusst ohne Punkt am Anfang: Manche Android-Ordneranbieter blenden solche Dateien aus,
 * dann könnte die App sie nach einem Absturz nicht wiederfinden.
 */
fun tempPath(path: String): String = "$path.tmp"

/**
 * Speichert Einträge als Dateien in einem [DocumentTree].
 *
 * Android kann in freigegebenen Ordnern eine Datei nicht in einem Schritt durch eine andere
 * ersetzen. Deshalb läuft Speichern so:
 * 1. Neuen Inhalt vollständig in die Zwischendatei schreiben.
 * 2. Alte Datei löschen.
 * 3. Zwischendatei auf den richtigen Namen umbenennen.
 *
 * Stürzt das Gerät zwischen 2 und 3 ab, gibt es nur noch die vollständige Zwischendatei.
 * [load] erkennt das und macht sie wieder zum Eintrag. Vorher ist die alte Datei noch da.
 */
class TreeJournalStore(
    private val tree: DocumentTree,
    private val clock: Clock = Clock.systemDefaultZone(),
) : JournalStore {

    override fun load(date: LocalDate): Entry? {
        val path = entryPath(date)
        val content = tree.readText(path) ?: recoverFromTemp(path) ?: return null
        return EntryFormat.parse(content, date)
    }

    /**
     * Reihenfolge, damit ein Absturz nie einen Eintrag hinterlässt, der auf ein fehlendes Foto zeigt:
     * 1. neue Fotos schreiben, 2. Eintragsdatei schreiben (oder löschen), 3. entfernte Fotos löschen.
     * Stürzt es nach 1 ab, liegen höchstens Fotos zu viel im Ordner – nie fehlt eins.
     */
    override fun save(date: LocalDate, text: String, photos: List<DayPhoto>?): SaveResult {
        val path = entryPath(date)
        val dir = splitPath(path).first
        val existing = load(date)
        val normalized = EntryFormat.normalizeText(text)
        val oldNames = existing?.photos.orEmpty()

        // 1. Neue Fotos unter freien Namen ablegen.
        val newNames = if (photos == null) {
            oldNames
        } else {
            val taken = (oldNames + photos.filterIsInstance<StoredPhoto>().map { it.fileName } +
                tree.list(dir).orEmpty()).toMutableSet()
            photos.map { photo ->
                when (photo) {
                    is StoredPhoto -> photo.fileName
                    is NewPhoto -> {
                        val name = PhotoNames.nextFree(date, taken)
                        taken += name
                        writeSafely("$dir/$name", photo.jpeg())
                        name
                    }
                }
            }
        }

        // 2. Eintragsdatei schreiben oder löschen.
        val result = if (normalized.isEmpty() && newNames.isEmpty()) {
            // Zuerst eine alte Zwischendatei entfernen, sonst würde load() sie wiederbeleben.
            tree.delete(tempPath(path))
            tree.delete(path)
            SaveResult.DELETED
        } else {
            val now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
            val entry = Entry(
                date = date,
                created = existing?.created ?: now,
                updated = now,
                text = normalized,
                photos = newNames,
                extraFrontmatter = existing?.extraFrontmatter.orEmpty(),
            )
            writeSafely(path, EntryFormat.serialize(entry).toByteArray(Charsets.UTF_8))
            SaveResult.SAVED
        }

        // 3. Fotos löschen, die der Eintrag nicht mehr benutzt. Nur eigene, nie fremde Dateien.
        for (removed in oldNames - newNames.toSet()) {
            if (PhotoNames.belongsTo(removed, date)) tree.delete("$dir/$removed")
        }
        return result
    }

    /**
     * Sucht Einträge in `JJJJ/MM/JJJJ-MM-TT.md`. Fremde Dateien und Ordner werden ignoriert.
     * Eine Zwischendatei ohne zugehörige Datei zählt mit (Absturz vor dem Umbenennen, siehe [load]).
     */
    override fun entryDates(): Set<LocalDate> {
        val dates = mutableSetOf<LocalDate>()
        for (year in tree.list("").orEmpty().filter { YEAR.matches(it) }) {
            for (month in tree.list(year).orEmpty().filter { MONTH.matches(it) }) {
                for (name in tree.list("$year/$month").orEmpty()) {
                    val match = ENTRY_FILE.matchEntire(name) ?: continue
                    val day = match.groupValues[1]
                    if (!day.startsWith("$year-$month-")) continue
                    val date = try {
                        LocalDate.parse(day)
                    } catch (e: DateTimeParseException) {
                        continue // z. B. 2026-02-30
                    }
                    dates += date
                }
            }
        }
        return dates
    }

    override fun openPhoto(date: LocalDate, fileName: String): InputStream? {
        // Namen stammen aus der Eintragsdatei und könnten von Hand verändert sein:
        // nur Dateien direkt neben dem Eintrag, nie in anderen Ordnern.
        if (fileName.isBlank() || '/' in fileName || '\\' in fileName || fileName.startsWith("..")) return null
        return tree.openRead("${splitPath(entryPath(date)).first}/$fileName")
    }

    private fun writeSafely(path: String, content: ByteArray) {
        val temp = tempPath(path)
        tree.writeBytes(temp, content)
        tree.delete(path)
        tree.rename(temp, splitPath(path).second)
    }

    /** Fehlt die Datei, aber die Zwischendatei ist da, war der Absturz zwischen Löschen und Umbenennen. */
    private fun recoverFromTemp(path: String): String? {
        val temp = tempPath(path)
        val content = tree.readText(temp) ?: return null
        try {
            tree.rename(temp, splitPath(path).second)
        } catch (e: IOException) {
            // Inhalt trotzdem anzeigen; beim nächsten Speichern wird die Datei ordentlich geschrieben.
        }
        return content
    }

    private companion object {
        val YEAR = Regex("""\d{4}""")
        val MONTH = Regex("""\d{2}""")
        // Eintrag oder übrig gebliebene Zwischendatei
        val ENTRY_FILE = Regex("""(\d{4}-\d{2}-\d{2})\.md(\.tmp)?""")
    }
}
