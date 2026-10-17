package de.riemer87.relic.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class JournalSearchTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(day: Int, text: String) = SearchableEntry(LocalDate.of(2026, 10, day), text, emptyList())

    private val entries = listOf(
        entry(3, "Spaziergang am Fluss"),
        entry(9, "Heute war ein guter Tag.\nAbends gekocht"),
        entry(12, "Am FLUSS gesessen, Äpfel gegessen"),
    )

    @Test
    fun searchIgnoresUpperAndLowerCase() {
        assertEquals(listOf(12, 3), JournalSearch.search(entries, "fluss").map { it.date.dayOfMonth })
        assertEquals(listOf(12, 3), JournalSearch.search(entries, "FlUsS").map { it.date.dayOfMonth })
    }

    @Test
    fun umlautsIgnoreCaseToo() {
        assertEquals(listOf(12), JournalSearch.search(entries, "äpfel").map { it.date.dayOfMonth })
        assertEquals(listOf(12), JournalSearch.search(entries, "ÄPFEL").map { it.date.dayOfMonth })
    }

    @Test
    fun newestFirstAcrossMonthsAndYears() {
        val many = listOf(
            SearchableEntry(LocalDate.of(2025, 12, 24), "Kerze", emptyList()),
            SearchableEntry(LocalDate.of(2026, 1, 2), "Kerze", emptyList()),
            SearchableEntry(LocalDate.of(2024, 3, 1), "Kerze", emptyList()),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2025, 12, 24), LocalDate.of(2024, 3, 1)),
            JournalSearch.search(many, "kerze").map { it.date },
        )
    }

    @Test
    fun partOfAWordAndSeveralWordsAreFound() {
        assertEquals(listOf(3), JournalSearch.search(entries, "ziergang").map { it.date.dayOfMonth })
        assertEquals(listOf(9), JournalSearch.search(entries, "guter tag").map { it.date.dayOfMonth })
    }

    @Test
    fun blankQueryFindsNothingAndSpacesAroundAreIgnored() {
        assertTrue(JournalSearch.search(entries, "").isEmpty())
        assertTrue(JournalSearch.search(entries, "   ").isEmpty())
        assertEquals(listOf(9), JournalSearch.search(entries, "  gekocht ").map { it.date.dayOfMonth })
    }

    @Test
    fun noMatchGivesEmptyList() {
        assertTrue(JournalSearch.search(entries, "Berge").isEmpty())
    }

    @Test
    fun allOccurrencesAreMarkedForHighlighting() {
        val hit = JournalSearch.search(listOf(entry(1, "Tee, dann TEE, dann tee")), "tee").single()
        assertEquals(listOf(0..2, 10..12, 20..22), hit.matches)
        hit.matches.forEach { assertEquals("tee", hit.snippet.substring(it).lowercase()) }
    }

    @Test
    fun matchNearTheStartKeepsTheWholeText() {
        val text = "Am Fluss gesessen und lange geredet."
        assertEquals(text, JournalSearch.snippet(text, "fluss"))
    }

    /** Wunsch Meilenstein 13: Die Fundstelle muss in der Vorschau zu sehen sein, auch wenn sie weit hinten steht. */
    @Test
    fun matchFarBehindIsBroughtToTheFront() {
        val text = "Ein langer Anfang mit vielen Worten, die nichts zur Sache tun und einfach nur Platz brauchen, " +
            "bis endlich der Leuchtturm kommt."
        val snippet = JournalSearch.snippet(text, "leuchtturm")
        assertTrue(snippet, snippet.startsWith(JournalSearch.ELLIPSIS))
        assertTrue(snippet, snippet.indexOf("Leuchtturm") in 1..40)
        assertFalse("beginnt mitten im Wort: $snippet", snippet[1] == ' ')
    }

    @Test
    fun matchInALaterLineStartsAtThatLine() {
        val text = "Eins\nZwei\nDrei\nVier\nFünf mit Leuchtturm"
        assertEquals("…Fünf mit Leuchtturm", JournalSearch.snippet(text, "leuchtturm"))
    }

    @Test
    fun highlightRangesFitTheShortenedSnippet() {
        val text = "x".repeat(100) + " und dann der Leuchtturm"
        val hit = JournalSearch.search(listOf(entry(1, text)), "LEUCHTTURM").single()
        assertEquals("Leuchtturm", hit.snippet.substring(hit.matches.single()))
    }

    // --- Zwischenspeicher ---

    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))

    private fun realStore() = TreeJournalStore(FileDocumentTree(folder.root), clock)

    /** Zählt, wie oft eine Datei gelesen wird. */
    private class CountingStore(private val inner: JournalStore, private val failOn: LocalDate? = null) : JournalStore by inner {
        var loads = 0
        override fun load(date: LocalDate): Entry? {
            loads++
            if (date == failOn) throw IOException("kaputt")
            return inner.load(date)
        }
        override fun openPhoto(date: LocalDate, fileName: String): InputStream? = inner.openPhoto(date, fileName)
    }

    @Test
    fun cacheReadsEachFileOnlyOnce() {
        val store = realStore()
        store.save(LocalDate.of(2026, 10, 9), "Am Fluss\n\nAbends")
        store.save(LocalDate.of(2026, 10, 12), "Regen")
        val counting = CountingStore(store)
        val cache = JournalTextCache(counting)

        val first = cache.sync(store.entryDates())
        val second = cache.sync(store.entryDates())

        assertEquals(2, counting.loads)
        assertEquals(first.toSet(), second.toSet())
        assertEquals("Am Fluss\nAbends", first.single { it.date.dayOfMonth == 9 }.text)
    }

    @Test
    fun forgottenDayIsReadAgainWithItsNewText() {
        val store = realStore()
        val day = LocalDate.of(2026, 10, 9)
        store.save(day, "Alt")
        val cache = JournalTextCache(store)
        cache.sync(store.entryDates())

        store.save(day, "Neu am Fluss")
        cache.forget(day)

        assertEquals(1, JournalSearch.search(cache.sync(store.entryDates()), "fluss").size)
    }

    /** Nach dem Leeren (App war im Hintergrund) wird alles frisch gelesen – auch anderswo geänderte Texte. */
    @Test
    fun clearedCacheReadsChangedFilesAgain() {
        val store = realStore()
        val day = LocalDate.of(2026, 10, 9)
        store.save(day, "Alt")
        val cache = JournalTextCache(store)
        cache.sync(store.entryDates())

        File(folder.root, entryPath(day)).writeText("---\ndate: 2026-10-09\n---\n\nAm PC geändert: Fluss\n")
        cache.clear()

        assertEquals(1, JournalSearch.search(cache.sync(store.entryDates()), "fluss").size)
    }

    @Test
    fun deletedDaysDisappearFromTheCache() {
        val store = realStore()
        val day = LocalDate.of(2026, 10, 9)
        store.save(day, "Fluss")
        val cache = JournalTextCache(store)
        cache.sync(store.entryDates())

        store.save(day, "") // leerer Text ohne Fotos löscht die Datei

        assertTrue(cache.sync(store.entryDates()).isEmpty())
    }

    @Test
    fun unreadableFileIsSkippedAndTriedAgainLater() {
        val store = realStore()
        val broken = LocalDate.of(2026, 10, 9)
        store.save(broken, "Fluss")
        store.save(LocalDate.of(2026, 10, 12), "Fluss")
        val failing = CountingStore(store, failOn = broken)

        val result = JournalTextCache(failing).sync(store.entryDates())

        assertEquals(listOf(12), result.map { it.date.dayOfMonth })
    }

    @Test
    fun searchingNeverChangesTheJournal() {
        val store = realStore()
        store.save(LocalDate.of(2026, 10, 9), "Am Fluss")
        val before = folder.root.walkTopDown().filter(File::isFile).associate { it.path to it.readBytes().toList() }

        JournalSearch.search(JournalTextCache(store).sync(store.entryDates()), "fluss")

        assertEquals(before, folder.root.walkTopDown().filter(File::isFile).associate { it.path to it.readBytes().toList() })
    }
}
