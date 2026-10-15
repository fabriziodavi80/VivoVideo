package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
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
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.SpeedCurve
import java.io.File

enum class OutputSize(val label: String, val shortSide: Int?) {
    ORIGINAL("Originale", null),
    P2160("4K", 2160),
    P1080("1080p", 1080),
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
)

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
    fun colorEffects(clip: Clip, project: Project): List<Effect> = buildList {
        val c = project.color
        if (clip.log) add(builtInLog.toEffect())
        customLut(project)?.let { add(it.toEffect()) }
        if (c.denoise > 0) add(DenoiseEffect(c.denoise / 100f))
        if (c.highlights > 0 || clip.log) add(HighlightEffect(c.highlights / 100f))
        addAll(runCatching { Look.valueOf(c.look) }.getOrDefault(Look.NONE).effects())
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

    /** Dimensioni del video finale: orientamento e proporzioni della prima clip. */
    fun outputSize(project: Project, shortSide: Int?): Pair<Int, Int> {
        val first = project.clips.firstOrNull() ?: return 1920 to 1080
        val w = first.width.coerceAtLeast(2)
        val h = first.height.coerceAtLeast(2)
        val srcShort = minOf(w, h)
        val target = minOf(shortSide ?: srcShort, srcShort)
        fun even(v: Long) = (v / 2 * 2).toInt()
        val scale = target.toDouble() / srcShort
        return even((w * scale).toLong()) to even((h * scale).toLong())
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
    ): Composition {
        val (outW, outH) = outputSize(project, if (forPreview) 1080 else options.size.shortSide)
        val starts = project.clipStartsUs()
        val items = project.clips.mapIndexed { index, clip ->
            val startUs = starts[index]
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.trimStartUs)
                        .setEndPositionUs(clip.trimEndUs)
                        .build()
                )
                .build()
            val effects = buildList {
                addAll(colorEffects(clip, project))
                add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT))
                if (clip.keyframes.isNotEmpty()) {
                    if (clip.keyframes.any { it.value.x != 0f || it.value.y != 0f || it.value.scale != 1f || it.value.rotation != 0f }) {
                        add(KeyframeTransformation(clip, outW.toFloat() / outH, startUs))
                    }
                    if (clip.keyframes.any { it.value.opacity != 1f }) add(KeyframeFade(clip, startUs))
                }
                // Transizioni: metà alla fine di questa clip, metà all'inizio della successiva.
                val inSpec = project.clips.getOrNull(index - 1)?.transitionOut
                val inUs = if (index > 0) project.transitionHalfUs(index - 1) else 0
                val outSpec = clip.transitionOut
                val outUs = project.transitionHalfUs(index)
                if ((inSpec != null && inUs > 0) || (outSpec != null && outUs > 0)) {
                    add(TransitionEffect(startUs, clip.outputUs, inSpec?.type, inUs, outSpec?.type, outUs))
                }
            }
            val b = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(clip.sourceDurationUs)
                .setEffects(Effects(volumeProcessors(clip.volume), effects))
                .setRemoveAudio(nativeAudio != null || clip.muted)
            val curve = clip.speedCurve()
            if (curve.speeds.any { it != 1f }) {
                val pitch = forPreview || options.keepPitch
                b.setSpeed(SpeedParameters(CurveSpeedProvider(curve), pitch && nativeAudio == null))
            }
            if (!forPreview) options.fps.max?.let { b.setFrameRate(it) }
            b.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
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
        // Tutto in SDR: i filtri e le LUT sono pensati per colori normali.
        return Composition.Builder(sequences)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }
}
