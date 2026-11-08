package it.fabriziodavi.vivovideo.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.media3.effect.StaticOverlaySettings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.KineticStyle
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.Subtitles
import it.fabriziodavi.vivovideo.model.TeleItem
import it.fabriziodavi.vivovideo.model.TeleLayout
import it.fabriziodavi.vivovideo.model.TeleLayoutCalc
import it.fabriziodavi.vivovideo.model.TeleParser
import it.fabriziodavi.vivovideo.model.TeleSlot
import it.fabriziodavi.vivovideo.model.TeleStyle
import it.fabriziodavi.vivovideo.model.TeleWidget
import it.fabriziodavi.vivovideo.model.Telemetry
import it.fabriziodavi.vivovideo.model.TextFont
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dati della pedalata (indicatori disegnati in tutti gli stili), testi cinetici, testo dietro la persona e datamosh.
 * Le immagini finiscono fra le schermate (24_dati_pedalata, 25_testi_cinetici) per controllarle a occhio.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SportAndTextFxTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val t0 = 1_793_527_200_000L

    /** Un giro finto di 10 minuti: anello con una salita e una discesa, battito, potenza, cadenza. */
    private fun gpx(): ByteArray {
        val pts = (0 until 600).joinToString("") { i ->
            val a = i / 600.0 * 2 * Math.PI
            val lat = 45.8 + 0.01 * sin(a) + 0.003 * sin(3 * a)
            val lon = 9.2 + 0.014 * cos(a)
            val ele = 400 + 120 * sin(a) + 15 * sin(5 * a)
            val time = java.time.Instant.ofEpochMilli(t0 + i * 1000L).toString()
            val hr = 120 + (50 * (0.5 + 0.5 * sin(a))).toInt()
            "<trkpt lat=\"$lat\" lon=\"$lon\"><ele>$ele</ele><time>$time</time><extensions><gpxtpx:hr>$hr</gpxtpx:hr><gpxtpx:cad>${80 + i % 15}</gpxtpx:cad><power>${180 + (140 * (0.5 + 0.5 * sin(a))).toInt()}</power><gpxtpx:atemp>17</gpxtpx:atemp></extensions></trkpt>"
        }
        return "<gpx><trk><trkseg>$pts</trkseg></trk></gpx>".toByteArray()
    }

    private fun save(b: Bitmap, name: String) {
        val f = File("build/outputs/roborazzi/$name").apply { parentFile?.mkdirs() }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Sfondo "da video": cielo, prato, una strada. */
    private fun scene(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.45f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0xFF6FA8DC.toInt(), 0xFFCFE2F3.toInt(), Shader.TileMode.CLAMP) })
        c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), 0xFF6B8E23.toInt(), 0xFF2E3B12.toInt(), Shader.TileMode.CLAMP) })
        c.drawRect(w * 0.42f, h * 0.45f, w * 0.58f, h.toFloat(), Paint().apply { color = 0xFF55504A.toInt() })
        return b
    }

    @Test fun telemetryWidgetsInEveryStyle() {
        val track = TeleParser.parse(gpx())!!
        // Il momento più veloce del giro (per vedere scie e badge MAX).
        var best = 0
        for (i in 0 until track.size) if (track.speedKmh[i] > track.speedKmh[best]) best = i
        val s = track.sample(track.timeMs[best])!!
        val u = 8f
        val sheetW = 1080
        // Prima si disegnano tutti, poi si dispongono a righe sotto ogni stile.
        val rendered = TeleStyle.entries.mapIndexed { row, st ->
            val t = Telemetry(file = "x", name = "giro", style = st, accent = Telemetry.ACCENTS[row % Telemetry.ACCENTS.size])
            val r = TeleRenderer(ctx, t, track)
            st to TeleWidget.entries.map { w ->
                val (a, bb) = TeleLayoutCalc.sizeU(w, st)
                val one = Bitmap.createBitmap((a * u).toInt(), (bb * u).toInt(), Bitmap.Config.ARGB_8888)
                r.draw(Canvas(one), w, TeleItem(w, TeleSlot.TOP_LEFT), s, u, 400)
                var any = false
                for (py in 0 until one.height step 4) { for (px in 0 until one.width step 4) if (Color.alpha(one.getPixel(px, py)) > 0) { any = true; break }; if (any) break }
                assertTrue("$st $w vuoto", any)
                one
            }
        }
        val places = ArrayList<Triple<Bitmap, Float, Float>>()
        val labels = ArrayList<Pair<String, Float>>()
        var y = 10f
        rendered.forEach { (st, bitmaps) ->
            labels += st.label to y + 24f
            y += 34f
            var x = 10f; var line = 0f
            bitmaps.forEach { b ->
                if (x + b.width > sheetW) { x = 10f; y += line + 10f; line = 0f }
                places += Triple(b, x, y)
                x += b.width + 10f; line = maxOf(line, b.height.toFloat())
            }
            y += line + 24f
        }
        val sheet = scene(sheetW, y.toInt() + 10)
        val c = Canvas(sheet)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f; setShadowLayer(4f, 0f, 1f, Color.BLACK) }
        labels.forEach { (l, ly) -> c.drawText(l, 12f, ly, lp) }
        places.forEach { (b, x, yy) -> c.drawBitmap(b, x, yy, null) }
        save(sheet, "24_dati_pedalata.png")
    }

    @Test fun telemetryOverlaysOnAFrame() {
        val dir = File(ctx.filesDir, "telemetry").apply { mkdirs() }
        val f = File(dir, "prova.gpx").apply { writeBytes(gpx()) }
        val clip = Clip(uri = "content://v/1", sourceDurationUs = 30_000_000, width = 1080, height = 1920)
        for ((name, layout, style) in listOf(Triple("26_dati_verticale.png", TeleLayout.FULL, TeleStyle.GLASS), Triple("27_dati_gara.png", TeleLayout.ACTION, TeleStyle.RACE))) {
            val t = Telemetry(file = f.absolutePath, name = "giro", clipStartMs = mapOf(clip.uri to t0 + 200_000), items = layout.items, style = style)
            val ov = TelemetryOverlay.all(ctx, t, listOf(clip), 1080, 1920)
            assertEquals(layout.items.size, ov.size)
            val frame = scene(1080, 1920)
            val c = Canvas(frame)
            val pos = TeleLayoutCalc.positions(t, 1080, 1920)
            ov.forEachIndexed { i, o ->
                val b = o.getBitmap(5_000_000)
                val st = o.getOverlaySettings(5_000_000)
                assertEquals(1f, st.alphaScale, 0f)
                c.drawBitmap(b, pos[i].first.toFloat(), pos[i].second.toFloat(), null)
            }
            save(frame, name)
            // Fuori dall'attività: invisibili.
            val far = TelemetryOverlay.all(ctx, t.copy(clipStartMs = mapOf(clip.uri to t0 + 9_000_000)), listOf(clip), 1080, 1920)
            far[0].getBitmap(1_000_000)
            assertEquals(0f, far[0].getOverlaySettings(1_000_000).alphaScale, 0f)
        }
        assertTrue(TelemetryOverlay.all(ctx, null, listOf(clip), 1080, 1920).isEmpty())
        assertTrue(TelemetryOverlay.all(ctx, Telemetry(file = "/non/esiste", name = "x"), listOf(clip), 1080, 1920).isEmpty())
    }

    @Test fun kineticTextsRenderAndMove() {
        val styles = KineticStyle.entries
        val sheet = scene(1080, 260 * styles.size)
        val c = Canvas(sheet)
        styles.forEachIndexed { row, st ->
            val item = TextItem(text = "Che salita incredibile oggi", startUs = 0, durationUs = 3_000_000, size = 0.075f, font = TextFont.POSTER,
                look = TextLook.CONTORNO, kinetic = st, subtitle = true, wordsUs = listOf(0, 300_000, 600_000, 900_000), highlight = Subtitles.KINETIC_HIGHLIGHT)
            // Parola importante ("incredibile") 100 ms dopo che è detta.
            val b = KineticText.bitmap(ctx, item, 1080, 1920, 700_000)
            assertNotNull("$st", b)
            c.drawBitmap(b!!, (1080 - b.width) / 2f, row * 260f + (260 - b.height) / 2f, null)
            c.drawText(st.label, 12f, row * 260f + 250f, Paint().apply { color = Color.WHITE; textSize = 22f })
            // Prima della prima parola: invisibile; poi entra e si assesta.
            val o = TimedTextOverlay(ctx, item.copy(startUs = 1_000_000, wordsUs = listOf(200_000, 500_000)), 1080, 1920)
            assertEquals(0f, o.getOverlaySettings(1_100_000).alphaScale, 0f)
            assertEquals(1f, o.getOverlaySettings(1_900_000).alphaScale, 0.01f)
            val m = KineticText.motion(st, key = false, index = 0, since = 1_000_000, local = 1_200_000, duration = 3_000_000)
            assertEquals("$st si assesta", 1f, m.scale, 0.02f)
        }
        save(sheet, "25_testi_cinetici.png")
        // Pop: la parola importante è più grande e inclinata.
        assertTrue(KineticText.motion(KineticStyle.POP, true, 1, 500_000, 1_000_000, 3_000_000).let { it.scale > 1.05f && it.rotation != 0f })
        // Testo scritto a mano: una parola ogni 0,35 s.
        val hand = TextItem(text = "Si parte", durationUs = 2_000_000, kinetic = KineticStyle.POP)
        assertEquals(1 to 50_000L, KineticText.current(hand, 400_000))
    }

    @Test fun textBehindPlacesLikeTheNormalOverlay() {
        val st = StaticOverlaySettings.Builder().setScale(2f, 2f).setBackgroundFrameAnchor(0.5f, -0.5f).setRotationDegrees(-90f).setAlphaScale(0.8f).build()
        val k = TextBehindEffect.place(st, 100, 40, 1000f, 2000f)
        assertEquals(750f, k[0], 0.01f) // x = (0,5 + 1) / 2 × 1000
        assertEquals(1500f, k[1], 0.01f) // y dall'alto
        assertEquals(100f, k[2], 0.01f); assertEquals(40f, k[3], 0.01f)
        assertEquals(0f, k[4], 1e-4f); assertEquals(1f, k[5], 1e-4f) // 90° in senso orario
        assertTrue(TextBehindEffect.FRAGMENT.contains("texture2D (t, uv)"))
    }

    @Test fun datamoshWindowsStartAtTheCut() {
        val a = Clip(uri = "a", sourceDurationUs = 3_000_000, transitionOut = TransitionSpec(TransitionType.DATAMOSH, 1_400_000))
        val b = Clip(uri = "b", sourceDurationUs = 1_000_000, transitionOut = TransitionSpec(TransitionType.DATAMOSH, 1_400_000))
        val c = Clip(uri = "c", sourceDurationUs = 4_000_000)
        val p = Project(name = "x", clips = listOf(a, b, c))
        val w = datamoshWindows(p, p.clipStartsUs())
        assertEquals(listOf(3_000_000L until 4_000_000L, 4_000_000L until 5_400_000L), w)
        val fx = DatamoshEffect(w)
        assertNull(fx.progress(2_000_000))
        assertEquals(0.5f, fx.progress(3_500_000)!!, 0.01f)
        assertTrue(TransitionType.DATAMOSH.defaultUs!! >= 1_000_000 && !TransitionType.DATAMOSH.composite && TransitionType.DATAMOSH.hint != null)
        assertTrue(datamoshWindows(Project(name = "y", clips = listOf(c)), listOf(0)).isEmpty())
    }
}
