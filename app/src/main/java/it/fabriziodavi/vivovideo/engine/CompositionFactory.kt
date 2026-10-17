package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import it.fabriziodavi.vivovideo.CubeLut
import it.fabriziodavi.vivovideo.DenoiseEffect
import it.fabriziodavi.vivovideo.HighlightEffect
import it.fabriziodavi.vivovideo.Look
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.KeyColor
import it.fabriziodavi.vivovideo.model.Layer
import it.fabriziodavi.vivovideo.model.LayerBorder
import it.fabriziodavi.vivovideo.model.LayerMask
import it.fabriziodavi.vivovideo.model.packLanes
import it.fabriziodavi.vivovideo.model.FxType
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.PersonMode
import it.fabriziodavi.vivovideo.model.SpeedCurve
import it.fabriziodavi.vivovideo.model.Transform
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import java.io.File

enum class OutputSize(val label: String, val shortSide: Int?) {
    ORIGINAL("Originale", null),
    P2160("4K", 2160),
    P1080("1080p", 1080),
    P720("720p", 720),
}

enum class OutputFps(val label: String, val max: Int?) {
    ORIGINAL("Originali", null),
    F60("60", 60),
    F30("30", 30),
}

enum class OutputCodec(val label: String, val mime: String) {
    H265("H.265", MimeTypes.VIDEO_H265),
    H264("H.264", MimeTypes.VIDEO_H264),
}

data class ExportOptions(
    val size: OutputSize = OutputSize.ORIGINAL,
    val fps: OutputFps = OutputFps.ORIGINAL,
    val codec: OutputCodec = OutputCodec.H265,
    /** Audio con tono naturale (motore nativo) nei tratti a velocità diversa da 1×. */
    val keepPitch: Boolean = true,
    /** Volume uniformato a -14 LUFS (come YouTube e Instagram). */
    val loudness: Boolean = true,
    /** Tetto alla qualità (bit al secondo), per i video da mandare in chat. */
    val maxBitrate: Int? = null,
    /** HDR a 10 bit (H.265) come l'ha girato il telefono, invece di convertire in SDR. */
    val hdr: Boolean = false,
)

/** Preimpostazioni pronte per dove finirà il video. */
enum class ExportPreset(val label: String, val hint: String, val options: ExportOptions) {
    YOUTUBE_4K("YouTube 4K", "4K, fino a 60 fps, H.265", ExportOptions(OutputSize.P2160, OutputFps.F60, OutputCodec.H265)),
    YOUTUBE_HD("YouTube 1080p", "1080p, fino a 60 fps, H.264", ExportOptions(OutputSize.P1080, OutputFps.F60, OutputCodec.H264)),
    REELS("Reels e TikTok", "1080p, 30 fps, H.264 (meglio in verticale)", ExportOptions(OutputSize.P1080, OutputFps.F30, OutputCodec.H264)),
    WHATSAPP("WhatsApp", "720p, 30 fps, leggero", ExportOptions(OutputSize.P720, OutputFps.F30, OutputCodec.H264, maxBitrate = 3_000_000));

    /** Vale se le opzioni scelte sono proprio queste (il resto, come il volume, non conta). */
    fun matches(o: ExportOptions) = o.size == options.size && o.fps == options.fps && o.maxBitrate == options.maxBitrate &&
        (o.codec == options.codec || o.hdr)

    fun applyTo(o: ExportOptions) = o.copy(
        size = options.size, fps = options.fps, codec = if (o.hdr && this != WHATSAPP) OutputCodec.H265 else options.codec,
        maxBitrate = options.maxBitrate, hdr = o.hdr && this != WHATSAPP,
    )
}

/** Qualità (bit al secondo) e peso stimato del video esportato: la stessa formula dell'esportazione. */
object ExportEstimate {
    const val AUDIO_BPS = 192_000

    fun bitrate(outW: Int, outH: Int, outFps: Float, codec: OutputCodec, maxBitrate: Int? = null, attempt: Int = 0): Int {
        val top = if (attempt >= 1) 100_000_000.0 else 160_000_000.0
        var b = (45_000_000.0 * outW * outH * outFps / (3840.0 * 2160 * 30)).coerceIn(8_000_000.0, top).toInt()
        if (codec == OutputCodec.H264) b = minOf((b * 1.4).toInt(), top.toInt())
        maxBitrate?.let { b = minOf(b, it) }
        return b
    }

    fun bytes(outW: Int, outH: Int, outFps: Float, codec: OutputCodec, durationUs: Long, maxBitrate: Int? = null): Long =
        ((bitrate(outW, outH, outFps, codec, maxBitrate).toLong() + AUDIO_BPS) * durationUs / 8_000_000L)

    /** "circa 85 MB" / "circa 1,2 GB". */
    fun label(bytes: Long): String {
        val mb = bytes / 1_000_000.0
        return if (mb >= 1000) "circa " + String.format(java.util.Locale.ITALY, "%.1f GB", mb / 1000) else "circa ${maxOf(1, mb.toInt())} MB"
    }
}

/** Adatta la curva del modello all'interfaccia di Media3. */
class CurveSpeedProvider(private val curve: SpeedCurve) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = curve.speedAt(timeUs)
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = curve.nextChangeUs(timeUs) ?: C.TIME_UNSET
}

/** Traduce il progetto in una Composition di Media3, la stessa per anteprima ed export. */
class CompositionFactory(private val context: Context) {

    private val builtInLog by lazy {
        CubeLut.fromAsset(context, CubeLut.BUILT_IN_LOG, "vivo LOG → normale")
    }
    private var customLutCache: Pair<String, CubeLut>? = null

    private fun customLut(project: Project): CubeLut? {
        val path = project.color.lutPath ?: return null
        customLutCache?.let { if (it.first == path) return it.second }
        val lut = runCatching {
            File(path).inputStream().use { CubeLut.parse(it, project.color.lutName ?: "LUT") }
        }.getOrNull() ?: return null
        customLutCache = path to lut
        return lut
    }

    /** Catena colore di una clip: LOG → LUT → riduzione rumore → alte luci → filtro. */
    fun colorEffects(clip: Clip, project: Project): List<Effect> = if (clip.baked) emptyList() else buildList {
        val c = project.color
        if (clip.log) add(builtInLog.toEffect())
        customLut(project)?.let { add(it.toEffect()) }
        if (c.denoise > 0) add(DenoiseEffect(c.denoise / 100f))
        if (c.highlights > 0 || clip.log) add(HighlightEffect(c.highlights / 100f))
        addAll(runCatching { Look.valueOf(c.look) }.getOrDefault(Look.NONE).effects())
        // Correzione della singola clip, dopo LOG, LUT e filtro del progetto.
        if (!clip.grade.isIdentity) add(GradeEffect(clip.grade))
    }

    /** Persona scontornata, stabilizzazione e "segui il soggetto": sulla sorgente, prima dell'inquadratura. */
    private fun smartEffects(clip: Clip, startUs: Long, outW: Int, outH: Int): List<Effect> = if (clip.baked) emptyList() else buildList {
        clip.person?.takeIf { it.maskFile.isNotEmpty() && !clip.isPhoto }?.let { fx ->
            add(PersonEffect(fx) { t -> clip.trimStartUs + clip.speedCurve().sourceTimeAt(localTimeUs(t, startUs)) })
        }
        if (clip.stab != null && !clip.isPhoto) add(StabilizeTransformation(clip, startUs))
        if (clip.reframe != null && clip.fill) add(ReframeTransformation(clip, startUs, outW.toFloat() / outH))
    }

    private fun volumeProcessors(volume: Float): List<androidx.media3.common.audio.AudioProcessor> {
        if (volume == 1f) return emptyList()
        val p = androidx.media3.common.audio.ChannelMixingAudioProcessor()
        for (ch in 1..2) {
            p.putChannelMixingMatrix(
                androidx.media3.common.audio.ChannelMixingMatrix.createForConstantGain(ch, ch).scaleBy(volume)
            )
        }
        return listOf(p)
    }

    /** Dimensioni del video finale: orientamento e proporzioni del primo video (o della prima foto). */
    /** Clip nel quadro finale: riempi, adatta (bande nere) o adatta con sfondo sfocato. */
    private fun framing(clip: Clip, outW: Int, outH: Int): Effect = when {
        clip.blurBg -> BlurFillEffect(outW, outH)
        clip.fill -> Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        else -> Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)
    }

    fun outputSize(project: Project, shortSide: Int?): Pair<Int, Int> {
        // Il formato lo decide il primo video; con sole foto, la prima foto (al massimo in 4K).
        val first = project.clips.firstOrNull { !it.isPhoto } ?: project.clips.firstOrNull() ?: return 1920 to 1080
        val w = first.width.coerceAtLeast(2)
        val h = first.height.coerceAtLeast(2)
        val srcShort = if (first.isPhoto) minOf(w, h, 2160) else minOf(w, h)
        val target = minOf(shortSide ?: srcShort, srcShort)
        fun even(v: Long) = (v / 2 * 2).toInt()
        // Formato scelto: lato corto come sopra, l'altro dalle proporzioni (9:16, 16:9, 1:1).
        project.format.aspect?.let { a ->
            val s = target.toLong()
            return when {
                a < 1f -> even(s) to even((s / a).toLong())
                a > 1f -> even((s * a).toLong()) to even(s)
                else -> even(s) to even(s)
            }
        }
        val scale = target.toDouble() / srcShort
        return even((w * scale).toLong()) to even((h * scale).toLong())
    }

    /** Una riga di video sovrapposti: pause fino a ogni video, video senza audio alla misura "adattata". */
    private fun videoLayerSequence(lane: List<Layer>, outW: Int, outH: Int, keepAudio: Boolean, padToUs: Long): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(if (keepAudio) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO))
        var cursor = 0L
        lane.forEach { l ->
            if (l.startUs - cursor > 1_000) b.addGap(l.startUs - cursor)
            val item = MediaItem.Builder()
                .setUri(Uri.parse(l.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(l.sourceStartUs)
                        .setEndPositionUs(l.sourceStartUs + l.durationUs)
                        .build()
                )
                .build()
            val (lw, lh) = LayerCompositorSettings.layerFrameSize(l, outW, outH)
            b.addItem(
                EditedMediaItem.Builder(item)
                    .setDurationUs(l.sourceDurationUs)
                    .setRemoveAudio(!keepAudio || l.muted)
                    .setEffects(Effects(
                        if (keepAudio && !l.muted) volumeProcessors(l.volume) else emptyList(),
                        buildList<Effect> {
                            l.person?.takeIf { it.maskFile.isNotEmpty() }?.let { fx ->
                                add(PersonEffect(fx.copy(mode = PersonMode.REMOVE)) { t -> l.sourceStartUs + (t - l.startUs).coerceAtLeast(0) })
                            }
                            add(Presentation.createForWidthAndHeight(lw, lh, Presentation.LAYOUT_SCALE_TO_FIT))
                            if (l.shape != LayerMask.NONE || l.key != KeyColor.NONE || l.border != LayerBorder.NONE) add(LayerMaskEffect(l))
                        },
                    ))
                    .build()
            )
            cursor = maxOf(cursor, l.startUs) + l.durationUs
        }
        if (padToUs - cursor > 1_000) b.addGap(padToUs - cursor)
        return b.build()
    }

    /**
     * Seconda traccia per le dissolvenze incrociate: prima del taglio la testa della clip successiva,
     * dopo il taglio la coda della precedente, entrambe a tutto quadro; la trasparenza la regola il compositore.
     */
    private fun crossfadeSequence(project: Project, cfs: List<Project.Crossfade>, outW: Int, outH: Int, padToUs: Long): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        var cursor = 0L
        val starts = project.clipStartsUs()
        val beats = project.beatTimes()
        // Ogni pezzo ha gli stessi colori, inquadratura, keyframe ed effetti della sua clip: niente salti nella dissolvenza.
        // (I keyframe prima dell'inizio o dopo la fine della clip restano fermi sul primo/ultimo valore.)
        fun piece(clip: Clip, fromUs: Long, lengthUs: Long) {
            val clipStart = starts[project.clips.indexOf(clip)]
            val effects = buildList<Effect> {
                addAll(colorEffects(clip, project))
                addAll(smartEffects(clip, clipStart, outW, outH))
                add(framing(clip, outW, outH))
                if (clip.frame != Transform()) add(FrameTransformation(clip.frame, outW.toFloat() / outH))
                if (clip.keyframes.any { it.value.x != 0f || it.value.y != 0f || it.value.scale != 1f || it.value.rotation != 0f }) {
                    add(KeyframeTransformation(clip, outW.toFloat() / outH, clipStart))
                }
                if (clip.keyframes.any { it.value.opacity != 1f }) add(KeyframeFade(clip, clipStart))
                if (clip.fx.isNotEmpty() || clip.fadeInUs > 0 || clip.fadeOutUs > 0) {
                    add(FxEffect(clip.fx, beats, clipStart, clip.outputUs, clip.fadeInUs, clip.fadeOutUs, clip.fadeColor))
                }
            }
            val edited = if (clip.isPhoto) {
                EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(Uri.parse(clip.uri)).setMimeType(MimeTypes.IMAGE_JPEG)
                        .setImageDurationMs((lengthUs / 1000).coerceAtLeast(1)).build()
                ).setDurationUs(lengthUs).setFrameRate(30)
            } else {
                EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(Uri.parse(clip.uri)).setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionUs(fromUs).setEndPositionUs(fromUs + lengthUs).build()
                    ).build()
                ).setDurationUs(clip.sourceDurationUs)
            }
            b.addItem(edited.setRemoveAudio(true).setEffects(Effects(emptyList(), effects)).build())
            cursor += lengthUs
        }
        cfs.forEach { cf ->
            val a = project.clips[cf.index]
            val next = project.clips[cf.index + 1]
            val from = cf.cutUs - cf.beforeUs
            if (from - cursor > 1_000) { b.addGap(from - cursor); cursor = from }
            if (cf.beforeUs > 0) piece(next, next.trimStartUs - cf.beforeUs, cf.beforeUs)
            if (cf.afterUs > 0) piece(a, a.trimEndUs, cf.afterUs)
        }
        if (padToUs - cursor > 1_000) b.addGap(padToUs - cursor)
        return b.build()
    }

    private fun musicSequences(project: Project): List<EditedMediaItemSequence> {
        val total = project.durationUs
        return project.musicLanes(project.audibleMusic()).map { lane ->
            val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            var cursor = 0L
            lane.forEach { t ->
                if (t.startUs - cursor > 1_000) b.addGap(t.startUs - cursor)
                val len = t.playUs(total)
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(t.uri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionUs(t.offsetUs)
                            .setEndPositionUs(t.offsetUs + len)
                            .build()
                    )
                    .build()
                b.addItem(
                    EditedMediaItem.Builder(item)
                        .setDurationUs(t.sourceDurationUs)
                        .setRemoveVideo(true)
                        .setEffects(Effects(volumeProcessors(t.volume), emptyList()))
                        .build()
                )
                cursor = maxOf(cursor, t.startUs) + len
            }
            b.build()
        }
    }

    /**
     * @param forPreview anteprima: audio di Media3 (tono mantenuto) e risoluzione ridotta.
     * @param nativeAudio WAV della colonna sonora già pronta (solo export), o null.
     */
    fun build(
        project: Project,
        options: ExportOptions,
        forPreview: Boolean,
        nativeAudio: File?,
        /** false = niente compositore a più ingressi (ripiego): dissolvenza incrociata → passaggio al nero, video sovrapposti esclusi. */
        allowMulti: Boolean = true,
        /** Dimensioni imposte (pezzi preparati per l'anteprima: stesse misure dell'anteprima). */
        sizeOverride: Pair<Int, Int>? = null,
        /** Dissolvenze da usare al posto di quelle calcolate (pezzi preparati). */
        crossfadesOverride: List<Project.Crossfade>? = null,
    ): Composition {
        val (outW, outH) = sizeOverride ?: outputSize(project, if (forPreview) 1080 else options.size.shortSide)
        val keepHdr = !forPreview && options.hdr && project.clips.any { it.hdr > 0 }
        if (keepHdr) HdrGl.gain = HdrGl.gainFor(project.clips.count { it.hdr == 2 }, project.clips.count { it.hdr == 1 })
        val hdrMode = if (keepHdr) Composition.HDR_MODE_KEEP_HDR else Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
        val starts = project.clipStartsUs()
        val beats = project.beatTimes()
        val videoLayers = project.layers.filter { it.isVideo }
        val crossfades = crossfadesOverride ?: project.crossfades()
        val multi = allowMulti && (videoLayers.isNotEmpty() || crossfades.isNotEmpty())
        // Con video sovrapposti, testi e immagini vanno sopra a tutto: effetti dell'intera composizione.
        fun overlaysFor(fromUs: Long, toUs: Long): List<androidx.media3.effect.TextureOverlay> {
            val texts = project.texts.filter { t -> t.startUs < toUs && t.endUs > fromUs && t.text.isNotBlank() }
            // Senza compositore, i video sovrapposti si vedono come immagine ferma (posizione e movimento restano veri).
            val layers = project.layers.filter { l -> (!l.isVideo || !multi) && l.startUs < toUs && l.endUs > fromUs }
            return layers.map { l -> LayerOverlay(context, l, outW, outH) } + texts.map { t -> TimedTextOverlay(context, t, outW, outH) }
        }
        /** Clip della traccia principale; [light] = solo per i tempi (invisibile, senza effetti). */
        fun mainItems(light: Boolean, removeAudio: Boolean) = project.clips.mapIndexed { index, clip ->
            val startUs = starts[index]
            val mediaItem = if (clip.isPhoto) {
                MediaItem.Builder()
                    .setUri(Uri.parse(clip.uri))
                    .setMimeType(MimeTypes.IMAGE_JPEG)
                    .setImageDurationMs(clip.trimmedUs / 1000)
                    .build()
            } else MediaItem.Builder()
                .setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.trimStartUs)
                        .setEndPositionUs(clip.trimEndUs)
                        .build()
                )
                .build()
            val effects: List<Effect> = if (light) listOf(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)) else buildList<Effect> {
                addAll(colorEffects(clip, project))
                addAll(smartEffects(clip, startUs, outW, outH))
                add(framing(clip, outW, outH))
                if (clip.frame != Transform()) {
                    add(FrameTransformation(clip.frame, outW.toFloat() / outH))
                }
                if (clip.keyframes.isNotEmpty()) {
                    if (clip.keyframes.any { it.value.x != 0f || it.value.y != 0f || it.value.scale != 1f || it.value.rotation != 0f }) {
                        add(KeyframeTransformation(clip, outW.toFloat() / outH, startUs))
                    }
                    if (clip.keyframes.any { it.value.opacity != 1f }) add(KeyframeFade(clip, startUs))
                }
                if (clip.fx.isNotEmpty() || clip.fadeInUs > 0 || clip.fadeOutUs > 0) {
                    val needsBeats = clip.fx.any { f -> f.type.onBeats }
                    add(FxEffect(clip.fx, if (needsBeats) beats else emptyList(), startUs, clip.outputUs, clip.fadeInUs, clip.fadeOutUs, clip.fadeColor))
                }
                // Transizioni: metà alla fine di questa clip, metà all'inizio della successiva.
                // (la dissolvenza incrociata la fa il compositore, non questo effetto)
                fun adapt(t: TransitionSpec?) = when {
                    t?.type != TransitionType.CROSSFADE -> t
                    multi -> null
                    else -> t.copy(type = TransitionType.FADE_BLACK)
                }
                val inSpec = adapt(project.clips.getOrNull(index - 1)?.transitionOut)
                val inUs = if (index > 0) project.transitionHalfUs(index - 1) else 0
                val outSpec = adapt(clip.transitionOut)
                val outUs = project.transitionHalfUs(index)
                if ((inSpec != null && inUs > 0) || (outSpec != null && outUs > 0)) {
                    add(TransitionEffect(startUs, clip.outputUs, inSpec?.type, inUs, outSpec?.type, outUs))
                }
                // Testi e immagini: livelli sopra a tutto (dopo inquadratura, keyframe e transizioni).
                if (!multi) {
                    val ov = overlaysFor(startUs, startUs + clip.outputUs)
                    if (ov.isNotEmpty()) add(OverlayEffect(ov))
                }
            }
            if (clip.isPhoto) {
                return@mapIndexed EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(clip.trimmedUs)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), effects))
                    .build()
            }
            val b = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(clip.sourceDurationUs)
                .setEffects(Effects(volumeProcessors(clip.volume), effects))
                .setRemoveAudio(removeAudio || clip.muted)
            val curve = clip.speedCurve()
            if (curve.speeds.any { it != 1f }) {
                val pitch = forPreview || options.keepPitch
                b.setSpeed(SpeedParameters(CurveSpeedProvider(curve), pitch && nativeAudio == null))
            }
            if (!forPreview) options.fps.max?.let { b.setFrameRate(it) }
            b.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        if (multi) {
            // Il compositore disegna la prima sequenza sopra a tutte e ne usa i tempi: la prima è una copia
            // invisibile della traccia principale (tempi e audio), poi i video sovrapposti, in fondo la traccia visibile.
            val audioInMain = nativeAudio == null
            sequences += EditedMediaItemSequence.Builder(
                if (audioInMain) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
            ).addItems(mainItems(light = true, removeAudio = !audioInMain)).build()
            val lanes = packLanes(videoLayers, { it.startUs }, { it.endUs })
            // Le sequenze secondarie si allungano oltre la fine con una pausa: altrimenti il lettore
            // le ripeterebbe tagliando di nuovo clip già tagliate (caso non gestito dalla libreria).
            val padTo = project.durationUs + 1_000_000
            lanes.forEach { lane -> sequences += videoLayerSequence(lane, outW, outH, keepAudio = audioInMain, padToUs = padTo) }
            if (crossfades.isNotEmpty()) sequences += crossfadeSequence(project, crossfades, outW, outH, padTo)
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                .addItems(mainItems(light = false, removeAudio = true)).build()
            if (nativeAudio != null) {
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(nativeAudio)))
                    .setDurationUs(project.durationUs)
                    .build()
                sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(audioItem).build()
            } else {
                sequences += musicSequences(project)
            }
            val ov = overlaysFor(0, Long.MAX_VALUE)
            return Composition.Builder(sequences)
                .setHdrMode(hdrMode)
                .setVideoCompositorSettings(LayerCompositorSettings(outW, outH, lanes, crossfades))
                .setEffects(Effects(emptyList(), if (ov.isEmpty()) emptyList() else listOf(OverlayEffect(ov))))
                .build()
        }
        val items = mainItems(light = false, removeAudio = nativeAudio != null)
        if (nativeAudio != null) {
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItems(items).build()
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(nativeAudio)))
                .setDurationUs(project.durationUs)
                .build()
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO)).addItem(audioItem).build()
        } else {
            sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
                .addItems(items).build()
            // Musica (anteprima): una sequenza solo audio per riga, con pause fino a ogni brano.
            // Le dissolvenze si applicano nel video esportato.
            sequences += musicSequences(project)
        }
        // Di solito tutto in SDR; in HDR i nostri filtri lavorano in una scala con gamma (HdrGl).
        return Composition.Builder(sequences)
            .setHdrMode(hdrMode)
            .build()
    }
}
