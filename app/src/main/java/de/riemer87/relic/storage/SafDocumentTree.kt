package de.riemer87.relic.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import de.riemer87.relic.journal.DocumentTree
import de.riemer87.relic.journal.FileInfo
import de.riemer87.relic.journal.splitPath
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SyncFailedException

/**
 * [DocumentTree] über einen Ordner, der per Storage Access Framework (SAF)
 * freigegeben hat. Alle Fehler von Android (z. B. entzogene Freigabe) kommen als [IOException] an.
 */
class SafDocumentTree(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : DocumentTree {

    private class Doc(val id: String, val name: String, val isDir: Boolean, val size: Long = 0, val lastModified: Long = 0)

    private val root: Doc by lazy { io { Doc(DocumentsContract.getTreeDocumentId(treeUri), "", isDir = true) } }

    /** `true`, wenn der Ordner existiert und gelesen werden kann. */
    fun isAccessible(): Boolean = try {
        io { children(root.id) }
        true
    } catch (e: IOException) {
        false
    }

    override fun list(dir: String): List<String>? = io {
        val doc = find(dir)?.takeIf { it.isDir } ?: return@io null
        children(doc.id).map { it.name }
    }

    override fun listInfo(dir: String): List<FileInfo>? = io {
        val doc = find(dir)?.takeIf { it.isDir } ?: return@io null
        children(doc.id).map { FileInfo(it.name, it.isDir, it.size, it.lastModified) }
    }

    override fun exists(path: String): Boolean = io { find(path) != null }

    override fun readText(path: String): String? = io {
        val doc = find(path)?.takeIf { !it.isDir } ?: return@io null
        val input = resolver.openInputStream(uriOf(doc)) ?: throw IOException("Konnte $path nicht öffnen")
        input.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun openRead(path: String): InputStream? = io {
        val doc = find(path)?.takeIf { !it.isDir } ?: return@io null
        resolver.openInputStream(uriOf(doc))
    }

    override fun writeBytes(path: String, content: ByteArray) = io {
        val target = createOrFind(path)

        // "wt" = schreiben und vorhandenen Inhalt vorher abschneiden.
        val descriptor = resolver.openFileDescriptor(target, "wt") ?: throw IOException("Konnte $path nicht öffnen")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { out ->
            out.write(content)
            out.flush()
            try {
                out.fd.sync() // Wirklich auf den Speicher schreiben, bevor es weitergeht.
            } catch (e: SyncFailedException) {
                // Manche Anbieter (z. B. Cloud-Ordner) können das nicht; dann reicht flush.
            }
        }
    }

    override fun openWrite(path: String): OutputStream = io {
        resolver.openOutputStream(createOrFind(path), "wt") ?: throw IOException("Konnte $path nicht öffnen")
    }

    /** Die Datei unter [path]; fehlt sie, wird sie samt Ordnern angelegt. */
    private fun createOrFind(path: String): Uri {
        val (dirPath, name) = splitPath(path)
        val parent = ensureDir(dirPath)
        val existing = children(parent.id).firstOrNull { it.name == name }
        return existing?.let(::uriOf) ?: create(parent, name, FILE_MIME_TYPE)
    }

    override fun rename(path: String, newName: String) = io {
        val doc = find(path) ?: throw FileNotFoundException(path)
        // Rückgabe ist auch bei Erfolg manchmal null, daher danach selbst nachsehen.
        DocumentsContract.renameDocument(resolver, uriOf(doc), newName)
        val dirPath = splitPath(path).first
        val renamedPath = if (dirPath.isEmpty()) newName else "$dirPath/$newName"
        if (find(renamedPath) == null) throw IOException("Konnte $path nicht in $newName umbenennen")
    }

    override fun delete(path: String) = io {
        val doc = find(path) ?: return@io
        if (!DocumentsContract.deleteDocument(resolver, uriOf(doc))) throw IOException("Konnte $path nicht löschen")
    }

    private fun uriOf(doc: Doc): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.id)

    private fun children(parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = resolver.query(uri, projection, null, null, null) ?: throw IOException("Ordner nicht lesbar")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Doc(
                            id = it.getString(0),
                            name = it.getString(1),
                            isDir = it.getString(2) == Document.MIME_TYPE_DIR,
                            // Nicht jeder Anbieter kennt Größe und Änderungszeit; dann 0.
                            size = if (it.isNull(3)) 0 else it.getLong(3),
                            lastModified = if (it.isNull(4)) 0 else it.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    private fun find(path: String): Doc? {
        var current = root
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            if (!current.isDir) return null
            current = children(current.id).firstOrNull { it.name == segment } ?: return null
        }
        return current
    }

    private fun ensureDir(dirPath: String): Doc {
        var current = root
        for (segment in dirPath.split('/').filter { it.isNotEmpty() }) {
            val existing = children(current.id).firstOrNull { it.name == segment }
            current = when {
                existing == null -> Doc(DocumentsContract.getDocumentId(create(current, segment, Document.MIME_TYPE_DIR)), segment, isDir = true)
                existing.isDir -> existing
                else -> throw IOException("$segment ist eine Datei, kein Ordner")
            }
        }
        return current
    }

    private fun create(parent: Doc, name: String, mimeType: String): Uri {
        val uri = DocumentsContract.createDocument(resolver, uriOf(parent), mimeType, name)
            ?: throw IOException("Konnte $name nicht anlegen")
        // Gibt es den Namen schon, legen manche Anbieter still „Name (1)“ an. Das darf nicht passieren.
        val actualName = resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (actualName != name) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw IOException("Statt $name wurde $actualName angelegt")
        }
        return uri
    }

    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e
    } catch (e: SecurityException) {
        throw IOException("Kein Zugriff auf den Ordner", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("Ungültiger Ordner", e)
    } catch (e: IllegalStateException) {
        throw IOException("Ordner nicht verfügbar", e)
    } catch (e: UnsupportedOperationException) {
        throw IOException("Ordner unterstützt das nicht", e)
    }

    private companion object {
        /**
         * Neutraler Dateityp: Damit hängt Android keine eigene Endung an
         * (bei „text/markdown“ könnte aus „x.md“ sonst z. B. „x.md.txt“ werden).
         */
        const val FILE_MIME_TYPE = "application/octet-stream"
    }
}
