package de.riemer87.relic.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val journalUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FRelic".toUri()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var before: ReminderConfig

    private fun str(id: Int) = rule.activity.getString(id)

    @Before
    fun setUp() {
        before = ReminderSettings(context).load()
        ReminderSettings(context).save(ReminderConfig(enabled = false))
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        ReminderSettings(context).save(before)
        ReminderScheduler(context).apply(before)
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openSettings(store: FakeJournalStore = FakeJournalStore()) {
        rule.setContent { RelicTheme { RelicApp(store, clock, photoPreparer = { null }, journalUri = journalUri) } }
        rule.waitForIdle()
        rule.onNode(hasContentDescription(str(R.string.action_settings))).performClick()
        waitForText(str(R.string.settings_title))
    }

    @Test
    fun settingsShowTheFolderInPlainWords() {
        openSettings()
        rule.onNode(hasText("${str(R.string.settings_folder_internal)} › Documents › Relic")).assertExists()
    }

    @Test
    fun turningOnTheReminderShowsTheTimeAndPlansIt() {
        openSettings()
        rule.onNode(hasText(str(R.string.settings_reminder_time))).assertDoesNotExist()

        // Erster Schalter = Erinnerung (der zweite ist die App-Sperre).
        rule.onAllNodes(isToggleable())[0].performSemanticsAction(SemanticsActions.OnClick)
        waitForText(str(R.string.settings_reminder_time))

        assertTrue(ReminderSettings(context).load().enabled)
        assertTrue(ReminderScheduler(context).isScheduled())
    }

    @Test
    fun changeFolderShowsTheNoticeAboutOldEntries() {
        openSettings()
        rule.onNode(hasText(str(R.string.settings_folder_change))).performClick()
        waitForText(str(R.string.folder_change_notice))
        rule.onNode(hasText(str(R.string.folder_recommended))).assertExists()

        rule.onNode(hasContentDescription(str(R.string.action_back))).performClick()
        waitForText(str(R.string.settings_title))
    }

    @Test
    fun licensesAreShown() {
        openSettings()
        rule.onNode(hasText(str(R.string.settings_licenses))).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText("SIL Open Font License", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Tippen auf die Erinnerung öffnet die Eintragsseite von heute – auch wenn gerade eine andere Seite offen ist. */
    @Test
    fun reminderTapOpensTodaysEntry() {
        var request by mutableIntStateOf(0)
        val store = FakeJournalStore(mapOf(LocalDate.of(2026, 10, 1) to "Alt"))
        rule.setContent { RelicTheme { RelicApp(store, clock, photoPreparer = { null }, journalUri = journalUri, openTodayRequest = request) } }
        rule.waitForIdle()
        rule.onNode(hasContentDescription(str(R.string.action_settings))).performClick()
        waitForText(str(R.string.settings_title))

        rule.runOnIdle { request++ }
        waitForText(str(R.string.entry_title_today))
    }
}
