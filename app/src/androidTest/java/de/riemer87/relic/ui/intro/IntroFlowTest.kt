package de.riemer87.relic.ui.intro

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import de.riemer87.relic.lock.Authenticator
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class IntroFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var acceptedTime: LocalTime? = null
    private var lockEnabled = false
    private var finished = false

    /** Ergebnis des (vorgetäuschten) Android-Entsperrdialogs. */
    private var unlockSucceeds = true
    private var unlockAttempts = 0

    private fun str(id: Int) = rule.activity.getString(id)

    @Before
    fun setUp() {
        // Sonst würde Android 13+ beim „Ja, erinnere mich“ einen Erlaubnis-Dialog zeigen.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Statt des echten Ordner-Bildschirms (der den Android-Ordnerdialog öffnet) ein einfacher Knopf. */
    private fun startIntro(lockAvailable: Boolean = true) {
        rule.setContent {
            RelicTheme {
                IntroFlow(
                    folderStep = { _, onReady -> Button(onClick = onReady) { Text("FOLDER_STEP") } },
                    onReminderAccepted = { acceptedTime = it },
                    lockAvailable = lockAvailable,
                    authenticator = Authenticator { result ->
                        unlockAttempts++
                        result(unlockSucceeds)
                    },
                    onLockEnabled = { lockEnabled = true },
                    onFinished = { finished = true },
                )
            }
        }
    }

    private fun goToReminderStep() {
        rule.onNode(hasText(str(R.string.intro_welcome_start))).performClick()
        rule.onNode(hasText("FOLDER_STEP")).performClick()
        rule.onNode(hasText(str(R.string.intro_reminder_title))).assertExists()
    }

    private fun goToLockStep() {
        goToReminderStep()
        rule.onNode(hasText(str(R.string.intro_later))).performClick()
        rule.onNode(hasText(str(R.string.intro_lock_title))).assertExists()
    }

    @Test
    fun welcomeLeadsToFolderStep() {
        startIntro()

        rule.onNode(hasText(str(R.string.intro_welcome_title))).assertExists()
        rule.onNode(hasText(str(R.string.intro_welcome_start))).performClick()

        rule.onNode(hasText("FOLDER_STEP")).assertExists()
        rule.onNode(hasText(str(R.string.intro_welcome_title))).assertDoesNotExist()
    }

    @Test
    fun backFromFolderStepReturnsToWelcome() {
        startIntro()
        rule.onNode(hasText(str(R.string.intro_welcome_start))).performClick()
        rule.waitForIdle()

        Espresso.pressBack()

        rule.onNode(hasText(str(R.string.intro_welcome_title))).assertExists()
    }

    @Test
    fun acceptingTheReminderUsesTheDefaultTimeAndContinuesToLock() {
        startIntro()
        goToReminderStep()

        rule.onNode(hasText(str(R.string.intro_reminder_yes))).performClick()
        rule.waitUntil(3_000) { acceptedTime != null }
        rule.waitForIdle()

        assertEquals(ReminderConfig.DEFAULT_TIME, acceptedTime)
        rule.onNode(hasText(str(R.string.intro_lock_title))).assertExists()
        assertFalse(finished)
    }

    @Test
    fun backOnReminderStepMeansMaybeLater() {
        startIntro()
        goToReminderStep()

        Espresso.pressBack()
        rule.waitForIdle()

        assertNull(acceptedTime)
        rule.onNode(hasText(str(R.string.intro_lock_title))).assertExists()
    }

    @Test
    fun lockIsEnabledOnlyAfterSuccessfulUnlock() {
        startIntro()
        goToLockStep()

        rule.onNode(hasText(str(R.string.intro_lock_yes))).performClick()
        rule.waitForIdle()

        assertEquals(1, unlockAttempts)
        assertTrue(lockEnabled)
        assertTrue(finished)
    }

    /** Entsperren klappt nicht: Sperre bleibt aus, man bleibt auf dem Schritt und kann es erneut versuchen. */
    @Test
    fun failedUnlockKeepsLockOffAndStaysOnStep() {
        unlockSucceeds = false
        startIntro()
        goToLockStep()

        rule.onNode(hasText(str(R.string.intro_lock_yes))).performClick()
        rule.waitForIdle()

        assertFalse(lockEnabled)
        assertFalse(finished)
        rule.onNode(hasText(str(R.string.lock_enable_failed))).assertExists()

        unlockSucceeds = true
        rule.onNode(hasText(str(R.string.intro_lock_yes))).performClick()
        rule.waitForIdle()

        assertTrue(lockEnabled)
        assertTrue(finished)
    }

    @Test
    fun maybeLaterOnLockStepFinishesWithoutLock() {
        startIntro()
        goToLockStep()

        rule.onNode(hasText(str(R.string.intro_later))).performClick()
        rule.waitForIdle()

        assertEquals(0, unlockAttempts)
        assertFalse(lockEnabled)
        assertTrue(finished)
    }

    @Test
    fun backOnLockStepFinishesWithoutLock() {
        startIntro()
        goToLockStep()

        Espresso.pressBack()
        rule.waitForIdle()

        assertFalse(lockEnabled)
        assertTrue(finished)
    }

    /** Handy ohne Displaysperre: Nach der Erinnerung ist Schluss, die Punkte zeigen nur drei Schritte. */
    @Test
    fun withoutScreenLockTheIntroEndsAfterReminder() {
        startIntro(lockAvailable = false)
        rule.onNode(hasContentDescription(rule.activity.getString(R.string.intro_step, 1, 3))).assertExists()
        goToReminderStep()

        rule.onNode(hasText(str(R.string.intro_later))).performClick()
        rule.waitForIdle()

        assertTrue(finished)
        assertEquals(0, unlockAttempts)
        rule.onNode(hasText(str(R.string.intro_lock_title))).assertDoesNotExist()
    }
}
