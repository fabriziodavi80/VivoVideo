package it.fabriziodavi.vivovideo.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import it.fabriziodavi.vivovideo.model.CellFrame
import it.fabriziodavi.vivovideo.model.CellShape
import it.fabriziodavi.vivovideo.model.CollageAnim
import it.fabriziodavi.vivovideo.model.CollageCell
import it.fabriziodavi.vivovideo.model.CollageDecor
import it.fabriziodavi.vivovideo.model.CollageGeometry
import it.fabriziodavi.vivovideo.model.CollageItem
import it.fabriziodavi.vivovideo.model.CollageMotion
import it.fabriziodavi.vivovideo.model.CollagePlan
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.CollageTemplate
import it.fabriziodavi.vivovideo.model.CollageTemplates
import it.fabriziodavi.vivovideo.model.PrintFormat
import it.fabriziodavi.vivovideo.model.Pt
import it.fabriziodavi.vivovideo.model.blobPoints
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Disegna un collage (sfondo, cornici, forme, foto, rullino, scritta, grana) su una tela qualsiasi: l'anteprima
 * nell'app, l'immagine da salvare e ogni fotogramma del video. Lo stesso codice per tutti: si vede quello che si salva.
 */
object CollageRenderer {
    /** La foto (o il fotogramma del video) numero [item] all'istante [tUs] del collage. */
    fun interface Frames {
        fun get(item: Int, tUs: Long): Bitmap?
        /** La persona scontornata della foto [item] (maschera allineata alla foto), se c'è. */
        fun mask(item: Int): Bitmap? = null
    }

    /** Grana: rumore grigio ripetuto, spostato a ogni fotogramma (come la pellicola). */
    private val noise: Bitmap by lazy {
        val n = 256
        val r = java.util.Random(7)
        val px = IntArray(n * n) { val v = 96 + r.nextInt(128); Color.argb(255, v, v, v) }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /** Sfondi sfocati già pronti (per la foto da cui vengono). */
    private val blurCache = java.util.WeakHashMap<Bitmap, Bitmap>()

    internal fun blurred(b: Bitmap): Bitmap = synchronized(blurCache) {
        blurCache.getOrPut(b) {
            // Rimpicciolita due volte e ingrandita con il filtro: una sfocatura morbida e leggerissima.
            val k = 48f / max(b.width, b.height)
            val s1 = Bitmap.createScaledBitmap(b, (b.width * k * 4).toInt().coerceAtLeast(4), (b.height * k * 4).toInt().coerceAtLeast(4), true)
            val s2 = Bitmap.createScaledBitmap(s1, (b.width * k).toInt().coerceAtLeast(2), (b.height * k).toInt().coerceAtLeast(2), true)
            if (s1 !== s2) s1.recycle()
            s2
        }
    }

    /** Rettangolo della cella in pixel (con lo spazio fra le foto, se il modello lo usa). */
    fun cellRect(t: CollageTemplate, cell: CollageCell, gap: Float, w: Int, h: Int): RectF {
        if (!t.spaced || gap <= 0f) return RectF(cell.x * w, cell.y * h, (cell.x + cell.w) * w, (cell.y + cell.h) * h)
        val g = gap * min(w, h)
        val aw = w - g; val ah = h - g
        return RectF(g / 2 + cell.x * aw + g / 2, g / 2 + cell.y * ah + g / 2, g / 2 + (cell.x + cell.w) * aw - g / 2, g / 2 + (cell.y + cell.h) * ah - g / 2)
    }

    /** Poligono della cella obliqua in pixel, rientrato per lo spazio fra le foto. */
    private fun cellPoly(t: CollageTemplate, cell: CollageCell, gap: Float, w: Int, h: Int): List<Pt> {
        val g = if (t.spaced) gap * min(w, h) else 0f
        val aw = w - g; val ah = h - g
        val p = cell.poly.map { Pt(g / 2 + it.x * aw, g / 2 + it.y * ah) }
        return if (g > 0) CollageGeometry.inset(p, g / 2) else p
    }

    /** Contorno della foto nella cella (forma, angoli arrotondati, taglio obliquo). */
    fun shapePath(t: CollageTemplate, cell: CollageCell, index: Int, r: RectF, radius: Float, gap: Float, w: Int, h: Int): Path {
        val p = Path()
        when {
            cell.poly.isNotEmpty() -> {
                val pts = cellPoly(t, cell, gap, w, h)
                pts.forEachIndexed { i, q -> if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
                p.close()
                if (radius > 0) {
                    val out = Path()
                    Paint().apply { pathEffect = CornerPathEffect(radius); style = Paint.Style.FILL }.getFillPath(p, out)
                    return out
                }
            }
            cell.shape == CellShape.CIRCLE -> { val s = min(r.width(), r.height()) / 2; p.addCircle(r.centerX(), r.centerY(), s, Path.Direction.CW) }
            cell.shape == CellShape.ARCH -> {
                val rad = r.width() / 2
                val br = min(radius, r.width() / 4)
                p.moveTo(r.left, r.top + rad)
                p.arcTo(RectF(r.left, r.top, r.right, r.top + 2 * rad), 180f, 180f, false)
                p.lineTo(r.right, r.bottom - br)
                p.quadTo(r.right, r.bottom, r.right - br, r.bottom)
                p.lineTo(r.left + br, r.bottom)
                p.quadTo(r.left, r.bottom, r.left, r.bottom - br)
                p.close()
            }
            cell.shape == CellShape.HEART -> {
                val s = min(r.width(), r.height())
                val ox = r.centerX() - s / 2; val oy = r.centerY() - s / 2
                fun x(v: Float) = ox + v * s
                fun y(v: Float) = oy + v * s
                p.moveTo(x(0.5f), y(0.28f))
                p.cubicTo(x(0.5f), y(0.12f), x(0.3f), y(0.0f), x(0.15f), y(0.07f))
                p.cubicTo(x(-0.03f), y(0.16f), x(0.0f), y(0.44f), x(0.12f), y(0.58f))
                p.cubicTo(x(0.25f), y(0.73f), x(0.42f), y(0.85f), x(0.5f), y(0.96f))
                p.cubicTo(x(0.58f), y(0.85f), x(0.75f), y(0.73f), x(0.88f), y(0.58f))
                p.cubicTo(x(1.0f), y(0.44f), x(1.03f), y(0.16f), x(0.85f), y(0.07f))
                p.cubicTo(x(0.7f), y(0.0f), x(0.5f), y(0.12f), x(0.5f), y(0.28f))
                p.close()
            }
            cell.shape == CellShape.HEXAGON -> {
                val cx = r.centerX()
                p.moveTo(cx, r.top); p.lineTo(r.right, r.top + r.height() / 4); p.lineTo(r.right, r.bottom - r.height() / 4)
                p.lineTo(cx, r.bottom); p.lineTo(r.left, r.bottom - r.height() / 4); p.lineTo(r.left, r.top + r.height() / 4); p.close()
            }
            cell.shape == CellShape.BLOB -> {
                // Curva chiusa morbida (Catmull-Rom) attraverso otto punti a raggio variabile.
                val pts = blobPoints(seed = 3 + index * 7).map { Pt(r.centerX() + it.x * r.width() / 2, r.centerY() + it.y * r.height() / 2) }
                val n = pts.size
                p.moveTo(pts[0].x, pts[0].y)
                for (i in 0 until n) {
                    val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
                    p.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
                }
                p.close()
            }
            else -> if (radius > 0) p.addRoundRect(r, radius, radius, Path.Direction.CW) else p.addRect(r, Path.Direction.CW)
        }
        return p
    }

    /** La cella sotto il punto ([x],[y]) in pixel (collage fermo), o -1. Per scegliere una foto toccandola. */
    fun cellAt(t: CollageTemplate, spec: CollageSpec, w: Int, h: Int, x: Float, y: Float): Int {
        for (i in t.cells.indices.reversed()) {
            val r = cellRect(t, t.cells[i], spec.style.gap, w, h)
            if (r.contains(x, y)) return i
        }
        return -1
    }

    /**
     * Disegna il collage nell'istante [tUs] ([still] = immagine ferma, tutto al suo posto).
     * [selected] = cella evidenziata (nell'editor del collage).
     */
    fun draw(
        c: Canvas, w: Int, h: Int, t: CollageTemplate, spec: CollageSpec, tUs: Long, still: Boolean,
        frames: Frames, caption: Typeface?, selected: Int = -1,
    ) {
        if (t.design != null) { DesignRenderer.draw(c, w, h, t, spec, tUs, still, frames, selected); return }
        val st = spec.style
        val short = min(w, h).toFloat()
        val dur = spec.durationUs
        val motion = spec.motion
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Sfondo.
        val bg = st.bg
        val first = if (spec.items.isNotEmpty()) frames.get(0, tUs) else null
        if (bg.blur && first != null) {
            val b = blurred(first)
            val k = max(w.toFloat() / b.width, h.toFloat() / b.height) * 1.08f
            val dw = b.width * k; val dh = b.height * k
            c.drawBitmap(b, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
            c.drawColor(0x59000000)
        } else if (bg.color2 != null) {
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), bg.color.toInt(), bg.color2.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        } else c.drawColor(bg.color.toInt())
        if (bg.paper) {
            grain(c, w, h, 0, 34)
            // Carta: bordi appena più scuri.
            paint.shader = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.75f, 0x00000000, 0x26000000, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
        }

        // Inquadratura: primo piano che si allarga, o un lento allontanarsi per "Scorre" senza pellicola.
        var cam = CollageAnim.camera(motion, t.cells.firstOrNull(), w.toFloat() / h, tUs, dur, still)
        if (!still && motion == CollageMotion.SCROLL && t.decor == CollageDecor.NONE) {
            cam = cam.copy(scale = 1.12f - 0.12f * (tUs.toFloat() / dur.coerceAtLeast(1)).coerceIn(0f, 1f))
        }
        c.save()
        c.translate(w / 2f, h / 2f)
        c.scale(cam.scale, cam.scale)
        c.translate(-cam.cx * w, -cam.cy * h)

        val scroll = if (t.decor == CollageDecor.NONE) 0f else CollageAnim.scroll(motion, tUs, dur, still)
        drawFilm(c, t, st.gap, w, h, scroll, bg.light)
        val copies = if (scroll != 0f) listOf(0f, 1f) else listOf(0f)
        for (copy in copies) {
            val shiftX = if (t.decor == CollageDecor.FILM_H) (scroll + copy) * w else 0f
            val shiftY = if (t.decor == CollageDecor.FILM_V) (scroll + copy) * h else 0f
            t.cells.forEachIndexed { i, cell ->
                val item = CollagePlan.itemFor(i, spec.items.size)
                val pose = CollageAnim.pose(motion, i, t.count, cell, tUs, dur, spec.kenBurns, still)
                if (pose.alpha <= 0.002f || pose.reveal <= 0.002f) return@forEachIndexed
                val r = cellRect(t, cell, st.gap, w, h).apply { offset(shiftX, shiftY) }
                c.save()
                c.translate(pose.dx * w, pose.dy * h)
                c.rotate(cell.rotation + pose.rot, r.centerX(), r.centerY())
                c.scale(pose.scale, pose.scale, r.centerX(), r.centerY())
                val layer = if (pose.alpha < 0.999f) c.saveLayerAlpha(null, (pose.alpha * 255).toInt()) else -1
                drawCell(c, t, cell, i, r, spec, item, tUs, pose.inner, pose.reveal, frames, short, w, h, shiftX, shiftY, selected == i)
                if (layer >= 0) c.restoreToCount(layer)
                c.restore()
            }
        }
        c.restore()

        // Scritta.
        if (spec.caption.isNotBlank()) {
            val a = CollageAnim.captionAlpha(tUs, dur, still)
            if (a > 0f) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = caption ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    textSize = short * 0.058f
                    textAlign = Paint.Align.CENTER
                    val dark = bg.light && !bg.blur
                    color = if (dark) 0xFF26221E.toInt() else Color.WHITE
                    if (!dark) setShadowLayer(short * 0.012f, 0f, short * 0.004f, 0x99000000.toInt())
                    alpha = (a * 255).toInt()
                }
                val text = spec.caption.trim()
                val tw = tp.measureText(text)
                if (tw > w * 0.9f) tp.textSize *= w * 0.9f / tw
                val y = t.captionY * h + (1 - a) * short * 0.02f
                c.drawText(text, w / 2f, y - (tp.ascent() + tp.descent()) / 2, tp)
            }
        }
        if (st.grain) grain(c, w, h, (tUs / 33_333L).toInt(), 20)
    }

    internal fun grain(c: Canvas, w: Int, h: Int, frame: Int, alpha: Int) {
        val p = Paint().apply {
            val sh = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            // Grana grande quanto un granello di pellicola anche in 4K.
            val k = max(1f, min(w, h) / 1080f)
            sh.setLocalMatrix(Matrix().apply { setScale(k, k); postTranslate((frame * 97 % 256).toFloat(), (frame * 57 % 256).toFloat()) })
            shader = sh
            this.alpha = alpha
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** Pellicola dietro le foto del rullino, con i fori che scorrono insieme ai fotogrammi. */
    private fun drawFilm(c: Canvas, t: CollageTemplate, gap: Float, w: Int, h: Int, scroll: Float, lightBg: Boolean) {
        if (t.decor == CollageDecor.NONE) return
        val vertical = t.decor == CollageDecor.FILM_V
        val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF151515.toInt() }
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightBg) 0xFFF0EEE9.toInt() else 0xFF3A3A3A.toInt() }
        // Le colonne (o righe) di fotogrammi: una pellicola per ciascuna.
        val groups = t.cells.map { cellRect(t, it, gap, w, h) }.groupBy { if (vertical) (it.left / 4).toInt() else (it.top / 4).toInt() }
        for (rs in groups.values) {
            val a = if (vertical) rs.minOf { it.left } else rs.minOf { it.top }
            val b = if (vertical) rs.maxOf { it.right } else rs.maxOf { it.bottom }
            val band = (b - a) * 0.13f
            val lo = a - band; val hi = b + band
            if (vertical) c.drawRect(lo, 0f, hi, h.toFloat(), strip) else c.drawRect(0f, lo, w.toFloat(), hi, strip)
            val step = band * 1.6f
            val hw = band * 0.42f; val hl = band * 0.62f
            val len = if (vertical) h else w
            val off = ((scroll * len) % step + step) % step
            var p = -step + off
            while (p < len + step) {
                for (side in listOf(lo + band / 2, hi - band / 2)) {
                    val rr = if (vertical) RectF(side - hw / 2, p, side + hw / 2, p + hl) else RectF(p, side - hw / 2, p + hl, side + hw / 2)
                    c.drawRoundRect(rr, hw * 0.25f, hw * 0.25f, hole)
                }
                p += step
            }
        }
    }

    private fun drawCell(
        c: Canvas, t: CollageTemplate, cell: CollageCell, index: Int, r: RectF, spec: CollageSpec, item: Int, tUs: Long,
        inner: Float, reveal: Float, frames: Frames, short: Float, w: Int, h: Int, shiftX: Float, shiftY: Float, selected: Boolean,
    ) {
        val st = spec.style
        val radius = st.radius * short
        val polaroid = cell.frame != CellFrame.NONE
        // Tendina: la foto si scopre da sinistra (celle pari) o dall'alto (dispari).
        if (reveal < 0.999f) {
            val clip = if (index % 2 == 0) RectF(r.left - short, r.top - short, r.left + (r.width() + short * 0.3f) * reveal, r.bottom + short)
                else RectF(r.left - short, r.top - short, r.right + short, r.top + (r.height() + short * 0.3f) * reveal)
            c.clipRect(clip)
        }
        val path = shapePath(t, cell, index, r, if (polaroid) 0f else radius, st.gap, w, h).apply { offset(if (cell.poly.isNotEmpty()) shiftX else 0f, if (cell.poly.isNotEmpty()) shiftY else 0f) }
        if (polaroid) {
            // Cornice bianca della polaroid (più alta sotto), con l'ombra sul tavolo.
            val pad = r.width() * 0.06f
            val card = RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + r.width() * 0.24f)
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFBFAF7.toInt()
                setShadowLayer(short * 0.018f, 0f, short * 0.008f, 0x55000000)
            }
            c.drawRect(card, cp)
        } else if (st.shadow) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                setShadowLayer(short * 0.022f, 0f, short * 0.01f, 0x66000000)
            }
            c.drawPath(path, sp)
        }
        c.save()
        c.clipPath(path)
        val bmp = if (item >= 0) frames.get(item, tUs) else null
        if (bmp == null || bmp.isRecycled) {
            c.drawColor(0xFF3A3A3A.toInt())
        } else {
            val it = spec.items[item]
            val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
            // La foto riempie la cella (taglia i bordi), con lo zoom scelto e quello lento del video.
            val cover = if (cell.poly.isNotEmpty()) {
                val b = RectF(); path.computeBounds(b, true); b
            } else r
            val k = max(cover.width() / bw, cover.height() / bh) * it.zoom.coerceAtLeast(1f) * inner
            val dw = bw * k; val dh = bh * k
            val ox = (dw - cover.width()) / 2 * it.panX.coerceIn(-1f, 1f)
            val oy = (dh - cover.height()) / 2 * it.panY.coerceIn(-1f, 1f)
            val l = cover.centerX() - dw / 2 - ox; val tp = cover.centerY() - dh / 2 - oy
            c.drawBitmap(bmp, null, RectF(l, tp, l + dw, tp + dh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        c.restore()
        if (polaroid && cell.frame == CellFrame.TAPE) {
            // Nastro adesivo semitrasparente sul bordo alto.
            c.save()
            c.rotate(-6f + (index % 3) * 5f, r.centerX(), r.top - r.width() * 0.06f)
            val tw = r.width() * 0.36f; val th = r.width() * 0.1f
            c.drawRect(RectF(r.centerX() - tw / 2, r.top - r.width() * 0.06f - th / 2, r.centerX() + tw / 2, r.top - r.width() * 0.06f + th / 2),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB8EDE3C8.toInt() })
            c.restore()
        }
        if (selected) {
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = short * 0.008f; color = 0xFFFFC83D.toInt() })
        }
    }

    // ---------------- Foto e video ----------------

    /** Lato (pixel) che basta per la foto [item] nel collage [w]×[h]: grande quanto la sua cella più grande, con margine per gli zoom. */
    fun neededSide(t: CollageTemplate, spec: CollageSpec, item: Int, w: Int, h: Int): Int {
        var side = 64f
        t.cells.forEachIndexed { i, cell ->
            if (CollagePlan.itemFor(i, spec.items.size) != item) return@forEachIndexed
            val r = cellRect(t, cell, spec.style.gap, w, h)
            var s = max(r.width(), r.height()) * 1.15f * spec.items[item].zoom.coerceAtLeast(1f)
            if (spec.motion == CollageMotion.ZOOM && i == 0) s *= CollageAnim.camera(CollageMotion.ZOOM, cell, w.toFloat() / h, 0, spec.durationUs).scale
            side = max(side, s)
        }
        return side.toInt().coerceIn(64, 4096)
    }

    /** Foto ridotta a circa [maxSide] di lato lungo (già raddrizzata: le foto del progetto lo sono). */
    fun loadPhoto(ctx: Context, uri: String, maxSide: Int): Bitmap? = runCatching {
        val u = Uri.parse(uri)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(u)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        ctx.contentResolver.openInputStream(u)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }.getOrNull()

    /** Un fotogramma del video (per l'anteprima e per i video troppo brevi da leggere in sequenza). */
    fun videoFrame(ctx: Context, item: CollageItem, srcUs: Long, maxSide: Int): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, Uri.parse(item.uri))
            val k = maxSide.toFloat() / max(item.width, item.height).coerceAtLeast(1)
            r.getScaledFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, (item.width * k).toInt().coerceAtLeast(2), (item.height * k).toInt().coerceAtLeast(2))
        } finally { r.release() }
    }.getOrNull()

    /**
     * Fotogrammi di un video in sequenza (decodificati a gruppi, molto più veloce che cercarli uno per uno),
     * ridotti a [maxSide] e girati come si vedono.
     */
    class VideoFrames(private val ctx: Context, private val item: CollageItem, private val maxSide: Int) : AutoCloseable {
        private val r = MediaMetadataRetriever().apply { setDataSource(ctx, Uri.parse(item.uri)) }
        private val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        private val durUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        private val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        private val rawW = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        private val buf = LinkedHashMap<Int, Bitmap>()
        private var last: Bitmap? = null

        private fun fit(b: Bitmap): Bitmap {
            // Se il fotogramma arriva "coricato" (senza la rotazione del video), si raddrizza qui.
            val m = Matrix()
            if (rotation % 180 != 0 && b.width == rawW && b.width > b.height) m.postRotate(rotation.toFloat())
            else if (rotation == 180 && b.width == rawW) m.postRotate(180f)
            val k = min(1f, maxSide.toFloat() / max(b.width, b.height))
            if (k < 1f) m.postScale(k, k)
            if (m.isIdentity) return b
            val out = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            if (out !== b) b.recycle()
            return out
        }

        /** Il fotogramma all'istante [tUs] del collage. */
        fun at(tUs: Long): Bitmap? {
            val src = CollagePlan.videoTimeUs(item, tUs)
            if (count <= 1 || durUs <= 0) {
                return videoFrame(ctx, item, src, maxSide)?.also { last?.recycle(); last = it } ?: last
            }
            val idx = (src.toDouble() / durUs * count).toInt().coerceIn(0, count - 1)
            buf[idx]?.let { return it }
            // Si leggono 4 fotogrammi di fila; quelli vecchi si buttano.
            val n = min(4, count - idx)
            val got = runCatching { r.getFramesAtIndex(idx, n) }.getOrNull()
            if (got.isNullOrEmpty()) return videoFrame(ctx, item, src, maxSide) ?: buf.values.lastOrNull()
            buf.keys.filter { it < idx }.forEach { k -> buf.remove(k)?.let { b -> if (b !== last) b.recycle() } }
            got.forEachIndexed { k, b -> buf[idx + k] = fit(b) }
            return buf[idx]
        }

        override fun close() {
            buf.values.forEach { it.recycle() }; buf.clear()
            runCatching { r.release() }
        }
    }

    // ---------------- Immagine e video ----------------

    /** Salva il collage come immagine (JPEG, lato corto 2160 pixel) nella galleria, in Immagini/VivoVideo. */
    fun saveImage(ctx: Context, spec: CollageSpec, caption: Typeface?): Uri {
        DesignFonts.init(ctx)
        val t = CollageTemplates.byId(spec.templateId, spec.aspect) ?: error("modello non trovato")
        val (w, h) = spec.aspect.size(2160)
        val photos = spec.items.indices.map { i -> load(ctx, spec.items[i], neededSide(t, spec, i, w, h), 0) }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bmp), w, h, t, spec, spec.durationUs, true, { i, _ -> photos.getOrNull(i) }, caption)
            val name = "VivoVideo_Collage_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date()) + ".jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VivoVideo")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val cr = ctx.contentResolver
            val uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("galleria non disponibile")
            try {
                cr.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, values, null, null)
            } catch (e: Exception) { cr.delete(uri, null, null); throw e }
            return uri
        } finally {
            bmp.recycle()
            photos.forEach { it?.recycle() }
        }
    }

    /**
     * Collage da stampare nel formato [fmt]: [shop] = negozio o sito (margine di taglio di 3 mm, il fondo va oltre il bordo),
     * altrimenti stampante di casa (bordo bianco di 4 mm). JPEG alla risoluzione giusta (scritta nel file) in Immagini/VivoVideo.
     * Restituisce dove è stato salvato e la misura in pixel.
     */
    fun savePrint(
        ctx: Context, spec: CollageSpec, fmt: PrintFormat, shop: Boolean, caption: Typeface?,
        onStep: (String, Float) -> Unit = { _, _ -> },
    ): Triple<Uri, Int, Int> {
        DesignFonts.init(ctx)
        val t = CollageTemplates.byId(spec.templateId, spec.aspect) ?: error("modello non trovato")
        val (w, h, dpi) = fmt.pixels(shop)
        val m = if (shop) 0 else (PrintFormat.HOME_MARGIN_MM / 25.4f * dpi).toInt()
        val iw = w - 2 * m; val ih = h - 2 * m
        // Foto grandi quanto serve, ma non oltre 3600 pixel (la memoria del telefono non è infinita).
        val photos = spec.items.indices.map { i ->
            onStep("Carico le foto in alta qualità (${i + 1} di ${spec.items.size})…", 0.6f * i / spec.items.size.coerceAtLeast(1))
            load(ctx, spec.items[i], neededSide(t, spec, i, iw, ih).coerceAtMost(3600), 0)
        }
        onStep("Disegno il foglio ${fmt.label} (${w}×$h pixel)…", 0.65f)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            c.translate(m.toFloat(), m.toFloat())
            c.clipRect(0, 0, iw, ih)
            draw(c, iw, ih, t, spec, spec.durationUs, true, { i, _ -> photos.getOrNull(i) }, caption)
            val name = "VivoVideo_Stampa_${fmt.label.replace("×", "x").replace(" ", "").replace(".", "")}_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date()) + ".jpg"
            onStep("Salvo il file per la stampa…", 0.85f)
            val tmp = File(ctx.cacheDir, name)
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            // La risoluzione nel file: il negozio stampa alla misura giusta senza chiedere.
            runCatching {
                val ex = ExifInterface(tmp.absolutePath)
                ex.setAttribute(ExifInterface.TAG_X_RESOLUTION, "$dpi/1")
                ex.setAttribute(ExifInterface.TAG_Y_RESOLUTION, "$dpi/1")
                ex.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2")
                ex.setAttribute(ExifInterface.TAG_SOFTWARE, "VivoVideo")
                ex.saveAttributes()
            }
            try { return Triple(insertImage(ctx, name) { out -> tmp.inputStream().use { it.copyTo(out) } }, w, h) } finally { tmp.delete() }
        } finally {
            bmp.recycle()
            photos.forEach { it?.recycle() }
        }
    }

    /** Mette un'immagine nella galleria (Immagini/VivoVideo) scrivendola con [write]. */
    private fun insertImage(ctx: Context, name: String, write: (java.io.OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VivoVideo")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = ctx.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("galleria non disponibile")
        try {
            cr.openOutputStream(uri)!!.use(write)
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(uri, values, null, null)
        } catch (e: Exception) { cr.delete(uri, null, null); throw e }
        return uri
    }

    /** La foto, o il fotogramma del video all'istante [tUs], ridotti a [side]. */
    fun load(ctx: Context, item: CollageItem, side: Int, tUs: Long): Bitmap? =
        if (item.video) videoFrame(ctx, item, CollagePlan.videoTimeUs(item, tUs), side) else loadPhoto(ctx, item.uri, side)

    /** La misura più grande che il codificatore H.264 del telefono accetta per questo formato (4K, poi Full HD, poi 720p). */
    fun videoSize(spec: CollageSpec): Pair<Int, Int> {
        val caps = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .mapNotNull { runCatching { it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities }.getOrNull() }
        for (short in listOf(2160, 1080, 720)) {
            val (w, h) = spec.aspect.size(short)
            if (caps.any { runCatching { it.isSizeSupported(w, h) }.getOrDefault(false) }) return w to h
        }
        return spec.aspect.size(720)
    }

    const val FPS = 30

    /**
     * Prepara il collage come video (H.264, 30 fotogrammi al secondo, audio muto): poi è una clip come le altre,
     * in anteprima e nel video esportato. Restituisce la misura in pixel.
     */
    fun renderVideo(ctx: Context, spec: CollageSpec, out: File, caption: Typeface?, onProgress: (Float) -> Unit): Pair<Int, Int> {
        DesignFonts.init(ctx)
        val t = CollageTemplates.byId(spec.templateId, spec.aspect) ?: error("modello non trovato")
        val (w, h) = videoSize(spec)
        val frameUs = 1_000_000L / FPS
        val total = (spec.durationUs / frameUs).toInt().coerceAtLeast(1)
        val photos = HashMap<Int, Bitmap?>()
        val videos = HashMap<Int, VideoFrames>()
        spec.items.forEachIndexed { i, it ->
            val side = neededSide(t, spec, i, w, h)
            if (it.video) videos[i] = VideoFrames(ctx, it, side) else photos[i] = loadPhoto(ctx, it.uri, side)
        }
        val frames = Frames { i, tu -> if (i in videos) videos[i]!!.at(tu) else photos[i] }

        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * FPS / 8).toInt().coerceIn(4_000_000, 50_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        // Audio muto: la clip si comporta come un video normale nel montaggio.
        val audio = runCatching { Reverser.encodeAac(ShortArray((48_000L * 2 * spec.durationUs / 1_000_000).toInt()), 48_000, 2) }.getOrNull()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var vTrack = -1
        var started = false
        var written = 0
        var done = false
        val info = MediaCodec.BufferInfo()
        fun drain(timeoutUs: Long) {
            while (true) {
                val i = enc.dequeueOutputBuffer(info, timeoutUs)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        vTrack = muxer.addTrack(enc.outputFormat)
                        val aTrack = audio?.let { muxer.addTrack(it.first) }
                        muxer.start(); started = true
                        if (audio != null && aTrack != null) audio.second.forEach { (bytes, bi) -> muxer.writeSampleData(aTrack, ByteBuffer.wrap(bytes), bi) }
                    }
                    i >= 0 -> {
                        val b = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            // I tempi della superficie sono quelli del disegno (più lento del vero): si rimettono in fila a 30 fps.
                            info.presentationTimeUs = written * frameUs
                            muxer.writeSampleData(vTrack, b, info)
                            written++
                        }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { done = true; return }
                    }
                }
            }
        }
        try {
            for (k in 0 until total) {
                val tu = k * frameUs
                val canvas = runCatching { surface.lockHardwareCanvas() }.getOrElse { surface.lockCanvas(null) }
                try {
                    draw(canvas, w, h, t, spec, tu, false, frames, caption)
                } finally { surface.unlockCanvasAndPost(canvas) }
                drain(0)
                if (k % 5 == 0) onProgress(k.toFloat() / total)
            }
            enc.signalEndOfInputStream()
            var guard = 0
            while (!done && guard++ < 600) drain(10_000)
            check(started && written > 0) { "il codificatore non ha prodotto fotogrammi" }
        } finally {
            runCatching { enc.stop() }; enc.release()
            surface.release()
            runCatching { if (started) muxer.stop() }; muxer.release()
            videos.values.forEach { it.close() }
            photos.values.forEach { it?.recycle() }
        }
        onProgress(1f)
        return w to h
    }
}
