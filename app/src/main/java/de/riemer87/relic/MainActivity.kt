package de.riemer87.relic

import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.riemer87.relic.export.ExportService
import de.riemer87.relic.export.InterruptedExport
import de.riemer87.relic.journal.TreeJournalStore
import de.riemer87.relic.lock.AppLockGate
import de.riemer87.relic.lock.AppLockViewModel
import de.riemer87.relic.lock.BiometricAuthenticator
import de.riemer87.relic.lock.canUseAppLock
import de.riemer87.relic.photos.PhotoProcessor
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.reminder.ReminderReceiver
import de.riemer87.relic.reminder.ReminderScheduler
import de.riemer87.relic.reminder.ReminderSettings
import de.riemer87.relic.storage.JournalLocation
import de.riemer87.relic.storage.SafDocumentTree
import de.riemer87.relic.ui.RelicApp
import de.riemer87.relic.ui.folder.FolderSetupScreen
import de.riemer87.relic.ui.intro.IntroFlow
import de.riemer87.relic.ui.intro.IntroSettings
import de.riemer87.relic.ui.intro.firstIntroStep
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalTime

// FragmentActivity statt ComponentActivity: der Entsperr-Dialog von Android (androidx.biometric) braucht das.
class MainActivity : FragmentActivity() {

    private val appLock: AppLockViewModel by viewModels()

    private lateinit var location: JournalLocation
    private lateinit var introSettings: IntroSettings

    /** Der eingerichtete Journal-Ordner, `null` solange keiner gewählt (oder der Zugriff verloren) ist. */
    private var journalUri by mutableStateOf<Uri?>(null)
    private var checked by mutableStateOf(false)

    /** Beim Start gefunden: ein Export wurde unterbrochen (Hinweis anzeigen). */
    private var interruptedExport by mutableStateOf<InterruptedExport?>(null)

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
        introSettings = IntroSettings(applicationContext)
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
        // Reste eines unterbrochenen Exports (z. B. Akku leer) wegräumen – nur beim echten Start.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                interruptedExport = withContext(Dispatchers.IO) { ExportService.recoverInterrupted(applicationContext) }
            }
        }

        val authenticator = BiometricAuthenticator(this, getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle))

        setContent {
            RelicTheme {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                val lockEnabled by appLock.enabled.collectAsStateWithLifecycle()
                LaunchedEffect(lockEnabled) { hideFromRecents(lockEnabled) }
                AppLockGate(locked = locked, authenticator = authenticator, onUnlocked = appLock::unlocked) {
                    AppContent(photoProcessor, authenticator, lockEnabled)
                }
                // Erst nach dem Entsperren zeigen – ein Dialog läge sonst über dem Sperrbildschirm.
                interruptedExport?.takeIf { !locked }?.let { interrupted ->
                    AlertDialog(
                        onDismissRequest = { interruptedExport = null },
                        text = {
                            Text(
                                if (interrupted.targetDeleted) {
                                    stringResource(R.string.export_interrupted)
                                } else {
                                    stringResource(R.string.export_interrupted_not_deleted, interrupted.targetName)
                                },
                            )
                        },
                        confirmButton = { TextButton(onClick = { interruptedExport = null }) { Text(stringResource(R.string.ok)) } },
                        containerColor = Relic.colors.sheet,
                    )
                }
            }
        }
    }

    @Composable
    private fun AppContent(photoProcessor: PhotoProcessor, authenticator: BiometricAuthenticator, lockEnabled: Boolean) {
        if (!checked) return
        // Einführung nur beim allerersten Start; überlebt Drehen und das Beenden durch Android.
        var introRunning by rememberSaveable { mutableStateOf(firstIntroStep(journalUri != null, introSettings.isDone()) != null) }
        if (introRunning) {
            IntroFlow(
                folderStep = { progress, onReady ->
                    FolderSetupScreen(
                        onFolderReady = {
                            journalUri = it
                            introSettings.markDone()
                            onReady()
                        },
                        introProgress = progress,
                    )
                },
                onReminderAccepted = ::enableReminder,
                lockAvailable = remember { canUseAppLock(this) },
                authenticator = authenticator,
                onLockEnabled = { appLock.setEnabled(true) },
                onFinished = { introRunning = false },
            )
            return
        }
        val uri = journalUri
        if (uri == null) {
            FolderSetupScreen(onFolderReady = { journalUri = it })
            return
        }
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
                authenticator = authenticator,
                lockEnabled = lockEnabled,
                onLockEnabledChange = appLock::setEnabled,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Beim Drehen geht die App nicht wirklich in den Hintergrund.
        if (!isChangingConfigurations) appLock.onBackground()
    }

    /** Bei aktiver Sperre zeigt die App-Übersicht kein Bild vom Inhalt (Android 13+). */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!hide)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderTap(intent)
    }

    private fun handleReminderTap(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderReceiver.EXTRA_OPEN_TODAY, false) == true) openTodayRequest++
    }

    /** „Ja, erinnere mich“ in der Einführung: Erinnerung einschalten und einplanen. */
    private fun enableReminder(time: LocalTime) {
        val config = ReminderConfig(enabled = true, time = time)
        ReminderSettings(applicationContext).save(config)
        ReminderScheduler(applicationContext).apply(config)
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
