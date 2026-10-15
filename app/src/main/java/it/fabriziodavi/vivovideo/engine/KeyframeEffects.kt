package it.fabriziodavi.vivovideo.engine

import android.graphics.Matrix
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Keyframes
import it.fabriziodavi.vivovideo.model.Transform

/*
 * I tempi che Media3 passa agli effetti partono da 0 all'inizio della clip tagliata e sono già
 * "dopo la velocità": li riportiamo al tempo del video originale, dove vivono i keyframe.
 */
private fun Clip.transformAt(presentationTimeUs: Long): Transform =
    Keyframes.valueAt(keyframes, speedCurve().sourceTimeAt(presentationTimeUs.coerceAtLeast(0)))

/** Posizione, zoom e rotazione animati. [aspect] = larghezza/altezza del fotogramma finale. */
class KeyframeTransformation(private val clip: Clip, private val aspect: Float) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val t = clip.transformAt(presentationTimeUs)
        return Matrix().apply {
            // Coordinate normalizzate (-1..1): la rotazione va fatta in proporzioni reali,
            // altrimenti l'immagine si deformerebbe.
            postScale(t.scale, t.scale)
            postScale(aspect, 1f)
            postRotate(-t.rotation)
            postScale(1f / aspect, 1f)
            postTranslate(2f * t.x, -2f * t.y)
        }
    }
}

/** Opacità animata: sotto non c'è altro, quindi l'immagine sfuma verso il nero. */
class KeyframeFade(private val clip: Clip) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val o = clip.transformAt(presentationTimeUs).opacity.coerceIn(0f, 1f)
        return floatArrayOf(
            o, 0f, 0f, 0f,
            0f, o, 0f, 0f,
            0f, 0f, o, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
