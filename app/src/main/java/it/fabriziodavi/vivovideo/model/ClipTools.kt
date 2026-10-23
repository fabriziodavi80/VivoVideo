package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** Il video originale di una clip messa al contrario. */
@Serializable
data class ReversedSource(
    val uri: String, val sourceDurationUs: Long, val trimStartUs: Long, val trimEndUs: Long,
    val width: Int, val height: Int, val hdr: Int = 0,
)

/**
 * Stabilizzazione: percorso della camera misurato ([dx], [dy] = spostamento accumulato dell'immagine,
 * in frazioni di larghezza/altezza) a [rateHz] campioni al secondo, dal tempo [fromUs] del file.
 * [strength] 0…1: quanto si leviga il movimento.
 */
@Serializable
data class Stabilization(
    val strength: Float = 0.6f,
    val rateHz: Int = 30,
    val fromUs: Long = 0,
    val dx: List<Float> = emptyList(),
    val dy: List<Float> = emptyList(),
    /** Rotazione accumulata della camera (radianti, verso orario sull'immagine); vuota nelle analisi vecchie. */
    val dr: List<Float> = emptyList(),
)

object StabMath {
    /** Zoom massimo (forza al massimo): oltre l'immagine perde troppa nitidezza. */
    const val MAX_ZOOM = 1.12f

    /** Zoom concesso per una forza 0…1: dal 4% al 12%. */
    fun budget(strength: Float) = 1.04f + 0.08f * strength.coerceIn(0f, 1f)

    /** Media mobile gaussiana; [sigma] in campioni. */
    fun smooth(v: FloatArray, sigma: Float): FloatArray {
        if (v.isEmpty() || sigma <= 0f) return v.copyOf()
        val r = (sigma * 3).roundToInt().coerceAtLeast(1)
        val w = FloatArray(2 * r + 1) { i -> exp(-((i - r) * (i - r)) / (2f * sigma * sigma)) }
        return FloatArray(v.size) { i ->
            var acc = 0f; var ws = 0f
            for (k in -r..r) {
                // Bordi: riflessione "dispari" (una panoramica continua dritta, non si ferma all'inizio e alla fine).
                val j = i + k
                val x = when {
                    j < 0 -> 2 * v[0] - v[(-j).coerceAtMost(v.lastIndex)]
                    j > v.lastIndex -> 2 * v[v.lastIndex] - v[(2 * v.lastIndex - j).coerceAtLeast(0)]
                    else -> v[j]
                }
                acc += x * w[k + r]; ws += w[k + r]
            }
            acc / ws
        }
    }

    /** Correzioni per campione (spostamento e rotazione) e zoom che le contiene. */
    class Plan(val cx: FloatArray, val cy: FloatArray, val zoom: Float, val rateHz: Int, val fromUs: Long, val cr: FloatArray = FloatArray(0)) {
        /** Rotazione di correzione (radianti) al tempo [tUs] del file. */
        fun rotAt(tUs: Long): Float {
            if (cr.isEmpty()) return 0f
            val f = ((tUs - fromUs) * rateHz / 1e6f).coerceIn(0f, (cr.size - 1).toFloat())
            val i = f.toInt().coerceAtMost(cr.size - 1); val j = (i + 1).coerceAtMost(cr.size - 1)
            return cr[i] + (cr[j] - cr[i]) * (f - i)
        }

        /** Correzione (x, y) al tempo [tUs] del file, interpolata. */
        fun at(tUs: Long): Pair<Float, Float> {
            if (cx.isEmpty()) return 0f to 0f
            val f = ((tUs - fromUs) * rateHz / 1e6f).coerceIn(0f, (cx.size - 1).toFloat())
            val i = f.toInt().coerceAtMost(cx.size - 1)
            val j = (i + 1).coerceAtMost(cx.size - 1)
            val u = f - i
            return (cx[i] + (cx[j] - cx[i]) * u) to (cy[i] + (cy[j] - cy[i]) * u)
        }
    }

    /** [aspect] = larghezza/altezza del fotogramma come lo si vede (serve per il margine della rotazione). */
    fun plan(s: Stabilization, aspect: Float = 16f / 9f): Plan {
        val rx = s.dx.toFloatArray(); val ry = s.dy.toFloatArray()
        // Da 0,15 s (leggera) a 1,5 s (forte, quasi un treppiede).
        val sigma = s.rateHz * (0.15f + 1.35f * s.strength.coerceIn(0f, 1f))
        // Zoom concesso dalla forza: il percorso levigato non può allontanarsi dal vero più di metà del margine.
        // Invece di tagliare le correzioni (scatti quando si tocca il limite) il percorso si "piega" dolcemente
        // per restare nel margine: si leviga, si riporta dentro, si leviga di nuovo.
        val maxZoom = budget(s.strength)
        val rr = s.dr.toFloatArray()
        val hasRot = rr.size == rx.size && rr.isNotEmpty()
        // Il margine si divide: 70% agli spostamenti, 30% alla rotazione (al massimo 1°).
        // Ruotare un quadro di θ richiede circa θ·max(a, 1/a) di zoom in più.
        val k = max(aspect, 1f / aspect)
        val margin = maxZoom - 1f
        val limit = (if (hasRot) 0.7f else 1f) * margin / 2f / 1.05f
        val rLimit = if (hasRot) minOf(0.3f * margin / k, 0.0175f) else 0f
        val cx = constrained(rx, sigma, limit)
        val cy = constrained(ry, sigma, limit)
        val cr = if (hasRot) constrained(rr, sigma, rLimit) else FloatArray(0)
        // Lo zoom è quello che serve davvero (spesso meno del massimo).
        val need = max(cx.maxOfOrNull { abs(it) } ?: 0f, cy.maxOfOrNull { abs(it) } ?: 0f)
        val needR = cr.maxOfOrNull { abs(it) } ?: 0f
        val zoom = (1f + 2f * need * 1.05f + needR * k).coerceIn(1f, maxZoom)
        return Plan(cx, cy, zoom, s.rateHz, s.fromUs, cr)
    }

    fun aspectOf(w: Int, h: Int): Float = if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f

    /**
     * Matrice 3×3 (per righe, come android.graphics.Matrix) della correzione al tempo [tUs], nello spazio -1…1:
     * rotazione fatta con le proporzioni vere del quadro (niente immagine "storta"), zoom, poi spostamento.
     * Sull'immagine l'asse y scende, nello spazio -1…1 sale: per questo spostamento verticale e rotazione cambiano segno.
     */
    fun matrixValues(plan: Plan, tUs: Long, aspect: Float): FloatArray {
        val (cx, cy) = plan.at(tUs)
        val phi = -plan.rotAt(tUs)
        val z = plan.zoom
        val c = kotlin.math.cos(phi); val sn = kotlin.math.sin(phi)
        return floatArrayOf(z * c, -z * sn / aspect, 2f * cx * z, z * aspect * sn, z * c, -2f * cy * z, 0f, 0f, 1f)
    }

    /** Movimento fra due fotogrammi: spostamento (pixel, con i decimali) e rotazione (radianti). */
    data class Motion(val dx: Float, val dy: Float, val rot: Float)

    /**
     * Movimento del contenuto da [a] a [b] (grigio [w]×[h]): spostamento al centro con precisione al decimo di pixel,
     * rotazione dalla differenza di spostamento fra lati opposti (sinistra/destra, sopra/sotto).
     */
    fun motion(a: ByteArray, b: ByteArray, w: Int, h: Int, range: Int = 16): Motion {
        val c = shiftSub(a, b, w, h, w / 10, w - w / 10, h / 10, h - h / 10, range)
        // Bande laterali: la differenza di spostamento verticale fra destra e sinistra dice la rotazione.
        val l = shiftSub(a, b, w, h, w / 12, w * 4 / 12, h / 5, h * 4 / 5, range, c)
        val r = shiftSub(a, b, w, h, w * 8 / 12, w * 11 / 12, h / 5, h * 4 / 5, range, c)
        val t = shiftSub(a, b, w, h, w / 5, w * 4 / 5, h / 12, h * 4 / 12, range, c)
        val bt = shiftSub(a, b, w, h, w / 5, w * 4 / 5, h * 8 / 12, h * 11 / 12, range, c)
        val dxLR = (w * 8 / 12 + w * 11 / 12 - w / 12 - w * 4 / 12) / 2f
        val dyTB = (h * 8 / 12 + h * 11 / 12 - h / 12 - h * 4 / 12) / 2f
        val rot1 = (r.second - l.second) / dxLR
        val rot2 = -(bt.first - t.first) / dyTB
        // Se le due stime non vanno d'accordo (soggetti che si muovono) la rotazione non si usa.
        val rot = if (abs(rot1 - rot2) < 0.01f) (rot1 + rot2) / 2f else 0f
        return Motion(c.first, c.second, rot.coerceIn(-0.05f, 0.05f))
    }

    /** Spostamento con i decimali nella zona [x0,x1)×[y0,y1): grossolano a metà risoluzione, fine, poi parabola sul minimo. */
    fun shiftSub(a: ByteArray, b: ByteArray, w: Int, h: Int, x0: Int, x1: Int, y0: Int, y1: Int, range: Int, guess: Pair<Float, Float>? = null): Pair<Float, Float> {
        val (bx, by) = if (guess == null) {
            val w2 = w / 2; val h2 = h / 2
            val a2 = half(a, w, h); val b2 = half(b, w, h)
            val r2 = (range / 2).coerceAtLeast(1)
            val (cx, cy) = bestIn(a2, b2, w2, x0 / 2, x1 / 2, y0 / 2, y1 / 2, h2, -r2..r2, -r2..r2, 1)
            bestIn(a, b, w, x0, x1, y0, y1, h, (cx * 2 - 2)..(cx * 2 + 2), (cy * 2 - 2)..(cy * 2 + 2), 2)
        } else {
            val gx = Math.round(guess.first); val gy = Math.round(guess.second)
            bestIn(a, b, w, x0, x1, y0, y1, h, (gx - 3)..(gx + 3), (gy - 3)..(gy + 3), 2)
        }
        return refine(a, b, w, h, x0, x1, y0, y1, bx.toFloat(), by.toFloat(), if (guess == null) 2 else 3)
    }

    /**
     * Rifinitura con i decimali (metodo di Lucas-Kanade): partendo dallo spostamento intero, usa le pendenze
     * dell'immagine per trovare lo spostamento esatto in x e y insieme (anche in diagonale).
     */
    private fun refine(a: ByteArray, b: ByteArray, w: Int, h: Int, x0: Int, x1: Int, y0: Int, y1: Int, sx: Float, sy: Float, step: Int): Pair<Float, Float> {
        fun px(img: ByteArray, x: Int, y: Int) = (img[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)].toInt() and 255).toFloat()
        fun bil(x: Float, y: Float): Float {
            val xi = kotlin.math.floor(x).toInt(); val yi = kotlin.math.floor(y).toInt()
            val fx = x - xi; val fy = y - yi
            val p = px(b, xi, yi) + (px(b, xi + 1, yi) - px(b, xi, yi)) * fx
            val q = px(b, xi, yi + 1) + (px(b, xi + 1, yi + 1) - px(b, xi, yi + 1)) * fx
            return p + (q - p) * fy
        }
        var ux = sx; var uy = sy
        val m = 2 + kotlin.math.ceil(maxOf(abs(sx), abs(sy))).toInt()
        val xa = maxOf(x0, m); val xb = minOf(x1, w - m); val ya = maxOf(y0, m); val yb = minOf(y1, h - m)
        if (xb <= xa || yb <= ya) return sx to sy
        repeat(4) {
            var gxx = 0.0; var gxy = 0.0; var gyy = 0.0; var ex = 0.0; var ey = 0.0
            var y = ya
            while (y < yb) {
                var x = xa
                while (x < xb) {
                    val bx = x + ux; val by = y + uy
                    val ix = (bil(bx + 1, by) - bil(bx - 1, by)) / 2f
                    val iy = (bil(bx, by + 1) - bil(bx, by - 1)) / 2f
                    val e = px(a, x, y) - bil(bx, by)
                    gxx += ix * ix; gxy += ix * iy; gyy += iy * iy; ex += ix * e; ey += iy * e
                    x += step
                }
                y += step
            }
            val det = gxx * gyy - gxy * gxy
            if (det < 1e-6) return ux to uy
            val dx = ((gyy * ex - gxy * ey) / det).toFloat().coerceIn(-1f, 1f)
            val dy = ((gxx * ey - gxy * ex) / det).toFloat().coerceIn(-1f, 1f)
            ux += dx; uy += dy
            if (abs(dx) < 0.01f && abs(dy) < 0.01f) return ux to uy
        }
        return ux to uy
    }

    private fun sadAt(a: ByteArray, b: ByteArray, w: Int, h: Int, x0: Int, x1: Int, y0: Int, y1: Int, dx: Int, dy: Int, step: Int): Long {
        val xa = maxOf(x0, -dx); val xb = minOf(x1, w - dx)
        val ya = maxOf(y0, -dy); val yb = minOf(y1, h - dy)
        if (xb <= xa || yb <= ya) return Long.MAX_VALUE / 4
        var sad = 0L; var n = 0L
        var y = ya
        while (y < yb) {
            val ra = y * w; val rb = (y + dy) * w + dx
            var x = xa
            while (x < xb) { sad += abs((a[ra + x].toInt() and 255) - (b[rb + x].toInt() and 255)); n++; x += step }
            y += step
        }
        // Media per pixel (le zone hanno misure diverse a seconda dello spostamento).
        return if (n == 0L) Long.MAX_VALUE / 4 else sad * 1024 / n
    }

    private fun bestIn(a: ByteArray, b: ByteArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int, h: Int, rx: IntRange, ry: IntRange, step: Int): Pair<Int, Int> {
        var best = Long.MAX_VALUE; var bx = 0; var by = 0
        for (dy in ry) for (dx in rx) {
            val sad = sadAt(a, b, w, h, x0, x1, y0, y1, dx, dy, step)
            if (sad < best || (sad == best && abs(dx) + abs(dy) < abs(bx) + abs(by))) { best = sad; bx = dx; by = dy }
        }
        return bx to by
    }

    /** Correzioni (levigato − vero) che restano entro ±[limit], senza scatti. */
    fun constrained(raw: FloatArray, sigma: Float, limit: Float): FloatArray {
        if (raw.isEmpty()) return raw
        var p = smooth(raw, sigma)
        repeat(8) {
            if (raw.indices.all { abs(p[it] - raw[it]) <= limit }) return FloatArray(raw.size) { p[it] - raw[it] }
            val inside = FloatArray(raw.size) { raw[it] + (p[it] - raw[it]).coerceIn(-limit, limit) }
            p = smooth(inside, sigma * 0.5f)
        }
        return FloatArray(raw.size) { (p[it] - raw[it]).coerceIn(-limit, limit) }
    }

    /**
     * Spostamento (in pixel) del contenuto da [a] a [b], immagini in scala di grigi [w]×[h]:
     * ricerca a due livelli (metà risoluzione ±[range], poi rifinitura ±2).
     */
    fun shift(a: ByteArray, b: ByteArray, w: Int, h: Int, range: Int = 8): Pair<Int, Int> {
        val w2 = w / 2; val h2 = h / 2
        val a2 = half(a, w, h); val b2 = half(b, w, h)
        val r2 = (range / 2).coerceAtLeast(1)
        val (cx, cy) = best(a2, b2, w2, h2, -r2..r2, -r2..r2, step = 1)
        return best(a, b, w, h, (cx * 2 - 2)..(cx * 2 + 2), (cy * 2 - 2)..(cy * 2 + 2), step = 2)
    }

    private fun half(src: ByteArray, w: Int, h: Int): ByteArray {
        val w2 = w / 2; val h2 = h / 2
        return ByteArray(w2 * h2) { i ->
            val x = (i % w2) * 2; val y = (i / w2) * 2
            val s = (src[y * w + x].toInt() and 255) + (src[y * w + x + 1].toInt() and 255) +
                (src[(y + 1) * w + x].toInt() and 255) + (src[(y + 1) * w + x + 1].toInt() and 255)
            (s / 4).toByte()
        }
    }

    /** Spostamento (dx, dy) che rende b(x+dx, y+dy) più simile ad a(x, y) nella parte centrale. */
    private fun best(a: ByteArray, b: ByteArray, w: Int, h: Int, rx: IntRange, ry: IntRange, step: Int): Pair<Int, Int> {
        val mx = maxOf(abs(rx.first), abs(rx.last)) + 1
        val my = maxOf(abs(ry.first), abs(ry.last)) + 1
        val x0 = maxOf(mx, w / 10); val x1 = minOf(w - mx, w - w / 10)
        val y0 = maxOf(my, h / 10); val y1 = minOf(h - my, h - h / 10)
        if (x1 <= x0 || y1 <= y0) return 0 to 0
        var bestSad = Long.MAX_VALUE; var bx = 0; var by = 0
        for (dy in ry) for (dx in rx) {
            var sad = 0L
            var y = y0
            while (y < y1) {
                val ra = y * w; val rb = (y + dy) * w + dx
                var x = x0
                while (x < x1) {
                    sad += abs((a[ra + x].toInt() and 255) - (b[rb + x].toInt() and 255))
                    x += step
                }
                y += step
            }
            // A parità, preferisce lo spostamento più piccolo (zone uniformi).
            if (sad < bestSad || (sad == bestSad && abs(dx) + abs(dy) < abs(bx) + abs(by))) { bestSad = sad; bx = dx; by = dy }
        }
        return bx to by
    }

    /** Spostamento del contenuto ruotato come lo vede chi guarda (rotazione del video in gradi, senso orario). */
    fun rotate(dx: Float, dy: Float, rotation: Int): Pair<Float, Float> = when (((rotation % 360) + 360) % 360) {
        90 -> -dy to dx
        180 -> -dx to -dy
        270 -> dy to -dx
        else -> dx to dy
    }
}

object ClipTools {
    /** La clip al contrario, letta da [uri] (lungo [durUs]): keyframe e curva di velocità si specchiano. */
    fun reversed(c: Clip, uri: String, durUs: Long, w: Int, h: Int): Clip {
        val k = if (c.trimmedUs > 0) durUs.toDouble() / c.trimmedUs else 1.0
        return c.copy(
            uri = uri, sourceDurationUs = durUs, trimStartUs = 0, trimEndUs = durUs, width = w, height = h, hdr = 0,
            keyframes = c.keyframes.map { it.copy(timeUs = (durUs - it.timeUs * k).toLong().coerceIn(0, durUs)) }.sortedBy { it.timeUs },
            speedPoints = c.speedPoints.map { SpeedPoint(1f - it.t, it.speed) }.sortedBy { it.t },
            reversed = ReversedSource(c.uri, c.sourceDurationUs, c.trimStartUs, c.trimEndUs, c.width, c.height, c.hdr),
            stab = null, reframe = null, person = null,
        )
    }

    /** Torna al video originale (con i keyframe e la curva rimessi nel verso giusto). */
    fun unreversed(c: Clip): Clip {
        val o = c.reversed ?: return c
        val len = o.trimEndUs - o.trimStartUs
        val k = if (c.trimmedUs > 0) len.toDouble() / c.trimmedUs else 1.0
        return c.copy(
            uri = o.uri, sourceDurationUs = o.sourceDurationUs, trimStartUs = o.trimStartUs, trimEndUs = o.trimEndUs,
            width = o.width, height = o.height, hdr = o.hdr,
            keyframes = c.keyframes.map { it.copy(timeUs = ((c.trimmedUs - it.timeUs) * k).toLong().coerceIn(0, len)) }.sortedBy { it.timeUs },
            speedPoints = c.speedPoints.map { SpeedPoint(1f - it.t, it.speed) }.sortedBy { it.t },
            reversed = null,
        )
    }

    /** Fermo immagine: una foto da [uri] lunga [durUs], con l'aspetto della clip [from]. */
    fun freezeClip(from: Clip, uri: String, w: Int, h: Int, durUs: Long = 2_000_000) = Clip(
        id = UUID.randomUUID().toString(), uri = uri, sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = durUs,
        isPhoto = true, width = w, height = h, fps = 30f, freeze = true,
        grade = from.grade, frame = from.frame, fill = from.fill, blurBg = from.blurBg, log = false,
    )
}

/**
 * Inserisce [clip] nel tempo [timeUs] della timeline: divide la clip lì sotto (se il punto cade vicino a un bordo,
 * la mette prima o dopo). Testi e livelli che vengono dopo si spostano della sua durata, così restano sulle stesse immagini.
 */
fun Project.insertAt(timeUs: Long, clip: Clip): Project {
    val (index, off) = locate(timeUs) ?: return addClips(listOf(clip))
    val c = clips[index]
    val split = if (off > 100_000 && c.outputUs - off > 100_000) splitAt(timeUs) else null
    val at = when {
        split != null -> index + 1
        off <= 100_000 -> index
        else -> index + 1
    }
    val base = split ?: this
    val point = base.clipStartsUs().getOrElse(at) { base.durationUs }
    val d = clip.outputUs
    return base.addClips(listOf(clip), at).copy(
        texts = texts.map { if (it.startUs >= point) it.copy(startUs = it.startUs + d) else it },
        layers = layers.map { if (it.startUs >= point) it.copy(startUs = it.startUs + d) else it },
    )
}
