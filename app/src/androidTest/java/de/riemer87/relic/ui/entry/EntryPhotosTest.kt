package de.riemer87.relic.ui.entry

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import de.riemer87.relic.journal.MAX_PHOTOS
import de.riemer87.relic.journal.NewPhoto
import de.riemer87.relic.journal.StoredPhoto
import de.riemer87.relic.ui.FakeJournalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Foto-Logik der Eintragsseite (ohne Fotoauswahl, die gehört Android). */
@RunWith(AndroidJUnit4::class)
class EntryPhotosTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val clock = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.of(2026, 10, 16)
    private val pendingDir = File(context.cacheDir, "entry-photos-test").apply { mkdirs() }

    /** Liefert für jede „ausgewählte“ Adresse ein fertiges JPEG; Adressen mit „kaputt“ scheitern. */
    private val preparer = PhotoPreparer { uri ->
        if ("kaputt" in uri.toString()) null
        else File(pendingDir, "${System.nanoTime()}.jpg").apply { writeBytes(FakeJournalStore.testJpeg()) }
    }

    private fun uris(count: Int) = (1..count).map { Uri.parse("content://test/foto$it") }

    @After
    fun cleanUp() {
        pendingDir.deleteRecursively()
    }

    private fun viewModel(store: FakeJournalStore, date: LocalDate = today): EntryViewModel {
        lateinit var vm: EntryViewModel
        instrumentation.runOnMainSync { vm = EntryViewModel(store, date, clock, preparer) }
        waitFor { !vm.state.value.isLoading }
        return vm
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Bedingung nicht erfüllt")
            Thread.sleep(20)
        }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Test
    fun addedPhotosAreSavedWhenClosing() {
        val store = FakeJournalStore()
        val vm = viewModel(store)

        main { vm.addPhotos(uris(2)) }
        waitFor { vm.state.value.photos.size == 2 }
        main { vm.close() }
        waitFor { vm.state.value.closeWith != null }

        assertEquals(R.string.entry_saved, vm.state.value.closeWith)
        assertEquals(listOf("2026-10-16_1.jpg", "2026-10-16_2.jpg"), store.photosOf(today))
        assertEquals("Zwischenspeicher aufgeräumt", 0, pendingDir.list()!!.size)
    }

    @Test
    fun atMostFourPhotosPerDay() {
        val store = FakeJournalStore(initialPhotos = mapOf(today to 3))
        val vm = viewModel(store)

        main { vm.addPhotos(uris(3)) }
        waitFor { !vm.state.value.isAddingPhotos && vm.state.value.message != null }

        assertEquals(MAX_PHOTOS, vm.state.value.photos.size)
        assertEquals(EntryMessage(R.plurals.photo_limit, MAX_PHOTOS), vm.state.value.message)

        main { vm.messageShown(); vm.addPhotos(uris(1)) }
        waitFor { vm.state.value.message != null }
        assertEquals(MAX_PHOTOS, vm.state.value.photos.size)
    }

    @Test
    fun brokenPhotoIsSkippedWithMessage() {
        val vm = viewModel(FakeJournalStore())

        main { vm.addPhotos(listOf(Uri.parse("content://test/kaputt"), Uri.parse("content://test/gut"))) }
        waitFor { !vm.state.value.isAddingPhotos && vm.state.value.message != null }

        assertEquals(1, vm.state.value.photos.size)
        assertEquals(EntryMessage(R.string.photo_failed), vm.state.value.message)
    }

    @Test
    fun removingAPhotoDeletesItWhenSaved() {
        val store = FakeJournalStore(mapOf(today to "Text"), initialPhotos = mapOf(today to 2))
        val vm = viewModel(store)

        main { vm.removePhoto(StoredPhoto("2026-10-16_1.jpg")) }
        main { vm.close() }
        waitFor { vm.state.value.closeWith != null }

        assertEquals(listOf("2026-10-16_2.jpg"), store.photosOf(today))
    }

    @Test
    fun removingAnUnsavedPhotoDeletesItsTemporaryFile() {
        val vm = viewModel(FakeJournalStore())
        main { vm.addPhotos(uris(1)) }
        waitFor { vm.state.value.photos.size == 1 }
        val pending = vm.state.value.photos.single() as NewPhoto

        main { vm.removePhoto(pending) }
        assertFalse(pending.file.exists())
    }

    @Test
    fun backgroundSaveFollowedByMorePhotosCreatesNoDuplicates() {
        val store = FakeJournalStore()
        val vm = viewModel(store)

        main { vm.addPhotos(uris(1)) }
        waitFor { vm.state.value.photos.size == 1 }
        main { vm.saveInBackground() } // wie beim Öffnen der Fotoauswahl
        waitFor { vm.state.value.photos.single() is StoredPhoto }

        main { vm.addPhotos(uris(1)) }
        waitFor { vm.state.value.photos.size == 2 }
        main { vm.close() }
        waitFor { vm.state.value.closeWith != null }

        assertEquals(listOf("2026-10-16_1.jpg", "2026-10-16_2.jpg"), store.photosOf(today))
    }

    @Test
    fun photosOnlyMakeAPastDayReadOnly() {
        val yesterday = today.minusDays(1)
        val vm = viewModel(FakeJournalStore(initialPhotos = mapOf(yesterday to 1)), yesterday)
        assertEquals(EntryMode.READ, vm.state.value.mode)
        assertTrue(vm.state.value.photos.isNotEmpty())
    }
}
