package it.fabriziodavi.vivovideo.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import it.fabriziodavi.vivovideo.decodePhotoThumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Miniature della timeline, in memoria. Un retriever per video, riusato. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val retrievers = HashMap<String, MediaMetadataRetriever>()
    private val lock = Mutex()

    fun cached(uri: String, timeUs: Long, heightPx: Int): Bitmap? = cache.get(key(uri, timeUs, heightPx))

    private fun key(uri: String, timeUs: Long, h: Int) = "$uri@${timeUs / 100_000}@$h"

    suspend fun get(context: Context, uri: String, timeUs: Long, heightPx: Int): Bitmap? {
        val k = key(uri, timeUs, heightPx)
        cache.get(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                cache.get(k)?.let { return@withLock it }
                val parsed = Uri.parse(uri)
                val frame = if (parsed.scheme == "file" && parsed.path?.endsWith(".jpg") == true) {
                    // Foto: si legge l'immagine.
                    runCatching { decodePhotoThumb(parsed.path!!, heightPx) }.getOrNull() ?: return@withLock null
                } else {
                    val r = retrievers[uri] ?: runCatching {
                        MediaMetadataRetriever().apply { setDataSource(context, parsed) }
                    }.getOrNull()?.also { retrievers[uri] = it } ?: return@withLock null
                    runCatching {
                        r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, heightPx * 2, heightPx)
                    }.getOrNull() ?: return@withLock null
                }
                // Ritaglio centrale quadrato-ish per riempire la tessera.
                val side = minOf(frame.width, frame.height)
                val bmp = Bitmap.createBitmap(frame, (frame.width - side) / 2, (frame.height - side) / 2, side, side)
                val scaled = Bitmap.createScaledBitmap(bmp, heightPx, heightPx, true)
                cache.put(k, scaled)
                scaled
            }
        }
    }

    fun releaseAll() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear()
    }
}
