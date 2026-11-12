package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import it.fabriziodavi.vivovideo.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Persona (o soggetto) scontornata nelle foto dei collage, per la scritta "dietro la persona" delle copertine.
 * Una maschera per foto (trasparenza = sfondo), grande come l'anteprima della foto e allineata a lei.
 */
object SubjectMasks {
    private val masks = HashMap<String, Bitmap>()
    private val failed = HashSet<String>()

    fun get(uri: String): Bitmap? = synchronized(masks) { masks[uri] }

    fun has(uri: String): Boolean = synchronized(masks) { uri in masks || uri in failed }

    fun put(uri: String, mask: Bitmap) = synchronized(masks) { masks[uri] = mask }

    /** Ricampiona una maschera [mw]×[mh] a [w]×[h] (bilineare). */
    fun resample(src: FloatArray, mw: Int, mh: Int, w: Int, h: Int): FloatArray = FloatArray(w * h) { i ->
        val x = (i % w + 0.5f) * mw / w - 0.5f
        val y = (i / w + 0.5f) * mh / h - 0.5f
        val x0 = x.toInt().coerceIn(0, mw - 1); val y0 = y.toInt().coerceIn(0, mh - 1)
        val x1 = (x0 + 1).coerceAtMost(mw - 1); val y1 = (y0 + 1).coerceAtMost(mh - 1)
        val fx = (x - x0).coerceIn(0f, 1f); val fy = (y - y0).coerceIn(0f, 1f)
        val a = src[y0 * mw + x0] + (src[y0 * mw + x1] - src[y0 * mw + x0]) * fx
        val b = src[y1 * mw + x0] + (src[y1 * mw + x1] - src[y1 * mw + x0]) * fx
        (a + (b - a) * fy).coerceIn(0f, 1f)
    }

    /** Maschera da valori 0..1 ([w]×[h]): bianco opaco dove c'è il soggetto. */
    fun fromConfidence(conf: FloatArray, w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            // Bordo morbido ma deciso: sotto 0,35 è sfondo, sopra 0,65 è soggetto.
            val a = ((conf[i] - 0.35f) / 0.3f).coerceIn(0f, 1f)
            Color.argb((a * 255).toInt(), 255, 255, 255)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Scontorna la foto [bmp] (quella di [uri]): prima con il modello preciso di Google (qualsiasi soggetto),
     * se non c'è con quello per le persone. Restituisce false se non riesce.
     */
    suspend fun compute(ctx: Context, uri: String, bmp: Bitmap): Boolean = withContext(Dispatchers.Default) {
        if (has(uri)) return@withContext get(uri) != null
        val k = 1024f / max(bmp.width, bmp.height)
        val src = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(2), (bmp.height * k).toInt().coerceAtLeast(2), true) else bmp
        val sw = src.width; val sh = src.height
        val img = InputImage.fromBitmap(src, 0)
        val conf: FloatArray? = runCatching {
            val seg = PersonMasks.preciseSegmenter(ctx) {}
            if (seg != null) try {
                val r = Tasks.await(seg.process(img), 30, TimeUnit.SECONDS)
                val fb = r.foregroundConfidenceMask ?: return@runCatching null
                fb.rewind(); FloatArray(src.width * src.height) { if (fb.hasRemaining()) fb.get() else 0f }
            } finally { seg.close() } else {
                val selfie = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build())
                try {
                    val m = Tasks.await(selfie.process(img), 20, TimeUnit.SECONDS)
                    val buf = m.buffer.asFloatBuffer()
                    resample(FloatArray(m.width * m.height) { buf.get(it) }, m.width, m.height, sw, sh)
                } finally { selfie.close() }
            }
        }.onFailure { Diary.log("Scontorno della foto non riuscito: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
        if (src !== bmp) src.recycle()
        if (conf == null || conf.none { it > 0.5f }) { synchronized(masks) { failed += uri }; return@withContext false }
        put(uri, fromConfidence(conf, sw, sh))
        true
    }
}
