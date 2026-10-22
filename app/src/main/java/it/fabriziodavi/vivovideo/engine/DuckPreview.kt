package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import it.fabriziodavi.vivovideo.decodeAudioRange
import it.fabriziodavi.vivovideo.model.Project
import java.nio.ByteBuffer

/**
 * Ducking anche in anteprima: la musica si abbassa sotto la voce fuori campo e l'audio staccato.
 * I livelli delle voci si calcolano una volta (in secondo piano) e restano in memoria finché le voci non cambiano.
 * L'audio delle clip conta solo nel video esportato (decodificarlo tutto per l'anteprima sarebbe troppo lento).
 */
object DuckPreview {
    private val levels = object : LinkedHashMap<String, FloatArray>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 4
    }

    /** Chiave delle voci del progetto: cambia se si sposta, allunga o cambia volume una voce (solo il nome del file: in anteprima può cambiare cartella). */
    fun key(p: Project): String? {
        val d = p.audibleMusic().filter { it.dialog }
        if (!p.duck || d.isEmpty() || p.audibleMusic().none { !it.dialog }) return null
        return p.durationUs.toString() + "|" + d.joinToString("|") { "${it.uri.substringAfterLast('/')},${it.startUs},${it.offsetUs},${it.lengthUs},${it.volume},${it.fadeInUs},${it.fadeOutUs}" }
    }

    /** Da preparare: ducking acceso, voci e musica presenti, livelli non ancora calcolati. */
    fun needs(p: Project): Boolean = key(p)?.let { synchronized(levels) { it !in levels } } ?: false

    /** Curva della musica (blocchi da 10 ms) o null se non serve / non è pronta. */
    fun gainsFor(p: Project): FloatArray? {
        val k = key(p) ?: return null
        val lv = synchronized(levels) { levels[k] } ?: return null
        return AudioDsp.duckGainsFrom(AudioDsp.voiceMask(lv), p.duckDb.toFloat())
    }

    /** Legge le voci e ne misura il livello (lento: da chiamare fuori dal thread principale). */
    fun prepare(ctx: Context, p: Project) {
        val k = key(p) ?: return
        val energy = DoubleArray((p.durationUs / 10_000).toInt() + 1)
        p.audibleMusic().filter { it.dialog }.forEach { t ->
            val len = t.playUs(p.durationUs)
            val audio = runCatching { decodeAudioRange(ctx, Uri.parse(t.uri), t.offsetUs, t.offsetUs + len) }.getOrNull() ?: return@forEach
            val start = t.startUs * AudioDsp.RATE / 1_000_000
            val frames = minOf((audio.size / 2).toLong(), len * AudioDsp.RATE / 1_000_000)
            AudioDsp.addEnergy(energy, audio, start, frames) { fi -> t.gainAt(fi * 1_000_000 / AudioDsp.RATE, p.durationUs) }
        }
        synchronized(levels) { levels[k] = AudioDsp.levelsFromEnergy(energy) }
    }

    /** Solo per le prove. */
    internal fun put(p: Project, lv: FloatArray) { key(p)?.let { k -> synchronized(levels) { levels[k] = lv } } }
}

/**
 * Applica a un brano la curva del ducking. La posizione nel brano arriva da Media3 a ogni salto
 * ([AudioProcessor.StreamMetadata.positionOffsetUs], dall'inizio del tratto usato); [itemStartUs] è dove
 * il brano comincia nel montaggio.
 */
class DuckAudioProcessor(private val gains: FloatArray, private val itemStartUs: Long) : BaseAudioProcessor() {
    private var frame = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val float = fmt.encoding == C.ENCODING_PCM_FLOAT
        val ch = fmt.channelCount
        val bpf = ch * if (float) 4 else 2
        val frames = inputBuffer.remaining() / bpf
        if (frames == 0) return
        val out = replaceOutputBuffer(frames * bpf)
        for (i in 0 until frames) {
            val g = AudioDsp.gainAt(gains, itemStartUs + (frame + i) * 1_000_000 / fmt.sampleRate)
            for (c in 0 until ch) {
                if (float) out.putFloat(inputBuffer.getFloat() * g)
                else out.putShort((inputBuffer.getShort() * g).toInt().coerceIn(-32768, 32767).toShort())
            }
        }
        frame += frames
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val rate = inputAudioFormat.sampleRate
        frame = if (rate > 0) streamMetadata.positionOffsetUs * rate / 1_000_000 else 0
    }

    override fun onReset() { frame = 0 }
}
