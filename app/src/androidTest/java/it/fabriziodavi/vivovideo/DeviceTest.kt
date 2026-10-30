package it.fabriziodavi.vivovideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.MediaMetadataRetriever
import android.net.Uri
import it.fabriziodavi.vivovideo.online.Library
import it.fabriziodavi.vivovideo.engine.AudioDsp
import it.fabriziodavi.vivovideo.engine.DuckPreview
import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.BusMix
import it.fabriziodavi.vivovideo.model.LoudTarget
import it.fabriziodavi.vivovideo.model.Mix
import it.fabriziodavi.vivovideo.engine.VoiceChain
import it.fabriziodavi.vivovideo.engine.VoiceRecorder
import it.fabriziodavi.vivovideo.engine.Decoders
import it.fabriziodavi.vivovideo.engine.Whisper
import it.fabriziodavi.vivovideo.model.Subtitles
import it.fabriziodavi.vivovideo.model.SubStyle
import it.fabriziodavi.vivovideo.model.SubPosition
import it.fabriziodavi.vivovideo.online.LibraryKeys
import it.fabriziodavi.vivovideo.online.LibrarySource
import kotlinx.coroutines.runBlocking
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.fabriziodavi.vivovideo.engine.CompositionFactory
import it.fabriziodavi.vivovideo.engine.CrossfadePreview
import it.fabriziodavi.vivovideo.engine.Proxies
import it.fabriziodavi.vivovideo.engine.Reverser
import it.fabriziodavi.vivovideo.engine.Backup
import it.fabriziodavi.vivovideo.engine.MediaRef
import it.fabriziodavi.vivovideo.engine.TextRenderer
import it.fabriziodavi.vivovideo.engine.SmartAnalyzer
import it.fabriziodavi.vivovideo.engine.PersonMasks
import it.fabriziodavi.vivovideo.model.Moments
import it.fabriziodavi.vivovideo.model.PersonMode
import it.fabriziodavi.vivovideo.model.PersonFx
import it.fabriziodavi.vivovideo.model.Interview
import it.fabriziodavi.vivovideo.model.InterviewFormat
import it.fabriziodavi.vivovideo.model.InterviewOptions
import it.fabriziodavi.vivovideo.model.InterviewQA
import it.fabriziodavi.vivovideo.model.Span
import it.fabriziodavi.vivovideo.model.Reframe
import it.fabriziodavi.vivovideo.model.VideoFormat
import it.fabriziodavi.vivovideo.engine.Stabilizer
import it.fabriziodavi.vivovideo.model.ClipTools
import it.fabriziodavi.vivovideo.model.StabMath
import it.fabriziodavi.vivovideo.ui.EditorViewModel
import it.fabriziodavi.vivovideo.engine.FxEffect
import it.fabriziodavi.vivovideo.engine.TransitionEffect
import it.fabriziodavi.vivovideo.engine.GradeEffect
import it.fabriziodavi.vivovideo.engine.BlurFillEffect
import it.fabriziodavi.vivovideo.engine.LayerMaskEffect
import it.fabriziodavi.vivovideo.engine.HdrGl
import it.fabriziodavi.vivovideo.engine.WowFxEffect
import it.fabriziodavi.vivovideo.model.Grade
import androidx.media3.common.util.GlUtil
import it.fabriziodavi.vivovideo.engine.ExportOptions
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.engine.Exporter
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.ClipFx
import it.fabriziodavi.vivovideo.model.ClipSpeed
import it.fabriziodavi.vivovideo.model.FadeColor
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
import it.fabriziodavi.vivovideo.model.AutoEdit
import it.fabriziodavi.vivovideo.model.AutoOptions
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.TextItem
import it.fabriziodavi.vivovideo.model.TextLook
import it.fabriziodavi.vivovideo.model.TextPreset
import it.fabriziodavi.vivovideo.model.RBox
import it.fabriziodavi.vivovideo.model.RedactStyle
import it.fabriziodavi.vivovideo.model.Redaction
import it.fabriziodavi.vivovideo.model.Credits
import it.fabriziodavi.vivovideo.model.withCredits
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Prove vere su un Android (emulatore): esportazioni complete, anteprima che scorre, dissolvenze preparate.
 * I video, le foto, i loghi e la musica di prova si creano qui.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTest {
    /**
     * L'emulatore (decodifica e GPU via software) a volte si inceppa da solo: ogni prova ha un secondo tentativo.
     * Un errore vero dell'app fallisce comunque due volte di fila; i tentativi falliti finiscono nel registro.
     */
    @get:org.junit.Rule val retry = org.junit.rules.TestRule { base, d ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                try { base.evaluate() } catch (e: Throwable) {
                    if (e is org.junit.AssumptionViolatedException) throw e
                    android.util.Log.w("VivoVideoTest", "${d.methodName}: primo tentativo fallito, riprovo: ${e.message?.take(300)}")
                    base.evaluate()
                }
            }
        }
    }

    companion object {
        private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private val main = Handler(Looper.getMainLooper())
        private const val W = 640
        private const val H = 360
        lateinit var videoA: Uri
        lateinit var videoB: Uri
        lateinit var videoC: Uri
        lateinit var photo: Uri
        lateinit var logo: Uri
        lateinit var music: Uri

        private fun dir() = File(ctx.cacheDir, "prove").apply { mkdirs() }

        private fun frame(color: Int, label: String, w: Int = W, h: Int = H, transparent: Boolean = false): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            if (!transparent) c.drawColor(color)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (transparent) color else Color.WHITE; textSize = h / 4f }
            if (transparent) c.drawCircle(w / 2f, h / 2f, h / 3f, p) else c.drawText(label, w / 10f, h / 2f, p)
            return b
        }

        private fun save(b: Bitmap, name: String, png: Boolean): File {
            val f = File(dir(), name)
            f.outputStream().use { b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
            return f
        }

        private fun sineWav(name: String, seconds: Double, hz: Double): File {
            val n = (48_000 * seconds).toInt()
            val s = FloatArray(n * 2)
            for (i in 0 until n) {
                // Battito: un colpo ogni 0,5 s (per il rilevamento dei battiti).
                val beat = if ((i % 24_000) < 2_000) 0.6f else 0f
                val v = (0.25 * sin(2 * PI * hz * i / 48_000)).toFloat() + beat * (if (i % 2 == 0) 1 else -1)
                s[2 * i] = v; s[2 * i + 1] = v
            }
            return File(dir(), name).also { writeWavFile(it, s) }
        }

        /** Video di prova: immagini colorate che cambiano ogni mezzo secondo, con audio. */
        private fun makeVideo(name: String, seconds: Int, colors: List<Int>): Uri {
            val out = File(dir(), name)
            if (out.exists()) return Uri.fromFile(out)
            val per = 500_000L
            val video = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            for (k in 0 until seconds * 2) {
                val img = save(frame(colors[k % colors.size], "$name $k"), "${name}_$k.png", png = true)
                video.addItem(
                    EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(img)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(per / 1000).build())
                        .setDurationUs(per).setFrameRate(30).build()
                )
            }
            val wav = sineWav("${name}.wav", seconds.toDouble(), 330.0)
            val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItem(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).setDurationUs(seconds * 1_000_000L).build())
            transform(Composition.Builder(listOf(video.build(), audio.build())).build(), out)
            return Uri.fromFile(out)
        }

        /** Esegue Transformer sul thread principale e aspetta la fine. */
        fun transform(c: Composition, out: File, timeoutS: Long = 240, softwareDecoder: Boolean = false) {
            val done = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            main.post {
                Transformer.Builder(ctx).setMaxDelayBetweenMuxerSamplesMs(30_000).setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAssetLoaderFactory(Decoders.assetLoader(ctx, softwareDecoder))
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.countDown() }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            error.set(chain(exportException)); done.countDown()
                        }
                    }).build().start(c, out.absolutePath)
            }
            assertTrue("Transformer bloccato", done.await(timeoutS, TimeUnit.SECONDS))
            error.get()?.let { fail("Transformer: $it") }
        }

        fun chain(t: Throwable): String = generateSequence(t) { it.cause }.take(4).joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }

        /**
         * Video di prova a 60 fotogrammi al secondo per misurare la fluidità: sfondo [bg] e una riga bianca orizzontale che
         * scende di [STEP] pixel a ogni fotogramma (dal fotogramma si ricava la sua posizione nel tempo).
         */
        fun makeMotionVideo(name: String, seconds: Int, bg: Int): Uri {
            val out = File(dir(), name)
            if (out.exists()) return Uri.fromFile(out)
            val base = save(frame(bg, "", W, H).also { Canvas(it).drawColor(bg) }, "${name}_fondo.png", png = true)
            // La riga si disegna dal tempo esatto del fotogramma: il video di prova è fluido per costruzione.
            val line = object : androidx.media3.effect.BitmapOverlay() {
                private val bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                private val paint = Paint().apply { color = Color.WHITE }
                override fun getBitmap(presentationTimeUs: Long): Bitmap {
                    val k = Math.round(presentationTimeUs * 60 / 1e6).toInt()
                    val y = (k * STEP) % (H - 8)
                    bm.eraseColor(Color.TRANSPARENT)
                    Canvas(bm).drawRect(0f, y.toFloat(), W.toFloat(), (y + 4).toFloat(), paint)
                    return bm
                }
            }
            val video = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItem(
                EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(base)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(seconds * 1000L).build())
                    .setDurationUs(seconds * 1_000_000L).setFrameRate(60)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(androidx.media3.effect.OverlayEffect(listOf(line)))))
                    .build()
            )
            val wav = sineWav("${name}.wav", seconds.toDouble(), 330.0)
            val audio = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItem(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).setDurationUs(seconds * 1_000_000L).build())
            transform(Composition.Builder(listOf(video.build(), audio.build())).build(), out, timeoutS = 600)
            return Uri.fromFile(out)
        }

        const val STEP = 3

        @JvmStatic
        @BeforeClass
        fun media() {
            videoA = makeVideo("a.mp4", 6, listOf(Color.RED, Color.rgb(200, 0, 0)))
            videoB = makeVideo("b.mp4", 6, listOf(Color.BLUE, Color.rgb(0, 0, 200)))
            videoC = makeVideo("c.mp4", 6, listOf(Color.GREEN, Color.rgb(0, 160, 0)))
            photo = Uri.fromFile(save(frame(Color.YELLOW, "FOTO", 800, 600), "foto.jpg", png = false))
            logo = Uri.fromFile(save(frame(Color.MAGENTA, "", 300, 300, transparent = true), "logo.png", png = true))
            music = Uri.fromFile(sineWav("musica.wav", 30.0, 440.0))
        }
    }

    private fun video(id: String, uri: Uri, s: Long = 0, e: Long = 6_000_000) =
        Clip(id = id, uri = uri.toString(), sourceDurationUs = 6_000_000, trimStartUs = s, trimEndUs = e, width = W, height = H, fps = 30f)

    private fun photoClip(id: String) =
        Clip(id = id, uri = photo.toString(), sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 2_000_000, isPhoto = true, width = 800, height = 600)

    /** Una clip breve per ogni gruppo di effetti: così lo shader con tutti gli effetti gira davvero. */
    private fun allEffectsClips(): List<Clip> = FxType.entries.chunked(6).mapIndexed { i, group ->
        video("fx$i", if (i % 2 == 0) videoB else videoC, s = 1_000_000, e = 2_200_000).copy(fx = group.map { ClipFx(it) }, fadeInUs = 300_000)
    }

    private fun fullProject(): Project {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        return Project(
            name = "tutto",
            clips = listOf(
                video("a", videoA, s = 500_000, e = 4_000_000).copy(transitionOut = x, fadeInUs = 800_000,
                    keyframes = listOf(Keyframe(0, Transform()), Keyframe(2_000_000, Transform(scale = 1.4f, rotation = 10f, opacity = 0.7f))),
                    fx = listOf(ClipFx(FxType.VHS), ClipFx(FxType.VIGNETTE), ClipFx(FxType.PULSE))),
                video("b", videoB, s = 1_000_000, e = 5_000_000).copy(speed = ClipSpeed.RAMP, speedPoints = SpeedPreset.BULLET.points,
                    transitionOut = TransitionSpec(TransitionType.ZOOM), frame = Transform(scale = 1.2f), fill = true),
                photoClip("p").copy(transitionOut = x, keyframes = listOf(Keyframe(0, Transform()), Keyframe(2_000_000, Transform(scale = 1.2f)))),
                video("c", videoC, s = 1_000_000, e = 4_000_000).copy(transitionOut = TransitionSpec(TransitionType.SPIN), fx = listOf(ClipFx(FxType.GLITCH), ClipFx(FxType.BW))),
                video("a2", videoA, s = 2_000_000, e = 5_000_000).copy(volume = 0.5f, fadeOutUs = 1_000_000, fadeColor = FadeColor.WHITE),
            ) + allEffectsClips(),
            music = listOf(MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, fadeInUs = 1_000_000, fadeOutUs = 2_000_000,
                beatsUs = (0 until 60).map { it * 500_000L }, bpm = 120f)),
            texts = listOf(
                TextItem(text = "Titolo", startUs = 0, durationUs = 3_000_000, look = TextLook.NEON, animIn = TextAnim.WORDS, endPose = Transform(scale = 1.2f)),
                TextItem(text = "Didascalia", startUs = 4_000_000, durationUs = 4_000_000, look = TextLook.RIQUADRO, animIn = TextAnim.TYPE, gradientTo = Color.CYAN),
            ),
            layers = listOf(
                Layer(uri = logo.toString(), name = "logo", width = 300, height = 300, startUs = 1_000_000, durationUs = 5_000_000,
                    mask = LayerMask.STAR, border = LayerBorder.WHITE, shadow = true, endPose = Transform(x = 0.3f, scale = 0.3f)),
            ),
        )
    }

    /** Le transizioni e le animazioni dei testi "wow", tutte insieme. */
    private fun wowProject(premium: Boolean = true): Project {
        val wow = listOf(TransitionType.GLITCH, TransitionType.PRISM, TransitionType.LEAK, TransitionType.IRIS, TransitionType.PIXELATE, TransitionType.SHAKE) +
            (if (premium) TransitionType.entries.filter { it.premium } else emptyList())
        val clips = (0..wow.size).map { i ->
            video("w$i", listOf(videoA, videoB, videoC)[i % 3], s = 1_000_000, e = 2_500_000)
                .copy(transitionOut = wow.getOrNull(i)?.let { TransitionSpec(it, 600_000) },
                    fx = if (premium) FxType.entries.filter { it.wow2 }.let { w -> listOf(ClipFx(w[i % w.size])) } else emptyList())
        }
        val anims = listOf(TextAnim.DROP, TextAnim.RISE, TextAnim.WAVE, TextAnim.GLITCH, TextAnim.SHINE) +
            (if (premium) listOf(TextAnim.BOUNCE, TextAnim.FLIP, TextAnim.SPIN, TextAnim.SCATTER, TextAnim.BLUR, TextAnim.FLICKER) else emptyList())
        return Project(
            name = "wow",
            clips = clips,
            texts = anims.mapIndexed { i, a ->
                TextItem(text = "Wow ${a.label}", startUs = i * 1_500_000L, durationUs = 2_500_000, animIn = a,
                    animOut = if (a.inOnly) TextAnim.FADE else a, look = TextLook.entries[i % TextLook.entries.size], gradientTo = if (i % 2 == 0) Color.MAGENTA else null)
            },
        )
    }

    /** Audio: voce (audio staccato) che abbassa la musica e volume finale uniformato a -14 LUFS. */
    @Test fun exportDuckingAndLoudness() {
        val a = video("a", videoA, s = 0, e = 5_000_000)
        val p = Project(
            name = "audio", clips = listOf(a.copy(muted = true)),
            music = listOf(
                MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 5_000_000, volume = 0.3f),
                MusicTrack(uri = videoA.toString(), name = "voce", sourceDurationUs = 6_000_000, startUs = 1_000_000, lengthUs = 3_000_000, dialog = true),
            ),
            duck = true,
        )
        val uri = exportAndCheckUri(p)
        val pcm = decodeAudioRange(ctx, uri, 0, 5_000_000)!!
        val lufs = AudioDsp.LoudnessMeter().apply { addInterleaved(pcm) }.lufs()
        android.util.Log.i("VivoVideoTest", "volume del video esportato: $lufs LUFS")
        assertTrue("volume $lufs invece di circa -14 LUFS", lufs in -16.5..-12.0 || lufs < -16.5 && pcm.maxOf { kotlin.math.abs(it) } > 0.85f)
    }

    /** Brano più corto del video: l'anteprima continua oltre la fine del brano (prima si bloccava). */
    @Test fun previewPlaysPastTheEndOfAShortSong() {
        val p = Project(
            name = "brano corto", clips = listOf(video("a", videoA, s = 0, e = 6_000_000)),
            music = listOf(MusicTrack(uri = music.toString(), name = "corto", sourceDurationUs = 30_000_000, lengthUs = 2_000_000)),
        )
        playFor(p, seconds = 6, minFps = 3f)
    }

    /** Mixer nel video esportato: musica muta = silenzio; musica a -12 dB = molto più bassa; limitatore sotto -1 dBFS. */
    @Test fun exportFollowsTheMixer() {
        val a = video("a", videoA, s = 0, e = 4_000_000).copy(muted = true)
        val song = MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 4_000_000)
        val base = Project(name = "mixer", clips = listOf(a), music = listOf(song), mix = Mix(target = LoudTarget.OFF))
        fun rms(p: Project): Double {
            val pcm = decodeAudioRange(ctx, exportAndCheckUri(p), 500_000, 3_500_000)!!
            return 20 * kotlin.math.log10(kotlin.math.sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size) + 1e-9)
        }
        val full = rms(base)
        val low = rms(base.copy(mix = base.mix.with(Bus.MUSIC, BusMix(gainDb = -12f))))
        val muted = rms(base.copy(mix = base.mix.with(Bus.MUSIC, BusMix(mute = true))))
        android.util.Log.i("VivoVideoTest", "mixer: piena $full, -12 dB $low, muta $muted")
        assertTrue("-12 dB: $full → $low", full - low in 9.0..15.0)
        assertTrue("muta: $muted", muted < full - 40)
    }

    /** Registrazione vera dal microfono (sull'emulatore è virtuale): file grezzo leggibile, pulito e salvato in WAV. */
    @Test fun voiceRecorderRecordsAndCleans() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, android.Manifest.permission.RECORD_AUDIO)
        val raw = File(ctx.cacheDir, "voce_prova.pcm")
        val rec = VoiceRecorder(raw)
        assertTrue("il microfono non parte", rec.start())
        Thread.sleep(1500)
        val n = rec.stop()
        assertTrue("campioni: $n", n > VoiceChain.RATE / 2)
        val pcm = VoiceRecorder.read(raw)
        assertEquals(n, pcm.size.toLong())
        val clean = VoiceChain.process(pcm)
        val wav = File(ctx.cacheDir, "voce_prova.wav")
        writeWavFile(wav, VoiceChain.toStereo(clean))
        val back = decodeAudioRange(ctx, Uri.fromFile(wav), 0, 1_000_000)
        assertTrue(back != null && back.size >= VoiceChain.RATE)
    }

    /** Voce fuori campo: registrazione portata a volume chiaro e musica abbassata sotto la voce anche in anteprima. */
    @Test fun voiceOverLevelAndPreviewDucking() {
        // "Registrazione" piana: 1 s di tono a 0,03 fra mezzo secondo di silenzio prima e dopo.
        val sr = AudioDsp.RATE
        val rec = FloatArray(sr * 2 * 2) { i -> val f = i / 2; if (f in sr / 2 until sr * 3 / 2) (0.03f * kotlin.math.sin(2 * Math.PI * 220.0 * f / sr)).toFloat() else 0f }
        assertTrue(AudioDsp.levelVoice(rec) > 3f)
        val wav = File(ctx.cacheDir, "voice/voce_test.wav").apply { parentFile?.mkdirs() }
        writeWavFile(wav, rec)
        val a = video("a", videoA, s = 0, e = 5_000_000)
        val p = Project(
            name = "voce", clips = listOf(a.copy(muted = true)), duck = true,
            music = listOf(
                MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 5_000_000),
                MusicTrack(uri = Uri.fromFile(wav).toString(), name = "Voce 1", sourceDurationUs = 2_000_000, startUs = 1_000_000, lengthUs = 2_000_000, dialog = true),
            ),
        )
        assertTrue(DuckPreview.needs(p))
        DuckPreview.prepare(ctx, p)
        val g = DuckPreview.gainsFor(p)!!
        // La voce suona fra 1,5 s e 2,5 s del montaggio.
        assertEquals(1f, g[80], 0.02f)
        assertTrue("musica non abbassata: ${g[200]}", g[200] < 0.3f)
        assertEquals(1f, g[450], 0.02f)
        // L'anteprima con la musica che si abbassa scorre senza errori.
        playFor(p, seconds = 5, minFps = 3f)
    }

    /** Sottotitoli: riconoscimento vocale vero (modello minimo) sull'audio d'esempio di whisper.cpp. */
    @Test fun whisperTranscribes() {
        val m = try {
            runBlocking { Whisper.download(ctx, Whisper.Model.TINY) {} }
        } catch (e: java.net.UnknownHostException) { org.junit.Assume.assumeNoException(e); return }
        val wav = File(ctx.cacheDir, "jfk.wav")
        if (!wav.exists()) {
            val c = java.net.URL("https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/samples/jfk.wav").openConnection() as java.net.HttpURLConnection
            c.inputStream.use { i -> wav.outputStream().use { i.copyTo(it) } }
        }
        val bytes = wav.readBytes()
        // Dati PCM dopo il blocco "data" (16 kHz, mono, 16 bit).
        val dataAt = (0 until bytes.size - 4).first { i -> bytes[i] == 'd'.code.toByte() && bytes[i + 1] == 'a'.code.toByte() && bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 'a'.code.toByte() } + 8
        val bb = java.nio.ByteBuffer.wrap(bytes, dataAt, bytes.size - dataAt).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val pcm = FloatArray((bytes.size - dataAt) / 2) { bb.getShort().toFloat() / 32768f }
        val t0 = System.currentTimeMillis()
        val words = runBlocking { Whisper.transcribe(m, pcm, "en") }
        android.util.Log.i("VivoVideoTest", "whisper: ${words.size} parole in ${System.currentTimeMillis() - t0} ms: ${words.joinToString(" ") { it.text }}")
        val text = words.joinToString(" ") { it.text.lowercase() }
        assertTrue("trascrizione: $text", text.contains("country"))
        assertTrue(words.zipWithNext().all { (a, b) -> a.startUs <= b.startUs })
        val subs = Subtitles.build(words, SubStyle.KARAOKE, SubPosition.BOTTOM, 11_000_000)
        assertTrue(subs.isNotEmpty())
    }

    /** Libreria in rete: una ricerca vera su Openverse (musica ed effetti) e lo scaricamento di un brano. */
    @Test fun onlineLibraryOpenverse() {
        val keys = LibraryKeys(null, null)
        val music = try {
            runBlocking { Library.search(LibrarySource.OPENVERSE, "piano", false, keys, false, false) }
        } catch (e: java.io.IOException) {
            // Rete assente o lenta sull'emulatore (anche il timeout): non è un errore dell'app.
            org.junit.Assume.assumeNoException("rete non disponibile sull'emulatore", e); return
        }
        android.util.Log.i("VivoVideoTest", "Openverse musica: ${music.size} risultati, primo: ${music.firstOrNull()}")
        assertTrue("nessun brano da Openverse", music.isNotEmpty())
        assertTrue(music.all { it.downloadUrl.startsWith("http") && it.license.derivatives })
        val sfx = runBlocking { Library.search(LibrarySource.OPENVERSE, "whoosh", true, keys, false, false) }
        android.util.Log.i("VivoVideoTest", "Openverse effetti: ${sfx.size} risultati, primo: ${sfx.firstOrNull()}")
        assertTrue("nessun effetto da Openverse", sfx.isNotEmpty())
        // Scarica il più corto fra i primi e controlla che si legga come audio.
        val pick = (sfx.take(5) + music.take(3)).minByOrNull { if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE }!!
        val f = runBlocking { Library.download(ctx, pick) }
        val (_, durUs) = readAudioInfo(ctx, Uri.fromFile(f))
        android.util.Log.i("VivoVideoTest", "scaricato ${f.name} ${f.length()} byte, ${durUs / 1000} ms")
        assertTrue("file scaricato non leggibile", durUs > 0)
    }

    /** Video automatico con musica e battiti: tutte le regie si esportano. */
    @Test fun exportAutoVideoCinema() = exportAuto(AutoStyle.CINEMA)

    @Test fun exportAutoVideoEnergico() = exportAuto(AutoStyle.ENERGICO)

    private fun exportAuto(s: AutoStyle) {
        val srcs = listOf(video("a", videoA), photoClip("p1"), video("b", videoB), photoClip("p2"), video("c", videoC))
            .map { it.copy(trimStartUs = 0, trimEndUs = it.sourceDurationUs.coerceAtMost(Clip.PHOTO_MAX_US)) }
        val m = MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 30_000_000,
            beatsUs = (1 until 60).map { it * 500_000L }, bpm = 120f)
        // 12 s bastano per intro, tagli, stacchi netti e transizioni del tema; l'emulatore regge pochi video lunghi di fila.
        val p = AutoEdit.build("auto ${s.label}", srcs, m, AutoOptions(style = s, title = "Prova", subtitle = "Ottobre 2026", targetUs = 12_000_000))
        // Com'è fatta ogni scena: scritto nel registro prima di esportare (resta anche se l'emulatore va in crash).
        val scenes = p.clips.joinToString("\n") { c ->
            "  ${c.uri.substringAfterLast('/')} ${c.trimStartUs / 1000}-${c.trimEndUs / 1000} ms di ${c.sourceDurationUs / 1000}, " +
                "esce ${c.outputUs / 1000} ms, ${c.speed}${if (c.speedPoints.isNotEmpty()) " curva" else ""}, " +
                "passaggio ${c.transitionOut?.type ?: "-"} ${c.transitionOut?.durationUs?.div(1000) ?: 0} ms, fx ${c.fx.map { it.type }}"
        }
        scenes.lines().forEach { android.util.Log.i("AutoScene", "${s.label}: $it") }
        // Senza compositore a più ingressi: l'emulatore regge pochi decodificatori insieme (il telefono sì).
        try { exportAndCheck(p, multi = false) }
        catch (e: AssertionError) { throw AssertionError("${s.label}: ${e.message}\nScene:\n$scenes", e) }
    }

    @Test fun exportWowTransitionsAndTexts() {
        exportAndCheck(wowProject())
    }

    @Test fun previewPlaysWow() {
        playFor(wowProject(premium = false), minFps = 3f)
    }

    /** Pacchetto premium in anteprima: sull'emulatore la grafica è software (lenta), basta che scorra e mostri immagini. */
    @Test fun previewPlaysPremium() {
        // Sei scene con alcune novità (sull'emulatore la grafica è software): basta che scorra e mostri immagini.
        val w = wowProject(premium = true)
        val p = w.copy(clips = w.clips.drop(6).take(6).mapIndexed { i, c -> if (i == 5) c.copy(transitionOut = null) else c }, texts = w.texts.drop(5).take(3).mapIndexed { i, t -> t.copy(startUs = i * 1_000_000L) })
        playFor(p, seconds = 6, minFps = 1f)
    }

    private var lastDetails = ""

    private fun exportAndCheck(p: Project, extraLayers: List<Layer> = emptyList(), multi: Boolean = true, options: ExportOptions = ExportOptions()) { exportAndCheckUri(p, extraLayers, multi, options) }

    private fun exportAndCheckUri(p: Project, extraLayers: List<Layer> = emptyList(), multi: Boolean = true, options: ExportOptions = ExportOptions()): Uri {
        val project = p.copy(layers = p.layers + extraLayers)
        Safety.setMultiExport(ctx, multi)
        val state = AtomicReference<ExportState>(ExportState.Idle)
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val exporter = Exporter(ctx, scope, CompositionFactory(ctx)) { s ->
            state.set(s)
            if (s is ExportState.Done || s is ExportState.Failed) done.countDown()
        }
        main.post { exporter.start(project, options) }
        // Diagnosi: se dopo 30 s non è nemmeno partita, il thread principale è occupato: si scrive da cosa.
        if (!done.await(30, TimeUnit.SECONDS) && state.get() is ExportState.Idle) {
            val stack = Looper.getMainLooper().thread.stackTrace.take(25).joinToString("\n  ")
            android.util.Log.e("VivoTest", "Esportazione ferma, thread principale:\n  $stack")
            fail("esportazione non partita: thread principale occupato da\n  $stack")
        }
        assertTrue("esportazione bloccata (${state.get()})", done.await(600, TimeUnit.SECONDS))
        val s = state.get()
        if (s is ExportState.Failed) fail("esportazione fallita: ${s.message}")
        val uri = (s as ExportState.Done).uri
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("durata ${ms} ms invece di ${project.durationUs / 1000}", abs(ms * 1000 - project.durationUs) < 250_000)
            assertTrue("niente audio", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            assertTrue("niente video", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")
            assertTrue("solo note su sovrapposizioni mancanti", !s.details.contains("Attenzione") || !multi)
            lastDetails = s.details
        } finally { r.release() }
        return uri
    }

    @Test fun exportSimple() {
        exportAndCheck(Project(name = "semplice", clips = listOf(video("a", videoA), video("b", videoB, s = 1_000_000, e = 3_000_000))))
    }

    @Test fun exportEveryFeature() {
        exportAndCheck(fullProject())
    }

    @Test fun exportVideoLayer() {
        exportAndCheck(fullProject(), listOf(Layer(uri = videoC.toString(), name = "pip", width = W, height = H, isVideo = true,
            sourceDurationUs = 6_000_000, sourceStartUs = 1_000_000, startUs = 2_000_000, durationUs = 4_000_000,
            pose = Transform(x = -0.25f, y = -0.25f, scale = 0.4f), mask = LayerMask.ROUNDED, key = KeyColor.GREEN, volume = 0.5f)))
    }

    // ---- Prove mirate per isolare i blocchi ----

    @Test fun bisectEffectsOnly() {
        exportAndCheck(Project(name = "effetti", clips = allEffectsClips().map { it.copy(fadeInUs = 0) }), multi = false)
    }

    @Test fun bisectFadesOnly() {
        exportAndCheck(Project(name = "dissolvenze", clips = listOf(
            video("a", videoA, e = 3_000_000).copy(fadeInUs = 800_000),
            video("b", videoB, e = 3_000_000).copy(fadeOutUs = 1_000_000, fadeColor = FadeColor.WHITE),
        )), multi = false)
    }

    @Test fun bisectFullWithoutNewEffectsSingle() {
        val p = fullProject()
        exportAndCheck(p.copy(clips = p.clips.filterNot { it.id.startsWith("fx") }), multi = false)
    }

    @Test fun bisectEachEffectGroupSingle() {
        FxType.entries.chunked(6).forEachIndexed { i, group ->
            val c = video("g$i", videoB, s = 1_000_000, e = 2_000_000).copy(fx = group.map { ClipFx(it) })
            try { exportAndCheck(Project(name = "gruppo$i", clips = listOf(c)), multi = false) }
            catch (e: Throwable) { fail("gruppo $i (${group.joinToString { it.name }}): ${e.message}") }
        }
    }

    @Test fun exportWithoutCompositor() {
        exportAndCheck(fullProject(), multi = false)
    }

    /**
     * L'anteprima deve scorrere davvero: dopo l'avvio, il tempo avanza quasi come l'orologio e
     * arrivano i fotogrammi. Registra la posizione ogni mezzo secondo per capire dove si ferma.
     */
    private fun playFor(p: Project, seconds: Int = 8, minFps: Float = 5f, startS: Long = 15) {
        val factory = CompositionFactory(ctx)
        val (w, h) = factory.outputSize(p, 1080)
        val shown = CrossfadePreview.previewProject(ctx, p, w, h)
        val composition = factory.build(shown, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = false)
        val frames = AtomicInteger(0)
        val error = AtomicReference<String?>(null)
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 4)
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close(); frames.incrementAndGet() }, main)
        val player = AtomicReference<CompositionPlayer>()
        val ready = CountDownLatch(1)
        main.post {
            val pl = CompositionPlayer.Builder(ctx).build()
            pl.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error.set(chain(e)); ready.countDown() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) ready.countDown() }
            })
            pl.setVideoSurface(reader.surface, Size(w, h))
            pl.setComposition(composition)
            pl.prepare()
            pl.play()
            player.set(pl)
        }
        assertTrue("l'anteprima non parte", ready.await(startS, TimeUnit.SECONDS))
        error.get()?.let { fail("anteprima: $it") }
        fun pos(): Long {
            val r = AtomicReference(0L); val l = CountDownLatch(1)
            main.post { r.set(player.get().currentPosition); l.countDown() }
            l.await(5, TimeUnit.SECONDS)
            return r.get()
        }
        val samples = mutableListOf<Long>()
        val f0 = frames.get()
        var recoveries = 0
        repeat(seconds * 2) {
            samples += pos()
            // Come il guardiano dell'app: tempo fermo per 4 s → lettore ricaricato da quel punto.
            if (samples.size >= 9 && samples.takeLast(9).toSet().size == 1 && recoveries < 2) {
                recoveries++
                val at = samples.last()
                val l = CountDownLatch(1)
                main.post {
                    player.get().release()
                    val pl = CompositionPlayer.Builder(ctx).build()
                    pl.addListener(object : Player.Listener {
                        override fun onPlayerError(e: PlaybackException) { error.set(chain(e)) }
                    })
                    pl.setVideoSurface(reader.surface, Size(w, h))
                    pl.setComposition(composition, at)
                    pl.prepare(); pl.play()
                    player.set(pl)
                    l.countDown()
                }
                l.await(5, TimeUnit.SECONDS)
            }
            Thread.sleep(500)
        }
        if (recoveries > 0) android.util.Log.w("VivoVideoTest", "anteprima ripartita $recoveries volte")
        // Gli errori durante la chiusura del lettore (come "release timeout" sull'emulatore lento) non contano, come nell'app.
        val playError = error.get()
        val latch = CountDownLatch(1)
        main.post { player.get().release(); latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
        reader.close()
        playError?.let { fail("anteprima: $it") }
        val advanced = samples.last() - samples.first()
        val trace = samples.joinToString(" ")
        val expected = (seconds * 2 - 1) * 500L
        assertTrue("anteprima lenta: avanzata di $advanced ms invece di ~$expected (posizioni: $trace, ripartenze: $recoveries)", advanced > expected * (if (recoveries > 0) 0.4 else 0.6))
        assertTrue("anteprima senza immagini: ${frames.get() - f0} fotogrammi (posizioni: $trace)", frames.get() - f0 > seconds * minFps)
    }

    @Test fun previewPlaysEveryFeature() {
        // Tutti gli effetti insieme: sull'emulatore (grafica senza GPU) basta che scorra; sul telefono è fluida.
        playFor(fullProject(), minFps = 2f, startS = 40)
    }

    @Test fun crossfadeSegmentRendersAndPlays() {
        val p = fullProject()
        val factory = CompositionFactory(ctx)
        val (w, h) = factory.outputSize(p, 1080)
        val jobs = CrossfadePreview.jobs(p, w, h)
        assertTrue("nessuna dissolvenza da preparare", jobs.isNotEmpty())
        jobs.forEach { job ->
            val out = CrossfadePreview.file(ctx, job.key)
            val c = factory.build(job.sub, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = true,
                sizeOverride = w to h, crossfadesOverride = job.cf?.let { listOf(it) })
            // Come nell'app: se si inceppa si riprova una volta, con il decodificatore software.
            try { transform(c, out, timeoutS = 120) } catch (e: Throwable) { out.delete(); transform(c, out, timeoutS = 180, softwareDecoder = true) }
            assertTrue(out.length() > 1000)
        }
        // L'anteprima con le dissolvenze pronte scorre.
        playFor(p, seconds = 8)
    }

    /**
     * Fluidità delle transizioni a due clip nel video esportato (segnalazione dell'utente: "lampi o scatti, esitazioni").
     * Due video a 60 fps con una riga che scende di STEP pixel a fotogramma; dopo l'esportazione si rilegge ogni fotogramma:
     * nella metà sinistra (dove arriva per prima la clip successiva) e in quella destra si cerca la riga, e il suo passo
     * deve restare STEP (0 = fotogramma ripetuto, 2·STEP = fotogramma saltato); la luminosità non deve fare sbalzi.
     */
    @Test fun transitionsAreSmoothInExport() {
        val a = makeMotionVideo("moto_a.mp4", 4, Color.rgb(150, 20, 20))
        val b = makeMotionVideo("moto_b.mp4", 4, Color.rgb(20, 20, 150))
        val report = StringBuilder()
        var bad = 0
        for (t in listOf(TransitionType.CROSSFADE, TransitionType.WIPE)) {
            val clips = listOf(
                Clip(id = "a", uri = a.toString(), sourceDurationUs = 4_000_000, trimStartUs = 0, trimEndUs = 2_000_000, width = W, height = H, fps = 60f)
                    .copy(transitionOut = TransitionSpec(t, 1_000_000)),
                Clip(id = "b", uri = b.toString(), sourceDurationUs = 4_000_000, trimStartUs = 1_000_000, trimEndUs = 3_000_000, width = W, height = H, fps = 60f),
            )
            val uri = exportAndCheckUri(Project(name = "fluido ${t.name}", clips = clips))
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toInt() ?: 0
                // Riga bianca in una colonna (x): la prima riga di pixel chiari dall'alto; -1 se coperta dall'altra clip.
                fun rowAt(bm: Bitmap, x: Int): Int {
                    for (y in 0 until bm.height) {
                        val px = bm.getPixel(x, y)
                        if (Color.red(px) > 200 && Color.green(px) > 200 && Color.blue(px) > 200) return y
                    }
                    return -1
                }
                fun luma(bm: Bitmap): Float {
                    var acc = 0.0; var n = 0
                    for (y in 0 until bm.height step 8) for (x in 0 until bm.width step 8) { val px = bm.getPixel(x, y); acc += Color.red(px) + Color.green(px) + Color.blue(px); n++ }
                    return (acc / n / 3).toFloat()
                }
                val rows = ArrayList<String>()
                var prevL = -1; var prevR = -1; var prevLuma = -1f
                var steps = 0; var repeats = 0; var skips = 0; var jumps = 0
                for (i in 0 until count) {
                    val bm = r.getFrameAtIndex(i) ?: continue
                    val sx = bm.width * 15 / 100; val dx = bm.width * 85 / 100
                    val yl = rowAt(bm, sx); val yr = rowAt(bm, dx); val lu = luma(bm)
                    bm.recycle()
                    fun step(prev: Int, cur: Int): Int? = if (prev < 0 || cur < 0) null else (cur - prev + (H - 8)) % (H - 8)
                    listOfNotNull(step(prevL, yl), step(prevR, yr)).forEach { d ->
                        steps++
                        when {
                            d == STEP || d == STEP + 1 || d == STEP - 1 -> {}
                            d == 0 -> repeats++
                            d in 2 * STEP - 1..2 * STEP + 1 -> skips++
                            // Cambio di clip sotto la colonna (prima A, poi B): non è un salto del movimento.
                            else -> {}
                        }
                    }
                    if (prevLuma >= 0 && abs(lu - prevLuma) > 25f) jumps++
                    rows += "$i:$yl/$yr/${lu.toInt()}"
                    prevL = yl; prevR = yr; prevLuma = lu
                }
                val line = "${t.name}: $count fotogrammi, passi $steps, ripetuti $repeats, saltati $skips, sbalzi di luce $jumps"
                android.util.Log.i("VivoVideoTest", "$line\n" + rows.joinToString(" "))
                report.append(line).append('\n')
                bad += repeats + skips + jumps
            } finally { r.release() }
        }
        android.util.Log.i("VivoVideoTest", "Fluidità transizioni:\n$report")
        assertTrue("transizioni non fluide:\n$report", bad == 0)
    }

    /**
     * Passaggio con maschera nel video esportato (segnalazione: "funziona in anteprima ma non in export").
     * Maschera finta: la "persona" è la metà sinistra del quadro. Dopo il taglio, a sinistra deve restare la clip prima
     * (rossa) per la prima metà della transizione, a destra c'è già la successiva (blu).
     */
    @Test fun maskPassInExport() {
        val a = makeMotionVideo("moto_a.mp4", 4, Color.rgb(150, 20, 20))
        val b = makeMotionVideo("moto_b.mp4", 4, Color.rgb(20, 20, 150))
        val side = 256; val n = 70
        val maskFile = File(dir(), "maschera_sinistra.bin")
        java.io.DataOutputStream(maskFile.outputStream().buffered()).use { o ->
            o.writeInt(n); o.writeInt(side)
            val one = ByteArray(side * side) { i -> if (i % side < side / 2) 255.toByte() else 0 }
            repeat(n) { o.write(one) }
        }
        val fx = PersonFx(mode = PersonMode.BLUR, maskFile = maskFile.absolutePath, fromUs = 0, rateHz = 15, count = n)
        val p = Project(name = "maschera", clips = listOf(
            Clip(id = "a", uri = a.toString(), sourceDurationUs = 4_000_000, trimStartUs = 0, trimEndUs = 2_000_000, width = W, height = H, fps = 60f)
                .copy(person = fx, transitionOut = TransitionSpec(TransitionType.MASK_PASS, 1_200_000)),
            Clip(id = "b", uri = b.toString(), sourceDurationUs = 4_000_000, trimStartUs = 1_000_000, trimEndUs = 3_000_000, width = W, height = H, fps = 60f),
        ))
        val cf = p.crossfades().single()
        android.util.Log.i("VivoVideoTest", "maschera: $cf")
        val uri = exportAndCheckUri(p)
        val r = MediaMetadataRetriever()
        val rows = ArrayList<String>()
        var leftRed = 0; var rightBlue = 0; var checked = 0
        try {
            r.setDataSource(ctx, uri)
            val count = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toInt() ?: 0
            fun tone(bm: Bitmap, x: Int): Char {
                var rr = 0; var bb = 0
                for (y in bm.height / 4 until bm.height * 3 / 4 step 6) { val px = bm.getPixel(x, y); rr += Color.red(px); bb += Color.blue(px) }
                return if (rr > bb * 2) 'R' else if (bb > rr * 2) 'B' else '?'
            }
            for (i in 100 until minOf(count, 200)) {
                val bm = r.getFrameAtIndex(i) ?: continue
                val l = tone(bm, bm.width * 15 / 100); val rt = tone(bm, bm.width * 85 / 100)
                bm.recycle()
                rows += "$i:$l$rt"
                // Dal taglio (120) per un quarto di secondo: la persona (sinistra) è ancora la clip prima.
                if (i in 123..135) { checked++; if (l == 'R') leftRed++; if (rt == 'B') rightBlue++ }
            }
        } finally { r.release() }
        android.util.Log.i("VivoVideoTest", "maschera esportata: sinistra rossa $leftRed/$checked, destra blu $rightBlue/$checked\n" + rows.joinToString(" "))
        assertTrue("passaggio con maschera non visibile nel video esportato: sinistra rossa $leftRed/$checked, destra blu $rightBlue/$checked\n" + rows.joinToString(" "),
            leftRed >= checked - 1 && rightBlue >= checked - 1)
    }

    /**
     * Come il progetto del collaudo (29/10): 8 clip intere, lo scontorno fatto prima, poi le transizioni scelte una alla
     * volta come nell'app (con lo spazio fatto da makeRoomForTransition): il passaggio con maschera deve esserci nel video
     * esportato (sul telefono risultava un taglio netto).
     */
    @Test fun maskPassAmongOtherTransitionsInExport() {
        val a = makeMotionVideo("moto_a.mp4", 4, Color.rgb(150, 20, 20))
        val b = makeMotionVideo("moto_b.mp4", 4, Color.rgb(20, 20, 150))
        val side = 256; val n = 62
        val maskFile = File(dir(), "maschera_sinistra2.bin")
        java.io.DataOutputStream(maskFile.outputStream().buffered()).use { o ->
            o.writeInt(n); o.writeInt(side)
            val one = ByteArray(side * side) { i -> if (i % side < side / 2) 255.toByte() else 0 }
            repeat(n) { o.write(one) }
        }
        var p = Project(name = "come il collaudo", clips = (0 until 8).map { i ->
            Clip(id = "c$i", uri = (if (i % 2 == 0) a else b).toString(), sourceDurationUs = 4_000_000, width = W, height = H, fps = 60f)
        })
        // Scontorno sulla prima clip intera (maschere per tutto il file), poi le transizioni.
        // Stesso ordine del progetto del collaudo (30/10): la maschera è la quinta transizione, dopo quattro a due clip.
        p = p.replaceClip(p.clips[4].copy(person = PersonFx(mode = PersonMode.BLUR, maskFile = maskFile.absolutePath, fromUs = 0, rateHz = 15, count = n, precise = true)))
        val order = listOf(TransitionType.WIPE, TransitionType.WIPE_DIAG, TransitionType.CLOCK, TransitionType.STEPS, TransitionType.MASK_PASS, TransitionType.OVERLAY)
        order.forEachIndexed { i, t ->
            val c = p.clips[i]
            p = p.replaceClip(c.copy(transitionOut = TransitionSpec(t, t.defaultUs ?: 500_000)))
            p.makeRoomForTransition(i)?.let { (q, _) -> p = q }
        }
        val cfs = p.crossfades()
        android.util.Log.i("VivoVideoTest", "collaudo: ${cfs.joinToString { "${it.index}:${it.type} ${it.beforeUs}/${it.afterUs}" }}")
        val mp = cfs.firstOrNull { it.type == TransitionType.MASK_PASS }
        assertTrue("passaggio con maschera non preparato: $cfs", mp != null)
        val uri = exportAndCheckUri(p)
        val cutFrame = (mp!!.cutUs * 60 / 1_000_000).toInt()
        val r = MediaMetadataRetriever()
        val rows = ArrayList<String>()
        var leftRed = 0; var rightBlue = 0; var checked = 0
        try {
            r.setDataSource(ctx, uri)
            fun tone(bm: Bitmap, x: Int): Char {
                var rr = 0; var bb = 0
                for (y in bm.height / 4 until bm.height * 3 / 4 step 6) { val px = bm.getPixel(x, y); rr += Color.red(px); bb += Color.blue(px) }
                return if (rr > bb * 2) 'R' else if (bb > rr * 2) 'B' else '?'
            }
            for (i in cutFrame - 10 until cutFrame + 40) {
                val bm = r.getFrameAtIndex(i) ?: continue
                val l = tone(bm, bm.width * 15 / 100); val rt = tone(bm, bm.width * 85 / 100)
                bm.recycle()
                rows += "$i:$l$rt"
                if (i in cutFrame + 3..cutFrame + 15) { checked++; if (l == 'R') leftRed++; if (rt == 'B') rightBlue++ }
            }
        } finally { r.release() }
        val msg = "collaudo esportato (taglio al fotogramma $cutFrame): sinistra rossa $leftRed/$checked, destra blu $rightBlue/$checked\n" + rows.joinToString(" ")
        android.util.Log.i("VivoVideoTest", msg)
        assertTrue(msg, leftRed >= checked - 1 && rightBlue >= checked - 1)
    }

    /** Intervista doppia a schermo diviso: due video sovrapposti uno dopo l'altro, chi ascolta fermo, cartelli: esportazione vera. */
    @Test fun exportInterviewSplit() {
        val a = video("A", videoA)
        val b = video("B", videoB)
        val qas = listOf(
            InterviewQA("Come ti chiami?", Span(500_000, 2_000_000), Span(1_000_000, 2_200_000)),
            InterviewQA("Che lavoro fai?", Span(3_000_000, 4_500_000), null),
        )
        val blackFile = save(frame(Color.BLACK, "", W, H).also { Canvas(it).drawColor(Color.BLACK) }, "nero.jpg", png = false)
        val black = Clip(uri = Uri.fromFile(blackFile).toString(), sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 5_000_000, isPhoto = true, width = W, height = H, fill = true)
        val still = Layer(uri = photo.toString(), width = 800, height = 600)
        val p = Interview.build("intervista", a, b, qas, InterviewOptions(format = InterviewFormat.SPLIT, nameA = "Anna", nameB = "Bruno", cardUs = 1_000_000), black, W, H) { _, _ -> still }
        assertEquals(3, p.layers.count { it.isVideo })
        exportAndCheck(p)
    }

    /** Le transizioni dal video dell'utente (tendine, orologio, scalini, sovrapposizione, taglio invisibile): esportazione vera. */
    @Test fun exportNewestTransitions() {
        val cuts = TransitionType.entries.filter { it.newest && it != TransitionType.MASK_PASS }
        val srcs = listOf(videoA, videoB, videoC)
        val clips = (0..cuts.size).map { i ->
            video("n$i", srcs[i % 3], s = 1_000_000, e = 2_500_000).copy(transitionOut = cuts.getOrNull(i)?.let { TransitionSpec(it, 800_000) })
        }
        exportAndCheck(Project(name = "nuove transizioni", clips = clips, texts = listOf(TextItem(text = "Prova", startUs = 0, durationUs = 2_000_000))))
    }

    /** Tendenze 2026: le nuove transizioni, gli effetti combinati nella stessa scena (con i battiti) e i nuovi testi. */
    @Test fun exportTrendKit() {
        val cuts = TransitionType.entries.filter { it.trend }
        val looks = listOf(
            listOf(FxType.FLASHBEAT, FxType.BWBEAT, FxType.BOUNCE, FxType.GRAIN),
            listOf(FxType.HALATION, FxType.MUTED),
            listOf(FxType.DIGICAM, FxType.SHAKEBEAT),
            listOf(FxType.HALATION, FxType.DIGICAM, FxType.GRAIN, FxType.MUTED, FxType.FLASHBEAT),
        )
        val clips = (0..cuts.size).map { i ->
            video("t$i", listOf(videoA, videoB, videoC)[i % 3], s = 1_000_000, e = 2_500_000)
                .copy(transitionOut = cuts.getOrNull(i)?.let { TransitionSpec(it, 300_000) }, fx = looks[i % looks.size].map { ClipFx(it) })
        }
        val texts = TextPreset.entries.filter { it.trend }.mapIndexed { i, pr ->
            pr.applyTo(TextItem(text = if (pr == TextPreset.TITOLONE) "Supercalifragilistico" else "Tendenza ${pr.label}", startUs = i * 1_400_000L, durationUs = 1_800_000))
        }
        val m = MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 8_000_000,
            beatsUs = (1 until 60).map { it * 500_000L }, bpm = 120f)
        exportAndCheck(Project(name = "tendenze", clips = clips, texts = texts, music = listOf(m)))
    }

    /** Titoli di coda su nero dopo il video: il rullo (più alto del quadro) scorre e si esporta. */
    @Test fun exportCreditsRoll() {
        val base = Project(name = "titoli", clips = listOf(video("a", videoA, s = 0, e = 3_000_000), video("b", videoB, s = 0, e = 3_000_000)))
        val rows = (1..14).map { Credits.Row("Ruolo $it", "Nome Cognome $it") }
        val text = Credits.text("Grazie per la visione", rows, listOf("\"Brano\" di Autore (CC BY 4.0)"))
        val p = base.withCredits(text, 6_000_000, onBlack = true, black = photoClip("nero"))
        assertEquals(12_000_000L, p.durationUs)
        exportAndCheck(p)
    }

    /** Privacy: i modelli (volti, scritte, oggetti) si caricano e l'analisi gira; i riquadri si oscurano nel video esportato. */
    @Test fun redactAnalyzesAndExports() {
        val clip = video("r", videoA, s = 0, e = 3_000_000)
        val found = kotlinx.coroutines.runBlocking {
            it.fabriziodavi.vivovideo.engine.Redactor.analyze(ctx, clip,
                Redaction(faces = true, plates = true, objectSeed = RBox(0.4f, 0.4f, 0.2f, 0.2f)), 1_000_000) {}
        }
        assertTrue("nessun fotogramma analizzato", found.count > 0 && found.file.isNotEmpty())
        val box = RBox(0.3f, 0.3f, 0.4f, 0.3f)
        val p = Project(name = "privacy", clips = listOf(
            clip.copy(redact = found),
            photoClip("p1").copy(redact = Redaction(boxes = listOf(box, box.copy(x = 0.1f, round = true)), style = RedactStyle.PIXEL)),
            photoClip("p2").copy(redact = Redaction(boxes = listOf(box), style = RedactStyle.BLUR)),
            photoClip("p3").copy(redact = Redaction(boxes = listOf(box), style = RedactStyle.BLACK)),
        ))
        exportAndCheck(p)
    }

    /** Il ripiego dell'app quando il decodificatore hardware si guasta: con quello software si esporta lo stesso. */
    @Test fun softwareDecoderExports() {
        val seq = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .addItem(EditedMediaItem.Builder(MediaItem.fromUri(videoA)).setRemoveAudio(true).build()).build()
        val out = File(dir(), "software.mp4").apply { delete() }
        transform(Composition.Builder(listOf(seq)).build(), out, timeoutS = 180, softwareDecoder = true)
        assertTrue(out.length() > 1000)
    }

    /** Video sovrapposto: il tratto si prepara (con il video vero dentro) e l'anteprima lo riproduce al posto dell'immagine ferma. */
    @Test fun overlaidVideoStretchRendersAndPlays() {
        val p = Project(
            name = "pip", clips = listOf(video("a", videoA), video("b", videoB)),
            layers = listOf(Layer(uri = videoC.toString(), name = "pip", width = W, height = H, isVideo = true,
                sourceDurationUs = 6_000_000, startUs = 1_000_000, durationUs = 3_000_000, pose = Transform(scale = 0.4f))),
        )
        val factory = CompositionFactory(ctx)
        val (w, h) = factory.outputSize(p, 1080)
        val jobs = CrossfadePreview.jobs(p, w, h)
        assertTrue("nessun tratto da preparare", jobs.size == 1 && jobs[0].cf == null)
        val job = jobs[0]
        val out = CrossfadePreview.file(ctx, job.key)
        val c = factory.build(job.sub, ExportOptions(), forPreview = true, nativeAudio = null, allowMulti = true,
            sizeOverride = w to h, crossfadesOverride = null)
        try { transform(c, out, timeoutS = 120) } catch (e: Throwable) { out.delete(); transform(c, out, timeoutS = 120) }
        assertTrue(out.length() > 1000)
        val shown = CrossfadePreview.previewProject(ctx, p, w, h)
        assertTrue("il tratto non è stato usato", shown.clips.any { it.baked } && shown.layers.none { it.isVideo })
        playFor(p, seconds = 6)
    }

    /** Copia leggera come la prepara l'app (720p, stessa durata) e anteprima che la usa. */
    @Test fun proxyCopyIsMadeAndUsed() {
        val src = videoA.toString()
        val out = Proxies.file(ctx, src)
        out.delete()
        val (pw, ph) = Proxies.size(W * 3, H * 3)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(videoA))
            .setEffects(androidx.media3.transformer.Effects(emptyList(), listOf(
                androidx.media3.effect.Presentation.createForWidthAndHeight(pw, ph, androidx.media3.effect.Presentation.LAYOUT_SCALE_TO_FIT),
            ))).build()
        transform(Composition.Builder(listOf(EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItem(item).build())).build(), out)
        assertTrue(Proxies.ready(ctx, src))
        val mmr = MediaMetadataRetriever().apply { setDataSource(out.absolutePath) }
        val ow = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val oh = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        mmr.release()
        assertTrue("copia ${ow}x$oh", minOf(ow, oh) == 720)
        assertTrue("durata $dur", abs(dur - 6000) < 150)
        // Clip "pesante" (finta 4K): l'anteprima prende la copia.
        val heavy = video("a", videoA).copy(width = 3840, height = 2160)
        val p = Project(name = "proxy", clips = listOf(heavy, video("b", videoB)))
        val shown = Proxies.forPreview(ctx, p)
        assertTrue(shown.clips[0].uri.startsWith("file:") && shown.clips[0].uri.contains("copie_leggere"))
        playFor(shown, seconds = 5)
    }

    /** Tutti i nostri shader si compilano anche nella versione HDR (luce lineare ↔ gamma). */
    @Test fun shadersCompileForHdr() {
        val l = Layer(uri = "x", width = W, height = H, mask = LayerMask.HEART)
        val effects = listOf(
            FxEffect(FxType.entries.map { ClipFx(it, 0.5f) }, listOf(500_000L), 0, 3_000_000, 500_000, 500_000),
            TransitionEffect(0, 3_000_000, TransitionType.GLITCH, 500_000, TransitionType.PRISM, 500_000),
            GradeEffect(Grade(exposure = 0.5f, contrast = 0.2f)),
            BlurFillEffect(W, H),
            LayerMaskEffect(l),
            DenoiseEffect(0.5f),
            HighlightEffect(0.5f),
            WowFxEffect(FxType.entries.filter { it.wow2 }.map { ClipFx(it, 0.6f) }, listOf(500_000L)),
        )
        val error = AtomicReference<String?>(null)
        val done = CountDownLatch(1)
        Thread {
            try {
                val display = GlUtil.getDefaultEglDisplay()
                val egl = GlUtil.createEglContext(display)
                GlUtil.createFocusedPlaceholderEglSurface(egl, display)
                HdrGl.gain = 10f
                for (hdr in listOf(false, true)) for (e in effects) {
                    try { e.toGlShaderProgram(ctx, hdr).release() } catch (t: Throwable) { error.set("${e.javaClass.simpleName} hdr=$hdr: ${chain(t)}"); break }
                }
                HdrGl.gain = 1f
            } catch (t: Throwable) { error.set(chain(t)) } finally { done.countDown() }
        }.start()
        assertTrue(done.await(60, TimeUnit.SECONDS))
        error.get()?.let { fail(it) }
    }

    /** Con l'opzione HDR su video SDR l'esportazione va comunque e il risultato dice il colore vero. */
    @Test fun exportWithHdrOption() {
        val p = Project(name = "hdr", clips = listOf(video("a", videoA).copy(hdr = 1), video("b", videoB, s = 1_000_000, e = 3_000_000)))
        exportAndCheck(p, options = ExportOptions(hdr = true))
        assertTrue(lastDetails, lastDetails.contains("Colore: SDR") || lastDetails.contains("10 bit"))
    }

    private fun redAt(uri: Uri, us: Long): Int {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val b = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)!!
            return Color.red(b.getPixel(4, 4))
        } finally { r.release() }
    }

    /** Al contrario: il primo fotogramma è l'ultimo dell'originale (rosso scuro), l'audio c'è, la durata torna. */
    @Test fun reverseClip() {
        val c = video("a", videoA)
        val r = runBlocking { Reverser.reverse(ctx, c) {} }
        assertTrue("durata ${r.durationUs}", abs(r.durationUs - 6_000_000) < 150_000)
        val uri = Uri.fromFile(r.file)
        assertTrue("originale: inizio rosso pieno", redAt(videoA, 100_000) > 230)
        val first = redAt(uri, 100_000); val last = redAt(uri, 5_800_000)
        assertTrue("al contrario: inizio $first (atteso rosso scuro)", first in 170..229)
        assertTrue("al contrario: fine $last (atteso rosso pieno)", last > 230)
        val m = MediaMetadataRetriever().apply { setDataSource(ctx, uri) }
        assertTrue("niente audio", m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"); m.release()
        // E la clip al contrario si esporta come le altre.
        val rc = ClipTools.reversed(c, uri.toString(), r.durationUs, r.width, r.height)
        exportAndCheck(Project(name = "contrario", clips = listOf(rc, video("b", videoB))))
        // Video verticale: la copia al contrario resta verticale (prima usciva girata di 90°).
        val v = video("v", videoA, s = 0, e = 2_000_000).copy(width = 1080, height = 1920)
        val rv = runBlocking { Reverser.reverse(ctx, v) {} }
        assertTrue("misure ${rv.width}×${rv.height}", rv.width < rv.height)
        val mv = MediaMetadataRetriever().apply { setDataSource(ctx, Uri.fromFile(rv.file)) }
        val rot = mv.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
        val vw = mv.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val vh = mv.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt(); mv.release()
        val (dw, dh) = if (rot % 180 == 0) vw to vh else vh to vw
        assertTrue("si vede ${dw}×${dh} (rotazione $rot)", dw < dh)
    }

    /** Video che trema (macchie spostate a caso a ogni fotogramma): la misura trova il tremolio e l'esportazione lo usa. */
    @Test fun stabilizeShakyVideo() {
        val out = File(ctx.cacheDir, "prove/tremolio.mp4")
        if (!out.exists()) {
            val rnd = java.util.Random(7)
            val base = Bitmap.createBitmap(W + 80, H + 80, Bitmap.Config.ARGB_8888)
            Canvas(base).apply {
                drawColor(Color.DKGRAY)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                repeat(60) { p.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)); drawCircle(rnd.nextFloat() * (W + 80), rnd.nextFloat() * (H + 80), 8f + rnd.nextFloat() * 30f, p) }
            }
            val seq = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            for (k in 0 until 60) {
                val ox = if (k % 2 == 0) 40 + 12 else 40 - 12
                val oy = 40 + (k % 3 - 1) * 8
                val f = Bitmap.createBitmap(base, ox, oy, W, H)
                val img = File(ctx.cacheDir, "prove/trem_$k.png").also { file -> file.outputStream().use { f.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                seq.addItem(EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(img)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(33).build())
                    .setDurationUs(33_333).setFrameRate(30).build())
            }
            transform(Composition.Builder(listOf(seq.build())).build(), out)
        }
        val c = Clip(id = "t", uri = Uri.fromFile(out).toString(), sourceDurationUs = 2_000_000, trimEndUs = 1_900_000, width = W, height = H, fps = 30f)
        val s = runBlocking { Stabilizer.analyze(ctx, c, 0.7f) {} }
        assertTrue("campioni ${s.dx.size}", s.dx.size >= 50)
        // Fra un fotogramma e l'altro l'immagine salta di circa 24 px su $W: la misura deve vederlo.
        val steps = (1 until s.dx.size).map { abs(s.dx[it] - s.dx[it - 1]) }
        assertTrue("salti misurati ${steps.take(8)}", steps.count { it > 0.02f } > steps.size / 3)
        val plan = StabMath.plan(s)
        assertTrue("zoom ${plan.zoom}", plan.zoom > 1.02f)
        exportAndCheck(Project(name = "stab", clips = listOf(c.copy(stab = s), video("a", videoA))))
    }

    /** Fermo immagine dall'editor: compare una foto in mezzo alla clip e il progetto si esporta. */
    @Test fun freezeFrameFromEditor() {
        val vmRef = AtomicReference<EditorViewModel>()
        val made = CountDownLatch(1)
        main.post {
            val vm = EditorViewModel(ctx.applicationContext as android.app.Application)
            vm.openProject(Project(name = "fermo", clips = listOf(video("a", videoA), video("b", videoB))), startPreview = false)
            vm.seekTo(3_000_000)
            vm.freezeFrame()
            vmRef.set(vm); made.countDown()
        }
        assertTrue(made.await(10, TimeUnit.SECONDS))
        var p: Project? = null
        repeat(100) { if (p == null) { Thread.sleep(200); p = vmRef.get().project?.takeIf { q -> q.clips.size == 4 } } }
        val q = p ?: run { fail("il fermo immagine non è comparso: ${vmRef.get().message}"); return }
        assertTrue(q.clips[1].freeze && q.clips[1].isPhoto)
        assertTrue(abs(q.durationUs - 14_000_000) < 50_000)
        // L'editor si chiude (anteprima e preparazioni ferme) prima di esportare.
        val closed = CountDownLatch(1)
        main.post { vmRef.get().closeProject(); closed.countDown() }
        closed.await(10, TimeUnit.SECONDS)
        exportAndCheck(q)
    }

    /** Video mosso nei primi 3 s e nitido negli ultimi 3: il momento migliore cade nella parte nitida. */
    @Test fun bestMomentFindsTheSharpPart() {
        val out = File(ctx.cacheDir, "prove/mosso_nitido.mp4")
        if (!out.exists()) {
            val sharp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b); c.drawColor(Color.GRAY)
                val p = Paint().apply { color = Color.WHITE }
                for (x in 0 until W step 8) for (y in 0 until H step 8) if ((x / 8 + y / 8) % 2 == 0) c.drawRect(x.toFloat(), y.toFloat(), x + 8f, y + 8f, p)
            }
            val blurry = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b); c.drawColor(Color.GRAY)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; maskFilter = android.graphics.BlurMaskFilter(60f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
                c.drawCircle(W / 2f, H / 2f, H / 3f, p)
            }
            val fs = save(sharp, "nitido.png", png = true); val fb = save(blurry, "mosso.png", png = true)
            val seq = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            for (k in 0 until 12) {
                val f = if (k < 6) fb else fs
                seq.addItem(EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(500).build())
                    .setDurationUs(500_000).setFrameRate(30).build())
            }
            transform(Composition.Builder(listOf(seq.build())).build(), out)
        }
        val uri = Uri.fromFile(out).toString()
        val scan = runBlocking { SmartAnalyzer.scan(ctx, uri, false, 6_000_000) }
        assertTrue("campioni ${scan.samples.size}", scan.samples.size >= 4)
        val best = Moments.bestStarts(scan, 2_000_000, 1).first()
        assertTrue("migliore a $best (${scan.samples.map { it.tUs to it.sharp.toInt() }})", best >= 2_800_000)
        // Seconda volta: dalla cartella dei risultati, subito.
        assertTrue(SmartAnalyzer.cached(ctx, uri, 6_000_000) != null)
    }

    /** Scontorno della persona: le maschere si calcolano, la clip (sfondo sfocato) e il video sovrapposto (solo persona) si esportano. */
    @Test fun personMasksAndExport() {
        val fx = runBlocking { PersonMasks.analyze(ctx, videoA.toString(), 0, 6_000_000, PersonMode.BLUR, onProgress = {}) }
        assertTrue("maschere ${fx.count}", fx.count in 80..95)
        val m = PersonMasks.load(fx.maskFile)!!
        assertTrue(m.count == fx.count && m.side == PersonMasks.SIDE)
        val clip = video("a", videoA).copy(person = fx)
        val pip = Layer(uri = videoB.toString(), name = "pip", width = W, height = H, isVideo = true, sourceDurationUs = 6_000_000,
            startUs = 1_000_000, durationUs = 3_000_000, pose = Transform(scale = 0.5f), person = fx.copy(mode = PersonMode.REMOVE))
        exportAndCheck(Project(name = "persona", clips = listOf(clip, video("c", videoC)), layers = listOf(pip)))
        // Anche "segui il soggetto" (orizzontale in verticale) passa dall'esportazione.
        val follow = video("b", videoB).copy(fill = true, reframe = Reframe(listOf(0, 3_000_000), listOf(0.2f, 0.8f), listOf(0.5f, 0.5f)))
        exportAndCheck(Project(name = "segui", clips = listOf(follow), format = VideoFormat.VERTICAL))
    }

    /** Scontorno "Preciso" (Google): se l'emulatore non ha i servizi Google si torna a quello veloce, senza errori. */
    @Test fun precisePersonCutoutOrFallback() {
        val status = mutableListOf<String>()
        val fx = runBlocking { PersonMasks.analyze(ctx, videoA.toString(), 0, 2_000_000, PersonMode.BLUR, {}, precise = true) { status += it } }
        android.util.Log.i("VivoVideoTest", "scontorno preciso: ${fx.precise}, stati $status")
        val m = PersonMasks.load(fx.maskFile)!!
        assertTrue("maschere ${fx.count}", fx.count in 25..35 && m.count == fx.count)
        assertEquals(if (fx.precise) PersonMasks.PRECISE_SIDE else PersonMasks.SIDE, m.side)
        exportAndCheck(Project(name = "preciso", clips = listOf(video("a", videoA, s = 0, e = 2_000_000).copy(person = fx))))
    }

    /** Copia di sicurezza con i video, riaperta: il progetto si esporta come prima. E un file spostato si ritrova per nome. */
    @Test fun backupWithMediaAndRelink() {
        val p = Project(name = "copia", clips = listOf(video("a", videoA), photoClip("f"), video("b", videoB, s = 1_000_000, e = 3_000_000)))
        val file = File(ctx.cacheDir, "prova.vivo")
        file.outputStream().use { Backup.export(ctx, p, withMedia = true, out = it) {} }
        val imp = file.inputStream().use { Backup.import(ctx, it, emptySet()) }
        assertTrue("i video non sono nella copia", imp.project.clips.all { it.uri.startsWith("file:") && it.uri.contains("importati") })
        exportAndCheck(imp.project)

        // Un video nella galleria con un nome unico: lo si ritrova per nome e dimensione.
        val name = "vivo_prova_${System.currentTimeMillis()}.mp4"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/VivoProve")
        }
        val dst = ctx.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        try {
            val bytes = File(videoA.path!!).readBytes()
            ctx.contentResolver.openOutputStream(dst)!!.use { it.write(bytes) }
            val found = Backup.findByName(ctx, MediaRef("content://sparito/1", name, bytes.size.toLong()))
            assertTrue("non ritrovato: $found", found != null && Backup.exists(ctx, found))
        } finally { ctx.contentResolver.delete(dst, null, null) }
    }

    /** Testo con un font aggiunto (copiato da quelli dell'app): si disegna e si esporta. */
    @Test fun customFontText() {
        val f = File(ctx.filesDir, "fonts/prova.ttf").apply { parentFile!!.mkdirs() }
        ctx.resources.openRawResource(R.font.pacifico).use { s -> f.outputStream().use { s.copyTo(it) } }
        val t = TextItem(text = "Font mio", startUs = 0, durationUs = 3_000_000, fontFile = f.absolutePath)
        val tf = TextRenderer.typeface(ctx, t)
        assertTrue(tf != android.graphics.Typeface.DEFAULT_BOLD)
        exportAndCheck(Project(name = "font", clips = listOf(video("a", videoA)), texts = listOf(t)))
    }
}
