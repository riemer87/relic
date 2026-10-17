package de.riemer87.relic.export

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Stand des laufenden Exports, für Einstellungen und Dienst gemeinsam (solange die App läuft). */
object ExportStatus {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    internal fun update(state: ExportState) {
        _state.value = state
    }
}

/** Ein Export, der begonnen, aber nie abgeschlossen wurde. */
data class PendingExport(val target: Uri, val name: String)

/** Beim nächsten Start gefunden: ein unterbrochener Export und ob seine halbe Datei gelöscht werden konnte. */
data class InterruptedExport(val targetDeleted: Boolean, val targetName: String)

/**
 * Merkt sich dauerhaft, dass gerade ein Export läuft. Wird die App mittendrin hart beendet
 * (Akku leer, „Beenden erzwingen“), findet der nächste Start den Eintrag und räumt auf.
 */
class ExportRecord(context: Context) {

    private val prefs = context.getSharedPreferences("export", Context.MODE_PRIVATE)

    fun begin(target: Uri, name: String) = prefs.edit(commit = true) {
        putString(KEY_URI, target.toString())
        putString(KEY_NAME, name)
    }

    /** Der laufende Export ist beendet (der letzte Erfolg bleibt erhalten). */
    fun clear() = prefs.edit(commit = true) {
        remove(KEY_URI)
        remove(KEY_NAME)
    }

    /** Merkt sich den letzten erfolgreichen Export – auch über einen Neustart der App hinaus. */
    fun saveSuccess(done: ExportState.Done) = prefs.edit(commit = true) {
        putInt(KEY_DONE_FILES, done.files)
        putInt(KEY_DONE_SKIPPED, done.skipped)
        putLong(KEY_DONE_AT, (done.finishedAt ?: Instant.now()).toEpochMilli())
    }

    fun lastSuccess(): ExportState.Done? {
        if (!prefs.contains(KEY_DONE_AT)) return null
        return ExportState.Done(
            files = prefs.getInt(KEY_DONE_FILES, 0),
            skipped = prefs.getInt(KEY_DONE_SKIPPED, 0),
            finishedAt = Instant.ofEpochMilli(prefs.getLong(KEY_DONE_AT, 0)),
        )
    }

    fun pending(): PendingExport? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return PendingExport(uri.toUri(), prefs.getString(KEY_NAME, null).orEmpty())
    }

    private companion object {
        const val KEY_URI = "target_uri"
        const val KEY_NAME = "target_name"
        const val KEY_DONE_FILES = "done_files"
        const val KEY_DONE_SKIPPED = "done_skipped"
        const val KEY_DONE_AT = "done_at"
    }
}

/**
 * Beim App-Start: Gibt es einen begonnenen Export, der nicht mehr läuft, war er unterbrochen.
 * Dann die halbe ZIP-Datei löschen, damit sie nie für eine fertige Sicherung gehalten wird.
 */
fun recoverInterruptedExport(record: ExportRecord, exportRunning: Boolean, delete: (Uri) -> Boolean): InterruptedExport? {
    if (exportRunning) return null
    val pending = record.pending() ?: return null
    val deleted = try {
        delete(pending.target)
    } catch (e: Exception) {
        false
    }
    record.clear()
    return InterruptedExport(deleted, pending.name)
}
