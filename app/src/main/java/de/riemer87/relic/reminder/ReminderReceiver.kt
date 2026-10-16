package de.riemer87.relic.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.riemer87.relic.MainActivity
import de.riemer87.relic.R
import de.riemer87.relic.journal.TreeJournalStore
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock

/**
 * Empfängt den Wecker der Erinnerung und Systemereignisse (Neustart, App-Update,
 * Zeit- oder Zeitzonenwechsel), nach denen die Erinnerung neu geplant werden muss.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val config = ReminderSettings(app).load()
        Log.i(TAG, "Empfangen: ${intent.action}, Erinnerung ${if (config.enabled) "an (${config.time})" else "aus"}")
        if (intent.action != ACTION_REMIND) {
            // Systemereignis: siehe ReminderSchedule.reactionTo – einen offenen Wecker nie verschieben.
            val scheduler = ReminderScheduler(app)
            when (ReminderSchedule.reactionTo(intent.action)) {
                ReminderSchedule.Reaction.KEEP_IF_PLANNED -> scheduler.ensureScheduled(config)
                ReminderSchedule.Reaction.RECOMPUTE -> scheduler.apply(config)
            }
            return
        }
        // Prüfen, ob heute schon geschrieben wurde, braucht einen Dateizugriff – deshalb im Hintergrund.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val written = hasEntryToday(app)
                when {
                    !config.enabled -> Log.i(TAG, "Übersprungen: Erinnerung ist aus")
                    !ReminderSchedule.shouldNotify(written) -> Log.i(TAG, "Übersprungen: heute schon geschrieben")
                    !canPostNotifications(app) -> Log.w(TAG, "Nicht angezeigt: keine Erlaubnis für Benachrichtigungen")
                    else -> showNotification(app).also { Log.i(TAG, "Angezeigt") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Erinnerung", e)
            } finally {
                ReminderScheduler(app).apply(config) // die nächste Erinnerung planen
                pending.finish()
            }
        }
    }

    private fun hasEntryToday(context: Context): Boolean = try {
        val uri = JournalLocation(context).accessibleUri() ?: return false
        TreeJournalStore(SafDocumentTree(context.contentResolver, uri)).load(localToday(Clock.systemDefaultZone())) != null
    } catch (e: IOException) {
        false // Im Zweifel lieber erinnern.
    }

    companion object {
        private const val TAG = "RelicReminder"
        const val ACTION_REMIND = "de.riemer87.relic.action.REMIND"
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 21

        /** Extra für [MainActivity]: direkt die Eintragsseite von heute öffnen. */
        const val EXTRA_OPEN_TODAY = "de.riemer87.relic.extra.OPEN_TODAY"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun canPostNotifications(context: Context): Boolean {
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun showNotification(context: Context) {
            if (!canPostNotifications(context)) return
            createChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TODAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val contentIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Erlaubnis wurde gerade entzogen – dann eben nicht.
            }
        }
    }
}
