package de.riemer87.relic

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startet die echte Startseite der App (nicht nur einzelne Bildschirme in einer Testhülle).
 * Fehler aus Meilenstein 7: Mit androidx.biometric kam eine alte Version von „fragment“ ins Projekt;
 * damit stürzte die App beim Öffnen der Ordner- oder Fotoauswahl ab.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        }

    /** Welche App gerade ganz vorne ist. */
    private fun topActivity(): String =
        shell("dumpsys activity activities").lineSequence().firstOrNull { "topResumedActivity" in it }.orEmpty()

    @Test
    fun folderPickerOpensWithoutCrash() {
        // Nach der Installation für den Test ist noch kein Ordner eingerichtet: Ordner-Bildschirm.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasText(str(R.string.folder_existing))).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(str(R.string.folder_existing))).performClick()
        rule.onNode(hasText(str(R.string.folder_continue))).performClick() // öffnet die Android-Ordnerauswahl

        // Die Ordnerauswahl von Android liegt jetzt vorne – Relic ist dabei nicht abgestürzt.
        rule.waitUntil(timeoutMillis = 10_000) { "documentsui" in topActivity() }
        // Auswahl abbrechen: „Zurück“ geht dort erst Ordner für Ordner nach oben, dann zu Relic.
        repeat(6) {
            if ("documentsui" in topActivity()) {
                shell("input keyevent KEYCODE_BACK")
                Thread.sleep(800)
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) { "de.riemer87.relic" in topActivity() }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText(str(R.string.folder_existing))).fetchSemanticsNodes().isNotEmpty())
    }
}
