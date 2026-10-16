package de.riemer87.relic.ui.today

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(TodayUiState(date = LocalDate.of(2026, 10, 16), isLoading = false))

    private val savedText get() = rule.activity.getString(R.string.entry_saved)

    /** Zeigt die Heute-Seite randlos wie in der echten App; das Markieren als „angezeigt“ verhält sich wie im ViewModel. */
    private fun showTodayContent() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            RelicTheme {
                TodayContent(
                    state = state,
                    onTextChange = {},
                    onSave = {},
                    onMessageShown = { state = state.copy(message = null) },
                )
            }
        }
    }

    private fun waitForSavedMessage() {
        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodes(hasText(savedText)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Fehler aus Meilenstein 1: Die Meldung wurde sofort als „angezeigt“ markiert,
     * dadurch brach die Anzeige ab und „Eintrag gespeichert“ war nie zu sehen.
     */
    @Test
    fun savedMessageIsShownAfterItIsMarkedAsShown() {
        showTodayContent()
        state = state.copy(message = R.string.entry_saved)
        waitForSavedMessage()
    }

    /** Fehler aus Meilenstein 1: Bei offener Tastatur lag „Eintrag gespeichert“ hinter der Tastatur. */
    @Test
    fun savedMessageIsAboveOpenKeyboard() {
        showTodayContent()

        // Ins Textfeld tippen öffnet die Tastatur; warten, bis sie ganz offen ist.
        rule.onNode(hasSetTextAction()).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { keyboardHeight() > 0 }
        Thread.sleep(1_000) // Tastatur-Animation abwarten.
        rule.waitForIdle()
        val keyboardTop = rule.activity.window.decorView.height - keyboardHeight()

        state = state.copy(message = R.string.entry_saved)
        waitForSavedMessage()
        Thread.sleep(500) // Einblend-Animation der Meldung abwarten.
        rule.waitForIdle()

        val messageBottom = rule.onNode(hasText(savedText)).fetchSemanticsNode().boundsInWindow.bottom
        assertTrue(
            "Meldung endet bei $messageBottom px, Tastatur beginnt bei $keyboardTop px",
            messageBottom <= keyboardTop,
        )
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
