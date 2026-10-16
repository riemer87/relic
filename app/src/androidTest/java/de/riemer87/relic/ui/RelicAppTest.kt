package de.riemer87.relic.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import de.riemer87.relic.ui.timeline.PREVIEW_MAX_LINES
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RelicAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // Freitag, 16. Oktober 2026, 21:00 in Berlin
    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.of(2026, 10, 16)

    private fun str(id: Int) = rule.activity.getString(id)

    private fun start(store: FakeJournalStore) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent { RelicTheme { RelicApp(store, clock, photoPreparer = { null }) } }
        rule.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Tippt auf die kleine Zeile eines Tages im Zeitstrahl, z. B. „HEUTE“. */
    private fun openDay(kicker: String) {
        waitForText(kicker.uppercase())
        rule.onNode(hasText(kicker.uppercase())).performClick()
        rule.waitForIdle()
    }

    private val textField = hasSetTextAction()

    /**
     * Tippt Text ins Eingabefeld und wartet, bis er wirklich in der App angekommen ist.
     * Die nachgebildete Tastatur liefert manchmal einen Augenblick später.
     */
    private fun typeText(text: String) {
        rule.onNode(textField).performTextInput(text)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(textField.and(hasText(text, substring = true))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Ersetzt den ganzen Text und wartet, bis das Feld genau ihn zeigt (auch leer). */
    private fun replaceText(text: String) {
        rule.onNode(textField).performTextReplacement(text)
        val exactly = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(textField.and(exactly)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Tippt auf „Speichern“ und wartet, bis gespeichert ist und die Oberfläche fertig reagiert hat.
     * Gespeichert wird im Hintergrund; erst danach darf die Test-Uhr der Oberfläche weiterlaufen.
     */
    private fun tapSave(store: FakeJournalStore) {
        val before = store.saveCount
        // Direkt auslösen statt über die Bildschirmposition: Während die Tastatur hochfährt, rutscht der Knopf.
        rule.onNode(hasText(str(R.string.entry_save))).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { store.saveCount > before }
        rule.waitForIdle()
    }

    @Test
    fun timelineShowsTodayAndRecentDays() {
        start(FakeJournalStore(mapOf(LocalDate.of(2026, 10, 14) to "Spaziergang am Fluss")))

        waitForText(str(R.string.timeline_today).uppercase())
        rule.onNode(hasText(str(R.string.timeline_yesterday).uppercase())).assertExists()
        waitForText("Spaziergang am Fluss")
        rule.onAllNodes(hasText(str(R.string.timeline_missing))).fetchSemanticsNodes().let {
            assertTrue("Leere Tage zeigen „nachtragen“", it.isNotEmpty())
        }
    }

    @Test
    fun photosCanBeRemovedOnTheEntryPage() {
        val store = FakeJournalStore(mapOf(today to "Mit Fotos"), initialPhotos = mapOf(today to 2))
        start(store)

        openDay(str(R.string.timeline_today))
        waitForText("2 / 4")
        rule.onNode(hasContentDescription(rule.activity.getString(R.string.photo_remove, 1))).performClick()
        waitForText("1 / 4")
        tapSave(store)

        assertEquals(listOf("2026-10-16_2.jpg"), store.photosOf(today))
    }

    /** Wunsch aus Meilenstein 4: Wischen im Vollbild bleibt innerhalb des Tages. */
    @Test
    fun photoViewerSwipesOnlyWithinTheDay() {
        val yesterday = today.minusDays(1)
        start(FakeJournalStore(mapOf(yesterday to "Ausflug", today to "Heute"), initialPhotos = mapOf(yesterday to 2, today to 1)))

        openDay(str(R.string.timeline_yesterday))
        rule.onNode(hasContentDescription(rule.activity.getString(R.string.photo_number, 1))).performClick()
        waitForText("1 / 2")

        rule.onAllNodes(hasContentDescription(rule.activity.getString(R.string.photo_number, 1)))
            .onLast().performTouchInput { swipeLeft() }
        waitForText("2 / 2")

        rule.onAllNodes(hasContentDescription(rule.activity.getString(R.string.photo_number, 2)))
            .onLast().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNode(hasText("2 / 2")).assertExists() // am Ende des Tages ist Schluss

        rule.onNode(hasContentDescription(str(R.string.photo_close))).performClick()
        rule.waitForIdle()
        rule.onNode(hasText("2 / 2")).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.entry_title_past))).assertExists()
    }

    /** Wunsch aus Meilenstein 3: Lange Einträge zeigen im Zeitstrahl höchstens 2 Zeilen. */
    @Test
    fun longEntryPreviewIsCutToTwoLines() {
        val long = "Anfang eines langen Eintrags. " + "Heute ist sehr viel passiert, und ich schreibe alles auf. ".repeat(12)
        start(FakeJournalStore(mapOf(today.minusDays(2) to long)))
        waitForText("Anfang eines langen Eintrags.", substring = true)

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText("Anfang eines langen Eintrags.", substring = true), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertEquals(PREVIEW_MAX_LINES, layouts.single().lineCount)
        assertTrue("Text wird mit „…“ gekürzt", layouts.single().hasVisualOverflow)
    }

    @Test
    fun writingTodayAndGoingBackSavesAndShowsTheEntry() {
        val store = FakeJournalStore()
        start(store)

        openDay(str(R.string.timeline_today))
        typeText("Ein guter Tag")
        Espresso.pressBack() // Tastatur schließen
        Espresso.pressBack() // Seite verlassen
        rule.waitForIdle()

        waitForText(str(R.string.entry_saved))
        waitForText("Ein guter Tag")
        assertEquals("Ein guter Tag", store.textOf(today))
    }

    @Test
    fun saveButtonClosesThePage() {
        val store = FakeJournalStore()
        start(store)

        openDay(str(R.string.timeline_today))
        typeText("Mit Knopf gespeichert")
        tapSave(store)

        waitForText(str(R.string.entry_saved))
        waitForText("Mit Knopf gespeichert")
        rule.onNode(textField).assertDoesNotExist()
    }

    @Test
    fun goingBackWithoutChangesDoesNotWrite() {
        val store = FakeJournalStore()
        start(store)

        openDay(str(R.string.timeline_today))
        rule.onNode(hasContentDescription(str(R.string.action_back))).performClick()
        rule.waitForIdle()

        waitForText(str(R.string.timeline_today).uppercase())
        assertEquals(0, store.saveCount)
    }

    @Test
    fun pastDayWithEntryOpensReadOnlyUntilPencilIsTapped() {
        val yesterday = today.minusDays(1)
        val store = FakeJournalStore(mapOf(yesterday to "Gestern war schön"))
        start(store)

        openDay(str(R.string.timeline_yesterday))
        waitForText(str(R.string.entry_title_past))
        rule.onNode(hasText("Gestern war schön")).assertExists()
        rule.onNode(textField).assertDoesNotExist()

        rule.onNode(hasContentDescription(str(R.string.action_edit))).performClick()
        rule.onNode(textField).assertExists()
        replaceText("Gestern war sehr schön")
        tapSave(store)

        waitForText(str(R.string.entry_saved))
        assertEquals("Gestern war sehr schön", store.textOf(yesterday))
    }

    @Test
    fun emptyPastDayOpensForWriting() {
        start(FakeJournalStore())

        openDay(str(R.string.timeline_yesterday))
        waitForText(str(R.string.entry_title_past))
        rule.onNode(textField).assertExists()
    }

    @Test
    fun leavingTheAppWhileWritingSavesInTheBackground() {
        val store = FakeJournalStore()
        start(store)

        openDay(str(R.string.timeline_today))
        typeText("Halb geschrieben")
        rule.waitForIdle()

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // wie Home-Taste
        rule.waitUntil(timeoutMillis = 5_000) { store.textOf(today) != null }
        assertEquals("Halb geschrieben", store.textOf(today))
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) // App wieder in den Vordergrund, sonst wartet der Testabbau lange
    }

    @Test
    fun clearingTodaysTextRemovesTheEntry() {
        val store = FakeJournalStore(mapOf(today to "Wird gelöscht"))
        start(store)

        openDay(str(R.string.timeline_today))
        waitForText("Wird gelöscht")
        replaceText("")
        tapSave(store)

        waitForText(str(R.string.entry_removed))
        assertNull(store.textOf(today))
    }

    /** Fehler aus Meilenstein 1, weiter abgesichert: Meldungen liegen über der offenen Tastatur. */
    @Test
    fun errorMessageIsAboveOpenKeyboard() {
        val store = FakeJournalStore().apply { failSaving = true }
        start(store)

        openDay(str(R.string.timeline_today))
        rule.onNode(textField).performClick()
        typeText("Geht nicht")
        rule.waitUntil(timeoutMillis = 5_000) { keyboardHeight() > 0 }
        Thread.sleep(1_000) // Tastatur-Animation abwarten.
        val keyboardTop = rule.activity.window.decorView.height - keyboardHeight()

        rule.onNode(hasText(str(R.string.entry_save))).performClick()
        waitForText(str(R.string.entry_save_failed))
        Thread.sleep(500) // Einblend-Animation abwarten.
        rule.waitForIdle()

        val bottom = rule.onNode(hasText(str(R.string.entry_save_failed))).fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("Meldung endet bei $bottom px, Tastatur beginnt bei $keyboardTop px", bottom <= keyboardTop)
        // Bei einem Fehler bleibt die Seite offen, nichts geht verloren.
        rule.onNode(hasText("Geht nicht")).assertExists()
    }

    private fun keyboardHeight(): Int {
        var height = 0
        rule.runOnUiThread {
            height = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        return height
    }
}
