package de.riemer87.relic.backup

import de.riemer87.relic.export.ExportJob
import de.riemer87.relic.export.ExportState
import de.riemer87.relic.journal.DocumentTree
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/** Was im Sicherungsordner liegt: wie viele Sicherungen, zusammen wie groß, die neueste wann. */
data class BackupFolderSummary(val count: Int, val totalBytes: Long, val newest: LocalDateTime?)

sealed interface BackupResult {
    /** Seit der letzten Sicherung hat sich nichts geändert – keine neue Datei. */
    data class Unchanged(val summary: BackupFolderSummary) : BackupResult

    /**
     * Neue Sicherung geschrieben, geprüft und alte nach der Staffel aufgeräumt.
     * @param fingerprint Stand des Tagebuchs, den diese Sicherung enthält
     */
    data class Done(val name: String, val files: Int, val fingerprint: String, val summary: BackupFolderSummary) : BackupResult

    data object Failed : BackupResult
}

/**
 * Eine automatische Sicherung: das Tagebuch als geprüftes ZIP in den Sicherungsordner, danach
 * alte Sicherungen nach [BackupRetention] aufräumen. Liest im Tagebuch nur.
 *
 * Eine Sicherung entsteht als `….zip.part` und bekommt ihren richtigen Namen erst, wenn sie
 * vollständig geprüft ist. So wird eine abgebrochene Sicherung nie für eine gute gehalten.
 *
 * @param lastFingerprint Stand der letzten erfolgreichen Sicherung (`null`: noch keine)
 * @param force auch sichern, wenn sich nichts geändert hat („Jetzt sichern“)
 */
class BackupJob(
    private val journal: DocumentTree,
    private val target: DocumentTree,
    private val now: () -> LocalDateTime,
    private val isCancelled: () -> Boolean = { false },
) {

    fun run(lastFingerprint: String?, force: Boolean): BackupResult = try {
        removeLeftovers()
        val fingerprint = JournalFingerprint.of(journal)
        val before = summary()
        val at = now().withSecond(0).withNano(0)
        val name = BackupNames.name(at)
        when {
            // Nichts Neues und schon eine Sicherung da: keine fünf gleichen Dateien anlegen.
            !force && fingerprint == lastFingerprint && before.count > 0 -> BackupResult.Unchanged(before)
            // In derselben Minute schon gesichert (zweimal „Jetzt sichern“): die liegt ja schon da.
            target.exists(name) -> BackupResult.Unchanged(before)
            else -> write(at, name, fingerprint)
        }
    } catch (e: IOException) {
        BackupResult.Failed
    }

    private fun write(at: LocalDateTime, name: String, fingerprint: String): BackupResult {
        val part = BackupNames.partName(at)
        val result = ExportJob(
            tree = journal,
            openOutput = { target.openWrite(part) },
            openInput = { target.openRead(part) ?: throw IOException("Sicherung nicht lesbar") },
            deleteTarget = {
                target.delete(part)
                true
            },
            isCancelled = isCancelled,
        ).run(part)
        if (result !is ExportState.Done) return BackupResult.Failed

        try {
            target.rename(part, name)
        } catch (e: IOException) {
            deleteQuietly(part)
            return BackupResult.Failed
        }
        prune(at.toLocalDate())
        return BackupResult.Done(name, result.files, fingerprint, summary())
    }

    /** Alte Sicherungen nach der Staffel löschen. Klappt das einmal nicht, beim nächsten Mal. */
    private fun prune(today: LocalDate) {
        val backups = target.list("").orEmpty().mapNotNull { name -> BackupNames.parse(name)?.let { it to name } }.toMap()
        val keep = BackupRetention.keep(backups.keys, today)
        backups.filterKeys { it !in keep }.values.forEach(::deleteQuietly)
    }

    /** Halbe Sicherungen eines früheren, abgebrochenen Laufs entfernen. */
    private fun removeLeftovers() {
        target.list("")?.filter(BackupNames::isLeftover)?.forEach(::deleteQuietly)
            ?: throw IOException("Sicherungsordner nicht lesbar")
    }

    private fun summary(): BackupFolderSummary {
        val backups = target.listInfo("").orEmpty().filter { !it.isDir && BackupNames.parse(it.name) != null }
        return BackupFolderSummary(
            count = backups.size,
            totalBytes = backups.sumOf { it.size },
            newest = backups.mapNotNull { BackupNames.parse(it.name) }.maxOrNull(),
        )
    }

    private fun deleteQuietly(name: String) {
        try {
            target.delete(name)
        } catch (e: IOException) {
            // Liegt eben noch da; beim nächsten Lauf neuer Versuch.
        }
    }
}
