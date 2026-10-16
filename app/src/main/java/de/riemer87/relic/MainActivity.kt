package de.riemer87.relic

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.riemer87.relic.journal.TreeJournalStore
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import de.riemer87.relic.ui.folder.FolderSetupScreen
import de.riemer87.relic.ui.theme.RelicTheme
import de.riemer87.relic.ui.today.TodayScreen
import de.riemer87.relic.ui.today.TodayViewModel
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
        enableEdgeToEdge()
        location = JournalLocation(applicationContext)

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
                    val todayViewModel: TodayViewModel = viewModel(key = uri.toString(), factory = todayFactory(uri))
                    TodayScreen(todayViewModel)
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

    private fun todayFactory(uri: Uri) = viewModelFactory {
        initializer {
            TodayViewModel(TreeJournalStore(SafDocumentTree(contentResolver, uri)), Clock.systemDefaultZone())
        }
    }
}
