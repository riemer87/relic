package de.riemer87.relic.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.ui.theme.RelicTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(viewModel: TodayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayContent(
        state = state,
        onTextChange = viewModel::onTextChange,
        onSave = viewModel::save,
        onMessageShown = viewModel::messageShown,
    )
}

@Composable
internal fun TodayContent(
    state: TodayUiState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            // Eigene Coroutine, damit die Anzeige nicht abbricht, wenn die Meldung gleich als
            // „angezeigt“ markiert wird und dieser Effekt dadurch neu startet.
            scope.launch { snackbarHostState.showSnackbar(message) }
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Die Tastatur zählt als freizuhaltender Bereich, damit Inhalt und Meldungen über ihr bleiben.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatLongDate(state.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.entry_title_today),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                enabled = !state.isLoading,
                placeholder = { Text(stringResource(R.string.entry_text_placeholder)) },
                supportingText = {
                    Text(stringResource(R.string.entry_char_count, state.text.length, MAX_ENTRY_LENGTH))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Button(
                onClick = onSave,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.entry_save))
            }
        }
    }
}

/** Langes Datum in der Systemsprache, z. B. „Freitag · 9. Oktober“ / „Friday · October 9“. */
@Composable
private fun formatLongDate(date: LocalDate): String {
    val pattern = stringResource(R.string.entry_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, pattern, locale) {
        DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayContentPreview() {
    RelicTheme {
        TodayContent(
            state = TodayUiState(date = LocalDate.of(2026, 10, 9), text = "Heute war ein guter Tag.", isLoading = false),
            onTextChange = {},
            onSave = {},
            onMessageShown = {},
        )
    }
}
