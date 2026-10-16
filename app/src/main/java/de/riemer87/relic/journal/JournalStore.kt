package de.riemer87.relic.journal

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

/** Liest und schreibt Einträge. Ab Meilenstein 2 kommt eine Variante für den gewählten Ordner hinzu. */
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
 * Speichert Einträge als Dateien in einem normalen Ordner auf dem Gerät.
 *
 * Geschrieben wird immer erst in eine temporäre Datei, die dann in einem Schritt
 * umbenannt wird. So bleibt bei einem Absturz die alte Datei vollständig erhalten.
 */
class FileJournalStore(
    private val root: File,
    private val clock: Clock = Clock.systemDefaultZone(),
) : JournalStore {

    fun fileFor(date: LocalDate): File = File(root, entryPath(date))

    override fun load(date: LocalDate): Entry? {
        val file = fileFor(date)
        if (!file.isFile) return null
        return EntryFormat.parse(file.readText(Charsets.UTF_8), date)
    }

    override fun save(date: LocalDate, text: String): SaveResult {
        val file = fileFor(date)
        val existing = load(date)
        val normalized = EntryFormat.normalizeText(text)
        val photos = existing?.photos.orEmpty()

        if (normalized.isEmpty() && photos.isEmpty()) {
            if (file.exists() && !file.delete()) throw IOException("Konnte $file nicht löschen")
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
        writeAtomically(file, EntryFormat.serialize(entry))
        return SaveResult.SAVED
    }

    private fun writeAtomically(target: File, content: String) {
        val dir = target.parentFile ?: throw IOException("Kein Ordner für $target")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Konnte $dir nicht anlegen")

        // Mit Punkt am Anfang, damit andere Programme die Zwischendatei ignorieren.
        val temp = File(dir, ".${target.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.fd.sync() // Wirklich auf den Speicher schreiben, bevor umbenannt wird.
            }
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temp.delete()
        }
    }
}
