package it.fabriziodavi.vivovideo.engine

import android.content.Context
import it.fabriziodavi.vivovideo.model.JumpCuts
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Il whoosh dei jump cut "Social", fatto dall'app (niente libreria online né chiavi): rumore filtrato con un passa-banda
 * che sale e scende, volume che culmina sul taglio, e il suono che passa da sinistra a destra.
 */
object Whoosh {
    const val RATE = 48_000

    /** Il suono (stereo interlacciato, –1…1), lungo [JumpCuts.WHOOSH_US] e col culmine a [JumpCuts.WHOOSH_PEAK_US]. */
    fun samples(seed: Long = 7L): FloatArray {
        val n = (JumpCuts.WHOOSH_US * RATE / 1_000_000).toInt()
        val peak = JumpCuts.WHOOSH_PEAK_US.toFloat() / JumpCuts.WHOOSH_US
        val out = FloatArray(n * 2)
        val rnd = java.util.Random(seed)
        // Passa-banda a variabili di stato (Chamberlin): frequenza che sale fino al culmine, poi scende.
        var low = 0f; var band = 0f
        var max = 1e-6f
        for (i in 0 until n) {
            val u = i.toFloat() / n
            val f = if (u < peak) 350f + 2800f * (u / peak) * (u / peak) else 3150f - 2300f * ((u - peak) / (1 - peak))
            val fc = (2 * sin(PI * f / RATE)).toFloat()
            val q = 0.55f
            val x = rnd.nextGaussian().toFloat()
            low += fc * band
            val high = x - low - q * band
            band += fc * high
            // Volume: sale piano, culmina sul taglio, si spegne in fretta.
            val env = if (u < peak) (u / peak).let { it * it * it } else exp(-6.0 * (u - peak) / (1 - peak)).toFloat()
            val v = band * env
            // Da sinistra a destra (potenza costante).
            val pan = (u * PI / 2).toFloat()
            out[i * 2] = v * cos(pan)
            out[i * 2 + 1] = v * sin(pan)
            max = maxOf(max, kotlin.math.abs(v))
        }
        // Picco a –6 dBFS, con micro-dissolvenze agli estremi.
        val g = 0.5f / max
        val ramp = RATE / 200
        for (i in 0 until n) {
            val e = minOf(1f, i.toFloat() / ramp, (n - 1 - i).toFloat() / ramp)
            out[i * 2] *= g * e; out[i * 2 + 1] *= g * e
        }
        return out
    }

    /** WAV a 16 bit del suono. */
    fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val bb = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + data); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(2); bb.putInt(RATE); bb.putInt(RATE * 4)
        bb.putShort(4); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(data)
        for (v in samples) bb.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        return bb.array()
    }

    /** Il file del whoosh (fatto la prima volta, poi riusato da tutti i progetti). */
    fun file(ctx: Context): File {
        val f = File(File(ctx.filesDir, "effetti").apply { mkdirs() }, "whoosh_v1.wav")
        if (!f.exists() || f.length() < 1000) {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(wav(samples()))
            tmp.renameTo(f)
        }
        return f
    }

    /** Valore efficace (per le prove). */
    internal fun rms(x: FloatArray, from: Int, to: Int): Float {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from).coerceAtLeast(1)).toFloat()
    }
}
