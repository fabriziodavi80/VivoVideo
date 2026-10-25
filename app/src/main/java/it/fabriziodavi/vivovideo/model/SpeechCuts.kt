package it.fabriziodavi.vivovideo.model

import kotlin.math.log10
import kotlin.math.sqrt

/** Un tratto della timeline (µs). */
data class Span(val startUs: Long, val endUs: Long) {
    val lengthUs: Long get() = endUs - startUs
}

/**
 * Pulizia del parlato: trova le pause (silenzi lunghi nell'audio delle clip) e le parole di riempimento
 * ("ehm", "eh", "uhm"…) da togliere, come fanno gli editor per podcast e video parlati.
 */
object SpeechCuts {
    /** Quanto silenzio resta attorno al parlato dopo il taglio (così le frasi non si attaccano). */
    const val KEEP_US = 150_000L
    private const val FRAME = 320 // 20 ms a 16 kHz

    /**
     * Un ehm da togliere: dove sta, la parola sentita dal riconoscimento vocale (null = trovato solo dal suono)
     * e se si taglia ([on]: si può cambiare dalla lista prima di confermare).
     */
    data class Filler(val span: Span, val word: String?, val on: Boolean = true, val doubtful: Boolean = false)

    data class Result(val pauseSpans: List<Span>, val fillerCuts: List<Filler> = emptyList()) {
        /** Tutto quello che si toglie (pause ed ehm accesi), unito. */
        val spans: List<Span> get() = merge(pauseSpans + fillerCuts.filter { it.on }.map { it.span })
        val pauses: Int get() = pauseSpans.size
        val pauseUs: Long get() = pauseSpans.sumOf { it.lengthUs }
        val fillers: Int get() = fillerCuts.count { it.on }
        val fillerUs: Long get() = fillerCuts.filter { it.on }.sumOf { it.span.lengthUs }
        val totalUs: Long get() = spans.sumOf { it.lengthUs }
        val isEmpty: Boolean get() = spans.isEmpty()
        /** Accende o spegne l'ehm numero [i] della lista. */
        fun toggle(i: Int): Result = copy(fillerCuts = fillerCuts.mapIndexed { k, f -> if (k == i) f.copy(on = !f.on) else f })
    }

    /**
     * Silenzi lunghi almeno [minUs] in [pcm] (mono, [rate] Hz), ridotti di [keepUs] per lato.
     * La soglia si adatta al file: fra il rumore di fondo e il livello del parlato.
     */
    fun silences(pcm: FloatArray, rate: Int = 16_000, minUs: Long, keepUs: Long = KEEP_US): List<Span> {
        val frame = FRAME * rate / 16_000
        val n = pcm.size / frame
        if (n < 5) return emptyList()
        val db = FloatArray(n) { f ->
            var s = 0.0
            for (k in f * frame until (f + 1) * frame) s += pcm[k] * pcm[k]
            (20 * log10(sqrt(s / frame) + 1e-9)).toFloat()
        }
        val sorted = db.sorted()
        val floor = sorted[(n * 0.1).toInt()]
        val speech = sorted[(n * 0.9).toInt().coerceAtMost(n - 1)]
        // Niente parlato distinguibile dal fondo (tutto silenzio o tutto rumore): non si taglia nulla.
        if (speech - floor < 10f) return emptyList()
        val thr = floor + 0.3f * (speech - floor)
        val frameUs = frame * 1_000_000L / rate
        val out = mutableListOf<Span>()
        var start = -1
        for (f in 0..n) {
            val quiet = f < n && db[f] < thr
            if (quiet && start < 0) start = f
            if (!quiet && start >= 0) {
                val a = start * frameUs; val b = f * frameUs
                if (b - a >= minUs) {
                    val s = Span(a + keepUs, b - keepUs)
                    if (s.lengthUs >= 100_000) out += s
                }
                start = -1
            }
        }
        return out
    }

    private val FILLER = Regex("^(e+h+m*|e+m+|u+h*m+|h*m+h*|e{3,}|m+h+m+)$")

    /** "Ehm", "eh", "uhm", "mmm", "eee", e "M" (così il riconoscimento vocale scrive spesso "ehm"); "e" da sola no: è una congiunzione. */
    fun isFiller(word: String): Boolean = FILLER.matches(word.lowercase().filter { it.isLetter() })

    /** Le parole di riempimento, con un filo di margine. */
    fun fillers(words: List<SpokenWord>, padUs: Long = 30_000): List<Span> =
        words.filter { isFiller(it.text) }.map { Span((it.startUs - padUs).coerceAtLeast(0), it.endUs + padUs) }

    /**
     * Gli ehm, con i tempi presi dal suono: per ogni "ehm" sentito dal riconoscimento vocale si cerca nei dintorni il suono
     * tenuto che lo è davvero (i tempi delle parole sono imprecisi di qualche decimo di secondo); in più si aggiungono gli
     * ehm isolati fra due pause che il riconoscimento vocale ha saltato. Se attorno a un "ehm" sentito non c'è nulla che
     * suoni come un ehm, resta nella lista ma spento (da controllare: tagliarlo potrebbe togliere una parola).
     */
    fun fillerCuts(pcm: FloatArray, words: List<SpokenWord>): List<Filler> {
        val frames = Hesitations.analyze(pcm)
        val out = mutableListOf<Filler>()
        fun overlaps(a: Span, b: Span) = a.startUs < b.endUs && b.startUs < a.endUs
        words.filter { isFiller(it.text) }.forEach { w ->
            val label = w.text.filter { it.isLetter() }.lowercase()
            val heard = Span(w.startUs, w.endUs)
            val at = if (frames.isEmpty()) null else Hesitations.locate(frames, heard)
            val f = if (at != null) Filler(at, label) else Filler(Span((w.startUs - 30_000).coerceAtLeast(0), w.endUs + 30_000), label, on = false, doubtful = true)
            if (out.none { overlaps(it.span, f.span) }) out += f
        }
        Hesitations.detect(frames).forEach { d -> if (out.none { overlaps(it.span, d.span) }) out += Filler(d.span, null, on = d.sure, doubtful = !d.sure) }
        return out.sortedBy { it.span.startUs }
    }

    /** Unisce i tratti che si toccano o si sovrappongono. */
    fun merge(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        spans.filter { it.lengthUs > 0 }.sortedBy { it.startUs }.forEach { s ->
            val last = out.lastOrNull()
            if (last != null && s.startUs <= last.endUs + 20_000) out[out.lastIndex] = Span(last.startUs, maxOf(last.endUs, s.endUs)) else out += s
        }
        return out
    }

    /** Solo dentro i tratti [allowed] (i video con audio: le foto e le clip mute non si toccano). */
    fun within(spans: List<Span>, allowed: List<Span>): List<Span> = spans.flatMap { s ->
        allowed.mapNotNull { a -> Span(maxOf(s.startUs, a.startUs), minOf(s.endUs, a.endUs)).takeIf { it.lengthUs >= 80_000 } }
    }

    /** Tratti della timeline con video che hanno audio. */
    fun audibleSpans(p: Project): List<Span> {
        val starts = p.clipStartsUs()
        return p.clips.mapIndexedNotNull { i, c -> if (c.isPhoto || c.muted || c.volume <= 0f) null else Span(starts[i], starts[i] + c.outputUs) }
    }

    /** Tutto insieme: pause (se [minPauseUs] > 0) e parole di riempimento, solo dove c'è audio dei video. */
    fun plan(p: Project, pcm: FloatArray, words: List<SpokenWord>, minPauseUs: Long, keepUs: Long = KEEP_US, fillers: Boolean = words.isNotEmpty()): Result {
        val allowed = audibleSpans(p)
        val pauses = if (minPauseUs > 0) within(silences(pcm, minUs = minPauseUs, keepUs = keepUs), allowed) else emptyList()
        // Ogni ehm resta un pezzo solo (il tratto più lungo dentro i video con audio).
        val fill = if (!fillers) emptyList() else fillerCuts(pcm, words).mapNotNull { f ->
            within(listOf(f.span), allowed).maxByOrNull { it.lengthUs }?.let { f.copy(span = it) }
        }
        return Result(pauses, fill)
    }
}

/**
 * Toglie dalla timeline i tratti [spans] (tempi del montaggio): divide le clip ai bordi e toglie i pezzi in mezzo
 * (stacco netto). Testi, livelli e brani dopo il taglio si spostano indietro; i sottotitoli dentro un tratto tolto spariscono.
 */
fun Project.cutTimeline(spans: List<Span>): Project {
    var p = this
    for (s in SpeechCuts.merge(spans).sortedByDescending { it.startUs }) {
        if (s.lengthUs <= 0) continue
        p.splitAt(s.endUs, 20_000)?.let { p = it }
        p.splitAt(s.startUs, 20_000)?.let { p = it }
        val starts = p.clipStartsUs()
        val keep = p.clips.filterIndexed { i, c -> !(starts[i] >= s.startUs - 10_000 && starts[i] + c.outputUs <= s.endUs + 10_000) }
        if (keep.isEmpty() || keep.size == p.clips.size) continue
        val before = p.durationUs
        val q = p.copy(clips = keep)
        p = q.shiftAfterCut(s.startUs, before - q.durationUs)
    }
    return p
}

/** Sposta testi, livelli e brani dopo un taglio di [lenUs] al tempo [atUs]. */
internal fun Project.shiftAfterCut(atUs: Long, lenUs: Long): Project {
    if (lenUs <= 0) return this
    val end = atUs + lenUs
    /** Nuovo inizio e durata di un elemento [start, start+dur), o null se stava tutto dentro il taglio. */
    fun moved(start: Long, dur: Long): Pair<Long, Long>? {
        val stop = start + dur
        return when {
            start >= end -> (start - lenUs) to dur
            stop <= atUs -> start to dur
            start >= atUs && stop <= end -> null
            start >= atUs -> atUs to (stop - end)
            stop <= end -> start to (atUs - start)
            else -> start to (dur - lenUs)
        }
    }
    val texts = texts.mapNotNull { t ->
        val m = moved(t.startUs, t.durationUs)
        when {
            m == null && t.subtitle -> null
            m == null -> t.copy(startUs = atUs, durationUs = TextItem.MIN_US)
            else -> {
                val (st, d) = m
                // I tempi delle parole seguono il parlato tagliato.
                val words = t.wordsUs.map { w ->
                    val abs = t.startUs + w
                    val na = when { abs >= end -> abs - lenUs; abs >= atUs -> atUs; else -> abs }
                    (na - st).coerceIn(0, d.coerceAtLeast(0))
                }
                t.copy(startUs = st, durationUs = d.coerceAtLeast(TextItem.MIN_US), wordsUs = words)
            }
        }
    }
    val layers = layers.map { l ->
        val m = moved(l.startUs, l.durationUs) ?: (atUs to 300_000L)
        l.copy(startUs = m.first, durationUs = m.second.coerceAtLeast(300_000L))
    }
    // I brani non si tagliano (la musica continua): si sposta solo l'inizio di quelli che partono dopo il taglio.
    val music = music.map { m ->
        when {
            m.startUs >= end -> m.copy(startUs = m.startUs - lenUs)
            m.startUs >= atUs -> m.copy(startUs = atUs, offsetUs = m.offsetUs + (end - m.startUs), lengthUs = (m.lengthUs - (end - m.startUs)).coerceAtLeast(100_000))
            else -> m
        }
    }
    return copy(texts = texts, layers = layers, music = music)
}
