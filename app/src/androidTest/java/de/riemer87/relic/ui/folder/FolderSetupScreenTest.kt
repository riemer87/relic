package de.riemer87.relic.ui.folder

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSetupScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Fehler aus Meilenstein 2: Verschwand der Ordner, erschien kurz der Ordner-Bildschirm, meldete
     * aber sofort wieder den alten Ordner als „fertig“ – die App sprang zurück zur Heute-Seite.
     */
    @Test
    fun folderReadyIsReportedOnlyOnce() {
        val viewModel = FolderSetupViewModel(rule.activity.applicationContext as Application)
        var showSetup by mutableStateOf(true)
        var readyCount = 0

        rule.setContent {
            if (showSetup) {
                FolderSetupScreen(
                    onFolderReady = {
                        readyCount++
                        showSetup = false
                    },
                    viewModel = viewModel,
                )
            }
        }

        rule.runOnIdle { viewModel.onReady("content://test/tree/journal".toUri()) }
        rule.waitForIdle()
        assertEquals(1, readyCount)

        // Ordner verloren: Die App zeigt den Ordner-Bildschirm wieder an.
        rule.runOnIdle { showSetup = true }
        rule.waitForIdle()

        assertEquals("Alter Ordner darf nicht erneut gemeldet werden", 1, readyCount)
        val title = rule.activity.getString(R.string.folder_title)
        rule.onNode(hasText(title)).assertExists()
    }

    /** Wunsch aus Meilenstein 2: Wer ein Tagebuch verbinden will, bekommt erklärt, wie man den Ordner findet. */
    @Test
    fun existingOptionShowsHowToFindTheFolder() {
        rule.setContent { FolderSetupScreen(onFolderReady = {}) }

        rule.onNode(hasText(rule.activity.getString(R.string.folder_existing))).performClick()

        rule.onNode(hasText(rule.activity.getString(R.string.folder_hint_existing))).assertExists()
        rule.onNode(hasText(rule.activity.getString(R.string.folder_hint))).assertDoesNotExist()
    }

    @Test
    fun ownFolderOptionShowsGeneralHint() {
        rule.setContent { FolderSetupScreen(onFolderReady = {}) }

        rule.onNode(hasText(rule.activity.getString(R.string.folder_own))).performClick()

        rule.onNode(hasText(rule.activity.getString(R.string.folder_hint))).assertExists()
        rule.onNode(hasText(rule.activity.getString(R.string.folder_hint_existing))).assertDoesNotExist()
    }
}
