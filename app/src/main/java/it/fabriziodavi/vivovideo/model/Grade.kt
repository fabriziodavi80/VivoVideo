package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** I colori delle regolazioni HSL (centro della tinta in gradi). */
enum class HslBand(val label: String, val hue: Float, val argb: Long) {
    RED("Rosso", 0f, 0xFFE5484D), ORANGE("Arancio", 30f, 0xFFF08A24), YELLOW("Giallo", 60f, 0xFFF5D90A), GREEN("Verde", 120f, 0xFF46A758),
    AQUA("Acqua", 180f, 0xFF12A594), BLUE("Blu", 230f, 0xFF3E63DD), PURPLE("Viola", 275f, 0xFF8E4EC6), MAGENTA("Magenta", 320f, 0xFFD6409F),
}

/** Spinta di colore per una zona (ombre, toni medi, luci): tinta in gradi e forza 0…1. */
@Serializable
data class Wheel(val hue: Float = 0f, val amount: Float = 0f)

/** Regolazione HSL di un colore: spostamento di tinta (-1…1 = ±30°), saturazione e luminosità (-1…1). */
@Serializable
data class Hsl(val hue: Float = 0f, val sat: Float = 0f, val lum: Float = 0f)

/**
 * Correzione del colore di una clip. Tutti i valori a 0 = nessun cambiamento.
 * [exposure] in stop (-2…2); gli altri -1…1.
 */
@Serializable
data class Grade(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val shadowWheel: Wheel = Wheel(),
    val midWheel: Wheel = Wheel(),
    val highWheel: Wheel = Wheel(),
    val hsl: List<Hsl> = List(8) { Hsl() },
) {
    val isIdentity: Boolean get() = copy(hsl = List(8) { Hsl() }, shadowWheel = Wheel(), midWheel = Wheel(), highWheel = Wheel()) == Grade() &&
        shadowWheel.amount == 0f && midWheel.amount == 0f && highWheel.amount == 0f && hsl.all { it == Hsl() }

    fun band(b: HslBand): Hsl = hsl.getOrElse(b.ordinal) { Hsl() }
    fun withBand(b: HslBand, v: Hsl): Grade = copy(hsl = List(8) { if (it == b.ordinal) v else hsl.getOrElse(it) { Hsl() } })
}

/**
 * Lo stesso calcolo dello shader (GradeEffect), in Kotlin: per l'istogramma e per le prove.
 * Valori 0…1 in ingresso e in uscita.
 */
object GradeMath {
    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** Colore pieno (luminosità 1, saturazione 1) di una tinta in gradi, centrato sul grigio. */
    fun hueVec(deg: Float): FloatArray {
        val h = ((deg % 360f) + 360f) % 360f / 60f
        val x = 1 - abs(h % 2 - 1)
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(1f, x, 0f); 1 -> Triple(x, 1f, 0f); 2 -> Triple(0f, 1f, x)
            3 -> Triple(0f, x, 1f); 4 -> Triple(x, 0f, 1f); else -> Triple(1f, 0f, x)
        }
        val l = luma(r, g, b)
        return floatArrayOf(r - l, g - l, b - l)
    }

    fun apply(rgb: FloatArray, g: Grade): FloatArray {
        var r = rgb[0]; var gg = rgb[1]; var b = rgb[2]
        // Esposizione.
        val e = 2f.pow(g.exposure)
        r *= e; gg *= e; b *= e
        // Bilanciamento del bianco: caldo/freddo e verde/magenta.
        r *= 1f + 0.12f * g.temperature + 0.05f * g.tint
        gg *= 1f - 0.08f * g.tint
        b *= 1f - 0.12f * g.temperature + 0.05f * g.tint
        // Ombre e alte luci (secondo la luminosità).
        var l = luma(r, gg, b)
        val sh = 1f - smooth(0f, 0.55f, l)
        val hi = smooth(0.45f, 1f, l)
        val k = (1f + g.shadows * 0.9f * sh) * (1f + g.highlights * 0.45f * hi)
        r *= k; gg *= k; b *= k
        // Contrasto intorno al grigio medio.
        val c = 1f + g.contrast
        r = (r - 0.45f) * c + 0.45f; gg = (gg - 0.45f) * c + 0.45f; b = (b - 0.45f) * c + 0.45f
        // Ruote colore: ombre, toni medi, luci.
        l = luma(r, gg, b).coerceIn(0f, 1f)
        val ws = (1f - l) * (1f - l); val wh = l * l; val wm = (1f - ws - wh).coerceAtLeast(0f)
        for ((w, wheel) in listOf(ws to g.shadowWheel, wm to g.midWheel, wh to g.highWheel)) {
            if (wheel.amount <= 0f) continue
            val v = hueVec(wheel.hue)
            val a = w * wheel.amount * 0.35f
            r += v[0] * a; gg += v[1] * a; b += v[2] * a
        }
        // Saturazione e vividezza (la vividezza spinge di più i colori spenti).
        l = luma(r, gg, b)
        val s = max(r, max(gg, b)) - min(r, min(gg, b))
        val sat = (1f + g.saturation) * (1f + g.vibrance * (1f - s.coerceIn(0f, 1f)))
        r = l + (r - l) * sat; gg = l + (gg - l) * sat; b = l + (b - l) * sat
        // HSL per colore.
        if (g.hsl.any { it != Hsl() }) {
            val out = hsl(r.coerceIn(0f, 1f), gg.coerceIn(0f, 1f), b.coerceIn(0f, 1f), g.hsl)
            r = out[0]; gg = out[1]; b = out[2]
        }
        return floatArrayOf(r.coerceIn(0f, 1f), gg.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    /** Peso di una banda per una tinta (gradi): 1 al centro, 0 a 45° di distanza. */
    fun bandWeight(hueDeg: Float, center: Float): Float {
        var d = abs(hueDeg - center) % 360f
        if (d > 180f) d = 360f - d
        return (1f - d / 45f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    }

    private fun hsl(r: Float, g: Float, b: Float, bands: List<Hsl>): FloatArray {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d < 1e-5f) return floatArrayOf(r, g, b)
        var h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        if (h < 0) h += 360f
        var s = if (mx > 0f) d / mx else 0f
        var v = mx
        var dh = 0f; var ds = 0f; var dl = 0f
        HslBand.entries.forEachIndexed { i, band ->
            val w = bandWeight(h, band.hue) * s.coerceIn(0f, 1f).let { min(1f, it * 3f) }
            val x = bands.getOrElse(i) { Hsl() }
            dh += w * x.hue; ds += w * x.sat; dl += w * x.lum
        }
        h = (h + dh * 30f + 360f) % 360f
        s = (s * (1f + ds)).coerceIn(0f, 1f)
        v = (v * (1f + dl * 0.5f)).coerceIn(0f, 1f)
        val c = v * s
        val hp = h / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        return floatArrayOf(r1 + m, g1 + m, b1 + m)
    }

    /** Istogrammi (luminosità, rosso, verde, blu) di un'immagine in pixel ARGB, già corretta con [g]. */
    fun histogram(pixels: IntArray, g: Grade, bins: Int = 64): Array<IntArray> {
        val out = Array(4) { IntArray(bins) }
        val identity = g.isIdentity
        val px = FloatArray(3)
        for (p in pixels) {
            px[0] = ((p shr 16) and 0xFF) / 255f; px[1] = ((p shr 8) and 0xFF) / 255f; px[2] = (p and 0xFF) / 255f
            val c = if (identity) px else apply(px, g)
            fun bin(v: Float) = (v * (bins - 1) + 0.5f).toInt().coerceIn(0, bins - 1)
            out[0][bin(luma(c[0], c[1], c[2]))]++
            out[1][bin(c[0])]++; out[2][bin(c[1])]++; out[3][bin(c[2])]++
        }
        return out
    }
}
