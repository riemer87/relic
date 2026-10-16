package de.riemer87.relic.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.longDate
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalDate

/**
 * Eintragsseite für einen beliebigen Tag.
 * @param onClose Seite schließen; Meldung für den Zeitstrahl oder `null`.
 * @param showMessage Meldung anzeigen, während die Seite offen bleibt (z. B. Fehler).
 */
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onClose: (message: Int?) -> Unit,
    showMessage: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { viewModel.close() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }

    LaunchedEffect(state.closeWith) {
        state.closeWith?.let { onClose(it.takeIf { message -> message != 0 }) }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            viewModel.errorShown()
            showMessage(it)
        }
    }

    EntryContent(
        state = state,
        onTextChange = viewModel::onTextChange,
        onEdit = viewModel::edit,
        onClose = viewModel::close,
    )
}

@Composable
internal fun EntryContent(
    state: EntryUiState,
    onTextChange: (String) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Relic.colors
    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = colors.ink)
            }
            Spacer(Modifier.weight(1f))
            if (state.mode == EntryMode.READ && !state.isLoading) {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit), tint = colors.ink)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .background(colors.sheet, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = longDate(state.date).uppercase(currentLocale()),
                style = KickerStyle,
                color = colors.inkSoft,
            )
            Text(
                text = stringResource(if (state.isToday) R.string.entry_title_today else R.string.entry_title_past),
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink,
            )

            if (state.mode == EntryMode.EDIT) {
                EntryEditor(state, onTextChange, onSave = onClose, modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = state.text.ifBlank { stringResource(R.string.entry_empty) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.text.isBlank()) colors.inkFaint else colors.ink,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryEditor(
    state: EntryUiState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Relic.colors
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(
            value = state.text,
            onValueChange = onTextChange,
            enabled = !state.isLoading,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .drawBehind {
                    val y = size.height - 0.75.dp.toPx()
                    drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                }
                .padding(top = 8.dp, bottom = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (state.text.isEmpty()) {
                        Text(stringResource(R.string.entry_text_placeholder), style = textStyle, color = colors.inkFaint)
                    }
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.entry_char_count, state.text.length, MAX_ENTRY_LENGTH),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = !state.isLoading && !state.isSaving,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
            ) {
                Text(stringResource(R.string.entry_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun EntryEditPreview() {
    RelicTheme {
        EntryContent(
            EntryUiState(date = LocalDate.of(2026, 10, 16), isToday = true, mode = EntryMode.EDIT, isLoading = false),
            {}, {}, {},
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun EntryReadPreview() {
    RelicTheme {
        EntryContent(
            EntryUiState(
                date = LocalDate.of(2026, 10, 14),
                isToday = false,
                text = "Spaziergang am Fluss, abends gekocht.",
                isLoading = false,
            ),
            {}, {}, {},
        )
    }
}
