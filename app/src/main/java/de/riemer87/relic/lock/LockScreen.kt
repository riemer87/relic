package de.riemer87.relic.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.Relic

/**
 * Legt bei gesperrter App den Sperrbildschirm über den Inhalt. Der Inhalt darunter bleibt bestehen
 * (nichts Getipptes geht verloren), ist aber unsichtbar und für Bildschirmleser stumm.
 */
@Composable
fun AppLockGate(
    locked: Boolean,
    authenticator: Authenticator,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Den Android-Dialog nur einmal je Sperre automatisch öffnen – sonst öffnet er sich nach „Abbrechen“ sofort wieder.
    var autoPrompted by remember(locked) { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        if (prompting) return
        prompting = true
        authenticator.authenticate { success ->
            prompting = false
            if (success) onUnlocked()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(locked) {
        if (!locked) return@LaunchedEffect
        // Erst wenn die App wirklich sichtbar ist – der Android-Dialog braucht das.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!autoPrompted) {
                autoPrompted = true
                prompt()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
            content()
        }
        if (locked) LockScreen(onUnlock = ::prompt)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val colors = Relic.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            // Fängt alle Berührungen ab, damit nichts darunter angetippt wird.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = colors.ink)
            Text(stringResource(R.string.lock_locked), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
