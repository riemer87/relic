package de.riemer87.relic.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ArchiveFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // Freitag, 16. Oktober 2026, 21:00 in Berlin
    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.of(2026, 10, 16)

    private fun str(id: Int) = rule.activity.getString(id)
    private fun monthYear(year: Int, month: Int) = DateText.monthYear(LocalDate.of(year, month, 1), rule.activity.resources.configuration.locales[0])

    private fun store() = FakeJournalStore(
        mapOf(
            today to "Heute geschrieben",
            LocalDate.of(2026, 10, 3) to "Anfang Oktober",
            LocalDate.of(2026, 9, 20) to "Spätsommer im September",
            LocalDate.of(2025, 12, 24) to "Heiligabend 2025",
        ),
    )

    private fun start(store: FakeJournalStore) {
        rule.setContent { RelicTheme { RelicApp(store, clock, photoPreparer = { null }) } }
        rule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openArchive() {
        waitForText(str(R.string.archive_title))
        rule.onAllNodes(hasText(str(R.string.archive_title))).onFirst().performClick() // Knopf oben rechts
        rule.waitForIdle()
        waitForText(monthYear(2026, 10))
    }

    private fun tap(description: String) {
        rule.onNode(hasContentDescription(description)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun olderEntriesAreInTheArchiveNotInTheTimeline() {
        start(store())
        waitForText("Heute geschrieben")
        rule.onNode(hasText("Anfang Oktober")).assertDoesNotExist()

        openArchive()
        waitForText("Anfang Oktober")
        rule.onNode(hasText("Heute geschrieben")).assertExists()
    }

    @Test
    fun archiveCannotGoIntoTheFuture() {
        start(store())
        openArchive()
        rule.onNode(hasContentDescription(str(R.string.month_next))).assertIsNotEnabled()
    }

    /** Wunsch aus Meilenstein 5: Zurück aus einem Eintrag landet im selben Monat. */
    @Test
    fun goingBackFromAnEntryKeepsTheMonth() {
        start(store())
        openArchive()
        tap(str(R.string.month_previous))
        waitForText("Spätsommer im September")

        rule.onNode(hasText("Spätsommer im September")).performClick()
        waitForText(str(R.string.entry_title_past))
        tap(str(R.string.action_back))

        waitForText("Spätsommer im September")
        rule.onNode(hasText(monthYear(2026, 9))).assertExists()
    }

    /**
     * Fehler aus Meilenstein 5: Von Januar mit ‹ ins Vorjahr und wieder zurück – das Vorjahr verschwand
     * danach aus der Jahresliste. Jetzt ist die Liste fest: 5 Jahre zurück, leere Jahre inklusive.
     */
    @Test
    fun yearListStaysTheSameAfterBrowsingIntoAnEmptyYear() {
        start(FakeJournalStore(mapOf(today to "Nur dieses Jahr")))
        openArchive()
        repeat(9) { tap(str(R.string.month_previous)) } // Oktober → Januar 2026
        waitForText(monthYear(2026, 1))
        tap(str(R.string.month_previous))
        waitForText(monthYear(2025, 12))
        tap(str(R.string.month_next))
        waitForText(monthYear(2026, 1))

        rule.onNode(hasText(monthYear(2026, 1))).performClick() // Auswahl aufklappen
        rule.waitForIdle()
        listOf("2026", "2025", "2024", "2023", "2022", "2021").forEach { rule.onNode(hasText(it)).assertExists() }
        rule.onNode(hasText("2020")).assertDoesNotExist()
    }

    @Test
    fun archiveStopsAtJanuaryOfTheOldestYear() {
        start(FakeJournalStore(mapOf(today to "Nur dieses Jahr")))
        openArchive()
        rule.onNode(hasText(monthYear(2026, 10))).performClick()
        rule.onNode(hasText("2021")).performClick()
        val january = java.time.Month.JANUARY.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, rule.activity.resources.configuration.locales[0]).removeSuffix(".")
        rule.onNode(hasText(january)).performClick()
        waitForText(monthYear(2021, 1))

        rule.onNode(hasContentDescription(str(R.string.month_previous))).assertIsNotEnabled()
    }

    @Test
    fun monthPickerJumpsToAnotherYear() {
        start(store())
        openArchive()
        rule.onNode(hasText(monthYear(2026, 10))).performClick()
        rule.onNode(hasText("2025")).performClick()
        val december = java.time.Month.DECEMBER.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, rule.activity.resources.configuration.locales[0]).removeSuffix(".")
        rule.onNode(hasText(december)).performClick()

        waitForText("Heiligabend 2025")
        rule.onNode(hasText(monthYear(2025, 12))).assertExists()
    }

    /** Wunsch aus Meilenstein 5: In der Tagesauswahl sind Tage mit Eintrag markiert. */
    @Test
    fun dayPickerMarksDaysWithEntriesAndBlocksTheFuture() {
        start(store())
        openArchive()
        rule.onNode(hasText(str(R.string.archive_add_day))).performClick()
        waitForText(str(R.string.picker_prompt))

        val hasEntry = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.picker_has_entry))
        rule.onNode(hasContentDescription("3").and(hasEntry)).assertExists()
        rule.onNode(hasContentDescription("16").and(hasEntry)).assertExists()
        rule.onNode(hasContentDescription("4").and(hasEntry)).assertDoesNotExist()
        rule.onNode(hasContentDescription("17")).assertIsNotEnabled()
    }

    @Test
    fun aMissingOldDayCanBeAddedFromTheArchive() {
        val store = store()
        start(store)
        openArchive()
        tap(str(R.string.month_previous))
        rule.onNode(hasText(str(R.string.archive_add_day))).performClick()
        waitForText(str(R.string.picker_prompt))

        rule.onNode(hasContentDescription("5")).performClick()
        rule.onNode(hasText(str(R.string.picker_open))).performClick()
        rule.waitForIdle()

        waitForText(str(R.string.entry_title_past))
        rule.onNode(hasSetTextAction()).performTextInput("Nachgetragen")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasSetTextAction().and(hasText("Nachgetragen"))).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(str(R.string.entry_save))).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { store.textOf(LocalDate.of(2026, 9, 5)) != null }
        rule.waitForIdle()

        assertEquals("Nachgetragen", store.textOf(LocalDate.of(2026, 9, 5)))
        waitForText("Nachgetragen") // zurück im Archiv, September, mit dem neuen Eintrag
        rule.onNode(hasText(monthYear(2026, 9))).assertExists()
    }

    // --- Suche (Meilenstein 13) ---

    private fun searchStore() = FakeJournalStore(
        mapOf(
            today to "Heute geschrieben",
            LocalDate.of(2026, 10, 3) to "Am Fluss spaziert",
            LocalDate.of(2026, 9, 20) to "Wieder am FLUSS gesessen",
            LocalDate.of(2025, 12, 24) to "Heiligabend",
        ),
    )

    private val searchField get() = hasContentDescription(str(R.string.search_placeholder))

    private fun search(text: String) {
        tap(str(R.string.action_search))
        rule.onNode(searchField).performTextInput(text)
        rule.waitForIdle()
    }

    private fun kicker(year: Int, month: Int) = monthYear(year, month).uppercase(rule.activity.resources.configuration.locales[0])

    @Test
    fun searchFindsEntriesIgnoringCaseNewestFirst() {
        start(searchStore())
        openArchive()
        search("fluss")

        waitForText("Wieder am FLUSS gesessen")
        waitForText("Am Fluss spaziert")
        rule.onNode(hasText(rule.activity.resources.getQuantityString(R.plurals.archive_entries, 2, 2))).assertExists()
        rule.onNode(hasText("Heiligabend")).assertDoesNotExist()
        rule.onNode(hasText("Heute geschrieben")).assertDoesNotExist()
        // Monat und Jahr stehen auf jedem Treffer, der neueste oben.
        val october = rule.onNode(hasText(kicker(2026, 10))).fetchSemanticsNode().positionInRoot.y
        val september = rule.onNode(hasText(kicker(2026, 9))).fetchSemanticsNode().positionInRoot.y
        assertTrue(october < september)
    }

    @Test
    fun searchReachesOlderYears() {
        start(searchStore())
        openArchive()
        search("HEILIG")

        waitForText("Heiligabend")
        rule.onNode(hasText(kicker(2025, 12))).assertExists()
    }

    @Test
    fun noResultsSaysSo() {
        start(searchStore())
        openArchive()
        search("Berge")

        waitForText(rule.activity.getString(R.string.search_no_results, "Berge"))
    }

    /** Aus einem Treffer zurück: Suchbegriff und Treffer sind noch da. */
    @Test
    fun goingBackFromAHitKeepsTheResults() {
        start(searchStore())
        openArchive()
        search("fluss")
        waitForText("Am Fluss spaziert")

        rule.onNode(hasText("Am Fluss spaziert") and hasSetTextAction().not()).performClick()
        waitForText(str(R.string.entry_title_past))
        tap(str(R.string.action_back))

        waitForText("Wieder am FLUSS gesessen")
        rule.onNode(searchField).assertExists()
        rule.onNode(hasText("fluss") and hasSetTextAction()).assertExists()
    }

    /** Pfeil oben links schließt die Suche: wieder „Archiv“ mit dem Monat, Suchbegriff weg. */
    @Test
    fun backArrowClosesSearchAndReturnsToTheMonth() {
        start(searchStore())
        openArchive()
        search("fluss")
        waitForText("Wieder am FLUSS gesessen")
        rule.onNode(hasText(str(R.string.search_title))).assertExists()

        tap(str(R.string.action_back))

        rule.onNode(hasText(str(R.string.archive_title))).assertExists()
        rule.onNode(hasText(str(R.string.search_title))).assertDoesNotExist()

        waitForText(monthYear(2026, 10))
        rule.onNode(searchField).assertDoesNotExist()
        rule.onNode(hasText("Wieder am FLUSS gesessen")).assertDoesNotExist()
        tap(str(R.string.action_search))
        rule.onNode(hasText("fluss") and hasSetTextAction()).assertDoesNotExist()
    }

    /** Die Zurück-Geste schließt die Suche ebenfalls (und verlässt nicht gleich das Archiv). */
    @Test
    fun backGestureClosesSearch() {
        start(searchStore())
        openArchive()
        search("fluss")
        waitForText("Wieder am FLUSS gesessen")

        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        rule.waitForIdle()

        waitForText(monthYear(2026, 10))
        rule.onNode(searchField).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.archive_title))).assertExists()
    }

    /** Wunsch Meilenstein 13: Das ✕ leert nur das Suchfeld – man bleibt in der Suche. */
    @Test
    fun clearButtonOnlyEmptiesTheField() {
        start(searchStore())
        openArchive()
        search("fluss")
        waitForText("Wieder am FLUSS gesessen")

        tap(str(R.string.search_clear))

        rule.onNode(searchField).assertExists()
        rule.onNode(hasText(str(R.string.search_title))).assertExists()
        rule.onNode(hasText("fluss") and hasSetTextAction()).assertDoesNotExist()
        rule.onNode(hasText("Wieder am FLUSS gesessen")).assertDoesNotExist()
        rule.onNode(hasContentDescription(str(R.string.search_clear))).assertDoesNotExist()

        rule.onNode(searchField).performTextInput("heilig")
        waitForText("Heiligabend")
    }

    /** Ein Treffer wird geöffnet und geändert: Zurück in der Suche gilt schon der neue Text. */
    @Test
    fun editedEntryIsFoundWithItsNewText() {
        val store = searchStore()
        start(store)
        openArchive()
        search("fluss")
        waitForText("Am Fluss spaziert")

        rule.onNode(hasText("Am Fluss spaziert") and hasSetTextAction().not()).performClick()
        waitForText(str(R.string.entry_title_past))
        tap(str(R.string.action_edit))
        val textField = hasSetTextAction() and hasContentDescription(str(R.string.search_placeholder)).not()
        rule.onNode(textField).performTextReplacement("Jetzt am See")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(textField and hasText("Jetzt am See")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(str(R.string.entry_save))).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { store.textOf(LocalDate.of(2026, 10, 3)) == "Jetzt am See" }
        rule.waitForIdle()

        // Zurück in der Suche „fluss“: der geänderte Eintrag passt nicht mehr.
        waitForText("Wieder am FLUSS gesessen")
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodes(hasText("Am Fluss spaziert")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(searchField).performTextReplacement("see")
        waitForText("Jetzt am See")
    }
}
