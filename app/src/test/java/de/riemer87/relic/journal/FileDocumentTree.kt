package de.riemer87.relic.journal

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** [DocumentTree] über einen normalen Ordner – nur für Tests auf dem PC. Verhält sich streng wie SAF. */
class FileDocumentTree(private val root: File) : DocumentTree {

    private fun file(path: String) = if (path.isEmpty()) root else File(root, path)

    override fun list(dir: String): List<String>? =
        file(dir).takeIf { it.isDirectory }?.list()?.sorted()

    override fun exists(path: String): Boolean = file(path).exists()

    override fun readText(path: String): String? =
        file(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    override fun writeText(path: String, content: String) {
        val target = file(path)
        val dir = target.parentFile!!
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Konnte $dir nicht anlegen")
        target.writeText(content, Charsets.UTF_8)
    }

    override fun rename(path: String, newName: String) {
        val source = file(path)
        if (!source.isFile) throw FileNotFoundException(path)
        val target = File(source.parentFile, newName)
        // SAF würde hier stillschweigend „Name (1)“ anlegen – das darf nie passieren.
        if (target.exists()) throw IOException("$newName gibt es schon")
        if (!source.renameTo(target)) throw IOException("Konnte $path nicht umbenennen")
    }

    override fun delete(path: String) {
        val target = file(path)
        if (target.exists() && !target.delete()) throw IOException("Konnte $path nicht löschen")
    }
}

/**
 * Wie [FileDocumentTree], stürzt aber gezielt bei einem bestimmten Schritt ab.
 * Bei `writeText` wird vor dem Absturz nur die Hälfte geschrieben – wie bei leerem Akku.
 */
class CrashingDocumentTree(
    private val delegate: DocumentTree,
    private val crashOn: (operation: String, path: String) -> Boolean,
) : DocumentTree by delegate {

    class SimulatedCrash : IOException("Simulierter Absturz")

    override fun writeText(path: String, content: String) {
        if (crashOn("write", path)) {
            delegate.writeText(path, content.take(content.length / 2))
            throw SimulatedCrash()
        }
        delegate.writeText(path, content)
    }

    override fun rename(path: String, newName: String) {
        if (crashOn("rename", path)) throw SimulatedCrash()
        delegate.rename(path, newName)
    }

    override fun delete(path: String) {
        if (crashOn("delete", path)) throw SimulatedCrash()
        delegate.delete(path)
    }
}
