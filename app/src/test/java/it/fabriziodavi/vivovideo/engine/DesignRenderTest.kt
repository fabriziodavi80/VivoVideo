package it.fabriziodavi.vivovideo.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.CollageAspect
import it.fabriziodavi.vivovideo.model.CollageItem
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.CollageTemplate
import it.fabriziodavi.vivovideo.model.CollageTemplates
import it.fabriziodavi.vivovideo.model.DLayout
import it.fabriziodavi.vivovideo.model.DOrient
import it.fabriziodavi.vivovideo.model.DText
import it.fabriziodavi.vivovideo.model.Designs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * I modelli grafici disegnati davvero (foto finte a sfumature con una "persona" al centro): ogni modello finisce
 * anche come immagine nella cartella delle schermate, per controllarli a occhio dopo la compilazione.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DesignRenderTest {
    private val hues = listOf(
        0xFF2E86AB to 0xFFF6C85F, 0xFFE4572E to 0xFF29335C, 0xFF76B041 to 0xFF17BEBB, 0xFFFFC914 to 0xFF2E282A,
        0xFF9B5DE5 to 0xFFF15BB5, 0xFF00BBF9 to 0xFF00F5D4, 0xFFFF6B6B to 0xFF4ECDC4, 0xFF5F0F40 to 0xFFFB8B24,
    )

    /** Una foto finta 600×800: cielo sfumato e una sagoma scura al centro. */
    private val photos = hues.map { (a, b) ->
        Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { bm ->
            val c = Canvas(bm)
            c.drawRect(0f, 0f, 600f, 800f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 800f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1B.toInt() }
            c.drawCircle(300f, 190f, 95f, p)
            c.drawRoundRect(RectF(170f, 280f, 430f, 800f), 120f, 120f, p)
        }
    }

    /** Maschera della "persona": la sagoma della foto finta. */
    private val mask = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { bm ->
        val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(300f, 190f, 95f, p)
        c.drawRoundRect(RectF(170f, 280f, 430f, 800f), 120f, 120f, p)
    }

    private val frames = object : CollageRenderer.Frames {
        override fun get(item: Int, tUs: Long): Bitmap? = photos.getOrNull(item % photos.size)
        override fun mask(item: Int): Bitmap? = mask
    }

    @Before fun fonts() = DesignFonts.init(ApplicationProvider.getApplicationContext())

    private fun aspectFor(o: DOrient) = when (o) { DOrient.PORTRAIT -> CollageAspect.PAPER; DOrient.SQUARE -> CollageAspect.SQUARE; DOrient.LANDSCAPE -> CollageAspect.PHOTO_LAND }

    private fun spec(t: CollageTemplate, a: CollageAspect, n: Int = 8) =
        CollageSpec(t.id, a, (0 until n).map { CollageItem("foto$it", width = 600, height = 800) }, t.style, t.motion, kenBurns = false)

    private fun render(t: CollageTemplate, s: CollageSpec, w: Int, tUs: Long = s.durationUs, still: Boolean = true): Bitmap {
        val h = (w / s.aspect.ratio).toInt()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { CollageRenderer.draw(Canvas(it), w, h, t, s, tUs, still, frames, null) }
    }

    private fun save(b: Bitmap, name: String) {
        val dir = File("build/outputs/roborazzi").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun near(a: Int, b: Int, tol: Int = 40) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

    @Test fun everyDesignRendersAndIsSavedForReview() {
        Designs.all.forEachIndexed { i, d ->
            val a = aspectFor(d.orient)
            val t = CollageTemplates.byId(d.id, a)!!
            val s = spec(t, a)
            val b = render(t, s, 720)
            // Non è un foglio vuoto: tanti colori diversi.
            val seen = HashSet<Int>()
            for (y in 0 until b.height step 9) for (x in 0 until b.width step 9) seen += b.getPixel(x, y) and 0xF0F0F0
            assertTrue("${d.id}: troppo uniforme (${seen.size} colori)", seen.size > 40)
            save(b, "design_%02d_%s".format(i + 1, d.id))
            // Nel video, a metà dell'entrata (per controllare il movimento a occhio).
            if (d.id in setOf("x-cover", "x-bento-ride", "x-route", "x-letters")) save(render(t, s, 480, 1_300_000, still = false), "design_%02d_%s_video".format(i + 1, d.id))
        }
        // I verticali anche in 9:16 e 10×15: si adattano senza sbordare.
        for (id in listOf("x-cover", "x-sport", "x-year", "x-scrap")) for (a in listOf(CollageAspect.STORY, CollageAspect.PHOTO)) {
            val t = CollageTemplates.byId(id, a)!!
            save(render(t, spec(t, a), 540), "design_formato_${id}_${a.name.lowercase()}")
        }
    }

    @Test fun textsColorsAndBehind() {
        val a = CollageAspect.PAPER
        val t = CollageTemplates.byId("x-cover", a)!!
        val d = t.design!!
        val s = spec(t, a, 1)
        val title = d.els.first { it is DText && it.id == "title" } as DText
        val r = DLayout.rect(d, title.box, 794f, 794f / a.ratio)
        val plain = render(t, s, 794)
        // Scritta cambiata: cambia la zona del titolo.
        val other = render(t, s.copy(texts = mapOf("title" to "MARE")), 794)
        var diff = 0
        for (y in r[1].toInt() until (r[1] + r[3]).toInt() step 4) for (x in 0 until 794 step 4) if (!near(plain.getPixel(x, y), other.getPixel(x, y), 10)) diff++
        assertTrue("il titolo è cambiato ($diff)", diff > 200)
        // Colore del titolo: giallo invece di bianco.
        val yellow = render(t, s.copy(accent = 0xFFFFD23F), 794)
        var found = false
        for (y in r[1].toInt() until (r[1] + r[3]).toInt() step 3) for (x in 0 until 794 step 3) if (near(yellow.getPixel(x, y), 0xFFFFD23F.toInt(), 30)) found = true
        assertTrue("titolo giallo", found)
        // La persona davanti al titolo: dove la sagoma copre le lettere, con "dietro" si vede la sagoma (scura), senza il titolo (bianco).
        val behindOff = render(t, s.copy(behind = false), 794)
        var covered = 0; var shown = 0
        for (y in r[1].toInt() until (r[1] + r[3]).toInt() step 2) for (x in 300 until 500 step 2) {
            val off = behindOff.getPixel(x, y); val on = plain.getPixel(x, y)
            if (near(off, Color.WHITE, 30)) { shown++; if (near(on, 0xFF1B1B1B.toInt(), 50)) covered++ }
        }
        assertTrue("persona davanti: $covered su $shown", shown > 50 && covered > shown / 3)
    }

    @Test fun duotonePaletteAndPhotoLetters() {
        // Duotono: nella foto ci sono solo sfumature fra i due colori del modello.
        val a = CollageAspect.PAPER
        val t = CollageTemplates.byId("x-duo", a)!!
        val b = render(t, spec(t, a), 567)
        val px = b.getPixel(283, 300)
        val light = 0xFFFF6A3D.toInt(); val dark = 0xFF10206B.toInt()
        // Il punto sta sulla retta fra i due colori (entro una tolleranza).
        val k = ((Color.red(px) - Color.red(dark)).toFloat() / (Color.red(light) - Color.red(dark))).coerceIn(0f, 1f)
        val expect = Color.rgb((Color.red(dark) + k * (Color.red(light) - Color.red(dark))).toInt(), (Color.green(dark) + k * (Color.green(light) - Color.green(dark))).toInt(),
            (Color.blue(dark) + k * (Color.blue(light) - Color.blue(dark))).toInt())
        assertTrue("duotono ${Integer.toHexString(px)} vs ${Integer.toHexString(expect)}", near(px, expect, 40))
        // Palette: cinque colori presi dalle foto, dal più chiaro al più scuro.
        val pal = DesignRenderer.palette(photos, 5)
        assertEquals(5, pal.size)
        val lum = pal.map { 0.299 * Color.red(it) + 0.587 * Color.green(it) + 0.114 * Color.blue(it) }
        assertTrue("ordinata $lum", lum.zipWithNext().all { (x, y) -> x >= y })
        assertTrue("c'è lo scuro della sagoma", pal.any { near(it, 0xFF1B1B1B.toInt(), 45) })
        // Parola con le foto: dentro le lettere c'è la foto, fuori il fondo.
        val tl = CollageTemplates.byId("x-letters", a)!!
        val wb = render(tl, spec(tl, a), 567)
        var inPhoto = 0
        for (y in 120 until 700 step 5) for (x in 30 until 540 step 5) if (!near(wb.getPixel(x, y), 0xFFF2F4F7.toInt(), 12)) inPhoto++
        assertTrue("lettere piene di foto: $inPhoto", inPhoto > 1500)
    }

    @Test fun animationStartsEmptyAndEndsLikeTheImage() {
        val a = CollageAspect.SQUARE
        val t = CollageTemplates.byId("x-bento-ride", a)!!
        val s = spec(t, a)
        val end = render(t, s, 400, s.durationUs, still = false)
        val image = render(t, s, 400)
        var diff = 0
        for (y in 0 until 400 step 4) for (x in 0 until 400 step 4) if (!near(end.getPixel(x, y), image.getPixel(x, y), 8)) diff++
        assertEquals("alla fine il video è uguale all'immagine", 0, diff)
        // All'inizio le scritte non ci sono ancora: il riquadro arancione dei km è vuoto.
        val start = render(t, s, 400, 0, still = false)
        val d = t.design!!
        val km = DLayout.rect(d, (d.els.first { it is DText && it.id == "km" } as DText).box, 400f, 400f)
        var dark = 0
        for (y in km[1].toInt() until (km[1] + km[3]).toInt()) for (x in km[0].toInt() until (km[0] + km[2]).toInt()) if (near(start.getPixel(x, y), 0xFF121212.toInt(), 30)) dark++
        assertEquals(0, dark)
    }
}
