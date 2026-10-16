package de.riemer87.relic.reminder

import android.Manifest
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class ReminderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val settings = ReminderSettings(context)
    private val scheduler = ReminderScheduler(context)
    private lateinit var before: ReminderConfig

    @Before
    fun setUp() {
        before = settings.load()
        // Ohne Erlaubnisdialog: Benachrichtigungen für den Test direkt erlauben.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        notifications.cancelAll()
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        settings.save(before)
        scheduler.apply(before)
    }

    @Test
    fun notificationAsksWhatYouWereGratefulFor() {
        ReminderReceiver.showNotification(context)

        // Android trägt die Benachrichtigung einen Augenblick später ein.
        val deadline = System.currentTimeMillis() + 3_000
        while (notifications.activeNotifications.none { it.id == ReminderReceiver.NOTIFICATION_ID } && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        val shown = notifications.activeNotifications.single { it.id == ReminderReceiver.NOTIFICATION_ID }
        assertEquals(context.getString(R.string.reminder_title), shown.notification.extras.getCharSequence("android.title").toString())
        assertEquals(context.getString(R.string.reminder_text), shown.notification.extras.getCharSequence("android.text").toString())
        assertEquals(ReminderReceiver.CHANNEL_ID, shown.notification.channelId)
        assertTrue("verschwindet nach dem Antippen", shown.notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun turningOnPlansTheReminderAndTurningOffRemovesIt() {
        scheduler.apply(ReminderConfig(enabled = true, time = LocalTime.of(21, 0)))
        assertTrue(scheduler.isScheduled())

        scheduler.apply(ReminderConfig(enabled = false))
        assertFalse(scheduler.isScheduled())
    }

    /**
     * Fehler aus Meilenstein 6: Erinnerung auf 22:32, App um 22:32:50 geöffnet, bevor Android den
     * Wecker (Spielraum bis 22:42) ausgelöst hatte – die App verschob ihn auf morgen.
     */
    @Test
    fun openingTheAppDoesNotPushTodaysPendingReminderToTomorrow() {
        // Zeitpunkt in der echten Zukunft, damit der Wecker während des Tests nicht wirklich klingelt.
        val reminderAt = ZonedDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES)
        val config = ReminderConfig(enabled = true, time = reminderAt.toLocalTime())
        fun clockAt(time: ZonedDateTime) = Clock.fixed(time.toInstant(), time.zone)

        ReminderScheduler(context, clockAt(reminderAt.minusMinutes(1))).apply(config) // eingestellt um 22:31
        assertEquals(reminderAt.toInstant(), scheduler.plannedFor())

        ReminderScheduler(context, clockAt(reminderAt.plusSeconds(50))).ensureScheduled(config) // App-Start 22:32:50
        assertEquals("Der Wecker von heute muss bleiben", reminderAt.toInstant(), scheduler.plannedFor())
    }

    @Test
    fun openingTheAppPlansAReminderIfNoneIsPlanned() {
        scheduler.apply(ReminderConfig(enabled = false))
        assertFalse(scheduler.isScheduled())

        scheduler.ensureScheduled(ReminderConfig(enabled = true, time = LocalTime.of(21, 0)))
        assertTrue(scheduler.isScheduled())
    }

    @Test
    fun settingsAreRemembered() {
        settings.save(ReminderConfig(enabled = true, time = LocalTime.of(20, 15)))
        assertEquals(ReminderConfig(enabled = true, time = LocalTime.of(20, 15)), ReminderSettings(context).load())
    }

    @Test
    fun defaultIsOffAt21() {
        context.getSharedPreferences("reminder", 0).edit().clear().commit()
        assertEquals(ReminderConfig(enabled = false, time = LocalTime.of(21, 0)), ReminderSettings(context).load())
    }
}
