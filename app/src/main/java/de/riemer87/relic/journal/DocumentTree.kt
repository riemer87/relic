package de.riemer87.relic.journal

import java.io.IOException
import java.io.InputStream

/**
 * Ein Ordner mit Unterordnern und Dateien, angesprochen über Pfade relativ zur Wurzel
 * (z. B. `2026/10/2026-10-09.md`; `""` ist die Wurzel selbst).
 *
 * Auf dem Gerät steckt dahinter der selbst gewählte Journal-Ordner (SAF), in Tests ein
 * normaler Ordner auf dem PC. So lässt sich die Speicherlogik ohne Handy prüfen.
 */
interface DocumentTree {
    /** Namen aller Einträge im Ordner, oder `null`, wenn es den Ordner nicht gibt. */
    @Throws(IOException::class)
    fun list(dir: String): List<String>?

    @Throws(IOException::class)
    fun exists(path: String): Boolean

    /** Inhalt einer Datei als UTF-8-Text, oder `null`, wenn es sie nicht gibt. */
    @Throws(IOException::class)
    fun readText(path: String): String?

    /** Schreibt eine Datei vollständig neu. Fehlende Ordner werden angelegt. */
    @Throws(IOException::class)
    fun writeText(path: String, content: String) = writeBytes(path, content.toByteArray(Charsets.UTF_8))

    /** Wie [writeText], für Binärdateien (Fotos). */
    @Throws(IOException::class)
    fun writeBytes(path: String, content: ByteArray)

    /** Öffnet eine Datei zum Lesen, oder `null`, wenn es sie nicht gibt. Aufrufer muss schließen. */
    @Throws(IOException::class)
    fun openRead(path: String): InputStream?

    /** Benennt eine Datei im selben Ordner um. Unter dem neuen Namen darf noch nichts liegen. */
    @Throws(IOException::class)
    fun rename(path: String, newName: String)

    /** Löscht eine Datei. Gibt es sie nicht, passiert nichts. */
    @Throws(IOException::class)
    fun delete(path: String)
}

/** Zerlegt `a/b/c.md` in Ordner `a/b` und Name `c.md`. */
internal fun splitPath(path: String): Pair<String, String> {
    val slash = path.lastIndexOf('/')
    return if (slash < 0) "" to path else path.substring(0, slash) to path.substring(slash + 1)
}
