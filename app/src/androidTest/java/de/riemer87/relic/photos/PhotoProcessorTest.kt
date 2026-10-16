package de.riemer87.relic.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoProcessorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val processor = PhotoProcessor(context)
    private val workDir = File(context.cacheDir, "photo-test").apply { mkdirs() }

    @After
    fun cleanUp() {
        workDir.deleteRecursively()
        processor.clearPending()
    }

    /** Querformat-Foto, links rot und rechts blau, wie es eine Kamera mit EXIF-Angaben speichert. */
    private fun cameraPhoto(width: Int, height: Int, orientation: Int, withGps: Boolean): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        }
        val file = File(workDir, "kamera.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2025:07:14 18:30:12")
            setAttribute("OffsetTimeOriginal", "+02:00")
            setAttribute(ExifInterface.TAG_MAKE, "Testkamera")
            if (withGps) {
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "52/1,31/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "13/1,24/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            }
            saveAttributes()
        }
        return file
    }

    private fun bounds(file: File) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    @Test
    fun largePhotoIsScaledToJpegWithLongEdge2560() {
        val out = processor.prepare(Uri.fromFile(cameraPhoto(4000, 3000, ExifInterface.ORIENTATION_NORMAL, withGps = false)))!!
        val b = bounds(out)
        assertEquals("image/jpeg", b.outMimeType)
        assertEquals(2560, b.outWidth)
        assertEquals(1920, b.outHeight)
    }

    @Test
    fun locationIsRemovedButCaptureDateIsKept() {
        val out = processor.prepare(Uri.fromFile(cameraPhoto(1200, 900, ExifInterface.ORIENTATION_NORMAL, withGps = true)))!!
        val exif = ExifInterface(out.absolutePath)
        assertFalse("Standort muss weg sein", exif.getLatLong(FloatArray(2)))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("Auch andere Angaben fallen weg", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("2025:07:14 18:30:12", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("+02:00", exif.getAttribute("OffsetTimeOriginal"))
    }

    @Test
    fun rotatedPhotoIsStoredUpright() {
        // Kamera hat quer gespeichert, aber „um 90° drehen“ vermerkt: Ergebnis muss hochkant sein.
        val out = processor.prepare(Uri.fromFile(cameraPhoto(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90, withGps = false)))!!
        val b = bounds(out)
        assertEquals(1920, b.outWidth)
        assertEquals(2560, b.outHeight)

        // Nach 90° im Uhrzeigersinn liegt die rote linke Hälfte oben.
        val bitmap = BitmapFactory.decodeFile(out.absolutePath)
        val top = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
        assertTrue("oben rot", Color.red(top) > 200 && Color.blue(top) < 60)
        assertTrue("unten blau", Color.blue(bottom) > 200 && Color.red(bottom) < 60)

        // Die Drehung steckt jetzt im Bild selbst, nicht mehr in den Metadaten.
        val orientation = ExifInterface(out.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        assertTrue(orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED)
    }

    @Test
    fun smallPhotoIsNotEnlarged() {
        val out = processor.prepare(Uri.fromFile(cameraPhoto(640, 480, ExifInterface.ORIENTATION_NORMAL, withGps = false)))!!
        val b = bounds(out)
        assertEquals(640, b.outWidth)
        assertEquals(480, b.outHeight)
    }

    @Test
    fun brokenFileIsSkippedWithoutCrash() {
        val broken = File(workDir, "kaputt.jpg").apply { writeText("kein Bild") }
        assertNull(processor.prepare(Uri.fromFile(broken)))
        assertNull(processor.prepare(Uri.fromFile(File(workDir, "gibt-es-nicht.jpg"))))
    }

    @Test
    fun preparedFilesCanBeCleanedUp() {
        val out = processor.prepare(Uri.fromFile(cameraPhoto(640, 480, ExifInterface.ORIENTATION_NORMAL, withGps = false)))
        assertNotNull(out)
        processor.clearPending()
        assertTrue(!out!!.exists())
    }
}
