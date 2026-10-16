package de.riemer87.relic

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.riemer87.relic.journal.TreeJournalStore
import de.riemer87.relic.photos.PhotoProcessor
import de.riemer87.relic.reminder.ReminderReceiver
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import de.riemer87.relic.ui.RelicApp
import de.riemer87.relic.ui.folder.FolderSetupScreen
import de.riemer87.relic.ui.theme.RelicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

class MainActivity : ComponentActivity() {

    private lateinit var location: JournalLocation

    /** Der eingerichtete Journal-Ordner, `null` solange keiner gewählt (oder der Zugriff verloren) ist. */
    private var journalUri by mutableStateOf<Uri?>(null)
    private var checked by mutableStateOf(false)

    /** Wird hochgezählt, wenn die Eintragsseite von heute geöffnet werden soll (Tippen auf die Erinnerung). */
    private var openTodayRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Papier ist immer hell: dunkle Symbole in Status- und Navigationsleiste, auch wenn das Handy im Dunkelmodus ist.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        location = JournalLocation(applicationContext)
        val photoProcessor = PhotoProcessor(applicationContext)
        // Übrig gebliebene Fotos aus dem Zwischenspeicher (nur beim echten Start, nicht beim Drehen).
        if (savedInstanceState == null) photoProcessor.clearPending()
        if (savedInstanceState == null) handleReminderTap(intent)

        // Erinnerung sicherheitshalber einplanen (z. B. nach „Beenden erzwingen“) – eine schon geplante bleibt.
        ReminderScheduler(applicationContext).ensureScheduled(ReminderSettings(applicationContext).load())

        lifecycleScope.launch {
            journalUri = withContext(Dispatchers.IO) { location.accessibleUri() }
            checked = true
        }

        setContent {
            RelicTheme {
                if (!checked) return@RelicTheme
                val uri = journalUri
                if (uri == null) {
                    FolderSetupScreen(onFolderReady = { journalUri = it })
                } else {
                    // Neuer Ordner = alles neu aufbauen, damit keine Seite noch den alten Ordner benutzt.
                    key(uri) {
                        val store = remember { TreeJournalStore(SafDocumentTree(contentResolver, uri)) }
                        RelicApp(
                            store = store,
                            clock = Clock.systemDefaultZone(),
                            photoPreparer = photoProcessor::prepare,
                            journalUri = uri,
                            onFolderChanged = ::changeFolder,
                            openTodayRequest = openTodayRequest,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderTap(intent)
    }

    private fun handleReminderTap(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderReceiver.EXTRA_OPEN_TODAY, false) == true) openTodayRequest++
    }

    /** In den Einstellungen wurde ein anderer Ordner gewählt: alte Freigabe abgeben, alles neu aufbauen. */
    private fun changeFolder(newUri: Uri) {
        val old = journalUri
        if (old != null && old != newUri) location.releasePermission(old)
        journalUri = newUri
    }

    override fun onResume() {
        super.onResume()
        // Ordner gelöscht oder Freigabe entzogen, während die App im Hintergrund war? Dann neu wählen.
        val current = journalUri ?: return
        lifecycleScope.launch {
            val stillAccessible = withContext(Dispatchers.IO) { location.accessibleUri() } == current
            if (!stillAccessible && journalUri == current) journalUri = null
        }
    }
}
