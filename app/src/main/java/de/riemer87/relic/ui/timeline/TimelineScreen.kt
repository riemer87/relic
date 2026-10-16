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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
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
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.FrauncesDisplay
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalDate

/** Höchstens so viele Zeilen Textvorschau pro Tag, danach „…“. */
const val PREVIEW_MAX_LINES = 2

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onOpenDay: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Beim ersten Anzeigen, nach dem Zurückkommen von einer Eintragsseite und aus dem Hintergrund.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    TimelineContent(
        state = state,
        onOpenDay = onOpenDay,
        onDayVisible = viewModel::requestPreview,
    )
}

@Composable
internal fun TimelineContent(
    state: TimelineUiState,
    onOpenDay: (LocalDate) -> Unit,
    onDayVisible: (LocalDate) -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    val listState = rememberLazyListState()
    // Der Monat oben folgt dem obersten sichtbaren Tag.
    val topDate by remember(state.days) {
        derivedStateOf { state.days.getOrNull(listState.firstVisibleItemIndex)?.date ?: state.today }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(
                text = DateText.monthYear(topDate, locale).uppercase(locale),
                style = KickerStyle.copy(fontSize = 12.sp),
                color = colors.inkFaint,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        LazyColumn(
            state = listState,
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
                    preview = state.previews[day.date],
                    isFirst = index == 0,
                    isLast = index == state.days.lastIndex,
                    onClick = { onOpenDay(day.date) },
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(
    day: TimelineDay,
    today: LocalDate,
    preview: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    val label = Timeline.label(day.date, today)
    val isToday = label == DayLabel.TODAY
    val dotTop = 14.dp
    val dotSize = 12.dp

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Zeitstrahl: Linie durch alle Tage, Punkt auf Höhe der Tageszahl.
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotCenter = (dotTop + dotSize / 2).toPx()
                    val top = if (isFirst) dotCenter else 0f
                    val bottom = if (isLast) dotCenter else size.height
                    if (bottom > top) drawLine(colors.rule, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
                },
        ) {
            val dotModifier = Modifier
                .padding(top = dotTop)
                .align(Alignment.TopCenter)
                .size(dotSize)
                .clip(CircleShape)
            when {
                isToday -> Box(dotModifier.background(colors.accent))
                else -> Box(
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

        val sheetShape = RoundedCornerShape(10.dp)
        val filled = day.hasEntry || isToday
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .clip(sheetShape)
                .then(
                    if (filled) {
                        Modifier.background(colors.sheet)
                    } else {
                        Modifier.drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawRoundRect(
                                color = colors.rule,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = size.copy(width = size.width - stroke, height = size.height - stroke),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        }
                    },
                )
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.width(46.dp)) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    fontFamily = FrauncesDisplay,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    color = when {
                        isToday -> colors.accent
                        day.hasEntry -> colors.ink
                        else -> colors.inkFaint
                    },
                )
                Text(
                    text = DateText.weekdayShort(day.date.dayOfWeek, locale).uppercase(locale),
                    style = KickerStyle.copy(fontSize = 10.5.sp),
                    color = colors.inkFaint,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = when (label) {
                        DayLabel.TODAY -> stringResource(R.string.timeline_today)
                        DayLabel.YESTERDAY -> stringResource(R.string.timeline_yesterday)
                        DayLabel.WEEKDAY -> DateText.weekdayFull(day.date.dayOfWeek, locale)
                    }.uppercase(locale),
                    style = KickerStyle,
                    color = if (isToday) colors.accent else colors.inkFaint,
                )
                when {
                    day.hasEntry -> Text(
                        text = preview.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        maxLines = PREVIEW_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
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
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun TimelinePreview() {
    val today = LocalDate.of(2026, 10, 16)
    val entries = setOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 9, 1))
    RelicTheme {
        TimelineContent(
            state = TimelineUiState(
                today = today,
                days = Timeline.days(today, entries),
                previews = mapOf(
                    LocalDate.of(2026, 10, 14) to "Spaziergang am Fluss, abends gekocht. Die Blätter werden schon gelb.",
                    LocalDate.of(2026, 9, 1) to "Erster Schultag.",
                ),
                isLoading = false,
            ),
            onOpenDay = {},
            onDayVisible = {},
        )
    }
}
