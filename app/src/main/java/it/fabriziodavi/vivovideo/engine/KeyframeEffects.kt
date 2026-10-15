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
private fun Clip.transformAt(presentationTimeUs: Long, startUs: Long): Transform =
    Keyframes.valueAt(keyframes, speedCurve().sourceTimeAt(localTimeUs(presentationTimeUs, startUs)))

/**
 * Media3 passa agli effetti il tempo del montaggio intero (la clip i parte da [startUs]):
 * qui torna al tempo dall'inizio della clip.
 */
internal fun localTimeUs(presentationTimeUs: Long, startUs: Long): Long =
    // Prima dell'inizio della clip (pezzo di dissolvenza incrociata) resta fermo sul primo istante.
    (presentationTimeUs - startUs).coerceAtLeast(0)

/** Matrice di una trasformazione in coordinate normalizzate, con rotazione in proporzioni reali. */
private fun transformMatrix(t: Transform, aspect: Float): Matrix = Matrix().apply {
    postScale(t.scale, t.scale)
    postScale(aspect, 1f)
    postRotate(-t.rotation)
    postScale(1f / aspect, 1f)
    postTranslate(2f * t.x, -2f * t.y)
}

/** Inquadratura fissa della clip (strumento Inquadra). */
class FrameTransformation(private val frame: Transform, private val aspect: Float) : MatrixTransformation {
    private val m = transformMatrix(frame, aspect)
    override fun getMatrix(presentationTimeUs: Long): Matrix = m
}

/** Posizione, zoom e rotazione animati. [aspect] = larghezza/altezza del fotogramma finale. */
class KeyframeTransformation(private val clip: Clip, private val aspect: Float, private val startUs: Long) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long): Matrix {
        // Coordinate normalizzate (-1..1): la rotazione va fatta in proporzioni reali,
        // altrimenti l'immagine si deformerebbe.
        return transformMatrix(clip.transformAt(presentationTimeUs, startUs), aspect)
    }
}

/** Opacità animata: sotto non c'è altro, quindi l'immagine sfuma verso il nero. */
class KeyframeFade(private val clip: Clip, private val startUs: Long) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val o = clip.transformAt(presentationTimeUs, startUs).opacity.coerceIn(0f, 1f)
        return floatArrayOf(
            o, 0f, 0f, 0f,
            0f, o, 0f, 0f,
            0f, 0f, o, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
