package de.riemer87.relic.lock

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.riemer87.relic.R
import de.riemer87.relic.ui.FakeJournalStore
import de.riemer87.relic.ui.RelicApp
import de.riemer87.relic.ui.theme.RelicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class AppLockTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = AppLockSettings(context)
    private var before = false

    /** Attrappe für den Android-Dialog: zählt Aufrufe und antwortet wie vorgegeben. */
    private class FakeAuthenticator(var answer: Boolean) : Authenticator {
        var calls = 0
        override fun authenticate(onResult: (Boolean) -> Unit) {
            calls++
            onResult(answer)
        }
    }

    /** Uhr, die der Test vorstellen kann. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    @Before
    fun remember() {
        before = settings.enabled
    }

    @After
    fun restore() {
        settings.enabled = before
    }

    private fun str(id: Int) = rule.activity.getString(id)

    private fun waitForText(text: String) =
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    // --- Sperrbildschirm ---

    @Test
    fun lockedAppHidesTheDiaryAndAsksAutomatically() {
        val auth = FakeAuthenticator(answer = false)
        rule.setContent { RelicTheme { AppLockGate(locked = true, authenticator = auth, onUnlocked = {}) { Text("Geheimer Eintrag") } } }
        waitForText(str(R.string.lock_unlock))

        rule.onNode(hasText("Geheimer Eintrag")).assertDoesNotExist() // auch für Bildschirmleser verborgen
        rule.waitUntil(timeoutMillis = 5_000) { auth.calls == 1 }
    }

    @Test
    fun cancellingKeepsItLockedAndDoesNotAskAgainByItself() {
        val auth = FakeAuthenticator(answer = false)
        rule.setContent { RelicTheme { AppLockGate(locked = true, authenticator = auth, onUnlocked = {}) { Text("Geheimer Eintrag") } } }
        rule.waitUntil(timeoutMillis = 5_000) { auth.calls == 1 }
        rule.waitForIdle()
        assertEquals("Nach „Abbrechen“ nicht sofort wieder fragen", 1, auth.calls)

        rule.onNode(hasText(str(R.string.lock_unlock))).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { auth.calls == 2 }
        rule.onNode(hasText(str(R.string.lock_unlock))).assertExists()
    }

    @Test
    fun successfulUnlockShowsTheDiaryAgain() {
        val auth = FakeAuthenticator(answer = true)
        var locked by mutableStateOf(true)
        rule.setContent { RelicTheme { AppLockGate(locked = locked, authenticator = auth, onUnlocked = { locked = false }) { Text("Geheimer Eintrag") } } }

        waitForText("Geheimer Eintrag")
        rule.onNode(hasText(str(R.string.lock_unlock))).assertDoesNotExist()
    }

    // --- Wann gesperrt wird ---

    private fun lockModel(clock: Clock): AppLockViewModel {
        lateinit var vm: AppLockViewModel
        instrumentation.runOnMainSync {
            vm = AppLockViewModel(context.applicationContext as Application).apply { useClock(clock) }
        }
        return vm
    }

    @Test
    fun freshStartIsLockedWhenEnabled() {
        settings.enabled = true
        val vm = lockModel(TestClock(Instant.parse("2026-10-17T20:00:00Z")))
        vm.onForeground()
        assertTrue(vm.locked.value)
    }

    @Test
    fun rotatingDoesNotLockAgain() {
        settings.enabled = true
        val vm = lockModel(TestClock(Instant.parse("2026-10-17T20:00:00Z")))
        vm.onForeground()
        vm.unlocked()
        vm.onForeground() // Drehen: kein onBackground dazwischen
        assertFalse(vm.locked.value)
    }

    @Test
    fun shortTripAwayStaysUnlockedLongTripLocks() {
        settings.enabled = true
        val clock = TestClock(Instant.parse("2026-10-17T20:00:00Z"))
        val vm = lockModel(clock)
        vm.onForeground()
        vm.unlocked()

        vm.onBackground()
        clock.now = clock.now.plusSeconds(40)
        vm.onForeground()
        assertFalse("40 Sekunden weg: offen", vm.locked.value)

        vm.onBackground()
        clock.now = clock.now.plus(Duration.ofMinutes(2))
        vm.onForeground()
        assertTrue("2 Minuten weg: gesperrt", vm.locked.value)
    }

    @Test
    fun switchedOffNeverLocks() {
        settings.enabled = false
        val vm = lockModel(TestClock(Instant.parse("2026-10-17T20:00:00Z")))
        vm.onForeground()
        assertFalse(vm.locked.value)
    }

    // --- Einschalten in den Einstellungen ---

    private fun openSettings(auth: Authenticator, onChange: (Boolean) -> Unit) {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FRelic".toUri()
        rule.setContent {
            RelicTheme {
                RelicApp(
                    FakeJournalStore(), Clock.systemDefaultZone(), photoPreparer = { null }, journalUri = uri,
                    authenticator = auth, lockEnabled = false, onLockEnabledChange = onChange,
                )
            }
        }
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasContentDescription(str(R.string.action_settings))).performClick()
        waitForText(str(R.string.settings_title))
    }

    // [0] Erinnerung, [1] App-Sperre. Direkt auslösen statt über die Bildschirmposition.
    private fun lockSwitch() = rule.onAllNodes(isToggleable())[1]
    private fun tapLockSwitch() = lockSwitch().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun turningOnRequiresASuccessfulUnlock() {
        var enabledTo: Boolean? = null
        val auth = FakeAuthenticator(answer = true)
        openSettings(auth) { enabledTo = it }

        tapLockSwitch()
        rule.waitUntil(timeoutMillis = 5_000) { enabledTo != null }
        assertEquals(1, auth.calls)
        assertEquals(true, enabledTo)
    }

    @Test
    fun failedUnlockLeavesTheLockOff() {
        var enabledTo: Boolean? = null
        val auth = FakeAuthenticator(answer = false)
        openSettings(auth) { enabledTo = it }

        tapLockSwitch()
        waitForText(str(R.string.lock_enable_failed))
        assertEquals(null, enabledTo)
    }
}
