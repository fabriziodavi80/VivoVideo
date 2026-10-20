package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable

/** Come si oscura: a quadretti (pixel), sfocato o con un riquadro nero. */
@Serializable
enum class RedactStyle(val label: String) { PIXEL("Pixel"), BLUR("Sfocato"), BLACK("Nero") }

/** Un riquadro da oscurare, in frazioni del fotogramma dritto (0..1, origine in alto a sinistra); [round] = ovale (volti). */
@Serializable
data class RBox(val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean = false) {
    val cx: Float get() = x + w / 2
    val cy: Float get() = y + h / 2
    fun contains(px: Float, py: Float) = px in x..(x + w) && py in y..(y + h)

    /** Allargato di [k] (0,2 = 20%) attorno al centro, senza uscire dal fotogramma. */
    fun grow(k: Float): RBox {
        val nw = w * (1 + k); val nh = h * (1 + k)
        val nx = (cx - nw / 2).coerceIn(0f, 1f); val ny = (cy - nh / 2).coerceIn(0f, 1f)
        return RBox(nx, ny, nw.coerceAtMost(1f - nx), nh.coerceAtMost(1f - ny), round)
    }

    fun iou(o: RBox): Float {
        val ix = (minOf(x + w, o.x + o.w) - maxOf(x, o.x)).coerceAtLeast(0f)
        val iy = (minOf(y + h, o.y + o.h) - maxOf(y, o.y)).coerceAtLeast(0f)
        val inter = ix * iy
        val uni = w * h + o.w * o.h - inter
        return if (uni <= 0f) 0f else inter / uni
    }
}

/**
 * Oscuramento automatico di una clip: cosa cercare, come oscurare, e i riquadri trovati
 * (video: [file] con [count] fotogrammi a [rateHz] dal tempo [fromUs] del file; foto: [boxes]).
 */
@Serializable
data class Redaction(
    val faces: Boolean = true,
    val plates: Boolean = false,
    /** Oggetto scelto toccandolo (punto di partenza dell'inseguimento), o null. */
    val objectSeed: RBox? = null,
    val style: RedactStyle = RedactStyle.PIXEL,
    val file: String = "",
    val fromUs: Long = 0,
    val rateHz: Int = 10,
    val count: Int = 0,
    val boxes: List<RBox> = emptyList(),
    /** Quanti riquadri sono stati trovati in tutto (per dirlo all'utente). */
    val found: Int = 0,
)

object Redact {
    /** Al massimo tanti riquadri per fotogramma (sono uniform dello shader). */
    const val MAX = 6

    /** Sigla di targa: 5–8 tra lettere e cifre, con almeno 2 cifre e una lettera (AB123CD, AB 123 CD, 1ABC234…). */
    fun looksLikePlate(text: String): Boolean {
        val t = text.uppercase().filter { it.isLetterOrDigit() }
        if (t.length !in 5..8) return false
        val digits = t.count { it.isDigit() }
        val letters = t.count { it.isLetter() }
        return digits >= 2 && letters >= 1 && t.all { it in 'A'..'Z' || it in '0'..'9' }
    }

    /** Le targhe sono più larghe che alte: si scartano scritte di forma diversa. */
    fun plateShape(b: RBox, frameAspect: Float): Boolean {
        val a = (b.w * frameAspect) / b.h.coerceAtLeast(1e-4f)
        return a in 1.6f..7f
    }

    /**
     * Segue un oggetto: fra gli oggetti trovati in ogni fotogramma ([tracks]: id → riquadri per fotogramma),
     * prende quello che nel fotogramma [seedFrame] copre di più [seed]. Se nessuno lo copre, resta il riquadro scelto, fermo.
     */
    fun followObject(frames: List<Map<Int, RBox>>, seedFrame: Int, seed: RBox): List<RBox?> {
        if (frames.isEmpty()) return emptyList()
        val at = frames[seedFrame.coerceIn(0, frames.lastIndex)]
        val best = at.entries.maxByOrNull { (_, b) -> b.iou(seed) + if (b.contains(seed.cx, seed.cy)) 0.3f else 0f }
            ?.takeIf { (_, b) -> b.iou(seed) > 0.05f || b.contains(seed.cx, seed.cy) }?.key
            ?: return frames.map { seed }
        // Dove l'oggetto non si vede per un attimo, resta l'ultimo riquadro per mezzo secondo (circa 5 fotogrammi).
        var last: RBox? = null
        var miss = 0
        return frames.map { f ->
            val b = f[best]
            if (b != null) { last = b; miss = 0; b } else { miss++; if (miss <= 5) last else null }
        }
    }
}
