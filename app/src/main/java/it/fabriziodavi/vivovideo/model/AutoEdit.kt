package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Stili del video automatico: ognuno è una "regia" con ritmo, transizioni, effetti e titolo propri.
 * Le durate sono quelle senza musica; con la musica i tagli cadono sui battiti ([beatsPerSlot] battiti a scena).
 */
enum class AutoStyle(
    val label: String,
    val description: String,
    val photoUs: Long,
    val videoUs: Long,
    val beatsPerSlot: Int,
    val transitions: List<TransitionType>,
    val transitionUs: Long,
    val fx: List<ClipFx>,
    val accents: List<ClipFx>,
    val title: TextPreset,
) {
    CINEMA(
        "Cinema", "Solenne: look da film con bande nere, dissolvenze incrociate, luce calda.",
        photoUs = 3_500_000, videoUs = 4_000_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.LEAK, TransitionType.FADE_BLACK),
        transitionUs = 1_000_000,
        fx = listOf(ClipFx(FxType.CINEMATIC, 0.8f)),
        accents = listOf(ClipFx(FxType.RAYS, 0.4f)),
        title = TextPreset.CINEMA,
    ),
    ENERGICO(
        "Energico", "Ritmo serrato: tagli a tempo, colpi sui battiti, zoom, glitch e lampi.",
        photoUs = 1_500_000, videoUs = 1_800_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.ZOOM, TransitionType.WHIP, TransitionType.GLITCH, TransitionType.FLASH, TransitionType.SHAKE, TransitionType.PRISM),
        transitionUs = 350_000,
        fx = listOf(ClipFx(FxType.HIT, 0.6f), ClipFx(FxType.VIVID, 0.35f)),
        accents = listOf(ClipFx(FxType.SPEEDLINES, 0.5f), ClipFx(FxType.PRISM, 0.4f)),
        title = TextPreset.GLITCH,
    ),
    RICORDI(
        "Ricordi", "Nostalgico: pellicola, luce calda, passaggi morbidi.",
        photoUs = 3_000_000, videoUs = 3_000_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.LEAK, TransitionType.BLUR),
        transitionUs = 900_000,
        fx = listOf(ClipFx(FxType.FILM, 0.45f), ClipFx(FxType.LIGHTLEAK, 0.35f), ClipFx(FxType.VIGNETTE, 0.5f)),
        accents = listOf(ClipFx(FxType.BOKEH, 0.35f)),
        title = TextPreset.CASCATA,
    ),
    ELEGANTE(
        "Elegante", "Luminoso e raffinato: colori puliti, luci bokeh e polvere d'oro, titolo dorato.",
        photoUs = 3_000_000, videoUs = 3_200_000, beatsPerSlot = 4,
        transitions = listOf(TransitionType.CROSSFADE, TransitionType.CROSSFADE, TransitionType.ZOOM, TransitionType.LEAK, TransitionType.IRIS),
        transitionUs = 800_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.25f), ClipFx(FxType.GLOW, 0.3f)),
        accents = listOf(ClipFx(FxType.SPARKLE, 0.5f), ClipFx(FxType.BOKEH, 0.4f)),
        title = TextPreset.ORO,
    ),
    VIAGGIO(
        "Viaggio", "Dinamico: scorrimenti, zoom e rotazioni, colori vivaci e raggi di luce.",
        photoUs = 2_200_000, videoUs = 2_600_000, beatsPerSlot = 2,
        transitions = listOf(TransitionType.WHIP, TransitionType.ZOOM, TransitionType.SPIN, TransitionType.CROSSFADE, TransitionType.PRISM),
        transitionUs = 500_000,
        fx = listOf(ClipFx(FxType.VIVID, 0.45f)),
        accents = listOf(ClipFx(FxType.RAYS, 0.45f)),
        title = TextPreset.ONDA,
    );

    /** Durata media di una scena, per scegliere quanti elementi usare. */
    fun averageSlotUs(videoShare: Float): Long = (photoUs * (1 - videoShare) + videoUs * videoShare).toLong()
}

/** Un elemento della galleria candidato al video automatico. */
data class AutoItem(val isVideo: Boolean, val durationUs: Long, val dateMs: Long)

@Serializable
data class AutoOptions(
    val style: AutoStyle = AutoStyle.CINEMA,
    /** Durata desiderata (0 = usa tutto quello che è stato scelto). */
    val targetUs: Long = 60_000_000,
    val title: String = "",
    val subtitle: String = "",
    val seed: Int = 7,
    /** 0 = prima proposta; dalle successive cambiano anche le scene scelte e il punto dei video. */
    val variant: Int = 0,
    /** Formato del video: AUTO = l'orientamento più frequente fra foto e video scelti. */
    val format: VideoFormat = VideoFormat.AUTO,
)

/** Un elemento della galleria da cui è nato il video automatico. */
@Serializable
data class AutoSource(val uri: String, val isVideo: Boolean, val durationUs: Long, val dateMs: Long) {
    fun item() = AutoItem(isVideo, durationUs, dateMs)
}

/**
 * Come è nato un video automatico: serve per chiedere altre proposte. [imported] = clip già importate
 * (per indirizzo originale), così le proposte successive non rileggono le stesse foto.
 */
@Serializable
data class AutoRecipe(
    val sources: List<AutoSource>,
    val options: AutoOptions,
    val music: MusicTrack? = null,
    val round: Int = 1,
    val imported: Map<String, Clip> = emptyMap(),
)

object AutoEdit {
    /** Al massimo tanti elementi, per non importare intere gallerie. */
    const val MAX_ITEMS = 300

    /**
     * Quali elementi usare per stare nella durata: indici in ordine di data, presi a intervalli regolari
     * (così il video racconta tutto il periodo, non solo l'inizio).
     */
    fun pick(items: List<AutoItem>, style: AutoStyle, targetUs: Long, variant: Int = 0): List<Int> {
        if (items.isEmpty()) return emptyList()
        val order = items.indices.sortedBy { items[it].dateMs }
        val videoShare = items.count { it.isVideo }.toFloat() / items.size
        val wanted = if (targetUs <= 0) MAX_ITEMS
            else (targetUs / style.averageSlotUs(videoShare).coerceAtLeast(1)).toInt().coerceAtLeast(2)
        val n = minOf(wanted, order.size, MAX_ITEMS)
        if (n >= order.size) return order
        if (variant == 0 || n < 3) return (0 until n).map { k -> order[(k.toLong() * (order.size - 1) / (n - 1).coerceAtLeast(1)).toInt()] }.distinct()
        // Altre proposte: primo e ultimo restano, in mezzo si sceglie a caso dentro ogni tratto del periodo.
        val rnd = Random(variant * 7919 + 13)
        val size = order.size
        return (0 until n).map { k ->
            when (k) {
                0 -> order.first()
                n - 1 -> order.last()
                else -> {
                    val lo = (k.toLong() * size / n).toInt().coerceIn(1, size - 2)
                    val hi = ((k + 1).toLong() * size / n).toInt().coerceIn(lo + 1, size - 1)
                    order[rnd.nextInt(lo, hi)]
                }
            }
        }.distinct()
    }

    /**
     * Monta il video: [sources] sono le clip importate in ordine di data, [music] il brano (facoltativo,
     * con i battiti già trovati). Restituisce il progetto pronto, tutto ritoccabile nell'editor.
     */
    fun build(name: String, sources: List<Clip>, music: MusicTrack?, o: AutoOptions): Project {
        val style = o.style
        val rnd = Random(o.seed)
        val beats = music?.beatsUs.orEmpty().filter { it > 0 }
        val minSlot = 600_000L
        // Con la musica ogni scena dura [beatsPerSlot] battiti (al ritmo del brano).
        val period = beats.zipWithNext { a, b -> b - a }.sorted().let { if (it.isEmpty()) 0L else it[it.size / 2] }
        val beatSlot = period * style.beatsPerSlot

        // 1. Durata e pezzo di ogni scena (i video: un tratto centrale, dove di solito c'è l'azione).
        val clips = mutableListOf<Clip>()
        var t = 0L
        var prevMotion: MotionStyle? = null
        sources.forEach { src ->
            val base = if (beatSlot in 500_000L..6_000_000L) beatSlot else if (src.isPhoto) style.photoUs else style.videoUs
            val ideal = if (src.isPhoto) base else minOf(base, src.sourceDurationUs)
            val maxLen = if (src.isPhoto) Clip.PHOTO_MAX_US else src.sourceDurationUs
            var len = ideal
            if (beats.size > style.beatsPerSlot) {
                // Il taglio cade sul battito più vicino alla fine ideale (almeno [beatsPerSlot]/2 battiti dopo l'inizio).
                val end = beatAround(beats, t + ideal, t + minSlot, t + maxLen)
                if (end != null) len = end - t
            }
            len = len.coerceIn(minOf(minSlot, maxLen), maxLen)
            var c = if (src.isPhoto) {
                val m = MotionStyle.varied.filter { it != prevMotion }.random(rnd)
                prevMotion = m
                src.copy(trimStartUs = 0, trimEndUs = len, keyframes = m.keyframes(len))
            } else {
                val pos = if (o.variant == 0) 0.4 else 0.2 + rnd.nextDouble() * 0.45
                val start = ((src.sourceDurationUs - len) * pos).toLong().coerceAtLeast(0)
                src.copy(trimStartUs = start, trimEndUs = start + len, speed = ClipSpeed.NORMAL, keyframes = emptyList())
            }
            c = c.copy(muted = music != null || c.muted, fx = style.fx, fadeInUs = 0, fadeOutUs = 0, transitionOut = null)
            clips += c
            t += len
        }
        if (clips.isEmpty()) return Project(name = name)

        // 2. Transizioni (mai due uguali di fila, tranne l'incrociata che è la "normale") ed effetti d'accento.
        var prevT: TransitionType? = null
        for (i in clips.indices) {
            var c = clips[i]
            if (i < clips.lastIndex) {
                val choices = style.transitions.filter { it != prevT || it == TransitionType.CROSSFADE }
                val type = choices.random(rnd)
                prevT = type
                c = c.copy(transitionOut = TransitionSpec(type, minOf(style.transitionUs, c.outputUs / 2, clips[i + 1].outputUs / 2)))
            }
            if (style.accents.isNotEmpty() && i % 3 == 1 + (o.variant % 2)) {
                val a = style.accents[(i / 3) % style.accents.size]
                c = c.copy(fx = c.fx.filter { it.type != a.type } + a)
            }
            clips[i] = c
        }
        // 3. Apertura e chiusura dal nero.
        clips[0] = clips[0].copy(fadeInUs = minOf(1_000_000L, clips[0].outputUs / 2))
        clips[clips.lastIndex] = clips.last().copy(fadeOutUs = minOf(1_500_000L, clips.last().outputUs / 2))

        // Formato e inquadratura: stesso orientamento = riempi, orientamento opposto = sfondo sfocato.
        val format = if (o.format == VideoFormat.AUTO) VideoFormat.majority(sources) else o.format
        var p = Project(name = name, clips = clips, format = format).applyFramePresets()
        val total = p.durationUs

        // 4. Titolo (e sottotitolo) all'inizio, nello stile della regia.
        val texts = mutableListOf<TextItem>()
        if (o.title.isNotBlank()) {
            val dur = minOf(3_500_000L, total - 200_000).coerceAtLeast(TextItem.MIN_US)
            texts += style.title.applyTo(TextItem(text = o.title.trim(), startUs = 200_000, durationUs = dur))
            if (o.subtitle.isNotBlank()) {
                texts += TextPreset.ELEGANTE.applyTo(TextItem(text = o.subtitle.trim(), startUs = 700_000, durationUs = (dur - 500_000).coerceAtLeast(TextItem.MIN_US)))
                    .copy(size = 0.04f, y = 0.12f, endPose = null, upper = false)
            }
        }
        // 5. Musica dall'inizio, sfumata in chiusura.
        val tracks = music?.let {
            listOf(it.copy(startUs = 0, offsetUs = 0, lengthUs = minOf(it.sourceDurationUs, total), fadeInUs = 300_000, fadeOutUs = minOf(2_500_000L, total / 3)))
        }.orEmpty()
        p = p.copy(texts = texts, music = tracks)
        return p
    }

    /** Battito più vicino a [ideal] dentro [lo]…[hi], o null se non ce n'è. */
    private fun beatAround(beats: List<Long>, ideal: Long, lo: Long, hi: Long): Long? =
        beats.filter { it in lo..hi }.minByOrNull { kotlin.math.abs(it - ideal) }
}
