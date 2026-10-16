package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JournalFolderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tree get() = FileDocumentTree(folder.root)

    private fun file(path: String, content: String = "") =
        File(folder.root, path).apply { parentFile!!.mkdirs(); writeText(content) }

    // --- Erkennen eines vorhandenen Tagebuchs ---

    @Test
    fun `empty folder is not a journal`() {
        assertFalse(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `folder with German readme is a journal`() {
        file("LIES MICH.txt")
        assertTrue(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `folder with English readme is a journal`() {
        file("README.txt")
        assertTrue(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `folder with an entry in year and month folders is a journal`() {
        file("2026/10/2026-10-09.md")
        assertTrue(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `random files and folders are not a journal`() {
        file("Einkaufsliste.md")
        file("2026/Urlaub.jpg")
        file("2026/10/notizen.md")
        file("Fotos/2026/10/2026-10-09.md")
        assertFalse(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `entry in the wrong month folder does not count`() {
        file("2026/11/2026-10-09.md")
        assertFalse(JournalFolder.looksLikeJournal(tree))
    }

    @Test
    fun `missing folder is not a journal`() {
        assertFalse(JournalFolder.looksLikeJournal(FileDocumentTree(File(folder.root, "gibt-es-nicht"))))
    }

    // --- Entscheidung nach der Auswahl ---

    @Test
    fun `new folder without journal is used directly`() {
        assertEquals(FolderDecision.USE, JournalFolder.decide(FolderOption.RECOMMENDED, looksLikeJournal = false))
        assertEquals(FolderDecision.USE, JournalFolder.decide(FolderOption.OWN, looksLikeJournal = false))
    }

    @Test
    fun `new folder that already holds a journal asks to connect it`() {
        assertEquals(
            FolderDecision.ASK_CONNECT_EXISTING,
            JournalFolder.decide(FolderOption.RECOMMENDED, looksLikeJournal = true),
        )
        assertEquals(FolderDecision.ASK_CONNECT_EXISTING, JournalFolder.decide(FolderOption.OWN, looksLikeJournal = true))
    }

    @Test
    fun `existing journal is connected directly`() {
        assertEquals(FolderDecision.USE, JournalFolder.decide(FolderOption.EXISTING, looksLikeJournal = true))
    }

    @Test
    fun `existing option without journal asks before using the folder`() {
        assertEquals(
            FolderDecision.ASK_USE_WITHOUT_JOURNAL,
            JournalFolder.decide(FolderOption.EXISTING, looksLikeJournal = false),
        )
    }

    // --- Erklär-Datei ---

    @Test
    fun `readme is created when missing`() {
        JournalFolder.ensureReadme(tree, "LIES MICH.txt", "Erklärung")
        assertEquals("Erklärung", File(folder.root, "LIES MICH.txt").readText())
    }

    @Test
    fun `existing readme is never overwritten`() {
        file("LIES MICH.txt", "Von mir ergänzt")
        JournalFolder.ensureReadme(tree, "LIES MICH.txt", "Neue Erklärung")
        assertEquals("Von mir ergänzt", File(folder.root, "LIES MICH.txt").readText())
    }

    @Test
    fun `readme in the other language counts as present`() {
        file("README.txt", "English")
        JournalFolder.ensureReadme(tree, "LIES MICH.txt", "Deutsch")
        assertFalse(File(folder.root, "LIES MICH.txt").exists())
    }
}
