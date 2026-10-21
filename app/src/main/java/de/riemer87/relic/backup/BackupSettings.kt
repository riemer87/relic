package de.riemer87.relic.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime

/** Warum die automatische Sicherung gerade nicht klappt. */
enum class BackupProblem {
    /** Sicherungsordner weg oder Freigabe entzogen: neu wählen. */
    FOLDER_MISSING,

    /** Tagebuch-Ordner gerade nicht erreichbar. */
    JOURNAL_MISSING,

    /** Sicherungsordner liegt im Tagebuch-Ordner (z. B. nach einem Ordnerwechsel). */
    FOLDER_IN_JOURNAL,

    /** Schreiben oder Prüfen fehlgeschlagen (z. B. Speicher voll); wird erneut versucht. */
    FAILED,
}

/** Alles, was die Einstellungen über die automatische Sicherung anzeigen. */
data class BackupInfo(
    val enabled: Boolean = false,
    val folder: Uri? = null,
    val running: Boolean = false,
    /** Die neueste Sicherung im Ordner (nach Dateiname). */
    val newest: LocalDateTime? = null,
    val count: Int = 0,
    val totalBytes: Long = 0,
    val problem: BackupProblem? = null,
)

/**
 * Merkt sich Ordner, Schalter und Ergebnis der automatischen Sicherung dauerhaft – und stellt sie
 * als [info] bereit, damit die Einstellungen sofort sehen, wenn im Hintergrund gesichert wurde.
 */
class BackupSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val _info = MutableStateFlow(load())
    val info: StateFlow<BackupInfo> = _info.asStateFlow()

    /** Stand des Tagebuchs bei der letzten erfolgreichen Sicherung. */
    val lastFingerprint: String? get() = prefs.getString(KEY_FINGERPRINT, null)

    fun setEnabled(enabled: Boolean) = save { putBoolean(KEY_ENABLED, enabled) }

    /** Neuer Sicherungsordner: altes Ergebnis gilt nicht mehr, der nächste Lauf sichert sofort. */
    fun setFolder(folder: Uri) = save {
        putString(KEY_FOLDER, folder.toString())
        remove(KEY_FINGERPRINT)
        remove(KEY_NEWEST)
        remove(KEY_COUNT)
        remove(KEY_BYTES)
        remove(KEY_PROBLEM)
    }

    fun setRunning(running: Boolean) = _info.update { it.copy(running = running) }

    fun saveResult(summary: BackupFolderSummary, fingerprint: String?) = save {
        fingerprint?.let { putString(KEY_FINGERPRINT, it) }
        putString(KEY_NEWEST, summary.newest?.toString())
        putInt(KEY_COUNT, summary.count)
        putLong(KEY_BYTES, summary.totalBytes)
        remove(KEY_PROBLEM)
    }

    fun saveProblem(problem: BackupProblem) = save { putString(KEY_PROBLEM, problem.name) }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _info.update { load().copy(running = it.running) }
    }

    private fun load() = BackupInfo(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        folder = prefs.getString(KEY_FOLDER, null)?.toUri(),
        newest = prefs.getString(KEY_NEWEST, null)?.let(LocalDateTime::parse),
        count = prefs.getInt(KEY_COUNT, 0),
        totalBytes = prefs.getLong(KEY_BYTES, 0),
        problem = prefs.getString(KEY_PROBLEM, null)?.let { name -> BackupProblem.entries.firstOrNull { it.name == name } },
    )

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_FOLDER = "folder"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_NEWEST = "newest"
        private const val KEY_COUNT = "count"
        private const val KEY_BYTES = "bytes"
        private const val KEY_PROBLEM = "problem"

        @Volatile
        private var instance: BackupSettings? = null

        /** Eine gemeinsame Instanz für App und Hintergrund-Sicherung (gleicher Prozess). */
        fun get(context: Context): BackupSettings =
            instance ?: synchronized(this) { instance ?: BackupSettings(context.applicationContext).also { instance = it } }

        /**
         * `true`, wenn der Ordner [folder] der Ordner [journal] selbst ist oder darin liegt.
         * Dort darf keine Sicherung hin: Sie läge im eigenen Tagebuch.
         */
        fun isSameOrInside(folder: Uri, journal: Uri): Boolean {
            if (folder.authority != journal.authority) return false
            val folderId = treeId(folder) ?: return false
            val journalId = treeId(journal) ?: return false
            return folderId == journalId || folderId.startsWith("$journalId/")
        }

        private fun treeId(uri: Uri): String? = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
