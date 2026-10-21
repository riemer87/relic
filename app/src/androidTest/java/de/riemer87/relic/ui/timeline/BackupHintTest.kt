package de.riemer87.relic.ui.timeline

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupHint
import de.riemer87.relic.journal.Timeline
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupHintTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 21)

    private fun show(hint: BackupHint?, onOpenBackup: () -> Unit = {}) = rule.setContent {
        RelicTheme {
            TimelineContent(
                state = TimelineUiState(today = today, days = Timeline.days(today, setOf(today.minusDays(1))), isLoading = false, firstEntry = today.minusDays(1)),
                previews = emptyMap(),
                onOpenDay = {},
                onOpenArchive = {},
                onDayVisible = {},
                backupHint = hint,
                onOpenBackup = onOpenBackup,
            )
        }
    }

    @Test
    fun overdueHintShowsDaysAndOpensBackupSettings() {
        var opened = 0
        show(BackupHint.Overdue(34)) { opened++ }

        val text = rule.activity.resources.getQuantityString(R.plurals.backup_hint_overdue, 34, 34)
        rule.onNode(hasText(text)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun brokenAutomaticBackupIsShown() {
        show(BackupHint.NotWorking)
        rule.onNode(hasText(rule.activity.getString(R.string.backup_hint_broken))).assertExists()
    }

    @Test
    fun noHintWhenEverythingIsFine() {
        show(null)
        rule.onNode(hasText(rule.activity.getString(R.string.backup_hint_never))).assertDoesNotExist()
        rule.onNode(hasText(rule.activity.getString(R.string.backup_hint_broken))).assertDoesNotExist()
    }
}
