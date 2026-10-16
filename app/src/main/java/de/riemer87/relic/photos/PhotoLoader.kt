package de.riemer87.relic.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.riemer87.relic.journal.DayPhoto
import de.riemer87.relic.journal.JournalStore
import de.riemer87.relic.journal.NewPhoto
import de.riemer87.relic.journal.StoredPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.time.LocalDate

/**
 * Lädt Fotos passend verkleinert für die Anzeige und merkt sich die zuletzt gezeigten.
 * Fehlende oder kaputte Fotos ergeben still `null` – nie einen Absturz.
 */
class PhotoLoader(private val store: JournalStore) {

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    suspend fun load(date: LocalDate, photo: DayPhoto, maxEdgePx: Int): ImageBitmap? {
        val key = "${keyOf(date, photo)}@$maxEdgePx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = try {
                when (photo) {
                    is StoredPhoto -> store.openPhoto(date, photo.fileName)?.use { it.readBytes() }
                    is NewPhoto -> photo.file.takeIf { it.isFile }?.readBytes()
                }
            } catch (e: IOException) {
                null
            } ?: return@withContext null
            decode(bytes, maxEdgePx)?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }

    /** Nach dem Speichern eines Tages: Dateinamen können neu vergeben worden sein. */
    fun forget(date: LocalDate) {
        cache.snapshot().keys.filter { it.startsWith("$date/") }.forEach { cache.remove(it) }
    }

    private fun keyOf(date: LocalDate, photo: DayPhoto) = when (photo) {
        is StoredPhoto -> "$date/${photo.fileName}"
        is NewPhoto -> "pending/${photo.file.name}"
    }

    private fun decode(bytes: ByteArray, maxEdgePx: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                decoder.setTargetSampleSize(PhotoSize.sampleSize(info.size.width, info.size.height, maxEdgePx))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoSize.sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private companion object {
        fun cacheSizeKb() = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}

/** Wird von [de.riemer87.relic.ui.RelicApp] bereitgestellt. */
val LocalPhotoLoader = staticCompositionLocalOf<PhotoLoader?> { null }
