package de.riemer87.relic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.DmSans
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme

private val INDICATOR_WIDTH = 28.dp
private val LOGO_SIZE = 50.dp
private val TAB_HEIGHT = 56.dp

/** Die zwei Hauptteile der App, als Reiter unter dem Schriftzug. */
enum class HomeTab { WEEK, ARCHIVE }

/**
 * Kopfzeile von Zeitstrahl und Archiv: „Relic“, rechts Lupe (Suche) und Regler (Einstellungen),
 * darunter die Reiter „Diese Woche“ · „Archiv“ je über die halbe Breite.
 */
@Composable
fun HomeHeader(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    val colors = Relic.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rundes Logo vor dem Schriftzug; nur Schmuck, der Name steht ja daneben. Der weiße Kreis
            // hebt sich ohne Rand vom Papier ab.
            Image(
                painterResource(R.drawable.relic_logo),
                contentDescription = null,
                modifier = Modifier.size(LOGO_SIZE),
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.action_search), tint = colors.ink)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings), tint = colors.ink)
            }
        }
        HomeTabs(selected = selected, onSelect = onSelect)
    }
}

/**
 * Zwei Reiter, halb und halb: der aktive in Tinte mit kurzem Strich darunter, der andere blass.
 * Beim Wechsel gleitet der Strich hinüber und die Schrift färbt sich um – nur Position und Farbe.
 */
@Composable
private fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val colors = Relic.colors
    val labels = mapOf(HomeTab.WEEK to R.string.tab_week, HomeTab.ARCHIVE to R.string.archive_title)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val half = maxWidth / 2
        val lineX by animateDpAsState(
            targetValue = half * selected.ordinal + (half - INDICATOR_WIDTH) / 2,
            animationSpec = tween(TAB_FADE_MILLIS),
            label = "Strich",
        )
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            HomeTab.entries.forEach { tab ->
                val active = tab == selected
                val color by animateColorAsState(if (active) colors.ink else colors.inkFaint, tween(TAB_FADE_MILLIS), label = "Reiterfarbe")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TAB_HEIGHT)
                        // Ohne graue Drück-Fläche: Strich und Farbe zeigen den Wechsel schon (flacher Papierlook).
                        .selectable(selected = active, interactionSource = null, indication = null, role = Role.Tab) {
                            if (!active) onSelect(tab)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labels.getValue(tab)),
                        fontFamily = DmSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = color,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                // Verschieben nur beim Zeichnen – die Zeile wird dabei nicht neu aufgebaut.
                .offset { IntOffset(lineX.roundToPx(), 0) }
                .padding(bottom = 4.dp)
                .size(width = INDICATOR_WIDTH, height = 2.dp)
                .background(colors.ink, RoundedCornerShape(1.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeHeaderPreview() {
    RelicTheme { HomeHeader(selected = HomeTab.WEEK, onSelect = {}, onOpenSettings = {}, onSearch = {}) }
}
