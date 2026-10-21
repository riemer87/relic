package de.riemer87.relic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.riemer87.relic.R
import de.riemer87.relic.ui.archive.ARCHIVE_PREVIEW_LINES
import de.riemer87.relic.journal.SearchHit
import de.riemer87.relic.ui.DateText
import de.riemer87.relic.ui.DayPreviewContent
import de.riemer87.relic.ui.DaySheet
import de.riemer87.relic.ui.currentLocale
import de.riemer87.relic.ui.theme.SheetShape
import de.riemer87.relic.ui.theme.KickerStyle
import de.riemer87.relic.ui.theme.Relic
import java.time.LocalDate

/**
 * Suche als eigene Seite: Pfeil zurück (oder Zurück-Geste) führt dorthin, wo man herkam.
 * Die Tastatur öffnet sich nur beim ersten Öffnen, nicht beim Zurückkommen aus einem Treffer.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val colors = Relic.colors
    val search by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var focusNow by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().background(colors.paper)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = colors.ink)
            }
        }
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        SearchField(
            query = search.query,
            onQueryChange = viewModel::setQuery,
            focusNow = focusNow,
            onFocused = { focusNow = false },
        )
        SearchResults(search = search, listState = listState, onOpenDay = onOpenDay)
    }
}

/**
 * Suchfeld auf einem Blatt, mit Lupe links und ✕ rechts. Das ✕ leert nur das Feld; geschlossen wird
 * die Suche mit dem Pfeil oben links oder der Zurück-Geste.
 * @param focusNow Tastatur sofort öffnen (nur beim Antippen der Lupe, nicht beim Zurückkommen)
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focusNow: Boolean, onFocused: () -> Unit) {
    val colors = Relic.colors
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val placeholder = stringResource(R.string.search_placeholder)
    LaunchedEffect(focusNow) {
        if (focusNow) {
            focusRequester.requestFocus()
            onFocused()
        }
    }
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(SheetShape)
            .background(colors.sheet)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = colors.inkSoft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.inkFaint)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = {
                    onQueryChange("")
                    focusRequester.requestFocus() // gleich das nächste Wort tippen
                },
            ) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear), tint = colors.inkSoft)
            }
        }
    }
}

/** Trefferliste: „x Einträge“ und die Einträge als Blätter, neueste zuerst. */
@Composable
private fun SearchResults(search: SearchUiState, listState: LazyListState, onOpenDay: (LocalDate) -> Unit) {
    val colors = Relic.colors
    val locale = currentLocale()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "status") {
            val status = when {
                search.loading && search.query.isNotBlank() -> stringResource(R.string.search_loading)
                search.showNoResults -> stringResource(R.string.search_no_results, search.resultQuery)
                search.resultQuery.isEmpty() || search.query.isBlank() -> ""
                else -> pluralStringResource(R.plurals.archive_entries, search.hits.size, search.hits.size)
            }
            Text(
                text = status,
                style = if (search.showNoResults) MaterialTheme.typography.bodyMedium else KickerStyle,
                color = if (search.showNoResults) colors.inkSoft else colors.inkFaint,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
        if (search.query.isNotBlank()) {
            items(search.hits, key = { it.date.toEpochDay() }) { hit ->
                DaySheet(
                    date = hit.date,
                    filled = true,
                    numberColor = colors.ink,
                    kicker = DateText.monthYear(hit.date, locale),
                    kickerColor = colors.inkFaint,
                    onClick = { onOpenDay(hit.date) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DayPreviewContent(hit.date, highlighted(hit), hit.photos, maxLines = ARCHIVE_PREVIEW_LINES, photoSize = 56.dp)
                }
            }
        }
    }
}

/** Der Vorschautext mit fett hervorgehobenen Fundstellen. */
private fun highlighted(hit: SearchHit): AnnotatedString = buildAnnotatedString {
    var position = 0
    for (range in hit.matches) {
        append(hit.snippet.substring(position, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(hit.snippet.substring(range)) }
        position = range.last + 1
    }
    append(hit.snippet.substring(position))
}
