package de.riemer87.relic.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File

/**
 * Merkt sich, welcher Ordner für das Tagebuch freigegeben wurde,
 * und prüft, ob die App noch darauf zugreifen darf.
 */
class JournalLocation(context: Context) {

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("journal_location", Context.MODE_PRIVATE)

    /** Der gespeicherte Ordner, aber nur, wenn die Freigabe noch besteht und der Ordner lesbar ist. */
    fun accessibleUri(): Uri? {
        val uri = prefs.getString(KEY_TREE_URI, null)?.toUri() ?: return null
        val granted = resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return uri.takeIf { granted && SafDocumentTree(resolver, it).isAccessible() }
    }

    fun save(uri: Uri) = prefs.edit { putString(KEY_TREE_URI, uri.toString()) }

    /** Sichert die Freigabe dauerhaft, damit sie auch nach einem Neustart des Handys gilt. */
    fun takePermission(uri: Uri) = resolver.takePersistableUriPermission(uri, ACCESS_FLAGS)

    fun releasePermission(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, ACCESS_FLAGS)
        } catch (e: SecurityException) {
            // Gab es nicht (mehr) – nichts zu tun.
        }
    }

    companion object {
        private const val KEY_TREE_URI = "tree_uri"
        private const val ACCESS_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /**
         * Versucht, Dokumente/Relic anzulegen. Klappt nicht auf jedem Gerät
         * (z. B. Android 10 oder ältere Geräte ohne Speicher-Berechtigung).
         */
        @Suppress("DEPRECATION") // Nur der Pfad wird gebraucht; geschrieben wird später über SAF.
        fun createRecommendedFolder(): Boolean = try {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Relic")
            folder.isDirectory || folder.mkdirs()
        } catch (e: SecurityException) {
            false
        }

        /** Startpunkt für die Ordnerauswahl: Dokumente/Relic, falls angelegt, sonst Dokumente. */
        fun recommendedInitialUri(folderCreated: Boolean): Uri =
            DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                if (folderCreated) "primary:Documents/Relic" else "primary:Documents",
            )
    }
}
