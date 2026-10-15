package it.fabriziodavi.vivovideo.engine

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.Project
import kotlin.math.max
import kotlin.math.min

/**
 * Posizione dei video sovrapposti nel compositore. Ingressi: 0 = copia invisibile della traccia
 * principale (tempi), 1..n = righe di video sovrapposti, ultimo = traccia principale visibile.
 */
class LayerCompositorSettings(
    private val outW: Int,
    private val outH: Int,
    private val lanes: List<List<Layer>>,
    private val crossfades: List<Project.Crossfade> = emptyList(),
) : VideoCompositorSettings {
    private val crossfadeInput = if (crossfades.isEmpty()) -1 else lanes.size + 1
    private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
    private val full = StaticOverlaySettings.Builder().build()

    override fun getOutputSize(inputSizes: List<Size>): Size = Size(outW, outH)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId == 0) return hidden
        if (inputId == crossfadeInput) {
            // Peso della clip successiva: da 0 a 1 lungo tutta la dissolvenza.
            val cf = crossfades.firstOrNull { presentationTimeUs >= it.cutUs - it.beforeUs && presentationTimeUs < it.cutUs + it.afterUs } ?: return hidden
            val total = (cf.beforeUs + cf.afterUs).coerceAtLeast(1)
            val w = ((presentationTimeUs - (cf.cutUs - cf.beforeUs)).toFloat() / total).coerceIn(0f, 1f)
            val smooth = w * w * (3 - 2 * w)
            // Prima del taglio sopra c'è la clip successiva, dopo il taglio la precedente.
            val alpha = if (presentationTimeUs < cf.cutUs) smooth else 1f - smooth
            return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
        }
        val lane = lanes.getOrNull(inputId - 1) ?: return full
        val l = lane.firstOrNull { presentationTimeUs >= it.startUs && presentationTimeUs < it.endUs } ?: return hidden
        val local = presentationTimeUs - l.startUs
        val f = animFactors(l.animIn, l.animOut, local, l.durationUs)
        val p = l.poseAt(local)
        return overlaySettings(f.alpha * p.opacity, f.scale * p.scale / baseScale(l), p.rotation, p.x, p.y + f.dy)
    }

    companion object {
        /** Ingrandimento massimo usato dal livello (la sua immagine si prepara a questa misura). */
        fun baseScale(l: Layer): Float = max(l.pose.scale, l.endPose?.scale ?: 0f).coerceIn(0.05f, 1.5f)

        /** Misura in pixel del video sovrapposto: "adattato al quadro" per la scala massima. */
        fun layerFrameSize(l: Layer, outW: Int, outH: Int): Pair<Int, Int> {
            val k = min(outW.toFloat() / l.width.coerceAtLeast(1), outH.toFloat() / l.height.coerceAtLeast(1)) * baseScale(l)
            fun even(v: Float) = (v.toInt() / 2 * 2).coerceAtLeast(2)
            return even(l.width * k) to even(l.height * k)
        }
    }
}
