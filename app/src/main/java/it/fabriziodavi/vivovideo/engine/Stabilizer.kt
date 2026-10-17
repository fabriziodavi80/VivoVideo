package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.effect.MatrixTransformation
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.StabMath
import it.fabriziodavi.vivovideo.model.Stabilization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Stabilizzazione: misura quanto si sposta l'immagine fra un fotogramma e l'altro (versione piccola in grigio),
 * poi l'effetto sposta e ingrandisce leggermente ogni fotogramma per levigare il movimento.
 */
object Stabilizer {
    private const val AW = 192
    const val RATE = 30

    /** Analizza il tratto tagliato di [clip]; [onProgress] 0…1. */
    suspend fun analyze(ctx: Context, clip: Clip, strength: Float, onProgress: (Float) -> Unit): Stabilization = withContext(Dispatchers.Default) {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, Uri.parse(clip.uri), null)
        val vt = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ?: error("Nel video non c'è la traccia video.")
        val fmt = ex.getTrackFormat(vt)
        ex.selectTrack(vt)
        val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else runCatching {
            val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(clip.uri))
            try { r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0 } finally { r.release() }
        }.getOrDefault(0)
        var mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrElse {
            // Dolby Vision dei telefoni: si legge la base HEVC.
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC; fmt.setString(MediaFormat.KEY_MIME, mime); MediaCodec.createDecoderByType(mime)
        }
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec.configure(fmt, null, null, 0)
        codec.start()
        ex.seekTo(clip.trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val times = ArrayList<Long>(); val px = ArrayList<Float>(); val py = ArrayList<Float>()
        var prev: ByteArray? = null
        var aw = 0; var ah = 0
        var accX = 0f; var accY = 0f
        var lastKept = Long.MIN_VALUE
        val info = MediaCodec.BufferInfo()
        var inDone = false
        try {
            while (true) {
                coroutineContext.ensureActive()
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val t = ex.sampleTime
                        if (t < 0 || t > clip.trimEndUs + 100_000) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true
                        } else {
                            val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                            codec.queueInputBuffer(ii, 0, n.coerceAtLeast(0), t, 0); ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5_000)
                if (oi < 0) continue
                val t = info.presentationTimeUs
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val keep = info.size > 0 && t >= clip.trimStartUs - 20_000 && t <= clip.trimEndUs + 20_000 &&
                    (lastKept == Long.MIN_VALUE || t - lastKept >= 1_000_000L / RATE - 3_000)
                if (keep) {
                    val img = codec.getOutputImage(oi)
                    if (img != null) {
                        val plane = img.planes[0]
                        val w = img.width; val h = img.height
                        if (aw == 0) { aw = AW; ah = (AW * h / w).coerceAtLeast(16) / 2 * 2 }
                        // Lettura di Y a bassa risoluzione (2×2 punti per pixel); con 10 bit si prende il byte alto.
                        val buf = plane.buffer; val rs = plane.rowStride; val ps = plane.pixelStride
                        val hi = if (ps >= 2) 1 else 0
                        val g = ByteArray(aw * ah)
                        for (y in 0 until ah) {
                            val sy0 = (y * h / ah); val sy1 = minOf(h - 1, sy0 + h / ah / 2)
                            for (x in 0 until aw) {
                                val sx0 = (x * w / aw); val sx1 = minOf(w - 1, sx0 + w / aw / 2)
                                val v = (buf.get(sy0 * rs + sx0 * ps + hi).toInt() and 255) + (buf.get(sy0 * rs + sx1 * ps + hi).toInt() and 255) +
                                    (buf.get(sy1 * rs + sx0 * ps + hi).toInt() and 255) + (buf.get(sy1 * rs + sx1 * ps + hi).toInt() and 255)
                                g[y * aw + x] = (v / 4).toByte()
                            }
                        }
                        img.close()
                        prev?.let { p ->
                            val (dx, dy) = StabMath.shift(p, g, aw, ah)
                            // In frazioni dell'immagine come la vede chi guarda.
                            val (rx, ry) = StabMath.rotate(dx.toFloat() / aw, dy.toFloat() / ah, rotation)
                            accX += rx; accY += ry
                        }
                        prev = g
                        times += t; px += accX; py += accY
                        lastKept = t
                        onProgress(((t - clip.trimStartUs).toFloat() / clip.trimmedUs.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }
                }
                codec.releaseOutputBuffer(oi, false)
                if (eos) break
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        check(times.size >= 2) { "Non sono riuscito a leggere i fotogrammi del video." }
        // Ricampiona a passo fisso dall'inizio del taglio.
        val n = ((clip.trimmedUs * RATE) / 1_000_000L).toInt().coerceAtLeast(2) + 1
        val dx = ArrayList<Float>(n); val dy = ArrayList<Float>(n)
        var k = 0
        for (i in 0 until n) {
            val t = clip.trimStartUs + i * 1_000_000L / RATE
            while (k < times.size - 2 && times[k + 1] < t) k++
            val t0 = times[k]; val t1 = times[k + 1]
            val u = ((t - t0).toFloat() / (t1 - t0).coerceAtLeast(1)).coerceIn(0f, 1f)
            dx += px[k] + (px[k + 1] - px[k]) * u
            dy += py[k] + (py[k + 1] - py[k]) * u
        }
        onProgress(1f)
        Stabilization(strength = strength, rateHz = RATE, fromUs = clip.trimStartUs, dx = dx, dy = dy)
    }
}

/** Sposta e ingrandisce ogni fotogramma per levigare il movimento (prima dell'inquadratura). */
class StabilizeTransformation(private val clip: Clip, private val startUs: Long) : MatrixTransformation {
    private val plan = StabMath.plan(clip.stab ?: Stabilization())

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val src = clip.trimStartUs + clip.speedCurve().sourceTimeAt(localTimeUs(presentationTimeUs, startUs))
        val (cx, cy) = plan.at(src)
        val z = plan.zoom
        return Matrix().apply {
            postScale(z, z)
            postTranslate(2f * cx * z, -2f * cy * z)
        }
    }
}
