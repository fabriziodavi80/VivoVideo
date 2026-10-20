package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import it.fabriziodavi.vivovideo.model.Tutorial
import java.io.File

/** Disegna le evidenziazioni per i tutorial come PNG trasparenti (stile pulito: ambra e bianco, ombra morbida). */
object TutorialMarks {
    private const val AMBER = 0xFFF2B33D.toInt()
    private const val INK = 0xFF1A1300.toInt()

    /** File PNG dell'evidenziazione (rifatto solo se manca) con le sue misure. */
    fun file(ctx: Context, mark: Tutorial.Mark, number: Int = 0): Triple<File, Int, Int> {
        val (w, h) = if (mark == Tutorial.Mark.BOX) 640 to 400 else 512 to 512
        val dir = File(ctx.filesDir, "tutorial").apply { mkdirs() }
        val f = File(dir, "${mark.name.lowercase()}${if (mark == Tutorial.Mark.NUMBER) "_$number" else ""}_v1.png")
        if (!f.exists()) {
            val bmp = draw(mark, number, w, h)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
        return Triple(f, w, h)
    }

    fun draw(mark: Tutorial.Mark, number: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shadow = 0x66000000
        when (mark) {
            Tutorial.Mark.ARROW -> {
                // Freccia che punta in alto a sinistra: punta larga, gambo deciso.
                val p = Path().apply {
                    moveTo(w * 0.10f, h * 0.10f)
                    lineTo(w * 0.52f, h * 0.18f)
                    lineTo(w * 0.40f, h * 0.30f)
                    lineTo(w * 0.86f, h * 0.76f)
                    lineTo(w * 0.76f, h * 0.86f)
                    lineTo(w * 0.30f, h * 0.40f)
                    lineTo(w * 0.18f, h * 0.52f)
                    close()
                }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL; setShadowLayer(18f, 0f, 6f, shadow) }
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 10f; strokeJoin = Paint.Join.ROUND }
                c.drawPath(p, fill); c.drawPath(p, edge)
            }
            Tutorial.Mark.CIRCLE -> {
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; style = Paint.Style.STROKE; strokeWidth = 30f; setShadowLayer(16f, 0f, 4f, shadow) }
                c.drawOval(RectF(40f, 40f, w - 40f, h - 40f), ring)
            }
            Tutorial.Mark.BOX -> {
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; style = Paint.Style.STROKE; strokeWidth = 24f; setShadowLayer(16f, 0f, 4f, shadow) }
                c.drawRoundRect(RectF(36f, 36f, w - 36f, h - 36f), 40f, 40f, ring)
            }
            Tutorial.Mark.NUMBER -> {
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER; setShadowLayer(18f, 0f, 6f, shadow) }
                c.drawCircle(w / 2f, h / 2f, w * 0.42f, dot)
                val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = INK; textAlign = Paint.Align.CENTER; textSize = h * (if (number >= 10) 0.40f else 0.50f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val y = h / 2f - (txt.descent() + txt.ascent()) / 2f
                c.drawText(number.coerceAtLeast(1).toString(), w / 2f, y, txt)
            }
        }
        return bmp
    }
}
