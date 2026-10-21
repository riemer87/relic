package de.riemer87.relic.ui.photos

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.min

/**
 * Rechenregeln der Vollbild-Ansicht (Zoomen, Verschieben, Wegwischen), ohne Android – auf dem PC testbar.
 *
 * Verschiebungen werden von der Bildschirmmitte aus gemessen: Bei `offset = 0` liegt die Fotomitte
 * genau in der Mitte, und vergrößert wird um die Mitte herum.
 */
object ZoomMath {

    const val MIN_SCALE = 1f
    const val MAX_SCALE = 5f

    /** Zoomstufe nach einem Doppeltippen. */
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Wie weit (Anteil der Bildschirmhöhe) man ziehen muss, damit sich die Ansicht schließt. */
    const val DISMISS_DISTANCE_FRACTION = 0.18f

    /** Ab dieser Geschwindigkeit (Pixel pro Sekunde) genügt auch ein kurzer, schneller Wisch. */
    const val DISMISS_VELOCITY = 1800f

    fun clampScale(scale: Float) = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** So groß erscheint das Foto ungezoomt: ganz sichtbar, mittig, Seitenverhältnis bleibt (wie ContentScale.Fit). */
    fun fittedSize(container: Size, image: Size): Size {
        if (image.width <= 0f || image.height <= 0f) return container
        val factor = min(container.width / image.width, container.height / image.height)
        return Size(image.width * factor, image.height * factor)
    }

    /** Wie weit das vergrößerte Foto höchstens aus der Mitte rücken darf, ohne dass ein leerer Rand entsteht. */
    fun maxOffset(container: Size, fitted: Size, scale: Float) = Offset(
        x = ((fitted.width * scale - container.width) / 2f).coerceAtLeast(0f),
        y = ((fitted.height * scale - container.height) / 2f).coerceAtLeast(0f),
    )

    fun clampOffset(offset: Offset, max: Offset) = Offset(
        x = offset.x.coerceIn(-max.x, max.x),
        y = offset.y.coerceIn(-max.y, max.y),
    )

    /**
     * Neue Verschiebung, wenn von [oldScale] auf [newScale] gezoomt wird und die Stelle unter
     * [focus] (von der Mitte aus gemessen) dabei unter den Fingern bleiben soll.
     */
    fun zoomAround(focus: Offset, offset: Offset, oldScale: Float, newScale: Float): Offset =
        focus - (focus - offset) * (newScale / oldScale)

    /**
     * Bewegt ein Wisch um [pan] das vergrößerte Foto noch? Wenn nicht (Foto steht schon am Rand),
     * darf die Ansicht stattdessen zum nächsten Foto blättern.
     */
    fun panMovesImage(offset: Offset, pan: Offset, max: Offset): Boolean {
        val moved = clampOffset(offset + pan, max) - offset
        return if (abs(pan.x) >= abs(pan.y)) abs(moved.x) > 0.5f else abs(moved.y) > 0.5f
    }

    /** Schließt das Loslassen nach einem Zug um [dragY] (mit Tempo [velocityY]) die Ansicht? */
    fun shouldDismiss(dragY: Float, velocityY: Float, height: Float): Boolean {
        if (abs(dragY) >= height * DISMISS_DISTANCE_FRACTION) return true
        // Schneller Wisch zählt nur in die Richtung, in die das Foto auch schon gezogen wurde.
        return abs(velocityY) >= DISMISS_VELOCITY && dragY * velocityY > 0f
    }

    /** Deckkraft des schwarzen Hintergrunds während des Wegziehens: wird mit dem Weg durchsichtiger. */
    fun backgroundAlpha(dragY: Float, height: Float): Float {
        if (height <= 0f) return 1f
        return (1f - abs(dragY) / (height * 0.5f)).coerceIn(0.2f, 1f)
    }
}
