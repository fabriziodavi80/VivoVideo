package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.util.LruCache
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import it.fabriziodavi.vivovideo.R
import it.fabriziodavi.vivovideo.model.TextAnim
import it.fabriziodavi.vivovideo.model.TextFont
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
import it.fabriziodavi.vivovideo.model.Subtitles
import kotlin.math.ceil
import kotlin.math.max

/** Disegna i testi in immagini (una per testo), con cache condivisa fra anteprima ed export. */
object TextRenderer {
    private val faces = HashMap<TextFont, Typeface>()
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun typeface(context: Context, f: TextFont): Typeface = synchronized(faces) {
        faces.getOrPut(f) {
            fun res(id: Int) = runCatching { context.resources.getFont(id) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            when (f) {
                TextFont.MODERNO -> res(R.font.plex_semibold)
                TextFont.TITOLO -> res(R.font.bebas)
                TextFont.IMPATTO -> res(R.font.anton)
                TextFont.POSTER -> res(R.font.archivo)
                TextFont.ELEGANTE -> res(R.font.dmserif)
                TextFont.CORSIVO -> res(R.font.pacifico)
                TextFont.PENNARELLO -> res(R.font.marker)
                TextFont.MACCHINA -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
        }
    }

    private val fileFaces = HashMap<String, Typeface>()

    /** Il font del testo: quello aggiunto dall'utente se c'è (e si legge), altrimenti uno di quelli dell'app. */
    fun typeface(context: Context, item: TextItem): Typeface {
        val path = item.fontFile ?: return typeface(context, item.font)
        return synchronized(fileFaces) {
            fileFaces.getOrPut(path) { runCatching { Typeface.createFromFile(path) }.getOrNull() ?: typeface(context, item.font) }
        }
    }

    private fun blendToWhite(c: Int, k: Float): Int = Color.argb(
        255,
        (Color.red(c) + (255 - Color.red(c)) * k).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * k).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * k).toInt(),
    )

    fun displayText(item: TextItem): String = if (item.upper) item.text.uppercase() else item.text

    private fun isLight(c: Int): Boolean =
        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) > 140

    private class Prepared(val layout: StaticLayout, val width: Int, val pad: Int, val paint: TextPaint)

    private fun prepare(context: Context, item: TextItem, frameW: Int, frameH: Int, visible: Int, highlight: IntRange? = null): Prepared {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context, item)
            textSize = (item.size * frameH).coerceAtLeast(4f)
            color = item.color
            letterSpacing = item.spacing
        }
        val text = displayText(item).ifEmpty { " " }
        // Larghezza massima: 90% del quadro; poi si stringe alla riga più lunga.
        val maxW = (frameW * 0.9f).toInt().coerceAtLeast(16)
        // Titoli molto grandi (o video verticali): se una parola non entra in larghezza si rimpicciolisce il carattere,
        // invece di spezzarla a metà.
        val widest = text.split(' ', '\n').maxOfOrNull { paint.measureText(it) } ?: 0f
        if (widest > maxW) paint.textSize = (paint.textSize * maxW / widest * 0.98f).coerceAtLeast(4f)
        val content: CharSequence = if (visible >= text.length && highlight == null) text else SpannableString(text).apply {
            if (visible < text.length) setSpan(ForegroundColorSpan(Color.TRANSPARENT), visible.coerceAtLeast(0), text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // Karaoke: la parola detta in questo momento ha il colore di risalto.
            if (highlight != null && highlight.first >= 0 && highlight.last < text.length) {
                setSpan(ForegroundColorSpan(item.highlight), highlight.first, highlight.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        var layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        var w = 1f
        for (i in 0 until layout.lineCount) w = max(w, layout.getLineWidth(i))
        val width = ceil(w).toInt().coerceAtMost(maxW)
        layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        val pad = (paint.textSize * when (item.look) { TextLook.RIQUADRO -> 0.45f; TextLook.NEON -> 0.6f; else -> 0.25f }).toInt() + 2
        return Prepared(layout, width, pad, paint)
    }

    /** Dimensioni (in pixel del quadro) dell'immagine del testo: serve per il riquadro di modifica. */
    fun measure(context: Context, item: TextItem, frameW: Int, frameH: Int): Pair<Int, Int> {
        val p = prepare(context, item, frameW, frameH, Int.MAX_VALUE)
        return (p.width + 2 * p.pad) to (p.layout.height + 2 * p.pad)
    }

    fun render(context: Context, item: TextItem, frameW: Int, frameH: Int, visible: Int = Int.MAX_VALUE, highlight: IntRange? = null): Bitmap {
        val v = visible.coerceAtMost(displayText(item).length)
        val key = "${item.text.hashCode()}|${item.font}|${item.fontFile}|${item.look}|${item.color}|${item.gradientTo}|${item.spacing}|${item.upper}|${item.size}|$frameW|$frameH|$v|$highlight|${item.highlight}"
        cache.get(key)?.let { return it }
        val p = prepare(context, item, frameW, frameH, v, highlight)
        val bw = p.width + 2 * p.pad
        val bh = p.layout.height + 2 * p.pad
        val bmp = Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val light = isLight(item.color)
        when (item.look) {
            TextLook.RIQUADRO -> {
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (light) 0x99000000.toInt() else 0xE6FFFFFF.toInt() }
                val r = p.paint.textSize * 0.3f
                c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), r, r, bg)
            }
            TextLook.OMBRA -> p.paint.setShadowLayer(p.paint.textSize * 0.1f, 0f, p.paint.textSize * 0.04f, 0xB0000000.toInt())
            else -> {}
        }
        c.translate(p.pad.toFloat(), p.pad.toFloat())
        if (item.look == TextLook.CONTORNO) {
            // Prima il contorno (spesso, colore opposto), poi il riempimento sopra.
            val fill = p.paint.color
            p.paint.style = Paint.Style.STROKE
            p.paint.strokeWidth = p.paint.textSize * 0.14f
            p.paint.strokeJoin = Paint.Join.ROUND
            p.paint.color = if (light) Color.BLACK else Color.WHITE
            p.layout.draw(c)
            p.paint.style = Paint.Style.FILL
            p.paint.color = fill
        }
        // Sfumatura: il riempimento passa dal primo al secondo colore dall'alto in basso.
        val gradient = item.gradientTo?.let {
            android.graphics.LinearGradient(0f, 0f, 0f, p.layout.height.toFloat(), item.color, it, android.graphics.Shader.TileMode.CLAMP)
        }
        if (item.look == TextLook.NEON) {
            // Alone luminoso del colore scelto (due passate) e anima chiara al centro.
            val glow = p.paint.color
            p.paint.setShadowLayer(p.paint.textSize * 0.45f, 0f, 0f, glow)
            p.layout.draw(c)
            p.paint.setShadowLayer(p.paint.textSize * 0.18f, 0f, 0f, glow)
            p.layout.draw(c)
            p.paint.color = blendToWhite(glow, 0.75f)
            p.paint.setShadowLayer(p.paint.textSize * 0.06f, 0f, 0f, glow)
        }
        p.paint.shader = gradient
        p.layout.draw(c)
        p.paint.shader = null
        cache.put(key, bmp)
        return bmp
    }

    private fun baseKey(item: TextItem, frameW: Int, frameH: Int) =
        "${item.text.hashCode()}|${item.font}|${item.fontFile}|${item.look}|${item.color}|${item.gradientTo}|${item.spacing}|${item.upper}|${item.size}|$frameW|$frameH"

    /** Fotogrammi animati: in cache solo se piccoli (in 4K sarebbero troppi). */
    private fun keep(key: String, b: Bitmap): Bitmap { if (b.byteCount < 6 * 1024 * 1024) cache.put(key, b); return b }

    private fun withAlpha(col: Int, a: Float) =
        Color.argb((Color.alpha(col) * a.coerceIn(0f, 1f)).toInt(), Color.red(col), Color.green(col), Color.blue(col))

    /** Una lettera con lo stesso aspetto del testo intero (contorno, ombra, neon, sfumatura). */
    private fun drawGlyph(c: Canvas, base: TextPaint, item: TextItem, light: Boolean, s: String, x: Float, y: Float, a: Float, gradient: android.graphics.Shader?) {
        val paint = TextPaint(base).apply { letterSpacing = 0f; clearShadowLayer() }
        val size = paint.textSize
        when (item.look) {
            TextLook.OMBRA -> paint.setShadowLayer(size * 0.1f, 0f, size * 0.04f, withAlpha(0xB0000000.toInt(), a))
            TextLook.CONTORNO -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * 0.14f
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = withAlpha(if (light) Color.BLACK else Color.WHITE, a)
                c.drawText(s, x, y, paint)
                paint.style = Paint.Style.FILL
            }
            else -> {}
        }
        paint.color = withAlpha(item.color, a)
        if (item.look == TextLook.NEON) {
            paint.setShadowLayer(size * 0.45f, 0f, 0f, withAlpha(item.color, a))
            c.drawText(s, x, y, paint)
            paint.setShadowLayer(size * 0.18f, 0f, 0f, withAlpha(item.color, a))
            c.drawText(s, x, y, paint)
            paint.color = withAlpha(blendToWhite(item.color, 0.75f), a)
            paint.setShadowLayer(size * 0.06f, 0f, 0f, withAlpha(item.color, a))
        }
        paint.shader = gradient
        c.drawText(s, x, y, paint)
    }

    /** Una lettera già disegnata con il suo aspetto, e dove va (coordinate del testo). */
    private class Glyph(val bmp: Bitmap, val left: Float, val top: Float, val cx: Float, val cy: Float)

    private val glyphCache = LruCache<String, List<Glyph>>(6)

    /**
     * Le lettere del testo disegnate una volta sola (contorno, ombra, neon e sfumatura compresi):
     * i fotogrammi animati poi le spostano soltanto, così restano leggeri anche in anteprima.
     */
    private fun glyphs(context: Context, item: TextItem, frameW: Int, frameH: Int): List<Glyph> {
        val key = baseKey(item, frameW, frameH)
        glyphCache.get(key)?.let { return it }
        val p = prepare(context, item, frameW, frameH, Int.MAX_VALUE)
        val ts = p.paint.textSize
        val light = isLight(item.color)
        val pad = (ts * if (item.look == TextLook.NEON) 0.6f else 0.25f).toInt() + 2
        val text = p.layout.text.toString()
        val out = ArrayList<Glyph>()
        val bounds = android.graphics.Rect()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            if (!Character.isWhitespace(cp)) {
                val s1 = text.substring(i, i + len)
                val line = p.layout.getLineForOffset(i)
                val x = p.layout.getPrimaryHorizontal(i) + item.spacing * ts / 2f
                val base = p.layout.getLineBaseline(line).toFloat()
                val gp = TextPaint(p.paint).apply { letterSpacing = 0f }
                gp.getTextBounds(s1, 0, s1.length, bounds)
                if (bounds.isEmpty) bounds.set(0, (-ts * 0.7f).toInt(), (ts * 0.5f).toInt(), 0)
                val bw = bounds.width() + 2 * pad
                val bh = bounds.height() + 2 * pad
                val bmp = Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.translate((pad - bounds.left).toFloat(), (pad - bounds.top).toFloat())
                // La sfumatura resta quella dell'intero testo (dall'alto in basso del blocco).
                val gradient = item.gradientTo?.let {
                    android.graphics.LinearGradient(0f, -base, 0f, p.layout.height - base, item.color, it, android.graphics.Shader.TileMode.CLAMP)
                }
                drawGlyph(c, p.paint, item, light, s1, 0f, 0f, 1f, gradient)
                val left = x + bounds.left - pad
                val top = base + bounds.top - pad
                out += Glyph(bmp, left, top, left + bw / 2f, top + bh / 2f)
            }
            i += len
        }
        glyphCache.put(key, out)
        return out
    }

    /**
     * Testo con le lettere animate una per una ([progress] 0→1 lungo l'entrata o l'uscita).
     * L'immagine ha margini più larghi, così le lettere possono uscire dalla riga mentre si muovono.
     */
    fun renderLetters(
        context: Context, item: TextItem, frameW: Int, frameH: Int, anim: TextAnim, entering: Boolean, progress: Float,
        reuse: Bitmap? = null,
    ): Bitmap {
        val key = baseKey(item, frameW, frameH) + "|L|$anim|$entering|${(progress * 1000).toInt()}"
        if (reuse == null) cache.get(key)?.let { return it }
        val p = prepare(context, item, frameW, frameH, Int.MAX_VALUE)
        val ts = p.paint.textSize
        // Spazio intorno per le lettere che volano lontano (esplosione, vortice).
        val wide = anim == TextAnim.SCATTER || anim == TextAnim.SPIN
        val exX = (ts * if (wide) 2.2f else 0.4f).toInt()
        val exY = (ts * if (wide) 1.8f else 1.1f).toInt()
        val bw = p.width + 2 * p.pad + 2 * exX
        val bh = p.layout.height + 2 * p.pad + 2 * exY
        // Fotogrammi in sequenza: si riusa l'immagine passata (stessa misura) invece di crearne una nuova.
        val bmp = reuse?.takeIf { it.isMutable && it.width == bw.coerceAtLeast(2) && it.height == bh.coerceAtLeast(2) }?.apply { eraseColor(0) }
            ?: Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val light = isLight(item.color)
        if (item.look == TextLook.RIQUADRO) {
            val boxA = if (entering) (progress * 2.5f).coerceIn(0f, 1f) else 1f - progress
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(if (light) 0x99000000.toInt() else 0xE6FFFFFF.toInt(), boxA) }
            val r = ts * 0.3f
            c.drawRoundRect(RectF(exX.toFloat(), exY.toFloat(), (bw - exX).toFloat(), (bh - exY).toFloat()), r, r, bg)
        }
        c.translate((p.pad + exX).toFloat(), (p.pad + exY).toFloat())
        val gs = glyphs(context, item, frameW, frameH)
        val n = gs.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        gs.forEachIndexed { k, g ->
            val f = letterFx(anim, entering, progress, k, n)
            if (f.alpha > 0.004f) {
                paint.alpha = (f.alpha.coerceIn(0f, 1f) * 255).toInt()
                c.save()
                c.translate(f.dx * ts, f.dy * ts)
                c.rotate(f.rot, g.cx, g.cy)
                c.scale(f.scale * f.sx, f.scale, g.cx, g.cy)
                c.drawBitmap(g.bmp, g.left, g.top, paint)
                c.restore()
            }
        }
        return if (reuse != null) bmp else keep(key, bmp)
    }

    /** Messa a fuoco: il testo sfocato (rimpicciolito e ingrandito con filtro), [amount] 0…1. */
    fun renderBlur(context: Context, item: TextItem, frameW: Int, frameH: Int, amount: Float): Bitmap {
        val key = baseKey(item, frameW, frameH) + "|B|${(amount * 100).toInt()}"
        cache.get(key)?.let { return it }
        val base = render(context, item, frameW, frameH)
        val f = 1f + amount * 14f
        val sw = (base.width / f).toInt().coerceAtLeast(2); val sh = (base.height / f).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(base, sw, sh, true)
        // Due passaggi di riduzione danno una sfocatura più morbida.
        val smaller = Bitmap.createScaledBitmap(small, (sw / 2).coerceAtLeast(2), (sh / 2).coerceAtLeast(2), true)
        val mid = Bitmap.createScaledBitmap(smaller, sw, sh, true)
        val out = Bitmap.createScaledBitmap(mid, base.width, base.height, true)
        if (small !== base) small.recycle(); smaller.recycle(); if (mid !== out) mid.recycle()
        return keep(key, out)
    }

    /** Glitch: il testo a strisce spostate, con i bordi rossi e azzurri sfasati ([amount] 0…1). */
    fun renderGlitch(context: Context, item: TextItem, frameW: Int, frameH: Int, amount: Float, seed: Long): Bitmap {
        val key = baseKey(item, frameW, frameH) + "|G|$seed|${(amount * 20).toInt()}"
        cache.get(key)?.let { return it }
        val base = render(context, item, frameW, frameH)
        val ex = (base.height * 0.3f).toInt().coerceAtLeast(4)
        val out = Bitmap.createBitmap(base.width + 2 * ex, base.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val rnd = java.util.Random(seed * 31L + 7L)
        val red = Paint().apply { colorFilter = android.graphics.PorterDuffColorFilter(0xFFFF2B5E.toInt(), android.graphics.PorterDuff.Mode.SRC_IN) }
        val cyan = Paint().apply {
            colorFilter = android.graphics.PorterDuffColorFilter(0xFF1EF2FF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
        }
        val norm = Paint()
        val bands = 7
        val off = ex * 0.3f * amount + 2f
        for (b in 0 until bands) {
            val y0 = base.height * b / bands
            val y1 = base.height * (b + 1) / bands
            val dx = if (rnd.nextFloat() < 0.15f + 0.5f * amount) (rnd.nextFloat() - 0.5f) * 2f * ex * amount else 0f
            val src = android.graphics.Rect(0, y0, base.width, y1)
            fun dst(o: Float) = RectF(ex + dx + o, y0.toFloat(), ex + dx + o + base.width, y1.toFloat())
            c.drawBitmap(base, src, dst(-off), red)
            c.drawBitmap(base, src, dst(off), cyan)
            c.drawBitmap(base, src, dst(0f), norm)
        }
        return keep(key, out)
    }

    /** Luccichio: una striscia di luce che attraversa il testo ([phase] 0→1). */
    fun renderShine(context: Context, item: TextItem, frameW: Int, frameH: Int, phase: Float): Bitmap {
        val key = baseKey(item, frameW, frameH) + "|S|${(phase * 100).toInt()}"
        cache.get(key)?.let { return it }
        val out = render(context, item, frameW, frameH).copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val band = out.width * 0.3f + out.height
        val x0 = -band + phase * (out.width + band)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                x0, 0f, x0 + band, out.height * 0.6f,
                intArrayOf(0x00FFFFFF, 0xE6FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return keep(key, out)
    }
}

/** Una lettera durante l'animazione: trasparenza, spostamento verticale (in altezze del testo), scala, rotazione (gradi). */
internal data class LetterFx(val alpha: Float, val dy: Float, val scale: Float, val rot: Float, val dx: Float = 0f, val sx: Float = 1f)

private fun bounceOut(x: Float): Float {
    val n = 7.5625f; val d = 2.75f
    return when {
        x < 1f / d -> n * x * x
        x < 2f / d -> { val t = x - 1.5f / d; n * t * t + 0.75f }
        x < 2.5f / d -> { val t = x - 2.25f / d; n * t * t + 0.9375f }
        else -> { val t = x - 2.625f / d; n * t * t + 0.984375f }
    }
}

/**
 * Lettera [i] di [n] con [progress] 0→1 dell'entrata ([entering]) o dell'uscita: le lettere partono a turno,
 * da sinistra a destra, ognuna con una sua finestra di tempo.
 */
internal fun letterFx(anim: TextAnim, entering: Boolean, progress: Float, i: Int, n: Int): LetterFx {
    val win = if (n <= 1) 1f else 0.45f
    val start = if (n <= 1) 0f else (1f - win) * i / (n - 1)
    val q = ((progress - start) / win).coerceIn(0f, 1f)
    fun ease(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)
    val pi = Math.PI.toFloat()
    // Valori casuali stabili per lettera (direzioni dell'esplosione).
    fun rnd(k: Int): Float { val x = kotlin.math.sin(i * 12.9898f + k * 78.233f) * 43758.547f; return x - kotlin.math.floor(x) - 0.5f }
    fun elastic(x: Float): Float = if (x <= 0f) 0f else if (x >= 1f) 1f else
        (Math.pow(2.0, -10.0 * x) * kotlin.math.sin((x * 10f - 0.75f) * (2f * pi / 3f)) + 1.0).toFloat()
    when (anim) {
        TextAnim.BOUNCE -> return if (entering) LetterFx((q * 4f).coerceAtMost(1f), -0.25f * (1f - ease(q)), elastic(q).coerceAtLeast(0f), 0f)
            else LetterFx(1f - q, 0f, if (q < 0.3f) 1f + 0.2f * (q / 0.3f) else 1.2f * (1f - (q - 0.3f) / 0.7f), 0f)
        TextAnim.FLIP -> return if (entering) LetterFx(if (q > 0f) (q * 3f).coerceAtMost(1f) else 0f, 0f, 1f, 0f, sx = kotlin.math.cos((1f - ease(q)) * pi / 2f))
            else LetterFx(1f - q * q, 0f, 1f, 0f, sx = kotlin.math.cos(ease(q) * pi / 2f))
        TextAnim.SPIN -> return if (entering) LetterFx(ease(q), -0.3f * (1f - ease(q)), 0.3f + 0.7f * ease(q), (if (i % 2 == 0) -180f else 180f) * (1f - ease(q)))
            else LetterFx(1f - ease(q), 0f, 1f - 0.7f * ease(q), 180f * ease(q))
        TextAnim.SCATTER -> {
            val k = if (entering) 1f - ease(q) else ease(q)
            return LetterFx(if (entering) ease(q) else 1f - q, rnd(2) * 2.4f * k, 1f + 0.6f * k, rnd(3) * 260f * k, dx = rnd(1) * 3.2f * k)
        }
        else -> {}
    }
    return if (entering) when (anim) {
        TextAnim.DROP -> LetterFx((q * 4f).coerceAtMost(1f), -1f * (1f - bounceOut(q)), 1f, 0f)
        TextAnim.RISE -> LetterFx(ease(q), 0.6f * (1f - ease(q)), 1f + 0.5f * (1f - ease(q)), 0f)
        TextAnim.WAVE -> LetterFx(if (q > 0f) (q * 5f).coerceAtMost(1f) else 0f, -0.55f * kotlin.math.sin(q * pi), 1f + 0.35f * kotlin.math.sin(q * pi), 0f)
        else -> LetterFx(1f, 0f, 1f, 0f)
    } else when (anim) {
        TextAnim.DROP -> LetterFx(1f - q, 1.0f * q * q, 1f, (if (i % 2 == 0) 25f else -25f) * q)
        TextAnim.RISE -> LetterFx(1f - ease(q), -0.6f * ease(q), 1f + 0.5f * ease(q), 0f)
        TextAnim.WAVE -> LetterFx(1f - q, -0.5f * kotlin.math.sin(q * pi * 0.5f), 1f + 0.3f * kotlin.math.sin(q * pi), 0f)
        else -> LetterFx(1f, 0f, 1f, 0f)
    }
}

/** Durata dell'entrata lettera per lettera: più lettere, più tempo (al massimo 1,8 s o metà del testo). */
internal fun lettersInUs(item: TextItem): Long {
    val n = TextRenderer.displayText(item).count { !it.isWhitespace() }
    return minOf(maxOf(n * 55_000L, 450_000L) + 350_000L, 1_800_000L, item.durationUs / 2).coerceAtLeast(1)
}
internal fun lettersOutUs(item: TextItem): Long = minOf(600_000L, item.durationUs / 2).coerceAtLeast(1)
internal const val GLITCH_US = 600_000L

/** Fase del luccichio (0→1) se a [local] µs una striscia di luce sta passando, altrimenti null. */
internal fun shinePhase(local: Long): Float? {
    val t = local - 300_000L
    if (t < 0) return null
    val ph = t % 3_000_000L
    return if (ph < 900_000L) ph / 900_000f else null
}

private fun quant(x: Float, steps: Int = 60): Float = (x.coerceIn(0f, 1f) * steps).toInt() / steps.toFloat()

private const val IN_US = 450_000L
private const val OUT_US = 350_000L

/** Effetto delle animazioni di entrata/uscita a [local] µs dall'inizio: trasparenza, scala, spostamento verticale. */
internal data class AnimFactors(val alpha: Float, val scale: Float, val dy: Float)

internal fun animFactors(animIn: TextAnim, animOut: TextAnim, local: Long, duration: Long): AnimFactors {
    if (local < 0 || local >= duration) return AnimFactors(0f, 1f, 0f)
    var alpha = 1f
    var scale = 1f
    var dy = 0f
    fun ease(x: Float) = x.coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) * (1 - it) }
    val pin = local.toFloat() / minOf(IN_US, duration / 2).coerceAtLeast(1)
    val pout = (duration - local).toFloat() / minOf(OUT_US, duration / 2).coerceAtLeast(1)
    when (animIn) {
        TextAnim.FADE -> alpha *= ease(pin)
        TextAnim.POP -> if (pin < 1) {
            // Rimbalzo: cresce oltre la misura e torna indietro.
            val e = pin.coerceIn(0f, 1f)
            scale *= if (e < 0.7f) 0.5f + 0.6f * (e / 0.7f) else 1.1f - 0.1f * ((e - 0.7f) / 0.3f)
            alpha *= ease(pin * 2)
        }
        TextAnim.SLIDE -> { dy += 0.08f * (1 - ease(pin)); alpha *= ease(pin) }
        TextAnim.ZOOM -> { scale *= 1.6f - 0.6f * ease(pin); alpha *= ease(pin) }
        TextAnim.GLITCH -> alpha *= ease(pin * 1.5f)
        TextAnim.SHINE -> alpha *= ease(pin)
        TextAnim.BLUR -> { scale *= 1.18f - 0.18f * ease(pin * 0.75f); alpha *= ease(pin * 1.6f) }
        TextAnim.FLICKER -> alpha *= flicker(local)
        else -> {}
    }
    when (animOut) {
        TextAnim.FADE, TextAnim.TYPE, TextAnim.WORDS -> alpha *= ease(pout)
        TextAnim.POP -> { scale *= 0.5f + 0.5f * ease(pout); alpha *= ease(pout) }
        TextAnim.SLIDE -> { dy -= 0.08f * (1 - ease(pout)); alpha *= ease(pout) }
        TextAnim.ZOOM -> { scale *= 1f + 0.6f * (1 - ease(pout)); alpha *= ease(pout) }
        TextAnim.GLITCH -> alpha *= ease(pout * 1.5f)
        TextAnim.BLUR -> { scale *= 1f + 0.15f * (1 - ease(pout)); alpha *= ease(pout) }
        else -> {}
    }
    return AnimFactors(alpha, scale, dy)
}

/** Neon che si accende a scatti (primo secondo), poi ogni tanto un tremolio. */
internal fun flicker(local: Long): Float {
    val ms = local / 1000
    if (ms < 900) {
        // Acceso/spento a intervalli irregolari, sempre più acceso.
        val on = longArrayOf(0, 60, 140, 190, 330, 380, 520, 700)
        val off = longArrayOf(40, 110, 170, 260, 360, 450, 600, 900)
        for (k in on.indices) if (ms in on[k] until off[k]) return 0.35f + 0.65f * (k + 1) / on.size
        return 0.05f
    }
    val ph = (ms - 900) % 4200
    return if (ph in 3000..3060 || ph in 3120..3170) 0.3f else 1f
}

/** Messa a fuoco: quanto è sfocato il testo (1 = molto) a [local] µs. */
internal fun blurAmount(item: TextItem, local: Long): Float {
    val d = item.durationUs
    val inUs = minOf(700_000L, d / 2).coerceAtLeast(1)
    val outUs = minOf(500_000L, d / 2).coerceAtLeast(1)
    var a = 0f
    if (item.animIn == TextAnim.BLUR && local < inUs) a = maxOf(a, 1f - local.coerceAtLeast(0).toFloat() / inUs)
    if (item.animOut == TextAnim.BLUR && local > d - outUs) a = maxOf(a, (local - (d - outUs)).toFloat() / outUs)
    return a.coerceIn(0f, 1f).let { it * it }
}

/** Movimento morbido 0→1 lungo la durata. */
internal fun motionU(local: Long, duration: Long): Float {
    val u = (local.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}

/**
 * Titoli di coda: all'inizio il bordo alto del testo tocca il fondo del quadro, alla fine il bordo basso ne tocca la cima;
 * in mezzo sale a velocità costante (anche se il testo è più alto del quadro).
 */
internal fun rollSettings(local: Long, duration: Long): OverlaySettings {
    val u = rollU(local, duration)
    return StaticOverlaySettings.Builder()
        .setOverlayFrameAnchor(0f, 1f - 2f * u)
        .setBackgroundFrameAnchor(0f, -1f + 2f * u)
        .build()
}

/** Avanzamento lineare del rullo (0 all'inizio, 1 alla fine). */
internal fun rollU(local: Long, duration: Long): Float = (local.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)

internal fun overlaySettings(alpha: Float, scale: Float, rotation: Float, x: Float, y: Float): OverlaySettings =
    StaticOverlaySettings.Builder()
        .setAlphaScale(alpha.coerceIn(0f, 1f))
        .setScale(scale.coerceAtLeast(0.001f), scale.coerceAtLeast(0.001f))
        .setRotationDegrees(-rotation)
        .setBackgroundFrameAnchor((2 * x).coerceIn(-1f, 1f), (-2 * y).coerceIn(-1f, 1f))
        .build()

/**
 * Un testo come livello sopra al video: immagine fissa, animazioni con le impostazioni del livello
 * (trasparenza, scala, posizione), quindi leggere anche in 4K. Il tempo è quello del montaggio.
 */
class TimedTextOverlay(
    private val context: Context,
    private val item: TextItem,
    private val frameW: Int,
    private val frameH: Int,
) : androidx.media3.effect.TextOverlay() {
    // Per Media3 è un "testo": così si può disegnare anche sui video HDR (le immagini semplici lì non sono accettate).
    // L'immagine la fa getBitmap, come prima; getText non si usa.
    override fun getText(presentationTimeUs: Long): android.text.SpannableString = android.text.SpannableString(item.text)


    // Due immagini che si alternano per i fotogrammi lettera per lettera (il motore ricarica l'immagine solo se cambia).
    private val spare = arrayOfNulls<Bitmap>(2)
    private var flip = 0
    private var lastLetters: Triple<TextAnim, Boolean, Float>? = null
    private var lastBitmap: Bitmap? = null

    private fun letters(anim: TextAnim, entering: Boolean, progress: Float): Bitmap {
        val k = Triple(anim, entering, progress)
        val last = lastBitmap
        if (k == lastLetters && last != null) return last
        flip = 1 - flip
        val b = TextRenderer.renderLetters(context, item, frameW, frameH, anim, entering, progress, reuse = spare[flip])
        spare[flip] = b
        lastLetters = k
        lastBitmap = b
        return b
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val local = presentationTimeUs - item.startUs
        val dur = item.durationUs
        // Lettera per lettera (uscita prima: se il testo è corto vince la fine).
        if (item.animOut.letters) {
            val ou = lettersOutUs(item)
            if (local > dur - ou) return letters(item.animOut, false, quant((local - (dur - ou)).toFloat() / ou))
        }
        if (item.animIn.letters) {
            val iu = lettersInUs(item)
            if (local < iu) return letters(item.animIn, true, quant(local.coerceAtLeast(0).toFloat() / iu))
        }
        val g = minOf(GLITCH_US, dur / 2).coerceAtLeast(1)
        if (item.animOut == TextAnim.GLITCH && local > dur - g) {
            return TextRenderer.renderGlitch(context, item, frameW, frameH, (local - (dur - g)).toFloat() / g, local / 66_000)
        }
        if (item.animIn == TextAnim.GLITCH) {
            if (local < g) return TextRenderer.renderGlitch(context, item, frameW, frameH, 1f - local.coerceAtLeast(0).toFloat() / g, local / 66_000)
            // Ogni tanto un piccolo sussulto mentre il testo resta a schermo.
            val ph = local % 2_200_000L
            if (ph in 1_500_000L..1_650_000L) return TextRenderer.renderGlitch(context, item, frameW, frameH, 0.45f, local / 66_000)
        }
        if (item.animIn == TextAnim.BLUR || item.animOut == TextAnim.BLUR) {
            val b = blurAmount(item, local)
            if (b > 0.02f) return TextRenderer.renderBlur(context, item, frameW, frameH, quant(b, 24))
        }
        if (item.animIn == TextAnim.SHINE) {
            shinePhase(local)?.let { return TextRenderer.renderShine(context, item, frameW, frameH, quant(it, 30)) }
        }
        // Sottotitoli con i tempi del parlato: karaoke (parola colorata) o parole che compaiono mentre sono dette.
        if (item.wordMode != 0 && item.wordsUs.isNotEmpty()) {
            val ranges = Regex("\\S+").findAll(TextRenderer.displayText(item)).map { it.range }.toList()
            val k = Subtitles.wordAt(item.wordsUs, local).coerceAtMost(ranges.lastIndex)
            if (ranges.isNotEmpty()) {
                return if (item.wordMode == 1) TextRenderer.render(context, item, frameW, frameH, highlight = if (k >= 0) ranges[k] else null)
                else TextRenderer.render(context, item, frameW, frameH, visible = if (k >= 0) ranges[k].last + 1 else 0)
            }
        }
        if (item.animIn == TextAnim.WORDS) {
            // Parola per parola: ogni parola compare a turno (~0,25 s l'una).
            val text = TextRenderer.displayText(item)
            val ends = Regex("\\S+").findAll(text).map { it.range.last + 1 }.toList()
            if (ends.isNotEmpty()) {
                val revealUs = minOf(ends.size * 250_000L, 2_000_000L, item.durationUs / 2).coerceAtLeast(1)
                if (local < revealUs) {
                    val n = (ends.size * local.coerceAtLeast(0) / revealUs).toInt() + 1
                    return TextRenderer.render(context, item, frameW, frameH, ends[(n - 1).coerceIn(0, ends.lastIndex)])
                }
            }
        }
        if (item.animIn == TextAnim.TYPE) {
            // Macchina da scrivere: le lettere compaiono in ~1 s (al massimo metà della durata).
            val len = TextRenderer.displayText(item).length
            val typeUs = minOf(len * 60_000L, 1_200_000L, item.durationUs / 2).coerceAtLeast(1)
            if (local < typeUs) {
                val n = (len * local.coerceAtLeast(0) / typeUs).toInt()
                return TextRenderer.render(context, item, frameW, frameH, n)
            }
        }
        return TextRenderer.render(context, item, frameW, frameH)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val local = presentationTimeUs - item.startUs
        if (item.roll) return rollSettings(local, item.durationUs)
        val f = animFactors(item.animIn, item.animOut, local, item.durationUs)
        var x = item.x; var y = item.y; var scale = 1f; var rot = item.rotation; var alpha = 1f
        item.endPose?.let { e ->
            val u = motionU(local, item.durationUs)
            x += (e.x - x) * u; y += (e.y - y) * u
            scale = kotlin.math.exp(kotlin.math.ln(e.scale.coerceAtLeast(0.01f)) * u)
            rot += (e.rotation - rot) * u
            alpha = 1f + (e.opacity - 1f) * u
        }
        return overlaySettings(f.alpha * alpha, f.scale * scale, rot, x, y + f.dy)
    }
}
