package it.fabriziodavi.vivovideo

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter

/** Filtri colore della prova tecnica. Girano tutti sulla GPU. */
enum class Look(val label: String) {
    NONE("Nessuno"),
    VIVID("Vivido"),
    CINEMA("Cinema"),
    WARM("Caldo"),
    COOL("Freddo"),
    MONO("B/N");

    fun effects(): List<Effect> = when (this) {
        NONE -> emptyList()
        VIVID -> listOf(
            HslAdjustment.Builder().adjustSaturation(30f).build(),
            Contrast(0.15f),
        )
        CINEMA -> listOf(
            HslAdjustment.Builder().adjustSaturation(-20f).build(),
            Contrast(0.25f),
            Brightness(-0.04f),
            RgbAdjustment.Builder().setRedScale(1.04f).setBlueScale(0.96f).build(),
        )
        WARM -> listOf(
            RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.02f).setBlueScale(0.88f).build(),
        )
        COOL -> listOf(
            RgbAdjustment.Builder().setRedScale(0.9f).setBlueScale(1.12f).build(),
        )
        MONO -> listOf(
            RgbFilter.createGrayscaleFilter(),
            Contrast(0.1f),
        )
    }
}

/**
 * Recupero alte luci / cielo: scurisce le zone chiare e ne ravviva il colore,
 * lasciando intatti ombre e toni medi. amount da 0 (spento) a 1 (massimo).
 * Calcolato come LUT 3D, quindi gira sulla GPU come gli altri filtri.
 */
fun highlightEffect(amount: Float): androidx.media3.effect.SingleColorLut {
    val n = 33
    val cube = Array(n) { Array(n) { IntArray(n) } }
    for (r in 0 until n) for (g in 0 until n) for (b in 0 until n) {
        val rf = r / (n - 1f); val gf = g / (n - 1f); val bf = b / (n - 1f)
        val y = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf
        // Peso: 0 sotto i toni medi, sale dolcemente fino ai bianchi.
        val t = ((y - 0.5f) / 0.45f).coerceIn(0f, 1f)
        val w = t * t * (3 - 2 * t)
        val darken = 1f - 0.38f * amount * w
        val sat = 1f + 0.45f * amount * w
        val yd = y * darken
        fun ch(v: Float): Int {
            val out = yd + (v * darken - yd) * sat
            return (out.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        cube[r][g][b] = (0xFF shl 24) or (ch(rf) shl 16) or (ch(gf) shl 8) or ch(bf)
    }
    return androidx.media3.effect.SingleColorLut.createFromCube(cube)
}
