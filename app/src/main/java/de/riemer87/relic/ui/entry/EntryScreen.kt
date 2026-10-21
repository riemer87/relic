package de.riemer87.relic.ui.entry

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.journal.MAX_PHOTOS
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.longDate
import de.riemer87.relic.ui.photos.PhotoImage
import de.riemer87.relic.ui.photos.PhotoViewer
import de.riemer87.relic.ui.theme.SheetShape
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalDate

/**
 * Eintragsseite für einen beliebigen Tag.
 * @param onClose Seite schließen; Meldung für „Diese Woche“ bzw. das Archiv oder `null`.
 * @param showMessage Meldung anzeigen, während die Seite offen bleibt (z. B. Fehler).
 */
@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    onClose: (message: Int?) -> Unit,
    showMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    BackHandler { viewModel.close() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveInBackground() }

    LaunchedEffect(state.closeWith) {
        state.closeWith?.let { onClose(it.takeIf { message -> message != 0 }) }
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            viewModel.messageShown()
            showMessage(
                if (message.number != null) resources.getQuantityString(message.text, message.number, message.number)
                else resources.getString(message.text),
            )
        }
    }

    // Android-Fotoauswahl: die App sieht nur die ausgewählten Fotos, keine Galerie-Berechtigung nötig.
    val remaining = MAX_PHOTOS - state.photos.size
    val pickSeveral = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = remaining.coerceAtLeast(2)),
    ) { uris -> viewModel.addPhotos(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPhotos(listOf(it)) }
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    EntryContent(
        state = state,
        onTextChange = viewModel::onTextChange,
        onEdit = viewModel::edit,
        onClose = viewModel::close,
        onAddPhotos = { if (remaining > 1) pickSeveral.launch(imagesOnly) else pickOne.launch(imagesOnly) },
        onRemovePhoto = viewModel::removePhoto,
    )
}

@Composable
internal fun EntryContent(
    state: EntryUiState,
    onTextChange: (String) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onAddPhotos: () -> Unit,
    onRemovePhoto: (DayPhoto) -> Unit,
) {
    val colors = Relic.colors
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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
                .background(colors.sheet, SheetShape)
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

            val editing = state.mode == EntryMode.EDIT
            if (editing) {
                EntryTextField(state, onTextChange, Modifier.weight(1f))
            } else {
                Text(
                    text = state.text.ifBlank { stringResource(R.string.entry_empty) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.text.isBlank()) colors.inkFaint else colors.ink,
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                )
            }

            if (editing || state.photos.isNotEmpty()) {
                PhotoRow(
                    date = state.date,
                    photos = state.photos,
                    editing = editing && !state.isLoading,
                    isAdding = state.isAddingPhotos,
                    onOpen = { viewerIndex = it },
                    onAdd = onAddPhotos,
                    onRemove = onRemovePhoto,
                )
            }

            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.entry_char_count, state.text.length, MAX_ENTRY_LENGTH),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.inkFaint,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onClose,
                        enabled = !state.isLoading && !state.isSaving && !state.isAddingPhotos,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    ) {
                        Text(stringResource(R.string.entry_save), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    viewerIndex?.let { index ->
        if (state.photos.isEmpty()) {
            viewerIndex = null
        } else {
            PhotoViewer(date = state.date, photos = state.photos, startIndex = index, onDismiss = { viewerIndex = null })
        }
    }
}

@Composable
private fun EntryTextField(state: EntryUiState, onTextChange: (String) -> Unit, modifier: Modifier) {
    val colors = Relic.colors
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink)
    BasicTextField(
        value = state.text,
        onValueChange = onTextChange,
        enabled = !state.isLoading,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.ink),
        modifier = modifier
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
}

/** Fotos des Tages in einer Reihe, Zähler „x / 4“; beim Bearbeiten mit ✕ und „+“. */
@Composable
private fun PhotoRow(
    date: LocalDate,
    photos: List<DayPhoto>,
    editing: Boolean,
    isAdding: Boolean,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (DayPhoto) -> Unit,
) {
    val colors = Relic.colors
    val tile = 64.dp
    val tilePx = with(LocalDensity.current) { tile.roundToPx() } * 2

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.entry_photos).uppercase(currentLocale()), style = KickerStyle, color = colors.inkSoft)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.photo_position, photos.size, MAX_PHOTOS),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkFaint,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEachIndexed { index, photo ->
                Box(Modifier.size(tile)) {
                    PhotoImage(
                        date = date,
                        photo = photo,
                        maxEdgePx = tilePx,
                        contentDescription = stringResource(R.string.photo_number, index + 1),
                        modifier = Modifier.size(tile).clip(RoundedCornerShape(8.dp)).clickable { onOpen(index) },
                    )
                    if (editing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.ink.copy(alpha = 0.75f))
                                .clickable { onRemove(photo) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                stringResource(R.string.photo_remove, index + 1),
                                tint = colors.sheet,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            if (editing && photos.size < MAX_PHOTOS) {
                Box(
                    modifier = Modifier
                        .size(tile)
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = colors.rule,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                                ),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                            )
                        }
                        .clickable(enabled = !isAdding, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(color = colors.inkSoft, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.photo_add), tint = colors.inkSoft)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun EntryEditPreview() {
    RelicTheme {
        EntryContent(
            EntryUiState(date = LocalDate.of(2026, 10, 16), isToday = true, mode = EntryMode.EDIT, isLoading = false),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
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
            {}, {}, {}, {}, {},
        )
    }
}
