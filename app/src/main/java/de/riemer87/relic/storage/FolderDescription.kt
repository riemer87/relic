package de.riemer87.relic.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Anzeige eines freigegebenen Ordners. Auf dem Gerätespeicher wie [FolderLabel.parse]; bei anderen
 * Anbietern (z. B. Google Drive), deren Kennung unlesbar ist, mit App- und Ordnernamen:
 * „Drive › Relic-Backups“. Fragt dafür den Anbieter – nicht auf dem Hauptthread aufrufen.
 */
fun describeFolder(context: Context, tree: Uri): FolderLabel.Parts? {
    val authority = tree.authority ?: return null
    val documentId = try {
        DocumentsContract.getTreeDocumentId(tree)
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (authority == FolderLabel.EXTERNAL_STORAGE_AUTHORITY) return FolderLabel.parse(authority, documentId)

    val name = try {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        null // Anbieter gerade nicht erreichbar oder Freigabe entzogen.
    }
    val app = try {
        val pm = context.packageManager
        pm.resolveContentProvider(authority, 0)?.loadLabel(pm)?.toString()
    } catch (e: Exception) {
        null
    }
    val parts = listOfNotNull(app, name).filter { it.isNotBlank() }
    return if (parts.isEmpty()) FolderLabel.parse(authority, documentId) else FolderLabel.Parts(FolderLabel.Location.OTHER, parts)
}
