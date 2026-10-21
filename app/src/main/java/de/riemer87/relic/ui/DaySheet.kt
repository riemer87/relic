package de.riemer87.relic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.riemer87.relic.journal.MAX_PHOTOS
import de.riemer87.relic.journal.StoredPhoto
import de.riemer87.relic.ui.photos.PhotoImage
import de.riemer87.relic.ui.theme.SheetRadius
import de.riemer87.relic.ui.theme.SheetShape
import de.riemer87.relic.ui.theme.FrauncesDisplay
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import java.time.LocalDate

/** Gestrichelter Rand, wie bei leeren Tagen. */
fun Modifier.dashedOutline(color: Color, radius: Dp = SheetRadius): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = size.copy(width = size.width - stroke, height = size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * Ein Tag als flaches Blatt: links große Tageszahl mit Wochentag-Kürzel, rechts eine kleine Zeile
 * und der Inhalt (für „Diese Woche“, Archiv und Suche). Leere Tage zeigt nur „Diese Woche“, als schmale Zeile.
 */
@Composable
fun DaySheet(
    date: LocalDate,
    numberColor: Color,
    kicker: String?,
    kickerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Relic.colors
    val locale = currentLocale()
    Row(
        modifier = modifier
            .clip(SheetShape)
            .background(colors.sheet)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.width(46.dp)) {
            Text(
                text = date.dayOfMonth.toString(),
                fontFamily = FrauncesDisplay,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                color = numberColor,
            )
            Text(
                text = DateText.weekdayShort(date.dayOfWeek, locale).uppercase(locale),
                style = KickerStyle.copy(fontSize = 10.5.sp),
                color = colors.inkFaint,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (kicker != null) Text(kicker.uppercase(locale), style = KickerStyle, color = kickerColor)
            content()
        }
    }
}

/** Text (gekürzt) und Fotos eines Tages, wie auf „Diese Woche“ und im Archiv. */
@Composable
fun DayPreviewContent(date: LocalDate, preview: DayPreview?, maxLines: Int, photoSize: Dp) =
    DayPreviewContent(date, AnnotatedString(preview?.text.orEmpty()), preview?.photos.orEmpty(), maxLines, photoSize)

/** Wie oben, mit Text, in dem z. B. Suchtreffer hervorgehoben sind. */
@Composable
fun DayPreviewContent(date: LocalDate, text: AnnotatedString, photos: List<String>, maxLines: Int, photoSize: Dp) {
    val colors = Relic.colors
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (photos.isNotEmpty()) {
        val thumbPx = with(LocalDensity.current) { photoSize.roundToPx() } * 2
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            photos.take(MAX_PHOTOS).forEach { name ->
                PhotoImage(
                    date = date,
                    photo = StoredPhoto(name),
                    maxEdgePx = thumbPx,
                    modifier = Modifier.size(photoSize).clip(RoundedCornerShape(if (photoSize > 50.dp) 8.dp else 6.dp)),
                )
            }
        }
    }
}
