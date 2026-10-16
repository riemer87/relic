package de.riemer87.relic.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.photos.LocalPhotoLoader
import de.riemer87.relic.photos.PhotoLoader
import de.riemer87.relic.ui.archive.ArchiveScreen
import de.riemer87.relic.ui.archive.ArchiveViewModel
import de.riemer87.relic.ui.entry.EntryScreen
import de.riemer87.relic.ui.entry.EntryViewModel
import de.riemer87.relic.ui.entry.PhotoPreparer
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.timeline.TimelineScreen
import de.riemer87.relic.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private const val TIMELINE = "timeline"
private const val ENTRY = "entry/{date}"
private const val ARCHIVE = "archive"

/** Die App, sobald ein Journal-Ordner eingerichtet ist: Zeitstrahl als Start, Eintragsseite pro Tag. */
@Composable
fun RelicApp(
    store: JournalStore,
    clock: Clock,
    photoPreparer: PhotoPreparer,
    photoLoader: PhotoLoader = remember(store) { PhotoLoader(store) },
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
