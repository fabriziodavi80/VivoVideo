package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.LayerBorder
import kotlin.math.max
import kotlin.math.min

/** Immagini dei livelli già pronte (bordo, ombra, angoli), condivise fra anteprima ed export. */
object LayerRenderer {
    private val cache = object : LruCache<String, Prepared>(96 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Prepared) = value.bitmap.byteCount
    }

    /** [bitmap] contiene l'immagine grande [baseScale] volte la misura "adattata al quadro". */
    class Prepared(val bitmap: Bitmap, val baseScale: Float)

    /** Misura dell'immagine adattata al quadro (scala 1), in pixel del quadro. */
    fun containSize(l: Layer, frameW: Int, frameH: Int): Pair<Float, Float> {
        val k = min(frameW.toFloat() / l.width.coerceAtLeast(1), frameH.toFloat() / l.height.coerceAtLeast(1))
        return l.width * k to l.height * k
    }

    /** Margine attorno all'immagine (bordo e ombra), in frazione del lato corto. */
    fun padFraction(l: Layer): Float = (if (l.border != LayerBorder.NONE) 0.025f else 0f) + (if (l.shadow) 0.06f else 0f)

    fun prepare(l: Layer, frameW: Int, frameH: Int): Prepared {
        val maxScale = max(l.pose.scale, l.endPose?.scale ?: 0f).coerceIn(0.05f, 1.5f)
        val key = "${l.uri}|${l.border}|${l.shadow}|${l.rounded}|$frameW|$frameH|${(maxScale * 20).toInt()}"
        cache.get(key)?.let { return it }
        val (cw, ch) = containSize(l, frameW, frameH)
        // Lato lungo al massimo 4096 (limite pratico delle texture).
        var base = (maxScale * 20).toInt().coerceAtLeast(1) / 20f
        val longSide = max(cw, ch) * base
        if (longSide > 4096f) base *= 4096f / longSide
        val iw = (cw * base).toInt().coerceAtLeast(2)
        val ih = (ch * base).toInt().coerceAtLeast(2)
        val src = decode(Uri.parse(l.uri).path ?: "", iw, ih)
        val short = min(iw, ih).toFloat()
        val borderW = if (l.border != LayerBorder.NONE) short * 0.025f else 0f
        val shadowPad = if (l.shadow) short * 0.06f else 0f
        val pad = borderW + shadowPad
        val bw = (iw + 2 * pad).toInt()
        val bh = (ih + 2 * pad).toInt()
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val radius = if (l.rounded) short * 0.07f else 0f
        val outer = RectF(shadowPad, shadowPad, bw - shadowPad, bh - shadowPad)
        if (l.shadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                setShadowLayer(shadowPad * 0.6f, 0f, shadowPad * 0.25f, 0xA0000000.toInt())
            }
            c.drawRoundRect(outer, radius + borderW, radius + borderW, sp)
        }
        if (l.border != LayerBorder.NONE) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (l.border == LayerBorder.WHITE) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
            c.drawRoundRect(outer, radius + borderW, radius + borderW, bp)
        }
        if (src != null) {
            val dst = RectF(pad, pad, pad + iw, pad + ih)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (radius > 0) {
                val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader.setLocalMatrix(Matrix().apply { setRectToRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), dst, Matrix.ScaleToFit.FILL) })
                paint.shader = shader
                c.drawRoundRect(dst, radius, radius, paint)
            } else {
                c.drawBitmap(src, null, dst, paint)
            }
            src.recycle()
        }
        // La scala nel quadro si riferisce alla sola immagine: il margine si aggiunge in proporzione.
        return Prepared(out, base).also { cache.put(key, it) }
    }

    private fun decode(path: String, w: Int, h: Int): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= w && b.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Livello immagine sopra al video, con movimento (inizio → fine) e animazioni di entrata/uscita. */
class LayerOverlay(
    private val layer: Layer,
    private val frameW: Int,
    private val frameH: Int,
) : BitmapOverlay() {
    private val prepared by lazy { LayerRenderer.prepare(layer, frameW, frameH) }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = prepared.bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val local = presentationTimeUs - layer.startUs
        val f = animFactors(layer.animIn, layer.animOut, local, layer.durationUs)
        val p = layer.poseAt(local.coerceIn(0, layer.durationUs))
        return overlaySettings(f.alpha * p.opacity, f.scale * p.scale / prepared.baseScale, p.rotation, p.x, p.y + f.dy)
    }
}
