package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.LruCache
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import it.fabriziodavi.vivovideo.R
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.TeleItem
import it.fabriziodavi.vivovideo.model.TeleLayoutCalc
import it.fabriziodavi.vivovideo.model.TeleParser
import it.fabriziodavi.vivovideo.model.TeleSample
import it.fabriziodavi.vivovideo.model.TeleStyle
import it.fabriziodavi.vivovideo.model.TeleSync
import it.fabriziodavi.vivovideo.model.TeleTrack
import it.fabriziodavi.vivovideo.model.TeleWidget
import it.fabriziodavi.vivovideo.model.TeleZones
import it.fabriziodavi.vivovideo.model.Telemetry
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Attività lette dai file (una volta sola, poi in memoria). */
object TeleStore {
    private val cache = LruCache<String, TeleTrack>(4)
    fun load(path: String): TeleTrack? = synchronized(cache) {
        cache.get(path) ?: runCatching { TeleParser.parse(File(path).readBytes()) }.getOrNull()?.also { cache.put(path, it) }
    }
}

/**
 * Disegno degli indicatori: un pannello per ognuno, nello stile scelto, con le parti che si muovono a tempo
 * (lancetta, cuore, pedivella, mappa, profilo). [u] = unità (1% del lato corto per la grandezza scelta).
 */
class TeleRenderer(private val context: Context, private val t: Telemetry, private val track: TeleTrack) {
    private val accent = t.accent
    private val hot = 0xFFFF3D5A.toInt()
    private val ftp = if (t.ftp > 0) t.ftp else track.ftpAuto
    private val hrMax = if (t.hrMax > 0) t.hrMax else track.hrMaxAuto
    private fun font(id: Int, fallback: Typeface) = runCatching { context.resources.getFont(id) }.getOrNull() ?: fallback
    private val numFace: Typeface = when (t.style) {
        TeleStyle.GLASS -> font(R.font.plex_semibold, Typeface.DEFAULT_BOLD)
        TeleStyle.HUD -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        TeleStyle.RACE, TeleStyle.NEON -> font(R.font.bebas, Typeface.DEFAULT_BOLD)
        TeleStyle.MINIMAL -> font(R.font.anton, Typeface.DEFAULT_BOLD)
    }
    private val labelFace: Typeface = if (t.style == TeleStyle.HUD) Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL) else font(R.font.plex_medium, Typeface.DEFAULT)
    private val niceMaxSpeed = (ceil(max(track.maxSpeed, 25f) / 10f) * 10f)

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun withA(c: Int, a: Float) = Color.argb((Color.alpha(c) * a).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    private fun mix(a: Int, b: Int, k: Float): Int {
        val q = k.coerceIn(0f, 1f)
        return Color.argb(255, (Color.red(a) + (Color.red(b) - Color.red(a)) * q).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * q).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * q).toInt())
    }

    // ---------- pannelli ----------

    /** Fondo dell'indicatore nello stile scelto. [round] = cerchio (mappa nei visori). */
    private fun panel(c: Canvas, r: RectF, u: Float, round: Boolean = false) {
        val p = paint()
        val rad = if (round) r.width() / 2 else u * 3.2f
        when (t.style) {
            TeleStyle.GLASS -> {
                // Vetro: velo scuro, riflesso chiaro in alto, bordo luminoso che sfuma.
                p.color = 0x47101418
                c.drawRoundRect(r, rad, rad, p)
                p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0x40FFFFFF, 0x0DFFFFFF, 0x14FFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(r, rad, rad, p)
                p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0xB3FFFFFF.toInt(), 0x1AFFFFFF, withA(accent, 0.55f)), null, Shader.TileMode.CLAMP)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, u * 0.22f)
                val i = p.strokeWidth / 2
                c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), rad, rad, p)
            }
            TeleStyle.HUD -> {
                p.color = 0x2605080C
                if (round) c.drawOval(r, p) else c.drawRect(r, p)
                p.color = accent; p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, u * 0.28f)
                if (round) {
                    c.drawArc(r, 200f, 50f, false, p); c.drawArc(r, 20f, 50f, false, p)
                    p.alpha = 90; p.strokeWidth = max(1f, u * 0.12f); c.drawOval(r, p)
                } else {
                    // Angoli a squadra e una riga di tacche in basso.
                    val k = min(r.width(), r.height()) * 0.22f
                    val path = Path().apply {
                        moveTo(r.left, r.top + k); lineTo(r.left, r.top); lineTo(r.left + k, r.top)
                        moveTo(r.right - k, r.top); lineTo(r.right, r.top); lineTo(r.right, r.top + k)
                        moveTo(r.right, r.bottom - k); lineTo(r.right, r.bottom); lineTo(r.right - k, r.bottom)
                        moveTo(r.left + k, r.bottom); lineTo(r.left, r.bottom); lineTo(r.left, r.bottom - k)
                    }
                    c.drawPath(path, p)
                    p.strokeWidth = max(1f, u * 0.12f); p.alpha = 110
                    var x = r.left + k * 1.4f
                    while (x < r.right - k * 1.4f) { c.drawLine(x, r.bottom - u * 0.9f, x, r.bottom - u * 0.3f, p); x += u * 1.4f }
                }
            }
            TeleStyle.RACE -> {
                // Pannello inclinato con la banda colorata a sinistra.
                val s = r.height() * 0.22f
                val path = Path().apply { moveTo(r.left + s, r.top); lineTo(r.right, r.top); lineTo(r.right - s, r.bottom); lineTo(r.left, r.bottom); close() }
                p.shader = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(0xE60B0D12.toInt(), 0xB30B0D12.toInt()), null, Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                p.shader = null; p.color = accent
                val band = Path().apply { moveTo(r.left + s, r.top); lineTo(r.left + s + u * 1.1f, r.top); lineTo(r.left + u * 1.1f, r.bottom); lineTo(r.left, r.bottom); close() }
                c.drawPath(band, p)
            }
            TeleStyle.NEON -> {
                p.color = 0x5905060A
                c.drawRoundRect(r, rad, rad, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, u * 0.3f); p.color = accent
                p.maskFilter = BlurMaskFilter(u * 1.2f, BlurMaskFilter.Blur.NORMAL)
                val i = u * 0.8f
                val rr = RectF(r.left + i, r.top + i, r.right - i, r.bottom - i)
                c.drawRoundRect(rr, rad, rad, p)
                p.maskFilter = null; p.color = mix(accent, Color.WHITE, 0.55f); p.strokeWidth = max(1f, u * 0.14f)
                c.drawRoundRect(rr, rad, rad, p)
            }
            TeleStyle.MINIMAL -> {}
        }
    }

    // ---------- testi ----------

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, face: Typeface = numFace,
                     align: Paint.Align = Paint.Align.LEFT, glow: Boolean = false, spacing: Float = 0f): Float {
        val p = paint().apply {
            typeface = face; textSize = size; this.color = color; textAlign = align; letterSpacing = spacing
            if (t.style == TeleStyle.RACE && face == numFace) textSkewX = -0.18f
            when {
                glow || t.style == TeleStyle.NEON -> setShadowLayer(size * 0.28f, 0f, 0f, if (color == Color.WHITE) accent else color)
                else -> setShadowLayer(size * 0.12f, 0f, size * 0.04f, 0x99000000.toInt())
            }
        }
        c.drawText(s, x, y, p)
        return p.measureText(s)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, u: Float, color: Int = 0xC8FFFFFF.toInt(), align: Paint.Align = Paint.Align.LEFT) =
        text(c, s.uppercase(Locale.ITALIAN), x, y, u * 1.7f, color, labelFace, align, spacing = 0.14f)

    private fun fmt(v: Float, dec: Int = 0): String = if (v.isNaN()) "--" else String.format(Locale.ITALIAN, "%.${dec}f", v)

    // ---------- indicatori ----------

    fun draw(c: Canvas, w: TeleWidget, item: TeleItem, s: TeleSample, u: Float, nowMs: Long) {
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        val (wu, hu) = TeleLayoutCalc.sizeU(w, t.style)
        val r = RectF(0f, 0f, wu * u, hu * u)
        when (w) {
            TeleWidget.SPEED -> if (t.style == TeleStyle.RACE) speedRace(c, r, s, u) else speedGauge(c, r, s, u, nowMs)
            TeleWidget.HEART -> heart(c, r, s, u)
            TeleWidget.POWER -> power(c, r, s, u)
            TeleWidget.CADENCE -> cadence(c, r, s, u)
            TeleWidget.GRADE -> grade(c, r, s, u)
            TeleWidget.ALTITUDE -> altitude(c, r, s, u)
            TeleWidget.MAP -> map(c, r, s, u, nowMs)
            TeleWidget.DISTANCE -> distance(c, r, s, u)
            TeleWidget.TIME -> time(c, r, s, u)
            TeleWidget.TEMP -> temp(c, r, s, u)
        }
    }

    /** Contagiri: arco a 270° con tacche, scia colorata, punta luminosa, numero grande e scie di velocità. */
    private fun speedGauge(c: Canvas, r: RectF, s: TeleSample, u: Float, nowMs: Long) {
        panel(c, r, u, round = t.style == TeleStyle.HUD || t.style == TeleStyle.NEON)
        val v = if (s.speedKmh.isNaN()) 0f else s.speedKmh
        val k = (v / niceMaxSpeed).coerceIn(0f, 1f)
        val cx = r.centerX(); val cy = r.centerY() + u * 0.8f
        val rad = r.width() * 0.37f
        val arc = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        val p = paint().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = u * 2.1f }
        // Scie di velocità dietro al numero quando vai forte.
        if (k > 0.45f) {
            val sp = paint().apply { strokeCap = Paint.Cap.ROUND; strokeWidth = u * 0.35f }
            for (i in 0 until 7) {
                val seed = i * 37.17f
                val ph = ((nowMs / 1000f) * (1.6f + k * 2.5f) + frac(sin(seed) * 91.3f)) % 1f
                val yy = cy - rad * 0.55f + frac(sin(seed * 3.1f) * 47.7f) * rad * 1.1f
                val len = rad * (0.3f + 0.5f * k)
                val x0 = cx + rad * 0.9f - ph * rad * 2.2f
                sp.color = withA(Color.WHITE, (k - 0.45f) * 1.4f * (1f - ph))
                c.drawLine(x0, yy, x0 + len, yy, sp)
            }
        }
        p.color = 0x2EFFFFFF
        c.drawArc(arc, 135f, 270f, false, p)
        // Tacche: ogni 5 km/h, più lunghe ogni 10.
        val tp = paint().apply { strokeWidth = max(1f, u * 0.25f); strokeCap = Paint.Cap.ROUND }
        var mark = 0f
        while (mark <= niceMaxSpeed + 0.1f) {
            val a = Math.toRadians(135.0 + 270.0 * mark / niceMaxSpeed)
            val major = (mark % 10f) < 0.1f
            val r0 = rad + u * 1.6f; val r1 = r0 + (if (major) u * 1.4f else u * 0.7f)
            tp.color = if (mark <= v) withA(mix(accent, hot, mark / niceMaxSpeed), 0.95f) else 0x66FFFFFF
            c.drawLine(cx + (r0 * cos(a)).toFloat(), cy + (r0 * sin(a)).toFloat(), cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), tp)
            mark += 5f
        }
        if (k > 0.005f) {
            // Il colore va dal risalto al rosso lungo l'arco (che parte a 135°).
            val sweep = SweepGradient(cx, cy, intArrayOf(accent, mix(accent, hot, 0.5f), hot, hot), floatArrayOf(0f, 0.45f, 0.75f, 1f))
            sweep.setLocalMatrix(android.graphics.Matrix().apply { setRotate(135f, cx, cy) })
            p.shader = sweep
            if (t.style == TeleStyle.NEON || t.style == TeleStyle.GLASS) p.setShadowLayer(u * 1.4f, 0f, 0f, mix(accent, hot, k))
            c.drawArc(arc, 135f, 270f * k, false, p)
            p.shader = null; p.clearShadowLayer()
            // Punta luminosa.
            val a = Math.toRadians(135.0 + 270.0 * k)
            val tx = cx + (rad * cos(a)).toFloat(); val ty = cy + (rad * sin(a)).toFloat()
            val g = paint().apply { color = mix(accent, hot, k); maskFilter = BlurMaskFilter(u * 2.2f, BlurMaskFilter.Blur.NORMAL) }
            c.drawCircle(tx, ty, u * 2.2f, g)
            c.drawCircle(tx, ty, u * 1.05f, paint().apply { color = Color.WHITE })
        }
        val big = r.width() * 0.33f
        text(c, fmt(v), cx, cy + big * 0.32f, big, align = Paint.Align.CENTER)
        label(c, "km/h", cx, cy + big * 0.32f + u * 3.2f, u, align = Paint.Align.CENTER)
        if (t.peaks && track.maxSpeed > 15f && v >= track.maxSpeed * 0.97f) badge(c, cx, r.top + u * 3.6f, "MAX", u)
    }

    /** Gara: numero grande inclinato e barra a LED che si accende verso il rosso. */
    private fun speedRace(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        panel(c, r, u)
        val v = if (s.speedKmh.isNaN()) 0f else s.speedKmh
        val big = r.height() * 0.78f
        val x0 = r.left + u * 4f
        val wNum = text(c, fmt(v), x0, r.centerY() + big * 0.36f, big)
        label(c, "km/h", x0 + wNum + u * 0.8f, r.centerY() + big * 0.36f, u)
        val segs = 16
        val left = x0 + max(wNum, big * 1.1f) + u * 6.5f
        val right = r.right - u * 4.5f
        val sw = (right - left) / segs
        val k = (v / niceMaxSpeed).coerceIn(0f, 1f)
        val on = (k * segs).toInt()
        val p = paint()
        for (i in 0 until segs) {
            val hgt = r.height() * (0.25f + 0.45f * i / segs)
            val x = left + i * sw
            val base = r.centerY() + r.height() * 0.3f
            val col = mix(0xFF2EE59D.toInt(), if (i > segs * 0.6f) hot else 0xFFFFD43D.toInt(), i.toFloat() / segs)
            p.color = if (i < on) col else 0x26FFFFFF
            if (i < on) p.setShadowLayer(u * 0.8f, 0f, 0f, col) else p.clearShadowLayer()
            val sk = hgt * 0.2f
            val path = Path().apply { moveTo(x + sk, base - hgt); lineTo(x + sw * 0.72f + sk, base - hgt); lineTo(x + sw * 0.72f, base); lineTo(x, base); close() }
            c.drawPath(path, p)
        }
        if (t.peaks && track.maxSpeed > 15f && v >= track.maxSpeed * 0.97f) badge(c, right - u * 3f, r.top + u * 2.6f, "MAX", u)
    }

    private fun badge(c: Canvas, cx: Float, cy: Float, s: String, u: Float) {
        val p = paint().apply { color = hot; setShadowLayer(u * 1.2f, 0f, 0f, hot) }
        val w = u * 7.5f; val h = u * 2.8f
        c.drawRoundRect(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h / 2, h / 2, p)
        text(c, s, cx, cy + u * 0.75f, u * 2.1f, face = labelFace, align = Paint.Align.CENTER, spacing = 0.15f)
    }

    /** Scheda compatta: disegno a sinistra, etichetta, numero, unità e una barra delle zone in basso. */
    private fun card(c: Canvas, r: RectF, u: Float, name: String, value: String, unit: String, color: Int, zone: Int?, zoneName: String?,
                     icon: (Canvas, Float, Float, Float) -> Unit) {
        panel(c, r, u)
        val iconSize = r.height() * 0.62f
        val ix = r.left + u * 2f + iconSize / 2 + (if (t.style == TeleStyle.RACE) u * 1.8f else 0f)
        icon(c, ix, r.centerY() - (if (zone != null) u * 0.9f else 0f), iconSize)
        val tx = ix + iconSize / 2 + u * 1.8f
        // In alto il nome, o la zona in corso nel suo colore (il numero con l'unità dice già cos'è).
        if (zone != null && zoneName != null) label(c, "Z$zone  $zoneName", tx, r.top + u * 3f, u, TeleZones.colors[zone])
        else label(c, name, tx, r.top + u * 3f, u, 0xB3FFFFFF.toInt())
        val big = r.height() * 0.48f
        val wv = text(c, value, tx, r.top + u * 3.2f + big * 0.92f, big)
        label(c, unit, tx + wv + u * 0.7f, r.top + u * 3.2f + big * 0.92f, u)
        if (zone != null) {
            // Cinque tacche: quella della zona in corso accesa nel suo colore.
            val y = r.bottom - u * 2f
            val x0 = tx; val x1 = r.right - u * 2.5f
            val sw = (x1 - x0) / 5f
            val p = paint()
            for (z in 1..5) {
                p.color = if (z == zone) TeleZones.colors[z] else withA(TeleZones.colors[z], 0.28f)
                if (z == zone) p.setShadowLayer(u * 0.9f, 0f, 0f, TeleZones.colors[z]) else p.clearShadowLayer()
                c.drawRoundRect(RectF(x0 + (z - 1) * sw, y - u * 0.55f, x0 + z * sw - u * 0.5f, y + u * 0.55f), u * 0.55f, u * 0.55f, p)
            }
        }
    }

    /** Cuore che batte al ritmo vero (un colpo a ogni battito registrato). */
    private fun heart(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        val bpm = s.heart
        val zone = if (bpm.isNaN()) null else TeleZones.heart(bpm, hrMax)
        val col = zone?.let { TeleZones.colors[it] } ?: 0x99FFFFFF.toInt()
        val ph = frac(s.beats.toFloat())
        val beat = exp(-ph * 7f) + 0.45f * exp(-max(0f, ph - 0.18f) * 9f) * (if (ph > 0.18f) 1f else 0f)
        card(c, r, u, "Battito", fmt(bpm), "bpm", col, zone, zone?.let { TeleZones.heartNames[it] }) { cv, x, y, sz ->
            val k = 0.82f + 0.2f * beat
            val h = sz * 0.8f * k
            val path = Path().apply {
                moveTo(x, y + h * 0.42f)
                cubicTo(x - h * 0.75f, y - h * 0.05f, x - h * 0.42f, y - h * 0.62f, x, y - h * 0.22f)
                cubicTo(x + h * 0.42f, y - h * 0.62f, x + h * 0.75f, y - h * 0.05f, x, y + h * 0.42f)
                close()
            }
            val p = paint().apply { color = col; setShadowLayer(u * (0.6f + 1.6f * beat), 0f, 0f, col) }
            cv.drawPath(path, p)
            if (t.peaks && !bpm.isNaN() && track.maxHeart > 100 && bpm >= track.maxHeart * 0.98f) badge(cv, x, y + sz * 0.62f, "MAX", u * 0.8f)
        }
    }

    /** Potenza con il fulmine e le zone dell'FTP. */
    private fun power(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        val w = s.power
        val zone = if (w.isNaN()) null else TeleZones.power(w, ftp)
        val col = zone?.let { TeleZones.colors[it] } ?: 0x99FFFFFF.toInt()
        card(c, r, u, "Potenza", fmt(w), "W", col, zone, zone?.let { TeleZones.powerNames[it] }) { cv, x, y, sz ->
            val h = sz * 0.85f
            val path = Path().apply {
                moveTo(x + h * 0.12f, y - h * 0.5f); lineTo(x - h * 0.3f, y + h * 0.06f); lineTo(x - h * 0.02f, y + h * 0.06f)
                lineTo(x - h * 0.14f, y + h * 0.5f); lineTo(x + h * 0.3f, y - h * 0.08f); lineTo(x + h * 0.02f, y - h * 0.08f); close()
            }
            val intensity = if (w.isNaN()) 0f else (w / ftp).coerceIn(0f, 1.6f)
            cv.drawPath(path, paint().apply { color = col; setShadowLayer(u * (0.5f + intensity * 1.6f), 0f, 0f, col) })
        }
    }

    /** Cadenza: la pedivella gira davvero alla velocità delle pedalate. */
    private fun cadence(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        card(c, r, u, "Cadenza", fmt(s.cadence), "rpm", accent, null, null) { cv, x, y, sz ->
            val rad = sz * 0.36f
            val p = paint().apply { style = Paint.Style.STROKE; strokeWidth = u * 0.5f; color = 0x80FFFFFF.toInt() }
            cv.drawCircle(x, y, rad, p)
            val a = (s.revs * 2 * PI).toFloat()
            val ex = x + rad * 0.95f * cos(a); val ey = y + rad * 0.95f * sin(a)
            p.color = accent; p.strokeWidth = u * 0.9f; p.strokeCap = Paint.Cap.ROUND; p.setShadowLayer(u, 0f, 0f, accent)
            cv.drawLine(x - (ex - x) * 0.4f, y - (ey - y) * 0.4f, ex, ey, p)
            cv.drawCircle(ex, ey, u * 0.9f, paint().apply { color = Color.WHITE })
            cv.drawCircle(x, y, u * 0.8f, paint().apply { color = accent })
        }
    }

    /** Pendenza: la rampa disegnata si inclina con la strada e cambia colore. */
    private fun grade(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        val g = s.grade
        val col = when {
            g.isNaN() -> 0x99FFFFFF.toInt(); g < -2f -> 0xFF4DA3FF.toInt(); g < 3f -> 0xFF2EE59D.toInt()
            g < 6f -> 0xFFFFD43D.toInt(); g < 10f -> 0xFFFF8A3D.toInt(); else -> hot
        }
        card(c, r, u, if (!g.isNaN() && g < -1f) "Discesa" else "Pendenza", if (g.isNaN()) "--" else fmt(g, if (kotlin.math.abs(g) < 10) 1 else 0), "%", col, null, null) { cv, x, y, sz ->
            val ang = atan((if (g.isNaN()) 0f else g) / 100f * 2.2f).coerceIn(-0.9f, 0.9f)
            val half = sz * 0.48f
            val dy = half * kotlin.math.tan(ang)
            val path = Path().apply { moveTo(x - half, y + half * 0.45f); lineTo(x + half, y + half * 0.45f); lineTo(x + half, y + half * 0.45f - dy * 2); close() }
            if (dy < 0) path.apply { reset(); moveTo(x - half, y + half * 0.45f); lineTo(x + half, y + half * 0.45f); lineTo(x - half, y + half * 0.45f + dy * 2); close() }
            cv.drawPath(path, paint().apply { shader = LinearGradient(x - half, 0f, x + half, 0f, withA(col, 0.25f), col, Shader.TileMode.CLAMP) })
            cv.drawLine(x - half, y + half * 0.45f, x + half, y + half * 0.45f, paint().apply { color = 0x99FFFFFF.toInt(); strokeWidth = u * 0.3f })
        }
    }

    // Profilo e mappa: punti del giro già proiettati (al massimo ~400), ricalcolati solo se cambia la misura.
    private val step by lazy { max(1, track.size / 400) }
    private val profilePts by lazy { (0 until track.size step step).map { track.distanceM[it] to track.altitude[it] }.filter { !it.second.isNaN() } }

    private fun altitude(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        panel(c, r, u)
        val pad = u * 2.2f
        val box = RectF(r.left + pad + u * 14f, r.top + pad, r.right - pad, r.bottom - pad)
        label(c, "Quota", r.left + pad, r.top + u * 3.2f, u, 0xB3FFFFFF.toInt())
        val big = r.height() * 0.4f
        val wv = text(c, fmt(s.altitude), r.left + pad, r.top + u * 3.4f + big * 0.92f, big)
        label(c, "m", r.left + pad + wv + u * 0.6f, r.top + u * 3.4f + big * 0.92f, u)
        label(c, "+${fmt(track.climbM)} m", r.left + pad, r.bottom - u * 1.6f, u, withA(accent, 0.95f))
        if (profilePts.size < 2) return
        val dMax = max(1f, track.totalDistanceM)
        val aMin = track.minAltitude; val aMax = max(aMin + 20f, track.maxAltitude)
        fun px(d: Float) = box.left + d / dMax * box.width()
        fun py(a: Float) = box.bottom - (a - aMin) / (aMax - aMin) * box.height()
        val line = Path(); val fill = Path()
        profilePts.forEachIndexed { i, (d, a) -> if (i == 0) { line.moveTo(px(d), py(a)); fill.moveTo(px(d), box.bottom); fill.lineTo(px(d), py(a)) } else { line.lineTo(px(d), py(a)); fill.lineTo(px(d), py(a)) } }
        fill.lineTo(px(profilePts.last().first), box.bottom); fill.close()
        val cur = if (s.distanceM.isNaN()) 0f else s.distanceM
        val xNow = px(cur)
        c.drawPath(fill, paint().apply { shader = LinearGradient(0f, box.top, 0f, box.bottom, 0x40FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP) })
        // Il tratto già fatto si colora.
        c.save(); c.clipRect(box.left, r.top, xNow, r.bottom)
        c.drawPath(fill, paint().apply { shader = LinearGradient(0f, box.top, 0f, box.bottom, withA(accent, 0.75f), withA(accent, 0.05f), Shader.TileMode.CLAMP) })
        c.restore()
        c.drawPath(line, paint().apply { style = Paint.Style.STROKE; strokeWidth = u * 0.35f; color = 0xCCFFFFFF.toInt(); strokeJoin = Paint.Join.ROUND })
        val yNow = if (s.altitude.isNaN()) box.bottom else py(s.altitude)
        c.drawLine(xNow, yNow, xNow, box.bottom, paint().apply { color = accent; strokeWidth = u * 0.3f })
        c.drawCircle(xNow, yNow, u * 1.6f, paint().apply { color = accent; maskFilter = BlurMaskFilter(u * 1.4f, BlurMaskFilter.Blur.NORMAL) })
        c.drawCircle(xNow, yNow, u * 0.8f, paint().apply { color = Color.WHITE })
    }

    private var mapKey = -1f
    private var mapPath = Path()
    private var mapProj: ((Double, Double) -> Pair<Float, Float>)? = null

    /** Mappa: il percorso intero in chiaro, il tratto fatto acceso, il punto con l'onda che pulsa. */
    private fun map(c: Canvas, r: RectF, s: TeleSample, u: Float, nowMs: Long) {
        val round = t.style == TeleStyle.HUD || t.style == TeleStyle.NEON
        panel(c, r, u, round)
        if (!track.hasGps) { label(c, "Niente GPS", r.centerX(), r.centerY(), u, align = Paint.Align.CENTER); return }
        val pad = r.width() * (if (round) 0.2f else 0.11f)
        val box = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        if (mapKey != r.width()) {
            mapKey = r.width()
            var la0 = 90.0; var la1 = -90.0; var lo0 = 180.0; var lo1 = -180.0
            for (i in 0 until track.size) if (!track.lat[i].isNaN()) { la0 = min(la0, track.lat[i]); la1 = max(la1, track.lat[i]); lo0 = min(lo0, track.lon[i]); lo1 = max(lo1, track.lon[i]) }
            val kx = cos(Math.toRadians((la0 + la1) / 2))
            val w = max((lo1 - lo0) * kx, 1e-6); val h = max(la1 - la0, 1e-6)
            val sc = min(box.width() / w, box.height() / h)
            val ox = box.centerX() - (w * sc / 2).toFloat(); val oy = box.centerY() + (h * sc / 2).toFloat()
            val proj = { la: Double, lo: Double -> (ox + ((lo - lo0) * kx * sc).toFloat()) to (oy - ((la - la0) * sc).toFloat()) }
            mapProj = proj
            mapPath = Path().apply {
                var first = true
                for (i in 0 until track.size step step) if (!track.lat[i].isNaN()) { val (x, y) = proj(track.lat[i], track.lon[i]); if (first) moveTo(x, y) else lineTo(x, y); first = false }
            }
        }
        val proj = mapProj ?: return
        val p = paint().apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        p.color = 0x59FFFFFF; p.strokeWidth = u * 0.55f
        c.drawPath(mapPath, p)
        // Tratto fatto fino a qui.
        val done = Path()
        val last = s.index.toInt().coerceIn(0, track.size - 1)
        var first = true
        var i = 0
        while (i <= last) {
            if (!track.lat[i].isNaN()) { val (x, y) = proj(track.lat[i], track.lon[i]); if (first) done.moveTo(x, y) else done.lineTo(x, y); first = false }
            i += step
        }
        if (s.lat.isNaN()) return
        val (cx, cy) = proj(s.lat, s.lon)
        if (!first) done.lineTo(cx, cy)
        p.color = accent; p.strokeWidth = u * 0.95f; p.setShadowLayer(u * 1.2f, 0f, 0f, accent)
        c.drawPath(done, p)
        p.clearShadowLayer()
        // Partenza.
        if (!track.lat[0].isNaN()) { val (sx, sy) = proj(track.lat[0], track.lon[0]); c.drawCircle(sx, sy, u * 0.8f, paint().apply { color = 0xCCFFFFFF.toInt() }) }
        val ph = (nowMs % 1400L) / 1400f
        c.drawCircle(cx, cy, u * (1.2f + 4f * ph), paint().apply { style = Paint.Style.STROKE; strokeWidth = u * 0.4f; color = withA(accent, 1f - ph) })
        c.drawCircle(cx, cy, u * 1.5f, paint().apply { color = accent; maskFilter = BlurMaskFilter(u * 1.2f, BlurMaskFilter.Blur.NORMAL) })
        c.drawCircle(cx, cy, u * 0.95f, paint().apply { color = Color.WHITE })
    }

    private fun distance(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        panel(c, r, u)
        val pad = u * 2.4f
        label(c, "Distanza", r.left + pad, r.top + u * 3f, u, 0xB3FFFFFF.toInt())
        val big = r.height() * 0.45f
        val km = s.distanceM / 1000f
        val wv = text(c, fmt(km, if (km < 100) 1 else 0), r.left + pad, r.top + u * 3.2f + big * 0.9f, big)
        label(c, "km", r.left + pad + wv + u * 0.6f, r.top + u * 3.2f + big * 0.9f, u)
        label(c, "di ${fmt(track.totalDistanceM / 1000f, 1)}", r.right - pad, r.top + u * 3f, u, align = Paint.Align.RIGHT)
        val y = r.bottom - u * 1.9f
        val k = if (s.distanceM.isNaN()) 0f else (s.distanceM / max(1f, track.totalDistanceM)).coerceIn(0f, 1f)
        c.drawRoundRect(RectF(r.left + pad, y - u * 0.4f, r.right - pad, y + u * 0.4f), u, u, paint().apply { color = 0x33FFFFFF })
        c.drawRoundRect(RectF(r.left + pad, y - u * 0.4f, r.left + pad + (r.width() - 2 * pad) * k, y + u * 0.4f), u, u,
            paint().apply { color = accent; setShadowLayer(u, 0f, 0f, accent) })
    }

    private fun time(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        panel(c, r, u)
        val pad = u * 2.4f
        val local = java.time.Instant.ofEpochMilli(s.timeMs).atZone(java.time.ZoneId.systemDefault())
        val big = r.height() * 0.5f
        text(c, String.format(Locale.ITALIAN, "%02d:%02d", local.hour, local.minute), r.left + pad, r.top + u * 1.6f + big * 0.92f, big)
        val el = ((s.timeMs - track.startMs) / 1000).coerceAtLeast(0)
        label(c, "Tempo", r.right - pad, r.top + u * 3f, u, 0xB3FFFFFF.toInt(), Paint.Align.RIGHT)
        text(c, String.format(Locale.ITALIAN, "%d:%02d:%02d", el / 3600, (el / 60) % 60, el % 60), r.right - pad, r.bottom - u * 2.2f, r.height() * 0.3f,
            accent, align = Paint.Align.RIGHT)
    }

    private fun temp(c: Canvas, r: RectF, s: TeleSample, u: Float) {
        panel(c, r, u)
        val pad = u * 2.2f
        label(c, "Temp.", r.left + pad, r.top + u * 3f, u, 0xB3FFFFFF.toInt())
        val big = r.height() * 0.5f
        text(c, fmt(s.temp) + "°", r.left + pad, r.top + u * 3.2f + big * 0.9f, big)
    }

    private fun frac(x: Float) = x - floor(x)
}

/**
 * Un indicatore come livello sopra al video (come i testi: si disegna anche sui video HDR).
 * Il tempo che arriva è quello del montaggio: diventa l'ora dell'attività passando per la clip, i tagli e la velocità.
 */
class TelemetryOverlay(
    private val renderer: TeleRenderer,
    private val t: Telemetry,
    private val track: TeleTrack,
    private val clips: List<Clip>,
    private val item: TeleItem,
    private val frameW: Int,
    private val frameH: Int,
    private val x: Int,
    private val y: Int,
) : androidx.media3.effect.TextOverlay() {
    override fun getText(presentationTimeUs: Long): android.text.SpannableString = android.text.SpannableString("dati")

    private val u = TeleLayoutCalc.unit(frameW, frameH, t, item)
    private val size = TeleLayoutCalc.sizePx(item.widget, t, item, frameW, frameH)
    // Due immagini che si alternano: il motore ricarica la texture solo se l'immagine è un'altra.
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private var flip = 0
    private var lastKey = Long.MIN_VALUE
    private var visible = false

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        // Al massimo 30 disegni al secondo.
        val key = presentationTimeUs / 33_333
        bitmaps[flip]?.let { if (key == lastKey) return it }
        lastKey = key
        val epoch = TeleSync.epochAt(t, clips, presentationTimeUs)
        val s = epoch?.let { track.sample(it) }
        visible = s != null
        flip = 1 - flip
        val b = bitmaps[flip] ?: Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888).also { bitmaps[flip] = it }
        val c = Canvas(b)
        if (s != null) renderer.draw(c, item.widget, item, s, u, presentationTimeUs / 1000) else c.drawColor(0, PorterDuff.Mode.CLEAR)
        return b
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        visible = TeleSync.epochAt(t, clips, presentationTimeUs)?.let { track.locate(it) } != null
        return StaticOverlaySettings.Builder()
            .setAlphaScale(if (visible) t.opacity.coerceIn(0f, 1f) else 0f)
            .setOverlayFrameAnchor(-1f, 1f)
            .setBackgroundFrameAnchor((2f * x / frameW - 1f).coerceIn(-1f, 1f), (1f - 2f * y / frameH).coerceIn(-1f, 1f))
            .build()
    }

    companion object {
        /** Gli indicatori del progetto (vuoto se non ci sono dati o il file non si legge). */
        fun all(context: Context, t: Telemetry?, clips: List<Clip>, frameW: Int, frameH: Int): List<TelemetryOverlay> {
            if (t == null || t.items.isEmpty()) return emptyList()
            val track = TeleStore.load(t.file) ?: return emptyList()
            val renderer = TeleRenderer(context, t, track)
            val pos = TeleLayoutCalc.positions(t, frameW, frameH)
            return t.items.mapIndexed { i, it -> TelemetryOverlay(renderer, t, track, clips, it, frameW, frameH, pos[i].first, pos[i].second) }
        }
    }
}
