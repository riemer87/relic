package de.riemer87.relic.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizeTest {

    @Test
    fun `large landscape photo is scaled to 2560 on the long edge`() {
        assertEquals(2560 to 1920, PhotoSize.fit(4000, 3000))
    }

    @Test
    fun `large portrait photo is scaled to 2560 on the long edge`() {
        assertEquals(1920 to 2560, PhotoSize.fit(3000, 4000))
    }

    @Test
    fun `small photos are never enlarged`() {
        assertEquals(800 to 600, PhotoSize.fit(800, 600))
        assertEquals(2560 to 2560, PhotoSize.fit(2560, 2560))
    }

    @Test
    fun `extreme panorama keeps at least one pixel`() {
        assertEquals(2560 to 1, PhotoSize.fit(100_000, 10))
    }

    @Test
    fun `sample size keeps enough pixels`() {
        assertEquals(1, PhotoSize.sampleSize(4000, 3000, 2560))
        assertEquals(4, PhotoSize.sampleSize(4000, 3000, 1000))
        assertEquals(1, PhotoSize.sampleSize(500, 400, 1000))
        assertEquals(16, PhotoSize.sampleSize(4000, 3000, 200))
    }
}
