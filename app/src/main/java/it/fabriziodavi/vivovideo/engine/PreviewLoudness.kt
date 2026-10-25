package it.fabriziodavi.vivovideo.engine

import android.content.Context
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.renderTimelineAudio
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.pow

/**
 * Volume finale anche in anteprima: il video esportato porta tutto il mix al volume della destinazione (–14 LUFS per i
 * social), mentre l'anteprima suonava l'audio com'è (le riprese del telefono sono spesso basse). Qui si misura il mix
 * in secondo piano, una volta per ogni versione dell'audio, e l'anteprima applica lo stesso guadagno, fermandosi
 * prima che i picchi distorcano (in anteprima non c'è il limitatore).
 */
object PreviewLoudness {
    /** Oltre questa durata non si misura (troppo lungo da preparare): l'anteprima resta com'è. */
    const val MAX_US = 15 * 60_000_000L

    private val gains = object : LinkedHashMap<String, Float>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 8
    }

    /** Tutto ciò che cambia l'audio del mix (non il video). Null = nulla da misurare. */
    fun key(p: Project): String? {
        if (p.mix.target.lufs == null || p.durationUs > MAX_US) return null
        val clips = p.clips.filter { !it.isPhoto && !it.muted && it.volume > 0f }
        if (clips.isEmpty() && p.audibleMusic().isEmpty()) return null
        return buildString {
            append(p.mix.target.lufs).append('|').append(p.mix.toString()).append('|').append(p.duck).append(p.duckDb).append('|')
            p.clips.forEach { c ->
                append(c.uri.substringAfterLast('/')).append(',').append(c.isPhoto).append(c.muted).append(',').append(c.trimStartUs).append(',')
                    .append(c.trimEndUs).append(',').append(c.volume).append(',').append(c.speed).append(c.speedPoints).append(',')
                    .append(c.fadeAudio).append(c.fadeInUs).append(',').append(c.fadeOutUs).append(';')
            }
            p.music.forEach { m -> append(m.copy(beatsUs = emptyList(), energy = emptyList(), markersUs = emptyList(), hiddenUs = emptyList()).toString()).append(';') }
            p.layers.filter { it.isVideo }.forEach { l -> append(l.uri.substringAfterLast('/')).append(l.startUs).append(',').append(l.durationUs).append(l.muted).append(';') }
        }
    }

    /** Da misurare: c'è audio e questa versione non è ancora misurata. */
    fun needs(p: Project): Boolean = key(p)?.let { k -> synchronized(gains) { k !in gains } } ?: false

    /** Guadagno per l'anteprima (1 = com'è, anche finché la misura non è pronta). */
    fun gainFor(p: Project): Float = key(p)?.let { k -> synchronized(gains) { gains[k] } } ?: 1f

    /** Guadagno verso [targetLufs] che non fa superare ai picchi –1 dBFS (da –12 a +12 dB). */
    fun gainOf(lufs: Double, peak: Float, targetLufs: Double): Float =
        if (lufs <= -69.0 || peak <= 0f) 1f else AudioDsp.normalizeGain(lufs, peak, targetLufs).coerceIn(0.25f, 4f)

    /** Misura il mix (lento: fuori dal thread principale). */
    fun prepare(ctx: Context, p: Project) {
        val k = key(p) ?: return
        val target = p.mix.target.lufs ?: return
        val wav = renderTimelineAudio(ctx, p, loudness = false)
        val meter = AudioDsp.LoudnessMeter()
        try {
            BufferedInputStream(FileInputStream(wav), 1 shl 16).use { input ->
                input.skip(44)
                val buf = ByteArray(1 shl 14)
                var carry = -1
                val pair = FloatArray(2); var have = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val b = buf[i].toInt() and 0xff
                        if (carry < 0) { carry = b; continue }
                        pair[have++] = ((b shl 8) or carry).toShort() / 32768f
                        carry = -1
                        if (have == 2) { meter.add(pair[0], pair[1]); have = 0 }
                    }
                }
            }
        } finally { wav.delete() }
        val g = gainOf(meter.lufs(), meter.peak, target)
        synchronized(gains) { gains[k] = g }
    }

    /** Solo per le prove. */
    internal fun put(p: Project, g: Float) { key(p)?.let { k -> synchronized(gains) { gains[k] = g } } }

    fun db(g: Float): Double = 20 * kotlin.math.log10(g.toDouble())
    fun lin(db: Double): Float = 10.0.pow(db / 20).toFloat()
}
