package de.riemer87.relic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.riemer87.relic.journal.FileJournalStore
import de.riemer87.relic.ui.theme.RelicTheme
import de.riemer87.relic.ui.today.TodayScreen
import de.riemer87.relic.ui.today.TodayViewModel
import java.io.File
import java.time.Clock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bis Meilenstein 2: fester Ordner der App (Android/data/de.riemer87.relic/files/journal).
        val journalDir = getExternalFilesDir("journal") ?: File(filesDir, "journal")
        val factory = viewModelFactory {
            initializer { TodayViewModel(FileJournalStore(journalDir), Clock.systemDefaultZone()) }
        }

        setContent {
            RelicTheme {
                TodayScreen(viewModel(factory = factory))
            }
        }
    }
}
