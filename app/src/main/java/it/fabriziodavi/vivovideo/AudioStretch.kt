package it.fabriziodavi.vivovideo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.SpeedProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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

class DecodedAudio(val samples: FloatArray, val channels: Int, val sampleRate: Int)

/**
 * Prepara la traccia audio rallentata/accelerata con tono naturale:
 * decodifica, applica la stessa curva di velocità del video, salva un WAV.
 * Restituisce null se il video non ha audio.
 */
fun prepareStretchedAudio(context: Context, uri: Uri, provider: SpeedProvider, durationUs: Long): File? {
    val audio = decodeAudio(context, uri) ?: return null
    val (segEnd, speed) = curveFrom(provider, durationUs, audio.sampleRate)
    val out = NativeStretch.stretch(audio.samples, audio.channels, audio.sampleRate, segEnd, speed)
    val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.wav")
    writeWav(file, out, audio.channels, audio.sampleRate)
    return file
}

/** Legge la curva dal SpeedProvider (gli stessi tratti usati per il video). */
private fun curveFrom(provider: SpeedProvider, durationUs: Long, sampleRate: Int): Pair<DoubleArray, DoubleArray> {
    val ends = ArrayList<Double>()
    val speeds = ArrayList<Double>()
    var t = 0L
    var guard = 0
    while (guard++ < 1_000_000) {
        val s = provider.getSpeed(t).toDouble()
        val next = provider.getNextSpeedChangeTimeUs(t)
        val end = if (next == C.TIME_UNSET || next <= t) Long.MAX_VALUE else next
        speeds += s
        if (end == Long.MAX_VALUE || end >= durationUs * 2) {
            ends += Double.MAX_VALUE
            break
        }
        ends += end * sampleRate / 1_000_000.0
        t = end
    }
    return ends.toDoubleArray() to speeds.toDoubleArray()
}

private fun decodeAudio(context: Context, uri: Uri): DecodedAudio? {
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
    val mime = format.getString(MediaFormat.KEY_MIME)!!
    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(format, null, null, 0)
    codec.start()

    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var floatPcm = false
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
                    if (read < 0) {
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
                    sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIdx >= 0 -> {
                    val buf: ByteBuffer = codec.getOutputBuffer(outIdx)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    val count = if (floatPcm) info.size / 4 else info.size / 2
                    if (size + count > data.size) data = data.copyOf(maxOf(data.size * 2, size + count))
                    if (floatPcm) {
                        buf.asFloatBuffer().get(data, size, count)
                    } else {
                        val sb = buf.asShortBuffer()
                        for (i in 0 until count) data[size + i] = sb.get(i) / 32768f
                    }
                    size += count
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
    } finally {
        codec.stop(); codec.release(); extractor.release()
    }
    return DecodedAudio(data.copyOf(size - size % channels), channels, sampleRate)
}

private fun writeWav(file: File, samples: FloatArray, channels: Int, sampleRate: Int) {
    val dataBytes = samples.size * 2
    BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray()); header.put("fmt ".toByteArray())
        header.putInt(16); header.putShort(1); header.putShort(channels.toShort())
        header.putInt(sampleRate); header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort()); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(dataBytes)
        out.write(header.array())
        val chunk = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
        for (v in samples) {
            if (!chunk.hasRemaining()) { out.write(chunk.array(), 0, chunk.position()); chunk.clear() }
            chunk.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        out.write(chunk.array(), 0, chunk.position())
    }
}
