package de.riemer87.relic.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalDate

/** Höchstens so viele Zeilen Textvorschau pro Tag, danach „…“. */
const val PREVIEW_MAX_LINES = 2

private val DOT_TOP = 14.dp
private val DOT_SIZE = 12.dp

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

@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    previews: Map<LocalDate, DayPreview>,
    onOpenDay: (LocalDate) -> Unit,
    onOpenArchive: () -> Unit,
    onDayVisible: (LocalDate) -> Unit,
) {
    val colors = Relic.colors

    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            ArchiveButton(onClick = onOpenArchive)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.days, key = { _, day -> day.date.toEpochDay() }) { index, day ->
                if (day.hasEntry) {
                    LaunchedEffect(day.date, day.hasEntry) { onDayVisible(day.date) }
                }
                TimelineItem(
                    day = day,
                    today = state.today,
                    preview = previews[day.date],
                    isFirst = index == 0,
                    onClick = { onOpenDay(day.date) },
                )
            }
            if (state.days.isNotEmpty()) {
                item(key = "archive") { ArchiveLink(onClick = onOpenArchive) }
            }
        }
    }
}

/** Knopf oben rechts: „▤ Archiv“. */
@Composable
private fun ArchiveButton(onClick: () -> Unit) {
    val colors = Relic.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .drawBehind {
                drawRoundRect(
                    color = colors.rule,
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(painterResource(R.drawable.ic_archive), null, tint = colors.ink, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.archive_title), style = MaterialTheme.typography.labelLarge, color = colors.ink)
    }
}

/** Ende des Zeitstrahls: Linie läuft gestrichelt aus in ein Blatt „Archiv · Alle älteren Einträge →“. */
@Composable
private fun ArchiveLink(onClick: () -> Unit) {
    val colors = Relic.colors
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    drawLine(
                        colors.rule, Offset(x, 0f), Offset(x, 22.dp.toPx()), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                },
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.sheet)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.archive_title), style = MaterialTheme.typography.titleMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.archive_all_older), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
        }
    }
}

@Composable
private fun TimelineItem(
    day: TimelineDay,
    today: LocalDate,
    preview: DayPreview?,
    isFirst: Boolean,
    onClick: () -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    val label = Timeline.label(day.date, today)
    val isToday = label == DayLabel.TODAY

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Zeitstrahl: Linie durch alle Tage, Punkt auf Höhe der Tageszahl.
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val top = if (isFirst) (DOT_TOP + DOT_SIZE / 2).toPx() else 0f
                    drawLine(colors.rule, Offset(x, top), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                },
        ) {
            val dotModifier = Modifier
                .padding(top = DOT_TOP)
                .align(Alignment.TopCenter)
                .size(DOT_SIZE)
                .clip(CircleShape)
            if (isToday) {
                Box(dotModifier.background(colors.accent))
            } else {
                Box(
                    dotModifier
                        .background(colors.paper)
                        .drawBehind {
                            drawCircle(
                                color = if (day.hasEntry) colors.inkSoft else colors.rule,
                                radius = size.minDimension / 2 - 0.75.dp.toPx(),
                                style = Stroke(1.5.dp.toPx()),
                            )
                        },
                )
            }
        }
        Spacer(Modifier.width(10.dp))

        DaySheet(
            date = day.date,
            filled = day.hasEntry || isToday,
            numberColor = when {
                isToday -> colors.accent
                day.hasEntry -> colors.ink
                else -> colors.inkFaint
            },
            kicker = when (label) {
                DayLabel.TODAY -> stringResource(R.string.timeline_today)
                DayLabel.YESTERDAY -> stringResource(R.string.timeline_yesterday)
                DayLabel.WEEKDAY -> DateText.weekdayFull(day.date.dayOfWeek, locale)
            },
            kickerColor = if (isToday) colors.accent else colors.inkFaint,
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(bottom = 12.dp),
        ) {
            when {
                day.hasEntry -> DayPreviewContent(day.date, preview, maxLines = PREVIEW_MAX_LINES, photoSize = 44.dp)
                isToday -> Text(
                    text = stringResource(R.string.entry_text_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkFaint,
                )
                else -> Text(
                    text = stringResource(R.string.timeline_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = colors.inkSoft,
                )
            }
        }
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
