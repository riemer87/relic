package de.riemer87.relic.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LockPolicyTest {

    private val now = Instant.parse("2026-10-17T20:00:00Z")

    @Test
    fun `never locks when switched off`() {
        assertFalse(LockPolicy.shouldLock(enabled = false, backgroundSince = null, now = now))
        assertFalse(LockPolicy.shouldLock(enabled = false, backgroundSince = now.minusSeconds(3600), now = now))
    }

    @Test
    fun `locks on a fresh start`() {
        assertTrue(LockPolicy.shouldLock(enabled = true, backgroundSince = null, now = now))
    }

    @Test
    fun `quick trip to another app does not lock`() {
        // z. B. kurz eine Nachricht beantworten oder ein Foto auswählen
        assertFalse(LockPolicy.shouldLock(enabled = true, backgroundSince = now.minusSeconds(20), now = now))
        assertFalse(LockPolicy.shouldLock(enabled = true, backgroundSince = now.minusSeconds(60), now = now))
    }

    @Test
    fun `more than a minute away locks`() {
        assertTrue(LockPolicy.shouldLock(enabled = true, backgroundSince = now.minusSeconds(61), now = now))
        assertTrue(LockPolicy.shouldLock(enabled = true, backgroundSince = now.minusSeconds(3600), now = now))
    }
}
