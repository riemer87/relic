package de.riemer87.relic.ui.timeline

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.riemer87.relic.R
import de.riemer87.relic.journal.Timeline
import de.riemer87.relic.ui.DateText
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Fehler 2026-10-22: Auf Deutsch klebte „Nachtragen“ direkt an „Dienstag · nichts aufgeschrieben“,
 * weil zwischen den beiden Texten kein Abstand vorgesehen war.
 */
@RunWith(AndroidJUnit4::class)
class EmptyDayRowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Mittwoch; nur gestern hat einen Eintrag, Montag (vorgestern) ist leer. */
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun addActionKeepsItsDistanceFromTheMissingText() {
        rule.setContent {
            RelicTheme {
                // Schmal, damit der Text in jeder Sprache bis an „Nachtragen“ heranreicht.
                Box(Modifier.width(320.dp)) {
                    TimelineContent(
                        state = TimelineUiState(today = today, days = Timeline.days(today, setOf(today.minusDays(1))), isLoading = false, firstEntry = today.minusDays(1)),
                        previews = emptyMap(),
                        onOpenDay = {},
                        onOpenArchive = {},
                        onDayVisible = {},
                        backupHint = null,
                        onOpenBackup = {},
                    )
                }
            }
        }

        val res = rule.activity.resources
        val monday = DateText.weekdayFull(DayOfWeek.MONDAY, res.configuration.locales[0])
        val missing = rule.onNode(hasText(res.getString(R.string.timeline_missing, monday)), useUnmergedTree = true).getBoundsInRoot()
        // Die ganze Zeile ist antippbar und fasst ihre Texte zusammen; gemessen werden die einzelnen Texte.
        val adds = rule.onAllNodes(hasText(res.getString(R.string.timeline_add)), useUnmergedTree = true)
        val add = (0 until adds.fetchSemanticsNodes().size)
            .map { adds[it].getBoundsInRoot() }
            .single { it.top < missing.bottom && it.bottom > missing.top }

        val gap = add.left - missing.right
        assertTrue("Abstand nur $gap", gap >= EMPTY_DAY_ACTION_GAP)
    }
}
