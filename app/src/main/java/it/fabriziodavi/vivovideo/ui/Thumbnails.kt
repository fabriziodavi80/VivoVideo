package it.fabriziodavi.vivovideo.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
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
                val r = retrievers.getOrPut(uri) {
                    MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(uri)) }
                }
                val frame = runCatching {
                    r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, heightPx * 2, heightPx)
                }.getOrNull() ?: return@withLock null
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
