package de.riemer87.relic.ui.folder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.riemer87.relic.R
import de.riemer87.relic.journal.FolderDecision
import de.riemer87.relic.journal.FolderOption
import de.riemer87.relic.ui.theme.RelicTheme

/**
 * „Wo soll dein Tagebuch liegen?“ – erscheint, solange kein Ordner eingerichtet ist,
 * und aus den Einstellungen zum Wechseln des Ordners (dann mit [onBack] und [notice]).
 */
@Composable
fun FolderSetupScreen(
    onFolderReady: (Uri) -> Unit,
    viewModel: FolderSetupViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    notice: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onFolderPicked(uri)
    }

    LaunchedEffect(state.readyUri) {
        val uri = state.readyUri ?: return@LaunchedEffect
        // Sofort vergessen: Erscheint der Bildschirm später erneut (Ordner verloren),
        // darf er den alten Ordner nicht noch einmal melden.
        viewModel.readyHandled()
        onFolderReady(uri)
    }

    FolderSetupContent(
        state = state,
        onBack = onBack,
        notice = notice,
        onOptionSelected = viewModel::onOptionSelected,
        onHintConfirmed = {
            val initialUri = state.hint?.initialUri
            viewModel.hintClosed()
            picker.launch(initialUri)
        },
        onHintDismissed = viewModel::hintClosed,
        onQuestionConfirmed = viewModel::confirmQuestion,
        onQuestionRejected = {
            viewModel.rejectQuestion()
            picker.launch(viewModel.lastInitialUri)
        },
        onErrorShown = viewModel::errorShown,
    )
}

@Composable
private fun FolderSetupContent(
    state: FolderSetupUiState,
    onBack: (() -> Unit)?,
    notice: String?,
    onOptionSelected: (FolderOption) -> Unit,
    onHintConfirmed: () -> Unit,
    onHintDismissed: () -> Unit,
    onQuestionConfirmed: () -> Unit,
    onQuestionRejected: () -> Unit,
    onErrorShown: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            }
            Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.folder_text), style = MaterialTheme.typography.bodyLarge)
            if (notice != null) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onOptionSelected(FolderOption.RECOMMENDED) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.folder_recommended))
            }
            OptionDetail(stringResource(R.string.folder_recommended_detail))

            OutlinedButton(
                onClick = { onOptionSelected(FolderOption.OWN) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.folder_own))
            }
            OptionDetail(stringResource(R.string.folder_own_detail))

            TextButton(
                onClick = { onOptionSelected(FolderOption.EXISTING) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.folder_existing))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.folder_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.hint?.let { hint ->
        val text = when {
            hint.findExisting -> stringResource(R.string.folder_hint_existing)
            hint.mustCreateFolder -> stringResource(R.string.folder_hint_create) + "\n\n" + stringResource(R.string.folder_hint)
            else -> stringResource(R.string.folder_hint)
        }
        AlertDialog(
            onDismissRequest = onHintDismissed,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = onHintConfirmed) { Text(stringResource(R.string.folder_continue)) } },
            dismissButton = { TextButton(onClick = onHintDismissed) { Text(stringResource(R.string.cancel)) } },
        )
    }

    state.question?.let { question ->
        val (message, confirm) = when (question.decision) {
            FolderDecision.ASK_CONNECT_EXISTING -> R.string.folder_found_journal to R.string.folder_connect
            else -> R.string.folder_no_journal to R.string.folder_use_anyway
        }
        AlertDialog(
            onDismissRequest = {},
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = onQuestionConfirmed) { Text(stringResource(confirm)) } },
            dismissButton = { TextButton(onClick = onQuestionRejected) { Text(stringResource(R.string.folder_choose_other)) } },
        )
    }

    if (state.error) {
        AlertDialog(
            onDismissRequest = onErrorShown,
            text = { Text(stringResource(R.string.folder_access_failed)) },
            confirmButton = { TextButton(onClick = onErrorShown) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun OptionDetail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Preview(showBackground = true)
@Composable
private fun FolderSetupPreview() {
    RelicTheme {
        FolderSetupContent(FolderSetupUiState(), null, null, {}, {}, {}, {}, {}, {})
    }
}
