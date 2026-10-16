package de.riemer87.relic.ui.intro

import android.content.Context
import androidx.core.content.edit

/** Die Schritte der Einführung beim allerersten Start. */
enum class IntroStep { WELCOME, FOLDER, REMINDER, LOCK }

/**
 * Die Schritte in ihrer Reihenfolge. Ohne Displaysperre am Handy entfällt die App-Sperre,
 * weil es dann nichts gibt, womit man entsperren könnte.
 */
fun introSteps(lockAvailable: Boolean): List<IntroStep> =
    if (lockAvailable) IntroStep.entries else IntroStep.entries - IntroStep.LOCK

/** Der Schritt nach [current], oder `null`, wenn die Einführung danach vorbei ist. */
fun nextIntroStep(current: IntroStep, lockAvailable: Boolean): IntroStep? =
    introSteps(lockAvailable).let { it.getOrNull(it.indexOf(current) + 1) }

/**
 * Mit welchem Schritt die Einführung beginnt, oder `null`, wenn sie nicht erscheint.
 * Wer schon einen Ordner hat (auch aus der Zeit vor der Einführung) oder sie schon gesehen hat,
 * sieht sie nie wieder. Geht der Ordner später verloren, erscheint nur der Ordner-Bildschirm.
 */
fun firstIntroStep(hasFolder: Boolean, introDone: Boolean): IntroStep? =
    if (hasFolder || introDone) null else IntroStep.WELCOME

/** Merkt sich, ob die Einführung durchlaufen wurde (sobald ein Ordner gewählt ist). */
class IntroSettings(context: Context) {

    private val prefs = context.getSharedPreferences("intro", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(KEY_DONE, false)

    fun markDone() = prefs.edit { putBoolean(KEY_DONE, true) }

    private companion object {
        const val KEY_DONE = "done"
    }
}

/** Für die Punkte oben: der wievielte Schritt (ab 0) von wie vielen. */
data class IntroProgress(val index: Int, val total: Int)

fun introProgress(step: IntroStep, lockAvailable: Boolean): IntroProgress =
    introSteps(lockAvailable).let { IntroProgress(it.indexOf(step), it.size) }
