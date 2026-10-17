package de.riemer87.relic.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.JournalTextCache
import de.riemer87.relic.ui.FakeJournalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Collections

/**
 * Fehler aus Meilenstein 13: Beim Öffnen der Suche blitzte „Einträge werden durchsucht …“ kurz auf,
 * obwohl die Texte schnell (meist aus dem Zwischenspeicher) da waren.
 */
@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {

    private val observer = CoroutineScope(Dispatchers.Unconfined)
    private val seen = Collections.synchronizedList(mutableListOf<SearchUiState>())

    @After
    fun tearDown() = observer.cancel()

    private fun store() = FakeJournalStore(mapOf(LocalDate.of(2026, 10, 3) to "Am Fluss spaziert"))

    /** Wie [store], aber das Lesen dauert so lange wie bei einem großen Tagebuch nach dem Start. */
    private class SlowStore(private val inner: JournalStore, private val millis: Long) : JournalStore by inner {
        override fun entryDates(): Set<LocalDate> {
            Thread.sleep(millis)
            return inner.entryDates()
        }
    }

    /** Erstellt die Suche im Hauptthread und merkt sich jeden Zustand, den sie annimmt. */
    private fun searchFor(store: JournalStore): SearchViewModel {
        lateinit var viewModel: SearchViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = SearchViewModel(store, JournalTextCache(store), SavedStateHandle())
        }
        observer.launch { viewModel.state.collect { seen += it } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.setQuery("fluss")
            viewModel.refresh()
        }
        return viewModel
    }

    private fun waitForHits(viewModel: SearchViewModel) {
        val until = System.currentTimeMillis() + 5_000
        while (viewModel.state.value.hits.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertEquals(1, viewModel.state.value.hits.size)
    }

    @Test
    fun fastLoadingNeverShowsTheHint() {
        val viewModel = searchFor(store())

        waitForHits(viewModel)

        assertFalse("Hinweis blitzte auf: $seen", seen.toList().any { it.loading })
    }

    @Test
    fun slowLoadingShowsTheHintAndThenTheHits() {
        val viewModel = searchFor(SlowStore(store(), millis = 1_500))

        waitForHits(viewModel)

        assertTrue("Hinweis fehlte bei langem Einlesen", seen.toList().any { it.loading })
        assertFalse(viewModel.state.value.loading)
    }
}
