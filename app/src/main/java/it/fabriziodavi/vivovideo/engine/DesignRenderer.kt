package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import it.fabriziodavi.vivovideo.model.CollageAnim
import it.fabriziodavi.vivovideo.model.CollageCell
import it.fabriziodavi.vivovideo.model.CollageMotion
import it.fabriziodavi.vivovideo.model.CollagePlan
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.CollageTemplate
import it.fabriziodavi.vivovideo.model.ColorNames
import it.fabriziodavi.vivovideo.model.DAlign
import it.fabriziodavi.vivovideo.model.DBarcode
import it.fabriziodavi.vivovideo.model.DCalendar
import it.fabriziodavi.vivovideo.model.DColor
import it.fabriziodavi.vivovideo.model.DEl
import it.fabriziodavi.vivovideo.model.DFilter
import it.fabriziodavi.vivovideo.model.DFont
import it.fabriziodavi.vivovideo.model.DLayout
import it.fabriziodavi.vivovideo.model.DPalette
import it.fabriziodavi.vivovideo.model.DPath
import it.fabriziodavi.vivovideo.model.DPattern
import it.fabriziodavi.vivovideo.model.DPatternKind
import it.fabriziodavi.vivovideo.model.DPhoto
import it.fabriziodavi.vivovideo.model.DPhotoWord
import it.fabriziodavi.vivovideo.model.DRect
import it.fabriziodavi.vivovideo.model.DRansom
import it.fabriziodavi.vivovideo.model.DShape
import it.fabriziodavi.vivovideo.model.DSplat
import it.fabriziodavi.vivovideo.model.DStamp
import it.fabriziodavi.vivovideo.model.DTorn
import it.fabriziodavi.vivovideo.model.DText
import it.fabriziodavi.vivovideo.model.Design
import it.fabriziodavi.vivovideo.model.DesignCount
import it.fabriziodavi.vivovideo.model.DesignMeta
import it.fabriziodavi.vivovideo.model.DesignTexts
import it.fabriziodavi.vivovideo.model.TextFont
import kotlin.math.max
import kotlin.math.min

/** I font dei modelli grafici (caricati una volta). */
object DesignFonts {
    private val faces = HashMap<DFont, Typeface>()
    @Volatile private var app: Context? = null

    fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }

    fun get(f: DFont): Typeface = synchronized(faces) {
        faces[f] ?: load(f).also { if (app != null) faces[f] = it }
    }

    private fun load(f: DFont): Typeface {
        val ctx = app ?: return fallback(f)
        fun asset(name: String, vars: String? = null): Typeface? = runCatching {
            Typeface.Builder(ctx.assets, "fonts/$name").apply { if (vars != null) setFontVariationSettings(vars) }.build()
        }.getOrNull()
        return when (f) {
            DFont.SERIF -> asset("instrument_serif.ttf")
            DFont.SERIF_IT -> asset("instrument_serif_italic.ttf")
            DFont.GROTESK -> asset("bricolage.ttf", "'wght' 800, 'opsz' 96")
            DFont.GROTESK_MED -> asset("bricolage.ttf", "'wght' 500, 'opsz' 48")
            DFont.ANTON -> TextRenderer.typeface(ctx, TextFont.IMPATTO)
            DFont.ARCHIVO -> TextRenderer.typeface(ctx, TextFont.POSTER)
            DFont.MONO -> asset("space_mono.ttf")
            DFont.MONO_BOLD -> asset("space_mono_bold.ttf")
            DFont.HAND -> asset("caveat.ttf", "'wght' 700")
            DFont.SCRIPT -> asset("great_vibes.ttf")
            DFont.SANS -> TextRenderer.typeface(ctx, TextFont.MODERNO)
        } ?: fallback(f)
    }

    private fun fallback(f: DFont): Typeface = when (f) {
        DFont.SERIF, DFont.SERIF_IT -> Typeface.create(Typeface.SERIF, if (f == DFont.SERIF_IT) Typeface.ITALIC else Typeface.NORMAL)
        DFont.MONO, DFont.MONO_BOLD -> Typeface.create(Typeface.MONOSPACE, if (f == DFont.MONO_BOLD) Typeface.BOLD else Typeface.NORMAL)
        else -> Typeface.DEFAULT_BOLD
    }
}

/**
 * Disegna un modello grafico ([Design]) in un istante: lo stesso codice per l'anteprima, l'immagine, la stampa e
 * ogni fotogramma del video. Nel video le foto entrano col movimento scelto, le scritte salgono una dopo l'altra,
 * i numeri contano, i tratti (percorsi, frecce, profili) si disegnano.
 */
object DesignRenderer {
    fun tint(c: Long, d: Design, spec: CollageSpec): Int = when (c) {
        DColor.ACC -> (spec.accent ?: d.accents.first()).toInt()
        DColor.ACC2 -> (spec.accent2 ?: d.accents2.firstOrNull() ?: d.accents.first()).toInt()
        DColor.BG -> if (DColor.isToken(d.bg)) tint(d.bg.takeIf { it != DColor.BG } ?: DColor.WHITE, d, spec) else d.bg.toInt()
        else -> c.toInt()
    }

    private fun rectOf(d: Design, e: DEl, w: Int, h: Int): RectF {
        val r = DLayout.rect(d, e.box, w.toFloat(), h.toFloat())
        return RectF(r[0], r[1], r[0] + r[2], r[1] + r[3])
    }

    /** Tempo di entrata delle scritte e delle decorazioni: dopo le foto, una alla volta. */
    private fun lateU(k: Int, n: Int, tUs: Long, dur: Long, still: Boolean, len: Float = 0.45f): Float {
        if (still) return 1f
        val start = dur * 0.28f + k * min(160_000f, dur * 0.4f / n.coerceAtLeast(1))
        return ((tUs - start) / min(450_000f, dur * len)).coerceIn(0f, 1f)
    }

    fun draw(c: Canvas, w: Int, h: Int, t: CollageTemplate, spec: CollageSpec, tUs: Long, still: Boolean, frames: CollageRenderer.Frames, selected: Int = -1) {
        val d = t.design ?: return
        val meta = DesignMeta.of(spec.items)
        val s = DLayout.scale(d, w.toFloat(), h.toFloat())
        val dur = spec.durationUs
        c.drawColor(tint(d.bg, d, spec))
        val cam = CollageAnim.camera(spec.motion, t.cells.firstOrNull(), w.toFloat() / h, tUs, dur, still)
        c.save()
        c.translate(w / 2f, h / 2f); c.scale(cam.scale, cam.scale); c.translate(-cam.cx * w, -cam.cy * h)

        val photos = d.els.count { it is DPhoto } + d.words.sumOf { DesignTexts.word(it, spec, meta).length }
        val late = d.els.count { it !is DPhoto && it !is DPhotoWord && it !is DTorn && it !is DPattern }
        val torns = d.els.count { it is DTorn }
        var pi = 0; var li = 0; var ti = 0
        for (e in d.els) {
            val r = rectOf(d, e, w, h)
            when (e) {
                is DPhoto -> {
                    val cell = t.cells.getOrNull(e.slot) ?: CollageCell(0f, 0f, 1f, 1f)
                    val pose = CollageAnim.pose(spec.motion, pi++, photos, cell, tUs, dur, spec.kenBurns, still)
                    if (pose.alpha > 0.002f && pose.reveal > 0.002f) drawPhoto(c, d, e, r, s, spec, frames, tUs, pose, w, h, selected == e.slot, false)
                }
                is DPhotoWord -> pi = drawWord(c, d, e, r, s, spec, meta, frames, tUs, still, pi, photos, w, h, selected)
                is DText -> {
                    val u = lateU(li++, late, tUs, dur, still)
                    if (u > 0f) {
                        drawText(c, d, e, r, s, spec, meta, u)
                        if (e.behind >= 0 && spec.behind && u >= 1f) subject(c, d, e.behind, s, spec, frames, tUs, still, w, h, t)
                    }
                }
                is DRect -> {
                    val small = r.width() * r.height() < 0.05f * w * h && e.grad.isEmpty()
                    val u = if (small) lateU(li++, late, tUs, dur, still) else 1f
                    if (u > 0f) withPop(c, r, if (small) u else 1f) { drawRect(c, d, e, r, s, spec) }
                }
                is DPath -> {
                    val u = lateU(li++, late, tUs, dur, still, if (e.draw) 0.5f else 0.45f)
                    if (u > 0f) if (e.draw) drawPath(c, d, e, r, s, spec, u) else withPop(c, r, u) { drawPath(c, d, e, r, s, spec, 1f) }
                }
                is DPattern -> drawPattern(c, d, e, r, s, spec, if (still) 0f else tUs / 1_000_000f)
                is DBarcode -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) withPop(c, r, u) { drawBarcode(c, d, e, r, spec) } }
                is DPalette -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) drawPalette(c, d, e, r, s, spec, frames, tUs, u) }
                is DTorn -> {
                    // I pezzi di carta entrano per primi, a strappo, alternando sinistra e destra.
                    val k = ti++
                    val u = if (still) 1f else ((tUs - k * min(110_000f, dur * 0.25f / torns.coerceAtLeast(1))) / min(500_000f, dur * 0.2f)).coerceIn(0f, 1f)
                    if (u > 0f) {
                        c.save()
                        c.translate((if (k % 2 == 0) -1 else 1) * (1 - CollageAnim.outCubic(u)) * w * 0.8f, 0f)
                        drawTorn(c, d, e, r, s, spec, w, h)
                        c.restore()
                    }
                }
                is DRansom -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) drawRansom(c, d, e, r, s, spec, meta, tUs, still) }
                is DCalendar -> { val u = lateU(li++, late, tUs, dur, still, 0.5f); if (u > 0f) drawCalendar(c, d, e, r, s, spec, meta, u) }
                is DSplat -> { val u = lateU(li++, late, tUs, dur, still, 0.2f); if (u > 0f) drawSplat(c, d, e, r, s, spec, u) }
                is DStamp -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) withPop(c, r, u) { drawStamp(c, d, e, r, s, spec, meta) } }
            }
        }
        c.restore()
        if (d.grain || spec.style.grain) CollageRenderer.grain(c, w, h, if (still) 0 else (tUs / 33_333L).toInt(), 18)
    }

    /** Entrata delle decorazioni: appaiono con un piccolo rimbalzo. */
    private inline fun withPop(c: Canvas, r: RectF, u: Float, body: () -> Unit) {
        if (u >= 1f) { body(); return }
        val k = 0.6f + 0.4f * CollageAnim.outBack(u)
        val layer = c.saveLayerAlpha(null, (min(1f, u * 2.5f) * 255).toInt())
        c.scale(k, k, r.centerX(), r.centerY())
        body()
        c.restoreToCount(layer)
    }

    // ---------------- Foto ----------------

    private fun torn(r: RectF, top: Boolean): Path {
        val p = Path()
        val n = 22
        val amp = r.height() * 0.035f
        fun jag(i: Int) = if (i % 2 == 0) 0f else amp * (0.5f + 0.5f * ((i * 37 % 7) / 6f))
        if (top) {
            p.moveTo(r.left, r.top + amp)
            for (i in 1..n) p.lineTo(r.left + r.width() * i / n, r.top + amp - jag(i) + amp * 0.3f)
            p.lineTo(r.right, r.bottom); p.lineTo(r.left, r.bottom); p.close()
        } else {
            p.moveTo(r.left, r.top); p.lineTo(r.right, r.top)
            for (i in n downTo 0) p.lineTo(r.left + r.width() * i / n, r.bottom - amp + jag(i) - amp * 0.3f)
            p.close()
        }
        return p
    }

    private fun shape(e: DPhoto, r: RectF, s: Float): Path {
        val p = Path()
        when {
            e.poly.isNotEmpty() -> {
                e.poly.forEachIndexed { i, q -> val x = r.left + q.x * r.width(); val y = r.top + q.y * r.height(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                p.close()
            }
            e.shape == DShape.CIRCLE -> p.addOval(r, Path.Direction.CW)
            e.shape == DShape.ARCH -> {
                val rad = r.width() / 2
                p.moveTo(r.left, r.top + rad)
                p.arcTo(RectF(r.left, r.top, r.right, r.top + 2 * rad), 180f, 180f, false)
                p.lineTo(r.right, r.bottom); p.lineTo(r.left, r.bottom); p.close()
            }
            e.shape == DShape.PILL -> { val rr = min(r.width(), r.height()) / 2; p.addRoundRect(r, rr, rr, Path.Direction.CW) }
            e.shape == DShape.TORN_BOTTOM -> return torn(r, false)
            e.shape == DShape.TORN_TOP -> return torn(r, true)
            e.shape == DShape.TORN -> return tornPath(r, e.seed, 15, 6f * s)
            e.radius > 0 -> {
                val k = e.radius * s
                fun on(bit: Int) = if (e.corners and bit != 0) k else 0f
                p.addRoundRect(r, floatArrayOf(on(1), on(1), on(2), on(2), on(4), on(4), on(8), on(8)), Path.Direction.CW)
            }
            else -> p.addRect(r, Path.Direction.CW)
        }
        return p
    }

    /** Dove va la foto (taglia i bordi per riempire), con lo spostamento e lo zoom scelti. */
    private fun cover(bw: Float, bh: Float, area: RectF, zoom: Float, panX: Float, panY: Float): RectF {
        val k = max(area.width() / bw, area.height() / bh) * zoom
        val dw = bw * k; val dh = bh * k
        val ox = (dw - area.width()) / 2 * panX.coerceIn(-1f, 1f)
        val oy = (dh - area.height()) / 2 * panY.coerceIn(-1f, 1f)
        val l = area.centerX() - dw / 2 - ox; val t = area.centerY() - dh / 2 - oy
        return RectF(l, t, l + dw, t + dh)
    }

    private fun filterPaint(f: DFilter, d: Design, spec: CollageSpec): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (f) {
            DFilter.BW -> p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
                // Un po' più di contrasto, come una stampa in bianco e nero.
                postConcat(ColorMatrix(floatArrayOf(1.2f, 0f, 0f, 0f, -22f, 0f, 1.2f, 0f, 0f, -22f, 0f, 0f, 1.2f, 0f, -22f, 0f, 0f, 0f, 1f, 0f)))
            })
            DFilter.DUOTONE -> {
                val light = tint(DColor.ACC, d, spec); val dark = tint(DColor.ACC2, d, spec)
                val dr = Color.red(dark).toFloat(); val dg = Color.green(dark).toFloat(); val db = Color.blue(dark).toFloat()
                val lr = (Color.red(light) - dr) / 255f; val lg = (Color.green(light) - dg) / 255f; val lb = (Color.blue(light) - db) / 255f
                // Luminosità (con più contrasto) → dal colore scuro al chiaro.
                val y = floatArrayOf(0.299f * 1.25f, 0.587f * 1.25f, 0.114f * 1.25f)
                val off = -0.125f * 255
                p.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    lr * y[0], lr * y[1], lr * y[2], 0f, dr + lr * off,
                    lg * y[0], lg * y[1], lg * y[2], 0f, dg + lg * off,
                    lb * y[0], lb * y[1], lb * y[2], 0f, db + lb * off,
                    0f, 0f, 0f, 1f, 0f,
                )))
            }
            else -> {}
        }
        return p
    }

    private fun drawPhoto(
        c: Canvas, d: Design, e: DPhoto, r: RectF, s: Float, spec: CollageSpec, frames: CollageRenderer.Frames, tUs: Long,
        pose: it.fabriziodavi.vivovideo.model.CellPose, w: Int, h: Int, selected: Boolean, subjectOnly: Boolean,
    ) {
        c.save()
        c.translate(pose.dx * w, pose.dy * h)
        c.rotate(e.rot + pose.rot, r.centerX(), r.centerY())
        c.scale(pose.scale, pose.scale, r.centerX(), r.centerY())
        val layer = if (pose.alpha < 0.999f) c.saveLayerAlpha(null, (pose.alpha * 255).toInt()) else -1
        if (pose.reveal < 0.999f) c.clipRect(RectF(r.left - w, r.top - h, r.left + (r.width() + w * 0.05f) * pose.reveal, r.bottom + h))
        val path = shape(e, r, s)
        if (e.cutout && !subjectOnly) {
            val item = CollagePlan.itemFor(e.slot, spec.items.size)
            val bmp = if (item >= 0) frames.get(item, tUs) else null
            val it = spec.items.getOrNull(item)
            val mask = if (it == null || it.video) null else frames.mask(item) ?: SubjectMasks.get(it.uri)
            if (bmp != null && !bmp.isRecycled && mask != null) {
                drawCutout(c, d, e, r, s, spec, bmp, mask, cover(bmp.width.toFloat(), bmp.height.toFloat(), r, it!!.zoom.coerceAtLeast(1f) * pose.inner, it.panX, it.panY))
                if (selected) c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(3f, 4f * s); color = 0xFFFFC83D.toInt() })
                if (layer >= 0) c.restoreToCount(layer)
                c.restore()
                return
            }
        }
        if (!subjectOnly && (e.border > 0 || e.shadow > 0)) {
            val b = e.border * s
            val card = RectF(r.left - b, r.top - b, r.right + b, r.bottom + b + e.bottomPad * s)
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (e.border > 0) tint(e.borderColor, d, spec) else Color.BLACK
                if (e.shadow > 0) setShadowLayer(e.shadow * s, 0f, e.shadow * s * 0.45f, 0x4D000000)
            }
            val outer = when {
                e.shape == DShape.TORN -> tornPath(card, e.seed + 3, 15, 6f * s)
                e.poly.isNotEmpty() || e.shape == DShape.TORN_BOTTOM || e.shape == DShape.TORN_TOP -> shape(e, card, s)
                e.shape == DShape.RECT && e.radius > 0 -> shape(e.copy(radius = e.radius + e.border), card, s)
                else -> shape(e.copy(radius = e.radius + e.border), card, s)
            }
            c.drawPath(outer, cp)
        }
        val item = CollagePlan.itemFor(e.slot, spec.items.size)
        val bmp = if (item >= 0) frames.get(item, tUs) else null
        c.save()
        c.clipPath(path)
        if (bmp == null || bmp.isRecycled) {
            if (!subjectOnly) c.drawColor(0xFF3A3A3A.toInt())
        } else {
            val it = spec.items[item]
            val src = if (e.filter == DFilter.BLUR) CollageRenderer.blurred(bmp) else bmp
            val bounds = RectF().also { b -> path.computeBounds(b, true) }
            val dst = cover(bmp.width.toFloat(), bmp.height.toFloat(), bounds, it.zoom.coerceAtLeast(1f) * pose.inner * (if (e.filter == DFilter.BLUR) 1.15f else 1f), it.panX, it.panY)
            if (subjectOnly) {
                val mask = if (it.video) null else frames.mask(item) ?: SubjectMasks.get(it.uri)
                if (mask != null) {
                    val l = c.saveLayer(bounds, null)
                    c.drawBitmap(src, null, dst, filterPaint(e.filter, d, spec))
                    c.drawBitmap(mask, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
                    c.restoreToCount(l)
                }
            } else if (e.filter == DFilter.HALFTONE) {
                halftone(c, src, dst, bounds, max(1.6f, 4.5f * s), tint(e.ink, d, spec), tint(e.paper, d, spec), e.multiply, e.shiftX * s, e.shiftY * s)
            } else c.drawBitmap(src, null, dst, filterPaint(e.filter, d, spec))
        }
        c.restore()
        if (!subjectOnly && e.outline > 0) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = e.outline * s; color = DColor.BLACK.toInt() })
        if (selected && !subjectOnly) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(3f, 4f * s); color = 0xFFFFC83D.toInt() })
        if (layer >= 0) c.restoreToCount(layer)
        c.restore()
    }

    // ---------------- Carta strappata, retino, adesivo, lettere ritagliate, timbro ----------------

    private fun rnd(i: Int, seed: Int): Float {
        var x = i * 374761393 + seed * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535f
    }

    /**
     * Contorno di carta strappata: sui lati [edges] (1 sopra, 2 destra, 4 sotto, 8 sinistra) il bordo entra
     * di 0…[amp] in modo irregolare (rumore a due scale, come le fibre vere); gli altri lati restano dritti.
     */
    fun tornPath(r: RectF, seed: Int, edges: Int, amp: Float): Path {
        val p = Path()
        val step = max(2f, amp * 0.8f)
        var first = true
        fun edge(ax: Float, ay: Float, bx: Float, by: Float, nx: Float, ny: Float, torn: Boolean, base: Int) {
            val len = kotlin.math.hypot(bx - ax, by - ay)
            val n = if (torn) (len / step).toInt().coerceIn(1, 600) else 1
            for (k in 0..n) {
                if (k == 0 && !first) continue
                val t = k.toFloat() / n
                val coarse = rnd(base + k / 5, seed + 17) * (1 - (k % 5) / 5f) + rnd(base + k / 5 + 1, seed + 17) * ((k % 5) / 5f)
                val off = if (torn && k in 1 until n) amp * (0.45f * rnd(base + k, seed) + 0.55f * coarse) else 0f
                val x = ax + (bx - ax) * t + nx * off; val y = ay + (by - ay) * t + ny * off
                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
            }
        }
        edge(r.left, r.top, r.right, r.top, 0f, 1f, edges and 1 != 0, 0)
        edge(r.right, r.top, r.right, r.bottom, -1f, 0f, edges and 2 != 0, 10_000)
        edge(r.right, r.bottom, r.left, r.bottom, 0f, -1f, edges and 4 != 0, 20_000)
        edge(r.left, r.bottom, r.left, r.top, 1f, 0f, edges and 8 != 0, 30_000)
        p.close()
        return p
    }

    private fun drawTorn(c: Canvas, d: Design, e: DTorn, r: RectF, s: Float, spec: CollageSpec, w: Int, h: Int) {
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        val amp = 7f * s
        val rim = tornPath(r, e.seed, e.edges, amp)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (e.fiber) 0xFFFBF8F1.toInt() else tint(e.color, d, spec)
            if (e.shadow > 0) setShadowLayer(e.shadow * s, 0f, e.shadow * s * 0.4f, 0x40000000)
        }
        c.drawPath(rim, shadow)
        val body = if (e.fiber) {
            // Il colore sta un po' più dentro, con uno strappo suo: fra i due si vede la fibra bianca della carta.
            val k = amp * 0.55f
            val inner = RectF(r.left + if (e.edges and 8 != 0) k else 0f, r.top + if (e.edges and 1 != 0) k else 0f,
                r.right - if (e.edges and 2 != 0) k else 0f, r.bottom - if (e.edges and 4 != 0) k else 0f)
            tornPath(inner, e.seed + 5, e.edges, amp).also { c.drawPath(it, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.color, d, spec) }) }
        } else rim
        if (e.grain) {
            c.save(); c.clipPath(body)
            CollageRenderer.grain(c, w, h, e.seed, 28)
            c.restore()
        }
        c.restore()
    }

    /** Foto a retino: un punto per cella, grande quanto è scuro quel punto della foto. */
    private fun halftone(c: Canvas, src: Bitmap, dst: RectF, area: RectF, cell: Float, ink: Int, paper: Int, multiply: Boolean, sx: Float, sy: Float) {
        if (Color.alpha(paper) > 0) c.drawColor(paper)
        val cols = (dst.width() / cell).toInt().coerceIn(2, 600)
        val rows = (dst.height() / cell).toInt().coerceIn(2, 600)
        val sm = Bitmap.createScaledBitmap(src, cols, rows, true)
        val px = IntArray(cols * rows); sm.getPixels(px, 0, cols, 0, 0, cols, rows)
        if (sm !== src) sm.recycle()
        val cw = dst.width() / cols; val ch = dst.height() / rows
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; if (multiply) xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
        for (j in 0 until rows) {
            val cy = dst.top + (j + 0.5f) * ch + sy
            if (cy < area.top - ch || cy > area.bottom + ch) continue
            for (i in 0 until cols) {
                val cx = dst.left + (i + 0.5f) * cw + sx
                if (cx < area.left - cw || cx > area.right + cw) continue
                val v = px[j * cols + i]
                val lum = (0.299f * Color.red(v) + 0.587f * Color.green(v) + 0.114f * Color.blue(v)) / 255f
                val rad = min(cw, ch) * 0.64f * kotlin.math.sqrt((1 - lum).coerceAtLeast(0f))
                if (rad > 0.25f) c.drawCircle(cx, cy, rad, p)
            }
        }
    }

    /** La persona ritagliata come un adesivo: bordo bianco attorno alla sagoma, ombra, poi la persona. */
    private fun drawCutout(c: Canvas, d: Design, e: DPhoto, r: RectF, s: Float, spec: CollageSpec, src: Bitmap, mask: Bitmap, dst: RectF) {
        val b = max(1f, e.border * s)
        c.save()
        val shadow = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0x47000000, PorterDuff.Mode.SRC_IN) }
        c.drawBitmap(mask, null, RectF(dst).apply { offset(b * 0.3f, b * 0.9f + e.shadow * s * 0.3f) }, shadow)
        val edge = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(tint(e.borderColor, d, spec), PorterDuff.Mode.SRC_IN) }
        for (ring in listOf(1f, 0.5f)) for (k in 0 until 16) {
            val a = (2 * Math.PI * k / 16).toFloat()
            c.drawBitmap(mask, null, RectF(dst).apply { offset(kotlin.math.cos(a) * b * ring, kotlin.math.sin(a) * b * ring) }, edge)
        }
        // Strato senza bordi: con i bordi restava una riga chiara sul suo contorno.
        val l = c.saveLayer(null, null)
        c.drawBitmap(src, null, dst, filterPaint(e.filter, d, spec))
        c.drawBitmap(mask, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        c.restoreToCount(l)
        c.restore()
    }

    private val ransomFonts = listOf(DFont.ANTON, DFont.SERIF, DFont.ARCHIVO, DFont.MONO_BOLD, DFont.GROTESK, DFont.SERIF_IT)
    private val ransomPapers = listOf(
        0xFFF7F3EA to 0xFF111111, 0xFF111111 to 0xFFFFFFFF, 0xFFFFD23F to 0xFF111111, 0xFFE63946 to 0xFFFFFFFF,
        0xFFC8A57A to 0xFF1B1B1B, 0xFF1D4ED8 to 0xFFFFFFFF, 0xFFFFFFFF to 0xFFE63946,
    )

    /** Lettere ritagliate: ognuna su un pezzetto di carta suo, con un font e un colore suoi, un po' storta. */
    private fun drawRansom(c: Canvas, d: Design, e: DRansom, r: RectF, s: Float, spec: CollageSpec, meta: DesignMeta, tUs: Long, still: Boolean) {
        val text = (spec.texts[e.id] ?: DesignTexts.fill(e.text, meta)).uppercase(java.util.Locale.ITALY).trim()
        if (text.isEmpty()) return
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        class G(val ch: Char, val font: DFont, val sc: Float, val rot: Float, val bg: Long, val fg: Long, val w100: Float)
        var idx = 0
        val rows = lines.map { line ->
            line.map { ch ->
                val k = idx++ + e.seed * 31
                if (ch == ' ') G(ch, DFont.SANS, 1f, 0f, 0L, 0L, 38f) else {
                    val f = ransomFonts[(rnd(k, 3) * ransomFonts.size).toInt().coerceAtMost(ransomFonts.size - 1)]
                    val sc = 0.86f + 0.28f * rnd(k, 5)
                    p.typeface = DesignFonts.get(f); p.textSize = 100f * sc
                    val (bg, fg) = ransomPapers[(rnd(k, 7) * ransomPapers.size).toInt().coerceAtMost(ransomPapers.size - 1)]
                    G(ch, f, sc, (rnd(k, 11) - 0.5f) * 14f, bg, fg, p.measureText(ch.toString()) + 28f * sc)
                }
            }
        }
        val gap = 6f
        val rowW = rows.map { row -> row.sumOf { it.w100.toDouble() }.toFloat() + gap * (row.size - 1) }
        val rowH = rows.map { row -> 125f * (row.maxOfOrNull { it.sc } ?: 1f) }
        val k = min(r.width() / rowW.max(), r.height() / (rowH.sum() + gap * 2 * (rows.size - 1)))
        var y = r.top + (r.height() - (rowH.sum() + gap * 2 * (rows.size - 1)) * k) / 2
        var n = 0
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        rows.forEachIndexed { ri, row ->
            var x = r.left + (r.width() - rowW[ri] * k) / 2
            val cyRow = y + rowH[ri] * k / 2
            for (g in row) {
                val gw = g.w100 * k
                if (g.ch != ' ') {
                    val u = if (still) 1f else ((tUs - spec.durationUs * 0.25f - n * 90_000f) / 260_000f).coerceIn(0f, 1f)
                    n++
                    if (u > 0f) {
                        val ph = 112f * g.sc * k
                        val piece = RectF(x, cyRow - ph / 2, x + gw, cyRow + ph / 2)
                        c.save()
                        c.rotate(g.rot, piece.centerX(), piece.centerY())
                        val pop = 0.5f + 0.5f * CollageAnim.outBack(u)
                        c.scale(pop, pop, piece.centerX(), piece.centerY())
                        c.drawPath(tornPath(piece, n * 7 + e.seed, 15, 2.2f * s), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = g.bg.toInt(); setShadowLayer(3f * s, 0f, 1.5f * s, 0x33000000)
                        })
                        p.typeface = DesignFonts.get(g.font); p.textSize = 100f * g.sc * k; p.color = g.fg.toInt()
                        val fm = p.fontMetrics
                        c.drawText(g.ch.toString(), piece.centerX() - p.measureText(g.ch.toString()) / 2, piece.centerY() - (fm.ascent + fm.descent) / 2, p)
                        c.restore()
                    }
                }
                x += gw + gap * k
            }
            y += rowH[ri] * k + gap * 2 * k
        }
        c.restore()
    }

    /** Schizzo: macchia a raggi irregolari e gocce lungo i raggi; nel video si allarga di colpo. */
    private fun drawSplat(c: Canvas, d: Design, e: DSplat, r: RectF, s: Float, spec: CollageSpec, u: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.color, d, spec) }
        val k = if (u >= 1f) 1f else CollageAnim.outBack(u)
        val cx = r.centerX(); val cy = r.centerY()
        val rad = min(r.width(), r.height()) * 0.28f * k
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, cx, cy)
        val n = 26
        val path = Path()
        for (i in 0..n) {
            val a = (2 * Math.PI * i / n).toFloat()
            val rr = rad * (0.75f + 0.5f * rnd(i % n, e.seed) + if (i % 5 == 0) 0.45f * rnd(i, e.seed + 3) else 0f)
            val x = cx + kotlin.math.cos(a) * rr; val y = cy + kotlin.math.sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.quadTo(cx + kotlin.math.cos(a - 0.12f) * rr * 1.05f, cy + kotlin.math.sin(a - 0.12f) * rr * 1.05f, x, y)
        }
        path.close()
        c.drawPath(path, p)
        // Gocce sparse, più piccole man mano che si allontanano.
        for (i in 0 until 34) {
            val a = rnd(i, e.seed + 7) * 6.283f
            val dist = rad * (1.2f + 2.4f * rnd(i, e.seed + 11)) * k
            val gr = rad * (0.05f + 0.16f * rnd(i, e.seed + 13)) * (1.4f - dist / (rad * 3.8f + 1f))
            if (gr > 0.4f) c.drawCircle(cx + kotlin.math.cos(a) * dist, cy + kotlin.math.sin(a) * dist, gr, p)
        }
        c.restore()
    }

    private val weekdays = listOf("L", "M", "M", "G", "V", "S", "D")
    private val monthNames = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")

    /** Calendario del mese con il giorno cerchiato a mano (nel video il cerchio si disegna). */
    private fun drawCalendar(c: Canvas, d: Design, e: DCalendar, r: RectF, s: Float, spec: CollageSpec, meta: DesignMeta, u: Float) {
        val txt = spec.texts[e.id] ?: DesignTexts.fill(e.text, meta)
        val m = Regex("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})").find(txt)
        val cal = java.util.Calendar.getInstance(java.util.Locale.ITALY)
        if (m != null) cal.set(m.groupValues[3].toInt(), (m.groupValues[2].toInt() - 1).coerceIn(0, 11), 1) else cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val day = m?.groupValues?.get(1)?.toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val first = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7  // lunedì = 0
        val col = tint(e.color, d, spec); val acc = tint(e.accent, d, spec)
        val rows = (first + days + 6) / 7
        val cw = r.width() / 7
        val head = cw * 0.9f
        val ch = (r.height() - head * 2) / rows
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; textAlign = Paint.Align.CENTER }
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        p.typeface = DesignFonts.get(DFont.MONO_BOLD); p.textSize = head * 0.42f; p.letterSpacing = 0.2f
        c.drawText("${monthNames[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}".uppercase(java.util.Locale.ITALY), r.centerX(), r.top + head * 0.55f, p)
        p.letterSpacing = 0f; p.typeface = DesignFonts.get(DFont.MONO); p.textSize = min(cw, ch) * 0.34f
        weekdays.forEachIndexed { i, w -> c.drawText(w, r.left + cw * (i + 0.5f), r.top + head * 1.6f, p) }
        p.typeface = DesignFonts.get(DFont.SERIF); p.textSize = min(cw, ch) * 0.66f
        val fm = p.fontMetrics
        for (dd in 1..days) {
            val k = first + dd - 1
            val cx = r.left + cw * (k % 7 + 0.5f); val cy = r.top + head * 2 + ch * (k / 7 + 0.5f)
            p.color = if (dd == day) acc else col
            c.drawText("$dd", cx, cy - (fm.ascent + fm.descent) / 2, p)
            if (dd == day) {
                // Cerchio tracciato a mano: un giro e un pezzetto, un po' storto.
                val rad = min(cw, ch) * 0.46f
                val path = Path()
                for (i in 0..44) {
                    val a = (-1.9f + i / 44f * 7.1f)
                    val rr = rad * (1f + 0.08f * kotlin.math.sin(a * 2.3f))
                    val x = cx + kotlin.math.cos(a) * rr * 1.15f; val y = cy + kotlin.math.sin(a) * rr
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                var draw = path
                if (u < 1f) { draw = Path(); PathMeasure(path, false).let { pm -> pm.getSegment(0f, pm.length * u, draw, true) } }
                c.drawPath(draw, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(2f, 3.2f * s); color = acc; strokeCap = Paint.Cap.ROUND })
            }
        }
        c.restore()
    }

    private fun drawStamp(c: Canvas, d: Design, e: DStamp, r: RectF, s: Float, spec: CollageSpec, meta: DesignMeta) {
        val text = (spec.texts[e.id] ?: DesignTexts.fill(e.text, meta)).uppercase(java.util.Locale.ITALY)
        val lines = text.split('\n').take(2)
        val col = tint(e.color, d, spec)
        val rad = min(r.width(), r.height()) / 2
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = col; alpha = 215; strokeWidth = rad * 0.055f }
        c.drawCircle(r.centerX(), r.centerY(), rad - ring.strokeWidth, ring)
        ring.strokeWidth = rad * 0.025f
        c.drawCircle(r.centerX(), r.centerY(), rad * 0.8f, ring)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 225; textAlign = Paint.Align.CENTER }
        lines.forEachIndexed { i, line ->
            p.typeface = DesignFonts.get(if (i == 0) DFont.MONO_BOLD else DFont.MONO)
            p.textSize = rad * (if (i == 0) 0.3f else 0.18f)
            val tw = p.measureText(line)
            if (tw > rad * 1.35f) p.textSize *= rad * 1.35f / tw
            val fm = p.fontMetrics
            val cy = r.centerY() + if (lines.size == 1) 0f else if (i == 0) -rad * 0.12f else rad * 0.24f
            c.drawText(line, r.centerX(), cy - (fm.ascent + fm.descent) / 2, p)
        }
        c.restore()
    }

    /** La persona della foto [slot] ridisegnata sopra (davanti al titolo). */
    private fun subject(c: Canvas, d: Design, slot: Int, s: Float, spec: CollageSpec, frames: CollageRenderer.Frames, tUs: Long, still: Boolean, w: Int, h: Int, t: CollageTemplate) {
        val e = d.els.firstOrNull { it is DPhoto && it.slot == slot } as? DPhoto ?: return
        val r = rectOf(d, e, w, h)
        val cell = t.cells.getOrNull(slot) ?: return
        val pi = d.els.filterIsInstance<DPhoto>().indexOf(e)
        val pose = CollageAnim.pose(spec.motion, pi, d.els.count { it is DPhoto }, cell, tUs, spec.durationUs, spec.kenBurns, still)
        drawPhoto(c, d, e, r, s, spec, frames, tUs, pose, w, h, false, true)
    }

    // ---------------- Parola con le foto dentro ----------------

    private fun drawWord(
        c: Canvas, d: Design, e: DPhotoWord, r: RectF, s: Float, spec: CollageSpec, meta: DesignMeta, frames: CollageRenderer.Frames,
        tUs: Long, still: Boolean, pi0: Int, photos: Int, w: Int, h: Int, selected: Int,
    ): Int {
        val word = DesignTexts.word(e, spec, meta)
        val (rows, perRow) = DLayout.wordGrid(word.length)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = DesignFonts.get(e.font); textSize = 100f; color = Color.BLACK }
        val gap = e.gap * s
        val bounds = word.map { ch -> Rect().also { p.getTextBounds(ch.toString(), 0, 1, it) } }
        val lines = word.indices.chunked(perRow)
        val rowW = lines.map { l -> l.sumOf { bounds[it].width() }.coerceAtLeast(1) }
        val maxH = bounds.maxOf { it.height() }.toFloat()
        // Misura che riempie il riquadro: ogni riga entra in larghezza, tutte le righe in altezza.
        val k = min(lines.indices.minOf { i -> (r.width() - gap * (lines[i].size - 1)) / rowW[i].toFloat() }, (r.height() - gap * (rows - 1)) / (rows * maxH))
        p.textSize = 100f * k
        val lh = maxH * k
        val top0 = r.top + (r.height() - (rows * lh + (rows - 1) * gap)) / 2
        var pi = pi0
        lines.forEachIndexed { row, idxs ->
            val ws = idxs.map { bounds[it].width() * k }
            var x = r.left + (r.width() - ws.sum() - gap * (idxs.size - 1)) / 2
            val base = top0 + row * (lh + gap) + lh
            idxs.forEachIndexed { j, i ->
                val b = bounds[i]
                val lx = x - b.left * k
                val lr = RectF(x, base - lh, x + ws[j], base)
                val slot = e.firstSlot + i
                val item = CollagePlan.itemFor(slot, spec.items.size)
                val pose = CollageAnim.pose(if (spec.motion == CollageMotion.ZOOM) CollageMotion.CASCADE else spec.motion, pi++, photos,
                    CollageCell(lr.left / w, lr.top / h, lr.width() / w, lr.height() / h), tUs, spec.durationUs, spec.kenBurns, still)
                if (pose.alpha > 0.002f) {
                    c.save()
                    c.translate(pose.dx * w, pose.dy * h)
                    c.scale(pose.scale, pose.scale, lr.centerX(), lr.centerY())
                    val layer = c.saveLayerAlpha(RectF(lr.left - lr.width(), lr.top - lr.height(), lr.right + lr.width(), lr.bottom + lr.height()), (pose.alpha * 255).toInt())
                    c.drawText(word[i].toString(), lx, base - (b.bottom * k), p)
                    val bmp = if (item >= 0) frames.get(item, tUs) else null
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
                    if (bmp != null && !bmp.isRecycled) {
                        val it = spec.items[item]
                        c.drawBitmap(bmp, null, cover(bmp.width.toFloat(), bmp.height.toFloat(), lr, it.zoom.coerceAtLeast(1f) * pose.inner, it.panX, it.panY), fill)
                    } else c.drawRect(lr, fill.apply { color = 0xFF3A3A3A.toInt() })
                    c.restoreToCount(layer)
                    if (selected == slot) c.drawRect(lr, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(3f, 4f * s); color = 0xFFFFC83D.toInt() })
                    c.restore()
                }
                x += ws[j] + gap
            }
        }
        return pi
    }

    // ---------------- Scritte ----------------

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> = text.split('\n').flatMap { para ->
        val out = ArrayList<String>()
        var cur = ""
        for (word in para.split(' ')) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (cur.isNotEmpty() && p.measureText(t) > maxW) { out += cur; cur = word } else cur = t
        }
        out += cur
        out
    }

    fun drawText(c: Canvas, d: Design, e: DText, r: RectF, s: Float, spec: CollageSpec, meta: DesignMeta, u: Float) {
        var txt = DesignTexts.text(e, spec, meta)
        if (txt.isBlank()) return
        if (e.count && u < 1f) txt = DesignCount.at(txt, CollageAnim.outCubic(u))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = DesignFonts.get(e.font)
            textSize = e.size * s
            letterSpacing = e.track
            textSkewX = e.skew
            color = tint(e.color, d, spec)
            if (e.stroke > 0) { style = Paint.Style.STROKE; strokeWidth = e.stroke * s; strokeJoin = Paint.Join.ROUND }
            if (e.shadow) setShadowLayer(e.size * s * 0.08f, 0f, e.size * s * 0.03f, 0x73000000)
            if (e.glow != null) setShadowLayer(e.size * s * 0.22f, 0f, 0f, tint(e.glow, d, spec))
        }
        val p2 = if (e.font2 != null || e.color2 != null) Paint(p).apply {
            typeface = DesignFonts.get(e.font2 ?: e.font)
            color = tint(e.color2 ?: e.color, d, spec)
        } else null
        val avail = r.width() - 2 * e.pad * s
        val lines = if (e.wrap) wrap(txt, p, avail) else txt.split('\n')
        fun paintOf(i: Int) = if (i > 0 && p2 != null) p2 else p
        // Le scritte cambiate da te possono essere più lunghe: si rimpiccioliscono per restare nel riquadro.
        val widest = lines.indices.maxOf { paintOf(it).measureText(lines[it]) }
        var k = if (widest > avail && !e.wrap) avail / widest else 1f
        val lh0 = e.size * s * e.line
        if (lines.size * lh0 * k > r.height() * 1.3f) k = min(k, r.height() * 1.3f / (lines.size * lh0))
        if (k < 1f) { p.textSize *= k; p2?.let { it.textSize *= k } }
        val lh = lh0 * k
        val alpha = min(1f, u * 1.6f)
        val dy = (1 - CollageAnim.outCubic(u)) * e.size * s * 0.35f
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        c.translate(0f, dy)
        val y0 = if (e.bottom) r.bottom - lines.size * lh else r.top
        lines.forEachIndexed { i, line ->
            val lp = paintOf(i)
            val fm = lp.fontMetrics
            val lw = lp.measureText(line)
            val base = y0 + i * lh + lh / 2 - (fm.ascent + fm.descent) / 2
            val x = when (e.align) {
                DAlign.LEFT -> r.left + e.pad * s
                DAlign.CENTER -> r.centerX() - lw / 2
                DAlign.RIGHT -> r.right - lw - e.pad * s
            }
            if (e.pill != null && i == 0) {
                val cy = base + (fm.ascent + fm.descent) / 2
                val hh = lp.textSize * 0.62f + e.pad * s * 0.55f
                val pr = RectF(x - e.pad * s, cy - hh, x + lw + e.pad * s, cy + hh)
                val rr = if (e.square) 0f else hh
                c.drawRoundRect(pr, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.pill, d, spec); this.alpha = (alpha * 255).toInt() })
            }
            lp.alpha = (alpha * Color.alpha(tint(if (lp === p2) e.color2 ?: e.color else e.color, d, spec))).toInt()
            if (e.gradient.size >= 2) {
                // Metallo: sfumatura sulla riga, con il riflesso chiaro a metà.
                lp.shader = LinearGradient(0f, base + fm.ascent, 0f, base + fm.descent, e.gradient.map { tint(it, d, spec) }.toIntArray(), null, Shader.TileMode.CLAMP)
            }
            c.drawText(line, x, base, lp)
            if (e.glow != null) c.drawText(line, x, base, lp)
            lp.shader = null
        }
        c.restore()
    }

    // ---------------- Forme, tratti, motivi ----------------

    private fun drawRect(c: Canvas, d: Design, e: DRect, r: RectF, s: Float, spec: CollageSpec) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint(e.color, d, spec)
            if (e.stroke > 0) { style = Paint.Style.STROKE; strokeWidth = e.stroke * s }
            // Con la sfumatura il colore pieno non conta, ma la sua trasparenza sì: deve essere opaco.
            if (e.grad.isNotEmpty()) color = Color.BLACK
            if (e.grad.isNotEmpty()) shader = if (e.radial) android.graphics.RadialGradient(r.centerX(), r.centerY(), max(1f, min(r.width(), r.height()) / 2),
                e.grad.map { tint(it.second, d, spec) }.toIntArray(), e.grad.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
                else LinearGradient(0f, r.top, 0f, r.bottom, e.grad.map { tint(it.second, d, spec) }.toIntArray(), e.grad.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
            if (e.shadow > 0) setShadowLayer(e.shadow * s, 0f, e.shadow * s * 0.45f, 0x40000000)
        }
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        when {
            e.poly.isNotEmpty() -> c.drawPath(Path().apply {
                e.poly.forEachIndexed { i, q -> val x = r.left + q.x * r.width(); val y = r.top + q.y * r.height(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
                close()
            }, p)
            e.circle -> c.drawOval(r, p)
            e.radius > 0 -> c.drawRoundRect(r, e.radius * s, e.radius * s, p)
            else -> c.drawRect(r, p)
        }
        c.restore()
    }

    private val pathCache = HashMap<String, Path>()

    /** Percorso SVG semplice: comandi M L H V C Q Z assoluti. */
    fun parse(d: String): Path = synchronized(pathCache) {
        pathCache.getOrPut(d) {
            val p = Path()
            val tok = Regex("[MLHVCQZmlhvcqz]|-?\\d*\\.?\\d+(?:e-?\\d+)?").findAll(d).map { it.value }.toList()
            var i = 0; var cmd = 'M'; var x = 0f; var y = 0f
            fun n(): Float = tok[i++].toFloat()
            while (i < tok.size) {
                val t = tok[i]
                if (t[0].isLetter()) { cmd = t[0].uppercaseChar(); i++; if (cmd == 'Z') { p.close(); continue } }
                when (cmd) {
                    'M' -> { x = n(); y = n(); p.moveTo(x, y); cmd = 'L' }
                    'L' -> { x = n(); y = n(); p.lineTo(x, y) }
                    'H' -> { x = n(); p.lineTo(x, y) }
                    'V' -> { y = n(); p.lineTo(x, y) }
                    'C' -> { val a = n(); val b = n(); val cc = n(); val dd = n(); x = n(); y = n(); p.cubicTo(a, b, cc, dd, x, y) }
                    'Q' -> { val a = n(); val b = n(); x = n(); y = n(); p.quadTo(a, b, x, y) }
                    else -> i++
                }
            }
            p
        }
    }

    private fun drawPath(c: Canvas, d: Design, e: DPath, r: RectF, s: Float, spec: CollageSpec, u: Float) {
        val src = parse(e.d)
        val m = android.graphics.Matrix().apply { setScale(r.width() / e.vw, r.height() / e.vh); postTranslate(r.left, r.top) }
        var path = Path(src).apply { transform(m) }
        if (u < 1f) {
            // Tratto che si disegna: si prende la parte iniziale di ogni pezzo.
            val part = Path()
            val pm = PathMeasure(path, false)
            do { pm.getSegment(0f, pm.length * u, part, true) } while (pm.nextContour())
            path = part
        }
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        if (e.fill != null && u >= 1f) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.fill, d, spec) })
        if (e.stroke != null) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = e.width * s; color = tint(e.stroke, d, spec)
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            if (e.dash.isNotEmpty()) pathEffect = DashPathEffect(e.dash.map { it * s }.toFloatArray(), 0f)
        })
        c.restore()
    }

    private fun drawPattern(c: Canvas, d: Design, e: DPattern, r: RectF, s: Float, spec: CollageSpec, sec: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.color, d, spec) }
        c.save()
        if (e.rot != 0f) c.rotate(e.rot, r.centerX(), r.centerY())
        when (e.kind) {
            DPatternKind.GRID -> {
                p.strokeWidth = max(1f, s)
                val step = e.step * s
                var x = r.left; while (x <= r.right) { c.drawLine(x, r.top, x, r.bottom, p); x += step }
                var y = r.top; while (y <= r.bottom) { c.drawLine(r.left, y, r.right, y, p); y += step }
            }
            DPatternKind.TOPO -> {
                // Curve di livello: anelli deformati attorno a tre cime, come una carta topografica.
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, 1.3f * s)
                val peaks = listOf(0.3f to 0.3f, 0.72f to 0.55f, 0.4f to 0.85f)
                peaks.forEachIndexed { pi, (fx, fy) ->
                    val cx = r.left + fx * r.width(); val cy = r.top + fy * r.height()
                    for (ring in 1..14) {
                        val base = ring * e.step * s
                        val path = Path()
                        for (i in 0..72) {
                            val a = (2 * Math.PI * i / 72).toFloat()
                            val wob = 1 + 0.18f * kotlin.math.sin(a * 3 + pi + ring * 0.3f) + 0.1f * kotlin.math.sin(a * 5 + pi * 2f)
                            val x = cx + kotlin.math.cos(a) * base * wob * 1.25f; val y = cy + kotlin.math.sin(a) * base * wob
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        p.alpha = if (ring % 5 == 0) 200 else 110
                        c.save(); c.clipRect(r); c.drawPath(path, p); c.restore()
                    }
                }
            }
            DPatternKind.SPEED -> {
                // Linee di velocità orizzontali, di lunghezze e spessori diversi.
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 46) {
                    val y = r.top + rnd(i, 91) * r.height()
                    val len = r.width() * (0.15f + 0.5f * rnd(i, 93))
                    // Nel video le linee corrono verso sinistra, ognuna alla sua velocità.
                    val run = (rnd(i, 97) * (r.width() + len) - sec * r.width() * (0.8f + 1.6f * rnd(i, 103))) % (r.width() + len)
                    val x = r.left - len + (if (run < 0) run + r.width() + len else run)
                    p.strokeWidth = (1f + 3.5f * rnd(i, 99)) * s
                    p.alpha = (60 + 150 * rnd(i, 101)).toInt()
                    c.drawLine(x, y, x + len, y, p)
                }
            }
            DPatternKind.CONFETTI -> {
                // Coriandoli: rettangolini e pallini nei colori del modello; nel video cadono girando.
                val cols = listOf(tint(DColor.ACC, d, spec), tint(DColor.ACC2, d, spec), 0xFFFFFFFF.toInt(), 0xFFFFD23F.toInt(), 0xFFFF7AB8.toInt())
                c.save(); c.clipRect(r)
                for (i in 0 until 110) {
                    val fall = sec * r.height() * (0.15f + 0.25f * rnd(i, 113))
                    val x = r.left + rnd(i, 107) * r.width() + kotlin.math.sin(sec * 2 + i) * 8 * s
                    val y0 = r.top + rnd(i, 109) * r.height() + fall
                    val y = r.top + ((y0 - r.top) % r.height())
                    p.color = cols[i % cols.size]
                    val sz = (5f + 7f * rnd(i, 127)) * s
                    if (i % 3 == 0) c.drawCircle(x, y, sz * 0.45f, p) else {
                        c.save(); c.rotate(rnd(i, 131) * 360 + sec * 180 * (if (i % 2 == 0) 1 else -1), x, y)
                        c.drawRect(x - sz * 0.3f, y - sz * 0.8f, x + sz * 0.3f, y + sz * 0.8f, p); c.restore()
                    }
                }
                c.restore()
            }
            DPatternKind.QR -> {
                // Riquadro a quadretti tipo codice QR (solo decorativo): tre "occhi" agli angoli.
                val n = 25; val cell = min(r.width(), r.height()) / n
                fun eye(ex: Int, ey: Int) {
                    c.drawRect(r.left + ex * cell, r.top + ey * cell, r.left + (ex + 7) * cell, r.top + (ey + 7) * cell, p)
                    val w = Paint(p).apply { color = 0xFFFFFFFF.toInt() }
                    c.drawRect(r.left + (ex + 1) * cell, r.top + (ey + 1) * cell, r.left + (ex + 6) * cell, r.top + (ey + 6) * cell, w)
                    c.drawRect(r.left + (ex + 2) * cell, r.top + (ey + 2) * cell, r.left + (ex + 5) * cell, r.top + (ey + 5) * cell, p)
                }
                for (j in 0 until n) for (i in 0 until n) {
                    val inEye = (i < 8 && j < 8) || (i >= n - 8 && j < 8) || (i < 8 && j >= n - 8)
                    if (!inEye && rnd(j * n + i, 211) > 0.52f) c.drawRect(r.left + i * cell, r.top + j * cell, r.left + (i + 1) * cell, r.top + (j + 1) * cell, p)
                }
                eye(0, 0); eye(n - 7, 0); eye(0, n - 7)
            }
            DPatternKind.DISCO -> {
                // Palla a specchi: tasselli grigi più chiari verso la luce, qualche riflesso bianco.
                val cx = r.centerX(); val cy = r.centerY(); val rad = min(r.width(), r.height()) / 2
                val clip = Path().apply { addCircle(cx, cy, rad, Path.Direction.CW) }
                c.save(); c.clipPath(clip)
                c.drawCircle(cx, cy, rad, Paint(p).apply { color = 0xFF2A2A33.toInt() })
                val n = 14; val t = rad * 2 / n
                for (j in 0 until n) for (i in 0 until n) {
                    val x = cx - rad + i * t; val y = cy - rad + j * t
                    val dx = (x + t / 2 - (cx - rad * 0.35f)) / rad; val dy = (y + t / 2 - (cy - rad * 0.35f)) / rad
                    val light = (1f - kotlin.math.sqrt(dx * dx + dy * dy) * 0.55f).coerceIn(0.15f, 1f) * (0.75f + 0.25f * rnd(j * n + i, 223))
                    val v = (70 + 185 * light).toInt().coerceIn(0, 255)
                    c.drawRect(x + t * 0.06f, y + t * 0.06f, x + t * 0.94f, y + t * 0.94f, Paint(p).apply { color = Color.rgb(v, v, (v + 12).coerceAtMost(255)) })
                }
                c.restore()
                // Lampi che girano.
                for (k in 0 until 4) {
                    val a = rnd(k, 229) * 6.28f + sec * 1.5f
                    val px = cx + kotlin.math.cos(a) * rad * 0.55f; val py = cy + kotlin.math.sin(a) * rad * 0.55f
                    c.drawCircle(px, py, rad * 0.06f, Paint(p).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(rad * 0.15f, 0f, 0f, 0xFFFFFFFF.toInt()) })
                }
            }
            DPatternKind.WALL -> {
                // Intonaco: macchie chiare e scure sfumate, grana grossa, qualche graffio e i bordi più scuri.
                c.save(); c.clipRect(r)
                val big = max(r.width(), r.height())
                for (i in 0 until 70) {
                    val x = r.left + rnd(i, 61) * r.width(); val y = r.top + rnd(i, 67) * r.height()
                    val rad = big * (0.04f + 0.2f * rnd(i, 71))
                    val col = if (i % 2 == 0) Color.argb((18 + 30 * rnd(i, 73)).toInt(), 255, 255, 255) else Color.argb((20 + 34 * rnd(i, 79)).toInt(), 0, 0, 0)
                    c.drawCircle(x, y, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, rad, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP) })
                }
                CollageRenderer.grain(c, (r.right + 1).toInt(), (r.bottom + 1).toInt(), 5, 70)
                CollageRenderer.grain(c, (r.right + 1).toInt(), (r.bottom + 1).toInt(), 11, 40)
                val sc = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = max(1f, 0.9f * s); style = Paint.Style.STROKE }
                for (i in 0 until 60) {
                    val x = r.left + rnd(i, 83) * r.width(); val y = r.top + rnd(i, 89) * r.height()
                    val a = rnd(i, 97) * 6.28f; val len = (8f + 40f * rnd(i, 101)) * s
                    sc.color = if (i % 3 == 0) 0x30FFFFFF else 0x26000000
                    c.drawLine(x, y, x + kotlin.math.cos(a) * len, y + kotlin.math.sin(a) * len, sc)
                }
                c.drawRect(r, Paint().apply { shader = RadialGradient(r.centerX(), r.centerY(), big * 0.75f, intArrayOf(0, 0, 0x66000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) })
                c.restore()
            }
            DPatternKind.PAPER -> {
                // Grana della carta e qualche fibra.
                c.save(); c.clipRect(r)
                CollageRenderer.grain(c, (r.right + 1).toInt(), (r.bottom + 1).toInt(), 3, 30)
                val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(e.color, d, spec); strokeWidth = max(1f, 0.8f * s); style = Paint.Style.STROKE }
                for (i in 0 until 90) {
                    val x = r.left + rnd(i, 41) * r.width(); val y = r.top + rnd(i, 43) * r.height()
                    val a = rnd(i, 47) * 6.28f; val len = (6f + 14f * rnd(i, 53)) * s
                    c.drawLine(x, y, x + kotlin.math.cos(a) * len, y + kotlin.math.sin(a) * len, f)
                }
                c.restore()
            }
            DPatternKind.LINES -> {
                // Quaderno: righe e margine rosso.
                p.strokeWidth = max(1f, 1.2f * s)
                var y = r.top + e.step * s * 2.5f
                while (y < r.bottom - 4 * s) { c.drawLine(r.left, y, r.right, y, p); y += e.step * s }
                c.drawLine(r.left + 64 * s, r.top, r.left + 64 * s, r.bottom, Paint(p).apply { color = 0x88E63946.toInt() })
            }
            DPatternKind.TEXTLINES -> {
                // Colonne di testo di giornale (righe grigie, l'ultima di ogni paragrafo più corta).
                val colGap = 18 * s; val cols = 3
                val cw = (r.width() - colGap * (cols - 1)) / cols
                val lh = e.step * s; val bh = lh * 0.42f
                for (ci in 0 until cols) {
                    val x0 = r.left + ci * (cw + colGap)
                    var y = r.top; var line = 0; var para = 6 + (rnd(ci, 61) * 6).toInt()
                    while (y + bh <= r.bottom) {
                        val last = line == para
                        val wl = if (last) cw * (0.3f + 0.5f * rnd(ci * 100 + line, 67)) else cw * (0.94f + 0.06f * rnd(ci * 100 + line, 71))
                        c.drawRect(x0, y, x0 + wl, y + bh, p)
                        y += lh; line++
                        if (last) { y += lh * 0.6f; line = 0; para = 5 + (rnd(ci * 100 + line + y.toInt(), 73) * 7).toInt() }
                    }
                }
            }
            DPatternKind.SPROCKETS -> {
                // Fori della pellicola sopra e sotto.
                val hw = 10 * s; val hh = 12 * s; val step = e.step * s
                var x = r.left + 10 * s
                while (x + hw <= r.right - 6 * s) {
                    c.drawRoundRect(RectF(x, r.top + 9 * s, x + hw, r.top + 9 * s + hh), 2 * s, 2 * s, p)
                    c.drawRoundRect(RectF(x, r.bottom - 9 * s - hh, x + hw, r.bottom - 9 * s), 2 * s, 2 * s, p)
                    x += step
                }
            }
        }
        c.restore()
    }

    private fun drawBarcode(c: Canvas, d: Design, e: DBarcode, r: RectF, spec: CollageSpec) {
        val bars = intArrayOf(3, 1, 2, 1, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 3, 1, 1, 2, 1, 3, 2, 1, 1, 2, 1, 3, 1, 2)
        val total = bars.indices.sumOf { bars[it] + (it % 3) + 1 }.toFloat()
        val unit = r.width() / total
        val p = Paint().apply { color = tint(e.color, d, spec) }
        var x = r.left
        bars.forEachIndexed { i, b -> c.drawRect(x, r.top, x + b * unit, r.bottom, p); x += (b + (i % 3) + 1) * unit }
    }

    // ---------------- Palette dalle foto ----------------

    private val paletteCache = java.util.WeakHashMap<Bitmap, IntArray>()

    /** I colori principali delle foto (dal più chiaro al più scuro). */
    fun palette(bitmaps: List<Bitmap>, k: Int): IntArray {
        val key = bitmaps.firstOrNull() ?: return IntArray(0)
        synchronized(paletteCache) { paletteCache[key]?.takeIf { it.size == k }?.let { return it } }
        val px = ArrayList<Int>()
        for (b in bitmaps.take(9)) {
            if (b.isRecycled) continue
            val sm = Bitmap.createScaledBitmap(b, 24, 24, true)
            val a = IntArray(24 * 24); sm.getPixels(a, 0, 24, 0, 0, 24, 24)
            if (sm !== b) sm.recycle()
            a.forEach { px += it }
        }
        if (px.isEmpty()) return IntArray(0)
        fun lum(c: Int) = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        val sorted = px.sortedBy { lum(it) }
        // k-medie partendo da colori presi a quote diverse di luminosità.
        val cr = FloatArray(k); val cg = FloatArray(k); val cb = FloatArray(k)
        for (j in 0 until k) { val c0 = sorted[(sorted.size - 1) * (2 * j + 1) / (2 * k)]; cr[j] = Color.red(c0).toFloat(); cg[j] = Color.green(c0).toFloat(); cb[j] = Color.blue(c0).toFloat() }
        val n = IntArray(k)
        repeat(8) {
            val sr = FloatArray(k); val sg = FloatArray(k); val sb = FloatArray(k); n.fill(0)
            for (c0 in px) {
                val r0 = Color.red(c0); val g0 = Color.green(c0); val b0 = Color.blue(c0)
                var best = 0; var bd = Float.MAX_VALUE
                for (j in 0 until k) { val dd = (cr[j] - r0) * (cr[j] - r0) + (cg[j] - g0) * (cg[j] - g0) + (cb[j] - b0) * (cb[j] - b0); if (dd < bd) { bd = dd; best = j } }
                sr[best] += r0.toFloat(); sg[best] += g0.toFloat(); sb[best] += b0.toFloat(); n[best]++
            }
            for (j in 0 until k) if (n[j] > 0) { cr[j] = sr[j] / n[j]; cg[j] = sg[j] / n[j]; cb[j] = sb[j] / n[j] }
        }
        val out = (0 until k).map { Color.rgb(cr[it].toInt(), cg[it].toInt(), cb[it].toInt()) }.sortedByDescending { lum(it) }.toIntArray()
        synchronized(paletteCache) { paletteCache[key] = out }
        return out
    }

    private fun drawPalette(c: Canvas, d: Design, e: DPalette, r: RectF, s: Float, spec: CollageSpec, frames: CollageRenderer.Frames, tUs: Long, u: Float) {
        val bmps = spec.items.indices.mapNotNull { frames.get(it, if (spec.items[it].video) 0 else tUs) }
        val cols = palette(bmps, e.count)
        if (cols.isEmpty()) return
        val gap = 8 * s
        val cw = (r.width() - gap * (e.count - 1)) / e.count
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = DesignFonts.get(DFont.MONO); textSize = 10 * s; color = tint(e.color, d, spec) }
        val lh = 13 * s
        cols.forEachIndexed { i, col ->
            val uu = ((u * cols.size) - i).coerceIn(0f, 1f)
            if (uu <= 0f) return@forEachIndexed
            val x = r.left + i * (cw + gap)
            val bottom = r.bottom - 2 * lh - 6 * s
            c.drawRect(x, bottom - (bottom - r.top) * CollageAnim.outCubic(uu), x + cw, bottom, Paint().apply { color = col })
            label.alpha = (uu * 255).toInt()
            c.drawText(ColorNames.name(col), x, bottom + 6 * s + lh * 0.8f, label)
            c.drawText(ColorNames.hex(col), x, bottom + 6 * s + lh * 1.8f, label)
        }
    }
}
