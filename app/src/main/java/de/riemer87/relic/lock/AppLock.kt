package de.riemer87.relic.lock

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

/** Fingerabdruck, Gesicht oder Displaysperre des Handys – was das Handy anbietet. */
const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Fragt die Nutzerin, sich zu entsperren. Im Test durch eine Attrappe ersetzbar. */
fun interface Authenticator {
    fun authenticate(onResult: (success: Boolean) -> Unit)
}

/** Merkt sich, ob die App-Sperre eingeschaltet ist. */
class AppLockSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}

/** Kann das Handy überhaupt entsperren (Fingerabdruck, Gesicht oder Displaysperre eingerichtet)? */
fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Der Entsperr-Dialog von Android. Relic sieht nie Fingerabdruck oder PIN, nur „geklappt“ oder nicht. */
class BiometricAuthenticator(
    private val activity: FragmentActivity,
    private val title: String,
    private val subtitle: String,
) : Authenticator {
    override fun authenticate(onResult: (success: Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                // onAuthenticationFailed = ein Versuch passte nicht; der Dialog bleibt offen.
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build(),
        )
    }
}

/**
 * Zustand der App-Sperre für die ganze App. Überlebt das Drehen des Handys; wird Relic von Android
 * beendet, startet es gesperrt neu.
 */
class AppLockViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppLockSettings(application)
    private var clock: Clock = Clock.systemUTC()
    private var backgroundSince: Instant? = null

    private val _locked = MutableStateFlow(settings.enabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Erster Wechsel in den Vordergrund nach dem Start (beim Drehen bleibt dieses ViewModel bestehen). */
    private var freshStart = true

    /** Nur für Tests: eine feste Uhr. */
    internal fun useClock(testClock: Clock) {
        clock = testClock
    }

    /** App kommt in den Vordergrund. */
    fun onForeground() {
        val since = backgroundSince
        val lock = when {
            freshStart -> LockPolicy.shouldLock(settings.enabled, backgroundSince = null, now = Instant.now(clock))
            since == null -> false // nur gedreht, nicht im Hintergrund gewesen
            else -> LockPolicy.shouldLock(settings.enabled, since, Instant.now(clock))
        }
        freshStart = false
        backgroundSince = null
        if (lock) _locked.value = true
    }

    private val _enabled = MutableStateFlow(settings.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Einschalten nur nach erfolgreichem Entsperren (die Einstellungen rufen das danach auf). */
    fun setEnabled(enabled: Boolean) {
        settings.enabled = enabled
        _enabled.value = enabled
        if (!enabled) _locked.value = false
    }

    /** App geht in den Hintergrund (nicht beim bloßen Drehen). */
    fun onBackground() {
        backgroundSince = Instant.now(clock)
    }

    fun unlocked() {
        _locked.value = false
    }
}
