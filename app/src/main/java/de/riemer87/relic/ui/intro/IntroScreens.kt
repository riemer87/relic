package de.riemer87.relic.ui.intro

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.riemer87.relic.R
import de.riemer87.relic.backup.BackupFolderChoice
import de.riemer87.relic.ui.settings.rememberBackupFolderChooser
import de.riemer87.relic.lock.Authenticator
import de.riemer87.relic.reminder.ReminderConfig
import de.riemer87.relic.ui.settings.TimeDialog
import de.riemer87.relic.ui.settings.formatTime
import de.riemer87.relic.ui.theme.SheetShape
import de.riemer87.relic.ui.theme.FrauncesDisplay
import de.riemer87.relic.ui.theme.Relic
import de.riemer87.relic.ui.theme.RelicTheme
import java.time.LocalTime

/**
 * Die Einführung beim allerersten Start: Willkommen → Ordner → Erinnerung → App-Sperre → Sicherung.
 * @param folderStep der Ordner-Bildschirm; ruft `onReady` auf, sobald ein Ordner eingerichtet ist
 * @param onReminderAccepted „Ja, erinnere mich“ mit der gewählten Uhrzeit (Erlaubnis ist dann erteilt)
 * @param lockAvailable das Handy hat eine Displaysperre; sonst entfällt der Schritt App-Sperre
 * @param authenticator der Android-Entsperrdialog
 * @param onLockEnabled Entsperren hat geklappt: App-Sperre einschalten
 * @param chooseBackupFolder übernimmt den gewählten Sicherungsordner und schaltet die Sicherung ein
 * @param onFinished die Einführung ist vorbei, die App beginnt
 */
@Composable
fun IntroFlow(
    folderStep: @Composable (progress: IntroProgress, onReady: () -> Unit) -> Unit,
    onReminderAccepted: (LocalTime) -> Unit,
    lockAvailable: Boolean,
    authenticator: Authenticator,
    onLockEnabled: () -> Unit,
    chooseBackupFolder: (Uri) -> BackupFolderChoice,
    onFinished: () -> Unit,
    start: IntroStep = IntroStep.WELCOME,
) {
    var step by rememberSaveable { mutableStateOf(start) }
    val progress = introProgress(step, lockAvailable)
    fun next() {
        val following = nextIntroStep(step, lockAvailable)
        if (following == null) onFinished() else step = following
    }
    when (step) {
        IntroStep.WELCOME -> WelcomeStep(progress, onNext = ::next)
        IntroStep.FOLDER -> {
            BackHandler { step = IntroStep.WELCOME }
            folderStep(progress, ::next)
        }
        IntroStep.REMINDER -> {
            // Der Ordner ist schon gewählt; zurück heißt hier einfach „Vielleicht später“.
            BackHandler(onBack = ::next)
            ReminderStep(
                progress = progress,
                onAccept = { time ->
                    onReminderAccepted(time)
                    next()
                },
                onSkip = ::next,
            )
        }
        IntroStep.LOCK -> {
            BackHandler(onBack = ::next)
            LockStep(
                progress = progress,
                authenticator = authenticator,
                onEnabled = {
                    onLockEnabled()
                    next()
                },
                onSkip = ::next,
            )
        }
        IntroStep.BACKUP -> {
            BackHandler(onBack = ::next)
            BackupStep(progress = progress, chooseFolder = chooseBackupFolder, onDone = ::next)
        }
    }
}

/** Schritt 1: das Versprechen von Relic. */
@Composable
internal fun WelcomeStep(progress: IntroProgress, onNext: () -> Unit) {
    val colors = Relic.colors
    IntroPage(
        progress = progress,
        centered = true,
        bottom = { PrimaryButton(stringResource(R.string.intro_welcome_start), onNext) },
    ) {
        // Logo und Schriftzug wie in der Kopfzeile, nur größer (gleiches Verhältnis von Logo zu Schrift).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.relic_logo), contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = FrauncesDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 44.sp,
                color = colors.ink,
            )
        }
        IntroTitle(stringResource(R.string.intro_welcome_title))
        IntroText(stringResource(R.string.intro_welcome_text))
        Spacer(Modifier.height(4.dp))
        FileExample()
    }
}

/** Kleines Beispiel, wie ein Tag als Datei im Ordner aussieht. */
@Composable
private fun FileExample() {
    val colors = Relic.colors
    val description = stringResource(R.string.intro_file_example_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(colors.sheet)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 21.sp)
        Text("2026/10/", style = style, color = colors.ink, fontWeight = FontWeight.SemiBold)
        Text("  2026-10-16.md", style = style, color = colors.inkSoft)
        Text("  2026-10-16_1.jpg", style = style, color = colors.inkSoft)
    }
}

/** Schritt 3: abendliche Erinnerung ja oder später. */
@Composable
internal fun ReminderStep(progress: IntroProgress, onAccept: (LocalTime) -> Unit, onSkip: () -> Unit) {
    val colors = Relic.colors
    var time by rememberSaveable { mutableStateOf(ReminderConfig.DEFAULT_TIME) }
    var timeDialogOpen by rememberSaveable { mutableStateOf(false) }

    // Android 13+ fragt nach der Erlaubnis für Benachrichtigungen. Ohne sie gibt es keine Erinnerung.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onAccept(time) else onSkip()
    }

    IntroPage(
        progress = progress,
        bottom = {
            PrimaryButton(stringResource(R.string.intro_reminder_yes)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onAccept(time)
                }
            }
            LaterButton(onSkip)
        },
    ) {
        IntroTitle(stringResource(R.string.intro_reminder_title))
        IntroText(stringResource(R.string.intro_reminder_text))
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(colors.sheet)
                .clickable(role = Role.Button) { timeDialogOpen = true }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_reminder_time), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            Spacer(Modifier.weight(1f))
            Text(formatTime(time), fontFamily = FrauncesDisplay, fontSize = 26.sp, color = colors.ink)
        }
    }

    if (timeDialogOpen) {
        TimeDialog(
            initial = time,
            onConfirm = {
                time = it
                timeDialogOpen = false
            },
            onDismiss = { timeDialogOpen = false },
        )
    }
}

/**
 * Schritt 4: App-Sperre. Eingeschaltet wird sie erst nach einmal erfolgreichem Entsperren,
 * damit sich niemand aussperrt. Klappt es nicht, bleibt man hier und kann es erneut versuchen.
 */
@Composable
internal fun LockStep(progress: IntroProgress, authenticator: Authenticator, onEnabled: () -> Unit, onSkip: () -> Unit) {
    val colors = Relic.colors
    var failed by rememberSaveable { mutableStateOf(false) }
    IntroPage(
        progress = progress,
        bottom = {
            PrimaryButton(stringResource(R.string.intro_lock_yes)) {
                failed = false
                authenticator.authenticate { ok -> if (ok) onEnabled() else failed = true }
            }
            LaterButton(onSkip)
        },
    ) {
        IntroTitle(stringResource(R.string.intro_lock_title))
        IntroText(stringResource(R.string.intro_lock_text))
        Text(stringResource(R.string.intro_lock_note), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
        if (failed) {
            Text(
                text = stringResource(R.string.lock_enable_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sheet, SheetShape)
                    .padding(14.dp),
            )
        }
    }
}

/**
 * Letzter Schritt: automatische Sicherung anbieten. „Ja, Ordner wählen“ führt über den bekannten Hinweis in
 * die Ordnerauswahl (wie in den Einstellungen); „Vielleicht später“ überspringt – dann erinnert Relic
 * nach einer Woche auf „Diese Woche“.
 */
@Composable
internal fun BackupStep(progress: IntroProgress, chooseFolder: (Uri) -> BackupFolderChoice, onDone: () -> Unit) {
    val colors = Relic.colors
    val choose = rememberBackupFolderChooser(choose = chooseFolder, onChosen = onDone)
    IntroPage(
        progress = progress,
        bottom = {
            PrimaryButton(stringResource(R.string.intro_backup_yes), choose)
            LaterButton(onDone)
        },
    ) {
        IntroTitle(stringResource(R.string.intro_backup_title))
        IntroText(stringResource(R.string.intro_backup_text))
        Text(stringResource(R.string.intro_backup_note), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
    }
}

/**
 * Grundgerüst jeder Einführungsseite: Punkte oben, Inhalt in der Mitte (scrollbar, z. B. bei großer Schrift),
 * Knöpfe unten.
 */
@Composable
internal fun IntroPage(
    progress: IntroProgress,
    bottom: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Relic.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
    ) {
        StepDots(progress)
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = if (centered) {
                Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            } else {
                Arrangement.spacedBy(14.dp)
            },
            content = content,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = bottom)
    }
}

/** Fortschritt als Punkte; der aktuelle Schritt ist ein kurzer Strich. */
@Composable
fun StepDots(progress: IntroProgress, modifier: Modifier = Modifier) {
    val colors = Relic.colors
    val description = stringResource(R.string.intro_step, progress.index + 1, progress.total)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(progress.total) { index ->
            val current = index == progress.index
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (current) 18.dp else 6.dp)
                    .clip(if (current) RoundedCornerShape(3.dp) else CircleShape)
                    .background(if (current) colors.ink else colors.rule),
            )
        }
    }
}

/** Große Überschrift einer Einführungsseite (Fraunces). */
@Composable
fun IntroTitle(text: String) {
    Text(text, style = MaterialTheme.typography.displaySmall, color = Relic.colors.ink)
}

@Composable
fun IntroText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Relic.colors.inkSoft)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = Relic.colors
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

/** „Vielleicht später“ */
@Composable
private fun LaterButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.intro_later), color = Relic.colors.ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun WelcomePreview() {
    RelicTheme { WelcomeStep(IntroProgress(0, 4), onNext = {}) }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ReminderPreview() {
    RelicTheme { ReminderStep(IntroProgress(2, 4), onAccept = {}, onSkip = {}) }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun LockPreview() {
    RelicTheme { LockStep(IntroProgress(3, 4), authenticator = { it(false) }, onEnabled = {}, onSkip = {}) }
}

@Preview(showBackground = true)
@Composable
private fun DotsPreview() {
    RelicTheme { Box(Modifier.padding(16.dp).size(80.dp, 20.dp)) { StepDots(IntroProgress(1, 4)) } }
}
