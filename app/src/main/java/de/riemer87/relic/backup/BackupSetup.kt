package de.riemer87.relic.backup

import android.content.Context
import android.net.Uri

/** Ergebnis der Wahl eines Sicherungsordners. */
enum class BackupFolderChoice { OK, INSIDE_JOURNAL, NO_ACCESS }

/** Automatische Sicherung einrichten – gemeinsam für Einstellungen und Einführung. */
object BackupSetup {

    /** Ein- oder ausschalten. Beim Einschalten gleich eine erste Sicherung anstoßen (falls es schon Einträge gibt). */
    fun setEnabled(context: Context, enabled: Boolean) {
        BackupSettings.get(context).setEnabled(enabled)
        BackupWorker.schedule(context, enabled)
        if (enabled) BackupWorker.runNow(context, force = false)
    }

    /**
     * Neuer Sicherungsordner aus der Ordnerauswahl: prüfen, dauerhaften Zugriff sichern, die Freigabe
     * des alten Ordners zurückgeben und die Sicherung einschalten.
     * @param journal der Tagebuch-Ordner; dort (oder darin) darf keine Sicherung hin
     */
    fun chooseFolder(context: Context, folder: Uri, journal: Uri?): BackupFolderChoice {
        if (journal != null && BackupSettings.isSameOrInside(folder, journal)) return BackupFolderChoice.INSIDE_JOURNAL
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(folder, BackupWorker.ACCESS_FLAGS)
        } catch (e: SecurityException) {
            return BackupFolderChoice.NO_ACCESS
        }
        val settings = BackupSettings.get(context)
        val old = settings.info.value.folder
        settings.setFolder(folder)
        if (old != null && old != folder && old != journal) {
            try {
                resolver.releasePersistableUriPermission(old, BackupWorker.ACCESS_FLAGS)
            } catch (e: SecurityException) {
                // Gab es nicht (mehr).
            }
        }
        setEnabled(context, true)
        return BackupFolderChoice.OK
    }
}
