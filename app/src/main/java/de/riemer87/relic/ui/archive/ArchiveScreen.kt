package de.riemer87.relic.ui.archive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.journal.Archive
import de.riemer87.relic.ui.DateText
import de.riemer87.relic.ui.DayPreview
import de.riemer87.relic.ui.DayPreviewContent
import de.riemer87.relic.ui.DaySheet
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.dashedOutline
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/** Im Archiv zeigt die Vorschau mehr Text als im Zeitstrahl. */
const val ARCHIVE_PREVIEW_LINES = 4

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    ArchiveContent(
        state = state,
        previews = previews,
        onBack = onBack,
        onOpenDay = onOpenDay,
        onShowMonth = viewModel::showMonth,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onDayVisible = viewModel::requestPreview,
    )
}

@Composable
internal fun ArchiveContent(
    state: ArchiveUiState,
    previews: Map<LocalDate, DayPreview>,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onShowMonth: (YearMonth) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDayVisible: (LocalDate) -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var dayPickerOpen by rememberSaveable { mutableStateOf(false) }
    // Je Monat eine eigene Scroll-Position; sie bleibt erhalten, wenn man einen Eintrag öffnet und zurückkommt.
    val listState = rememberSaveable(state.month, saver = LazyListState.Saver) { LazyListState() }
    val entries = state.entries

    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = colors.ink)
            }
        }
        Text(
            text = stringResource(R.string.archive_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // Kopfzeile: „September 2026 ▾“ mit ‹ ›
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.sheet)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.archive_choose_month)) { pickerOpen = !pickerOpen }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(DateText.monthYear(state.month.atDay(1), locale), style = MaterialTheme.typography.headlineSmall, color = colors.ink)
                Text(if (pickerOpen) "▴" else "▾", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
            }
            IconButton(onClick = onPrevious, enabled = state.canGoBack) {
                Icon(
                    painterResource(R.drawable.ic_chevron_left),
                    stringResource(R.string.month_previous),
                    tint = if (state.canGoBack) colors.inkSoft else colors.rule,
                )
            }
            IconButton(onClick = onNext, enabled = state.canGoForward) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    stringResource(R.string.month_next),
                    tint = if (state.canGoForward) colors.inkSoft else colors.rule,
                )
            }
        }

        AnimatedVisibility(visible = pickerOpen) {
            MonthPicker(
                selected = state.month,
                today = state.today,
                entryDates = state.entryDates,
                onSelect = {
                    onShowMonth(it)
                    pickerOpen = false
                },
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "count") {
                Text(
                    text = if (state.isLoading) "" else if (entries.isEmpty()) {
                        stringResource(R.string.archive_empty)
                    } else {
                        pluralStringResource(R.plurals.archive_entries, entries.size, entries.size)
                    },
                    style = KickerStyle,
                    color = colors.inkFaint,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            items(entries, key = { it.toEpochDay() }) { date ->
                LaunchedEffect(date) { onDayVisible(date) }
                DaySheet(
                    date = date,
                    filled = true,
                    numberColor = colors.ink,
                    kicker = null,
                    kickerColor = colors.inkFaint,
                    onClick = { onOpenDay(date) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DayPreviewContent(date, previews[date], maxLines = ARCHIVE_PREVIEW_LINES, photoSize = 56.dp)
                }
            }
            item(key = "add") {
                AddDaySheet(month = state.month, onClick = { dayPickerOpen = true })
            }
        }
    }

    if (dayPickerOpen) {
        DayPickerDialog(
            startMonth = state.month,
            today = state.today,
            entryDates = state.entryDates,
            onPick = {
                dayPickerOpen = false
                onOpenDay(it)
            },
            onDismiss = { dayPickerOpen = false },
        )
    }
}

/** Aufgeklappte Auswahl: Jahre und 12 Monate. */
@Composable
private fun MonthPicker(
    selected: YearMonth,
    today: LocalDate,
    entryDates: Set<LocalDate>,
    onSelect: (YearMonth) -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    var year by rememberSaveable(selected) { mutableIntStateOf(selected.year) }
    val counts = Archive.monthCounts(year, entryDates)

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.sheet)
            .drawBehind {
                drawRoundRect(colors.ink, cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val yearsWithEntries = remember(entryDates) { entryDates.map { it.year }.toSet() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Archive.years(entryDates, today).forEach { y ->
                val isOn = y == year
                Text(
                    text = y.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isOn -> colors.ink
                        y in yearsWithEntries -> colors.inkSoft
                        else -> colors.rule // Jahr ohne Einträge: blass, aber wählbar
                    },
                    modifier = Modifier
                        .clickable { year = y }
                        .then(
                            if (isOn) {
                                Modifier.drawBehind {
                                    val yPos = size.height - 0.75.dp.toPx()
                                    drawLine(colors.ink, Offset(0f, yPos), Offset(size.width, yPos), 1.5.dp.toPx())
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(bottom = 3.dp),
                )
            }
        }
        Month.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val ym = YearMonth.of(year, m)
                    val isOn = ym == selected
                    val selectable = Archive.isSelectable(ym, entryDates, today)
                    val hasEntries = (counts[m.value] ?: 0) > 0
                    Text(
                        text = m.getDisplayName(TextStyle.SHORT_STANDALONE, locale).removeSuffix("."),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = when {
                            isOn -> colors.sheet
                            !selectable -> colors.rule
                            else -> colors.ink
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isOn -> colors.ink
                                    hasEntries -> colors.paper
                                    else -> colors.sheet
                                },
                            )
                            .clickable(enabled = selectable) { onSelect(ym) }
                            .padding(vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** Gestricheltes Blatt am Ende der Liste: „+ Einen Tag nachtragen“. */
@Composable
private fun AddDaySheet(month: YearMonth, onClick: () -> Unit) {
    val colors = Relic.colors
    val locale = currentLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .dashedOutline(colors.rule)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .drawBehind { drawCircle(colors.inkSoft, radius = size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx())) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_add), null, tint = colors.inkSoft, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(stringResource(R.string.archive_add_day), style = MaterialTheme.typography.labelLarge, color = colors.ink)
            Text(
                text = stringResource(R.string.archive_add_day_hint, month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ArchivePreview() {
    val today = LocalDate.of(2026, 10, 16)
    val dates = setOf(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 27), LocalDate.of(2026, 10, 12))
    RelicTheme {
        ArchiveContent(
            state = ArchiveUiState(today = today, month = YearMonth.of(2026, 9), entryDates = dates, isLoading = false),
            previews = mapOf(LocalDate.of(2026, 9, 29) to DayPreview("Heute ging es mir schon besser.", emptyList())),
            onBack = {}, onOpenDay = {}, onShowMonth = {}, onPrevious = {}, onNext = {}, onDayVisible = {},
        )
    }
}
