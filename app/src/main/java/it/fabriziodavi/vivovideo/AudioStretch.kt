package it.fabriziodavi.vivovideo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Project
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ponte verso il codice nativo (Signalsmith Stretch). */
object NativeStretch {
    init {
        System.loadLibrary("vivostretch")
    }

    external fun stretch(
        input: FloatArray,
        channels: Int,
        sampleRate: Int,
        segEnd: DoubleArray,
        speed: DoubleArray,
    ): FloatArray
}

private const val OUT_RATE = 48_000
private const val OUT_CH = 2

/**
 * Costruisce la colonna sonora dell'intera timeline con tono naturale:
 * per ogni clip decodifica il tratto tagliato, lo allunga/accorcia con la sua curva
 * di velocità e lo accoda in un unico WAV stereo a 48 kHz.
 * Clip mute o senza audio diventano silenzio della durata giusta, così tutto resta in sincrono.
 */
fun renderTimelineAudio(context: Context, project: Project, onProgress: (Float) -> Unit = {}): File {
    val file = File(context.cacheDir, "timeline_${System.currentTimeMillis()}.wav")
    var frames = 0L
    BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
        out.write(ByteArray(44)) // intestazione scritta alla fine
        project.clips.forEachIndexed { i, clip ->
            val target = clip.outputUs * OUT_RATE / 1_000_000
            val audio = if (clip.muted) null else runCatching {
                decodeAudioRange(context, Uri.parse(clip.uri), clip.trimStartUs, clip.trimEndUs)
            }.getOrNull()
            var samples: FloatArray = if (audio == null || audio.isEmpty()) FloatArray(0) else {
                val curve = clip.speedCurve()
                if (curve.speeds.all { it == 1f }) audio else {
                    val ends = DoubleArray(curve.segEndUs.size) { k ->
                        if (k == curve.segEndUs.lastIndex) Double.MAX_VALUE
                        else curve.segEndUs[k] * OUT_RATE / 1_000_000.0
                    }
                    val sp = DoubleArray(curve.speeds.size) { curve.speeds[it].toDouble() }
                    NativeStretch.stretch(audio, OUT_CH, OUT_RATE, ends, sp)
                }
            }
            // Lunghezza esatta della clip nel video: si completa con silenzio o si taglia.
            val needed = (target * OUT_CH).toInt()
            if (samples.size != needed) samples = samples.copyOf(needed)
            writePcm16(out, samples)
            frames += target
            onProgress((i + 1f) / project.clips.size)
        }
    }
    RandomAccessFile(file, "rw").use { raf -> raf.write(wavHeader(frames)) }
    return file
}

private fun writePcm16(out: BufferedOutputStream, samples: FloatArray) {
    val chunk = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
    for (v in samples) {
        if (!chunk.hasRemaining()) { out.write(chunk.array(), 0, chunk.position()); chunk.clear() }
        chunk.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    }
    out.write(chunk.array(), 0, chunk.position())
}

private fun wavHeader(frames: Long): ByteArray {
    val dataBytes = (frames * OUT_CH * 2).toInt()
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()); h.putInt(36 + dataBytes)
    h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
    h.putInt(16); h.putShort(1); h.putShort(OUT_CH.toShort())
    h.putInt(OUT_RATE); h.putInt(OUT_RATE * OUT_CH * 2)
    h.putShort((OUT_CH * 2).toShort()); h.putShort(16)
    h.put("data".toByteArray()); h.putInt(dataBytes)
    return h.array()
}

/**
 * Decodifica l'audio fra startUs e endUs e lo restituisce stereo a 48 kHz (interleaved).
 * Restituisce null se il video non ha audio.
 */
fun decodeAudioRange(context: Context, uri: Uri, startUs: Long, endUs: Long): FloatArray? {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri, null)
    var track = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
            track = i; format = f; break
        }
    }
    if (track < 0 || format == null) {
        extractor.release(); return null
    }
    extractor.selectTrack(track)
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()

    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var floatPcm = false
    // Accumula stereo alla frequenza originale; il ricampionamento si fa alla fine.
    var data = FloatArray(1 shl 20)
    var size = 0
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false
    try {
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0 || extractor.sampleTime > endUs + 100_000) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val of = codec.outputFormat
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    val count = if (floatPcm) info.size / 4 else info.size / 2
                    val framesInBuf = count / channels
                    val fb = if (floatPcm) buf.asFloatBuffer() else null
                    val sb = if (floatPcm) null else buf.asShortBuffer()
                    for (f in 0 until framesInBuf) {
                        val t = info.presentationTimeUs + f * 1_000_000L / rate
                        if (t < startUs || t >= endUs) continue
                        fun s(c: Int): Float {
                            val idx = f * channels + minOf(c, channels - 1)
                            return fb?.get(idx) ?: (sb!!.get(idx) / 32768f)
                        }
                        if (size + 2 > data.size) data = data.copyOf(data.size * 2)
                        data[size++] = s(0)
                        data[size++] = s(1)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
    } finally {
        codec.stop(); codec.release(); extractor.release()
    }
    val stereo = data.copyOf(size)
    return if (rate == OUT_RATE) stereo else resampleStereo(stereo, rate, OUT_RATE)
}

private fun resampleStereo(input: FloatArray, from: Int, to: Int): FloatArray {
    val inFrames = input.size / 2
    val outFrames = (inFrames.toLong() * to / from).toInt()
    val out = FloatArray(outFrames * 2)
    for (i in 0 until outFrames) {
        val pos = i.toDouble() * from / to
        val i0 = pos.toInt().coerceAtMost(inFrames - 1)
        val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
        val f = (pos - i0).toFloat()
        out[2 * i] = input[2 * i0] * (1 - f) + input[2 * i1] * f
        out[2 * i + 1] = input[2 * i0 + 1] * (1 - f) + input[2 * i1 + 1] * f
    }
    return out
}
