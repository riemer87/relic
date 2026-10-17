package de.riemer87.relic.export

import de.riemer87.relic.journal.DocumentTree
import de.riemer87.relic.journal.JournalFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Eine Datei für das ZIP: wo sie im ZIP liegt und woher sie im Tagebuch kommt. */
data class ExportFile(val zipPath: String, val source: String)

/**
 * Was ins ZIP kommt: nur Relic-Dateien (Einträge, Fotos, Erklär-Datei).
 * @param skipped wie viele andere Dateien oder Ordner im Tagebuch-Ordner liegen und nicht mitkommen
 */
data class ExportPlan(val files: List<ExportFile>, val skipped: Int)

/** Wo der Export gerade steht – für Einstellungen und Benachrichtigung. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Preparing : ExportState
    data class Writing(val done: Int, val total: Int) : ExportState
    data class Verifying(val done: Int, val total: Int) : ExportState
    /** @param finishedAt wann der Export fertig wurde (setzt der Dienst; im reinen Ablauf `null`) */
    data class Done(val files: Int, val skipped: Int, val finishedAt: Instant? = null) : ExportState

    /** @param targetDeleted die unvollständige ZIP-Datei wurde wieder gelöscht */
    data class Failed(val targetDeleted: Boolean, val targetName: String) : ExportState

    val isRunning: Boolean get() = this is Preparing || this is Writing || this is Verifying
}

object ExportPlanner {

    private val YEAR = Regex("""\d{4}""")
    private val MONTH = Regex("""\d{2}""")
    private val ENTRY = Regex("""(\d{4}-\d{2}-\d{2})\.md""")
    private val ENTRY_TEMP = Regex("""(\d{4}-\d{2}-\d{2})\.md\.tmp""")
    private val PHOTO = Regex("""(\d{4}-\d{2}-\d{2})_\d+\.jpg""")

    /**
     * Sammelt alle Relic-Dateien: `LIES MICH.txt`/`README.txt` und in `JJJJ/MM/` die Einträge
     * `JJJJ-MM-TT.md` mit ihren Fotos `JJJJ-MM-TT_N.jpg`.
     * Eine übrig gebliebene Zwischendatei ohne Eintrag (Absturz beim Speichern) ist der Eintrag selbst
     * und kommt unter dem richtigen Namen mit – so wie Relic sie auch anzeigt.
     */
    @Throws(IOException::class)
    fun plan(tree: DocumentTree): ExportPlan {
        val root = tree.list("") ?: throw IOException("Tagebuch-Ordner nicht lesbar")
        val files = mutableListOf<ExportFile>()
        var skipped = 0

        for (name in root) {
            when {
                name in JournalFolder.README_NAMES -> files += ExportFile(name, name)
                YEAR.matches(name) -> {
                    val months = tree.list(name)
                    if (months == null) skipped++ else skipped += planYear(tree, name, months, files)
                }
                else -> skipped++
            }
        }
        return ExportPlan(files.sortedBy { it.zipPath }, skipped)
    }

    private fun planYear(tree: DocumentTree, year: String, months: List<String>, files: MutableList<ExportFile>): Int {
        var skipped = 0
        for (month in months) {
            val names = if (MONTH.matches(month)) tree.list("$year/$month") else null
            if (names == null) skipped++ else skipped += planMonth("$year/$month", "$year-$month-", names, files)
        }
        return skipped
    }

    private fun planMonth(dir: String, prefix: String, names: List<String>, files: MutableList<ExportFile>): Int {
        var skipped = 0
        val present = names.toSet()
        for (name in names) {
            val entry = ENTRY.matchEntire(name)
            val temp = ENTRY_TEMP.matchEntire(name)
            val photo = PHOTO.matchEntire(name)
            when {
                entry != null && isDayOf(entry.groupValues[1], prefix) -> files += ExportFile("$dir/$name", "$dir/$name")
                photo != null && isDayOf(photo.groupValues[1], prefix) -> files += ExportFile("$dir/$name", "$dir/$name")
                temp != null && isDayOf(temp.groupValues[1], prefix) -> {
                    val real = name.removeSuffix(".tmp")
                    // Mit vorhandenem Eintrag ist die Zwischendatei nur ein Überbleibsel: weder mitnehmen noch zählen.
                    if (real !in present) files += ExportFile("$dir/$real", "$dir/$name")
                }
                else -> skipped++
            }
        }
        return skipped
    }

    private fun isDayOf(day: String, prefix: String): Boolean {
        if (!day.startsWith(prefix)) return false
        return try {
            LocalDate.parse(day)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}

/**
 * Schreibt das Tagebuch als ZIP und prüft es danach, indem es das ZIP vollständig wieder einliest.
 * Liest im Tagebuch nur – dort wird nie etwas verändert.
 * Geht etwas schief, wird die unvollständige ZIP-Datei gelöscht ([deleteTarget]).
 *
 * @param openOutput öffnet die ZIP-Datei zum Schreiben
 * @param openInput öffnet die fertige ZIP-Datei zum Prüfen
 * @param deleteTarget löscht die ZIP-Datei; `true`, wenn das geklappt hat
 * @param isCancelled `true`, wenn der Export abgebrochen werden soll (z. B. Android beendet den Dienst)
 */
class ExportJob(
    private val tree: DocumentTree,
    private val openOutput: () -> OutputStream,
    private val openInput: () -> InputStream,
    private val deleteTarget: () -> Boolean,
    private val isCancelled: () -> Boolean = { false },
    private val report: (ExportState) -> Unit = {},
) {

    class Cancelled : IOException("Export abgebrochen")

    /** Führt den Export aus; Ergebnis ist [ExportState.Done] oder [ExportState.Failed]. */
    fun run(targetName: String): ExportState = try {
        report(ExportState.Preparing)
        val plan = ExportPlanner.plan(tree)
        val written = write(plan.files)
        verify(written)
        ExportState.Done(written.size, plan.skipped)
    } catch (e: Exception) {
        // Jeder Fehler (Speicher voll, Ziel weg, Abbruch, kaputtes ZIP): keine halbe Sicherung liegen lassen.
        val deleted = try {
            deleteTarget()
        } catch (e: Exception) {
            false
        }
        ExportState.Failed(deleted, targetName)
    }

    private fun write(files: List<ExportFile>): List<String> {
        val written = mutableListOf<String>()
        ZipOutputStream(BufferedOutputStream(openOutput(), BUFFER)).use { zip ->
            files.forEachIndexed { index, file ->
                checkCancelled()
                report(ExportState.Writing(index, files.size))
                // Während des Exports gelöscht (z. B. Foto entfernt)? Dann fehlt sie eben – nie abstürzen.
                val input = tree.openRead(file.source) ?: return@forEachIndexed
                input.use {
                    // Fotos sind schon gepackt (JPEG); erneutes Packen kostet nur Zeit.
                    zip.setLevel(if (file.zipPath.endsWith(".jpg")) Deflater.BEST_SPEED else Deflater.DEFAULT_COMPRESSION)
                    zip.putNextEntry(ZipEntry(file.zipPath))
                    it.copyTo(zip, BUFFER)
                    zip.closeEntry()
                }
                written += file.zipPath
            }
            report(ExportState.Writing(files.size, files.size))
        }
        return written
    }

    /** Liest das ZIP komplett wieder ein. Beschädigte Daten fallen dabei über die Prüfsumme auf. */
    private fun verify(expected: List<String>) {
        val found = mutableListOf<String>()
        val sink = ByteArray(BUFFER)
        ZipInputStream(BufferedInputStream(openInput(), BUFFER)).use { zip ->
            while (true) {
                checkCancelled()
                report(ExportState.Verifying(found.size, expected.size))
                val entry = zip.nextEntry ?: break
                while (zip.read(sink) >= 0) Unit // wirft bei falscher Prüfsumme oder Länge
                found += entry.name
            }
        }
        if (found != expected) throw IOException("ZIP unvollständig: ${found.size} statt ${expected.size} Dateien")
    }

    private fun checkCancelled() {
        if (isCancelled()) throw Cancelled()
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
