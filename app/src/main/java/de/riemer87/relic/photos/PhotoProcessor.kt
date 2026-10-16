// Die in Android eingebaute ExifInterface reicht ab Android 8 für Datum, Drehung und das Weglassen
// des Standorts (siehe PhotoProcessorTest); die Zusatzbibliothek androidx.exifinterface ist nicht nötig.
@file:Suppress("ExifInterface")

package de.riemer87.relic.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Bereitet ein ausgewähltes Foto für das Tagebuch auf:
 * JPEG, lange Kante max. 2560 px, Qualität 85, richtig herum gedreht,
 * Aufnahmedatum übernommen, **Standort und alle anderen Metadaten entfernt**.
 *
 * Das Ergebnis liegt erst im Zwischenspeicher der App und wandert beim Speichern
 * des Eintrags in den Tagebuch-Ordner.
 */
class PhotoProcessor(context: Context) {

    private val resolver = context.contentResolver
    private val pendingDir = File(context.cacheDir, "pending-photos")

    /** Liefert das fertige JPEG, oder `null`, wenn das Foto nicht gelesen werden kann. */
    fun prepare(uri: Uri): File? {
        val exif = readExif(uri)
        val bitmap = try {
            decode(uri, exif)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null // z. B. unbekanntes Format oder zu wenig Speicher beim Dekodieren
        } ?: return null

        return try {
            if (!pendingDir.isDirectory) pendingDir.mkdirs()
            val out = File(pendingDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoSize.JPEG_QUALITY, it) }
            // Neues JPEG hat keinerlei Metadaten. Nur das Datum kommt zurück – kein Standort.
            exif?.let { copyCaptureDate(it, out) }
            out
        } catch (e: IOException) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Alte Zwischendateien entfernen (z. B. nach einem Absturz). */
    fun clearPending() {
        pendingDir.listFiles()?.forEach { it.delete() }
    }

    private fun readExif(uri: Uri): ExifInterface? = try {
        resolver.openInputStream(uri)?.use { ExifInterface(it) }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun decode(uri: Uri, exif: ExifInterface?): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(uri) else decodeLegacy(uri, exif)

    /** Android 9+: kann auch HEIC und dreht automatisch richtig herum. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val (width, height) = PhotoSize.fit(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Android 8: von Hand verkleinern und drehen. HEIC kann Android 8 nicht lesen. */
    private fun decodeLegacy(uri: Uri, exif: ExifInterface?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoSize.sampleSize(bounds.outWidth, bounds.outHeight, PhotoSize.MAX_EDGE)
        }
        val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val (width, height) = PhotoSize.fit(sampled.width, sampled.height)
        val scaled = if (width != sampled.width) {
            sampled.scale(width, height).also { sampled.recycle() }
        } else {
            sampled
        }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation) ?: return scaled
        return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true).also {
            if (it != scaled) scaled.recycle()
        }
    }

    private fun orientationMatrix(orientation: Int): Matrix? = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            else -> return null
        }
    }

    private fun copyCaptureDate(source: ExifInterface, target: File) {
        val values = DATE_TAGS.mapNotNull { tag -> source.getAttribute(tag)?.let { tag to it } }
        if (values.isEmpty()) return
        try {
            ExifInterface(target.absolutePath).apply {
                values.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
        } catch (e: IOException) {
            // Ohne Datum ist das Foto trotzdem in Ordnung.
        }
    }

    private companion object {
        /** Nur diese Angaben werden übernommen. Alles andere (Standort, Kamera, …) fällt weg. */
        val DATE_TAGS = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME,
            "OffsetTimeOriginal",
            "OffsetTimeDigitized",
            "OffsetTime",
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        )
    }
}
