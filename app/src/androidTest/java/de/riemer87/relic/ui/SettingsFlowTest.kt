package de.riemer87.relic.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import de.riemer87.relic.export.ExportRecord
import de.riemer87.relic.ui.theme.AccentColor
import de.riemer87.relic.ui.theme.Appearance
import de.riemer87.relic.ui.theme.AppearanceSettings
import de.riemer87.relic.ui.theme.ThemeMode
import de.riemer87.relic.export.ExportState
import de.riemer87.relic.export.ExportStatus
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    private lateinit var appearanceBefore: Appearance

    private fun str(id: Int) = rule.activity.getString(id)

    @Before
    fun setUp() {
        appearanceBefore = AppearanceSettings.get(context).appearance.value
        before = ReminderSettings(context).load()
        ReminderSettings(context).save(ReminderConfig(enabled = false))
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        AppearanceSettings.get(context).setMode(appearanceBefore.mode)
        AppearanceSettings.get(context).setAccent(appearanceBefore.accent)
        ExportStatus.update(ExportState.Idle)
        context.getSharedPreferences("export", android.content.Context.MODE_PRIVATE).edit().clear().commit()
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

    /**
     * Wunsch aus Meilenstein 12: Die Seite beginnt mit der Darstellung; der Speicherort steht weiter unten,
     * damit er nicht wirkt, als müsse man dort etwas tun.
     */
    @Test
    fun sectionsAreInTheAgreedOrder() {
        openSettings()
        val locale = rule.activity.resources.configuration.locales[0]
        val order = listOf(
            R.string.settings_appearance, R.string.settings_reminder, R.string.settings_lock,
            R.string.settings_folder, R.string.settings_export, R.string.settings_about,
        ).map { id ->
            rule.onNode(hasText(str(id).uppercase(locale))).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun settingsShowTheFolderInPlainWords() {
        openSettings()
        rule.onNode(hasText(str(R.string.settings_folder_explain))).assertExists()
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
        rule.onNode(hasText(str(R.string.settings_folder_change))).performScrollTo().performClick()
        waitForText(str(R.string.folder_change_notice))
        rule.onNode(hasText(str(R.string.folder_recommended))).assertExists()

        rule.onNode(hasContentDescription(str(R.string.action_back))).performClick()
        waitForText(str(R.string.settings_title))
    }

    @Test
    fun licensesAreShown() {
        openSettings()
        rule.onNode(hasText(str(R.string.settings_licenses))).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasText("SIL Open Font License", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Darstellung wählen: wirkt sofort (gemeinsamer Zustand) und bleibt gespeichert. */
    @Test
    fun appearanceChoiceIsAppliedAndSaved() {
        AppearanceSettings.get(context).setMode(ThemeMode.SYSTEM)
        AppearanceSettings.get(context).setAccent(AccentColor.INK)
        openSettings()
        rule.onNode(hasText(str(R.string.appearance_system))).assertIsSelected()

        rule.onNode(hasText(str(R.string.appearance_dark))).performClick()
        rule.onNode(hasText(str(R.string.accent_plum))).performClick()
        rule.waitForIdle()

        assertEquals(Appearance(ThemeMode.DARK, AccentColor.PLUM), AppearanceSettings.get(context).appearance.value)
        rule.onNode(hasText(str(R.string.appearance_dark))).assertIsSelected()
        rule.onNode(hasText(str(R.string.accent_plum))).assertIsSelected()
        val prefs = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        assertEquals("DARK", prefs.getString("mode", null))
        assertEquals("PLUM", prefs.getString("accent", null))
    }

    /** Während ein Export läuft: Fortschritt sichtbar, kein zweiter Start möglich. */
    @Test
    fun runningExportShowsProgressAndBlocksSecondStart() {
        ExportStatus.update(ExportState.Writing(done = 120, total = 340))
        openSettings()

        rule.onNode(hasText(rule.activity.resources.getQuantityString(R.plurals.export_writing, 340, 120, 340))).assertExists()
        rule.onNode(hasText(str(R.string.settings_export_button))).assertIsNotEnabled()
    }

    /** „Am 17. Oktober 2026 um 01:40 Uhr“, wie auf dem Handy eingestellt. */
    private fun exportedAtText(at: Instant): String {
        val locale = rule.activity.resources.configuration.locales[0]
        val local = at.atZone(ZoneId.systemDefault())
        return rule.activity.getString(
            R.string.export_done_at,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(local),
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local),
        )
    }

    /** Fertig, während die Seite offen ist: „Export gespeichert“ mit Zeitpunkt. */
    @Test
    fun exportFinishingWhileOpenShowsSavedWithTimeAndFilesLeftOut() {
        val at = Instant.parse("2026-10-16T23:40:00Z")
        ExportStatus.update(ExportState.Writing(done = 300, total = 340))
        openSettings()

        rule.runOnIdle { ExportStatus.update(ExportState.Done(files = 340, skipped = 2, finishedAt = at)) }
        rule.waitForIdle()

        val res = rule.activity.resources
        rule.onNode(hasText(res.getQuantityString(R.plurals.export_done, 340, 340))).assertExists()
        rule.onNode(hasText(exportedAtText(at))).assertExists()
        rule.onNode(hasText(res.getQuantityString(R.plurals.export_skipped, 2, 2), substring = true)).assertExists()
        rule.onNode(hasText(str(R.string.settings_export_button))).assertIsEnabled()
    }

    /**
     * Wunsch aus Meilenstein 10: Kommt man später wieder auf die Seite, steht „Letzter Export“ –
     * sonst könnte man meinen, man hätte gerade eben exportiert.
     */
    @Test
    fun reopeningSettingsAfterExportSaysLastExport() {
        val at = Instant.parse("2026-10-16T23:40:00Z")
        ExportRecord(context).saveSuccess(ExportState.Done(files = 14, skipped = 0, finishedAt = at))
        ExportStatus.update(ExportState.Done(files = 14, skipped = 0, finishedAt = at))
        openSettings()

        val res = rule.activity.resources
        rule.onNode(hasText(res.getQuantityString(R.plurals.export_last, 14, 14))).assertExists()
        rule.onNode(hasText(res.getQuantityString(R.plurals.export_done, 14, 14))).assertDoesNotExist()
        rule.onNode(hasText(exportedAtText(at))).assertExists()
    }

    /** App wurde nach dem Export beendet: Der Erfolg mit Zeitpunkt steht trotzdem in den Einstellungen. */
    @Test
    fun earlierSuccessIsShownAfterRestart() {
        val at = Instant.parse("2026-10-15T19:05:00Z")
        ExportRecord(context).saveSuccess(ExportState.Done(files = 12, skipped = 0, finishedAt = at))
        ExportStatus.update(ExportState.Idle)
        openSettings()

        rule.onNode(hasText(rule.activity.resources.getQuantityString(R.plurals.export_last, 12, 12))).assertExists()
        rule.onNode(hasText(exportedAtText(at))).assertExists()
    }

    @Test
    fun failedExportExplainsThatNothingIncompleteIsLeft() {
        ExportStatus.update(ExportState.Failed(targetDeleted = true, targetName = "Relic-Export.zip"))
        openSettings()

        rule.onNode(hasText(str(R.string.export_failed))).assertExists()
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
