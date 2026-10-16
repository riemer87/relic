package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class EntryFormatTest {

    private val date = LocalDate.of(2026, 10, 9)
    private val created = OffsetDateTime.parse("2026-10-09T21:14:03+02:00")
    private val updated = OffsetDateTime.parse("2026-10-09T21:20:47+02:00")

    @Test
    fun `serialize writes the documented format`() {
        val entry = Entry(
            date = date,
            created = created,
            updated = updated,
            text = "Heute war ein **guter** Tag.\n\n- Spaziergang am Fluss\n- Abends gekocht",
            photos = listOf("2026-10-09_1.jpg", "2026-10-09_2.jpg"),
        )

        val expected = """
            ---
            date: 2026-10-09
            created: 2026-10-09T21:14:03+02:00
            updated: 2026-10-09T21:20:47+02:00
            ---

            Heute war ein **guter** Tag.

            - Spaziergang am Fluss
            - Abends gekocht

            <!-- relic:photos -->
            ![](2026-10-09_1.jpg)
            ![](2026-10-09_2.jpg)

        """.trimIndent()
        assertEquals(expected, EntryFormat.serialize(entry))
    }

    @Test
    fun `serialize without photos writes no photo marker`() {
        val content = EntryFormat.serialize(Entry(date, created, updated, "Nur Text"))
        assertFalse(content.contains(EntryFormat.PHOTOS_MARKER))
        assertEquals(
            "---\ndate: 2026-10-09\ncreated: 2026-10-09T21:14:03+02:00\nupdated: 2026-10-09T21:20:47+02:00\n---\n\nNur Text\n",
            content,
        )
    }

    @Test
    fun `timestamps keep seconds even when they are zero`() {
        assertEquals(
            "2026-01-05T08:00:00+01:00",
            EntryFormat.formatTimestamp(OffsetDateTime.parse("2026-01-05T08:00+01:00")),
        )
    }

    @Test
    fun `round trip keeps tricky text unchanged`() {
        val texts = listOf(
            "Umlaute: äöüß ÄÖÜ – „Anführungszeichen“",
            "Emoji 🎉👩‍👩‍👧 und Akzente: café, naïve",
            "Absatz eins\n\n\n\nAbsatz zwei nach vielen Leerzeilen",
            "Text mit Trennlinie\n---\nund weiter",
            "---\nbeginnt mit einer Trennlinie",
            "date: 2020-01-01\ncreated: kein Datum",
            "Eingerückt:\n    Code-Block\n\t Tab",
            "a".repeat(5000),
        )
        for (text in texts) {
            val entry = Entry(date, created, updated, text, photos = listOf("2026-10-09_1.jpg"))
            val parsed = EntryFormat.parse(EntryFormat.serialize(entry), date)
            assertEquals(entry, parsed)
        }
    }

    @Test
    fun `parse reads photos and separates them from the text`() {
        val content = "---\ndate: 2026-10-09\n---\n\nText\n\n<!-- relic:photos -->\n![](2026-10-09_1.jpg)\n![Bild](2026-10-09_2.jpg)\n"
        val entry = EntryFormat.parse(content, date)
        assertEquals("Text", entry.text)
        assertEquals(listOf("2026-10-09_1.jpg", "2026-10-09_2.jpg"), entry.photos)
    }

    @Test
    fun `parse handles windows line endings and byte order mark`() {
        val content = Char(0xFEFF) + "---\r\ndate: 2026-10-09\r\ncreated: 2026-10-09T21:14:03+02:00\r\n---\r\n\r\nZeile 1\r\nZeile 2\r\n"
        val entry = EntryFormat.parse(content, date)
        assertEquals("Zeile 1\nZeile 2", entry.text)
        assertEquals(created, entry.created)
    }

    @Test
    fun `file without frontmatter is read completely as text`() {
        val entry = EntryFormat.parse("Einfach nur Text\nvon Hand geschrieben", date)
        assertEquals("Einfach nur Text\nvon Hand geschrieben", entry.text)
        assertNull(entry.created)
        assertNull(entry.updated)
    }

    @Test
    fun `unclosed frontmatter is kept as text instead of being lost`() {
        val content = "---\ndate: 2026-10-09\nHier fehlt das Ende"
        assertEquals(content, EntryFormat.parse(content, date).text)
    }

    @Test
    fun `broken timestamps are ignored without crashing`() {
        val content = "---\ncreated: gestern Abend\nupdated:\n---\n\nText"
        val entry = EntryFormat.parse(content, date)
        assertNull(entry.created)
        assertNull(entry.updated)
        assertEquals("Text", entry.text)
    }

    @Test
    fun `date from file name wins over date in frontmatter`() {
        val entry = EntryFormat.parse("---\ndate: 1999-01-01\n---\n\nText", date)
        assertEquals(date, entry.date)
    }

    @Test
    fun `unknown frontmatter fields survive a round trip`() {
        val content = "---\ndate: 2026-10-09\ntags:\n  - urlaub\nmood: gut\n---\n\nText\n"
        val entry = EntryFormat.parse(content, date)
        assertEquals(listOf("tags:", "  - urlaub", "mood: gut"), entry.extraFrontmatter)
        assertEquals(entry, EntryFormat.parse(EntryFormat.serialize(entry), date))
    }

    @Test
    fun `empty content gives empty text`() {
        assertEquals("", EntryFormat.parse("", date).text)
    }
}
