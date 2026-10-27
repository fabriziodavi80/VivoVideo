package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaCodecInfo
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
import it.fabriziodavi.vivovideo.model.StabMath
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
    /** Le foto si guardano più grandi: la differenza fra due scatti di una raffica (uno appena mosso) è fine. */
    private const val PHOTO_SIZE = 1024
    private const val MAX_SAMPLES = 40
    /** Video fino a 5 minuti: si leggono tutti i fotogrammi (4 istanti al secondo, tremolio su ogni fotogramma). */
    private const val DENSE_MAX_US = 5 * 60_000_000L
    private const val KEEP_HZ = 4
    /** Larghezza per misurare il tremolio fra un fotogramma e il successivo. */
    private const val SHAKE_W = 160

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
    private fun cacheFile(ctx: Context, uri: String, durUs: Long) = File(File(ctx.cacheDir, "momenti").apply { mkdirs() }, sha("v3|$uri|$durUs") + ".json")

    fun cached(ctx: Context, uri: String, durUs: Long): MomentScan? = runCatching {
        cacheFile(ctx, uri, durUs).takeIf { it.exists() }?.readText()?.let { Project.JSON.decodeFromString(MomentScan.serializer(), it) }
    }.getOrNull()

    /** Analisi di una foto o di un video (lungo [durUs]); usa quella già fatta se c'è. */
    suspend fun scan(ctx: Context, uri: String, isPhoto: Boolean, durUs: Long, onProgress: (Float) -> Unit = {}): MomentScan = withContext(Dispatchers.Default) {
        cached(ctx, uri, durUs)?.let { return@withContext it }
        val samples = if (isPhoto) {
            val b = runCatching { decodePhoto(ctx, Uri.parse(uri)) }.getOrNull()
            listOfNotNull(b?.let { sample(it, 0L, null).first.also { _ -> b.recycle() } })
        } else if (durUs in 1..DENSE_MAX_US) {
            runCatching { scanDense(ctx, uri, durUs, onProgress) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()?.takeIf { it.size >= 4 } ?: scanVideo(ctx, uri, durUs)
        } else scanVideo(ctx, uri, durUs)
        val s = MomentScan(uri, durUs, samples)
        runCatching { cacheFile(ctx, uri, durUs).writeText(Project.JSON.encodeToString(MomentScan.serializer(), s)) }
        s
    }

    private fun decodePhoto(ctx: Context, uri: Uri): Bitmap {
        val src = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { d, info, _ ->
            val w = info.size.width; val h = info.size.height
            val k = minOf(1f, PHOTO_SIZE.toFloat() / maxOf(w, h))
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

    /** Nitidezza dentro il riquadro [box] (il volto): conta più dello sfondo, è lì che si guarda. */
    private fun regionSharp(g: FloatArray, w: Int, h: Int, box: android.graphics.Rect): Float {
        val x0 = box.left.coerceIn(0, w - 1); val x1 = box.right.coerceIn(x0 + 1, w)
        val y0 = box.top.coerceIn(0, h - 1); val y1 = box.bottom.coerceIn(y0 + 1, h)
        val rw = x1 - x0; val rh = y1 - y0
        if (rw < 8 || rh < 8) return 0f
        val sub = FloatArray(rw * rh) { i -> g[(y0 + i / rw) * w + x0 + i % rw] }
        return ImageStats.measure(sub, rw, rh).sharp
    }

    /**
     * Lettura completa del video (decodificatore del telefono, solo la luminanza): a ogni fotogramma si misura quanto
     * l'immagine trema rispetto al precedente; 4 volte al secondo nitidezza, luce, movimento e soggetto; 2 volte
     * al secondo i volti. Prima si guardavano solo i fotogrammi chiave (uno al secondo, e sempre i più nitidi
     * della compressione): il mosso e il tremolio fra uno e l'altro sfuggivano.
     */
    private suspend fun scanDense(ctx: Context, uri: String, durUs: Long, onProgress: (Float) -> Unit): List<MomentSample> {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, Uri.parse(uri), null)
        val vt = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ?: run { ex.release(); error("niente video") }
        val fmt = ex.getTrackFormat(vt)
        ex.selectTrack(vt)
        val rotation = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else runCatching {
            val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(uri))
            try { r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0 } finally { r.release() }
        }.getOrDefault(0)
        var mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrElse {
            mime = MediaFormat.MIMETYPE_VIDEO_HEVC; fmt.setString(MediaFormat.KEY_MIME, mime); MediaCodec.createDecoderByType(mime)
        }
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val out = ArrayList<MomentSample>()
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var prevSmall: ByteArray? = null; var prevThumb: FloatArray? = null
        var lastDx = 0f; var lastDy = 0f
        var jitter = 0f; var jn = 0
        var lastKept = Long.MIN_VALUE
        var kept = 0
        var lastFace: MomentSample? = null
        try {
            while (true) {
                coroutineContext.ensureActive()
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5_000)
                    if (ii >= 0) {
                        val t = ex.sampleTime
                        if (t < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0); codec.queueInputBuffer(ii, 0, n.coerceAtLeast(0), t, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5_000)
                if (oi < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val t = info.presentationTimeUs
                val img = if (info.size > 0) codec.getOutputImage(oi) else null
                if (img != null) {
                    val plane = img.planes[0]
                    val w = img.width; val h = img.height
                    val buf = plane.buffer; val rs = plane.rowStride; val ps = plane.pixelStride
                    val hi = if (ps >= 2) 1 else 0 // 10 bit: byte alto
                    fun lumaAt(x: Int, y: Int) = buf.get(y * rs + x * ps + hi).toInt() and 255
                    // Tremolio: spostamento fra questo fotogramma e il precedente; conta quanto cambia da uno all'altro
                    // (una panoramica regolare va bene, gli scossoni no).
                    val sw = SHAKE_W; val sh = (SHAKE_W * h / w).coerceAtLeast(16) / 2 * 2
                    val small = ByteArray(sw * sh) { i -> lumaAt((i % sw) * w / sw, (i / sw) * h / sh).toByte() }
                    prevSmall?.let { p ->
                        val m = StabMath.motion(p, small, sw, sh, range = 12)
                        jitter += abs(m.dx - lastDx) + abs(m.dy - lastDy); jn++
                        lastDx = m.dx; lastDy = m.dy
                    }
                    prevSmall = small
                    if (lastKept == Long.MIN_VALUE || t - lastKept >= 1_000_000L / KEEP_HZ - 3_000) {
                        val gw = SIZE; val gh = (SIZE * h / w).coerceAtLeast(16)
                        var g = FloatArray(gw * gh) { i -> lumaAt((i % gw) * w / gw, (i / gw) * h / gh).toFloat() }
                        img.close()
                        // In verticale come lo vede chi guarda (posizione del soggetto e volti).
                        val (rg, rw, rh) = rotateGray(g, gw, gh, rotation)
                        g = rg
                        val f = ImageStats.measure(g, rw, rh)
                        val thumb = ImageStats.thumb(g, rw, rh, 64, 36)
                        val motion = prevThumb?.let { p -> (thumb.indices.sumOf { abs(thumb[it] - p[it]).toDouble() } / thumb.size / 255.0 * 5).toFloat().coerceIn(0f, 1f) } ?: 0f
                        prevThumb = thumb
                        val shake = if (jn > 0) jitter / jn / sw else 0f
                        jitter = 0f; jn = 0
                        var s = MomentSample(t, f.sharp, f.luma, f.clipped, motion, subjectX = f.cx, subjectY = f.cy, hasSubject = f.spread < 0.22f, shake = shake)
                        if (kept % 2 == 0) {
                            val bmp = Bitmap.createBitmap(IntArray(rw * rh) { i -> val v = g[i].toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }, rw, rh, Bitmap.Config.ARGB_8888)
                            s = withFaces(s, bmp, g, rw, rh)
                            bmp.recycle()
                            lastFace = s
                        } else lastFace?.let { lf ->
                            // Volti dell'istante prima (mezzo secondo fa).
                            s = s.copy(faces = lf.faces, smile = lf.smile, eyes = lf.eyes, faceSize = lf.faceSize, faceSharp = lf.faceSharp,
                                subjectX = if (lf.faces > 0) lf.subjectX else s.subjectX, subjectY = if (lf.faces > 0) lf.subjectY else s.subjectY,
                                hasSubject = s.hasSubject || lf.faces > 0)
                        }
                        out += s
                        lastKept = t; kept++
                        onProgress((t.toFloat() / durUs.coerceAtLeast(1)).coerceIn(0f, 1f))
                    } else img.close()
                }
                codec.releaseOutputBuffer(oi, false)
                if (eos) break
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        return out
    }

    /** Ruota un'immagine in grigio di [deg] gradi (0, 90, 180, 270) in senso orario. */
    internal fun rotateGray(g: FloatArray, w: Int, h: Int, deg: Int): Triple<FloatArray, Int, Int> = when (((deg % 360) + 360) % 360) {
        90 -> Triple(FloatArray(w * h) { i -> val x = i % h; val y = i / h; g[(h - 1 - x) * w + y] }, h, w)
        180 -> Triple(FloatArray(w * h) { i -> g[w * h - 1 - i] }, w, h)
        270 -> Triple(FloatArray(w * h) { i -> val x = i % h; val y = i / h; g[x * w + (w - 1 - y)] }, h, w)
        else -> Triple(g, w, h)
    }

    /** Volti nell'immagine [b] (grigio [g] per la nitidezza del volto). */
    private fun withFaces(s0: MomentSample, b: Bitmap, g: FloatArray, w: Int, h: Int): MomentSample {
        var s = s0
        runCatching { Tasks.await(faces.process(InputImage.fromBitmap(b, 0)), 5, TimeUnit.SECONDS) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { list ->
            val big = list.maxBy { it.boundingBox.width() }
            var sx = 0f; var sy = 0f; var sw = 0f
            list.forEach { fc -> val k = fc.boundingBox.width().toFloat(); sx += fc.boundingBox.exactCenterX() * k; sy += fc.boundingBox.exactCenterY() * k; sw += k }
            s = s.copy(
                faces = list.size, smile = big.smilingProbability ?: 0.5f,
                eyes = listOfNotNull(big.leftEyeOpenProbability, big.rightEyeOpenProbability).let { if (it.isEmpty()) 0.5f else it.average().toFloat() },
                faceSize = big.boundingBox.width().toFloat() / w, faceSharp = regionSharp(g, w, h, big.boundingBox),
                subjectX = (sx / sw / w).coerceIn(0f, 1f), subjectY = (sy / sw / h).coerceIn(0f, 1f), hasSubject = true,
            )
        }
        return s
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
            val faceSharp = regionSharp(g, w, h, big.boundingBox)
            var sx = 0f; var sy = 0f; var sw = 0f
            list.forEach { fc -> val k = fc.boundingBox.width().toFloat(); sx += fc.boundingBox.exactCenterX() * k; sy += fc.boundingBox.exactCenterY() * k; sw += k }
            s = s.copy(
                faces = list.size, smile = big.smilingProbability ?: 0.5f,
                eyes = listOfNotNull(big.leftEyeOpenProbability, big.rightEyeOpenProbability).let { if (it.isEmpty()) 0.5f else it.average().toFloat() },
                faceSize = big.boundingBox.width().toFloat() / w, faceSharp = faceSharp,
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
