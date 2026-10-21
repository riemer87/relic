package de.riemer87.relic.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupHint
import de.riemer87.relic.backup.BackupReminder
import de.riemer87.relic.backup.BackupSettings
import de.riemer87.relic.export.ExportRecord
import de.riemer87.relic.export.ExportStatus
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.JournalTextCache
import de.riemer87.relic.journal.localToday
import de.riemer87.relic.lock.Authenticator
import de.riemer87.relic.photos.LocalPhotoLoader
import de.riemer87.relic.photos.PhotoLoader
import de.riemer87.relic.ui.archive.ArchiveScreen
import de.riemer87.relic.ui.archive.ArchiveViewModel
import de.riemer87.relic.ui.entry.EntryScreen
import de.riemer87.relic.ui.entry.EntryViewModel
import de.riemer87.relic.ui.entry.PhotoPreparer
import de.riemer87.relic.ui.folder.FolderSetupScreen
import de.riemer87.relic.ui.search.SearchScreen
import de.riemer87.relic.ui.search.SearchViewModel
import de.riemer87.relic.ui.settings.SettingsScreen
import de.riemer87.relic.ui.settings.SettingsViewModel
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.timeline.TimelineScreen
import de.riemer87.relic.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

private const val HOME = "home"
private const val ENTRY = "entry/{date}"
private const val SEARCH = "search"
private const val SETTINGS = "settings?section={section}"

/** Einstellungen öffnen und gleich zur automatischen Sicherung scrollen (aus dem Hinweis auf „Diese Woche“). */
private const val SETTINGS_BACKUP = "settings?section=backup"
private const val FOLDER = "folder"

/** Dauer eines Seitenwechsels; beim Hinausgehen die Hälfte, damit die neue Seite gleich frei liegt. */
private const val PAGE_MILLIS = 220

/** Wie weit eine Seite beim Wechsel gleitet: ein Zehntel der Breite – angedeutet, nicht geflogen. */
private const val PAGE_SLIDE_FRACTION = 10

/**
 * Die App, sobald ein Journal-Ordner eingerichtet ist: „Diese Woche“ als Start, Eintragsseite pro Tag.
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
    authenticator: Authenticator = Authenticator { it(false) },
    lockEnabled: Boolean = false,
    onLockEnabledChange: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    // Texte für die Suche; bleiben erhalten, solange die App läuft.
    val textCache = remember(store) { JournalTextCache(store) }
    // Verlässt man die App, könnten Einträge woanders (z. B. am PC) geändert werden: danach frisch lesen.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { textCache.clear() }
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

    // Aus der Erinnerung: zurück zu „Diese Woche“ und die Eintragsseite von heute öffnen.
    LaunchedEffect(openTodayRequest) {
        if (openTodayRequest > 0) {
            navController.popBackStack(HOME, inclusive = false)
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
                startDestination = HOME,
                modifier = Modifier.padding(innerPadding),
                // Kurz und leicht statt der Standard-Überblendung von 0,7 s: Neue Seiten gleiten ein Stück von
                // rechts herein, beim Zurückgehen wieder hinaus. Bewegt wird nur Position und Durchsichtigkeit.
                enterTransition = { fadeIn(tween(PAGE_MILLIS)) + slideInHorizontally(tween(PAGE_MILLIS)) { it / PAGE_SLIDE_FRACTION } },
                exitTransition = { fadeOut(tween(PAGE_MILLIS / 2)) },
                popEnterTransition = { fadeIn(tween(PAGE_MILLIS / 2)) },
                popExitTransition = { fadeOut(tween(PAGE_MILLIS)) + slideOutHorizontally(tween(PAGE_MILLIS)) { it / PAGE_SLIDE_FRACTION } },
            ) {
                composable(HOME) {
                    val timeline: TimelineViewModel = viewModel(factory = viewModelFactory { initializer { TimelineViewModel(store, clock) } })
                    val archive: ArchiveViewModel = viewModel(
                        factory = viewModelFactory { initializer { ArchiveViewModel(store, clock, createSavedStateHandle()) } },
                    )
                    var tab by rememberSaveable { mutableStateOf(HomeTab.WEEK) }
                    // Archiv schon beim Start im Hintergrund vorbereiten, damit der erste Wechsel sofort fertig ist.
                    LaunchedEffect(archive) { archive.refresh() }
                    val backupHint = rememberBackupHint(timeline, clock)
                    HomeScreen(
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenSettings = { navigateFrom(HOME, "settings") },
                        onOpenSearch = { navigateFrom(HOME, SEARCH) },
                        week = {
                            TimelineScreen(
                                viewModel = timeline,
                                onOpenDay = { date -> navigateFrom(HOME, "entry/$date") },
                                onOpenArchive = { tab = HomeTab.ARCHIVE },
                                backupHint = backupHint,
                                onOpenBackup = { navigateFrom(HOME, SETTINGS_BACKUP) },
                            )
                        },
                        archive = {
                            ArchiveScreen(viewModel = archive, onOpenDay = { date -> navigateFrom(HOME, "entry/$date") })
                        },
                    )
                }
                composable(SEARCH) {
                    SearchScreen(
                        viewModel = viewModel(
                            factory = viewModelFactory { initializer { SearchViewModel(store, textCache, createSavedStateHandle()) } },
                        ),
                        onBack = { if (navController.currentDestination?.route == SEARCH) navController.popBackStack() },
                        onOpenDay = { date -> navigateFrom(SEARCH, "entry/$date") },
                    )
                }
                composable(
                    SETTINGS,
                    arguments = listOf(navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStackEntry ->
                    SettingsScreen(
                        focusBackup = backStackEntry.arguments?.getString("section") == "backup",
                        viewModel = viewModel(
                            factory = viewModelFactory { initializer { SettingsViewModel(this[APPLICATION_KEY]!!, journalUri) } },
                        ),
                        onBack = { if (navController.currentDestination?.route == SETTINGS) navController.popBackStack() },
                        onChangeFolder = { navigateFrom(SETTINGS, FOLDER) },
                        showMessage = showMessage,
                        lockEnabled = lockEnabled,
                        authenticator = authenticator,
                        onLockEnabledChange = onLockEnabledChange,
                    )
                }
                composable(FOLDER) {
                    FolderSetupScreen(
                        onFolderReady = onFolderChanged,
                        onBack = { if (navController.currentDestination?.route == FOLDER) navController.popBackStack() },
                        notice = stringResource(R.string.folder_change_notice),
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
                            textCache.forget(date) // Für die Suche: Text neu lesen.
                            // Zurück dorthin, wo man herkam („Diese Woche“ oder Archiv).
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

/**
 * Erinnerung ans Sichern für „Diese Woche“ ([BackupReminder]): aus automatischer Sicherung,
 * letztem Export und ob es überhaupt Einträge gibt. Aktualisiert sich, sobald gesichert wurde.
 */
@Composable
private fun rememberBackupHint(timeline: TimelineViewModel, clock: Clock): BackupHint? {
    val context = LocalContext.current
    val backup by BackupSettings.get(context).info.collectAsStateWithLifecycle()
    val export by ExportStatus.state.collectAsStateWithLifecycle()
    val week by timeline.state.collectAsStateWithLifecycle()
    val lastExport = remember(export) { ExportRecord(context).lastSuccess()?.finishedAt }
    val zone = clock.zone
    val lastSaved = listOfNotNull(
        lastExport?.atZone(zone)?.toLocalDate(),
        backup.newest?.toLocalDate(),
    ).maxOrNull()
    return BackupReminder.hint(
        autoBackupOn = backup.enabled,
        problem = backup.problem,
        lastSaved = lastSaved,
        firstEntry = week.firstEntry,
        today = week.today,
    )
}
