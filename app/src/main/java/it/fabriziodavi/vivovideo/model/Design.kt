package it.fabriziodavi.vivovideo.model

import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * Modelli grafici (collage "da stampa"): pagine composte come quelle di una rivista o di un poster,
 * con foto, scritte, riquadri, nastro adesivo, percorsi, palette di colori. Sono disegnati su un foglio di
 * riferimento ([Design.w]×[Design.h], in "pixel CSS" come le prove fatte sulla tavola) e si adattano agli altri
 * formati con gli ancoraggi: un elemento resta attaccato in alto, in basso, al centro, o si allunga.
 */

/** Font dei modelli grafici (file in assets/fonts, licenza OFL). */
enum class DFont { SERIF, SERIF_IT, GROTESK, GROTESK_MED, ANTON, ARCHIVO, MONO, MONO_BOLD, HAND, SANS }

/** Come un elemento segue il foglio quando cambia formato, su un asse. */
/** [SCALE]: posizione e misura crescono in proporzione (griglie che riempiono il foglio). */
enum class Anchor { START, END, CENTER, STRETCH, SCALE }

enum class DOrient { PORTRAIT, SQUARE, LANDSCAPE }

enum class DAlign { LEFT, CENTER, RIGHT }

/** Contorno di una foto. */
/** [TORN] = strappata su tutti i lati (con la fibra bianca se ha il bordo). */
enum class DShape { RECT, CIRCLE, ARCH, PILL, TORN_BOTTOM, TORN_TOP, TORN }

/** Filtro sulla foto: bianco e nero, due colori (quelli del modello), sfocata. */
/** [HALFTONE] = retino di punti (giornale, risografia) con l'inchiostro [DPhoto.ink] sulla carta [DPhoto.paper]. */
enum class DFilter { NONE, BW, DUOTONE, BLUR, HALFTONE }

/** Carta a quadretti, fori della pellicola, grana della carta, righe del quaderno, colonne di testo di giornale. */
/** ... [CONFETTI] coriandoli (cadono nel video), [QR] quadretti tipo codice QR (decorativo), [DISCO] palla a specchi. */
enum class DPatternKind { GRID, SPROCKETS, PAPER, LINES, TEXTLINES, TOPO, SPEED, CONFETTI, QR, DISCO }

/** Riquadro sul foglio di riferimento, con gli ancoraggi orizzontale e verticale. */
data class DBox(val x: Float, val y: Float, val w: Float, val h: Float, val ax: Anchor = Anchor.START, val ay: Anchor = Anchor.START)

/** Colori speciali: il colore scelto, il secondo colore, il fondo. Tutti gli altri valori sono ARGB veri. */
object DColor {
    const val ACC = 1L
    const val ACC2 = 2L
    const val BG = 3L
    const val WHITE = 0xFFFFFFFFL
    const val BLACK = 0xFF111111L
    fun isToken(c: Long) = c in 1L..3L
}

sealed interface DEl { val box: DBox; val rot: Float }

data class DPhoto(
    override val box: DBox,
    val slot: Int,
    val shape: DShape = DShape.RECT,
    /** Angoli arrotondati (pixel del foglio di riferimento). */
    val radius: Float = 0f,
    override val rot: Float = 0f,
    /** Bordo attorno (adesivo, polaroid, carta strappata) e il suo colore. */
    val border: Float = 0f,
    val borderColor: Long = DColor.WHITE,
    /** Bordo più alto sotto (polaroid). */
    val bottomPad: Float = 0f,
    val shadow: Float = 0f,
    val filter: DFilter = DFilter.NONE,
    /** Contorno a poligono: punti in frazioni del riquadro (taglio obliquo). */
    val poly: List<Pt> = emptyList(),
    /** Contorno nero (fumetto, pop). */
    val outline: Float = 0f,
    /** Angoli arrotondati: bit 1 alto-sinistra, 2 alto-destra, 4 basso-destra, 8 basso-sinistra. */
    val corners: Int = 15,
    /** Retino: colore dei punti e della carta (trasparente = solo punti), punti "moltiplicati" sopra quello che c'è sotto. */
    val ink: Long = DColor.BLACK,
    val paper: Long = DColor.WHITE,
    val multiply: Boolean = false,
    /** Solo la persona scontornata, con il bordo bianco da adesivo ([border]) attorno alla sagoma. */
    val cutout: Boolean = false,
    /** Variante dello strappo. */
    val seed: Int = 1,
    /** Spostamento del retino (pixel del foglio), per il fuori registro della risografia. */
    val shiftX: Float = 0f,
    val shiftY: Float = 0f,
) : DEl

/** Pezzo di carta strappata: bordo irregolare sui lati [edges] (1 sopra, 2 destra, 4 sotto, 8 sinistra) con la fibra bianca e la grana. */
data class DTorn(
    override val box: DBox,
    val color: Long,
    override val rot: Float = 0f,
    val seed: Int = 1,
    val edges: Int = 15,
    val fiber: Boolean = true,
    val grain: Boolean = true,
    val shadow: Float = 6f,
) : DEl

/** Scritta a lettere ritagliate da giornali diversi (ogni lettera un font, un colore, un pezzo di carta). */
data class DRansom(override val box: DBox, val id: String, val label: String, val text: String, val seed: Int = 7, override val rot: Float = 0f) : DEl

/** Il calendario del mese della data [text] ("gg.mm.aaaa" o segnaposto), con il giorno cerchiato a mano in [accent]. */
data class DCalendar(override val box: DBox, val id: String, val label: String, val text: String, val color: Long, val accent: Long, override val rot: Float = 0f) : DEl

/** Schizzi di fango o di vernice: una macchia irregolare e tante gocce attorno. */
data class DSplat(override val box: DBox, val color: Long, val seed: Int = 1, override val rot: Float = 0f) : DEl

/** Timbro rotondo (passaporto, "fatto in casa"): due righe dentro un doppio cerchio. */
data class DStamp(override val box: DBox, val id: String, val label: String, val text: String, val color: Long, override val rot: Float = 0f) : DEl

data class DText(
    override val box: DBox,
    /** Chiave della scritta (per cambiarla); vuota = scritta fissa. */
    val id: String,
    /** Nome della scritta nella scheda "Scritte"; vuoto = non si cambia. */
    val label: String,
    /** Testo di partenza, con i segnaposto ({ANNO}, {luogo|Sardegna}…): vedi [DesignTexts]. */
    val text: String,
    val font: DFont,
    val size: Float,
    val color: Long,
    val align: DAlign = DAlign.LEFT,
    /** Interlinea (come line-height). */
    val line: Float = 1f,
    /** Spaziatura fra le lettere (em, come letter-spacing). */
    val track: Float = 0f,
    val upper: Boolean = false,
    override val rot: Float = 0f,
    /** Va a capo da sola dentro la larghezza del riquadro. */
    val wrap: Boolean = false,
    /** Solo il contorno delle lettere (spessore). */
    val stroke: Float = 0f,
    /** La persona della foto [behind] torna davanti alla scritta (copertina di rivista). */
    val behind: Int = -1,
    /** I numeri contano da zero all'entrata nel video. */
    val count: Boolean = false,
    val shadow: Boolean = false,
    /** Font e colore delle righe dopo la prima (es. corsivo colorato). */
    val font2: DFont? = null,
    val color2: Long? = null,
    /** Etichetta a rettangolo (non a pastiglia). */
    val square: Boolean = false,
    /** Pastiglia colorata dietro la scritta e il suo margine. */
    val pill: Long? = null,
    val pad: Float = 0f,
    /** Etichetta del mese della foto [slotMonth] (se ha la data), altrimenti [text]. */
    val slotMonth: Int = -1,
    /** Le righe si allineano in basso al riquadro. */
    val bottom: Boolean = false,
    /** Lettere sfumate dall'alto in basso (cromo, metallo): i colori, uguali distanze. */
    val gradient: List<Long> = emptyList(),
    /** Lettere inclinate in avanti (velocità): come textSkewX, es. -0.25. */
    val skew: Float = 0f,
    /** Alone luminoso di questo colore (neon). */
    val glow: Long? = null,
) : DEl

/** Una parola con una foto dentro ogni lettera. */
data class DPhotoWord(
    override val box: DBox,
    val id: String,
    val label: String,
    val text: String,
    val font: DFont,
    val firstSlot: Int,
    val gap: Float = 10f,
    override val rot: Float = 0f,
) : DEl

data class DRect(
    override val box: DBox,
    val color: Long,
    val radius: Float = 0f,
    override val rot: Float = 0f,
    val circle: Boolean = false,
    /** Solo il contorno, di questo spessore. */
    val stroke: Float = 0f,
    val poly: List<Pt> = emptyList(),
    /** Sfumatura dall'alto in basso: (posizione 0..1, colore). */
    val grad: List<Pair<Float, Long>> = emptyList(),
    val shadow: Float = 0f,
    /** La sfumatura parte dal centro verso fuori (macchie di luce). */
    val radial: Boolean = false,
) : DEl

/** Tratto o forma da un percorso SVG semplice (M L C Q Z, assoluti) in un sistema [vw]×[vh] steso sul riquadro. */
data class DPath(
    override val box: DBox,
    val d: String,
    val vw: Float,
    val vh: Float,
    val stroke: Long? = null,
    val width: Float = 2f,
    val fill: Long? = null,
    val dash: List<Float> = emptyList(),
    /** Nel video il tratto si disegna piano piano. */
    val draw: Boolean = false,
    override val rot: Float = 0f,
) : DEl

data class DPattern(override val box: DBox, val kind: DPatternKind, val color: Long, val step: Float, override val rot: Float = 0f) : DEl

data class DBarcode(override val box: DBox, val color: Long, override val rot: Float = 0f) : DEl

/** I colori principali delle foto, con nome e codice. */
data class DPalette(override val box: DBox, val count: Int, val color: Long, override val rot: Float = 0f) : DEl

enum class DesignFamily(val label: String, val hint: String) {
    EVENTS("Eventi", "Matrimoni, compleanni, feste, lauree, nascite, convegni: sposi da rivista, save the date, cabina foto, usa e getta, anni con le foto, disco, pass VIP, badge, relatore"),
    ACTION("Azione", "Sport d'azione: MTB, bici, corsa, sci, surf. Pettorale, mappa, velocità, fango, action cam, record, sequenza, skipass"),
    PAPER("Carta", "Carta strappata con la fibra bianca, lettere ritagliate, retino, la persona ritagliata come un adesivo"),
    TREND("Tendenze", "I formati più usati del 2026: nota del telefono, cromo Y2K, luce calda, allora e adesso, finestre retrò, dump del mese"),
    MAGAZINE("Rivista", "Scritte grandi come una copertina: la persona può passare davanti al titolo"),
    BENTO("Bento", "Riquadri con numeri grandi, date e luoghi presi dalle foto"),
    SCRAP("Ritagliato", "Carta strappata, nastro adesivo, scritte a mano e adesivi"),
    POSTER("Poster", "Colore pieno, duotono, bianco e nero con un tocco di colore, vetro"),
    THEMES("Temi", "Sport, matrimonio, viaggi, città di notte, cucina"),
}

data class Design(
    val id: String,
    val name: String,
    val family: DesignFamily,
    val orient: DOrient,
    val w: Float,
    val h: Float,
    val bg: Long,
    val accents: List<Long>,
    val els: List<DEl>,
    val accents2: List<Long> = emptyList(),
    val motion: CollageMotion = CollageMotion.CASCADE,
    val grain: Boolean = false,
    /** Per che cosa è pensato (nella scheda dei modelli). */
    val hint: String = "",
) {
    /** Quante foto usa (le celle): le foto in più non si vedono, quelle in meno si ripetono. */
    val slots: Int get() = els.maxOf { e ->
        when (e) {
            is DPhoto -> e.slot + 1
            is DPhotoWord -> e.firstSlot + e.text.count { !it.isWhitespace() }
            else -> 0
        }
    }

    val texts: List<DText> get() = els.filterIsInstance<DText>().filter { it.label.isNotEmpty() }
    val hasBehind: Boolean get() = els.any { it is DText && it.behind >= 0 }
    /** Le foto da scontornare: quelle davanti al titolo e quelle ritagliate come adesivi. */
    val maskSlots: List<Int> get() = (els.filterIsInstance<DText>().filter { it.behind >= 0 }.map { it.behind } +
        els.filterIsInstance<DPhoto>().filter { it.cutout }.map { it.slot }).distinct()
    /** Scritte a lettere ritagliate e timbri (si cambiano come le altre scritte). */
    val extraTexts: List<Triple<String, String, String>> get() = els.mapNotNull { e ->
        when (e) { is DRansom -> Triple(e.id, e.label, e.text); is DStamp -> Triple(e.id, e.label, e.text); is DCalendar -> Triple(e.id, e.label, e.text); else -> null }
    }
    val words: List<DPhotoWord> get() = els.filterIsInstance<DPhotoWord>()
}

/** Dal foglio di riferimento al quadro vero (pixel). */
object DLayout {
    fun scale(d: Design, w: Float, h: Float): Float = when (d.orient) {
        DOrient.PORTRAIT -> w / d.w
        DOrient.LANDSCAPE -> h / d.h
        DOrient.SQUARE -> min(w / d.w, h / d.h)
    }

    /** Posizione e misura su un asse: [pos],[size] di riferimento, [ref] lato del foglio, [out] lato vero. */
    fun axis(pos: Float, size: Float, ref: Float, out: Float, s: Float, a: Anchor): Pair<Float, Float> {
        val extra = out - ref * s
        return when (a) {
            Anchor.START -> pos * s to size * s
            Anchor.END -> pos * s + extra to size * s
            Anchor.CENTER -> (pos + size / 2) * s + extra * ((pos + size / 2) / ref) - size * s / 2 to size * s
            Anchor.STRETCH -> pos * s to size * s + extra
            Anchor.SCALE -> pos * s + extra * (pos / ref) to size * s + extra * (size / ref)
        }
    }

    /** Il riquadro in pixel: sinistra, alto, larghezza, altezza. */
    fun rect(d: Design, b: DBox, w: Float, h: Float): FloatArray {
        val s = scale(d, w, h)
        val (x, ww) = axis(b.x, b.w, d.w, w, s, b.ax)
        val (y, hh) = axis(b.y, b.h, d.h, h, s, b.ay)
        return floatArrayOf(x, y, ww, hh)
    }

    /** Le celle (frazioni del quadro, in ordine di foto) per toccare, scambiare e animare le foto. */
    fun cells(d: Design, ratio: Float): List<CollageCell> {
        val w = 1000f; val h = 1000f / ratio
        val out = arrayOfNulls<CollageCell>(d.slots)
        for (e in d.els) {
            when (e) {
                is DPhoto -> if (out[e.slot] == null) {
                    val r = rect(d, e.box, w, h)
                    out[e.slot] = CollageCell(r[0] / w, r[1] / h, r[2] / w, r[3] / h, rotation = e.rot)
                }
                is DPhotoWord -> {
                    val n = e.text.count { !it.isWhitespace() }
                    val r = rect(d, e.box, w, h)
                    val (rows, perRow) = wordGrid(n)
                    for (k in 0 until n) {
                        val slot = e.firstSlot + k
                        if (out[slot] != null) continue
                        val row = k / perRow; val col = k % perRow
                        out[slot] = CollageCell((r[0] + r[2] * col / perRow) / w, (r[1] + r[3] * row / rows) / h, r[2] / perRow / w, r[3] / rows / h)
                    }
                }
                else -> {}
            }
        }
        return out.map { it ?: CollageCell(0f, 0f, 0f, 0f) }
    }

    /** File e lettere per riga di una parola-foto di [n] lettere. */
    fun wordGrid(n: Int): Pair<Int, Int> = when {
        n <= 3 -> 1 to max(1, n)
        n == 4 -> 2 to 2
        n <= 6 -> 2 to 3
        else -> 3 to (n + 2) / 3
    }
}

/** Quello che si sa delle foto: quando (millisecondi) e dove (nome del posto). */
data class DesignMeta(val dates: List<Long> = emptyList(), val places: List<String> = emptyList(), val photos: Int = 0, val now: Long = System.currentTimeMillis()) {
    companion object {
        fun of(items: List<CollageItem>, now: Long = System.currentTimeMillis()) =
            DesignMeta(items.map { it.takenMs }, items.map { it.place }, items.size, now)
    }
}

/**
 * Segnaposto nelle scritte, riempiti con la data e il luogo delle foto (o con il testo dopo "|" se non si sanno):
 * {ANNO} 2026 · {AA} 26 · {MESE} AGOSTO · {Mese} Agosto · {mese_n} 08 · {data} 14.09.2026 · {giorno} Domenica 14 settembre ·
 * {giorno_breve} 14 SET · {gg} 14 · {data_romana} 14 · IX · 2026 · {dal_al} 03 → 05.06.2026 · {giorni} 3 · {ora} 18:42 ·
 * {stagione} estate · {foto} 9 · {luogo} il posto della prima foto · {luogoN} quello della foto N · {luoghi} tutti, separati da " · ".
 */
object DesignTexts {
    private val months = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
    private val days = listOf("domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato")
    private val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    val monthShort = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

    fun cal(ms: Long): Calendar = Calendar.getInstance(Locale.ITALY).apply { timeInMillis = ms }

    private fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.ITALY) }
    private fun two(n: Int) = n.toString().padStart(2, '0')

    fun season(month0: Int, day: Int): String {
        val md = (month0 + 1) * 100 + day
        return when {
            md >= 1221 || md < 321 -> "inverno"
            md < 621 -> "primavera"
            md < 923 -> "estate"
            else -> "autunno"
        }
    }

    /** Il valore di un segnaposto, o null se non si sa (allora vale il testo dopo "|"). */
    fun value(key: String, m: DesignMeta): String? {
        val dated = m.dates.filter { it > 0 }.sorted()
        val first = dated.firstOrNull()
        val ref = first ?: m.now
        val c = cal(ref)
        val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH); val d = c.get(Calendar.DAY_OF_MONTH)
        val places = m.places.filter { it.isNotBlank() }.distinct()
        return when (key) {
            "ANNO" -> "$y"
            "AA" -> two(y % 100)
            "MESE" -> months[mo].uppercase(Locale.ITALY)
            "Mese" -> cap(months[mo])
            "mese" -> months[mo]
            "mese_n" -> two(mo + 1)
            "gg" -> two(d)
            "data" -> "${two(d)}.${two(mo + 1)}.$y"
            "giorno" -> "${cap(days[c.get(Calendar.DAY_OF_WEEK) - 1])} $d ${months[mo]}"
            "giorno_breve" -> "${two(d)} ${months[mo].take(3).uppercase(Locale.ITALY)}"
            "data_romana" -> "$d · ${roman[mo]} · $y"
            "stagione" -> season(mo, d)
            "Stagione" -> cap(season(mo, d))
            "ora" -> if (first == null) null else "${two(c.get(Calendar.HOUR_OF_DAY))}:${two(c.get(Calendar.MINUTE))}"
            "foto" -> "${m.photos}"
            "giorni" -> if (dated.isEmpty()) null else "${dated.map { val k = cal(it); k.get(Calendar.YEAR) * 400 + k.get(Calendar.DAY_OF_YEAR) }.distinct().let { ds -> ds.max() - ds.min() + 1 }}"
            "dal_al" -> {
                if (dated.isEmpty()) return null
                val a = cal(dated.first()); val b = cal(dated.last())
                val bs = "${two(b.get(Calendar.DAY_OF_MONTH))}.${two(b.get(Calendar.MONTH) + 1)}.${b.get(Calendar.YEAR)}"
                if (a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)) bs
                else if (a.get(Calendar.MONTH) == b.get(Calendar.MONTH)) "${two(a.get(Calendar.DAY_OF_MONTH))} → $bs"
                else "${two(a.get(Calendar.DAY_OF_MONTH))}.${two(a.get(Calendar.MONTH) + 1)} → $bs"
            }
            "luogo" -> places.firstOrNull()
            "luoghi" -> if (places.size < 2) null else places.take(4).joinToString(" · ")
            "n_luoghi" -> if (places.isEmpty()) null else "${places.size}"
            else -> if (key.startsWith("luogo") && key.drop(5).toIntOrNull() != null) m.places.getOrNull(key.drop(5).toInt())?.takeIf { it.isNotBlank() } else null
        }
    }

    /** Riempie i segnaposto di [text]. */
    fun fill(text: String, m: DesignMeta): String {
        val re = Regex("\\{([A-Za-z_0-9]+)(?:\\|([^}]*))?\\}")
        return re.replace(text) { r -> value(r.groupValues[1], m) ?: r.groupValues[2] }
    }

    /** Il testo da mostrare: quello scritto da te se c'è, altrimenti quello del modello con i segnaposto riempiti. */
    fun text(t: DText, spec: CollageSpec, m: DesignMeta): String {
        if (t.slotMonth >= 0) {
            val ms = spec.items.getOrNull(CollagePlan.itemFor(t.slotMonth, spec.items.size))?.takenMs ?: 0L
            if (ms > 0) return monthShort[cal(ms).get(Calendar.MONTH)]
        }
        val s = spec.texts[t.id]?.takeIf { t.label.isNotEmpty() } ?: fill(t.text, m)
        return if (t.upper) s.uppercase(Locale.ITALY) else s
    }

    fun word(w: DPhotoWord, spec: CollageSpec, m: DesignMeta): String =
        (spec.texts[w.id] ?: fill(w.text, m)).uppercase(Locale.ITALY).filter { !it.isWhitespace() }.take(9).ifEmpty { "A" }
}

/** Numeri che contano: "2.340 m" a metà strada diventa "1.170 m" (stesse cifre decimali, stessi separatori). */
object DesignCount {
    private val num = Regex("\\d+(?:[.,]\\d+)*")

    fun at(text: String, u: Float): String {
        if (u >= 1f) return text
        val k = u.coerceIn(0f, 1f)
        return num.replace(text) { r ->
            val s = r.value
            // Il separatore decimale è l'ultima virgola (italiano); i punti sono le migliaia. "5:12" resta due numeri.
            val dec = s.lastIndexOf(',')
            val decimals = if (dec >= 0) s.length - dec - 1 else 0
            val digits = s.filter { it.isDigit() }
            val v = digits.toLongOrNull() ?: return@replace s
            val cur = (v * k).toLong()
            var out = cur.toString().padStart(digits.length, '0')
            if (digits.length > 1 && !s.startsWith("0")) out = out.trimStart('0').ifEmpty { "0" }.padStart(decimals + 1, '0')
            // Rimette i separatori nella stessa posizione dalla fine.
            val seps = s.mapIndexedNotNull { i, ch -> if (!ch.isDigit()) (s.length - i) to ch else null }
            val sb = StringBuilder(out)
            for ((fromEnd, ch) in seps.sortedBy { it.first }) {
                val pos = sb.length - (fromEnd - 1)
                if (pos > 0) sb.insert(pos, ch)
            }
            sb.toString()
        }
    }
}

/** Nomi italiani dei colori (per la palette presa dalle foto). */
object ColorNames {
    fun name(argb: Int): String {
        val r = (argb shr 16 and 255) / 255f; val g = (argb shr 8 and 255) / 255f; val b = (argb and 255) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val s = if (mx == mn) 0f else (mx - mn) / (1 - kotlin.math.abs(2 * l - 1))
        var h = when {
            mx == mn -> 0f
            mx == r -> 60 * (((g - b) / (mx - mn)) % 6)
            mx == g -> 60 * ((b - r) / (mx - mn) + 2)
            else -> 60 * ((r - g) / (mx - mn) + 4)
        }
        if (h < 0) h += 360
        if (s < 0.06f) return when {
            l > 0.9f -> "Bianco"; l > 0.72f -> "Nebbia"; l > 0.5f -> "Grigio"; l > 0.25f -> "Ardesia"; else -> "Carbone"
        }
        val dark = l < 0.3f; val light = l > 0.72f
        // Toni caldi spenti (beige, tortora): i colori più comuni nelle foto.
        if (s < 0.3f && h in 15f..60f) return if (dark) "Caffè" else if (light) "Latte" else "Nocciola"
        if (s < 0.25f && h in 60f..200f) return if (dark) "Bosco" else if (light) "Nebbia" else "Salvia"
        return when {
            h < 15 || h >= 345 -> if (dark) "Bordeaux" else if (light) "Rosa" else "Rosso"
            h < 40 -> if (dark) "Caffè" else if (light) "Pesca" else if (s < 0.45f) "Nocciola" else "Arancio"
            h < 55 -> if (dark) "Bronzo" else if (light) "Sabbia" else if (s < 0.4f) "Latte" else "Ocra"
            h < 70 -> if (dark) "Oliva" else if (light) "Crema" else "Senape"
            h < 160 -> if (dark) "Bosco" else if (light) "Menta" else if (s < 0.3f) "Salvia" else "Verde"
            h < 200 -> if (dark) "Petrolio" else if (light) "Acqua" else "Turchese"
            h < 250 -> if (dark) "Notte" else if (light) "Cielo" else "Blu"
            h < 290 -> if (dark) "Prugna" else if (light) "Lavanda" else "Viola"
            else -> if (dark) "Prugna" else if (light) "Cipria" else "Magenta"
        }
    }

    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}

/** Formati di stampa (millimetri). */
enum class PrintFormat(val label: String, val wMm: Float, val hMm: Float) {
    P10x15("10×15", 100f, 150f), P13x18("13×18", 130f, 180f), P15x20("15×20", 150f, 200f), P20x30("20×30", 200f, 300f),
    P30x45("30×45", 300f, 450f), P50x70("50×70", 500f, 700f), A4("A4", 210f, 297f), A3("A3", 297f, 420f),
    S15("15×15", 150f, 150f), S20("20×20", 200f, 200f), S30("30×30", 300f, 300f),
    L15x10("15×10", 150f, 100f), L18x13("18×13", 180f, 130f), L30x20("30×20", 300f, 200f), A4L("A4 orizz.", 297f, 210f);

    val orient: DOrient get() = when { wMm < hMm -> DOrient.PORTRAIT; wMm == hMm -> DOrient.SQUARE; else -> DOrient.LANDSCAPE }

    /**
     * Misura in pixel e risoluzione (punti per pollice). [shop] = negozio o sito di stampa: 3 mm di margine di taglio per lato
     * (il fondo arriva oltre il bordo, le scritte restano dentro). Al massimo 300 dpi e 5400 pixel sul lato lungo
     * (oltre non serve: un poster si guarda da lontano, e la memoria del telefono ringrazia).
     */
    fun pixels(shop: Boolean): Triple<Int, Int, Int> {
        val w = wMm + if (shop) 2 * BLEED_MM else 0f
        val h = hMm + if (shop) 2 * BLEED_MM else 0f
        val dpi = minOf(300f, MAX_SIDE / (maxOf(w, h) / 25.4f)).toInt()
        return Triple((w / 25.4f * dpi).toInt() / 2 * 2, (h / 25.4f * dpi).toInt() / 2 * 2, dpi)
    }

    companion object {
        const val BLEED_MM = 3f
        /** Bordo bianco per la stampante di casa (che non arriva al bordo del foglio). */
        const val HOME_MARGIN_MM = 4f
        const val MAX_SIDE = 5400f

        /** I formati adatti al collage: verticali per i formati verticali, quadrati per 1:1, orizzontali per gli orizzontali. */
        fun forAspect(a: CollageAspect): List<PrintFormat> = entries.filter { it.orient == a.orient }
    }
}
