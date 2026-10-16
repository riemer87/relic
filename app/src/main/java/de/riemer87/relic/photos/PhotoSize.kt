package de.riemer87.relic.photos

import kotlin.math.max
import kotlin.math.roundToInt

/** Größenberechnung für Fotos, ohne Android – auf dem PC testbar. */
object PhotoSize {

    /** Lange Kante gespeicherter Fotos in Pixeln. */
    const val MAX_EDGE = 2560

    /** JPEG-Qualität gespeicherter Fotos (0–100). */
    const val JPEG_QUALITY = 85

    /** Verkleinert proportional, bis die lange Kante höchstens [maxEdge] ist. Vergrößert nie. */
    fun fit(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longEdge = max(width, height)
        if (longEdge <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longEdge
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /**
     * Größter Zweierpotenz-Teiler, mit dem das Bild beim Laden noch mindestens [minEdge] lange Kante hat.
     * Spart Speicher: ein 12-Megapixel-Foto wird gar nicht erst in voller Größe geladen.
     */
    fun sampleSize(width: Int, height: Int, minEdge: Int): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / (sample * 2) >= minEdge) sample *= 2
        return sample
    }
}
