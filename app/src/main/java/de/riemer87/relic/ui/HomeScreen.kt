package de.riemer87.relic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import de.riemer87.relic.ui.theme.Relic

/** So lange blendet der Inhalt beim Reiterwechsel über – kurz genug, dass es nur „weich“ wirkt. */
const val TAB_FADE_MILLIS = 150

/**
 * Die Startseite: Kopfzeile mit Reitern und darunter „Diese Woche“ oder „Archiv“.
 * Beide Reiter bleiben erhalten (Daten im ViewModel, Scroll-Position hier), damit der Wechsel
 * sofort fertig aussieht. Die Zurück-Geste im Archiv führt zu „Diese Woche“.
 */
@Composable
fun HomeScreen(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    week: @Composable () -> Unit,
    archive: @Composable () -> Unit,
) {
    BackHandler(enabled = tab == HomeTab.ARCHIVE) { onTabChange(HomeTab.WEEK) }
    // Hebt den Zustand des gerade nicht sichtbaren Reiters auf (z. B. wie weit gescrollt war).
    val tabStates = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize().background(Relic.colors.paper)) {
        HomeHeader(selected = tab, onSelect = onTabChange, onOpenSettings = onOpenSettings, onSearch = onOpenSearch)
        // Nur Durchsichtigkeit ändert sich – das schaffen auch ältere Handys mühelos.
        Crossfade(targetState = tab, animationSpec = tween(TAB_FADE_MILLIS), label = "Reiter") { shown ->
            tabStates.SaveableStateProvider(shown.name) {
                if (shown == HomeTab.WEEK) week() else archive()
            }
        }
    }
}
