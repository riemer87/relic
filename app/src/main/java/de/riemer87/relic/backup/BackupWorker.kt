package de.riemer87.relic.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.riemer87.relic.R
import de.riemer87.relic.export.ExportService
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Führt eine automatische Sicherung im Hintergrund aus. Android (WorkManager) startet sie einmal
 * pro Woche – auch wenn Relic geschlossen ist oder das Handy neu gestartet wurde – und auf Wunsch
 * sofort („Jetzt sichern“).
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** Nur vor Android 12 nötig: Dort läuft eine Sicherung mit Vorrang sichtbar als Vordergrunddienst. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ExportService.createChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, ExportService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.backup_notification))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = BackupSettings.get(applicationContext)
        if (!settings.info.value.enabled) return@withContext Result.success()
        // Nie zwei Sicherungen gleichzeitig (z. B. „Jetzt sichern“ während des Wochenlaufs).
        RUNNING.withLock {
            settings.setRunning(true)
            try {
                backUp(settings, force = inputData.getBoolean(KEY_FORCE, false))
            } finally {
                settings.setRunning(false)
            }
        }
    }

    private fun backUp(settings: BackupSettings, force: Boolean): Result {
        val resolver = applicationContext.contentResolver
        val folder = settings.info.value.folder
        if (folder == null || !hasAccess(folder) || !SafDocumentTree(resolver, folder).isAccessible()) {
            return problem(settings, BackupProblem.FOLDER_MISSING)
        }
        val journal = JournalLocation(applicationContext).accessibleUri()
            ?: return problem(settings, BackupProblem.JOURNAL_MISSING)
        if (BackupSettings.isSameOrInside(folder, journal)) return problem(settings, BackupProblem.FOLDER_IN_JOURNAL)

        val result = BackupJob(
            journal = SafDocumentTree(resolver, journal),
            target = SafDocumentTree(resolver, folder),
            now = LocalDateTime::now,
            isCancelled = { isStopped },
        ).run(settings.lastFingerprint, force)
        Log.i(TAG, "Sicherung: $result")
        return when (result) {
            is BackupResult.Done -> {
                settings.saveResult(result.summary, result.fingerprint)
                Result.success()
            }
            is BackupResult.Unchanged -> {
                settings.saveResult(result.summary, fingerprint = null)
                Result.success()
            }
            // Abgebrochen oder fehlgeschlagen: still später erneut versuchen, beim dritten Mal Bescheid geben.
            BackupResult.Failed -> if (isStopped || runAttemptCount < 2) {
                Result.retry()
            } else {
                problem(settings, BackupProblem.FAILED)
            }
        }
    }

    private fun hasAccess(folder: Uri) = applicationContext.contentResolver.persistedUriPermissions.any {
        it.uri == folder && it.isReadPermission && it.isWritePermission
    }

    private fun problem(settings: BackupSettings, problem: BackupProblem): Result {
        Log.w(TAG, "Sicherung nicht möglich: $problem")
        settings.saveProblem(problem)
        return Result.success() // Kein endloses Wiederholen; der Hinweis steht in den Einstellungen.
    }

    companion object {
        private const val TAG = "RelicBackup"
        private const val KEY_FORCE = "force"
        private const val WEEKLY = "backup-weekly"
        private const val NOW = "backup-now"
        private const val NOTIFICATION_ID = 33
        private val RUNNING = Mutex()

        const val ACCESS_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Wöchentliche Sicherung ein- oder ausplanen. Vorhandene Sicherungen bleiben immer liegen. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WEEKLY)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .build()
            work.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** `true`, solange eine angestoßene Sicherung darauf wartet, dass Android sie startet. */
        fun waiting(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NOW)
                .map { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } }

        /** Gleich sichern. [force]: auch wenn sich seit der letzten Sicherung nichts geändert hat. */
        fun runNow(context: Context, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                // Gleich starten statt irgendwann; ist das Kontingent von Android aufgebraucht, eben normal.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
