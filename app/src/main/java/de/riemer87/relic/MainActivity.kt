package de.riemer87.relic

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.riemer87.relic.journal.TreeJournalStore
import de.riemer87.relic.photos.PhotoProcessor
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
                        RelicApp(store = store, clock = Clock.systemDefaultZone(), photoPreparer = photoProcessor::prepare)
                    }
                }
            }
        }
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
