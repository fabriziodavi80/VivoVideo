package it.fabriziodavi.vivovideo.model

import kotlin.math.log10
import kotlin.math.sqrt

/** Stili dei jump cut pronti. */
enum class JumpStyle(val label: String, val hint: String) {
    CLEAN("Pulito", "Tolte pause (ed ehm), a ogni taglio l'inquadratura alterna normale e stretta sul volto."),
    DYNAMIC("Dinamico", "Come Pulito, e sulle frasi dette con più forza l'inquadratura si stringe di più (punch-in)."),
    SOCIAL("Social", "Come Pulito, con un lampo (e un whoosh) ogni tanto e i sottotitoli che compaiono parola per parola."),
}

/**
 * Jump cut pronti: si tolgono pause ed ehm (come in "Taglia pause e ehm") e a ogni taglio l'inquadratura cambia,
 * normale e stretta a turno, centrata sul volto: così il salto del taglio sembra voluto, come nei video parlati sui social.
 * Tutto il lavoro è qui (senza Android), così si prova; il ViewModel ci mette solo i volti e il suono del whoosh.
 */
object JumpCuts {
    /** Inquadratura stretta a turno: +14%, abbastanza per vedere il cambio senza perdere nitidezza. */
    const val ZOOM = 1.14f

    /** Punch-in sulle frasi forti (Dinamico). */
    const val PUNCH = 1.28f

    /** Lampo sui tagli (Social): breve, metà alla fine di un pezzo e metà all'inizio del successivo. */
    const val FLASH_US = 240_000L

    /** Un lampo ogni quanti tagli (Social): su tutti sarebbe stancante. */
    const val FLASH_EVERY = 4

    /** Frase forte: almeno tanti dB sopra la mediana dei pezzi, e lunga abbastanza da farci caso. */
    const val PUNCH_DB = 3f
    const val PUNCH_MIN_US = 500_000L

    /** Il whoosh: durata del suono e istante del suo culmine (dall'inizio del suono). */
    const val WHOOSH_US = 500_000L
    const val WHOOSH_PEAK_US = 300_000L

    /**
     * Un pezzo di parlato rimasto fra due tagli, nel file originale ([uri], da [srcStartUs] a [srcEndUs]).
     * [shot] è la ripresa (clip consecutive dello stesso video), [index] la posizione del pezzo nella ripresa.
     */
    data class Piece(
        val uri: String,
        val srcStartUs: Long,
        val srcEndUs: Long,
        val shot: Int,
        val index: Int,
        val zoom: Float,
        val punch: Boolean,
    )

    data class Summary(
        val cuts: Int,
        val removedUs: Long,
        val zoomed: Int,
        val punches: Int,
        val flashes: Int,
        val whooshes: Int,
        val subtitles: Int,
        val beforeUs: Long,
        val afterUs: Long,
    )

    data class Outcome(val project: Project, val summary: Summary, val whooshAtUs: List<Long>)

    /** Riprese: clip consecutive dello stesso video, una di seguito all'altra nel file e senza transizione fra loro. */
    internal fun shots(p: Project): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var from = 0
        for (i in 1..p.clips.size) {
            val prev = p.clips[i - 1]
            val cur = p.clips.getOrNull(i)
            val same = cur != null && !cur.isPhoto && !prev.isPhoto && cur.uri == prev.uri && prev.transitionOut == null &&
                kotlin.math.abs(cur.trimStartUs - prev.trimEndUs) <= 1_000
            if (!same) { out += from until i; from = i }
        }
        return out
    }

    /** Volume (dB) del tratto [aUs, bUs) dell'audio [pcm] (16 kHz mono, tempi della timeline). */
    internal fun levelDb(pcm: FloatArray, aUs: Long, bUs: Long, rate: Int = 16_000): Float {
        val a = (aUs * rate / 1_000_000).toInt().coerceIn(0, pcm.size)
        val b = (bUs * rate / 1_000_000).toInt().coerceIn(a, pcm.size)
        if (b - a < rate / 50) return -100f
        var s = 0.0
        for (k in a until b) s += pcm[k] * pcm[k]
        return (20 * log10(sqrt(s / (b - a)) + 1e-9)).toFloat()
    }

    /**
     * I pezzi che restano dopo aver tolto [spans] (tempi della timeline di [p]), con la loro inquadratura:
     * in ogni ripresa il primo pezzo è normale, poi stretto, normale… Con [style] Dinamico i pezzi detti più forte
     * (dall'audio [pcm]) diventano punch-in. Le foto e le clip mute restano come sono.
     */
    fun pieces(p: Project, spans: List<Span>, style: JumpStyle, pcm: FloatArray?): List<Piece> {
        val cuts = SpeechCuts.merge(spans)
        val starts = p.clipStartsUs()
        data class Raw(val uri: String, val a: Long, val b: Long, val srcA: Long, val srcB: Long, val shot: Int, val index: Int, val audible: Boolean)
        val raw = mutableListOf<Raw>()
        shots(p).forEachIndexed { s, range ->
            var index = 0
            for (i in range) {
                val c = p.clips[i]
                if (c.isPhoto) continue
                val a = starts[i]; val b = a + c.outputUs
                // Il tratto della clip meno i tagli.
                var cursor = a
                val inside = cuts.filter { it.endUs > a && it.startUs < b }
                val parts = mutableListOf<Pair<Long, Long>>()
                for (cut in inside) {
                    if (cut.startUs > cursor) parts += cursor to cut.startUs
                    cursor = maxOf(cursor, cut.endUs)
                }
                if (cursor < b) parts += cursor to b
                parts.forEachIndexed { k, (x, y) ->
                    // Un pezzo continua il precedente se fra loro non c'era un taglio (bordo di clip nella stessa ripresa).
                    val joined = k == 0 && raw.lastOrNull()?.let { it.shot == s && it.b == x } == true
                    val curve = c.speedCurve()
                    val srcA = c.trimStartUs + curve.sourceTimeAt(x - a)
                    val srcB = c.trimStartUs + curve.sourceTimeAt(y - a)
                    if (!joined && raw.lastOrNull()?.shot == s) index++
                    raw += Raw(c.uri, x, y, srcA, srcB, s, index, !c.muted && c.volume > 0f)
                }
            }
        }
        // Frasi forti: rispetto alla mediana dei pezzi del progetto.
        val levels = if (style == JumpStyle.DYNAMIC && pcm != null) raw.map { levelDb(pcm, it.a, it.b) } else null
        val median = levels?.filter { it > -90f }?.sorted()?.let { if (it.isEmpty()) null else it[it.size / 2] }
        var lastPunch = -1
        return raw.mapIndexed { i, r ->
            val audible = r.audible
            val alt = if (r.index % 2 == 1) ZOOM else 1f
            val strong = audible && median != null && levels!![i] >= median + PUNCH_DB && r.b - r.a >= PUNCH_MIN_US &&
                r.index > 0 && lastPunch != r.index - 1
            if (strong) lastPunch = r.index
            Piece(r.uri, r.srcA, r.srcB, r.shot, r.index, if (!audible) 1f else if (strong) PUNCH else alt, strong)
        }
    }

    /**
     * Inquadratura stretta di [zoom] centrata sul soggetto [subject] (centro 0…1 nell'immagine del video), senza mai
     * scoprire i bordi: lo spostamento resta entro quello che lo zoom permette. Se la clip ha già un'inquadratura
     * fatta a mano, la si stringe soltanto.
     */
    fun punchFrame(clip: Clip, zoom: Float, subject: Pair<Float, Float>?, outAspect: Float): Transform {
        val base = clip.frame
        if (zoom == 1f) return base
        if (!base.isIdentity) return base.copy(scale = base.scale * zoom)
        // "Segui il soggetto" già tiene il volto in centro: basta stringere.
        if (subject == null || (clip.reframe != null && clip.fill)) return Transform(scale = zoom)
        val ca = if (clip.width > 0 && clip.height > 0) clip.width.toFloat() / clip.height else outAspect
        val oa = outAspect.coerceAtLeast(0.01f)
        // Quanto è grande l'immagine del video rispetto al quadro (riempi: esce dai bordi; adatta: sta dentro).
        val (kx, ky) = if (clip.fill && !clip.blurBg) {
            if (ca > oa) (ca / oa) to 1f else 1f to (oa / ca)
        } else {
            if (ca > oa) 1f to (oa / ca) else (ca / oa) to 1f
        }
        val u = 0.5f + (subject.first - 0.5f) * kx
        val v = 0.5f + (subject.second - 0.5f) * ky
        val lim = (zoom - 1f) / 2f
        return Transform(x = ((0.5f - u) * zoom).coerceIn(-lim, lim), y = ((0.5f - v) * zoom).coerceIn(-lim, lim), scale = zoom)
    }

    /** Parole per i sottotitoli: senza gli ehm e senza quelle che cadono nei tratti tolti. */
    fun subtitleWords(words: List<SpokenWord>, spans: List<Span>): List<SpokenWord> {
        val cuts = SpeechCuts.merge(spans)
        return words.filter { w ->
            if (SpeechCuts.isFiller(w.text)) return@filter false
            val len = (w.endUs - w.startUs).coerceAtLeast(1)
            val gone = cuts.sumOf { c -> (minOf(c.endUs, w.endUs) - maxOf(c.startUs, w.startUs)).coerceAtLeast(0) }
            gone * 2 < len
        }
    }

    /**
     * Tutto il lavoro: toglie [spans], cambia l'inquadratura a ogni taglio, con Social mette i lampi, i sottotitoli
     * (da [words]) e dice dove vanno i whoosh ([whoosh]). [subject] dà il centro del volto di un video a un istante
     * del file (null = nessun volto: zoom al centro). [outAspect] = larghezza/altezza del video finale.
     */
    fun apply(
        p: Project,
        spans: List<Span>,
        style: JumpStyle,
        pcm: FloatArray? = null,
        words: List<SpokenWord> = emptyList(),
        whoosh: Boolean = true,
        outAspect: Float = 9f / 16f,
        subject: (uri: String, srcUs: Long) -> Pair<Float, Float>? = { _, _ -> null },
    ): Outcome {
        val cuts = SpeechCuts.merge(spans)
        val list = pieces(p, cuts, style, pcm)
        // Sottotitoli prima del taglio: poi seguono il parlato tagliato da soli (come testi della timeline).
        val subs = if (style == JumpStyle.SOCIAL) Subtitles.build(subtitleWords(words, cuts), SubStyle.SOCIAL, SubPosition.BOTTOM, p.durationUs) else emptyList()
        val withSubs = if (subs.isNotEmpty()) p.copy(texts = p.texts.filterNot { it.subtitle } + subs) else p
        val q = withSubs.cutTimeline(cuts)
        // Ogni clip dopo il taglio si riconosce dal suo tratto nel file: si va in ordine, così anche un video
        // usato due volte nel progetto trova i pezzi giusti.
        var at = 0
        val matched = q.clips.map { c ->
            if (c.isPhoto) return@map null
            val mid = (c.trimStartUs + c.trimEndUs) / 2
            val k = (at until list.size).firstOrNull { j -> list[j].uri == c.uri && mid >= list[j].srcStartUs - 1_000 && mid <= list[j].srcEndUs + 1_000 }
            if (k != null) at = k
            k?.let { list[it] }
        }
        val cutShots = list.filter { it.index > 0 }.map { it.shot }.toSet()
        var clips = q.clips.mapIndexed { i, c ->
            val piece = matched[i] ?: return@mapIndexed c
            if (piece.shot !in cutShots) return@mapIndexed c
            val mid = (c.trimStartUs + c.trimEndUs) / 2
            // Il volto serve solo ai pezzi stretti.
            val face = if (piece.zoom != 1f) subject(c.uri, mid) else null
            c.copy(frame = punchFrame(c, piece.zoom, face, outAspect), jumpCut = true)
        }
        // Tagli veri (dentro la stessa ripresa, fra due pezzi diversi): dove sono e, con Social, i lampi.
        val starts = q.copy(clips = clips).clipStartsUs()
        val jumpAt = (1 until clips.size).filter { j ->
            val a = matched[j - 1]; val b = matched[j]
            a != null && b != null && a.shot == b.shot && a.index != b.index
        }
        val flashAt = if (style == JumpStyle.SOCIAL) jumpAt.filterIndexed { n, _ -> n % FLASH_EVERY == FLASH_EVERY / 2 } else emptyList()
        if (flashAt.isNotEmpty()) {
            clips = clips.mapIndexed { i, c ->
                if (i + 1 in flashAt && c.transitionOut == null) c.copy(transitionOut = TransitionSpec(TransitionType.FLASH, FLASH_US)) else c
            }
        }
        val whooshAt = if (whoosh) flashAt.map { starts[it] }.filter { it >= WHOOSH_PEAK_US } else emptyList()
        val out = q.copy(clips = clips)
        val summary = Summary(
            cuts = jumpAt.size,
            removedUs = p.durationUs - out.durationUs,
            zoomed = clips.count { it.jumpCut && it.frame.scale > 1.001f && it.frame.scale < PUNCH - 0.001f },
            punches = clips.count { it.jumpCut && it.frame.scale >= PUNCH - 0.001f },
            flashes = flashAt.size,
            whooshes = whooshAt.size,
            subtitles = out.texts.count { it.subtitle && subs.isNotEmpty() },
            beforeUs = p.durationUs,
            afterUs = out.durationUs,
        )
        return Outcome(out, summary, whooshAt)
    }

    /** Un whoosh in tempo: il suono parte prima del taglio e culmina proprio sul taglio. */
    fun whooshTrack(uri: String, cutUs: Long): MusicTrack = MusicTrack(
        uri = uri, name = "Whoosh", sourceDurationUs = WHOOSH_US, startUs = (cutUs - WHOOSH_PEAK_US).coerceAtLeast(0),
        volume = 0.6f, dialog = true,
    )
}
