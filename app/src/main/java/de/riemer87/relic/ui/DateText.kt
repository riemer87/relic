package de.riemer87.relic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import de.riemer87.relic.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Datumstexte in der Systemsprache. */
object DateText {

    /** „Freitag · 16. Oktober“ / „Friday · October 16“ */
    fun long(date: LocalDate, pattern: String, locale: Locale): String =
        DateTimeFormatter.ofPattern(pattern, locale).format(date)

    /** „Oktober 2026“ (eigenständige Monatsform, wichtig z. B. im Polnischen oder Russischen) */
    fun monthYear(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(date)

    /** „Fr“ / „Fri“ – ohne Punkt, den manche Sprachen anhängen. */
    fun weekdayShort(day: DayOfWeek, locale: Locale): String =
        day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).removeSuffix(".")

    /** „Mittwoch“ / „Wednesday“ */
    fun weekdayFull(day: DayOfWeek, locale: Locale): String =
        day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
}

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Langes Datum, z. B. „Freitag · 16. Oktober“. */
@Composable
fun longDate(date: LocalDate): String {
    val pattern = stringResource(R.string.entry_date_pattern)
    val locale = currentLocale()
    return remember(date, pattern, locale) { DateText.long(date, pattern, locale) }
}
