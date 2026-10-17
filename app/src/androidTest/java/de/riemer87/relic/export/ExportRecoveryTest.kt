package de.riemer87.relic.export

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Akku leer oder „Beenden erzwingen“ mitten im Export: Der nächste Start räumt die halbe ZIP-Datei weg. */
@RunWith(AndroidJUnit4::class)
class ExportRecoveryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val record = ExportRecord(context)
    private val target = "content://com.android.providers.downloads.documents/document/42".toUri()
    private val deleted = mutableListOf<android.net.Uri>()

    private fun resetAll() = context.getSharedPreferences("export", android.content.Context.MODE_PRIVATE).edit().clear().commit()

    @Before
    fun setUp() {
        resetAll()
    }

    @After
    fun tearDown() {
        resetAll()
    }

    @Test
    fun nothingToDoWithoutUnfinishedExport() {
        val result = recoverInterruptedExport(record, exportRunning = false) { deleted += it; true }

        assertNull(result)
        assertEquals(emptyList<android.net.Uri>(), deleted)
    }

    @Test
    fun interruptedExportIsDeletedAndReported() {
        record.begin(target, "Relic-Export-2026-10-17.zip")

        val result = recoverInterruptedExport(record, exportRunning = false) { deleted += it; true }

        assertEquals(InterruptedExport(targetDeleted = true, targetName = "Relic-Export-2026-10-17.zip"), result)
        assertEquals(listOf(target), deleted)
        assertNull("nur einmal melden", record.pending())
    }

    /** Läuft der Export noch (App nur neu geöffnet), darf seine Datei auf keinen Fall gelöscht werden. */
    @Test
    fun runningExportIsLeftAlone() {
        record.begin(target, "Relic-Export.zip")

        val result = recoverInterruptedExport(record, exportRunning = true) { deleted += it; true }

        assertNull(result)
        assertEquals(emptyList<android.net.Uri>(), deleted)
        assertNotNull(record.pending())
    }

    /** Wunsch aus Meilenstein 10: Der letzte erfolgreiche Export bleibt mit Zeitpunkt gespeichert. */
    @Test
    fun lastSuccessIsKeptWhenTheNextExportFinishesOrIsInterrupted() {
        val done = ExportState.Done(files = 14, skipped = 1, finishedAt = Instant.parse("2026-10-16T23:40:00Z"))
        record.saveSuccess(done)

        record.begin(target, "Relic-Export.zip")
        record.clear()
        assertEquals(done, ExportRecord(context).lastSuccess())

        record.begin(target, "Relic-Export.zip")
        recoverInterruptedExport(record, exportRunning = false) { true }
        assertEquals(done, ExportRecord(context).lastSuccess())
    }

    @Test
    fun noLastSuccessBeforeTheFirstExport() {
        assertNull(record.lastSuccess())
    }

    /** Lässt sich die Datei nicht löschen, nennt der Hinweis ihren Namen, damit man sie selbst löschen kann. */
    @Test
    fun undeletableFileIsReportedByName() {
        record.begin(target, "Relic-Export.zip")

        val result = recoverInterruptedExport(record, exportRunning = false) { throw SecurityException("keine Freigabe") }

        assertEquals(InterruptedExport(targetDeleted = false, targetName = "Relic-Export.zip"), result)
        assertNull(record.pending())
    }
}
