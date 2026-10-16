package de.riemer87.relic.journal

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Wandelt einen [Entry] in den Inhalt einer `.md`-Datei um und zurück.
 *
 * Aufbau einer Datei:
 * ```
 * ---
 * date: 2026-10-09
 * created: 2026-10-09T21:14:03+02:00
 * updated: 2026-10-09T21:20:47+02:00
 * ---
 *
 * Text der Nutzerin
 *
 * <!-- relic:photos -->
 * ![](2026-10-09_1.jpg)
 * ```
 *
 * Das Lesen ist bewusst nachsichtig: Dateien können von Hand oder mit anderen
 * Programmen bearbeitet worden sein. Was nicht ins Schema passt, landet im Text,
 * statt verloren zu gehen.
 */
object EntryFormat {

    const val PHOTOS_MARKER = "<!-- relic:photos -->"

    private const val FENCE = "---"

    /** Unsichtbares Zeichen, das manche Windows-Programme an den Dateianfang setzen. */
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    /** Zeitstempel immer mit Sekunden und Zeitzonen-Versatz, z. B. `2026-10-09T21:14:03+02:00`. */
    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    private val PHOTO_LINE = Regex("""!\[[^\]]*]\(([^)]+)\)""")

    fun formatTimestamp(time: OffsetDateTime): String = TIMESTAMP.format(time)

    /** Bringt Text in die gespeicherte Form: Windows-Zeilenumbrüche vereinheitlicht, Rand-Leerraum entfernt. */
    fun normalizeText(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').trim()

    fun serialize(entry: Entry): String = buildString {
        append(FENCE).append('\n')
        append("date: ").append(entry.date).append('\n')
        entry.created?.let { append("created: ").append(formatTimestamp(it)).append('\n') }
        entry.updated?.let { append("updated: ").append(formatTimestamp(it)).append('\n') }
        entry.extraFrontmatter.forEach { append(it).append('\n') }
        append(FENCE).append('\n')

        val text = normalizeText(entry.text)
        if (text.isNotEmpty()) {
            append('\n').append(text).append('\n')
        }
        if (entry.photos.isNotEmpty()) {
            append('\n').append(PHOTOS_MARKER).append('\n')
            entry.photos.forEach { append("![](").append(it).append(")\n") }
        }
    }

    /**
     * Liest den Inhalt einer Eintragsdatei. Stürzt nie ab: Fehlt das Frontmatter oder ist es
     * kaputt, wird der gesamte Inhalt als Text behandelt.
     *
     * @param date Der Tag laut Dateiname. Er gilt, auch wenn im Frontmatter etwas anderes steht.
     */
    fun parse(content: String, date: LocalDate): Entry {
        val lines = content.removePrefix(BYTE_ORDER_MARK).replace("\r\n", "\n").replace('\r', '\n').split('\n')

        var created: OffsetDateTime? = null
        var updated: OffsetDateTime? = null
        val extra = mutableListOf<String>()
        var bodyLines = lines

        val closingFence = if (lines.firstOrNull()?.trimEnd() == FENCE) {
            (1 until lines.size).firstOrNull { lines[it].trimEnd() == FENCE }
        } else {
            null
        }
        if (closingFence != null) {
            for (line in lines.subList(1, closingFence)) {
                when (line.substringBefore(':').trim()) {
                    "date" -> Unit // Der Dateiname ist maßgeblich.
                    "created" -> created = parseTimestamp(line.substringAfter(':'))
                    "updated" -> updated = parseTimestamp(line.substringAfter(':'))
                    else -> if (line.isNotBlank()) extra += line
                }
            }
            bodyLines = lines.subList(closingFence + 1, lines.size)
        }

        val markerIndex = bodyLines.indexOfLast { it.trim() == PHOTOS_MARKER }
        val textLines = if (markerIndex >= 0) bodyLines.subList(0, markerIndex) else bodyLines
        val photos = if (markerIndex >= 0) {
            bodyLines.subList(markerIndex + 1, bodyLines.size)
                .mapNotNull { PHOTO_LINE.find(it)?.groupValues?.get(1)?.trim() }
        } else {
            emptyList()
        }

        return Entry(
            date = date,
            created = created,
            updated = updated,
            text = normalizeText(textLines.joinToString("\n")),
            photos = photos,
            extraFrontmatter = extra,
        )
    }

    private fun parseTimestamp(value: String): OffsetDateTime? = try {
        OffsetDateTime.parse(value.trim().trim('"', '\''))
    } catch (e: DateTimeParseException) {
        null
    }
}
