package de.riemer87.relic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Zugriff auf die Relic-Farben: `Relic.colors.ink` usw. */
object Relic {
    val colors: RelicColors
        @Composable @ReadOnlyComposable
        get() = LocalRelicColors.current

    /** `true` im Dunkelmodus. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalRelicDark.current
}

private val LocalRelicDark = staticCompositionLocalOf { false }

/** Papier-Thema, hell oder dunkel, mit der gewählten Akzentfarbe (Standard: wie das Handy, Tinte). */
@Composable
fun RelicTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors = relicColors(dark, appearance.accent)
    // Standard-Bausteine von Material (Knöpfe, Dialoge, Meldungen) passend einfärben.
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
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
        surfaceContainerLow = colors.sheet,
        surfaceContainerLowest = colors.paper,
        surfaceBright = colors.sheet,
        surfaceDim = colors.paper,
        surfaceTint = colors.paper,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.sheet,
        outline = colors.rule,
        outlineVariant = colors.rule,
    )
    CompositionLocalProvider(LocalRelicColors provides colors, LocalRelicDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = RelicTypography, content = content)
    }
}
