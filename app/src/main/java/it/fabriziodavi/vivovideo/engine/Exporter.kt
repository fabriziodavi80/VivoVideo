package it.fabriziodavi.vivovideo.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import it.fabriziodavi.vivovideo.Safety
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.renderTimelineAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int?, val label: String) : ExportState
    data class Done(val uri: Uri, val details: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Esporta un progetto: audio nativo, poi video con una scala di tentativi per l'hardware. */
class Exporter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val factory: CompositionFactory,
    private val onState: (ExportState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var cancelled = false
    private var startedAt = 0L

    fun start(project: Project, options: ExportOptions) {
        cancelled = false
        startedAt = SystemClock.elapsedRealtime()
        scope.launch {
            // Colonna sonora calcolata in proprio se c'è musica (mix con dissolvenze)
            // o se qualche clip cambia velocità (tono naturale).
            val needsNative = project.audibleMusic().isNotEmpty() || project.layers.any { it.isVideo && !it.muted } ||
                project.clips.any { it.fadeAudio && !it.muted && (it.fadeInUs > 0 || it.fadeOutUs > 0) } ||
                (options.keepPitch && project.clips.any { c -> !c.muted && c.speedCurve().speeds.any { it != 1f } }) ||
                (options.loudness && project.clips.any { !it.muted && !it.isPhoto })
            var audio: File? = null
            if (needsNative) {
                onState(ExportState.Running(null, "Elaborazione audio…"))
                val r = withContext(Dispatchers.Default) {
                    runCatching {
                        renderTimelineAudio(context, project, options.loudness) { p ->
                            handler.post { if (!cancelled) onState(ExportState.Running((p * 100).toInt(), "Elaborazione audio…")) }
                        }
                    }
                }
                if (r.isFailure) {
                    onState(ExportState.Failed("Errore nell'elaborazione dell'audio: ${r.exceptionOrNull()}"))
                    return@launch
                }
                audio = r.getOrNull()
            }
            if (cancelled) { audio?.delete(); return@launch }
            // Lascia all'anteprima il tempo di liberare il decoder hardware.
            delay(400)
            attempt(project, options, audio, 0, mutableListOf(), allowMulti = Safety.multiExport(context))
        }
    }

    fun cancel() {
        Safety.pendingExport(context, false)
        cancelled = true
        transformer?.cancel()
        transformer = null
        handler.removeCallbacksAndMessages(null)
        onState(ExportState.Idle)
    }

    private fun attempt(
        project: Project, options: ExportOptions, audio: File?, attempt: Int, log: MutableList<String>,
        allowMulti: Boolean = true,
    ) {
        if (cancelled) return
        val composition = factory.build(project, options, forPreview = false, nativeAudio = audio, allowMulti = allowMulti)
        val usesMulti = allowMulti && (project.layers.any { it.isVideo } || project.crossfades().isNotEmpty())
        Safety.pendingExport(context, usesMulti)
        val (outW, outH) = factory.outputSize(project, options.size.shortSide)
        val maxClipFps = project.clips.maxOfOrNull { it.fps } ?: 30f
        val outFps = options.fps.max?.let { minOf(it.toFloat(), maxClipFps) } ?: maxClipFps
        val maxBitrate = if (attempt >= 1) 100_000_000.0 else 160_000_000.0
        var bitrate = (45_000_000.0 * outW * outH * outFps / (3840.0 * 2160 * 30))
            .coerceIn(8_000_000.0, maxBitrate).toInt()
        if (options.codec == OutputCodec.H264) bitrate = minOf((bitrate * 1.4).toInt(), maxBitrate.toInt())

        val heavy = project.clips.any { it.isHeavy }
        val step = if (heavy) attempt + 1 else attempt
        val settings = VideoEncoderSettings.Builder().setBitrate(bitrate)
        var softwareDecoder = false
        val mode = when (step) {
            0 -> "massima velocità"
            1 -> { settings.setEncoderPerformanceParameters(VideoEncoderSettings.RATE_UNSET, VideoEncoderSettings.RATE_UNSET); "velocità standard" }
            2 -> { settings.setEncoderPerformanceParameters(outFps.toInt().coerceAtLeast(1), 1); "tempo reale" }
            else -> {
                settings.setEncoderPerformanceParameters(outFps.toInt().coerceAtLeast(1), 1)
                softwareDecoder = true
                "decoder software (lento)"
            }
        }
        val decoders = DefaultDecoderFactory.Builder(context)
        if (softwareDecoder) {
            decoders.setMediaCodecSelector { mime, secure, tunneling ->
                val all = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                all.filter { it.softwareOnly }.ifEmpty { all }
            }
        }
        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Safety.pendingExport(context, false)
                handler.removeCallbacksAndMessages(null)
                audio?.delete()
                transformer = null
                val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching { saveToGallery(outFile) } }
                    saved.onSuccess { uri ->
                        onState(ExportState.Done(uri, buildString {
                            append("Durata: ${"%.1f".format(exportResult.durationMs / 1000.0)} s")
                            append(" · ${"%.1f".format(exportResult.fileSizeBytes / 1_048_576.0)} MB")
                            append("\nClip: ${project.clips.size} · ${outW}×${outH}")
                            append("\nTempo di esportazione: ${"%.1f".format(seconds)} s")
                            append("\nCodec: ${options.codec.label}, ${bitrate / 1_000_000} Mbps, max ${"%.0f".format(outFps)} fps")
                            exportResult.videoEncoderName?.let { append("\nEncoder: $it ($mode)") }
                            if (audio != null) append("\nAudio: tono naturale (Signalsmith)")
                            if (log.isNotEmpty()) append("\nTentativi falliti prima: ${log.size}")
                            if (!allowMulti && (project.layers.any { it.isVideo } || project.crossfades().isNotEmpty())) {
                                append("\nAttenzione: esportato senza video sovrapposti e con le dissolvenze incrociate come passaggio al nero (il compositore ha dato errore).")
                            }
                        }))
                    }.onFailure { onState(ExportState.Failed("Video creato ma non salvato in galleria: ${it.message}")) }
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Safety.pendingExport(context, false)
                handler.removeCallbacksAndMessages(null)
                outFile.delete()
                transformer = null
                val cause = exportException.cause
                val codecInfo = (cause as? MediaCodec.CodecException)?.let { " [codice ${it.errorCode}]" } ?: ""
                log += "• $mode, ${bitrate / 1_000_000} Mbps: ${exportException.errorCodeName}$codecInfo"
                val retryable = exportException.errorCode == ExportException.ERROR_CODE_ENCODER_INIT_FAILED ||
                    exportException.errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED ||
                    exportException.errorCode == ExportException.ERROR_CODE_MUXING_TIMEOUT
                if (retryable && step < 3 && !cancelled) {
                    onState(ExportState.Running(null, "Nuovo tentativo con impostazioni più prudenti…"))
                    handler.post { attempt(project, options, audio, attempt + 1, log, allowMulti) }
                    return
                }
                if (usesMulti && !cancelled) {
                    // Ultimo ripiego: senza compositore a più ingressi.
                    val chain = generateSequence(exportException.cause) { it.cause }.take(3)
                        .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message?.take(100) ?: ""}" }
                    log += "• compositore: $chain"
                    onState(ExportState.Running(null, "Nuovo tentativo senza sovrapposizioni…"))
                    handler.post { attempt(project, options, audio, 0, log, allowMulti = false) }
                    return
                }
                audio?.delete()
                val causeText = generateSequence(cause) { it.cause }.take(3)
                    .joinToString("") { "\nCausa: ${it.javaClass.simpleName}: ${it.message?.take(160)}" }
                onState(ExportState.Failed(
                    "Errore ${exportException.errorCode} (${exportException.errorCodeName})$causeText\n\nTentativi:\n" +
                        log.joinToString("\n")
                ))
            }
        }

        val t = Transformer.Builder(context)
            // Se per un minuto non esce nulla la libreria si è inceppata (capita di rado): si riprova da capo.
            .setMaxDelayBetweenMuxerSamplesMs(60_000)
            .setVideoMimeType(options.codec.mime)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(settings.build()).build()
            )
            .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoders.build(), Clock.DEFAULT, null))
            .addListener(listener)
            .build()
        transformer = t
        try {
            t.start(composition, outFile.absolutePath)
        } catch (e: Exception) {
            transformer = null
            audio?.delete()
            onState(ExportState.Failed("Impossibile avviare l'esportazione: ${e.message}"))
            return
        }
        pollProgress(if (attempt > 0) "Esportazione (nuovo tentativo)…" else "Esportazione…")
    }

    private fun pollProgress(label: String) {
        val holder = ProgressHolder()
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                val st = t.getProgress(holder)
                onState(ExportState.Running(if (st == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null, label))
                handler.postDelayed(this, 300)
            }
        })
    }

    /** Copia il file esportato in Film/VivoVideo, visibile in galleria. */
    private fun saveToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val name = "VivoVideo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VivoVideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("MediaStore non disponibile")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 20) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            file.delete()
        }
        return uri
    }
}
