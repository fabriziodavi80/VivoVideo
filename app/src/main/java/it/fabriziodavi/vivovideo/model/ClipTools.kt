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
)

object StabMath {
    /** Zoom massimo usato per nascondere i bordi (oltre, la stabilizzazione si limita). */
    const val MAX_ZOOM = 1.2f

    /** Media mobile gaussiana; [sigma] in campioni. */
    fun smooth(v: FloatArray, sigma: Float): FloatArray {
        if (v.isEmpty() || sigma <= 0f) return v.copyOf()
        val r = (sigma * 3).roundToInt().coerceAtLeast(1)
        val w = FloatArray(2 * r + 1) { i -> exp(-((i - r) * (i - r)) / (2f * sigma * sigma)) }
        return FloatArray(v.size) { i ->
            var acc = 0f; var ws = 0f
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, v.lastIndex) // bordi: si ripete il valore estremo
                acc += v[j] * w[k + r]; ws += w[k + r]
            }
            acc / ws
        }
    }

    /** Correzioni per campione e zoom che le contiene. */
    class Plan(val cx: FloatArray, val cy: FloatArray, val zoom: Float, val rateHz: Int, val fromUs: Long) {
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

    fun plan(s: Stabilization): Plan {
        val rx = s.dx.toFloatArray(); val ry = s.dy.toFloatArray()
        // Da 0,15 s (leggera) a 1,5 s (forte, quasi un treppiede).
        val sigma = s.rateHz * (0.15f + 1.35f * s.strength.coerceIn(0f, 1f))
        val sx = smooth(rx, sigma); val sy = smooth(ry, sigma)
        var cx = FloatArray(rx.size) { sx[it] - rx[it] }
        var cy = FloatArray(ry.size) { sy[it] - ry[it] }
        // Lo zoom deve coprire lo spostamento più grande; oltre il massimo si riducono le correzioni.
        val need = max(cx.maxOfOrNull { abs(it) } ?: 0f, cy.maxOfOrNull { abs(it) } ?: 0f)
        val zoom = (1f + 2f * need * 1.05f).coerceIn(1f, MAX_ZOOM)
        val limit = (zoom - 1f) / 2f
        if (need > limit && need > 0f) {
            cx = FloatArray(cx.size) { cx[it].coerceIn(-limit, limit) }
            cy = FloatArray(cy.size) { cy[it].coerceIn(-limit, limit) }
        }
        return Plan(cx, cy, zoom, s.rateHz, s.fromUs)
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
