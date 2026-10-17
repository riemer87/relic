package de.riemer87.relic.export

import de.riemer87.relic.journal.DocumentTree
import de.riemer87.relic.journal.FileDocumentTree
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class ExportJobTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var journalDir: File
    private lateinit var tree: DocumentTree
    private lateinit var zipFile: File
    private var deleteCalls = 0

    @Before
    fun setUp() {
        journalDir = temp.newFolder("journal")
        tree = FileDocumentTree(journalDir)
        zipFile = File(temp.root, "export.zip")
    }

    private fun put(path: String, content: String) = put(path, content.toByteArray())

    private fun put(path: String, content: ByteArray) {
        val file = File(journalDir, path)
        file.parentFile!!.mkdirs()
        file.writeBytes(content)
    }

    /** Zufällige Bytes wie ein Foto, damit Byte-für-Byte-Vergleiche aussagekräftig sind. */
    private fun photoBytes(seed: Int) = ByteArray(200_000) { ((it * 31 + seed * 17) % 251).toByte() }

    private fun job(
        source: DocumentTree = tree,
        openOutput: () -> OutputStream = { FileOutputStream(zipFile) },
        openInput: () -> InputStream = { zipFile.inputStream() },
        deleteSucceeds: Boolean = true,
        isCancelled: () -> Boolean = { false },
    ) = ExportJob(
        tree = source,
        openOutput = openOutput,
        openInput = openInput,
        deleteTarget = {
            deleteCalls++
            deleteSucceeds && zipFile.delete()
        },
        isCancelled = isCancelled,
    )

    private fun zipContents(): Map<String, ByteArray> = ZipFile(zipFile).use { zip ->
        zip.entries().toList().associate { it.name to zip.getInputStream(it).readBytes() }
    }

    /** Fingerabdruck des ganzen Tagebuch-Ordners: alle Pfade mit Prüfsumme ihres Inhalts. */
    private fun snapshot(): Map<String, String> = journalDir.walkTopDown().filter { it.isFile }.associate { file ->
        file.relativeTo(journalDir).invariantSeparatorsPath to
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun sampleJournal() {
        put("LIES MICH.txt", "Das ist dein Relic-Tagebuch.")
        put("2026/10/2026-10-09.md", "---\ndate: 2026-10-09\n---\n\nHeute war ein **guter** Tag. Größe ✓\n")
        put("2026/10/2026-10-09_1.jpg", photoBytes(1))
        put("2026/10/2026-10-09_2.jpg", photoBytes(2))
        put("2025/12/2025-12-24.md", "Weihnachten")
    }

    @Test
    fun zipContainsEveryRelicFileByteForByte() {
        sampleJournal()

        val result = job().run("export.zip")

        assertEquals(ExportState.Done(files = 5, skipped = 0), result)
        val zip = zipContents()
        assertEquals(
            setOf("LIES MICH.txt", "2025/12/2025-12-24.md", "2026/10/2026-10-09.md", "2026/10/2026-10-09_1.jpg", "2026/10/2026-10-09_2.jpg"),
            zip.keys,
        )
        for ((path, bytes) in zip) assertArrayEquals(path, File(journalDir, path).readBytes(), bytes)
    }

    @Test
    fun exportNeverChangesTheJournal() {
        sampleJournal()
        put("2026/10/2026-10-12.md.tmp", "gerettet")
        put("Notizen/eigene.txt", "fremd")
        val before = snapshot()

        job().run("export.zip")

        assertEquals(before, snapshot())
    }

    @Test
    fun onlyRelicFilesAreExportedAndOthersAreCounted() {
        sampleJournal()
        put("Einkaufsliste.txt", "Milch") // fremde Datei in der Wurzel
        put(".obsidian/config", "{}") // fremder Ordner
        put("2026/10/notizen.txt", "fremd") // fremde Datei im Monatsordner
        put("2026/10/2026-10-09_1.png", "kein Relic-Foto")
        put("2026/Fotos/bild.jpg", "fremder Unterordner")
        put("2026/10/2026-11-01.md", "falscher Monatsordner")
        put("2026/02/2026-02-30.md", "kein echtes Datum")

        val result = job().run("export.zip")

        assertEquals(ExportState.Done(files = 5, skipped = 7), result)
        assertFalse(zipContents().keys.any { "notizen" in it || "Fotos" in it || it.endsWith(".png") || "obsidian" in it })
    }

    /** Absturz beim Speichern: Nur die Zwischendatei ist übrig – sie ist der Eintrag und kommt richtig benannt mit. */
    @Test
    fun orphanTempFileIsExportedAsTheEntry() {
        put("2026/10/2026-10-12.md.tmp", "Gerettet")

        val result = job().run("export.zip")

        assertEquals(ExportState.Done(files = 1, skipped = 0), result)
        assertEquals("Gerettet", String(zipContents().getValue("2026/10/2026-10-12.md")))
    }

    /** Liegt der Eintrag daneben, ist die Zwischendatei nur ein Überbleibsel: nicht mitnehmen, nicht als fremd zählen. */
    @Test
    fun leftoverTempFileNextToEntryIsIgnored() {
        put("2026/10/2026-10-12.md", "Richtig")
        put("2026/10/2026-10-12.md.tmp", "Alt")

        val result = job().run("export.zip")

        assertEquals(ExportState.Done(files = 1, skipped = 0), result)
        assertEquals("Richtig", String(zipContents().getValue("2026/10/2026-10-12.md")))
    }

    @Test
    fun emptyJournalGivesEmptyButValidZip() {
        val result = job().run("export.zip")

        assertEquals(ExportState.Done(files = 0, skipped = 0), result)
        assertTrue(zipContents().isEmpty())
    }

    /** Speicher voll mitten im Schreiben: keine halbe ZIP-Datei liegen lassen. */
    @Test
    fun failureWhileWritingDeletesTheIncompleteZip() {
        sampleJournal()
        val failing = {
            object : OutputStream() {
                val real = FileOutputStream(zipFile)
                var count = 0
                override fun write(b: Int) {
                    if (++count > 2_000) throw IOException("Kein Speicherplatz")
                    real.write(b)
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    count += len
                    if (count > 2_000) throw IOException("Kein Speicherplatz")
                    real.write(b, off, len)
                }
                override fun close() = real.close()
            }
        }

        val result = job(openOutput = failing).run("export.zip")

        assertEquals(ExportState.Failed(targetDeleted = true, targetName = "export.zip"), result)
        assertEquals(1, deleteCalls)
        assertFalse(zipFile.exists())
    }

    /** Die Nachprüfung bemerkt ein beschädigtes ZIP (z. B. Fehler beim Speicherziel). */
    @Test
    fun corruptedZipIsDetectedAndDeleted() {
        sampleJournal()
        val corrupted = {
            val bytes = zipFile.readBytes()
            bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
            ByteArrayInputStream(bytes)
        }

        val result = job(openInput = corrupted).run("export.zip")

        assertEquals(ExportState.Failed(targetDeleted = true, targetName = "export.zip"), result)
        assertFalse(zipFile.exists())
    }

    /** Ein ZIP, dem hinten Dateien fehlen, gilt nicht als fertig. */
    @Test
    fun truncatedZipIsDetected() {
        sampleJournal()
        val truncated = { ByteArrayInputStream(zipFile.readBytes().let { it.copyOf(it.size / 3) }) }

        val result = job(openInput = truncated).run("export.zip")

        assertTrue(result is ExportState.Failed)
    }

    @Test
    fun cancelledExportDeletesTheIncompleteZip() {
        sampleJournal()
        var checks = 0

        val result = job(isCancelled = { ++checks > 2 }).run("export.zip")

        assertEquals(ExportState.Failed(targetDeleted = true, targetName = "export.zip"), result)
        assertFalse(zipFile.exists())
    }

    /** Lässt sich die halbe Datei nicht löschen, muss die Meldung das sagen (damit die Nutzerin sie selbst löscht). */
    @Test
    fun failedDeletionIsReported() {
        sampleJournal()

        val result = job(openOutput = { throw IOException("Ziel weg") }, deleteSucceeds = false).run("Relic-Export.zip")

        assertEquals(ExportState.Failed(targetDeleted = false, targetName = "Relic-Export.zip"), result)
    }

    /** Ein Foto wird während des Exports entfernt: kein Absturz, der Rest kommt vollständig mit. */
    @Test
    fun fileVanishingDuringExportIsSkipped() {
        sampleJournal()
        val vanishing = object : DocumentTree by tree {
            override fun openRead(path: String): InputStream? =
                if (path.endsWith("_2.jpg")) null else tree.openRead(path)
        }

        val result = job(source = vanishing).run("export.zip")

        assertEquals(ExportState.Done(files = 4, skipped = 0), result)
        assertFalse("2026/10/2026-10-09_2.jpg" in zipContents())
    }

    @Test
    fun unreadableJournalFails() {
        val missing = FileDocumentTree(File(temp.root, "gibt-es-nicht"))

        val result = job(source = missing).run("export.zip")

        assertTrue(result is ExportState.Failed)
    }
}
