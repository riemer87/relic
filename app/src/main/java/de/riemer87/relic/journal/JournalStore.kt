package de.riemer87.relic.journal

import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
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

    /** Speichert den Text eines Tages. Leerer Text ohne Fotos löscht die Datei. */
    @Throws(IOException::class)
    fun save(date: LocalDate, text: String): SaveResult
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

    override fun save(date: LocalDate, text: String): SaveResult {
        val path = entryPath(date)
        val existing = load(date)
        val normalized = EntryFormat.normalizeText(text)
        val photos = existing?.photos.orEmpty()

        if (normalized.isEmpty() && photos.isEmpty()) {
            // Zuerst eine alte Zwischendatei entfernen, sonst würde load() sie wiederbeleben.
            tree.delete(tempPath(path))
            tree.delete(path)
            return SaveResult.DELETED
        }

        val now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val entry = Entry(
            date = date,
            created = existing?.created ?: now,
            updated = now,
            text = normalized,
            photos = photos,
            extraFrontmatter = existing?.extraFrontmatter.orEmpty(),
        )
        writeSafely(path, EntryFormat.serialize(entry))
        return SaveResult.SAVED
    }

    private fun writeSafely(path: String, content: String) {
        val temp = tempPath(path)
        tree.writeText(temp, content)
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
}
