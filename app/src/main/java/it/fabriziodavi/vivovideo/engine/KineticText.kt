package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import it.fabriziodavi.vivovideo.model.Keywords
import it.fabriziodavi.vivovideo.model.KineticStyle
import it.fabriziodavi.vivovideo.model.Subtitles
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Testi cinetici (2027): una parola alla volta, grande, con le parole importanti nel colore di risalto.
 * Le immagini cambiano solo a ogni parola (e per pochi fotogrammi nei riflessi e nei glitch); il rimbalzo, la salita
 * e la rotazione li fanno le impostazioni del livello, quindi restano leggeri anche in 4K.
 */
object KineticText {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun words(item: TextItem): List<String> = Regex("\\S+").findAll(TextRenderer.displayText(item)).map { it.value }.toList()

    /** Inizio di ogni parola: quelli della voce per i sottotitoli, a passo regolare per i testi scritti a mano. */
    fun times(item: TextItem): List<Long> =
        if (item.subtitle && item.wordsUs.isNotEmpty()) item.wordsUs else Subtitles.evenWords(item.text, item.durationUs)

    /** Parola in corso e da quanto (µs) è iniziata; -1 prima della prima. */
    fun current(item: TextItem, local: Long): Pair<Int, Long> {
        val ts = times(item)
        val k = Subtitles.wordAt(ts, local).coerceAtMost(words(item).lastIndex)
        return if (k < 0) -1 to 0L else k to (local - ts[k]).coerceAtLeast(0)
    }

    /** Scala, trasparenza, rotazione e spostamento in alto (frazione del quadro) della parola dopo [since] µs. */
    data class Motion(val scale: Float, val alpha: Float, val rotation: Float, val dy: Float)

    fun motion(style: KineticStyle, key: Boolean, index: Int, since: Long, local: Long, duration: Long): Motion {
        val x = since / 1e6f
        // Uscita: l'ultima parola sparisce negli ultimi 120 ms del testo.
        val outA = ((duration - local) / 120_000f).coerceIn(0f, 1f)
        return when (style) {
            KineticStyle.POP -> {
                // Da piccola a più grande del giusto, poi si assesta (molla smorzata).
                val s = if (x < 0.05f) 0.55f + x / 0.05f * 0.6f else 1f + 0.15f * exp(-(x - 0.05f) * 14f) * kotlin.math.cos((x - 0.05f) * 30f)
                val rot = if (key) (if (index % 2 == 0) -4f else 4f) else 0f
                Motion(s * (if (key) 1.12f else 1f), (x / 0.04f).coerceIn(0f, 1f) * outA, rot, 0f)
            }
            KineticStyle.GLASS -> {
                val e = 1f - exp(-x * 16f)
                Motion(0.88f + 0.12f * e, e * outA, 0f, 0.025f * (1f - e))
            }
            KineticStyle.CHROME -> {
                val e = 1f - exp(-x * 18f)
                Motion(1.25f - 0.25f * e, e * outA, 0f, 0f)
            }
            KineticStyle.GLITCH -> Motion(if (x < 0.06f) 1.08f else 1f, outA, 0f, 0f)
            KineticStyle.WAVE -> Motion(1f, outA, 0f, 0f)
        }
    }

    private fun paint(context: Context, item: TextItem, sizePx: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = TextRenderer.typeface(context, item)
        textSize = sizePx
    }

    /** Immagine della parola (o della frase, per l'onda) all'istante [local] del testo. */
    fun bitmap(context: Context, item: TextItem, frameW: Int, frameH: Int, local: Long): Bitmap? {
        val style = item.kinetic ?: return null
        val ws = words(item)
        if (ws.isEmpty()) return null
        val (k, since) = current(item, local)
        if (style == KineticStyle.WAVE) return wave(context, item, frameW, frameH, ws, k, since)
        val i = k.coerceAtLeast(0)
        val word = ws[i]
        val key = Keywords.isKey(word)
        return when (style) {
            KineticStyle.POP -> {
                val w = item.copy(text = word, color = if (key) item.highlight else item.color, look = TextLook.CONTORNO, wordsUs = emptyList(), wordMode = 0, kinetic = null)
                TextRenderer.render(context, w, frameW, frameH)
            }
            KineticStyle.GLITCH -> {
                val w = item.copy(text = word, color = if (key) item.highlight else item.color, look = TextLook.OMBRA, wordsUs = emptyList(), wordMode = 0, kinetic = null)
                // Arriva a scatti per 180 ms; le parole importanti hanno un sussulto anche dopo.
                val amount = when {
                    since < 180_000 -> 1f - since / 180_000f
                    key && (since / 1000) % 700 in 0..60 -> 0.35f
                    else -> 0f
                }
                if (amount > 0.02f) TextRenderer.renderGlitch(context, w, frameW, frameH, (amount * 12).toInt() / 12f, since / 50_000) else TextRenderer.render(context, w, frameW, frameH)
            }
            KineticStyle.GLASS -> glass(context, item, frameW, frameH, word, key)
            KineticStyle.CHROME -> chrome(context, item, frameW, frameH, word, key, since)
            KineticStyle.WAVE -> null
        }
    }

    /** Lastra di vetro: velo chiaro, riflesso in alto, bordo luminoso; la parola bianca (o colorata se importante). */
    private fun glass(context: Context, item: TextItem, frameW: Int, frameH: Int, word: String, key: Boolean): Bitmap {
        val ck = "g|$word|$key|${item.size}|${item.font}|${item.fontFile}|${item.highlight}|$frameW|$frameH"
        cache.get(ck)?.let { return it }
        val p = paint(context, item, item.size * frameH * 0.85f)
        val tw = p.measureText(word)
        val padX = p.textSize * 0.55f; val padY = p.textSize * 0.32f
        val fm = p.fontMetrics
        val th = fm.descent - fm.ascent
        val bw = ceil(tw + 2 * padX).toInt() + 4; val bh = ceil(th + 2 * padY).toInt() + 4
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = RectF(2f, 2f, bw - 2f, bh - 2f)
        val rad = bh / 2.2f
        val tint = if (key) item.highlight else Color.WHITE
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(Color.argb(110, Color.red(tint), Color.green(tint), Color.blue(tint)), 0x1FFFFFFF, 0x30FFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = max(2f, p.textSize * 0.045f)
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0xE6FFFFFF.toInt(), 0x33FFFFFF, 0x99FFFFFF.toInt()), null, Shader.TileMode.CLAMP)
        })
        p.color = if (key) item.highlight else Color.WHITE
        p.setShadowLayer(p.textSize * 0.08f, 0f, p.textSize * 0.03f, 0x66000000)
        c.drawText(word, bw / 2f - tw / 2f, bh / 2f - (fm.ascent + fm.descent) / 2f, p)
        cache.put(ck, b)
        return b
    }

    /** Metallo lucido: sfumatura argento a bande, contorno scuro e un riflesso che passa nei primi 450 ms. */
    private fun chrome(context: Context, item: TextItem, frameW: Int, frameH: Int, word: String, key: Boolean, since: Long): Bitmap {
        val phase = if (since < 450_000) (since / 450_000f * 15).toInt() else 15
        val ck = "c|$word|$key|$phase|${item.size}|${item.font}|${item.fontFile}|${item.highlight}|$frameW|$frameH"
        cache.get(ck)?.let { return it }
        val p = paint(context, item, item.size * frameH)
        val tw = p.measureText(word)
        val fm = p.fontMetrics
        val pad = p.textSize * 0.2f
        val bw = ceil(tw + 2 * pad).toInt(); val bh = ceil(fm.descent - fm.ascent + 2 * pad).toInt()
        val b = Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val x = pad; val y = pad - fm.ascent
        p.style = Paint.Style.STROKE; p.strokeWidth = p.textSize * 0.1f; p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFF14161C.toInt(); p.setShadowLayer(p.textSize * 0.12f, 0f, p.textSize * 0.05f, 0x99000000.toInt())
        c.drawText(word, x, y, p)
        p.clearShadowLayer(); p.style = Paint.Style.FILL
        val top = y + fm.ascent; val bottom = y + fm.descent
        val tint = if (key) item.highlight else 0xFFDDE3EA.toInt()
        p.shader = LinearGradient(0f, top, 0f, bottom,
            intArrayOf(Color.WHITE, tint, 0xFF5B6270.toInt(), 0xFFF2F5F8.toInt(), 0xFF8C96A3.toInt()),
            floatArrayOf(0f, 0.38f, 0.52f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawText(word, x, y, p)
        if (phase < 15) {
            // Riflesso: una banda chiara inclinata che attraversa la parola.
            val cx = -bw * 0.3f + (bw * 1.6f) * phase / 15f
            p.shader = LinearGradient(cx - bh * 0.5f, top, cx + bh * 0.5f, bottom, intArrayOf(0x00FFFFFF, 0xE6FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
            p.xfermode = null
        }
        p.shader = null
        if (b.byteCount < 4 * 1024 * 1024) cache.put(ck, b)
        return b
    }

    /** Onda: la frase si scrive parola per parola; quella detta salta su ed è più grande, quelle importanti colorate. */
    private fun wave(context: Context, item: TextItem, frameW: Int, frameH: Int, ws: List<String>, k: Int, since: Long): Bitmap {
        val q = if (since < 300_000) (since / 300_000f * 10).toInt() else 10
        val ck = "w|${ws.joinToString(" ")}|$k|$q|${item.size}|${item.font}|${item.fontFile}|${item.highlight}|${item.color}|$frameW|$frameH"
        cache.get(ck)?.let { return it }
        val p = paint(context, item, item.size * frameH)
        // La frase intera deve stare nel 90% della larghezza: se no il carattere si rimpicciolisce.
        val full = p.measureText(ws.joinToString(" "))
        if (full > frameW * 0.9f) p.textSize *= frameW * 0.9f / full
        val space = p.measureText(" ")
        val widths = ws.map { p.measureText(it) }
        val fm = p.fontMetrics
        val lift = p.textSize * 0.35f
        val pad = p.textSize * 0.25f
        val bw = ceil(widths.sum() + space * (ws.size - 1) + 2 * pad + p.textSize * 0.2f).toInt()
        val bh = ceil(fm.descent - fm.ascent + lift + 2 * pad).toInt()
        val b = Bitmap.createBitmap(bw.coerceAtLeast(2), bh.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        var x = pad
        val base = pad + lift - fm.ascent
        val bounce = sin(q / 10f * PI).toFloat()
        ws.forEachIndexed { i, w ->
            if (i > k) return@forEachIndexed
            val key = Keywords.isKey(w)
            val dy = if (i == k) -lift * bounce else 0f
            p.style = Paint.Style.STROKE; p.strokeWidth = p.textSize * 0.12f; p.strokeJoin = Paint.Join.ROUND; p.color = 0xE6000000.toInt()
            c.drawText(w, x, base + dy, p)
            p.style = Paint.Style.FILL
            p.color = if (key) item.highlight else item.color
            c.drawText(w, x, base + dy, p)
            x += widths[i] + space
        }
        if (b.byteCount < 4 * 1024 * 1024) cache.put(ck, b)
        return b
    }
}
