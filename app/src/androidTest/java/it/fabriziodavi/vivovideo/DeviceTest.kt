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
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        fun transform(c: Composition, out: File, timeoutS: Long = 240) {
            val done = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            main.post {
                Transformer.Builder(ctx).setMaxDelayBetweenMuxerSamplesMs(30_000).setVideoMimeType(MimeTypes.VIDEO_H264)
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
    private fun wowProject(): Project {
        val wow = listOf(TransitionType.GLITCH, TransitionType.PRISM, TransitionType.LEAK, TransitionType.IRIS, TransitionType.PIXELATE, TransitionType.SHAKE)
        val clips = (0..wow.size).map { i ->
            video("w$i", listOf(videoA, videoB, videoC)[i % 3], s = 1_000_000, e = 2_500_000)
                .copy(transitionOut = wow.getOrNull(i)?.let { TransitionSpec(it, 600_000) })
        }
        val anims = listOf(TextAnim.DROP, TextAnim.RISE, TextAnim.WAVE, TextAnim.GLITCH, TextAnim.SHINE)
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
        } catch (e: java.net.UnknownHostException) {
            org.junit.Assume.assumeNoException("nessuna rete sull'emulatore", e); return
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
    @Test fun exportAutoVideo() {
        val srcs = listOf(video("a", videoA), photoClip("p1"), video("b", videoB), photoClip("p2"), video("c", videoC))
            .map { it.copy(trimStartUs = 0, trimEndUs = it.sourceDurationUs.coerceAtMost(Clip.PHOTO_MAX_US)) }
        val m = MusicTrack(uri = music.toString(), name = "musica", sourceDurationUs = 30_000_000, lengthUs = 30_000_000,
            beatsUs = (1 until 60).map { it * 500_000L }, bpm = 120f)
        listOf(AutoStyle.CINEMA, AutoStyle.ENERGICO).forEach { s ->
            // Senza compositore a più ingressi: l'emulatore regge pochi decodificatori insieme (il telefono sì).
            exportAndCheck(AutoEdit.build("auto ${s.label}", srcs, m, AutoOptions(style = s, title = "Prova", subtitle = "Ottobre 2026")), multi = false)
        }
    }

    @Test fun exportWowTransitionsAndTexts() {
        exportAndCheck(wowProject())
    }

    @Test fun previewPlaysWow() {
        playFor(wowProject())
    }

    private fun exportAndCheck(p: Project, extraLayers: List<Layer> = emptyList(), multi: Boolean = true) { exportAndCheckUri(p, extraLayers, multi) }

    private fun exportAndCheckUri(p: Project, extraLayers: List<Layer> = emptyList(), multi: Boolean = true): Uri {
        val project = p.copy(layers = p.layers + extraLayers)
        Safety.setMultiExport(ctx, multi)
        val state = AtomicReference<ExportState>(ExportState.Idle)
        val done = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val exporter = Exporter(ctx, scope, CompositionFactory(ctx)) { s ->
            state.set(s)
            if (s is ExportState.Done || s is ExportState.Failed) done.countDown()
        }
        main.post { exporter.start(project, ExportOptions()) }
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
    private fun playFor(p: Project, seconds: Int = 8) {
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
        assertTrue("l'anteprima non parte", ready.await(15, TimeUnit.SECONDS))
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
        assertTrue("anteprima lenta: avanzata di $advanced ms invece di ~$expected (posizioni: $trace, ripartenze: $recoveries)", advanced > expected * (if (recoveries > 0) 0.4 else 0.7))
        assertTrue("anteprima senza immagini: ${frames.get() - f0} fotogrammi (posizioni: $trace)", frames.get() - f0 > seconds * 5)
    }

    @Test fun previewPlaysEveryFeature() {
        playFor(fullProject())
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
            // Come nell'app: se si inceppa si riprova una volta.
            try { transform(c, out, timeoutS = 120) } catch (e: Throwable) { out.delete(); transform(c, out, timeoutS = 120) }
            assertTrue(out.length() > 1000)
        }
        // L'anteprima con le dissolvenze pronte scorre.
        playFor(p, seconds = 8)
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
}
