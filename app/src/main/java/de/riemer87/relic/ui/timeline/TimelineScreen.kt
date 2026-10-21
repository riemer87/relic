package de.riemer87.relic.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.journal.DayLabel
import de.riemer87.relic.journal.Timeline
import de.riemer87.relic.journal.TimelineDay
import de.riemer87.relic.ui.DateText
import de.riemer87.relic.ui.DayPreview
import de.riemer87.relic.ui.DayPreviewContent
import de.riemer87.relic.ui.DaySheet
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.FrauncesDisplay
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import de.riemer87.relic.ui.theme.SheetShape
import java.time.LocalDate

/** Höchstens so viele Zeilen Textvorschau pro Tag, danach „…“. */
const val PREVIEW_MAX_LINES = 2

/** Heute steht größer oben und zeigt etwas mehr vom Text. */
private const val TODAY_PREVIEW_MAX_LINES = 3

/** Breite der Tageszahl-Spalte, damit leere Zeilen und Blätter bündig untereinander stehen. */
private val NUMBER_COLUMN = 46.dp

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onOpenDay: (LocalDate) -> Unit,
    onOpenArchive: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    // Beim ersten Anzeigen, nach dem Zurückkommen von einer Eintragsseite und aus dem Hintergrund.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    TimelineContent(
        state = state,
        previews = previews,
        onOpenDay = onOpenDay,
        onOpenArchive = onOpenArchive,
        onDayVisible = viewModel::requestPreview,
    )
}

/**
 * „Diese Woche“: oben das Blatt für heute, darunter die 6 Tage davor – Tage mit Eintrag als weiße
 * Blätter, leere Tage als schmale Zeile zum Nachtragen. Am Ende der Übergang ins Archiv.
 */
@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    previews: Map<LocalDate, DayPreview>,
    onOpenDay: (LocalDate) -> Unit,
    onOpenArchive: () -> Unit,
    onDayVisible: (LocalDate) -> Unit,
) {
    val colors = Relic.colors

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().background(colors.paper),
    ) {
        items(state.days, key = { day -> day.date.toEpochDay() }) { day ->
            if (day.hasEntry) {
                LaunchedEffect(day.date, day.hasEntry) { onDayVisible(day.date) }
            }
            val label = Timeline.label(day.date, state.today)
            val onClick = { onOpenDay(day.date) }
            when {
                label == DayLabel.TODAY -> TodaySheet(day, previews[day.date], onClick)
                day.hasEntry -> EntrySheet(day, label, previews[day.date], onClick)
                else -> EmptyDayRow(day, label, onClick)
            }
        }
        if (state.days.isNotEmpty()) {
            item(key = "archive") { ArchiveLink(onClick = onOpenArchive) }
        }
    }
}

/** Heute: „HEUTE“, groß das Datum, darunter der Text – oder „Was war heute?“ auf einer feinen Linie. */
@Composable
private fun TodaySheet(day: TimelineDay, preview: DayPreview?, onClick: () -> Unit) {
    val colors = Relic.colors
    val locale = currentLocale()
    val pattern = stringResource(R.string.today_date_pattern)
    val date = remember(day.date, pattern, locale) { DateText.long(day.date, pattern, locale) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(SheetShape)
            .background(colors.sheet)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.timeline_today).uppercase(locale), style = KickerStyle, color = colors.accent)
        Text(date, style = MaterialTheme.typography.displaySmall, color = colors.ink)
        Spacer(Modifier.height(4.dp))
        if (day.hasEntry) {
            DayPreviewContent(day.date, preview, maxLines = TODAY_PREVIEW_MAX_LINES, photoSize = 56.dp)
        } else {
            val rule = colors.rule
            Text(
                text = stringResource(R.string.entry_text_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkFaint,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** Ein vergangener Tag mit Eintrag: weißes Blatt mit großer Tageszahl und Textvorschau. */
@Composable
private fun EntrySheet(day: TimelineDay, label: DayLabel, preview: DayPreview?, onClick: () -> Unit) {
    val colors = Relic.colors
    DaySheet(
        date = day.date,
        filled = true,
        numberColor = colors.ink,
        kicker = dayName(day.date, label),
        kickerColor = colors.inkSoft,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DayPreviewContent(day.date, preview, maxLines = PREVIEW_MAX_LINES, photoSize = 48.dp)
    }
}

/** Ein vergangener Tag ohne Eintrag: nur eine schmale Zeile „Sonntag · nichts aufgeschrieben … Nachtragen“. */
@Composable
private fun EmptyDayRow(day: TimelineDay, label: DayLabel, onClick: () -> Unit) {
    val colors = Relic.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            fontFamily = FrauncesDisplay,
            fontSize = 20.sp,
            color = colors.inkFaint,
            modifier = Modifier.width(NUMBER_COLUMN),
        )
        Text(
            text = stringResource(R.string.timeline_missing, dayName(day.date, label)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSoft,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.timeline_add),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
        )
    }
}

/** „Gestern“ oder der Wochentag. */
@Composable
private fun dayName(date: LocalDate, label: DayLabel): String = when (label) {
    DayLabel.TODAY -> stringResource(R.string.timeline_today)
    DayLabel.YESTERDAY -> stringResource(R.string.timeline_yesterday)
    DayLabel.WEEKDAY -> DateText.weekdayFull(date.dayOfWeek, currentLocale())
}

/** Ende der Woche: ein Blatt „Archiv · Alle älteren Einträge →“. */
@Composable
private fun ArchiveLink(onClick: () -> Unit) {
    val colors = Relic.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(SheetShape)
            .background(colors.sheet)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.archive_title), style = MaterialTheme.typography.titleMedium, color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.archive_all_older), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun TimelinePreview() {
    val today = LocalDate.of(2026, 10, 16)
    val entries = setOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 12))
    RelicTheme {
        TimelineContent(
            state = TimelineUiState(today = today, days = Timeline.days(today, entries), isLoading = false),
            previews = mapOf(
                LocalDate.of(2026, 10, 14) to DayPreview("Spaziergang am Fluss, abends gekocht. Die Blätter werden schon gelb.", emptyList()),
                LocalDate.of(2026, 10, 12) to DayPreview("challo", emptyList()),
            ),
            onOpenDay = {},
            onOpenArchive = {},
            onDayVisible = {},
        )
    }
}
