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
