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
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
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
}
