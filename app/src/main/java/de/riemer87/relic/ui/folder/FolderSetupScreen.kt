package de.riemer87.relic.ui.folder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.riemer87.relic.R
import de.riemer87.relic.journal.FolderDecision
import de.riemer87.relic.journal.FolderOption
import de.riemer87.relic.ui.dashedOutline
import de.riemer87.relic.ui.intro.IntroProgress
import de.riemer87.relic.ui.intro.IntroText
import de.riemer87.relic.ui.intro.IntroTitle
import de.riemer87.relic.ui.intro.StepDots
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme

/**
 * „Wo soll dein Tagebuch liegen?“ – erscheint, solange kein Ordner eingerichtet ist,
 * und aus den Einstellungen zum Wechseln des Ordners (dann mit [onBack] und [notice]).
 * In der Einführung beim ersten Start zeigt [introProgress] die Fortschrittspunkte.
 */
@Composable
fun FolderSetupScreen(
    onFolderReady: (Uri) -> Unit,
    viewModel: FolderSetupViewModel = viewModel(),
    onBack: (() -> Unit)? = null,
    notice: String? = null,
    introProgress: IntroProgress? = null,
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
        introProgress = introProgress,
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
    introProgress: IntroProgress?,
    onOptionSelected: (FolderOption) -> Unit,
    onHintConfirmed: () -> Unit,
    onHintDismissed: () -> Unit,
    onQuestionConfirmed: () -> Unit,
    onQuestionRejected: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val colors = Relic.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = if (onBack != null) 4.dp else 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = colors.ink)
            }
        }
        if (introProgress != null) {
            StepDots(introProgress)
            Spacer(Modifier.height(4.dp))
        }
        IntroTitle(stringResource(R.string.folder_title))
        IntroText(stringResource(R.string.folder_text))
        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sheet, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            )
        }
        Spacer(Modifier.height(4.dp))

        FolderOptionSheet(
            title = stringResource(R.string.folder_recommended),
            detail = stringResource(R.string.folder_recommended_detail),
            style = OptionStyle.MAIN,
            enabled = !state.busy,
            onClick = { onOptionSelected(FolderOption.RECOMMENDED) },
        )
        FolderOptionSheet(
            title = stringResource(R.string.folder_own),
            detail = stringResource(R.string.folder_own_detail),
            style = OptionStyle.SHEET,
            enabled = !state.busy,
            onClick = { onOptionSelected(FolderOption.OWN) },
        )
        FolderOptionSheet(
            title = stringResource(R.string.folder_existing),
            detail = null,
            style = OptionStyle.DASHED,
            enabled = !state.busy,
            onClick = { onOptionSelected(FolderOption.EXISTING) },
        )

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.folder_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
        )
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
            containerColor = colors.sheet,
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
            containerColor = colors.sheet,
        )
    }

    if (state.error) {
        AlertDialog(
            onDismissRequest = onErrorShown,
            text = { Text(stringResource(R.string.folder_access_failed)) },
            confirmButton = { TextButton(onClick = onErrorShown) { Text(stringResource(R.string.ok)) } },
            containerColor = colors.sheet,
        )
    }
}

private enum class OptionStyle { MAIN, SHEET, DASHED }

/** Eine Auswahl als flaches Blatt: Hauptwahl mit Tintenrand, die zweite als Blatt, die dritte gestrichelt. */
@Composable
private fun FolderOptionSheet(title: String, detail: String?, style: OptionStyle, enabled: Boolean, onClick: () -> Unit) {
    val colors = Relic.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                when (style) {
                    OptionStyle.MAIN -> Modifier.background(colors.sheet).border(1.5.dp, colors.ink, shape)
                    OptionStyle.SHEET -> Modifier.background(colors.sheet)
                    OptionStyle.DASHED -> Modifier.dashedOutline(colors.rule)
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.ink)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FolderSetupPreview() {
    RelicTheme {
        FolderSetupContent(FolderSetupUiState(), null, null, IntroProgress(1, 4), {}, {}, {}, {}, {}, {})
    }
}
