package de.riemer87.relic.journal

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Ein Tagebucheintrag, so wie er in einer `.md`-Datei steht.
 *
 * @property date Der Tag, zu dem der Eintrag gehört (lokales Datum).
 * @property created Zeitpunkt der ersten Speicherung, `null` wenn in der Datei nicht lesbar.
 * @property updated Zeitpunkt der letzten Speicherung, `null` wenn in der Datei nicht lesbar.
 * @property text Der Text der Nutzerin (Markdown), ohne Frontmatter und ohne Foto-Block.
 * @property photos Dateinamen der Fotos aus dem Foto-Block, in Reihenfolge.
 * @property extraFrontmatter Frontmatter-Zeilen, die Relic nicht kennt (z. B. von Obsidian).
 *   Sie werden beim Speichern unverändert übernommen, damit nichts verloren geht.
 */
data class Entry(
    val date: LocalDate,
    val created: OffsetDateTime?,
    val updated: OffsetDateTime?,
    val text: String,
    val photos: List<String> = emptyList(),
    val extraFrontmatter: List<String> = emptyList(),
)
