package it.fabriziodavi.vivovideo.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.CollageAspect
import it.fabriziodavi.vivovideo.model.CollageBg
import it.fabriziodavi.vivovideo.model.CollageItem
import it.fabriziodavi.vivovideo.model.CollageMotion
import it.fabriziodavi.vivovideo.model.CollageSpec
import it.fabriziodavi.vivovideo.model.CollageStyle
import it.fabriziodavi.vivovideo.model.CollageTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Il disegno del collage: foto al loro posto, spazio fra le foto, forme, polaroid, movimento. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CollageRenderTest {
    private val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW)
    private val photos = colors.map { c -> Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(c) } }
    private val frames = CollageRenderer.Frames { i, _ -> photos.getOrNull(i) }

    private fun spec(id: String, n: Int, style: CollageStyle = CollageStyle(), motion: CollageMotion = CollageMotion.STILL, aspect: CollageAspect = CollageAspect.STORY) =
        CollageSpec(id, aspect, (0 until n).map { CollageItem("x$it", width = 40, height = 30) }, style, motion, kenBurns = false)

    private fun render(s: CollageSpec, w: Int = 180, h: Int = 320, tUs: Long = s.durationUs, still: Boolean = true): Bitmap {
        val t = CollageTemplates.byId(s.templateId, s.aspect)!!
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { CollageRenderer.draw(Canvas(it), w, h, t, s, tUs, still, frames, null) }
    }

    private fun near(a: Int, b: Int, tol: Int = 40) =
        abs(Color.red(a) - Color.red(b)) <= tol && abs(Color.green(a) - Color.green(b)) <= tol && abs(Color.blue(a) - Color.blue(b)) <= tol

    @Test fun photosGoInTheirCellsWithTheGapBetween() {
        // Due foto una sopra l'altra, spazio di 20 su 1000 del lato corto, fondo bianco caldo.
        val b = render(spec("g-r-11", 2, CollageStyle(gap = 0.02f, bg = CollageBg.CLOUD)))
        assertTrue(near(b.getPixel(90, 80), Color.RED))
        assertTrue(near(b.getPixel(90, 240), Color.BLUE))
        val cloud = CollageBg.CLOUD.color.toInt()
        assertTrue("spazio in mezzo", near(b.getPixel(90, 160), cloud, 10))
        assertTrue("margine sul bordo", near(b.getPixel(1, 80), cloud, 10))
        // Senza spazio le foto si toccano.
        val c = render(spec("g-r-11", 2, CollageStyle(gap = 0f)))
        assertTrue(near(c.getPixel(90, 158), Color.RED) && near(c.getPixel(90, 162), Color.BLUE))
        // Meno foto che celle: si ripetono.
        val r = render(spec("g-r-111", 2, CollageStyle(gap = 0f)))
        assertTrue(near(r.getPixel(90, 280), Color.RED))
    }

    @Test fun shapesAndPolaroidsAreDrawn() {
        // Cerchio su fondo burro: al centro la foto, negli angoli il fondo.
        val c = render(spec("s-circle-1", 1, CollageStyle(bg = CollageBg.BUTTER)))
        assertTrue(near(c.getPixel(90, 160), Color.RED))
        assertTrue(near(c.getPixel(4, 4), CollageBg.BUTTER.color.toInt(), 12))
        // Polaroid: sotto la foto c'è la cornice bianca.
        val t = CollageTemplates.byId("p-dump-1", CollageAspect.STORY)!!
        val cell = t.cells[0]
        val p = render(spec("p-dump-1", 1, t.style.copy(grain = false), aspect = CollageAspect.STORY))
        val cx = (cell.cx * 180).toInt()
        assertTrue(near(p.getPixel(cx, (cell.cy * 320).toInt()), Color.RED))
        val under = ((cell.y + cell.h) * 320 + cell.w * 180 * 0.12f).toInt()
        assertTrue("cornice: ${Integer.toHexString(p.getPixel(cx, under))}", near(p.getPixel(cx, under), 0xFFFBFAF7.toInt(), 30))
    }

    @Test fun captionAndGrainChangeThePicture() {
        val s = spec("e-frame-1", 1, CollageStyle(gap = 0f, bg = CollageBg.CLOUD))
        val plain = render(s)
        val withText = render(s.copy(caption = "Estate 2026"))
        val t = CollageTemplates.byId("e-frame-1", CollageAspect.STORY)!!
        val y = (t.captionY * 320).toInt()
        assertTrue("la scritta c'è", (0 until 180).any { x -> !near(withText.getPixel(x, y), plain.getPixel(x, y), 30) })
        val grain = render(s.copy(style = s.style.copy(grain = true)))
        assertTrue("grana", (0 until 180).count { x -> grain.getPixel(x, 5) != plain.getPixel(x, 5) } > 30)
    }

    @Test fun motionStartsEmptyAndEndsLikeTheImage() {
        val s = spec("g-r-22", 4, CollageStyle(gap = 0.02f, bg = CollageBg.BLACK), CollageMotion.CASCADE)
        val start = render(s, tUs = 0, still = false)
        val end = render(s, tUs = s.durationUs, still = false)
        val image = render(s)
        // All'inizio la quarta foto non c'è ancora; alla fine il video è uguale all'immagine.
        assertTrue(near(start.getPixel(135, 240), CollageBg.BLACK.color.toInt(), 10))
        var diff = 0
        for (y in 0 until 320 step 4) for (x in 0 until 180 step 4) if (!near(end.getPixel(x, y), image.getPixel(x, y), 6)) diff++
        assertEquals(0, diff)
        // Dal primo piano: all'inizio la prima foto riempie tutto il quadro.
        val z = render(s.copy(motion = CollageMotion.ZOOM), tUs = 0, still = false)
        assertTrue(listOf(10 to 10, 170 to 10, 90 to 160, 10 to 300, 170 to 300).all { (x, y) -> near(z.getPixel(x, y), Color.RED) })
    }

    @Test fun tappingFindsTheCell() {
        val s = spec("g-r-22", 4)
        val t = CollageTemplates.byId("g-r-22", CollageAspect.STORY)!!
        assertEquals(0, CollageRenderer.cellAt(t, s, 180, 320, 30f, 30f))
        assertEquals(3, CollageRenderer.cellAt(t, s, 180, 320, 150f, 300f))
        // Nello spazio fra le foto non c'è nessuna cella.
        assertEquals(-1, CollageRenderer.cellAt(t, s, 180, 320, 90f, 160f))
    }
}
