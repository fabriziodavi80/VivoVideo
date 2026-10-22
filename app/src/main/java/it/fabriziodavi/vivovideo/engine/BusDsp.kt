package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.BusMix
import it.fabriziodavi.vivovideo.model.Comp
import it.fabriziodavi.vivovideo.model.Mix
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Un canale del mixer su audio stereo intrecciato, a blocchi (stesso codice in anteprima ed esportazione):
 * equalizzatore a 3 bande, compressore, volume e bilanciamento. Tiene lo stato fra un blocco e l'altro.
 */
class BusDsp(m: BusMix, gains: Pair<Float, Float>, rate: Int = VoiceChain.RATE, private val meter: Bus? = null) {
    private val eqOn = !m.eq.flat
    private val low = Array(2) { VoiceChain.Biquad.lowShelf(120.0, m.eq.lowDb.toDouble(), rate) }
    private val mid = Array(2) { VoiceChain.Biquad.peak(1500.0, m.eq.midDb.toDouble(), 0.8, rate) }
    private val high = Array(2) { VoiceChain.Biquad.highShelf(8000.0, m.eq.highDb.toDouble(), rate) }
    private val comp = m.comp
    private val envA = 1 - exp(-1.0 / (0.01 * rate))
    private val gA = 1 - exp(-1.0 / (0.01 * rate))
    private val gR = 1 - exp(-1.0 / (0.15 * rate))
    /** Compensazione: metà della riduzione a 0 dBFS, così il canale non "sparisce". */
    private val makeup = if (comp == Comp.OFF) 1.0 else 10.0.pow(-comp.thresholdDb * (1 - 1 / comp.ratio) * 0.5 / 20)
    private var env = 0.0
    private var cg = 0.0
    private val gl = gains.first
    private val gr = gains.second

    /** Elabora [frames] fotogrammi stereo di [s] a partire dal campione [off]. */
    fun process(s: FloatArray, off: Int = 0, frames: Int = (s.size - off) / 2) {
        var peak = 0f
        for (f in 0 until frames) {
            val i = off + f * 2
            var l = s[i].toDouble(); var r = s[i + 1].toDouble()
            if (eqOn) {
                l = high[0].run(mid[0].run(low[0].run(l))); r = high[1].run(mid[1].run(low[1].run(r)))
            }
            if (comp != Comp.OFF) {
                env += ((l * l + r * r) / 2 - env) * envA
                val d = if (env <= 1e-12) -120.0 else 10 * log10(env)
                val want = if (d > comp.thresholdDb) -(d - comp.thresholdDb) * (1 - 1 / comp.ratio) else 0.0
                cg += (want - cg) * (if (want < cg) gA else gR)
                val k = 10.0.pow(cg / 20) * makeup
                l *= k; r *= k
            }
            val ol = (l * gl).toFloat(); val or = (r * gr).toFloat()
            s[i] = ol; s[i + 1] = or
            peak = max(peak, max(abs(ol), abs(or)))
        }
        meter?.let { Meters.feed(it, peak) }
    }

    companion object {
        /** Il canale cambia qualcosa? (altrimenti si salta). */
        fun needed(mix: Mix, b: Bus) = !mix.bus(b).neutral || !mix.audible(b)
        fun of(mix: Mix, b: Bus, rate: Int = VoiceChain.RATE, metered: Boolean = false) = BusDsp(mix.bus(b), mix.gains(b), rate, if (metered) b else null)
    }
}

/** Limitatore dei picchi dell'uscita (attacco istantaneo, rilascio morbido): niente distorsione digitale. */
class PeakLimiter(private val ceiling: Float = 0.891f, rate: Int = VoiceChain.RATE) {
    private var g = 1f
    private val rel = (1 - exp(-1.0 / (0.1 * rate))).toFloat()

    fun process(s: FloatArray, off: Int = 0, frames: Int = (s.size - off) / 2) {
        for (f in 0 until frames) {
            val i = off + f * 2
            val p = max(abs(s[i]), abs(s[i + 1]))
            val need = if (p * g > ceiling) ceiling / p else 1f
            g = if (need < g) need else g + (1f - g) * rel
            s[i] = (s[i] * g).coerceIn(-ceiling, ceiling); s[i + 1] = (s[i + 1] * g).coerceIn(-ceiling, ceiling)
        }
    }
}

/** Livelli dei canali in anteprima (picco recente, 0…1), letti dallo schermo del mixer. */
object Meters {
    private val peaks = AtomicIntegerArray(Bus.entries.size + 1)
    private val times = LongArray(Bus.entries.size + 1)
    fun feed(b: Bus, peak: Float) = feed(b.ordinal, peak)
    fun feedMaster(peak: Float) = feed(Bus.entries.size, peak)

    private fun feed(i: Int, peak: Float) {
        // Tiene il valore più alto e lo lascia scendere piano (come gli indicatori veri).
        val old = Float.fromBits(peaks.get(i))
        val now = System.nanoTime()
        val decayed = old * (1f - ((now - times[i]) / 1e9f * 2.5f).coerceIn(0f, 1f))
        times[i] = now
        peaks.set(i, max(decayed, peak).toBits())
    }

    /** Livello in dBFS (da -60 a 0) per l'indicatore. */
    fun db(b: Bus?): Float {
        // Uscita: il più alto dei gruppi (in anteprima l'uscita non ha un suo passaggio).
        if (b == null) return Bus.entries.maxOf { db(it) }
        val i = b.ordinal
        val v = Float.fromBits(peaks.get(i))
        val age = (System.nanoTime() - times[i]) / 1e9f
        val shown = v * (1f - (age * 2.5f).coerceIn(0f, 1f))
        return if (shown <= 0.001f) -60f else (20 * log10(shown)).coerceIn(-60f, 0f)
    }

    fun reset() { for (i in 0 until peaks.length()) peaks.set(i, 0) }
}

/**
 * In anteprima: applica un canale del mixer (o il limitatore dell'uscita, con [master]) all'audio di un elemento.
 * Lavora su PCM 16 bit o in virgola mobile, mono o stereo.
 */
class MixAudioProcessor(private val make: (Int) -> ((FloatArray, Int) -> Unit)) : androidx.media3.common.audio.BaseAudioProcessor() {
    private var run: ((FloatArray, Int) -> Unit)? = null
    private var buf = FloatArray(0)

    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if ((enc != androidx.media3.common.C.ENCODING_PCM_16BIT && enc != androidx.media3.common.C.ENCODING_PCM_FLOAT) || inputAudioFormat.channelCount !in 1..2) {
            throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: androidx.media3.common.audio.AudioProcessor.StreamMetadata) {
        if (run == null) run = make(inputAudioFormat.sampleRate)
    }

    override fun onReset() { run = null }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val fmt = inputAudioFormat
        val float = fmt.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT
        val ch = fmt.channelCount
        val bpf = ch * if (float) 4 else 2
        val frames = inputBuffer.remaining() / bpf
        if (frames == 0) return
        val r = run ?: make(fmt.sampleRate).also { run = it }
        if (buf.size < frames * 2) buf = FloatArray(frames * 2)
        for (f in 0 until frames) {
            val l = if (float) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
            val rr = if (ch == 2) (if (float) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f) else l
            buf[f * 2] = l; buf[f * 2 + 1] = rr
        }
        r(buf, frames)
        val out = replaceOutputBuffer(frames * bpf)
        for (f in 0 until frames) {
            if (ch == 2) {
                put(out, buf[f * 2], float); put(out, buf[f * 2 + 1], float)
            } else put(out, (buf[f * 2] + buf[f * 2 + 1]) / 2, float)
        }
        out.flip()
    }

    private fun put(out: java.nio.ByteBuffer, v: Float, float: Boolean) {
        if (float) out.putFloat(v) else out.putShort((v * 32767f).toInt().coerceIn(-32768, 32767).toShort())
    }

    companion object {
        /** Canale [b] del mixer, con indicatore di livello. */
        fun bus(mix: Mix, b: Bus) = MixAudioProcessor { rate -> val d = BusDsp.of(mix, b, rate, metered = true); { s, n -> d.process(s, 0, n) } }

        /** Uscita: limitatore (se acceso) e indicatore generale. */
        fun master(limiter: Boolean) = MixAudioProcessor { rate ->
            val lim = PeakLimiter(rate = rate)
            ({ s, n ->
                if (limiter) lim.process(s, 0, n)
                var p = 0f
                for (i in 0 until n * 2) p = max(p, abs(s[i]))
                Meters.feedMaster(p)
            })
        }
    }
}
