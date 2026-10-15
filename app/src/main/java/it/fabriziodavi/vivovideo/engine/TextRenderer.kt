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
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import it.fabriziodavi.vivovideo.R
import it.fabriziodavi.vivovideo.model.TextAnim
import it.fabriziodavi.vivovideo.model.TextFont
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
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

    private fun blendToWhite(c: Int, k: Float): Int = Color.argb(
        255,
        (Color.red(c) + (255 - Color.red(c)) * k).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * k).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * k).toInt(),
    )

    private fun isLight(c: Int): Boolean =
        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) > 140

    private class Prepared(val layout: StaticLayout, val width: Int, val pad: Int, val paint: TextPaint)

    private fun prepare(context: Context, item: TextItem, frameW: Int, frameH: Int, visible: Int): Prepared {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context, item.font)
            textSize = (item.size * frameH).coerceAtLeast(4f)
            color = item.color
        }
        val text = item.text.ifEmpty { " " }
        // Larghezza massima: 90% del quadro; poi si stringe alla riga più lunga.
        val maxW = (frameW * 0.9f).toInt().coerceAtLeast(16)
        val content: CharSequence = if (visible >= text.length) text else SpannableString(text).apply {
            setSpan(ForegroundColorSpan(Color.TRANSPARENT), visible.coerceAtLeast(0), text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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

    fun render(context: Context, item: TextItem, frameW: Int, frameH: Int, visible: Int = Int.MAX_VALUE): Bitmap {
        val v = visible.coerceAtMost(item.text.length)
        val key = "${item.text.hashCode()}|${item.font}|${item.look}|${item.color}|${item.size}|$frameW|$frameH|$v"
        cache.get(key)?.let { return it }
        val p = prepare(context, item, frameW, frameH, v)
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
        p.layout.draw(c)
        cache.put(key, bmp)
        return bmp
    }
}

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
        else -> {}
    }
    when (animOut) {
        TextAnim.FADE, TextAnim.TYPE -> alpha *= ease(pout)
        TextAnim.POP -> { scale *= 0.5f + 0.5f * ease(pout); alpha *= ease(pout) }
        TextAnim.SLIDE -> { dy -= 0.08f * (1 - ease(pout)); alpha *= ease(pout) }
        TextAnim.ZOOM -> { scale *= 1f + 0.6f * (1 - ease(pout)); alpha *= ease(pout) }
        else -> {}
    }
    return AnimFactors(alpha, scale, dy)
}

/** Movimento morbido 0→1 lungo la durata. */
internal fun motionU(local: Long, duration: Long): Float {
    val u = (local.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}

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
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val local = presentationTimeUs - item.startUs
        if (item.animIn == TextAnim.TYPE) {
            // Macchina da scrivere: le lettere compaiono in ~1 s (al massimo metà della durata).
            val typeUs = minOf(item.text.length * 60_000L, 1_200_000L, item.durationUs / 2).coerceAtLeast(1)
            if (local < typeUs) {
                val n = (item.text.length * local.coerceAtLeast(0) / typeUs).toInt()
                return TextRenderer.render(context, item, frameW, frameH, n)
            }
        }
        return TextRenderer.render(context, item, frameW, frameH)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val local = presentationTimeUs - item.startUs
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
