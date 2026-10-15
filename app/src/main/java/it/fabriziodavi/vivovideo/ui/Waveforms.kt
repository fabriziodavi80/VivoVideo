package it.fabriziodavi.vivovideo.ui

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Forma d'onda per la timeline: un picco ogni 20 ms dell'intero file sorgente.
 * Calcolata una volta per video (decodifica in streaming, poca memoria) e salvata su disco.
 */
object Waveforms {
    const val BUCKET_US = 20_000L
    private val memory = HashMap<String, FloatArray>()
    private val oneAtATime = Semaphore(1)

    fun cached(uri: String): FloatArray? = synchronized(memory) { memory[uri] }

    suspend fun get(context: Context, uri: String): FloatArray? {
        cached(uri)?.let { return it }
        return oneAtATime.withPermit {
            cached(uri)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "wave_${uri.hashCode().toUInt()}.bin")
                val peaks = if (file.exists()) readPeaks(file) else runCatching {
                    computePeaks(context, Uri.parse(uri))
                }.getOrNull()?.also { writePeaks(file, it) }
                if (peaks != null) synchronized(memory) { memory[uri] = peaks }
                peaks
            }
        }
    }

    private fun readPeaks(f: File): FloatArray {
        val bytes = f.readBytes()
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }

    private fun writePeaks(f: File, p: FloatArray) {
        val bb = ByteBuffer.allocate(p.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asFloatBuffer().put(p)
        f.writeBytes(bb.array())
    }

    /** Restituisce un array vuoto se il video non ha audio. */
    private fun computePeaks(context: Context, uri: Uri): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
        }
        if (track < 0 || format == null) { ex.release(); return FloatArray(0) }
        ex.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var floatPcm = false
        val peaks = ArrayList<Float>()
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        try {
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    buf.position(info.offset); buf.limit(info.offset + info.size); buf.order(ByteOrder.nativeOrder())
                    val count = if (floatPcm) info.size / 4 else info.size / 2
                    val fb = if (floatPcm) buf.asFloatBuffer() else null
                    val sb = if (floatPcm) null else buf.asShortBuffer()
                    val frames = count / channels
                    for (f in 0 until frames) {
                        val t = info.presentationTimeUs + f * 1_000_000L / rate
                        val bucket = (t / BUCKET_US).toInt()
                        if (bucket < 0) continue
                        while (peaks.size <= bucket) peaks += 0f
                        val v = abs(fb?.get(f * channels) ?: (sb!!.get(f * channels) / 32768f))
                        if (v > peaks[bucket]) peaks[bucket] = v
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        } finally {
            codec.stop(); codec.release(); ex.release()
        }
        // Normalizza sul picco del file, con una leggera compressione per vedere anche i suoni deboli.
        val max = (peaks.maxOrNull() ?: 0f).coerceAtLeast(0.05f)
        return FloatArray(peaks.size) { kotlin.math.sqrt(peaks[it] / max) }
    }
}
