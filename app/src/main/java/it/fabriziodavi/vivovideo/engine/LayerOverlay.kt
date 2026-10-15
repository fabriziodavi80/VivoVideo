package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.KeyColor
import it.fabriziodavi.vivovideo.model.LayerBorder
import it.fabriziodavi.vivovideo.model.LayerMask
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

    fun prepare(context: Context, l: Layer, frameW: Int, frameH: Int): Prepared {
        val maxScale = max(l.pose.scale, l.endPose?.scale ?: 0f).coerceIn(0.05f, 1.5f)
        val key = "${l.uri}|${l.sourceStartUs}|${l.border}|${l.shadow}|${l.shape}|${l.key}|${l.keyTolerance}|$frameW|$frameH|${(maxScale * 20).toInt()}"
        cache.get(key)?.let { return it }
        val (cw, ch) = containSize(l, frameW, frameH)
        // Lato lungo al massimo 4096 (limite pratico delle texture).
        var base = (maxScale * 20).toInt().coerceAtLeast(1) / 20f
        val longSide = max(cw, ch) * base
        if (longSide > 4096f) base *= 4096f / longSide
        val iw = (cw * base).toInt().coerceAtLeast(2)
        val ih = (ch * base).toInt().coerceAtLeast(2)
        // Video sovrapposto mostrato come immagine ferma (anteprima): un fotogramma vicino all'inizio.
        val src = if (l.isVideo) videoFrame(context, l, iw, ih) else decode(Uri.parse(l.uri).path ?: "", iw, ih)
        val short = min(iw, ih).toFloat()
        val borderW = if (l.border != LayerBorder.NONE) short * 0.025f else 0f
        val shadowPad = if (l.shadow) short * 0.06f else 0f
        val pad = borderW + shadowPad
        val bw = (iw + 2 * pad).toInt()
        val bh = (ih + 2 * pad).toInt()
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val img = RectF(pad, pad, pad + iw, pad + ih)
        val shape = shapePath(l.shape, img, short)
        // Ombra e cornice seguono la forma.
        if (l.shadow && l.shape != LayerMask.SOFT) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                setShadowLayer(shadowPad * 0.6f, 0f, shadowPad * 0.25f, 0xA0000000.toInt())
            }
            c.drawPath(shapePath(l.shape, RectF(img.left - borderW, img.top - borderW, img.right + borderW, img.bottom + borderW), short), sp)
        }
        if (l.border != LayerBorder.NONE && l.shape != LayerMask.SOFT) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (l.border == LayerBorder.WHITE) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
            c.drawPath(shapePath(l.shape, RectF(img.left - borderW, img.top - borderW, img.right + borderW, img.bottom + borderW), short), bp)
        }
        if (src != null) {
            val keyed = if (l.key != KeyColor.NONE) chromaKey(src, l.key, l.keyTolerance) else src
            // Immagine ritagliata nella forma: disegnata in un livello a parte e mascherata (DST_IN).
            val layerBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layerBmp)
            lc.drawBitmap(keyed, null, img, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
            if (l.shape == LayerMask.SOFT) {
                // Ovale con bordo sfumato.
                val r = max(img.width(), img.height()) / 2f
                mp.shader = RadialGradient(
                    img.centerX(), img.centerY(), r,
                    intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                lc.save()
                lc.scale(img.width() / (2 * r), img.height() / (2 * r), img.centerX(), img.centerY())
                lc.drawRect(img.left - r, img.top - r, img.right + r, img.bottom + r, mp)
                lc.restore()
            } else if (l.shape != LayerMask.NONE) {
                // Forma: si ridisegna da zero riempiendo il contorno con l'immagine (bordi morbidi).
                lc.drawColor(0, PorterDuff.Mode.CLEAR)
                val shader = BitmapShader(keyed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader.setLocalMatrix(Matrix().apply {
                    setRectToRect(RectF(0f, 0f, keyed.width.toFloat(), keyed.height.toFloat()), img, Matrix.ScaleToFit.FILL)
                })
                lc.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
            }
            c.drawBitmap(layerBmp, 0f, 0f, null)
            layerBmp.recycle()
            src.recycle()
        }
        // La scala nel quadro si riferisce alla sola immagine: il margine si aggiunge in proporzione.
        return Prepared(out, base).also { cache.put(key, it) }
    }

    /** Contorno della forma dentro [r]. */
    private fun shapePath(m: LayerMask, r: RectF, short: Float): Path = Path().apply {
        when (m) {
            LayerMask.NONE, LayerMask.SOFT -> addRect(r, Path.Direction.CW)
            LayerMask.ROUNDED -> addRoundRect(r, short * 0.08f, short * 0.08f, Path.Direction.CW)
            LayerMask.CIRCLE -> addOval(r, Path.Direction.CW)
            LayerMask.HEART -> {
                val w = r.width(); val h = r.height(); val x = r.left; val y = r.top
                moveTo(x + w / 2, y + h * 0.28f)
                cubicTo(x + w * 0.5f, y + h * 0.05f, x + w * 0.02f, y, x, y + h * 0.3f)
                cubicTo(x, y + h * 0.6f, x + w * 0.35f, y + h * 0.75f, x + w / 2, y + h)
                cubicTo(x + w * 0.65f, y + h * 0.75f, x + w, y + h * 0.6f, x + w, y + h * 0.3f)
                cubicTo(x + w * 0.98f, y, x + w * 0.5f, y + h * 0.05f, x + w / 2, y + h * 0.28f)
                close()
            }
            LayerMask.STAR -> {
                val cx = r.centerX(); val cy = r.centerY()
                val ro = min(r.width(), r.height()) / 2f
                val ri = ro * 0.45f
                for (i in 0 until 10) {
                    val a = Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) ro else ri
                    val px = cx + (rr * kotlin.math.cos(a)).toFloat()
                    val py = cy - (rr * kotlin.math.sin(a)).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
        }
    }

    /**
     * Scontorno: rende trasparenti i pixel vicini al colore di sfondo (verde/blu nel piano del colore,
     * bianco/nero sulla luminosità), con bordo morbido e riduzione dell'alone verde/blu.
     */
    private fun chromaKey(input: Bitmap, key: KeyColor, tol: Float): Bitmap {
        // Serve un'immagine modificabile: se il decoder ne ha data una fissa, se ne fa una copia.
        val b = if (input.isMutable && input.config == Bitmap.Config.ARGB_8888) input else input.copy(Bitmap.Config.ARGB_8888, true)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val t = 0.08f + tol * 0.5f
        val soft = 0.08f
        for (i in px.indices) {
            val p = px[i]
            val a0 = (p ushr 24) and 0xFF
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var bl = (p and 0xFF) / 255f
            val d = when (key) {
                KeyColor.GREEN -> (g - max(r, bl))
                KeyColor.BLUE -> (bl - max(r, g))
                KeyColor.WHITE -> (min(r, min(g, bl)) - 0.75f) * 2f
                KeyColor.BLACK -> (0.25f - max(r, max(g, bl))) * 2f
                KeyColor.NONE -> -1f
            }
            // d alto = molto simile allo sfondo.
            val alpha = ((t - d) / soft).coerceIn(0f, 1f).let { if (d <= 0f) 1f else it }
            if (key == KeyColor.GREEN && g > max(r, bl)) g = max(r, bl)
            if (key == KeyColor.BLUE && bl > max(r, g)) bl = max(r, g)
            val a = (a0 * alpha).toInt()
            px[i] = (a shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (bl * 255).toInt()
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    private fun videoFrame(context: Context, l: Layer, w: Int, h: Int): Bitmap? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(l.uri))
            r.getScaledFrameAtTime(l.sourceStartUs + 300_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
                ?.copy(Bitmap.Config.ARGB_8888, true)
        } finally { r.release() }
    }.getOrNull()

    private fun decode(path: String, w: Int, h: Int): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= w && b.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })
    }
}

/** Livello immagine sopra al video, con movimento (inizio → fine) e animazioni di entrata/uscita. */
class LayerOverlay(
    private val context: Context,
    private val layer: Layer,
    private val frameW: Int,
    private val frameH: Int,
) : BitmapOverlay() {
    private val prepared by lazy { LayerRenderer.prepare(context, layer, frameW, frameH) }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = prepared.bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val local = presentationTimeUs - layer.startUs
        val f = animFactors(layer.animIn, layer.animOut, local, layer.durationUs)
        val p = layer.poseAt(local.coerceIn(0, layer.durationUs))
        return overlaySettings(f.alpha * p.opacity, f.scale * p.scale / prepared.baseScale, p.rotation, p.x, p.y + f.dy)
    }
}
