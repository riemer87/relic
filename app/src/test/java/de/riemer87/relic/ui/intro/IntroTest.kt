package de.riemer87.relic.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntroTest {

    @Test
    fun firstStartBeginsWithWelcome() {
        assertEquals(IntroStep.WELCOME, firstIntroStep(hasFolder = false, introDone = false))
    }

    /** Wer Relic schon vor der Einführung benutzt hat, hat einen Ordner – und sieht sie nie. */
    @Test
    fun existingUsersNeverSeeTheIntro() {
        assertNull(firstIntroStep(hasFolder = true, introDone = false))
        assertNull(firstIntroStep(hasFolder = true, introDone = true))
    }

    @Test
    fun lockStepFollowsReminderWhenThePhoneHasAScreenLock() {
        assertEquals(IntroStep.FOLDER, nextIntroStep(IntroStep.WELCOME, lockAvailable = true))
        assertEquals(IntroStep.REMINDER, nextIntroStep(IntroStep.FOLDER, lockAvailable = true))
        assertEquals(IntroStep.LOCK, nextIntroStep(IntroStep.REMINDER, lockAvailable = true))
        assertNull(nextIntroStep(IntroStep.LOCK, lockAvailable = true))
        assertEquals(4, introSteps(lockAvailable = true).size)
    }

    /** Ohne Displaysperre gibt es nichts zum Entsperren: Schritt entfällt, nur drei Punkte. */
    @Test
    fun withoutScreenLockTheIntroEndsAfterTheReminder() {
        assertNull(nextIntroStep(IntroStep.REMINDER, lockAvailable = false))
        assertEquals(listOf(IntroStep.WELCOME, IntroStep.FOLDER, IntroStep.REMINDER), introSteps(lockAvailable = false))
    }

    /** Ordner später verloren: kein Willkommen mehr, nur der Ordner-Bildschirm. */
    @Test
    fun lostFolderAfterIntroSkipsWelcome() {
        assertNull(firstIntroStep(hasFolder = false, introDone = true))
    }
}
