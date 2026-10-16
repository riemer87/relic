package de.riemer87.relic.lock

import java.time.Duration
import java.time.Instant

/** Wann die App-Sperre greift – ohne Android, auf dem PC testbar. */
object LockPolicy {

    /** So lange darf Relic im Hintergrund sein, ohne dass man neu entsperren muss. */
    val GRACE: Duration = Duration.ofMinutes(1)

    /**
     * @param enabled App-Sperre eingeschaltet
     * @param backgroundSince seit wann die App im Hintergrund war; `null` beim frischen Start
     * @param now jetzt
     */
    fun shouldLock(enabled: Boolean, backgroundSince: Instant?, now: Instant): Boolean = when {
        !enabled -> false
        backgroundSince == null -> true // frisch gestartet
        else -> Duration.between(backgroundSince, now) > GRACE
    }
}
