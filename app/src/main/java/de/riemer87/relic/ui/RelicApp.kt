package de.riemer87.relic.ui

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.riemer87.relic.R
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.photos.LocalPhotoLoader
import de.riemer87.relic.photos.PhotoLoader
import de.riemer87.relic.ui.archive.ArchiveScreen
import de.riemer87.relic.ui.archive.ArchiveViewModel
import de.riemer87.relic.ui.entry.EntryScreen
import de.riemer87.relic.ui.entry.EntryViewModel
import de.riemer87.relic.ui.entry.PhotoPreparer
import de.riemer87.relic.ui.folder.FolderSetupScreen
import de.riemer87.relic.ui.settings.SettingsScreen
import de.riemer87.relic.ui.settings.SettingsViewModel
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.timeline.TimelineScreen
import de.riemer87.relic.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private const val TIMELINE = "timeline"
private const val ENTRY = "entry/{date}"
private const val ARCHIVE = "archive"
private const val SETTINGS = "settings"
private const val FOLDER = "folder"

/**
 * Die App, sobald ein Journal-Ordner eingerichtet ist: Zeitstrahl als Start, Eintragsseite pro Tag.
 * @param journalUri der eingerichtete Ordner (für die Anzeige in den Einstellungen)
 * @param onFolderChanged ein anderer Ordner wurde in den Einstellungen gewählt
 * @param openTodayRequest wird hochgezählt, wenn die Eintragsseite von heute geöffnet werden soll
 *   (Tippen auf die tägliche Erinnerung)
 */
@Composable
fun RelicApp(
    store: JournalStore,
    clock: Clock,
    photoPreparer: PhotoPreparer,
    photoLoader: PhotoLoader = remember(store) { PhotoLoader(store) },
    journalUri: Uri? = null,
    onFolderChanged: (Uri) -> Unit = {},
    openTodayRequest: Int = 0,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    /** Doppeltes Tippen öffnet eine Seite nicht zweimal. */
    fun navigateFrom(current: String, target: String) {
        if (navController.currentDestination?.route == current) navController.navigate(target)
    }

    // Aus der Erinnerung: zurück zum Zeitstrahl und die Eintragsseite von heute öffnen.
    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest > 0) {
            navController.popBackStack(TIMELINE, inclusive = false)
            navController.navigate("entry/${localToday(clock)}")
        }
    }

    CompositionLocalProvider(LocalPhotoLoader provides photoLoader) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Relic.colors.paper,
            // Die Tastatur zählt als freizuhaltender Bereich, damit Inhalt und Meldungen über ihr bleiben.
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TIMELINE,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TIMELINE) {
                    TimelineScreen(
                        viewModel = viewModel(factory = viewModelFactory { initializer { TimelineViewModel(store, clock) } }),
                        onOpenDay = { date -> navigateFrom(TIMELINE, "entry/$date") },
                        onOpenArchive = { navigateFrom(TIMELINE, ARCHIVE) },
                        onOpenSettings = { navigateFrom(TIMELINE, SETTINGS) },
                    )
                }
                composable(SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel(
                            factory = viewModelFactory { initializer { SettingsViewModel(this[APPLICATION_KEY]!!, journalUri) } },
                        ),
                        onBack = { if (navController.currentDestination?.route == SETTINGS) navController.popBackStack() },
                        onChangeFolder = { navigateFrom(SETTINGS, FOLDER) },
                        showMessage = showMessage,
                    )
                }
                composable(FOLDER) {
                    FolderSetupScreen(
                        onFolderReady = onFolderChanged,
                        onBack = { if (navController.currentDestination?.route == FOLDER) navController.popBackStack() },
                        notice = stringResource(R.string.folder_change_notice),
                    )
                }
                composable(ARCHIVE) {
                    ArchiveScreen(
                        viewModel = viewModel(
                            factory = viewModelFactory { initializer { ArchiveViewModel(store, clock, createSavedStateHandle()) } },
                        ),
                        onBack = { if (navController.currentDestination?.route == ARCHIVE) navController.popBackStack() },
                        onOpenDay = { date -> navigateFrom(ARCHIVE, "entry/$date") },
                    )
                }
                composable(ENTRY) { backStackEntry ->
                    val date = backStackEntry.arguments?.getString("date")
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?.takeIf { !it.isAfter(localToday(clock)) } // Zukunft ist nicht erreichbar.
                    if (date == null) {
                        navController.popBackStack()
                        return@composable
                    }
                    EntryScreen(
                        viewModel = viewModel(
                            factory = viewModelFactory { initializer { EntryViewModel(store, date, clock, photoPreparer) } },
                        ),
                        onClose = { message ->
                            photoLoader.forget(date) // Fotos können neue Dateinamen bekommen haben.
                            // Zurück dorthin, wo man herkam (Zeitstrahl oder Archiv).
                            if (navController.currentDestination?.route == ENTRY) navController.popBackStack()
                            message?.let { showMessage(resources.getString(it)) }
                        },
                        showMessage = showMessage,
                    )
                }
            }
        }
    }
}
