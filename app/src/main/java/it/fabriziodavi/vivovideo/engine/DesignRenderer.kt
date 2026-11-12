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
import android.graphics.PorterDuffXfermode
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
import it.fabriziodavi.vivovideo.model.DShape
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
        val late = d.els.count { it !is DPhoto && it !is DPhotoWord }
        var pi = 0; var li = 0
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
                is DPattern -> drawPattern(c, d, e, r, s, spec)
                is DBarcode -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) withPop(c, r, u) { drawBarcode(c, d, e, r, spec) } }
                is DPalette -> { val u = lateU(li++, late, tUs, dur, still); if (u > 0f) drawPalette(c, d, e, r, s, spec, frames, tUs, u) }
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
        if (!subjectOnly && (e.border > 0 || e.shadow > 0)) {
            val b = e.border * s
            val card = RectF(r.left - b, r.top - b, r.right + b, r.bottom + b + e.bottomPad * s)
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (e.border > 0) tint(e.borderColor, d, spec) else Color.BLACK
                if (e.shadow > 0) setShadowLayer(e.shadow * s, 0f, e.shadow * s * 0.45f, 0x4D000000)
            }
            val outer = when {
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
            } else c.drawBitmap(src, null, dst, filterPaint(e.filter, d, spec))
        }
        c.restore()
        if (!subjectOnly && e.outline > 0) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = e.outline * s; color = DColor.BLACK.toInt() })
        if (selected && !subjectOnly) c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(3f, 4f * s); color = 0xFFFFC83D.toInt() })
        if (layer >= 0) c.restoreToCount(layer)
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
            color = tint(e.color, d, spec)
            if (e.stroke > 0) { style = Paint.Style.STROKE; strokeWidth = e.stroke * s; strokeJoin = Paint.Join.ROUND }
            if (e.shadow) setShadowLayer(e.size * s * 0.08f, 0f, e.size * s * 0.03f, 0x73000000)
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
            c.drawText(line, x, base, lp)
        }
        c.restore()
    }

    // ---------------- Forme, tratti, motivi ----------------

    private fun drawRect(c: Canvas, d: Design, e: DRect, r: RectF, s: Float, spec: CollageSpec) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint(e.color, d, spec)
            if (e.stroke > 0) { style = Paint.Style.STROKE; strokeWidth = e.stroke * s }
            if (e.grad.isNotEmpty()) shader = LinearGradient(0f, r.top, 0f, r.bottom, e.grad.map { tint(it.second, d, spec) }.toIntArray(), e.grad.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
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

    private fun drawPattern(c: Canvas, d: Design, e: DPattern, r: RectF, s: Float, spec: CollageSpec) {
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
