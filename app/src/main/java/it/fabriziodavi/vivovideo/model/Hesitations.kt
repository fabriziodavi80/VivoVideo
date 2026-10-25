package it.fabriziodavi.vivovideo.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Ehm" riconosciuti dal suono, non solo dalle parole del riconoscimento vocale (che spesso li salta, li scrive "M"
 * o ne sbaglia i tempi). Un ehm è un suono tenuto: voce continua con tono e timbro fermi per mezzo secondo o più
 * ("eee", "mmm", "ehm"). Le parole vere invece cambiano timbro ogni pochi centesimi di secondo (consonanti,
 * passaggi fra vocali). Le soglie sono relative al file: funzionano anche con il microfono lontano e il fondo
 * di una stanza (provate su una ripresa vera del telefono).
 *
 * Lavora a 8 kHz, un fotogramma ogni 10 ms.
 */
object Hesitations {
    const val HOP_US = 10_000L
    private const val RATE = 8_000
    private const val HOP = 80
    private const val NFFT = 256

    /** Un fotogramma: volume (dB), voce sì/no, tono (semitoni da 100 Hz), cambio di timbro rispetto a 20 ms prima (dB). */
    data class Frame(val db: Float, val voiced: Boolean, val semitone: Float, val flux: Float)

    /** Analisi dell'audio mono a 16 kHz. */
    fun analyze(pcm16: FloatArray): List<Frame> {
        val n8 = pcm16.size / 2
        val x = FloatArray(n8) { (pcm16[2 * it] + pcm16[2 * it + 1]) * 0.5f }
        val frames = (n8 - 440) / HOP + 1
        if (frames <= 2) return emptyList()
        val window = FloatArray(NFFT) { (0.5 - 0.5 * cos(2 * PI * it / NFFT)).toFloat() }
        val re = FloatArray(NFFT); val im = FloatArray(NFFT)
        val bands = 16 // 250 Hz l'una
        var prev2: FloatArray? = null
        var prev1: FloatArray? = null
        val out = ArrayList<Frame>(frames)
        for (f in 0 until frames) {
            val o = f * HOP
            var e = 0.0
            for (i in 0 until NFFT) { val v = x[o + i]; e += v * v; re[i] = v * window[i]; im[i] = 0f }
            val db = (10 * log10(e / NFFT + 1e-12)).toFloat()
            fft(re, im)
            val pow = FloatArray(NFFT / 2) { re[it] * re[it] + im[it] * im[it] }
            fun band(lo: Double, hi: Double): Double {
                var s = 1e-12
                for (b in (lo / 31.25).toInt() until (hi / 31.25).toInt().coerceAtMost(NFFT / 2)) s += pow[b]
                return 10 * log10(s)
            }
            val bandDb = FloatArray(bands) { b -> band(b * 250.0, (b + 1) * 250.0).toFloat() }
            // Stabilità del timbro: differenza media (dB) con 20 ms prima, solo sulle bande forti (quelle deboli sono rumore di fondo).
            val ref = prev2
            val flux = if (ref == null) 99f else {
                val top = bandDb.max()
                var s = 0f; var k = 0
                for (b in 0 until bands) if (bandDb[b] > top - 25 && ref[b] > top - 25) { s += abs(bandDb[b] - ref[b]); k++ }
                if (k == 0) 99f else s / k
            }
            prev2 = prev1; prev1 = bandDb
            // Voce: autocorrelazione normalizzata fra 70 e 400 Hz (finestra di 40 ms: almeno due periodi anche per le voci basse). Si prende il ritardo più corto
            // vicino al massimo, così il tono non salta all'ottava sotto da un fotogramma all'altro.
            val w = 320
            val r = DoubleArray(115)
            var best = 0.0
            for (l in 20..114) {
                var a = 0.0; var b = 0.0; var c = 0.0
                for (i in 0 until w) { val p = x[o + i]; val q = x[o + i + l]; a += p * q; b += p * p; c += q * q }
                r[l] = if (b > 0 && c > 0) a / sqrt(b * c) else 0.0
                if (r[l] > best) best = r[l]
            }
            var lag = 0
            for (l in 21..113) if (r[l] >= 0.92 * best && r[l] >= r[l - 1] && r[l] >= r[l + 1]) { lag = l; break }
            val voiced = best > 0.5 && lag > 0
            val st = if (voiced) (12 * ln(RATE.toDouble() / lag / 100.0) / ln(2.0)).toFloat() else 0f
            out += Frame(db, voiced, st, flux)
        }
        return out
    }

    /**
     * Suoni tenuti: voce con tono e timbro fermi per almeno [minFrames] fotogrammi (si tollerano tre fotogrammi
     * storti di fila, e due tratti fermi separati da un attimo con lo stesso tono diventano uno).
     */
    fun held(frames: List<Frame>, minFrames: Int = 30): List<IntRange> {
        if (frames.size < 10) return emptyList()
        // Solo la voce vera, non il fondo della stanza (che può ronzare): almeno 8 dB sopra i momenti più silenziosi.
        val sorted = frames.map { it.db }.sorted()
        val floor = sorted[(sorted.size * 0.05).toInt()]
        val runs = ArrayList<IntArray>() // inizio, fine (inclusa)
        var start = -1; var last = -1; var bad = 0; var lastSt = 0f
        fun close() { if (start >= 0) runs += intArrayOf(start, last); start = -1; bad = 0 }
        for (i in frames.indices) {
            val f = frames[i]
            val good = f.voiced && f.db >= floor + 8 && f.flux < 4f && (start < 0 || abs(f.semitone - lastSt) < 0.8f)
            if (good) {
                if (start < 0) start = i
                last = i; lastSt = f.semitone; bad = 0
            } else if (start >= 0 && (f.flux >= 8f || ++bad > 3)) close() // un cambio netto di timbro è una nuova sillaba
        }
        close()
        fun tone(r: IntArray) = (r[0]..r[1]).filter { frames[it].voiced }.map { frames[it].semitone }.sorted().let { it[it.size / 2] }
        val merged = ArrayList<IntArray>()
        runs.filter { it[1] - it[0] >= 4 }.forEach { r ->
            val l = merged.lastOrNull()
            val smooth = l != null && (l[1] + 1 until r[0]).none { frames[it].flux >= 8f }
            if (l != null && smooth && r[0] - l[1] <= 8 && abs(tone(r) - tone(l)) < 2.5f) l[1] = r[1] else merged += r
        }
        return merged.filter { r ->
            if (r[1] - r[0] + 1 < minFrames) return@filter false
            // Volume regolare: dentro un ehm non ci sono i buchi delle consonanti (oltre 8 dB sotto, per più di un attimo).
            val lv = (r[0]..r[1]).map { frames[it].db }.sorted()
            val med = lv[lv.size / 2]
            if (lv.count { it < med - 8f } > lv.size / 10) return@filter false
            val tones = (r[0]..r[1]).filter { frames[it].voiced }.map { frames[it].semitone }.sorted()
            tones[(tones.size * 0.9).toInt().coerceAtMost(tones.size - 1)] - tones[(tones.size * 0.1).toInt()] <= 3f
        }.map { it[0]..it[1] }
    }

    /** Livello tipico (dB) del suono tenuto [r]. */
    private fun level(frames: List<Frame>, r: IntRange) = r.map { frames[it].db }.sorted().let { it[it.size / 2] }

    /** Primo fotogramma della voce prima di [r] (fin dove si resta a meno di 6 dB dal suo livello, al più 10). */
    private fun onset(frames: List<Frame>, r: IntRange, quiet: Float): Int {
        var o = r.first
        while (o > 0 && r.first - o <= 10 && frames[o - 1].db >= quiet) o--
        return o
    }

    /** Fine del tratto da togliere: fino alla pausa, se la voce si spegne entro 120 ms; altrimenti la fine del suono tenuto. */
    private fun release(frames: List<Frame>, r: IntRange, quiet: Float): Int {
        val end = r.last + 1
        var k = end
        while (k < frames.size && k - end < 12 && frames[k].db >= quiet) k++
        return if (k < frames.size && frames[k].db < quiet) k else end
    }

    /** Un ehm trovato dal suono; [sure] = isolato fra due pause, sul tono basso e senza salire: si taglia di serie. */
    data class Found(val span: Span, val sure: Boolean)

    /**
     * Ehm trovati solo dal suono: un suono tenuto da almeno 0,35 s che comincia subito dopo una pausa (entro 60 ms
     * dall'attacco della voce, cioè senza una consonante davanti: "eee", "mmm", "ehm") e che non sale di tono
     * (le parole accentate salgono, gli ehm restano sul tono di base della voce o scendono).
     * Si toglie dall'attacco fino alla pausa dopo, con un filo di margine. È "sicuro" se finisce in una pausa e sta
     * sul tono di base di chi parla; gli altri si propongono spenti, da ascoltare.
     */
    fun detect(frames: List<Frame>): List<Found> {
        val tones = frames.filter { it.voiced }.map { it.semitone }.sorted()
        if (tones.size < 50) return emptyList()
        val base = tones[tones.size / 2]
        val near = held(frames, minFrames = 15)
        fun toneOf(r: IntRange) = r.filter { frames[it].voiced }.map { frames[it].semitone }.sorted().let { it[it.size / 2] }
        val found = held(frames, minFrames = 35).filter { r -> r.last - r.first < 250 }.mapNotNull { r ->
            // "Pausa" rispetto al suono tenuto: almeno 6 dB sotto il suo livello (le stanze hanno un fondo che non è silenzio).
            val quiet = level(frames, r) - 6f
            val o = onset(frames, r, quiet)
            val quietBefore = (maxOf(0, o - 12) until o).count { frames[it].db < quiet }
            if (r.first - o > 6 || quietBefore < 8) return@mapNotNull null
            val v = r.filter { frames[it].voiced }.map { frames[it].semitone }
            val tone = v.sorted()[v.size / 2]
            val q = v.size / 4
            val rise = v.takeLast(q.coerceAtLeast(1)).average() - v.take(q.coerceAtLeast(1)).average()
            if (tone > base + 4f || rise > 1.5) return@mapNotNull null
            // Un secondo suono tenuto subito dopo, sullo stesso tono ("eeh… mmm" ripreso): fa parte dello stesso ehm.
            var tail = r
            for (n in near) if (n.first > tail.last && n.first - tail.last <= 10 && abs(toneOf(n) - tone) < 2.5f) tail = n
            val end = release(frames, tail, quiet)
            val endsInPause = end < frames.size && frames[end].db < quiet
            Found(
                Span((o - 3).coerceAtLeast(0) * HOP_US, (end + 3).coerceAtMost(frames.size) * HOP_US),
                sure = endsInPause && tone <= base + 1.5f && rise <= 0.5,
            )
        }
        // Due suoni tenuti di fila (con i margini che si toccano) sono un ehm solo.
        val out = ArrayList<Found>()
        found.forEach { f ->
            val l = out.lastOrNull()
            if (l != null && f.span.startUs <= l.span.endUs) out[out.lastIndex] = Found(Span(l.span.startUs, maxOf(l.span.endUs, f.span.endUs)), l.sure || f.sure)
            else out += f
        }
        return out
    }

    /**
     * Dove sta davvero l'ehm che il riconoscimento vocale ha messo in [heard] (i suoi tempi sbagliano anche di qualche
     * decimo di secondo): il suono tenuto più vicino, anche se attaccato a una parola ("allora-ehm": si toglie solo
     * l'ehm). Null se nei paraggi non c'è niente di tenuto: forse è una parola, meglio non tagliare.
     */
    fun locate(frames: List<Frame>, heard: Span): Span? {
        val a = ((heard.startUs - 300_000) / HOP_US).toInt()
        val b = ((heard.endUs + 300_000) / HOP_US).toInt()
        val mid = (heard.startUs + heard.endUs) / 2 / HOP_US
        // Il più lungo nei paraggi, preferendo quelli vicini al punto sentito.
        val r = held(frames, minFrames = 20).filter { it.last >= a && it.first <= b }
            .maxByOrNull { (it.last - it.first) - abs((it.first + it.last) / 2 - mid) / 3 } ?: return null
        val quiet = level(frames, r) - 6f
        val o = onset(frames, r, quiet)
        // Se prima c'è una pausa si parte dall'attacco; se c'è una parola attaccata, un po' dentro il suono tenuto
        // (50 ms: il passaggio dalla vocale della parola all'ehm è graduale, meglio lasciare un filo di ehm che mozzare la parola).
        val quietBefore = (maxOf(0, o - 12) until o).count { frames[it].db < quiet }
        val afterPause = r.first - o <= 6 && quietBefore >= 8
        // Attaccato a una parola deve essere tenuto più a lungo (un quarto di secondo), se no può essere una vocale della parola.
        if (!afterPause && r.last - r.first + 1 < 25) return null
        val from = if (afterPause) (o - 3).coerceAtLeast(0) * HOP_US else r.first * HOP_US + 50_000
        return Span(from, (release(frames, r, quiet) + 3).coerceAtMost(frames.size) * HOP_US)
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val p = i + k; val q = p + len / 2
                    val tr = re[q] * cr - im[q] * ci; val ti = re[q] * ci + im[q] * cr
                    re[q] = re[p] - tr; im[q] = im[p] - ti; re[p] += tr; im[p] += ti
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
