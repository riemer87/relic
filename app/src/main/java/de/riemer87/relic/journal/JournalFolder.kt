package de.riemer87.relic.journal

import java.io.IOException

/** Wie beim Einrichten der Ordner ausgewählt wird. */
enum class FolderOption {
    /** „Empfohlenen Ordner verwenden“ (Dokumente/Relic) */
    RECOMMENDED,

    /** „Eigenen Ordner wählen“ */
    OWN,

    /** „Ich habe schon ein Relic-Tagebuch“ */
    EXISTING,
}

/** Was nach der Ordnerauswahl passiert. */
enum class FolderDecision {
    /** Ordner ohne Rückfrage verwenden. */
    USE,

    /** Neuer Ordner gewünscht, dort liegt aber schon ein Tagebuch: fragen, ob es verbunden werden soll. */
    ASK_CONNECT_EXISTING,

    /** Vorhandenes Tagebuch gewünscht, aber keins gefunden: fragen, ob der Ordner trotzdem verwendet werden soll. */
    ASK_USE_WITHOUT_JOURNAL,
}

object JournalFolder {

    /** Mögliche Namen der Erklär-Datei im Journal-Ordner (Deutsch, Englisch). */
    val README_NAMES = listOf("LIES MICH.txt", "README.txt")

    private val YEAR = Regex("""\d{4}""")
    private val MONTH = Regex("""\d{2}""")
    private val ENTRY = Regex("""\d{4}-\d{2}-\d{2}\.md""")

    fun decide(option: FolderOption, looksLikeJournal: Boolean): FolderDecision = when {
        option == FolderOption.EXISTING && !looksLikeJournal -> FolderDecision.ASK_USE_WITHOUT_JOURNAL
        option != FolderOption.EXISTING && looksLikeJournal -> FolderDecision.ASK_CONNECT_EXISTING
        else -> FolderDecision.USE
    }

    /**
     * Ein Ordner gilt als Relic-Tagebuch, wenn er eine Erklär-Datei oder mindestens eine
     * Eintragsdatei an der richtigen Stelle (`JJJJ/MM/JJJJ-MM-TT.md`) enthält.
     */
    @Throws(IOException::class)
    fun looksLikeJournal(tree: DocumentTree): Boolean {
        val root = tree.list("") ?: return false
        if (root.any { it in README_NAMES }) return true
        return hasEntries(tree)
    }

    /** Gibt es mindestens eine Eintragsdatei an der richtigen Stelle (`JJJJ/MM/JJJJ-MM-TT.md`)? */
    @Throws(IOException::class)
    fun hasEntries(tree: DocumentTree): Boolean {
        val root = tree.list("") ?: return false
        return root.filter { YEAR.matches(it) }.any { year ->
            tree.list(year).orEmpty().filter { MONTH.matches(it) }.any { month ->
                tree.list("$year/$month").orEmpty().any { ENTRY.matches(it) && it.startsWith("$year-$month-") }
            }
        }
    }

    /** Legt die Erklär-Datei an, falls es noch keine gibt. Eine vorhandene wird nie überschrieben. */
    @Throws(IOException::class)
    fun ensureReadme(tree: DocumentTree, fileName: String, text: String) {
        val root = tree.list("").orEmpty()
        if (root.any { it in README_NAMES }) return
        tree.writeText(fileName, text)
    }
}
