package it.fabriziodavi.vivovideo.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Registra la voce senza compressione (PCM 16 bit mono a 48 kHz) in un file grezzo.
 * Prima fonte: "riconoscimento vocale" (risposta piatta, senza controllo automatico del volume),
 * se il telefono non la offre si usa il microfono normale. Niente file MP4 da chiudere: anche
 * una registrazione interrotta resta leggibile.
 */
class VoiceRecorder(private val raw: File) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var samples = 0L
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(VoiceChain.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val size = maxOf(min * 4, VoiceChain.RATE / 5 * 2)
        val r = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC).firstNotNullOfOrNull { src ->
            runCatching {
                val a = AudioRecord(src, VoiceChain.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                if (a.state == AudioRecord.STATE_INITIALIZED) a else { a.release(); null }
            }.getOrNull()
        } ?: return false
        runCatching { r.startRecording() }.onFailure { r.release(); return false }
        record = r
        running = true
        samples = 0
        thread = Thread({
            val buf = ShortArray(VoiceChain.RATE / 20)
            val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            BufferedOutputStream(FileOutputStream(raw), 1 shl 16).use { out ->
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    bytes.clear()
                    for (i in 0 until n) bytes.putShort(buf[i])
                    out.write(bytes.array(), 0, n * 2)
                    samples += n
                }
            }
        }, "voce").apply { start() }
        return true
    }

    /** Ferma e restituisce i campioni registrati. */
    fun stop(): Long {
        running = false
        runCatching { record?.stop() }
        thread?.join(2000)
        runCatching { record?.release() }
        record = null; thread = null
        return samples
    }

    companion object {
        /** Legge il file grezzo come audio mono in virgola mobile. */
        fun read(raw: File): FloatArray {
            val b = raw.readBytes()
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.size / 2) { bb.getShort(it * 2) / 32768f }
        }
    }
}
