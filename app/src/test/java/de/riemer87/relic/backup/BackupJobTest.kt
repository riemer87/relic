package de.riemer87.relic.backup

import de.riemer87.relic.journal.CrashingDocumentTree
import de.riemer87.relic.journal.DocumentTree
import de.riemer87.relic.journal.FileDocumentTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipFile

class BackupJobTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var journalDir: File
    private lateinit var backupDir: File
    private var clock = LocalDateTime.parse("2026-10-21T14:30")

    @Before
    fun setUp() {
        journalDir = temp.newFolder("journal")
        backupDir = temp.newFolder("backups")
        put("LIES MICH.txt", "Erklärung")
        put("2026/10/2026-10-20.md", "---\ndate: 2026-10-20\n---\n\nAm Fluss")
        put("2026/10/2026-10-20_1.jpg", "foto")
    }

    private fun put(path: String, content: String) {
        val file = File(journalDir, path)
        file.parentFile!!.mkdirs()
        file.writeText(content)
    }

    private fun job(target: DocumentTree = FileDocumentTree(backupDir)) =
        BackupJob(FileDocumentTree(journalDir), target, now = { clock })

    private fun backupNames() = backupDir.list()!!.sorted()

    @Test
    fun `first backup is a complete zip with the journal`() {
        val result = job().run(lastFingerprint = null, force = false)

        assertTrue(result is BackupResult.Done)
        assertEquals(listOf("Relic-Backup-2026-10-21_1430.zip"), backupNames())
        ZipFile(File(backupDir, "Relic-Backup-2026-10-21_1430.zip")).use { zip ->
            assertEquals(
                listOf("2026/10/2026-10-20.md", "2026/10/2026-10-20_1.jpg", "LIES MICH.txt"),
                zip.entries().toList().map { it.name }.sorted(),
            )
        }
        val summary = (result as BackupResult.Done).summary
        assertEquals(1, summary.count)
        assertEquals(File(backupDir, "Relic-Backup-2026-10-21_1430.zip").length(), summary.totalBytes)
    }

    @Test
    fun `unchanged journal makes no new backup`() {
        val first = job().run(null, force = false) as BackupResult.Done
        clock = clock.plusWeeks(1)

        val second = job().run(first.fingerprint, force = false)

        assertTrue(second is BackupResult.Unchanged)
        assertEquals(1, backupNames().size)
    }

    @Test
    fun `back up now creates one even when unchanged`() {
        val first = job().run(null, force = false) as BackupResult.Done
        clock = clock.plusHours(1)

        assertTrue(job().run(first.fingerprint, force = true) is BackupResult.Done)
        assertEquals(2, backupNames().size)
    }

    @Test
    fun `a changed entry is noticed without reading it`() {
        val first = job().run(null, force = false) as BackupResult.Done
        put("2026/10/2026-10-21.md", "---\ndate: 2026-10-21\n---\n\nNeu")
        clock = clock.plusWeeks(1)

        val second = job().run(first.fingerprint, force = false)

        assertTrue(second is BackupResult.Done)
        assertNotEquals(first.fingerprint, (second as BackupResult.Done).fingerprint)
    }

    @Test
    fun `fingerprint changes when a file is edited elsewhere`() {
        val tree = FileDocumentTree(journalDir)
        val before = JournalFingerprint.of(tree)
        File(journalDir, "2026/10/2026-10-20.md").setLastModified(1_000_000)
        assertNotEquals(before, JournalFingerprint.of(tree))
    }

    @Test
    fun `old backups are pruned, foreign files are never touched`() {
        // Fremde Dateien im Sicherungsordner, die zufällig ähnlich heißen.
        val foreign = listOf("Urlaub.zip", "Relic-Export-2026-01-01.zip", "Relic-Backup-Notizen.txt", "Relic-Backup-2026-01-01.zip")
        foreign.forEach { File(backupDir, it).writeText("fremd") }
        var fingerprint: String? = null
        // Ein Jahr lang jede Woche etwas Neues schreiben und sichern.
        clock = LocalDateTime.parse("2025-10-22T14:30")
        repeat(53) { week ->
            put("2026/10/2026-10-20.md", "Woche $week " + "x".repeat(week)) // jede Woche andere Größe
            fingerprint = (job().run(fingerprint, force = false) as BackupResult.Done).fingerprint
            clock = clock.plusWeeks(1)
        }

        val backups = backupNames().filter { BackupNames.parse(it) != null }
        assertTrue("höchstens 5, waren ${backups.size}", backups.size <= 5)
        assertEquals(
            listOf(
                "Relic-Backup-2025-12-31_1430.zip", // Vorjahr
                "Relic-Backup-2026-09-30_1430.zip", // Vormonat und Vorquartal
                "Relic-Backup-2026-10-14_1430.zip",
                "Relic-Backup-2026-10-21_1430.zip",
            ),
            backups,
        )
        foreign.forEach { assertTrue("$it muss bleiben", File(backupDir, it).exists()) }
    }

    @Test
    fun `leftover half backup from a crash is removed and never counted`() {
        File(backupDir, "Relic-Backup-2026-10-14_0900.zip.part").writeText("halb")

        val result = job().run(null, force = false) as BackupResult.Done

        assertEquals(listOf("Relic-Backup-2026-10-21_1430.zip"), backupNames())
        assertEquals(1, result.summary.count)
    }

    @Test
    fun `failed backup leaves no half file and keeps the old backups`() {
        val first = job().run(null, force = false) as BackupResult.Done
        put("2026/10/2026-10-21.md", "Neu")
        clock = clock.plusWeeks(1)
        // Speicher voll: Schreiben bricht nach ein paar Bytes ab.
        val full = object : DocumentTree by FileDocumentTree(backupDir) {
            override fun openWrite(path: String): OutputStream {
                val real = FileDocumentTree(backupDir).openWrite(path)
                return object : OutputStream() {
                    var written = 0
                    override fun write(b: Int) {
                        if (++written > 10) throw IOException("Kein Platz")
                        real.write(b)
                    }
                    override fun close() = real.close()
                }
            }
        }

        val result = job(full).run(first.fingerprint, force = false)

        assertEquals(BackupResult.Failed, result)
        assertEquals(listOf("Relic-Backup-2026-10-21_1430.zip"), backupNames())
    }

    @Test
    fun `crash while renaming keeps no half file`() {
        val crashing = CrashingDocumentTree(FileDocumentTree(backupDir)) { operation, _ -> operation == "rename" }

        assertEquals(BackupResult.Failed, job(crashing).run(null, force = false))
        assertEquals(emptyList<String>(), backupNames())
    }

    /** Direkt nach der Einführung gibt es noch keinen Eintrag: keine fast leere ZIP-Datei anlegen. */
    @Test
    fun `journal without entries is not backed up`() {
        File(journalDir, "2026").deleteRecursively()

        assertTrue(job().run(lastFingerprint = null, force = true) is BackupResult.Unchanged)
        assertEquals(emptyList<String>(), backupNames())

        put("2026/10/2026-10-21.md", "Erster Eintrag")
        assertTrue(job().run(lastFingerprint = null, force = false) is BackupResult.Done)
    }

    @Test
    fun `missing backup folder is a failure, not a crash`() {
        backupDir.deleteRecursively()
        assertEquals(BackupResult.Failed, job().run(null, force = false))
    }

    @Test
    fun `journal is only read, never changed`() {
        val before = journalDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(journalDir).path to it.readText() }
        job().run(null, force = false)
        val after = journalDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(journalDir).path to it.readText() }
        assertEquals(before, after)
    }
}
