package de.riemer87.relic.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val nine = LocalTime.of(21, 0)

    private fun at(iso: String, zone: ZoneId = berlin) = ZonedDateTime.of(java.time.LocalDateTime.parse(iso), zone)

    @Test
    fun `before the time it rings today`() {
        assertEquals(at("2026-10-16T21:00"), ReminderSchedule.nextTrigger(at("2026-10-16T18:30"), nine))
    }

    @Test
    fun `after the time it rings tomorrow`() {
        assertEquals(at("2026-10-17T21:00"), ReminderSchedule.nextTrigger(at("2026-10-16T21:30"), nine))
    }

    @Test
    fun `exactly at the time it plans the next day`() {
        assertEquals(at("2026-10-17T21:00"), ReminderSchedule.nextTrigger(at("2026-10-16T21:00"), nine))
    }

    @Test
    fun `end of month and year roll over`() {
        assertEquals(at("2027-01-01T21:00"), ReminderSchedule.nextTrigger(at("2026-12-31T22:00"), nine))
    }

    @Test
    fun `stays at 21 o clock across the switch to summer time`() {
        // Nacht vom 28. auf den 29. März 2026: Uhren springen von 2 auf 3 Uhr.
        val next = ReminderSchedule.nextTrigger(at("2026-03-28T22:00"), nine)
        assertEquals(at("2026-03-29T21:00"), next)
        assertEquals(21, next.hour)
    }

    @Test
    fun `stays at 21 o clock across the switch to winter time`() {
        val next = ReminderSchedule.nextTrigger(at("2026-10-24T22:00"), nine)
        assertEquals(at("2026-10-25T21:00"), next)
        assertEquals(21, next.hour)
    }

    @Test
    fun `time inside the summer time gap moves to the next valid time`() {
        val next = ReminderSchedule.nextTrigger(at("2026-03-28T23:00"), LocalTime.of(2, 30))
        assertEquals(29, next.dayOfMonth)
        assertEquals(3, next.hour) // 2:30 gibt es an diesem Tag nicht
    }

    @Test
    fun `uses the current time zone after travelling`() {
        val ny = ZoneId.of("America/New_York")
        val next = ReminderSchedule.nextTrigger(at("2026-07-10T18:00", ny), nine)
        assertEquals(at("2026-07-10T21:00", ny), next)
    }

    /**
     * Fehler aus Meilenstein 6: Android meldet „Neustart“ auch, wenn eine zwangsweise beendete App
     * wieder startet. Kommt das nach der Erinnerungszeit an, darf es den Wecker nicht auf morgen schieben.
     */
    @Test
    fun `restart and update keep a planned reminder`() {
        assertEquals(ReminderSchedule.Reaction.KEEP_IF_PLANNED, ReminderSchedule.reactionTo("android.intent.action.BOOT_COMPLETED"))
        assertEquals(ReminderSchedule.Reaction.KEEP_IF_PLANNED, ReminderSchedule.reactionTo("android.intent.action.MY_PACKAGE_REPLACED"))
        assertEquals(ReminderSchedule.Reaction.KEEP_IF_PLANNED, ReminderSchedule.reactionTo(null))
    }

    @Test
    fun `time and time zone changes recompute`() {
        assertEquals(ReminderSchedule.Reaction.RECOMPUTE, ReminderSchedule.reactionTo("android.intent.action.TIME_SET"))
        assertEquals(ReminderSchedule.Reaction.RECOMPUTE, ReminderSchedule.reactionTo("android.intent.action.TIMEZONE_CHANGED"))
    }

    @Test
    fun `only reminds when nothing was written today`() {
        assertTrue(ReminderSchedule.shouldNotify(hasEntryToday = false))
        assertFalse(ReminderSchedule.shouldNotify(hasEntryToday = true))
    }
}
