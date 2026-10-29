package it.fabriziodavi.vivovideo.model

import kotlin.math.abs
import kotlin.random.Random

/**
 * Rimescola clip: un ordine nuovo per le clip scelte (o per tutte), quando non serve tenere quello dell'album.
 * Se le clip sono tagliate a ritmo, si rimescola il contenuto e restano le durate: i tagli restano sul battito.
 * Due pezzi dello stesso video non finiscono vicini (se si può). Testi, livelli e musica restano dove sono;
 * le transizioni restano sui tagli (al loro posto nella timeline).
 */
object Shuffle {
    /** Un taglio è "sul battito" se dista meno di così da un battito. */
    const val BEAT_TOLERANCE_US = 40_000L

    /** Quanti tentativi per trovare un ordine senza video uguali vicini. */
    private const val TRIES = 300

    data class Result(val project: Project, val onBeat: Boolean, val moved: Int, val sameNeighbours: Int)

    /** Le clip scelte sono tagliate a ritmo? (almeno il 60% dei tagli fra loro sta su un battito) */
    fun onBeat(p: Project, indices: List<Int>): Boolean {
        val beats = p.beatTimes()
        if (beats.isEmpty() || indices.size < 2) return false
        val starts = p.clipStartsUs()
        val cuts = indices.drop(1).map { starts[it] }
        val hits = cuts.count { c -> beats.any { abs(it - c) <= BEAT_TOLERANCE_US } }
        return hits * 10 >= cuts.size * 6
    }

    /** Quanto può durare al massimo la clip nel video (con la sua velocità), allungandola dentro il suo file. */
    internal fun maxOutputUs(c: Clip): Long =
        if (c.isPhoto) Clip.PHOTO_MAX_US
        else if (c.trimmedUs <= 0) 0L
        else (c.outputUs.toDouble() * c.sourceDurationUs / c.trimmedUs).toLong()

    /**
     * La clip [c] con la durata [targetUs] nel video: si tiene l'inizio del taglio e si sposta la fine; se il file
     * finisce prima, si anticipa l'inizio. Per le velocità costanti la durata è esatta.
     */
    internal fun fit(c: Clip, targetUs: Long): Clip {
        if (c.outputUs == targetUs || targetUs <= 0) return c
        if (c.isPhoto) return c.copy(trimEndUs = c.trimStartUs + targetUs.coerceAtMost(Clip.PHOTO_MAX_US))
        val srcLen = (targetUs.toDouble() * c.trimmedUs / c.outputUs.coerceAtLeast(1)).toLong().coerceIn(1, c.sourceDurationUs)
        var start = c.trimStartUs
        if (start + srcLen > c.sourceDurationUs) start = (c.sourceDurationUs - srcLen).coerceAtLeast(0)
        // I keyframe seguono il video: si spostano con l'inizio del taglio.
        val shift = start - c.trimStartUs
        val keys = if (shift == 0L) c.keyframes else c.keyframes.map { it.copy(timeUs = (it.timeUs - shift).coerceAtLeast(0)) }
        var out = c.copy(trimStartUs = start, trimEndUs = start + srcLen, keyframes = keys)
        // Con le curve di velocità la durata non è proporzionale: si aggiusta la fine con una ricerca binaria.
        if (c.speed != ClipSpeed.NORMAL && abs(out.outputUs - targetUs) > 1_000) {
            var lo = start + 1_000; var hi = c.sourceDurationUs
            repeat(40) {
                val mid = (lo + hi) / 2
                if (out.copy(trimEndUs = mid).outputUs < targetUs) lo = mid else hi = mid
            }
            out = out.copy(trimEndUs = (lo + hi) / 2)
        }
        return out
    }

    /**
     * Rimescola le clip [ids] (null o vuoto = tutte) di [p]. Null se ce ne sono meno di due.
     * [seed] cambia l'ordine: "Rimescola ancora" usa un seme nuovo sullo stesso progetto di partenza.
     */
    fun apply(p: Project, ids: Set<String>?, seed: Long): Result? {
        val indices = p.clips.indices.filter { ids.isNullOrEmpty() || p.clips[it].id in ids }
        if (indices.size < 2) return null
        val beat = onBeat(p, indices)
        val contents = indices.map { p.clips[it] }
        val slots = indices.map { p.clips[it].outputUs }
        val rnd = Random(seed)
        val original = contents.map { it.id }

        /** Le clip vicine nel progetto finale, per contare i video uguali uno accanto all'altro. */
        fun build(order: List<Clip>): List<Clip> {
            val list = p.clips.toMutableList()
            indices.forEachIndexed { k, i ->
                val slot = p.clips[i]
                val c = order[k]
                val placed = if (beat) fit(c, slots[k]) else c
                // La transizione resta sul taglio: appartiene al posto, non al contenuto.
                list[i] = placed.copy(transitionOut = slot.transitionOut)
            }
            return list
        }
        fun sameNeighbours(list: List<Clip>): Int = (1 until list.size).count { j ->
            (j in indices || j - 1 in indices) && !list[j].isPhoto && list[j].uri == list[j - 1].uri
        }

        var best: List<Clip>? = null
        var bestScore = Int.MAX_VALUE
        for (attempt in 0 until TRIES) {
            val left = contents.toMutableList()
            val order = mutableListOf<Clip>()
            var ok = true
            for (k in indices.indices) {
                // Il vicino prima: il contenuto appena messo, o la clip del progetto subito prima (se non è fra le scelte).
                val i = indices[k]
                val prev = when {
                    k > 0 && indices[k - 1] == i - 1 -> order[k - 1]
                    i > 0 -> p.clips[i - 1]
                    else -> null
                }
                // Con i tagli a ritmo il contenuto deve poter durare quanto il posto.
                val fits = left.filter { !beat || maxOutputUs(it) + 1_000 >= slots[k] }
                if (fits.isEmpty()) { ok = false; break }
                val good = fits.filter { prev == null || it.isPhoto || it.uri != prev.uri }
                val pick = (good.ifEmpty { fits }).random(rnd)
                order += pick
                left.remove(pick)
            }
            if (!ok) continue
            val list = build(order)
            // Meglio un ordine diverso da quello di partenza.
            val same = if (order.map { it.id } == original) 1000 else 0
            val score = sameNeighbours(list) * 10 + same
            if (score < bestScore) { best = list; bestScore = score }
            if (score == 0) break
        }
        val list = best ?: return null
        val out = p.copy(clips = list)
        val moved = indices.count { p.clips[it].id != list[it].id }
        return Result(out, beat, moved, sameNeighbours(list))
    }
}
