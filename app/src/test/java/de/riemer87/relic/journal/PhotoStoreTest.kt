package de.riemer87.relic.journal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PhotoStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val date = LocalDate.of(2026, 10, 16)
    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))

    private val journal get() = File(folder.root, "journal").apply { mkdirs() }
    private val dayDir get() = File(journal, "2026/10")
    private fun store(tree: DocumentTree = FileDocumentTree(journal)) = TreeJournalStore(tree, clock)

    private var counter = 0

    /** Ein „aufbereitetes“ Foto im Zwischenspeicher der App, mit erkennbarem Inhalt. */
    private fun newPhoto(content: String = "foto-${++counter}"): NewPhoto {
        val file = File(folder.root, "cache/$content.jpg").apply { parentFile!!.mkdirs(); writeText(content) }
        return NewPhoto(file)
    }

    private fun photoContent(name: String) = File(dayDir, name).readText()
    private fun filesInDay() = dayDir.list()!!.sorted()

    // --- Namen ---

    @Test
    fun `photo names count up from one`() {
        assertEquals("2026-10-16_1.jpg", PhotoNames.nextFree(date, emptySet()))
        assertEquals("2026-10-16_3.jpg", PhotoNames.nextFree(date, setOf("2026-10-16_1.jpg", "2026-10-16_2.jpg")))
        assertEquals("2026-10-16_1.jpg", PhotoNames.nextFree(date, setOf("2026-10-16_2.jpg")))
    }

    @Test
    fun `only own photo names of that day count as deletable`() {
        assertTrue(PhotoNames.belongsTo("2026-10-16_1.jpg", date))
        assertTrue(PhotoNames.belongsTo("2026-10-16_12.jpg", date))
        assertFalse(PhotoNames.belongsTo("2026-10-15_1.jpg", date))
        assertFalse(PhotoNames.belongsTo("Urlaub.jpg", date))
        assertFalse(PhotoNames.belongsTo("2026-10-16_1.png", date))
        assertFalse(PhotoNames.belongsTo("2026-10-16.md", date))
    }

    // --- Speichern ---

    @Test
    fun `new photos are written next to the entry and listed in it`() {
        store().save(date, "Mit Fotos", listOf(newPhoto("a"), newPhoto("b")))

        assertEquals(listOf("2026-10-16.md", "2026-10-16_1.jpg", "2026-10-16_2.jpg"), filesInDay())
        assertEquals("a", photoContent("2026-10-16_1.jpg"))
        assertEquals("b", photoContent("2026-10-16_2.jpg"))
        assertEquals(listOf("2026-10-16_1.jpg", "2026-10-16_2.jpg"), store().load(date)!!.photos)
        assertTrue(File(dayDir, "2026-10-16.md").readText().contains("<!-- relic:photos -->\n![](2026-10-16_1.jpg)\n![](2026-10-16_2.jpg)\n"))
    }

    @Test
    fun `photos without text make a valid entry`() {
        assertEquals(SaveResult.SAVED, store().save(date, "", listOf(newPhoto())))
        val entry = store().load(date)!!
        assertEquals("", entry.text)
        assertEquals(1, entry.photos.size)
    }

    @Test
    fun `adding more photos later keeps the old ones`() {
        store().save(date, "Text", listOf(newPhoto("a")))
        store().save(date, "Text", listOf(StoredPhoto("2026-10-16_1.jpg"), newPhoto("b")))

        assertEquals(listOf("2026-10-16_1.jpg", "2026-10-16_2.jpg"), store().load(date)!!.photos)
        assertEquals("a", photoContent("2026-10-16_1.jpg"))
        assertEquals("b", photoContent("2026-10-16_2.jpg"))
    }

    @Test
    fun `removed photo file is deleted`() {
        store().save(date, "Text", listOf(newPhoto("a"), newPhoto("b")))
        store().save(date, "Text", listOf(StoredPhoto("2026-10-16_2.jpg")))

        assertEquals(listOf("2026-10-16_2.jpg"), store().load(date)!!.photos)
        assertEquals(listOf("2026-10-16.md", "2026-10-16_2.jpg"), filesInDay())
    }

    @Test
    fun `order of photos follows the given list`() {
        store().save(date, "Text", listOf(newPhoto("a"), newPhoto("b")))
        store().save(date, "Text", listOf(StoredPhoto("2026-10-16_2.jpg"), StoredPhoto("2026-10-16_1.jpg")))
        assertEquals(listOf("2026-10-16_2.jpg", "2026-10-16_1.jpg"), store().load(date)!!.photos)
    }

    @Test
    fun `replacing a photo never overwrites one the entry still uses`() {
        store().save(date, "Text", (1..4).map { newPhoto("alt$it") })
        val keep = listOf("2026-10-16_1.jpg", "2026-10-16_3.jpg", "2026-10-16_4.jpg").map(::StoredPhoto)

        store().save(date, "Text", keep + newPhoto("neu"))

        assertEquals(keep.map { it.fileName } + "2026-10-16_5.jpg", store().load(date)!!.photos)
        assertEquals("neu", photoContent("2026-10-16_5.jpg"))
        assertFalse(File(dayDir, "2026-10-16_2.jpg").exists())
    }

    @Test
    fun `foreign files are never overwritten or deleted`() {
        dayDir.mkdirs()
        File(dayDir, "2026-10-16_1.jpg").writeText("fremd")
        File(dayDir, "Urlaub.jpg").writeText("fremd")

        store().save(date, "Text", listOf(newPhoto("meins")))
        assertEquals(listOf("2026-10-16_2.jpg"), store().load(date)!!.photos)
        assertEquals("fremd", photoContent("2026-10-16_1.jpg"))

        store().save(date, "", emptyList())
        assertEquals(listOf("2026-10-16_1.jpg", "Urlaub.jpg"), filesInDay())
    }

    @Test
    fun `photo referenced by hand with a foreign name is kept when removed from the entry`() {
        dayDir.mkdirs()
        File(dayDir, "Urlaub.jpg").writeText("von Hand")
        File(dayDir, "2026-10-16.md").writeText("---\ndate: 2026-10-16\n---\n\nText\n\n<!-- relic:photos -->\n![](Urlaub.jpg)\n")

        store().save(date, "Text", emptyList())
        assertTrue(File(dayDir, "Urlaub.jpg").exists())
    }

    @Test
    fun `removing all photos and text deletes entry and photos`() {
        store().save(date, "Text", listOf(newPhoto(), newPhoto()))
        assertEquals(SaveResult.DELETED, store().save(date, "", emptyList()))
        assertNull(store().load(date))
        assertEquals(emptyList<String>(), filesInDay())
    }

    @Test
    fun `saving text only keeps photos untouched`() {
        store().save(date, "Text", listOf(newPhoto("a")))
        store().save(date, "Neuer Text")
        assertEquals(listOf("2026-10-16_1.jpg"), store().load(date)!!.photos)
        assertEquals("a", photoContent("2026-10-16_1.jpg"))
    }

    @Test
    fun `photo bytes are stored unchanged`() {
        val bytes = ByteArray(300_000) { (it * 31 % 256).toByte() }
        val file = File(folder.root, "cache/binaer.jpg").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        store().save(date, "", listOf(NewPhoto(file)))
        assertArrayEquals(bytes, File(dayDir, "2026-10-16_1.jpg").readBytes())
    }

    @Test
    fun `no temporary files are left behind`() {
        store().save(date, "Text", listOf(newPhoto(), newPhoto()))
        assertTrue(filesInDay().none { it.endsWith(".tmp") })
    }

    // --- Abstürze ---

    private fun saveWithCrash(photos: List<DayPhoto>, crashOn: (String, String) -> Boolean) {
        try {
            store(CrashingDocumentTree(FileDocumentTree(journal), crashOn)).save(date, "Neu", photos)
            fail("Absturz wurde nicht ausgelöst")
        } catch (e: CrashingDocumentTree.SimulatedCrash) {
            // erwartet
        }
    }

    @Test
    fun `crash while writing a new photo leaves the old entry intact`() {
        store().save(date, "Alt", listOf(newPhoto("a")))
        saveWithCrash(listOf(StoredPhoto("2026-10-16_1.jpg"), newPhoto("b"))) { op, path -> op == "write" && path.endsWith("_2.jpg.tmp") }

        val entry = store().load(date)!!
        assertEquals("Alt", entry.text)
        assertEquals(listOf("2026-10-16_1.jpg"), entry.photos)
        assertEquals("a", photoContent("2026-10-16_1.jpg"))
    }

    @Test
    fun `crash after new photos before the entry leaves only an extra file`() {
        store().save(date, "Alt", listOf(newPhoto("a")))
        saveWithCrash(listOf(StoredPhoto("2026-10-16_1.jpg"), newPhoto("b"))) { op, path -> op == "write" && path.endsWith(".md.tmp") }

        val entry = store().load(date)!!
        assertEquals("Alt", entry.text)
        assertEquals(listOf("2026-10-16_1.jpg"), entry.photos)
        // Das neue Foto liegt schon da, wird aber beim nächsten Speichern nicht überschrieben.
        store().save(date, "Weiter", listOf(StoredPhoto("2026-10-16_1.jpg"), newPhoto("c")))
        assertEquals("b", photoContent("2026-10-16_2.jpg"))
        assertEquals(listOf("2026-10-16_1.jpg", "2026-10-16_3.jpg"), store().load(date)!!.photos)
    }

    @Test
    fun `crash before deleting a removed photo never leaves a missing photo`() {
        store().save(date, "Alt", listOf(newPhoto("a"), newPhoto("b")))
        saveWithCrash(listOf(StoredPhoto("2026-10-16_2.jpg"))) { op, path -> op == "delete" && path.endsWith("_1.jpg") }

        val entry = store().load(date)!!
        assertEquals(listOf("2026-10-16_2.jpg"), entry.photos)
        entry.photos.forEach { assertTrue("$it fehlt", File(dayDir, it).exists()) }
    }
}
