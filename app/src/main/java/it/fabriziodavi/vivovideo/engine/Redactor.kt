package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import it.fabriziodavi.vivovideo.decodePhotoThumb
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.RBox
import it.fabriziodavi.vivovideo.model.Redact
import it.fabriziodavi.vivovideo.model.Redaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Oscuramento automatico (tutto sul telefono, con ML Kit): volti, targhe (scritte con la forma di una targa)
 * e un oggetto scelto toccandolo, seguito fotogramma per fotogramma. I riquadri si salvano in un file.
 */
object Redactor {
    const val RATE = 10
    private const val SIZE = 720

    fun dir(ctx: Context) = File(ctx.filesDir, "oscuramenti").apply { mkdirs() }

    /** Riquadri per fotogramma, letti dal file. */
    fun load(path: String): List<List<RBox>>? = runCatching {
        DataInputStream(File(path).inputStream().buffered()).use { s ->
            val n = s.readInt()
            List(n) {
                val k = s.readByte().toInt()
                List(k) { RBox(s.readFloat(), s.readFloat(), s.readFloat(), s.readFloat(), s.readBoolean()) }
            }
        }
    }.getOrNull()

    private fun save(ctx: Context, frames: List<List<RBox>>): File {
        val f = File(dir(ctx), "oscura_${System.currentTimeMillis()}.bin")
        DataOutputStream(f.outputStream().buffered()).use { o ->
            o.writeInt(frames.size)
            frames.forEach { boxes ->
                o.writeByte(boxes.size)
                boxes.forEach { b -> o.writeFloat(b.x); o.writeFloat(b.y); o.writeFloat(b.w); o.writeFloat(b.h); o.writeBoolean(b.round) }
            }
        }
        return f
    }

    private fun norm(r: Rect, w: Int, h: Int, round: Boolean = false) = RBox(
        (r.left.toFloat() / w).coerceIn(0f, 1f), (r.top.toFloat() / h).coerceIn(0f, 1f),
        (r.width().toFloat() / w).coerceIn(0f, 1f), (r.height().toFloat() / h).coerceIn(0f, 1f), round,
    )

    /** I più grandi per primi, al massimo [n]. */
    private fun cap(boxes: List<RBox>, n: Int) = boxes.sortedByDescending { it.w * it.h }.take(n)

    private class Detectors(faces: Boolean, plates: Boolean, objects: Boolean, stream: Boolean) {
        val face = if (faces) FaceDetection.getClient(
            FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setMinFaceSize(0.04f).build()
        ) else null
        val text = if (plates) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
        val obj = if (objects) ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(if (stream) ObjectDetectorOptions.STREAM_MODE else ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects().build()
        ) else null

        fun faces(img: InputImage, w: Int, h: Int): List<RBox> = face?.let { d ->
            Tasks.await(d.process(img), 10, TimeUnit.SECONDS).map { norm(it.boundingBox, w, h, round = true).grow(0.35f) }
        }.orEmpty()

        fun plates(img: InputImage, w: Int, h: Int): List<RBox> = text?.let { d ->
            val r = Tasks.await(d.process(img), 10, TimeUnit.SECONDS)
            val aspect = w.toFloat() / h
            r.textBlocks.flatMap { b -> listOf(b.text to b.boundingBox) + b.lines.map { it.text to it.boundingBox } }
                .mapNotNull { (t, box) -> box?.takeIf { Redact.looksLikePlate(t) }?.let { norm(it, w, h) } }
                .filter { Redact.plateShape(it, aspect) }
                .map { it.grow(0.3f) }
                .distinctBy { (it.cx * 50).toInt() to (it.cy * 50).toInt() }
        }.orEmpty()

        /** Oggetti trovati: id d'inseguimento (o indice) → riquadro. */
        fun objects(img: InputImage, w: Int, h: Int): Map<Int, RBox> = obj?.let { d ->
            Tasks.await(d.process(img), 10, TimeUnit.SECONDS).mapIndexed { i, o -> (o.trackingId ?: (10_000 + i)) to norm(o.boundingBox, w, h).grow(0.12f) }.toMap()
        }.orEmpty()

        fun close() { face?.close(); text?.close(); obj?.close() }
    }

    /** Oggetti in un'immagine ferma (per scegliere quale seguire). */
    suspend fun objectsIn(bmp: Bitmap): List<RBox> = withContext(Dispatchers.Default) {
        val d = Detectors(faces = false, plates = false, objects = true, stream = false)
        try { d.objects(InputImage.fromBitmap(bmp, 0), bmp.width, bmp.height).values.toList() } finally { d.close() }
    }

    /**
     * Analizza la clip secondo [r] (cosa cercare); [seedSrcUs] = tempo del file in cui è stato scelto l'oggetto.
     * Restituisce [r] con i riquadri trovati.
     */
    suspend fun analyze(ctx: Context, clip: Clip, r: Redaction, seedSrcUs: Long, onProgress: (Float) -> Unit): Redaction =
        withContext(Dispatchers.Default) {
            val seed = r.objectSeed
            val d = Detectors(r.faces, r.plates, seed != null, stream = !clip.isPhoto)
            try {
                if (clip.isPhoto) {
                    val bmp = decodePhotoThumb(Uri.parse(clip.uri).path ?: error("Foto non leggibile."), 1280) ?: error("Foto non leggibile.")
                    val img = InputImage.fromBitmap(bmp, 0)
                    val boxes = d.faces(img, bmp.width, bmp.height) + d.plates(img, bmp.width, bmp.height) +
                        (seed?.let { s -> Redact.followObject(listOf(d.objects(img, bmp.width, bmp.height)), 0, s).firstOrNull()?.let { listOf(it) } }.orEmpty())
                    bmp.recycle()
                    onProgress(1f)
                    return@withContext r.copy(boxes = cap(boxes, Redact.MAX), file = "", count = 0, found = boxes.size)
                }
                // Mezzo secondo in più per lato: se poi la clip si allunga un poco, resta coperta.
                val from = (clip.trimStartUs - 500_000).coerceAtLeast(0)
                val to = (clip.trimEndUs + 500_000).coerceAtMost(clip.sourceDurationUs.coerceAtLeast(clip.trimEndUs))
                val frames = mutableListOf<MutableList<RBox>>()
                val objFrames = mutableListOf<Map<Int, RBox>>()
                var lastPlates = emptyList<RBox>()
                FrameReader.read(ctx, clip.uri, from, to, RATE, SIZE) { bmp, t ->
                    coroutineContext.ensureActive()
                    val img = InputImage.fromBitmap(bmp, 0)
                    val w = bmp.width; val h = bmp.height
                    val boxes = mutableListOf<RBox>()
                    boxes += d.faces(img, w, h)
                    // Le scritte costano: si cercano un fotogramma sì e uno no (in mezzo restano le ultime trovate).
                    if (r.plates) { if (frames.size % 2 == 0) lastPlates = d.plates(img, w, h); boxes += lastPlates }
                    if (seed != null) objFrames += d.objects(img, w, h)
                    frames += boxes
                    onProgress(((t - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f))
                }
                check(frames.isNotEmpty()) { "Non sono riuscito a leggere i fotogrammi del video." }
                if (seed != null) {
                    val seedFrame = ((seedSrcUs - from) * RATE / 1_000_000L).toInt()
                    Redact.followObject(objFrames, seedFrame, seed).forEachIndexed { i, b -> if (b != null && i < frames.size) frames[i].add(0, b) }
                }
                val capped = frames.map { cap(it, Redact.MAX) }
                val f = save(ctx, capped)
                r.copy(file = f.absolutePath, fromUs = from, rateHz = RATE, count = capped.size, boxes = emptyList(), found = capped.count { it.isNotEmpty() })
            } finally { d.close() }
        }
}
