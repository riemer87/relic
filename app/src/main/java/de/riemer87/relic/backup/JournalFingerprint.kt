package de.riemer87.relic.backup

import de.riemer87.relic.journal.DocumentTree
import java.io.IOException
import java.security.MessageDigest

/**
 * Ein kurzer „Fingerabdruck“ des Tagebuchs aus Namen, Größen und Änderungszeiten aller Dateien
 * (Wurzel und `JJJJ/MM/`). Ändert sich irgendeine Datei – auch außerhalb von Relic, z. B. in
 * Obsidian –, ändert sich der Fingerabdruck. Gelesen wird dabei keine Datei, das geht schnell.
 */
object JournalFingerprint {

    private val YEAR = Regex("""\d{4}""")
    private val MONTH = Regex("""\d{2}""")

    @Throws(IOException::class)
    fun of(tree: DocumentTree): String {
        val root = tree.listInfo("") ?: throw IOException("Tagebuch-Ordner nicht lesbar")
        val lines = mutableListOf<String>()
        for (item in root) {
            when {
                !item.isDir -> lines += "${item.name}|${item.size}|${item.lastModified}"
                YEAR.matches(item.name) -> for (month in tree.listInfo(item.name).orEmpty()) {
                    if (!month.isDir || !MONTH.matches(month.name)) continue
                    val dir = "${item.name}/${month.name}"
                    for (file in tree.listInfo(dir).orEmpty()) {
                        if (!file.isDir) lines += "$dir/${file.name}|${file.size}|${file.lastModified}"
                    }
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        lines.sorted().forEach { digest.update((it + "\n").toByteArray(Charsets.UTF_8)) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
