package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import it.fabriziodavi.vivovideo.model.MomentSample
import it.fabriziodavi.vivovideo.model.MomentScan
import it.fabriziodavi.vivovideo.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Guarda foto e video come farebbe un montatore: nitidezza, luce, movimento, volti (sorrisi, occhi aperti)
 * e dove sta il soggetto. Tutto sul telefono. I risultati si ricordano (cartella momenti).
 */
object SmartAnalyzer {
    private const val SIZE = 480
    private const val MAX_SAMPLES = 40

    private val faces: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.08f)
                .build()
        )
    }

    private fun sha(text: String) = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    private fun cacheFile(ctx: Context, uri: String, durUs: Long) = File(File(ctx.cacheDir, "momenti").apply { mkdirs() }, sha("v2|$uri|$durUs") + ".json")

    fun cached(ctx: Context, uri: String, durUs: Long): MomentScan? = runCatching {
        cacheFile(ctx, uri, durUs).takeIf { it.exists() }?.readText()?.let { Project.JSON.decodeFromString(MomentScan.serializer(), it) }
    }.getOrNull()

    /** Analisi di una foto o di un video (lungo [durUs]); usa quella già fatta se c'è. */
    suspend fun scan(ctx: Context, uri: String, isPhoto: Boolean, durUs: Long): MomentScan = withContext(Dispatchers.Default) {
        cached(ctx, uri, durUs)?.let { return@withContext it }
        val samples = if (isPhoto) {
            val b = runCatching { decodePhoto(ctx, Uri.parse(uri)) }.getOrNull()
            listOfNotNull(b?.let { sample(it, 0L, null).first.also { _ -> b.recycle() } })
        } else scanVideo(ctx, uri, durUs)
        val s = MomentScan(uri, durUs, samples)
        runCatching { cacheFile(ctx, uri, durUs).writeText(Project.JSON.encodeToString(MomentScan.serializer(), s)) }
        s
    }

    private fun decodePhoto(ctx: Context, uri: Uri): Bitmap {
        val src = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val w = info.size.width; val h = info.size.height
            val k = minOf(1f, SIZE.toFloat() / maxOf(w, h))
            d.setTargetSize((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    /** Istanti da guardare: i fotogrammi chiave (si leggono subito), al massimo [MAX_SAMPLES] distribuiti. */
    private fun syncTimes(ctx: Context, uri: String, durUs: Long): List<Long> {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, Uri.parse(uri), null)
            val vt = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return emptyList()
            ex.selectTrack(vt)
            val out = ArrayList<Long>()
            while (true) {
                val t = ex.sampleTime
                if (t < 0) break
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) out += t
                ex.advance()
            }
            return out.sorted()
        } catch (_: Exception) { return emptyList() } finally { ex.release() }
    }

    private suspend fun scanVideo(ctx: Context, uri: String, durUs: Long): List<MomentSample> {
        val sync = syncTimes(ctx, uri, durUs)
        // Pochi fotogrammi chiave (video con GOP lunghi): istanti regolari, letti con precisione.
        val exact = sync.size < 6
        val raw = if (exact) (0 until 8).map { (durUs * (it + 0.5) / 8).toLong() } else sync
        val times = if (raw.size <= MAX_SAMPLES) raw else (0 until MAX_SAMPLES).map { raw[(it.toLong() * (raw.size - 1) / (MAX_SAMPLES - 1)).toInt()] }
        val r = MediaMetadataRetriever()
        val out = ArrayList<MomentSample>()
        try {
            r.setDataSource(ctx, Uri.parse(uri))
            var prev: FloatArray? = null
            for (t in times) {
                coroutineContext.ensureActive()
                val b = r.getScaledFrameAtTime(t, if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SIZE, SIZE) ?: continue
                val (s, small) = sample(b, t, prev)
                b.recycle()
                out += s; prev = small
            }
        } finally { r.release() }
        return out
    }

    /** Misure di un'immagine (già piccola) e la sua versione 64×36 in grigio per il movimento. */
    internal fun sample(b: Bitmap, tUs: Long, prev: FloatArray?): Pair<MomentSample, FloatArray> {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val g = FloatArray(w * h) { i -> val p = px[i]; 0.2126f * ((p shr 16) and 255) + 0.7152f * ((p shr 8) and 255) + 0.0722f * (p and 255) }
        val f = ImageStats.measure(g, w, h)
        val small = ImageStats.thumb(g, w, h, 64, 36)
        val motion = prev?.let { p -> (small.indices.sumOf { abs(small[it] - p[it]).toDouble() } / small.size / 255.0 * 5).toFloat().coerceIn(0f, 1f) } ?: 0f
        var s = MomentSample(tUs, f.sharp, f.luma, f.clipped, motion, subjectX = f.cx, subjectY = f.cy, hasSubject = f.spread < 0.22f)
        runCatching { Tasks.await(faces.process(InputImage.fromBitmap(b, 0)), 5, TimeUnit.SECONDS) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
            val big = list.maxBy { it.boundingBox.width() }
            var sx = 0f; var sy = 0f; var sw = 0f
            list.forEach { fc -> val k = fc.boundingBox.width().toFloat(); sx += fc.boundingBox.exactCenterX() * k; sy += fc.boundingBox.exactCenterY() * k; sw += k }
            s = s.copy(
                faces = list.size, smile = big.smilingProbability ?: 0.5f,
                eyes = listOfNotNull(big.leftEyeOpenProbability, big.rightEyeOpenProbability).let { if (it.isEmpty()) 0.5f else it.average().toFloat() },
                faceSize = big.boundingBox.width().toFloat() / w,
                subjectX = (sx / sw / w).coerceIn(0f, 1f), subjectY = (sy / sw / h).coerceIn(0f, 1f), hasSubject = true,
            )
        }
        return s to small
    }
}

/** Misure su un'immagine in grigio (0…255), in Kotlin puro (provabili). */
object ImageStats {
    class Stats(val sharp: Float, val luma: Float, val clipped: Float, val cx: Float, val cy: Float, val spread: Float)

    fun measure(g: FloatArray, w: Int, h: Int): Stats {
        var sum = 0.0; var sum2 = 0.0; var n = 0; var clip = 0
        var ex = 0.0; var ey = 0.0; var ew = 0.0; var ex2 = 0.0; var ey2 = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
            sum += lap; sum2 += lap * lap; n++
            val e = (lap * lap).toDouble()
            ex += e * x; ey += e * y; ew += e; ex2 += e * x * x; ey2 += e * y * y
        }
        var lum = 0.0
        for (v in g) { lum += v; if (v < 6 || v > 250) clip++ }
        val mean = sum / n.coerceAtLeast(1)
        val varLap = (sum2 / n.coerceAtLeast(1) - mean * mean).toFloat()
        val cx = if (ew > 0) ex / ew else w / 2.0
        val cy = if (ew > 0) ey / ew else h / 2.0
        val sx = if (ew > 0) sqrt((ex2 / ew - cx * cx).coerceAtLeast(0.0)) / w else 0.5
        val sy = if (ew > 0) sqrt((ey2 / ew - cy * cy).coerceAtLeast(0.0)) / h else 0.5
        return Stats(varLap, (lum / g.size / 255).toFloat(), clip.toFloat() / g.size, (cx / w).toFloat(), (cy / h).toFloat(), ((sx + sy) / 2).toFloat())
    }

    fun thumb(g: FloatArray, w: Int, h: Int, tw: Int, th: Int): FloatArray = FloatArray(tw * th) { i ->
        val x = i % tw * w / tw; val y = i / tw * h / th
        g[y * w + x]
    }
}
