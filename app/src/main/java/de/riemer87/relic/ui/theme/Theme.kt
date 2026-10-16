package de.riemer87.relic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Zugriff auf die Relic-Farben: `Relic.colors.ink` usw. */
object Relic {
    val colors: RelicColors
        @Composable @ReadOnlyComposable
        get() = LocalRelicColors.current
}

/** Papier-Thema. Vorerst nur hell; Dunkelmodus und Akzentfarben folgen in Meilenstein 12. */
@Composable
fun RelicTheme(content: @Composable () -> Unit) {
    val colors = PaperLight
    // Standard-Bausteine von Material (Knöpfe, Dialoge, Meldungen) passend einfärben.
    val scheme = lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.inkSoft,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.paper,
        onSurface = colors.ink,
        surfaceVariant = colors.sheet,
        onSurfaceVariant = colors.inkSoft,
        surfaceContainer = colors.sheet,
        surfaceContainerHigh = colors.sheet,
        surfaceContainerHighest = colors.sheet,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.sheet,
        outline = colors.rule,
        outlineVariant = colors.rule,
    )
    CompositionLocalProvider(LocalRelicColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = RelicTypography, content = content)
    }
}
