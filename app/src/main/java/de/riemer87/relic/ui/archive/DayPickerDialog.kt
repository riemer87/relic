package de.riemer87.relic.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.riemer87.relic.R
import de.riemer87.relic.journal.Archive
import de.riemer87.relic.ui.DateText
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.longDate
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tagesauswahl im Relic-Stil zum Nachtragen: Woche ab Montag, Punkt unter Tagen mit Eintrag,
 * heute umrandet, Zukunft nicht wählbar.
 */
@Composable
fun DayPickerDialog(
    startMonth: YearMonth,
    today: LocalDate,
    entryDates: Set<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    var month by rememberSaveable { mutableStateOf(startMonth) }
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.sheet)
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.picker_title).uppercase(locale), style = KickerStyle, color = colors.inkFaint)
            Text(
                text = selected?.let { longDate(it) } ?: stringResource(R.string.picker_prompt),
                style = MaterialTheme.typography.headlineSmall,
                color = if (selected == null) colors.inkFaint else colors.ink,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateText.monthYear(month.atDay(1), locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                val canGoBack = Archive.canGoBack(month, entryDates, today)
                IconButton(onClick = { month = month.minusMonths(1) }, enabled = canGoBack) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_left),
                        stringResource(R.string.month_previous),
                        tint = if (canGoBack) colors.ink else colors.rule,
                    )
                }
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = Archive.canGoForward(month, today)) {
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        stringResource(R.string.month_next),
                        tint = if (Archive.canGoForward(month, today)) colors.ink else colors.rule,
                    )
                }
            }

            Row {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        text = DateText.weekdayShort(day, locale).take(2).uppercase(locale),
                        style = KickerStyle.copy(fontSize = 10.sp),
                        color = colors.inkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val withEntry = stringResource(R.string.picker_has_entry)
            Column {
                Archive.weeks(month).forEach { week ->
                    Row {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    DayCell(
                                        date = date,
                                        isToday = date == today,
                                        isFuture = date.isAfter(today),
                                        isSelected = date == selected,
                                        hasEntry = date in entryDates,
                                        withEntryLabel = withEntry,
                                        onClick = { selected = date },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.ink) }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { selected?.let(onPick) },
                    enabled = selected != null,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                ) {
                    Text(stringResource(R.string.picker_open))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    hasEntry: Boolean,
    withEntryLabel: String,
    onClick: () -> Unit,
) {
    val colors = Relic.colors
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(colors.ink) else Modifier)
            .then(
                if (isToday && !isSelected) {
                    Modifier.drawBehind { drawCircle(colors.ink, radius = size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx())) }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = !isFuture, onClick = onClick)
            .semantics {
                contentDescription = date.dayOfMonth.toString()
                if (hasEntry) stateDescription = withEntryLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isSelected -> colors.sheet
                isFuture -> colors.rule
                else -> colors.ink
            },
        )
        if (hasEntry) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.sheet else colors.ink),
            )
        }
    }
}
