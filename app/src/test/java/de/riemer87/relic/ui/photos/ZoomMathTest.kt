package de.riemer87.relic.ui.photos

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    private val screen = Size(1080f, 2400f)

    @Test
    fun `zoom stays between normal size and five times`() {
        assertEquals(1f, ZoomMath.clampScale(0.3f))
        assertEquals(5f, ZoomMath.clampScale(12f))
        assertEquals(2.5f, ZoomMath.clampScale(2.5f))
    }

    @Test
    fun `landscape photo fills the width of a portrait screen`() {
        val fitted = ZoomMath.fittedSize(screen, Size(2560f, 1920f))
        assertEquals(1080f, fitted.width, 0.01f)
        assertEquals(810f, fitted.height, 0.01f)
    }

    @Test
    fun `missing image size falls back to the screen`() {
        assertEquals(screen, ZoomMath.fittedSize(screen, Size(0f, 0f)))
    }

    @Test
    fun `unzoomed photo cannot be moved`() {
        val fitted = ZoomMath.fittedSize(screen, Size(2560f, 1920f))
        assertEquals(Offset.Zero, ZoomMath.maxOffset(screen, fitted, 1f))
    }

    @Test
    fun `zoomed photo can be moved only up to its edge`() {
        val fitted = Size(1080f, 810f)
        val max = ZoomMath.maxOffset(screen, fitted, 2f)
        assertEquals(540f, max.x, 0.01f) // 2160 breit, 1080 sichtbar → je 540 nach links und rechts
        assertEquals(0f, max.y, 0.01f) // 1620 hoch passt noch auf den Schirm → kein Verschieben nach oben/unten
        assertEquals(Offset(540f, 0f), ZoomMath.clampOffset(Offset(900f, 300f), max))
    }

    @Test
    fun `the spot under the fingers stays in place while zooming`() {
        val focus = Offset(200f, -100f)
        // Ein Punkt p des Fotos liegt auf dem Schirm bei offset + p * scale.
        // Bei Zoom 1 ohne Verschiebung liegt unter den Fingern also der Fotopunkt p = focus.
        val offset = ZoomMath.zoomAround(focus, Offset.Zero, 1f, 2f)
        assertEquals(focus, offset + focus * 2f)
    }

    @Test
    fun `zooming back to normal around the center returns to the middle`() {
        assertEquals(Offset.Zero, ZoomMath.zoomAround(Offset.Zero, Offset.Zero, 2.5f, 1f))
    }

    @Test
    fun `swiping a zoomed photo moves it until the edge, then the next photo may come`() {
        val max = Offset(540f, 0f)
        assertTrue(ZoomMath.panMovesImage(Offset.Zero, Offset(-40f, 0f), max))
        assertTrue(ZoomMath.panMovesImage(Offset(-520f, 0f), Offset(-40f, 0f), max))
        assertFalse(ZoomMath.panMovesImage(Offset(-540f, 0f), Offset(-40f, 0f), max))
        // Am linken Rand in die andere Richtung geht es weiter.
        assertTrue(ZoomMath.panMovesImage(Offset(-540f, 0f), Offset(40f, 0f), max))
    }

    @Test
    fun `unzoomed photo never moves, so swiping always flips`() {
        assertFalse(ZoomMath.panMovesImage(Offset.Zero, Offset(-40f, 3f), Offset.Zero))
    }

    @Test
    fun `pulling far enough down or up closes the viewer`() {
        assertTrue(ZoomMath.shouldDismiss(500f, 0f, 2400f))
        assertTrue(ZoomMath.shouldDismiss(-500f, 0f, 2400f))
        assertFalse(ZoomMath.shouldDismiss(200f, 0f, 2400f))
    }

    @Test
    fun `a quick flick closes, but not against the pulled direction`() {
        assertTrue(ZoomMath.shouldDismiss(80f, 2500f, 2400f))
        assertFalse(ZoomMath.shouldDismiss(80f, -2500f, 2400f))
        assertFalse(ZoomMath.shouldDismiss(80f, 900f, 2400f))
    }

    @Test
    fun `background fades while pulling but never disappears completely`() {
        assertEquals(1f, ZoomMath.backgroundAlpha(0f, 2400f))
        assertEquals(0.5f, ZoomMath.backgroundAlpha(600f, 2400f), 0.001f)
        assertEquals(0.2f, ZoomMath.backgroundAlpha(5000f, 2400f))
    }
}
