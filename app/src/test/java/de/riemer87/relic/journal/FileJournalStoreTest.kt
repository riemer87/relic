package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class FileJournalStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val berlin = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 16)

    private fun clockAt(isoInstant: String): Clock = Clock.fixed(Instant.parse(isoInstant), berlin)

    private fun store(clock: Clock = clockAt("2026-10-16T19:14:03Z")) = FileJournalStore(folder.root, clock)

    @Test
    fun `entry is stored in year and month folders named after the day`() {
        assertEquals("2026/10/2026-10-16.md", entryPath(date))
        assertEquals("2026/01/2026-01-05.md", entryPath(LocalDate.of(2026, 1, 5)))

        store().save(date, "Hallo")
        assertTrue(File(folder.root, "2026/10/2026-10-16.md").isFile)
    }

    @Test
    fun `saved text is loaded back`() {
        val store = store()
        assertEquals(SaveResult.SAVED, store.save(date, "Ein guter Tag 🌤"))
        assertEquals("Ein guter Tag 🌤", store.load(date)?.text)
    }

    @Test
    fun `timestamps use local time with offset`() {
        store(clockAt("2026-10-16T19:14:03.987Z")).save(date, "Text")
        val content = File(folder.root, "2026/10/2026-10-16.md").readText()
        assertTrue(content, content.contains("created: 2026-10-16T21:14:03+02:00\n"))
        assertTrue(content, content.contains("updated: 2026-10-16T21:14:03+02:00\n"))
    }

    @Test
    fun `saving again keeps created and changes updated`() {
        store(clockAt("2026-10-16T19:14:03Z")).save(date, "Erste Fassung")
        store(clockAt("2026-10-16T19:20:47Z")).save(date, "Zweite Fassung")

        val entry = store().load(date)!!
        assertEquals(OffsetDateTime.parse("2026-10-16T21:14:03+02:00"), entry.created)
        assertEquals(OffsetDateTime.parse("2026-10-16T21:20:47+02:00"), entry.updated)
        assertEquals("Zweite Fassung", entry.text)
    }

    @Test
    fun `missing day loads as null`() {
        assertNull(store().load(date))
    }

    @Test
    fun `empty text deletes the file`() {
        val store = store()
        store.save(date, "Etwas")
        assertEquals(SaveResult.DELETED, store.save(date, ""))
        assertFalse(store.fileFor(date).exists())
        assertNull(store.load(date))
    }

    @Test
    fun `whitespace only counts as empty`() {
        val store = store()
        store.save(date, "Etwas")
        assertEquals(SaveResult.DELETED, store.save(date, "  \n\n\t "))
        assertFalse(store.fileFor(date).exists())
    }

    @Test
    fun `saving empty text for a day without file creates nothing`() {
        assertEquals(SaveResult.DELETED, store().save(date, ""))
        assertEquals(0, folder.root.walk().filter { it.isFile }.count())
    }

    @Test
    fun `file with photos is kept when text is removed`() {
        val file = store().fileFor(date)
        file.parentFile!!.mkdirs()
        file.writeText("---\ndate: 2026-10-16\n---\n\nText\n\n<!-- relic:photos -->\n![](2026-10-16_1.jpg)\n")

        assertEquals(SaveResult.SAVED, store().save(date, ""))
        val entry = store().load(date)!!
        assertEquals("", entry.text)
        assertEquals(listOf("2026-10-16_1.jpg"), entry.photos)
    }

    @Test
    fun `no temporary files are left behind`() {
        val store = store()
        store.save(date, "Eins")
        store.save(date, "Zwei")
        val names = store.fileFor(date).parentFile!!.list()!!.toList()
        assertEquals(listOf("2026-10-16.md"), names)
    }

    @Test
    fun `leftover temporary file from a crash does not break saving`() {
        val store = store()
        val dir = store.fileFor(date).parentFile!!
        dir.mkdirs()
        File(dir, ".2026-10-16.md.tmp").writeText("halb geschrieben")

        store.save(date, "Neu")
        assertEquals("Neu", store.load(date)?.text)
        assertEquals(listOf("2026-10-16.md"), dir.list()!!.toList())
    }

    @Test
    fun `fields added by other programs survive saving`() {
        val file = store().fileFor(date)
        file.parentFile!!.mkdirs()
        file.writeText("---\ndate: 2026-10-16\ncreated: 2026-10-16T08:00:00+02:00\nmood: gut\n---\n\nAlt\n")

        store().save(date, "Neu")
        val content = file.readText()
        assertTrue(content, content.contains("mood: gut\n"))
        assertTrue(content, content.contains("created: 2026-10-16T08:00:00+02:00\n"))
    }

    @Test
    fun `hand written file without frontmatter is not lost when saving`() {
        val file = store().fileFor(date)
        file.parentFile!!.mkdirs()
        file.writeText("Von Hand geschrieben")

        val store = store()
        assertEquals("Von Hand geschrieben", store.load(date)?.text)
        store.save(date, store.load(date)!!.text + "\nund ergänzt")
        assertEquals("Von Hand geschrieben\nund ergänzt", store.load(date)?.text)
    }

    @Test
    fun `today is the local date shortly before midnight`() {
        // 23:30 in Berlin, aber erst 21:30 UTC.
        assertEquals(LocalDate.of(2026, 10, 16), localToday(clockAt("2026-10-16T21:30:00Z")))
    }

    @Test
    fun `today is the local date shortly after midnight`() {
        // 00:30 in Berlin am 17., in UTC aber noch der 16.
        assertEquals(LocalDate.of(2026, 10, 17), localToday(clockAt("2026-10-16T22:30:00Z")))
    }

    @Test
    fun `winter time uses the winter offset`() {
        val winterDay = LocalDate.of(2026, 12, 24)
        store(clockAt("2026-12-24T17:00:00Z")).save(winterDay, "Heiligabend")
        val content = store().fileFor(winterDay).readText()
        assertTrue(content, content.contains("created: 2026-12-24T18:00:00+01:00\n"))
    }
}
