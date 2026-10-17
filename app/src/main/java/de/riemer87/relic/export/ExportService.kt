package de.riemer87.relic.export

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.riemer87.relic.MainActivity
import de.riemer87.relic.R
import de.riemer87.relic.reminder.ReminderReceiver
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

/**
 * Exportiert das Tagebuch als Vordergrunddienst: Er läuft mit sichtbarer Benachrichtigung weiter,
 * auch wenn die Nutzerin die App verlässt, sie wegwischt oder die App-Sperre greift.
 */
class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var export: Job? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi") // ServiceCompat beachtet die Dienstart erst ab Android 10 und übergeht sie davor.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.data
        if (export?.isActive == true) return START_NOT_STICKY // läuft schon – nie zwei gleichzeitig
        if (target == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        createChannel(this)
        ServiceCompat.startForeground(
            this, PROGRESS_ID, progressNotification(ExportState.Preparing),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        export = scope.launch {
            val running = coroutineContext.job
            val result = exportTo(target) { !running.isActive }
            ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            showResult(result)
            stopSelf()
        }
        return START_NOT_STICKY // nach einem harten Ende nicht neu starten; der nächste App-Start räumt auf
    }

    private fun exportTo(target: Uri, isCancelled: () -> Boolean): ExportState {
        val record = ExportRecord(this)
        val name = displayName(target)
        try {
            // Damit die halbe Datei auch nach einem Neustart noch gelöscht werden darf.
            contentResolver.takePersistableUriPermission(target, ACCESS_FLAGS)
        } catch (e: SecurityException) {
            Log.w(TAG, "Keine dauerhafte Freigabe für das Ziel", e)
        }
        record.begin(target, name)

        val journal = JournalLocation(this).accessibleUri()
        val outcome = if (journal == null) {
            ExportState.Failed(delete(target), name)
        } else {
            ExportJob(
                tree = SafDocumentTree(contentResolver, journal),
                openOutput = { contentResolver.openOutputStream(target, "wt") ?: throw IOException("Ziel nicht beschreibbar") },
                openInput = { contentResolver.openInputStream(target) ?: throw IOException("Ziel nicht lesbar") },
                deleteTarget = { delete(target) },
                isCancelled = isCancelled,
                report = ::publish,
            ).run(name)
        }
        // Erfolg mit Zeitpunkt dauerhaft merken – auch falls Android die App später im Hintergrund beendet.
        val result = if (outcome is ExportState.Done) outcome.copy(finishedAt = Instant.now()) else outcome
        if (result is ExportState.Done) record.saveSuccess(result)
        Log.i(TAG, "Export beendet: $result")
        record.clear()
        try {
            contentResolver.releasePersistableUriPermission(target, ACCESS_FLAGS)
        } catch (e: SecurityException) {
            // Gab es nicht.
        }
        ExportStatus.update(result)
        return result
    }

    private fun publish(state: ExportState) {
        ExportStatus.update(state)
        // Benachrichtigung höchstens alle halbe Sekunde erneuern, sonst bremst Android sie aus.
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotified < 500) return
        lastNotified = now
        notify(PROGRESS_ID, progressNotification(state))
    }

    private fun progressNotification(state: ExportState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.export_notification_title))
        .setContentText(progressText(this, state))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .apply {
            when (state) {
                is ExportState.Writing -> setProgress(state.total, state.done, false)
                is ExportState.Verifying -> setProgress(state.total, state.done, false)
                else -> setProgress(0, 0, true)
            }
        }
        .build()

    /** Ergebnis als Benachrichtigung – wichtig, falls die Nutzerin gerade nicht in der App ist. */
    private fun showResult(result: ExportState) {
        val text = progressText(this, result)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (result is ExportState.Done) R.string.export_done_title else R.string.export_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(RESULT_ID, notification)
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        if (!ReminderReceiver.canPostNotifications(this)) return
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            // Erlaubnis gerade entzogen – der Export läuft trotzdem.
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, PROGRESS_ID,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun displayName(target: Uri): String = try {
        contentResolver.query(target, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: target.lastPathSegment.orEmpty()

    private fun delete(target: Uri): Boolean = deleteDocument(this, target)

    /** Android 15+: Datensync-Dienste dürfen höchstens 6 Stunden laufen. Dann sauber abbrechen. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        export?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RelicExport"
        private const val CHANNEL_ID = "export"
        private const val PROGRESS_ID = 31
        private const val RESULT_ID = 32
        private const val ACCESS_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Startet den Export in die gerade im „Speichern unter“-Dialog angelegte Datei. */
        fun start(context: Context, target: Uri) {
            if (ExportStatus.state.value.isRunning) return
            ExportStatus.update(ExportState.Preparing) // Knopf sofort sperren, kein doppelter Start
            val intent = Intent(context, ExportService::class.java).setData(target).addFlags(ACCESS_FLAGS)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Beim App-Start: Reste eines unterbrochenen Exports löschen. */
        fun recoverInterrupted(context: Context): InterruptedExport? =
            recoverInterruptedExport(ExportRecord(context), ExportStatus.state.value.isRunning) { target ->
                deleteDocument(context, target).also {
                    try {
                        context.contentResolver.releasePersistableUriPermission(target, ACCESS_FLAGS)
                    } catch (e: SecurityException) {
                        // Gab es nicht.
                    }
                }
            }

        private fun deleteDocument(context: Context, target: Uri): Boolean = try {
            DocumentsContract.deleteDocument(context.contentResolver, target)
        } catch (e: Exception) {
            Log.w(TAG, "Unvollständige Exportdatei nicht löschbar", e)
            false
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.export_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.export_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Der Stand in Worten – für Benachrichtigung und Einstellungen. */
        fun progressText(context: Context, state: ExportState): String = when (state) {
            ExportState.Idle, ExportState.Preparing -> context.getString(R.string.export_preparing)
            is ExportState.Writing -> context.resources.getQuantityString(R.plurals.export_writing, state.total, state.done, state.total)
            is ExportState.Verifying -> context.getString(R.string.export_verifying, state.done, state.total)
            is ExportState.Done -> buildString {
                append(context.resources.getQuantityString(R.plurals.export_done, state.files, state.files))
                if (state.skipped > 0) {
                    append("\n")
                    append(context.resources.getQuantityString(R.plurals.export_skipped, state.skipped, state.skipped))
                }
            }
            is ExportState.Failed -> if (state.targetDeleted) {
                context.getString(R.string.export_failed)
            } else {
                context.getString(R.string.export_failed_not_deleted, state.targetName)
            }
        }
    }
}
