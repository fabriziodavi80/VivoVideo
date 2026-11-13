package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Collage di foto (e video): modelli ispirati ai migliori programmi a pagamento del 2026
 * (Canva, Picsart, Unfold, Mojo, CapCut, MOLDIV): griglie di ogni forma, una foto grande e le altre piccole,
 * tagli diagonali, forme (archi, cerchi, cuore, esagoni, forme morbide), polaroid sparse su carta con il nastro
 * adesivo (scrapbook / photo dump), rullino, impaginazioni editoriali con la grana della pellicola.
 * Lo stesso modello fa un'immagine da salvare o un pezzo di video animato.
 *
 * Coordinate: frazioni del quadro (0..1), x verso destra, y verso il basso.
 */

@Serializable
enum class CollageAspect(val label: String, val w: Int, val h: Int) {
    STORY("9:16", 9, 16), PORTRAIT("4:5", 4, 5), SQUARE("1:1", 1, 1), LANDSCAPE("16:9", 16, 9),
    /** Formati da stampa: 10×15 (anche 20×30, 30×45), A4 (anche A3), 15×10 orizzontale. */
    PHOTO("10×15", 2, 3), PAPER("A4", 1000, 1414), PHOTO_LAND("15×10", 3, 2);

    /** Verticale, quadrato o orizzontale (per i modelli grafici). */
    val orient: DOrient get() = when { w < h -> DOrient.PORTRAIT; w == h -> DOrient.SQUARE; else -> DOrient.LANDSCAPE }

    /** Larghezza / altezza. */
    val ratio: Float get() = w.toFloat() / h

    /** Misura in pixel (pari) con il lato corto di [short] pixel. */
    fun size(short: Int): Pair<Int, Int> =
        if (w <= h) short / 2 * 2 to (short * h / w) / 2 * 2 else (short * w / h) / 2 * 2 to short / 2 * 2

    companion object {
        /** Il formato più vicino a un quadro [w]×[h]. */
        fun closest(w: Int, h: Int): CollageAspect {
            if (w <= 0 || h <= 0) return STORY
            val r = w.toFloat() / h
            return entries.minBy { abs(kotlin.math.ln(it.ratio / r)) }
        }
    }
}

@Serializable
data class Pt(val x: Float, val y: Float)

/** Forma della foto dentro la cella. */
@Serializable
enum class CellShape { RECT, CIRCLE, ARCH, HEART, HEXAGON, BLOB }

/** Cornice attorno alla foto. */
@Serializable
enum class CellFrame { NONE, POLAROID, TAPE }

/**
 * Una cella: il rettangolo [x],[y],[w],[h] (frazioni del quadro) e, per i tagli diagonali, il poligono convesso [poly].
 * [rotation] in gradi; le polaroid hanno la cornice bianca attorno al rettangolo (che è la foto).
 */
@Serializable
data class CollageCell(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val poly: List<Pt> = emptyList(),
    val shape: CellShape = CellShape.RECT,
    val rotation: Float = 0f,
    val frame: CellFrame = CellFrame.NONE,
) {
    val cx: Float get() = x + w / 2
    val cy: Float get() = y + h / 2
}

@Serializable
enum class CollageFamily(val label: String, val hint: String) {
    GRID("Griglie", "Tutte le griglie, con lo spazio fra le foto che scegli tu"),
    HERO("Grande e piccole", "Una foto grande e le altre piccole"),
    DIAGONAL("Tagli diagonali", "Foto separate da tagli obliqui, dinamiche"),
    SHAPES("Forme", "Archi, cerchi, cuore, esagoni e forme morbide su un fondo colorato"),
    POLAROID("Polaroid", "Photo dump: polaroid sparse sulla carta, con il nastro adesivo"),
    FILM("Rullino", "Le foto su una pellicola che scorre"),
    EDITORIAL("Editoriale", "Margini ampi, bianco caldo, grana della pellicola e una scritta"),
}

/** Sfondo. [color2] = sfumatura verso il basso; [paper] = carta con la grana; [blur] = la prima foto sfocata. */
@Serializable
enum class CollageBg(val label: String, val color: Long, val color2: Long? = null, val paper: Boolean = false, val blur: Boolean = false) {
    CLOUD("Bianco caldo", 0xFFF0EEE9),
    PAPER("Carta", 0xFFEDE4D3, paper = true),
    BLACK("Nero", 0xFF111111),
    TERRACOTTA("Terracotta", 0xFFC4704F),
    SAGE("Salvia", 0xFF9CAF88),
    BURGUNDY("Bordeaux", 0xFF6D1A36),
    EMERALD("Smeraldo", 0xFF046A4A),
    BUTTER("Burro", 0xFFF6E7A8),
    LAVENDER("Lavanda", 0xFFB9A7D9),
    BLUE_WASH("Blu cielo", 0xFF1F3A68, 0xFF9CC0E6),
    SUNSET("Tramonto", 0xFFFF7A59, 0xFFFFC371),
    BLUR("Foto sfocata", 0xFF222222, blur = true),
    ;

    /** Colore chiaro: sopra vanno scritte scure. */
    val light: Boolean get() {
        val c = (color2 ?: color)
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return !blur && 0.2126 * r + 0.7152 * g + 0.0722 * b > 150
    }
}

/** Come entrano e si muovono le foto nel video. */
@Serializable
enum class CollageMotion(val label: String, val hint: String) {
    CASCADE("Una dopo l'altra", "Le foto spuntano a turno con un piccolo rimbalzo"),
    SLIDE("Scivolano dentro", "Ogni foto entra dal bordo più vicino"),
    WIPE("Si scoprono", "Ogni foto si scopre come una tendina"),
    DROP("Cadono sul tavolo", "Le foto cadono una sull'altra, ruotando, come polaroid lanciate"),
    ZOOM("Dal primo piano", "Si parte con la prima foto a tutto schermo e si allarga sulla griglia"),
    SCROLL("Scorre", "Il collage scorre lento, come un rullino"),
    STILL("Ferma", "Tutto fermo (solo lo zoom lento dentro le foto, se acceso)"),
}

/** Elementi decorativi del modello. */
@Serializable
enum class CollageDecor { NONE, FILM_V, FILM_H }

@Serializable
data class CollageStyle(
    /** Spazio fra le foto (frazione del lato corto). */
    val gap: Float = 0.012f,
    /** Angoli arrotondati (frazione del lato corto). */
    val radius: Float = 0f,
    val bg: CollageBg = CollageBg.CLOUD,
    val shadow: Boolean = false,
    /** Grana della pellicola su tutto. */
    val grain: Boolean = false,
)

/**
 * Un modello: le celle, se lo spazio fra le foto si applica ([spaced]: griglie e tagli), lo stile e il movimento di partenza.
 * [captionY] = dove va la scritta (centro, frazione dell'altezza).
 */
data class CollageTemplate(
    val id: String,
    val family: CollageFamily,
    val name: String,
    val cells: List<CollageCell>,
    val spaced: Boolean,
    val style: CollageStyle,
    val motion: CollageMotion,
    val decor: CollageDecor = CollageDecor.NONE,
    val captionY: Float = 0.92f,
    /** Modello grafico (pagina composta con scritte e decorazioni): lo disegna [DesignRenderer]. */
    val design: Design? = null,
) {
    val count: Int get() = cells.size
    /** Nome della famiglia (quelle grafiche e quelle a celle). */
    val familyLabel: String get() = design?.family?.label ?: family.label
}

/** Una foto o un video del collage: [panX]/[panY] spostano l'inquadratura nella cella (-1..1), [zoom] ≥ 1. */
@Serializable
data class CollageItem(
    val uri: String,
    val video: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    /** Video: da dove parte nel file e quanto dura il file. */
    val startUs: Long = 0,
    val sourceDurationUs: Long = 0,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    /** Quando è stata scattata (millisecondi, 0 = non si sa) e dove (nome del posto, vuoto = non si sa). */
    val takenMs: Long = 0,
    val place: String = "",
    /** Da dove viene la foto (galleria): per rileggere data e luogo. */
    val source: String = "",
)

/** Tutto quello che serve per rifare il collage (resta nella clip, per modificarlo dopo). */
@Serializable
data class CollageSpec(
    val templateId: String,
    val aspect: CollageAspect = CollageAspect.STORY,
    val items: List<CollageItem> = emptyList(),
    val style: CollageStyle = CollageStyle(),
    val motion: CollageMotion = CollageMotion.CASCADE,
    /** Zoom lento dentro le foto. */
    val kenBurns: Boolean = true,
    val durationUs: Long = 4_000_000,
    val caption: String = "",
    /** Modelli grafici: le scritte cambiate da te (per chiave), il colore e il secondo colore scelti, la persona davanti al titolo. */
    val texts: Map<String, String> = emptyMap(),
    val accent: Long? = null,
    val accent2: Long? = null,
    val behind: Boolean = true,
) {
    companion object {
        val DURATIONS = listOf(3_000_000L, 4_000_000L, 6_000_000L, 8_000_000L)
        const val MAX_ITEMS = 9
    }
}

/** Posa di una cella in un istante: trasparenza, ingrandimento, spostamento (frazioni del quadro), rotazione in più, parte scoperta. */
data class CellPose(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rot: Float = 0f,
    /** Tendina: 0 = coperta, 1 = tutta scoperta. */
    val reveal: Float = 1f,
    /** Zoom dentro la foto (movimento lento). */
    val inner: Float = 1f,
)

/** Inquadratura di tutto il collage: ingrandimento attorno al punto ([cx],[cy]) che va al centro del quadro. */
data class CollageCamera(val scale: Float = 1f, val cx: Float = 0.5f, val cy: Float = 0.5f)

/** Il movimento nel tempo (uguale in anteprima e nel video). [still] = immagine ferma: tutto al suo posto. */
object CollageAnim {
    /** Durata dell'entrata di una cella. */
    fun entryUs(motion: CollageMotion, durationUs: Long): Long =
        min(if (motion == CollageMotion.DROP) 700_000L else 550_000L, (durationUs * 0.35).toLong()).coerceAtLeast(1)

    /** Ritardo fra una cella e la successiva: tutte dentro entro metà durata. */
    fun staggerUs(n: Int, motion: CollageMotion, durationUs: Long): Long {
        if (n <= 1) return 0
        val room = durationUs / 2 - entryUs(motion, durationUs)
        return (room / (n - 1)).coerceIn(60_000L, if (motion == CollageMotion.DROP) 260_000L else 170_000L)
    }

    fun outBack(u: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; val v = u - 1; return 1 + c3 * v * v * v + c1 * v * v }
    fun outCubic(u: Float): Float = 1 - (1 - u).pow(3)
    fun inOut(u: Float): Float = u * u * (3 - 2 * u)

    fun pose(motion: CollageMotion, i: Int, n: Int, cell: CollageCell, tUs: Long, durationUs: Long, kenBurns: Boolean, still: Boolean = false): CellPose {
        val inner = if (!kenBurns || still) 1f else {
            val p = (tUs.toFloat() / durationUs.coerceAtLeast(1)).coerceIn(0f, 1f)
            // A turno: una foto si avvicina, la successiva si allontana.
            if (i % 2 == 0) 1f + 0.08f * p else 1.08f - 0.08f * p
        }
        if (still) return CellPose()
        val start = i * staggerUs(n, motion, durationUs)
        val u = ((tUs - start).toFloat() / entryUs(motion, durationUs)).coerceIn(0f, 1f)
        return when (motion) {
            CollageMotion.CASCADE -> CellPose(alpha = (u * 2.5f).coerceAtMost(1f), scale = 0.55f + 0.45f * outBack(u), inner = inner)
            CollageMotion.SLIDE -> {
                val e = 1 - outCubic(u)
                val d = listOf(cell.cx, 1 - cell.cx, cell.cy, 1 - cell.cy)
                val k = d.indices.minBy { d[it] }
                val (dx, dy) = when (k) { 0 -> -1.1f to 0f; 1 -> 1.1f to 0f; 2 -> 0f to -1.1f; else -> 0f to 1.1f }
                CellPose(alpha = (u * 3).coerceAtMost(1f), dx = dx * e, dy = dy * e, inner = inner)
            }
            CollageMotion.WIPE -> CellPose(reveal = inOut(u), inner = inner)
            CollageMotion.DROP -> {
                val e = 1 - outCubic(u)
                CellPose(alpha = (u * 4).coerceAtMost(1f), dy = -1.25f * e, rot = (if (i % 2 == 0) 22f else -22f) * e, scale = 1f + 0.3f * e, inner = inner)
            }
            CollageMotion.ZOOM, CollageMotion.SCROLL, CollageMotion.STILL -> CellPose(inner = inner)
        }
    }

    /** Inquadratura: con "Dal primo piano" parte sulla prima cella a tutto schermo e si allarga fino al collage intero. */
    fun camera(motion: CollageMotion, first: CollageCell?, aspect: Float, tUs: Long, durationUs: Long, still: Boolean = false): CollageCamera {
        if (still || motion != CollageMotion.ZOOM || first == null) return CollageCamera()
        // La cella riempie il quadro (primo piano).
        val s0 = max(1f / first.w, 1f / first.h).coerceIn(1f, 6f)
        val hold = durationUs * 0.15f
        val u = ((tUs - hold) / (durationUs * 0.45f)).coerceIn(0f, 1f)
        val e = inOut(u)
        // Zoom in scala logaritmica: velocità costante a occhio.
        val s = kotlin.math.exp(kotlin.math.ln(s0) * (1 - e))
        return CollageCamera(s, first.cx + (0.5f - first.cx) * e, first.cy + (0.5f - first.cy) * e)
    }

    /** Scorrimento del rullino (frazioni del quadro, si ripete all'infinito). */
    fun scroll(motion: CollageMotion, tUs: Long, durationUs: Long, still: Boolean = false): Float =
        if (still || motion != CollageMotion.SCROLL) 0f else -0.45f * (tUs.toFloat() / durationUs.coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Trasparenza della scritta: entra a metà, dopo le foto. */
    fun captionAlpha(tUs: Long, durationUs: Long, still: Boolean = false): Float =
        if (still) 1f else ((tUs - durationUs * 0.5f) / (durationUs * 0.2f)).coerceIn(0f, 1f)
}

/** Geometria pura (provata senza Android). */
object CollageGeometry {
    /** Area con segno di un poligono (positiva in senso orario con y verso il basso). */
    fun area(p: List<Pt>): Float {
        var a = 0f
        for (i in p.indices) { val q = p[i]; val r = p[(i + 1) % p.size]; a += q.x * r.y - r.x * q.y }
        return a / 2
    }

    /** Poligono convesso rientrato di [d] su ogni lato (stesse unità dei punti). */
    fun inset(p: List<Pt>, d: Float): List<Pt> {
        if (p.size < 3 || d == 0f) return p
        val s = if (area(p) > 0) 1f else -1f
        // Ogni lato spostato verso l'interno; i nuovi vertici sono le intersezioni dei lati vicini.
        val lines = p.indices.map { i ->
            val a = p[i]; val b = p[(i + 1) % p.size]
            val ex = b.x - a.x; val ey = b.y - a.y
            val len = sqrt(ex * ex + ey * ey).coerceAtLeast(1e-6f)
            // Normale interna: con y verso il basso e verso orario, (−ey, ex) punta dentro.
            val nx = -ey / len * s; val ny = ex / len * s
            floatArrayOf(a.x + nx * d, a.y + ny * d, ex, ey)
        }
        return p.indices.map { i ->
            val l1 = lines[(i - 1 + p.size) % p.size]; val l2 = lines[i]
            val den = l1[2] * l2[3] - l1[3] * l2[2]
            if (abs(den) < 1e-9f) Pt(l2[0], l2[1]) else {
                val t = ((l2[0] - l1[0]) * l2[3] - (l2[1] - l1[1]) * l2[2]) / den
                Pt(l1[0] + l1[2] * t, l1[1] + l1[3] * t)
            }
        }
    }

    /** Il punto dove due rette (per a1-a2 e b1-b2) si incontrano. */
    fun cross(a1: Pt, a2: Pt, b1: Pt, b2: Pt): Pt {
        val d = (a2.x - a1.x) * (b2.y - b1.y) - (a2.y - a1.y) * (b2.x - b1.x)
        val t = ((b1.x - a1.x) * (b2.y - b1.y) - (b1.y - a1.y) * (b2.x - b1.x)) / d
        return Pt(a1.x + (a2.x - a1.x) * t, a1.y + (a2.y - a1.y) * t)
    }

    fun bounds(p: List<Pt>): CollageCell {
        val x0 = p.minOf { it.x }; val y0 = p.minOf { it.y }
        return CollageCell(x0, y0, p.maxOf { it.x } - x0, p.maxOf { it.y } - y0, poly = p)
    }
}

/** Tutti i modelli, generati per il formato del quadro (le forme restano rotonde anche in 9:16). */
object CollageTemplates {
    private val cache = HashMap<CollageAspect, List<CollageTemplate>>()

    fun all(aspect: CollageAspect): List<CollageTemplate> = synchronized(cache) {
        cache.getOrPut(aspect) { designs(aspect) + build(aspect.ratio) }
    }

    /** I modelli grafici per questo formato (verticali, quadrati o orizzontali). */
    fun designs(aspect: CollageAspect): List<CollageTemplate> = Designs.all.filter { fits(it, aspect) }.map { d ->
        CollageTemplate(d.id, CollageFamily.EDITORIAL, d.name, DLayout.cells(d, aspect.ratio), spaced = false,
            style = CollageStyle(gap = 0f, bg = CollageBg.CLOUD, grain = d.grain), motion = d.motion, design = d)
    }

    /** Un modello grafico verticale va bene per 9:16, 4:5, 10×15 e A4; i quadrati anche per 4:5. */
    fun fits(d: Design, aspect: CollageAspect): Boolean = when (d.orient) {
        DOrient.PORTRAIT -> aspect.orient == DOrient.PORTRAIT
        DOrient.SQUARE -> aspect == CollageAspect.SQUARE || aspect == CollageAspect.PORTRAIT
        DOrient.LANDSCAPE -> aspect.orient == DOrient.LANDSCAPE
    }

    /** I modelli a celle per [n] foto, più tutti i modelli grafici (le foto in meno si ripetono). */
    fun forCount(n: Int, aspect: CollageAspect): List<CollageTemplate> = all(aspect).filter { it.design != null || it.count == n }

    fun byId(id: String, aspect: CollageAspect): CollageTemplate? = all(aspect).firstOrNull { it.id == id }

    private fun build(a: Float): List<CollageTemplate> {
        val out = ArrayList<CollageTemplate>()
        grids(out); heroes(out); diagonals(out); shapes(out, a); polaroids(out, a); films(out, a); editorial(out, a)
        // Niente doppioni (stesse celle): resta il primo.
        val seen = HashSet<String>()
        return out.filter { t -> seen.add(t.cells.joinToString("|") { c -> "%.3f,%.3f,%.3f,%.3f,%s,%s".format(java.util.Locale.ROOT, c.x, c.y, c.w, c.h, c.shape, c.poly.size) }) }
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float) = CollageCell(x, y, w, h)

    /** Fasce: [parts] celle per fascia, fasce alte [weights]; [vertical] = fasce verticali (colonne). */
    private fun bands(parts: List<Int>, weights: List<Float> = parts.map { 1f }, vertical: Boolean = false): List<CollageCell> {
        val tot = weights.sum()
        var p = 0f
        val out = ArrayList<CollageCell>()
        parts.forEachIndexed { b, k ->
            val size = weights[b] / tot
            for (j in 0 until k) {
                val along = j.toFloat() / k
                out += if (vertical) rect(p, along, size, 1f / k) else rect(along, p, 1f / k, size)
            }
            p += size
        }
        return out
    }

    /** Composizioni di [n] in fasce da 1 a 4 celle (al massimo 4 fasce, fasce simili fra loro). */
    private fun compositions(n: Int): List<List<Int>> {
        val res = ArrayList<List<Int>>()
        fun go(left: Int, cur: List<Int>) {
            if (left == 0) { if (cur.isNotEmpty()) res += cur; return }
            if (cur.size == 4) return
            for (k in 1..min(4, left)) go(left - k, cur + k)
        }
        go(n, emptyList())
        return res.filter { c -> c.max() - c.min() <= 2 }.sortedWith(compareBy({ it.size != 2 && it.size != 3 }, { it.max() - it.min() }, { it.size }))
    }

    private fun grids(out: MutableList<CollageTemplate>) {
        val style = CollageStyle(gap = 0.012f, bg = CollageBg.CLOUD)
        for (n in 1..9) for (c in compositions(n)) for (vertical in listOf(false, true)) {
            val name = if (c.all { it == c[0] } && c.size > 1) "${if (vertical) c[0] else c.size}×${if (vertical) c.size else c[0]}"
                else c.joinToString("+")
            out += CollageTemplate("g-${if (vertical) "c" else "r"}-${c.joinToString("")}", CollageFamily.GRID, if (n == 1) "Foto intera" else name,
                bands(c, vertical = vertical), spaced = true, style = style, motion = CollageMotion.CASCADE)
        }
    }

    /** Una foto grande e le altre piccole. */
    private fun heroes(out: MutableList<CollageTemplate>) {
        val style = CollageStyle(gap = 0.014f, radius = 0.018f, bg = CollageBg.CLOUD)
        fun add(id: String, name: String, cells: List<CollageCell>, m: CollageMotion = CollageMotion.SLIDE) {
            out += CollageTemplate(id, CollageFamily.HERO, name, cells, spaced = true, style = style, motion = m)
        }
        fun split(k: Int) = if (k <= 4) listOf(k) else listOf((k + 1) / 2, k / 2)
        for (n in 2..9) {
            val rest = split(n - 1)
            add("h-top-$n", "Grande sopra", bands(listOf(1) + rest, listOf(1.7f) + rest.map { 1f }))
            add("h-bottom-$n", "Grande sotto", bands(rest + 1, rest.map { 1f } + 1.7f).let { cs -> cs.takeLast(1) + cs.dropLast(1) })
            add("h-left-$n", "Grande a sinistra", bands(listOf(1) + rest, listOf(1.7f) + rest.map { 1f }, vertical = true))
            add("h-right-$n", "Grande a destra", bands(rest + 1, rest.map { 1f } + 1.7f, vertical = true).let { cs -> cs.takeLast(1) + cs.dropLast(1) })
        }
        // Grande in mezzo, fra due file.
        for (n in listOf(3, 5, 7, 9)) {
            val k = (n - 1) / 2
            val cs = bands(listOf(k, 1, k), listOf(1f, 2f, 1f))
            add("h-mid-$n", "Grande in mezzo", listOf(cs[k]) + cs.take(k) + cs.drop(k + 1), CollageMotion.ZOOM)
        }
        // Cornice a L: grande in un angolo, le piccole lungo due lati.
        fun lShape(g: Int, big: Int, corner: Int): List<CollageCell> {
            val u = 1f / g
            val bx = if (corner % 2 == 0) 0 else g - big
            val by = if (corner < 2) 0 else g - big
            val cells = arrayListOf(rect(bx * u, by * u, big * u, big * u))
            for (r in 0 until g) for (c in 0 until g) if (c !in bx until bx + big || r !in by until by + big) cells += rect(c * u, r * u, u, u)
            return cells
        }
        for ((g, big) in listOf(3 to 2, 4 to 3)) {
            val n = g * g - big * big + 1
            add("h-l-$n", "Grande nell'angolo", lShape(g, big, 0), CollageMotion.ZOOM)
            add("h-l2-$n", "Grande nell'angolo (destra)", lShape(g, big, 1), CollageMotion.ZOOM)
        }
        // Mosaico 4×4: grande 2×2 al centro sopra, attorno le altre.
        val u = 0.25f
        val mosaic = arrayListOf(rect(u, 0f, 2 * u, 2 * u))
        for (r in 0 until 4) for (c in 0 until 4) if (!(r < 2 && c in 1..2)) mosaic += rect(c * u, r * u, u, u)
        // 13 celle sono troppe: si uniscono le file sotto due a due.
        val m9 = mosaic.take(5) + listOf(rect(0f, 0.5f, 0.5f, 0.25f), rect(0.5f, 0.5f, 0.5f, 0.25f), rect(0f, 0.75f, 0.5f, 0.25f), rect(0.5f, 0.75f, 0.5f, 0.25f))
        add("h-mosaic-9", "Mosaico", m9, CollageMotion.CASCADE)
    }

    /** Fasce con i bordi obliqui. */
    private fun diagonals(out: MutableList<CollageTemplate>) {
        val style = CollageStyle(gap = 0.014f, bg = CollageBg.BLACK)
        val s = 0.09f
        fun vBands(n: Int, zig: Boolean): List<CollageCell> {
            // Bordi da (x+s, 0) a (x−s, 1); a zigzag la pendenza si alterna.
            val tops = (0..n).map { i -> if (i == 0 || i == n) i.toFloat() / n else i.toFloat() / n + if (zig && i % 2 == 0) -s else s }
            val bots = (0..n).map { i -> if (i == 0 || i == n) i.toFloat() / n else i.toFloat() / n - if (zig && i % 2 == 0) -s else s }
            return (0 until n).map { i -> CollageGeometry.bounds(listOf(Pt(tops[i], 0f), Pt(tops[i + 1], 0f), Pt(bots[i + 1], 1f), Pt(bots[i], 1f))) }
        }
        fun hBands(n: Int, zig: Boolean): List<CollageCell> = vBands(n, zig).map { c ->
            CollageGeometry.bounds(c.poly.map { Pt(it.y, it.x) }.reversed())
        }
        for (n in 2..4) {
            out += CollageTemplate("d-v-$n", CollageFamily.DIAGONAL, "Colonne oblique", vBands(n, false), true, style, CollageMotion.WIPE)
            if (n >= 3) out += CollageTemplate("d-vz-$n", CollageFamily.DIAGONAL, "Lampo", vBands(n, true), true, style, CollageMotion.WIPE)
        }
        for (n in 2..5) {
            out += CollageTemplate("d-h-$n", CollageFamily.DIAGONAL, "Fasce oblique", hBands(n, false), true, style, CollageMotion.SLIDE)
            if (n >= 3) out += CollageTemplate("d-hz-$n", CollageFamily.DIAGONAL, "Zigzag", hBands(n, true), true, style, CollageMotion.SLIDE)
        }
        // Croce obliqua: quattro foto.
        val t = Pt(0.58f, 0f); val b = Pt(0.42f, 1f); val l = Pt(0f, 0.45f); val r = Pt(1f, 0.55f)
        val x = CollageGeometry.cross(t, b, l, r)
        val cross = listOf(
            listOf(Pt(0f, 0f), t, x, l), listOf(t, Pt(1f, 0f), r, x),
            listOf(l, x, b, Pt(0f, 1f)), listOf(x, r, Pt(1f, 1f), b),
        ).map { CollageGeometry.bounds(it) }
        out += CollageTemplate("d-x-4", CollageFamily.DIAGONAL, "Croce obliqua", cross, true, style, CollageMotion.WIPE)
        // Grande obliqua in alto e due sotto.
        val y0 = 0.62f
        val tri = listOf(
            listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, y0 - 0.1f), Pt(0f, y0 + 0.1f)),
            listOf(Pt(0f, y0 + 0.1f), Pt(0.5f, y0), Pt(0.5f, 1f), Pt(0f, 1f)),
            listOf(Pt(0.5f, y0), Pt(1f, y0 - 0.1f), Pt(1f, 1f), Pt(0.5f, 1f)),
        ).map { CollageGeometry.bounds(it) }
        out += CollageTemplate("d-hero-3", CollageFamily.DIAGONAL, "Grande obliqua", tri, true, style, CollageMotion.SLIDE)
    }

    /** Un quadrato di lato [s] (frazione del lato corto) centrato in ([cx],[cy]), in frazioni del quadro. */
    private fun sq(cx: Float, cy: Float, s: Float, a: Float, sh: CellShape, hRatio: Float = 1f, rot: Float = 0f, frame: CellFrame = CellFrame.NONE): CollageCell {
        val w = if (a < 1) s else s / a
        val h = (if (a < 1) s * a else s) * hRatio
        return CollageCell(cx - w / 2, cy - h / 2, w, h, shape = sh, rotation = rot, frame = frame)
    }

    private fun shapes(out: MutableList<CollageTemplate>, a: Float) {
        fun add(id: String, name: String, bg: CollageBg, cells: List<CollageCell>, m: CollageMotion = CollageMotion.CASCADE) {
            out += CollageTemplate(id, CollageFamily.SHAPES, name, cells.map { fit(it) }, spaced = false, style = CollageStyle(bg = bg, shadow = true), motion = m)
        }
        val tall = a < 0.9f
        val wide = a > 1.1f
        // Archi affiancati (la forma più di tendenza), su una fila o su due. In pixel l'arco è alto [ratio] volte la larghezza.
        fun archRow(n: Int, cy: Float, maxH: Float, ratio: Float): List<CollageCell> {
            val g = 0.035f
            var w = (0.88f - g * (n - 1)) / n
            var h = w * ratio * a
            if (h > maxH) { w *= maxH / h; h = maxH }
            val total = n * w + (n - 1) * g
            return (0 until n).map { i -> CollageCell(0.5f - total / 2 + i * (w + g), cy - h / 2, w, h, shape = CellShape.ARCH) }
        }
        for (n in 1..4) {
            add("s-arch-$n", if (n == 1) "Arco" else "Archi", if (n % 2 == 0) CollageBg.TERRACOTTA else CollageBg.SAGE,
                archRow(n, 0.5f, 0.82f, if (n == 1) 1.35f else 1.6f), if (n == 1) CollageMotion.STILL else CollageMotion.SLIDE)
        }
        for (n in listOf(4, 6)) {
            val perRow = if (wide) n / 2 else 2
            val rows = n / perRow
            val cells = (0 until rows).flatMap { r -> archRow(perRow, 0.5f + (r - (rows - 1) / 2f) * (0.92f / rows), 0.92f / rows - 0.04f, 1.45f) }
            add("s-arches-$n", "Archi in fila", CollageBg.BURGUNDY, cells, CollageMotion.WIPE)
        }
        // Cerchi.
        add("s-circle-1", "Cerchio", CollageBg.BUTTER, listOf(sq(0.5f, 0.5f, 0.82f, a, CellShape.CIRCLE)), CollageMotion.STILL)
        add("s-circle-2", "Due cerchi", CollageBg.LAVENDER, if (tall) listOf(sq(0.42f, 0.33f, 0.68f, a, CellShape.CIRCLE), sq(0.6f, 0.67f, 0.62f, a, CellShape.CIRCLE))
            else listOf(sq(0.36f, 0.45f, 0.62f, a, CellShape.CIRCLE), sq(0.64f, 0.57f, 0.56f, a, CellShape.CIRCLE)))
        // Bolle: una grande e le altre attorno, a zigzag lungo il lato lungo.
        for (n in 3..6) {
            val cells = ArrayList<CollageCell>()
            val big = if (n <= 4) 0.62f else 0.52f
            val small = if (n <= 4) 0.4f else 0.32f
            for (i in 0 until n) {
                val f = (i + 0.5f) / n
                val side = if (i % 2 == 0) -1 else 1
                val s = if (i == 0) big else small
                val along = 0.1f + 0.8f * f
                val across = 0.5f + side * (if (i == 0) 0.06f else 0.2f)
                cells += if (tall || !wide) sq(across, along, s, a, CellShape.CIRCLE) else sq(along, across, s, a, CellShape.CIRCLE)
            }
            add("s-bubbles-$n", "Bolle", if (n % 2 == 0) CollageBg.EMERALD else CollageBg.BLUE_WASH, cells)
        }
        // Cuore.
        add("s-heart-1", "Cuore", CollageBg.BUTTER, listOf(sq(0.5f, 0.5f, 0.86f, a, CellShape.HEART)), CollageMotion.STILL)
        add("s-heart-3", "Cuore e cerchi", CollageBg.LAVENDER, listOf(
            sq(0.5f, if (tall) 0.42f else 0.5f, 0.7f, a, CellShape.HEART),
            sq(if (tall) 0.27f else 0.16f, if (tall) 0.78f else 0.78f, 0.3f, a, CellShape.CIRCLE),
            sq(if (tall) 0.73f else 0.84f, if (tall) 0.8f else 0.24f, 0.26f, a, CellShape.CIRCLE),
        ))
        // Esagoni ad alveare.
        for (n in 3..7) {
            val centers = listOf(0f to 0f, 1f to 0f, 0.5f to 0.866f, -0.5f to 0.866f, -1f to 0f, -0.5f to -0.866f, 0.5f to -0.866f)
            val pick = when (n) { 3 -> listOf(0, 1, 2); 4 -> listOf(0, 1, 2, 3); 5 -> listOf(0, 1, 2, 3, 4); 6 -> listOf(0, 1, 2, 3, 4, 5); else -> centers.indices.toList() }
            val pts = pick.map { centers[it] }
            val mx = (pts.minOf { it.first } + pts.maxOf { it.first }) / 2
            val my = (pts.minOf { it.second } + pts.maxOf { it.second }) / 2
            val spanX = pts.maxOf { it.first } - pts.minOf { it.first } + 1.1f
            val spanY = pts.maxOf { it.second } - pts.minOf { it.second } + 1.2f
            // Lato dell'esagono (frazione del lato corto) perché tutto stia nel quadro.
            val unit = min(0.92f / spanX * (if (a < 1) 1f else a), 0.92f / spanY * (if (a < 1) 1f / a else 1f))
            val cells = pts.map { (px, py) ->
                val ox = (px - mx) * unit * (if (a < 1) 1f else 1f / a)
                val oy = (py - my) * unit * (if (a < 1) a else 1f)
                sq(0.5f + ox, 0.5f + oy, unit * 0.98f, a, CellShape.HEXAGON, 1.1547f)
            }
            add("s-hex-$n", "Alveare", if (n % 2 == 0) CollageBg.BLACK else CollageBg.BUTTER, cells)
        }
        // Forme morbide.
        add("s-blob-1", "Forma morbida", CollageBg.SUNSET, listOf(sq(0.5f, 0.5f, 0.84f, a, CellShape.BLOB)), CollageMotion.STILL)
        add("s-blob-2", "Due forme morbide", CollageBg.SAGE, if (tall) listOf(sq(0.4f, 0.32f, 0.7f, a, CellShape.BLOB), sq(0.6f, 0.68f, 0.66f, a, CellShape.BLOB, rot = 40f))
            else listOf(sq(0.33f, 0.5f, 0.66f, a, CellShape.BLOB), sq(0.67f, 0.5f, 0.6f, a, CellShape.BLOB, rot = 40f)))
        add("s-blob-3", "Tre forme morbide", CollageBg.TERRACOTTA, if (tall) listOf(
            sq(0.38f, 0.22f, 0.56f, a, CellShape.BLOB), sq(0.64f, 0.5f, 0.56f, a, CellShape.BLOB, rot = 70f), sq(0.38f, 0.78f, 0.56f, a, CellShape.BLOB, rot = 140f),
        ) else listOf(sq(0.22f, 0.45f, 0.5f, a, CellShape.BLOB), sq(0.5f, 0.56f, 0.5f, a, CellShape.BLOB, rot = 70f), sq(0.78f, 0.45f, 0.5f, a, CellShape.BLOB, rot = 140f)))
    }

    /** La cella dentro il quadro (con un filo di margine): si sposta e, se serve, si rimpicciolisce. */
    private fun fit(c: CollageCell, m: Float = 0.02f): CollageCell {
        val k = min(1f, min((1 - 2 * m) / c.w, (1 - 2 * m) / c.h))
        val w = c.w * k; val h = c.h * k
        return c.copy(x = (c.cx - w / 2).coerceIn(m, 1 - m - w), y = (c.cy - h / 2).coerceIn(m, 1 - m - h), w = w, h = h)
    }

    /** Numeri pseudo-casuali ripetibili (lo stesso modello è sempre uguale). */
    private class Rnd(var s: Int) { fun next(): Float { s = s * 1103515245 + 12345; return ((s ushr 8) and 0xFFFF) / 65535f } }

    private fun polaroids(out: MutableList<CollageTemplate>, a: Float) {
        fun add(id: String, name: String, cells: List<CollageCell>, m: CollageMotion = CollageMotion.DROP, bg: CollageBg = CollageBg.PAPER) {
            out += CollageTemplate(id, CollageFamily.POLAROID, name, cells, spaced = false,
                style = CollageStyle(bg = bg, shadow = true, grain = true), motion = m, captionY = 0.93f)
        }
        // Photo dump: sparse su una griglia un po' storta.
        for (n in 1..9) {
            val r = Rnd(n * 7919)
            val cols = when { n == 1 -> 1; n <= 4 -> 2; else -> 3 }
            val rows = ceil(n / cols.toFloat()).toInt()
            // Passo fra le colonne e fra le file in lati corti; la polaroid è larga 1,15 e alta 1,3 volte la foto.
            val colSp = 0.9f / cols * (if (a < 1) 1f else a)
            val rowSp = 0.84f / rows * (if (a < 1) 1f / a else 1f)
            val cellS = min(colSp / 1.15f, rowSp / 1.3f) * 0.95f
            val cells = (0 until n).map { i ->
                val c = i % cols; val rr = i / cols
                val inRow = if (rr == rows - 1) n - rr * cols else cols
                val cx = 0.5f + (c - (inRow - 1) / 2f) * (0.9f / cols) + (r.next() - 0.5f) * 0.05f
                val cy = 0.5f + (rr - (rows - 1) / 2f) * (0.84f / rows) + (r.next() - 0.5f) * 0.04f - 0.02f
                val frame = if (i % 3 == 1) CellFrame.TAPE else CellFrame.POLAROID
                sq(cx, cy, cellS.coerceAtMost(if (n == 1) 0.72f else 0.6f), a, CellShape.RECT, rot = (r.next() - 0.5f) * 16f, frame = frame)
            }
            add("p-dump-$n", if (n == 1) "Polaroid" else "Photo dump", cells)
        }
        // Pila al centro: le foto cadono una sull'altra.
        for (n in 2..6) {
            val r = Rnd(n * 104729)
            val cells = (0 until n).map { i ->
                sq(0.5f + (r.next() - 0.5f) * 0.16f, 0.47f + (r.next() - 0.5f) * 0.12f, 0.62f, a, CellShape.RECT, rot = (r.next() - 0.5f) * 30f,
                    frame = if (i == n - 1) CellFrame.TAPE else CellFrame.POLAROID)
            }
            add("p-stack-$n", "Pila", cells)
        }
        // In fila, appese.
        for (n in 2..4) {
            val r = Rnd(n * 31)
            val cells = (0 until n).map { i ->
                val f = (i + 0.5f) / n
                if (a < 0.9f) sq(0.5f + (if (i % 2 == 0) -0.12f else 0.12f), 0.08f + 0.84f * f, min(0.55f, 0.84f / n / a / 1.3f * 0.95f), a, CellShape.RECT, rot = (r.next() - 0.5f) * 12f, frame = CellFrame.TAPE)
                else sq(0.08f + 0.84f * f, 0.5f + (if (i % 2 == 0) -0.04f else 0.04f), min(0.55f, 0.84f / n * a / 1.15f * 0.95f), a, CellShape.RECT, rot = (r.next() - 0.5f) * 12f, frame = CellFrame.TAPE)
            }
            add("p-line-$n", "Appese col nastro", cells, CollageMotion.SLIDE, CollageBg.CLOUD)
        }
        // Griglia ordinata di polaroid, appena storte.
        for (n in listOf(4, 6, 9)) {
            val cols = if (n == 4) 2 else 3
            val rows = n / cols
            val r = Rnd(n * 17)
            val s = min(0.92f / cols * (if (a < 1) 1f else a) / 1.15f, 0.86f / rows * (if (a < 1) 1f / a else 1f) / 1.3f) * 0.92f
            val cells = (0 until n).map { i ->
                sq(0.5f + (i % cols - (cols - 1) / 2f) * (0.92f / cols), 0.48f + (i / cols - (rows - 1) / 2f) * (0.86f / rows), s, a, CellShape.RECT,
                    rot = (r.next() - 0.5f) * 6f, frame = CellFrame.POLAROID)
            }
            add("p-grid-$n", "Bacheca", cells, CollageMotion.CASCADE, CollageBg.SAGE)
        }
    }

    private fun films(out: MutableList<CollageTemplate>, a: Float) {
        val style = CollageStyle(gap = 0.02f, bg = CollageBg.CLOUD, grain = true)
        for (n in 3..5) {
            if (a <= 1.1f) {
                // Pellicola verticale al centro: [n] fotogrammi che riempiono l'altezza (e si ripetono quando scorre).
                val w = if (a < 1) 0.62f else 0.5f
                val cells = (0 until n).map { i -> rect(0.5f - w / 2, i.toFloat() / n, w, 1f / n) }
                out += CollageTemplate("f-v-$n", CollageFamily.FILM, "Rullino", cells, true, style, CollageMotion.SCROLL, CollageDecor.FILM_V)
            }
            if (a >= 0.9f) {
                val h = if (a > 1) 0.56f else 0.42f
                val cells = (0 until n).map { i -> rect(i.toFloat() / n, 0.5f - h / 2, 1f / n, h) }
                out += CollageTemplate("f-h-$n", CollageFamily.FILM, "Rullino orizzontale", cells, true, style, CollageMotion.SCROLL, CollageDecor.FILM_H)
            }
        }
        if (a < 1) for (k in listOf(3, 4)) {
            // Due pellicole affiancate.
            val w = 0.4f
            val cells = (0 until 2 * k).map { i -> rect(if (i < k) 0.06f else 0.54f, (i % k).toFloat() / k, w, 1f / k) }
            out += CollageTemplate("f-v2-${2 * k}", CollageFamily.FILM, "Due rullini", cells, true, style.copy(bg = CollageBg.BLACK), CollageMotion.SCROLL, CollageDecor.FILM_V)
        }
    }

    private fun editorial(out: MutableList<CollageTemplate>, a: Float) {
        val style = CollageStyle(gap = 0f, bg = CollageBg.CLOUD, grain = true)
        fun add(id: String, name: String, cells: List<CollageCell>, m: CollageMotion = CollageMotion.WIPE, capY: Float = 0.91f) {
            out += CollageTemplate(id, CollageFamily.EDITORIAL, name, cells, false, style, m, captionY = capY)
        }
        // Coordinate pensate per il verticale; in orizzontale le foto si dispongono di lato.
        val tall = a < 1
        add("e-frame-1", "Cornice", listOf(if (tall) rect(0.1f, 0.08f, 0.8f, 0.72f) else rect(0.08f, 0.1f, 0.84f, 0.68f)), CollageMotion.STILL, if (tall) 0.88f else 0.88f)
        add("e-stagger-2", "Sfalsate", if (tall) listOf(rect(0.07f, 0.06f, 0.6f, 0.42f), rect(0.33f, 0.44f, 0.6f, 0.42f))
            else listOf(rect(0.06f, 0.08f, 0.44f, 0.62f), rect(0.5f, 0.24f, 0.44f, 0.62f)), CollageMotion.SLIDE, if (tall) 0.93f else 0.93f)
        add("e-stagger-3", "Scala", if (tall) listOf(rect(0.07f, 0.05f, 0.52f, 0.3f), rect(0.41f, 0.31f, 0.52f, 0.3f), rect(0.07f, 0.57f, 0.52f, 0.3f))
            else listOf(rect(0.04f, 0.08f, 0.3f, 0.56f), rect(0.35f, 0.26f, 0.3f, 0.56f), rect(0.66f, 0.08f, 0.3f, 0.56f)), CollageMotion.SLIDE, if (tall) 0.93f else 0.92f)
        add("e-pair-2", "Coppia", if (tall) listOf(rect(0.08f, 0.2f, 0.4f, 0.5f), rect(0.52f, 0.2f, 0.4f, 0.5f))
            else listOf(rect(0.1f, 0.12f, 0.38f, 0.66f), rect(0.52f, 0.12f, 0.38f, 0.66f)), CollageMotion.WIPE, 0.84f)
        add("e-big-3", "Copertina", if (tall) listOf(rect(0.08f, 0.06f, 0.84f, 0.5f), rect(0.08f, 0.59f, 0.405f, 0.24f), rect(0.515f, 0.59f, 0.405f, 0.24f))
            else listOf(rect(0.05f, 0.08f, 0.56f, 0.72f), rect(0.64f, 0.08f, 0.31f, 0.35f), rect(0.64f, 0.45f, 0.31f, 0.35f)), CollageMotion.SLIDE, 0.91f)
        add("e-contact-4", "Provini", if (tall) listOf(rect(0.1f, 0.1f, 0.38f, 0.3f), rect(0.52f, 0.1f, 0.38f, 0.3f), rect(0.1f, 0.44f, 0.38f, 0.3f), rect(0.52f, 0.44f, 0.38f, 0.3f))
            else listOf(rect(0.12f, 0.08f, 0.36f, 0.34f), rect(0.52f, 0.08f, 0.36f, 0.34f), rect(0.12f, 0.46f, 0.36f, 0.34f), rect(0.52f, 0.46f, 0.36f, 0.34f)), CollageMotion.CASCADE, 0.86f)
        add("e-strip-3", "Striscia", if (tall) listOf(rect(0.18f, 0.06f, 0.64f, 0.25f), rect(0.18f, 0.33f, 0.64f, 0.25f), rect(0.18f, 0.6f, 0.64f, 0.25f))
            else listOf(rect(0.06f, 0.16f, 0.28f, 0.6f), rect(0.36f, 0.16f, 0.28f, 0.6f), rect(0.66f, 0.16f, 0.28f, 0.6f)), CollageMotion.WIPE, 0.92f)
    }
}

/** Che cosa disegnare in un istante: celle (con la loro posa) nell'ordine, inquadratura, scorrimento e scritta. */
object CollagePlan {
    /** Quale foto va in quale cella: in ordine, ripetendo se le foto sono meno delle celle. */
    fun itemFor(cell: Int, items: Int): Int = if (items <= 0) -1 else cell % items

    /** Durata utile di un video in una cella: al massimo quella del collage. */
    fun videoTimeUs(item: CollageItem, tUs: Long): Long {
        val avail = (item.sourceDurationUs - item.startUs).coerceAtLeast(1)
        // Se il video è più corto del collage riparte da capo.
        return item.startUs + (tUs % avail)
    }
}

/** Punti di una forma morbida (8 lobi ripetibili), centro 0,0 raggio 1. */
fun blobPoints(seed: Int = 3, n: Int = 8): List<Pt> {
    val out = ArrayList<Pt>()
    var s = seed
    for (i in 0 until n) {
        s = s * 1103515245 + 12345
        val r = 0.82f + 0.18f * (((s ushr 8) and 0xFF) / 255f)
        val ang = (2 * Math.PI * i / n).toFloat()
        out += Pt(cos(ang) * r, sin(ang) * r)
    }
    return out
}

/** Il punto ([x],[y]) sta dentro il poligono convesso [p]. */
fun insideConvex(p: List<Pt>, x: Float, y: Float): Boolean {
    var sign = 0
    for (i in p.indices) {
        val a = p[i]; val b = p[(i + 1) % p.size]
        val c = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
        val sg = if (c > 0) 1 else if (c < 0) -1 else 0
        if (sg != 0) { if (sign == 0) sign = sg else if (sg != sign) return false }
    }
    return true
}

/** La foto in posizione [index] sostituita da [item]; se l'indice non c'è, si aggiunge in fondo. */
fun CollageSpec.withItemReplaced(index: Int, item: CollageItem): CollageSpec =
    if (index in items.indices) copy(items = items.toMutableList().also { it[index] = item })
    else copy(items = (items + item).take(CollageSpec.MAX_ITEMS))
