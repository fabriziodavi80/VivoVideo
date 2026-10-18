package it.fabriziodavi.vivovideo.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.media3.effect.OverlayEffect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Grade
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.KeyColor
import it.fabriziodavi.vivovideo.model.Keyframe
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.LayerBorder
import it.fabriziodavi.vivovideo.model.LayerMask
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.SpeedPreset
import it.fabriziodavi.vivovideo.model.TextAnim
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.VideoFormat
import it.fabriziodavi.vivovideo.model.FxCategory
import it.fabriziodavi.vivovideo.model.TextFont
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Motore: costruzione del montaggio, testi, livelli, tempi degli effetti (ambiente Android simulato). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EngineTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val factory = CompositionFactory(app)

    private fun video(id: String, src: Long = 10_000_000, s: Long = 0, e: Long = src) =
        Clip(id = id, uri = "content://v/$id", sourceDurationUs = src, trimStartUs = s, trimEndUs = e, width = 1920, height = 1080)

    private fun photo(id: String) =
        Clip(id = id, uri = "file:///p/$id.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 2_000_000, isPhoto = true, width = 4000, height = 3000)

    private fun seqDuration(items: List<androidx.media3.transformer.EditedMediaItem>) = items.sumOf { it.presentationDurationUs }

    // ---------------- Montaggio ----------------

    @Test fun simpleProjectIsOneSequenceWithRightDuration() {
        val p = Project(name = "x", clips = listOf(video("a", e = 4_000_000), photo("p"), video("b").copy(speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.VALLEY.points)))
        val c = factory.build(p, ExportOptions(), forPreview = true, nativeAudio = null)
        assertEquals(1, c.sequences.size)
        val items = c.sequences[0].editedMediaItems
        assertEquals(3, items.size)
        // La durata che calcola Media3 coincide con quella della timeline.
        assertTrue("media3 ${seqDuration(items)} vs ${p.durationUs}", abs(seqDuration(items) - p.durationUs) < 2_000)
    }

    @Test fun musicAddsAudioSequencesAndNativeAudioReplacesThem() {
        val p = Project(name = "x", clips = listOf(video("a")), music = listOf(
            MusicTrack(id = "m1", uri = "content://m/1", name = "1", sourceDurationUs = 60_000_000, startUs = 0, lengthUs = 6_000_000),
            MusicTrack(id = "m2", uri = "content://m/2", name = "2", sourceDurationUs = 60_000_000, startUs = 3_000_000, lengthUs = 6_000_000),
        ))
        val prev = factory.build(p, ExportOptions(), forPreview = true, nativeAudio = null)
        assertEquals(3, prev.sequences.size) // video + due righe di musica (si sovrappongono)
        val exp = factory.build(p, ExportOptions(), forPreview = false, nativeAudio = File(app.cacheDir, "a.wav"))
        assertEquals(2, exp.sequences.size) // video + colonna sonora già mixata
    }

    @Test fun crossfadeExportUsesCompositorWithPaddedLane() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        val p = Project(
            name = "x",
            clips = listOf(video("a", e = 5_000_000).copy(transitionOut = x), video("b", s = 2_000_000, e = 6_000_000).copy(transitionOut = x), photo("c")),
            texts = listOf(TextItem(text = "Ciao")),
        )
        val c = factory.build(p, ExportOptions(), forPreview = false, nativeAudio = null)
        // tempi (invisibile) + dissolvenze + principale visibile
        assertEquals(3, c.sequences.size)
        assertTrue(c.videoCompositorSettings is LayerCompositorSettings)
        // Testi sopra a tutto: effetto dell'intera composizione.
        assertTrue(c.effects.videoEffects.any { it is OverlayEffect })
        // La riga delle dissolvenze dura almeno quanto il video (niente ripetizioni del lettore).
        assertTrue(seqDuration(c.sequences[1].editedMediaItems) >= p.durationUs)
        assertTrue(abs(seqDuration(c.sequences[0].editedMediaItems) - p.durationUs) < 2_000)
    }

    @Test fun withoutCompositorCrossfadeBecomesSingleTrack() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        val p = Project(name = "x", clips = listOf(video("a", e = 5_000_000).copy(transitionOut = x), video("b", s = 2_000_000)))
        val c = factory.build(p, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false)
        assertEquals(1, c.sequences.size)
    }

    @Test fun videoLayersMakeOneLanePerOverlap() {
        val l1 = Layer(id = "1", uri = "content://v/l1", width = 1920, height = 1080, isVideo = true, sourceDurationUs = 8_000_000, startUs = 0, durationUs = 4_000_000)
        val l2 = l1.copy(id = "2", startUs = 2_000_000)
        val p = Project(name = "x", clips = listOf(video("a")), layers = listOf(l1, l2))
        val c = factory.build(p, ExportOptions(), forPreview = false, nativeAudio = null)
        assertEquals(4, c.sequences.size) // tempi + 2 righe + principale
        c.sequences.drop(1).dropLast(1).forEach { s -> assertTrue(seqDuration(s.editedMediaItems) >= p.durationUs) }
    }

    @Test fun everyFeatureTogetherBuildsInBothModes() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 800_000)
        val p = Project(
            name = "tutto",
            clips = listOf(
                video("a", e = 6_000_000).copy(transitionOut = TransitionSpec(TransitionType.ZOOM), fx = FxType.entries.map { ClipFx(it) },
                    keyframes = listOf(Keyframe(0, Transform()), Keyframe(3_000_000, Transform(scale = 1.5f, opacity = 0.5f))), frame = Transform(rotation = 90f), log = true),
                video("b", s = 1_000_000, e = 7_000_000).copy(speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.BULLET.points, transitionOut = x, fill = true),
                photo("c").copy(transitionOut = x, keyframes = listOf(Keyframe(0, Transform()), Keyframe(2_000_000, Transform(scale = 1.2f)))),
                photo("d"),
            ),
            music = listOf(MusicTrack(uri = "content://m", name = "m", sourceDurationUs = 60_000_000, beatsUs = (0 until 99).map { it * 500_000L })),
            texts = TextAnim.entries.map { a -> TextItem(text = "T $a", animIn = a, startUs = 1_000_000) },
            layers = listOf(
                Layer(uri = "file:///x.png", width = 100, height = 100, mask = LayerMask.STAR, key = KeyColor.WHITE),
                Layer(uri = "content://v/l", width = 1920, height = 1080, isVideo = true, sourceDurationUs = 5_000_000, durationUs = 3_000_000, mask = LayerMask.HEART, border = LayerBorder.WHITE),
            ),
        )
        listOf(true, false).forEach { preview ->
            listOf(true, false).forEach { multi ->
                val c = factory.build(p, ExportOptions(), forPreview = preview, nativeAudio = null, allowMulti = multi)
                assertTrue(c.sequences.isNotEmpty())
            }
        }
    }

    // ---------------- Compositore ----------------

    @Test fun compositorAlphaForCrossfadeAndLayers() {
        val cf = Project.Crossfade(index = 0, cutUs = 5_000_000, beforeUs = 500_000, afterUs = 500_000)
        val layer = Layer(uri = "v", width = 100, height = 100, isVideo = true, startUs = 1_000_000, durationUs = 2_000_000, animIn = TextAnim.NONE, animOut = TextAnim.NONE)
        val s = LayerCompositorSettings(1080, 1920, listOf(listOf(layer)), listOf(cf))
        assertEquals(0f, s.getOverlaySettings(0, 0).alphaScale, 0f)                    // copia per i tempi: invisibile
        assertEquals(0f, s.getOverlaySettings(1, 500_000).alphaScale, 0f)              // livello non ancora iniziato
        assertEquals(1f, s.getOverlaySettings(1, 2_000_000).alphaScale, 1e-3f)         // livello visibile
        assertEquals(0f, s.getOverlaySettings(2, 4_000_000).alphaScale, 0f)            // prima della dissolvenza
        assertTrue(s.getOverlaySettings(2, 4_750_000).alphaScale in 0.1f..0.5f)         // la successiva compare
        assertEquals(0.5f, s.getOverlaySettings(2, 4_999_999).alphaScale, 0.02f)       // al taglio metà e metà
        assertEquals(0.5f, s.getOverlaySettings(2, 5_000_000).alphaScale, 0.02f)       // continua dopo il taglio
        assertTrue(s.getOverlaySettings(2, 5_250_000).alphaScale in 0.1f..0.5f)        // poi la precedente svanisce
        assertEquals(0f, s.getOverlaySettings(2, 6_000_000).alphaScale, 0f)
        assertEquals(1f, s.getOverlaySettings(3, 0).alphaScale, 0f)                    // principale visibile
        assertEquals(androidx.media3.common.util.Size(1080, 1920), s.getOutputSize(emptyList()))
    }

    // ---------------- Tempi degli effetti ----------------

    @Test fun localTimeIsClipRelativeAndClamped() {
        assertEquals(0L, localTimeUs(3_000_000, 5_000_000))
        assertEquals(1_000_000L, localTimeUs(6_000_000, 5_000_000))
    }

    @Test fun transitionPeaksAtTheCut() {
        val e = TransitionEffect(10_000_000, 4_000_000, TransitionType.FADE_BLACK, 500_000, TransitionType.FADE_BLACK, 500_000)
        assertEquals(1f, e.params(10_000_000).black, 1e-3f)          // inizio della clip = subito dopo il taglio
        assertEquals(0f, e.params(12_000_000).black, 1e-3f)          // a metà clip niente
        assertTrue(e.params(13_999_000).black > 0.95f)               // fine della clip
    }

    @Test fun pulseFollowsBeats() {
        val fx = FxEffect(listOf(ClipFx(FxType.PULSE)), listOf(1_000_000, 2_000_000))
        assertEquals(0f, fx.pulseAt(500_000), 0f)
        assertEquals(1f, fx.pulseAt(1_000_000), 1e-3f)
        assertTrue(fx.pulseAt(1_300_000) < 0.2f)
        assertEquals(0f, FxEffect(emptyList(), listOf(1_000_000)).pulseAt(1_000_000), 0f)
    }

    @Test fun everyEffectHasItsUniformInTheShader() {
        assertEquals(FxType.entries.toSet(), FX_UNIFORMS.keys + WowFxEffect.UNIFORMS.keys)
        assertTrue(FX_UNIFORMS.keys.none { it.wow2 } && WowFxEffect.UNIFORMS.keys.all { it.wow2 })
        FX_UNIFORMS.values.forEach { u -> assertTrue("$u manca nello shader", Regex("\\b$u\\b").findAll(FX_FRAGMENT).count() >= 2) }
        WowFxEffect.UNIFORMS.values.forEach { u -> assertTrue("$u manca nello shader", Regex("\\b$u\\b").findAll(WowFxEffect.FRAGMENT).count() >= 2) }
        assertEquals(FX_UNIFORMS.size, FX_UNIFORMS.values.toSet().size)
    }

    @Test fun clipFadesInAndOut() {
        val fx = FxEffect(emptyList(), emptyList(), clipStartUs = 10_000_000, clipOutputUs = 4_000_000, fadeInUs = 1_000_000, fadeOutUs = 2_000_000)
        assertEquals(1f, fx.fadeAt(10_000_000), 1e-3f)       // inizio: tutto nero
        assertEquals(0.5f, fx.fadeAt(10_500_000), 1e-3f)     // a metà dell'entrata
        assertEquals(0f, fx.fadeAt(11_500_000), 1e-3f)       // in mezzo: niente
        assertEquals(0.5f, fx.fadeAt(13_000_000), 1e-3f)     // a metà dell'uscita
        assertTrue(fx.fadeAt(13_999_000) > 0.99f)            // fine: tutto nero
        assertEquals(0f, FxEffect(emptyList(), emptyList()).fadeAt(5_000_000), 0f)
    }

    @Test fun strobeOnBeats() {
        val fx = FxEffect(listOf(ClipFx(FxType.STROBE)), listOf(1_000_000))
        assertEquals(1f, fx.strobeAt(1_000_000), 1e-3f)
        assertTrue(fx.strobeAt(1_150_000) < 0.1f)
        assertEquals(0f, fx.strobeAt(900_000), 0f)
    }

    @Test fun animationsStartAndEndInvisible() {
        TextAnim.entries.forEach { a ->
            val mid = animFactors(a, a, 1_000_000, 2_000_000)
            assertEquals("$a a metà", 1f, mid.alpha, 1e-3f)
            assertEquals(0f, animFactors(a, a, -1, 2_000_000).alpha, 0f)
            assertEquals(0f, animFactors(a, a, 2_000_000, 2_000_000).alpha, 0f)
        }
    }

    @Test fun formatsSetTheOutputSizeAndBlurredBackgroundReplacesTheBands() {
        val land = Clip(id = "l", uri = "content://l", sourceDurationUs = 4_000_000, width = 3840, height = 2160)
        val port = Clip(id = "p", uri = "content://p", sourceDurationUs = 4_000_000, width = 2160, height = 3840)
        val p = Project(name = "f", clips = listOf(land, port))
        assertEquals(3840 to 2160, factory.outputSize(p, null))
        assertEquals(2160 to 3840, factory.outputSize(p.copy(format = VideoFormat.VERTICAL), null))
        assertEquals(1080 to 1920, factory.outputSize(p.copy(format = VideoFormat.VERTICAL), 1080))
        assertEquals(1920 to 1080, factory.outputSize(p.copy(format = VideoFormat.HORIZONTAL), 1080))
        assertEquals(1080 to 1080, factory.outputSize(p.copy(format = VideoFormat.SQUARE), 1080))
        // Orizzontale in verticale: rettangolo centrato a tutta larghezza.
        val r = fitRect(1920, 1080, 1080, 1920)
        assertEquals(0f, r[0], 1e-4f); assertEquals(1f, r[2], 1e-4f)
        assertEquals(0.3164f, r[3], 1e-3f); assertEquals((1f - r[3]) / 2f, r[1], 1e-4f)
        // La clip con sfondo sfocato usa l'effetto apposito al posto di Presentation.
        val q = p.copy(format = VideoFormat.VERTICAL).applyFramePresets()
        assertTrue(q.clips[0].blurBg && q.clips[1].fill)
        val c = factory.build(q, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false)
        val effects = c.sequences.flatMap { s -> s.editedMediaItems.flatMap { it.effects.videoEffects } }
        assertTrue(effects.any { it is BlurFillEffect })
    }

    @Test fun clipGradeAddsItsShaderOnlyWhenNeeded() {
        val a = Clip(id = "a", uri = "content://a", sourceDurationUs = 3_000_000, width = 1920, height = 1080)
        fun hasGrade(p: Project) = factory.build(p, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false)
            .sequences.flatMap { s -> s.editedMediaItems.flatMap { it.effects.videoEffects } }.any { it is GradeEffect }
        assertTrue(!hasGrade(Project(name = "g", clips = listOf(a))))
        assertTrue(hasGrade(Project(name = "g", clips = listOf(a.copy(grade = Grade(contrast = 0.2f))))))
        listOf("uBasic", "uWb", "uWheelS", "uWheelM", "uWheelH", "uHslH0", "uHslH1", "uHslS0", "uHslS1", "uHslL0", "uHslL1", "uHslOn")
            .forEach { u -> assertTrue("$u nello shader", Regex("\\b$u\\b").findAll(GRADE_FRAGMENT).count() >= 2) }
    }

    @Test fun karaokeColorsTheSpokenWord() {
        fun amber(b: Bitmap): Int { var n = 0; for (y in 0 until b.height) for (x in 0 until b.width) { val c = b.getPixel(x, y)
            if (Color.alpha(c) > 200 && Color.red(c) > 200 && Color.green(c) in 140..210 && Color.blue(c) < 120) n++ }; return n }
        val t = TextItem(text = "che bella giornata", look = TextLook.SEMPLICE, wordsUs = listOf(0, 300_000, 600_000), wordMode = 1)
        assertEquals(0, amber(TextRenderer.render(app, t, 1080, 1920)))
        assertTrue(amber(TextRenderer.render(app, t, 1080, 1920, highlight = 4..8)) > 30)
        // Il livello del testo sceglie la parola giusta nel tempo.
        val ov = TimedTextOverlay(app, t, 1080, 1920)
        assertTrue(amber(ov.getBitmap(400_000)) > 30)
    }

    @Test fun wowTransitionsPeakAtTheCutAndVanishAway() {
        val wow = listOf(TransitionType.GLITCH, TransitionType.PRISM, TransitionType.LEAK, TransitionType.IRIS, TransitionType.PIXELATE, TransitionType.SHAKE)
        wow.forEach { t ->
            val e = TransitionEffect(0, 4_000_000, t, 500_000, t, 500_000)
            assertEquals("$t a metà clip", TransitionEffect.Params(), e.params(2_000_000).copy(seed = 0f))
            assertTrue("$t sul taglio", e.params(3_999_000).copy(seed = 0f) != TransitionEffect.Params())
            assertTrue("$t all'inizio", e.params(0).copy(seed = 0f) != TransitionEffect.Params())
        }
        // Il cerchio è chiuso del tutto proprio sul taglio.
        assertTrue(TransitionEffect(0, 4_000_000, null, 0, TransitionType.IRIS, 500_000).params(3_999_000).iris > 0.99f)
        assertTrue(TransitionType.entries.filter { it.soft }.none { it == TransitionType.GLITCH || it == TransitionType.SHAKE })
    }

    @Test fun hitFollowsBeats() {
        val fx = FxEffect(listOf(ClipFx(FxType.HIT)), listOf(1_000_000))
        assertEquals(1f, fx.hitAt(1_000_000), 1e-3f)
        assertTrue(fx.hitAt(1_300_000) < 0.1f)
        assertEquals(0f, fx.hitAt(900_000), 0f)
        assertTrue(FxType.HIT.onBeats && FxType.PULSE.onBeats && !FxType.BOKEH.onBeats)
        assertEquals(8, FxType.entries.count { it.category == FxCategory.WOW })
    }

    @Test fun lettersArriveInTurnAndLeave() {
        TextAnim.entries.filter { it.letters }.forEach { a ->
            val n = 8
            (0 until n).forEach { i ->
                assertEquals("$a lettera $i all'inizio", 0f, letterFx(a, true, 0f, i, n).alpha, 1e-3f)
                val end = letterFx(a, true, 1f, i, n)
                assertEquals("$a lettera $i alla fine", 1f, end.alpha, 1e-3f)
                assertEquals("$a al suo posto", 0f, end.dy, 0.02f)
                assertEquals("$a misura normale", 1f, end.scale, 0.02f)
                assertEquals("$a uscita finita", 0f, letterFx(a, false, 1f, i, n).alpha, 1e-3f)
                assertEquals("$a uscita non iniziata", 1f, letterFx(a, false, 0f, i, n).alpha, 1e-3f)
            }
            // A metà entrata la prima lettera è più avanti dell'ultima.
            assertTrue("$a a turno", letterFx(a, true, 0.3f, 0, n).alpha > letterFx(a, true, 0.3f, n - 1, n).alpha)
        }
        assertEquals(1f, letterFx(TextAnim.DROP, true, 1f, 0, 1).alpha, 1e-3f) // una sola lettera
    }

    @Test fun wowTextFramesDraw() {
        val t = TextItem(text = "Ciao mondo", look = TextLook.SEMPLICE, gradientTo = Color.RED)
        val still = TextRenderer.render(app, t, 1080, 1920)
        listOf(TextAnim.DROP, TextAnim.RISE, TextAnim.WAVE).forEach { a ->
            assertEquals("$a prima: nulla", 0, opaquePixels(TextRenderer.renderLetters(app, t, 1080, 1920, a, true, 0f)))
            assertTrue("$a dopo: tutto", opaquePixels(TextRenderer.renderLetters(app, t, 1080, 1920, a, true, 1f)) > opaquePixels(still) / 2)
            TextLook.entries.forEach { l -> assertTrue("$a/$l", opaquePixels(TextRenderer.renderLetters(app, t.copy(look = l), 1080, 1920, a, true, 0.7f)) > 10) }
        }
        val g = TextRenderer.renderGlitch(app, t, 1080, 1920, 1f, 3)
        assertTrue(g.width > still.width && g.height == still.height && opaquePixels(g) > opaquePixels(still) / 2)
        val sh = TextRenderer.renderShine(app, t, 1080, 1920, 0.5f)
        assertEquals(still.width, sh.width)
        assertTrue(opaquePixels(sh) >= opaquePixels(still) - 2)
        assertEquals(null, shinePhase(100_000))
        assertEquals(0f, shinePhase(300_000)!!, 1e-3f)
        assertEquals(null, shinePhase(1_500_000))
        assertTrue(lettersInUs(t) in 450_000L..1_800_000L)
    }

    @Test fun wowTextPresetsAreRecognized() {
        listOf(TextPreset.GLITCH, TextPreset.ONDA, TextPreset.ORO, TextPreset.CASCATA).forEach { p ->
            assertEquals(p, TextPreset.matching(p.applyTo(TextItem(text = "x"))))
        }
    }

    // ---------------- Disegno di testi e livelli ----------------

    private fun opaquePixels(b: Bitmap): Int {
        var n = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) if (Color.alpha(b.getPixel(x, y)) > 128) n++
        return n
    }

    @Test fun everyFontAndLookDraws() {
        TextFont.entries.forEach { f ->
            TextLook.entries.forEach { l ->
                val t = TextItem(text = "Prova\nriga due", font = f, look = l, gradientTo = if (l == TextLook.OMBRA) Color.RED else null, spacing = 0.1f, upper = true)
                val b = TextRenderer.render(app, t, 1080, 1920)
                assertTrue("$f/$l", b.width > 20 && b.height > 20)
                assertTrue("$f/$l vuoto", opaquePixels(b) > 20)
            }
        }
        // Macchina da scrivere: con 0 lettere visibili non si vede nulla (salvo il riquadro).
        val empty = TextRenderer.render(app, TextItem(text = "Ciao", look = TextLook.SEMPLICE), 1080, 1920, visible = 0)
        assertEquals(0, opaquePixels(empty))
    }

    private fun testImage(): File {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.GREEN)
        c.drawRect(70f, 20f, 130f, 80f, Paint().apply { color = Color.RED })
        val f = File(app.cacheDir, "livello_test.png")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    @Test fun layerShapesBordersAndKey() {
        val f = testImage()
        LayerMask.entries.forEach { m ->
            LayerBorder.entries.forEach { br ->
                val l = Layer(uri = Uri.fromFile(f).toString(), width = 200, height = 100, mask = m, border = br, shadow = true)
                val p = LayerRenderer.prepare(app, l, 1080, 1920)
                assertTrue("$m/$br", p.bitmap.width > 10 && opaquePixels(p.bitmap) > 10)
            }
        }
        // Scontorno verde: lo sfondo sparisce, il quadrato rosso resta.
        val keyed = LayerRenderer.prepare(app, Layer(uri = Uri.fromFile(f).toString(), width = 200, height = 100, key = KeyColor.GREEN), 1080, 1920).bitmap
        assertEquals("scontorno: angolo verde", 0, Color.alpha(keyed.getPixel(3, 3)))
        assertTrue(Color.alpha(keyed.getPixel(keyed.width / 2, keyed.height / 2)) > 200)
        // Cerchio: gli angoli sono trasparenti.
        val circle = LayerRenderer.prepare(app, Layer(uri = Uri.fromFile(f).toString(), width = 200, height = 100, mask = LayerMask.CIRCLE), 1080, 1920).bitmap
        assertEquals("cerchio: angolo", 0, Color.alpha(circle.getPixel(1, 1)))
    }

    // ---------------- Dissolvenze preparate per l'anteprima ----------------

    @Test fun previewProjectSwapsReadyCrossfadesKeepingDuration() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        val p = Project(name = "x", clips = listOf(
            video("a", s = 1_000_000, e = 5_000_000).copy(transitionOut = x),
            video("b", s = 2_000_000, e = 6_000_000).copy(transitionOut = x),
            video("c", s = 2_000_000, e = 6_000_000),
        ))
        // Nulla di pronto: progetto invariato.
        assertEquals(p, CrossfadePreview.previewProject(app, p, 1080, 1920))
        // Pronto solo il primo taglio.
        val jobs = CrossfadePreview.jobs(p, 1080, 1920)
        CrossfadePreview.file(app, jobs[0].key).writeText("finto")
        val q = CrossfadePreview.previewProject(app, p, 1080, 1920)
        assertEquals(4, q.clips.size) // a1, pezzo pronto, b2 (con la seconda dissolvenza ancora da fare), c
        assertTrue(q.clips[1].baked)
        assertTrue(abs(q.durationUs - p.durationUs) < 10_000)
        // La dissolvenza non pronta resta (in anteprima diventerà passaggio al nero).
        assertEquals(TransitionType.CROSSFADE, q.clips[2].transitionOut?.type)
        // Tutti pronti.
        jobs.forEach { CrossfadePreview.file(app, it.key).writeText("finto") }
        val r = CrossfadePreview.previewProject(app, p, 1080, 1920)
        assertEquals(5, r.clips.size)
        assertTrue(abs(r.durationUs - p.durationUs) < 10_000)
        assertTrue(r.crossfades().isEmpty())
        // E il montaggio dell'anteprima si costruisce.
        assertEquals(1, factory.build(r, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false).sequences.size)
        jobs.forEach { CrossfadePreview.file(app, it.key).delete() }
    }
}
