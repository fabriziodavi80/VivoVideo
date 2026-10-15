package it.fabriziodavi.vivovideo

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.SpeedParameters
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OutputSize(val label: String, val shortSide: Int?) {
    ORIGINAL("Originale", null),
    P2160("4K", 2160),
    P1080("1080p", 1080),
}

/** Fps massimi in uscita: da un video a 100/120fps si ottiene un rallentatore fluido. */
enum class OutputFps(val label: String, val max: Int?) {
    ORIGINAL("Originali", null),
    F60("60", 60),
    F30("30", 30),
}

enum class OutputCodec(val label: String, val mime: String) {
    H265("H.265", MimeTypes.VIDEO_H265),
    H264("H.264", MimeTypes.VIDEO_H264),
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val percent: Int?, val label: String? = null) : ExportState
    data class Done(val uri: Uri, val details: String) : ExportState
    data class Failed(val message: String) : ExportState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var sourceUri by mutableStateOf<Uri?>(null)
        private set
    var info by mutableStateOf<VideoInfo?>(null)
        private set
    var infoError by mutableStateOf<String?>(null)
        private set

    var look by mutableStateOf(Look.NONE)
    /** Conversione LOG → colori normali con la LUT tarata sul vivo X300 Ultra. */
    var logMode by mutableStateOf(false)
    var customLut by mutableStateOf<CubeLut?>(null)
        private set
    /** Recupero alte luci / cielo, da 0 a 100. */
    var highlights by mutableStateOf(0)
    var lutError by mutableStateOf<String?>(null)
        private set
    private val builtInLog: CubeLut by lazy {
        CubeLut.fromAsset(getApplication(), CubeLut.BUILT_IN_LOG, "vivo LOG → normale")
    }

    /** Catena colore: conversione LOG, poi LUT personalizzata, poi filtro. */
    fun colorEffects(): List<Effect> = buildList {
        if (logMode) add(builtInLog.toEffect())
        customLut?.let { add(it.toEffect()) }
        // Shader alte luci; con il LOG serve comunque per il dithering anti-bande.
        if (highlights > 0 || logMode) add(HighlightEffect(highlights / 100f))
        addAll(look.effects())
    }

    fun loadLut(uri: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { CubeLut.fromUri(getApplication(), uri) } }
            r.onSuccess { customLut = it; lutError = null }
                .onFailure { lutError = "LUT non valida: ${it.message}" }
        }
    }

    fun clearLut() { customLut = null; lutError = null }
    var speed by mutableStateOf(SpeedMode.NORMAL)
    var size by mutableStateOf(OutputSize.ORIGINAL)
    var fps by mutableStateOf(OutputFps.ORIGINAL)
    var codec by mutableStateOf(OutputCodec.H265)
    var keepAudio by mutableStateOf(true)
    var keepPitch by mutableStateOf(true)
    var toneMapHdr by mutableStateOf(false)

    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    val encoderSummary: String by lazy {
        runCatching { describeEncoders() }.getOrElse { "Impossibile leggere gli encoder: ${it.message}" }
    }

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    fun onVideoPicked(uri: Uri) {
        sourceUri = uri
        info = null
        infoError = null
        exportState = ExportState.Idle
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readVideoInfo(getApplication(), uri) }
            }
            result.onSuccess {
                info = it
                toneMapHdr = false
                // I video LOG del vivo sono a 10 bit ma etichettati come SDR normali.
                logMode = it.tenBit && !it.isHdr
            }.onFailure { infoError = it.message ?: it.toString() }
        }
    }

    fun startExport() {
        val uri = sourceUri ?: return
        val src = info ?: return
        if (exportState is ExportState.Running) return
        val context = getApplication<Application>()
        exportState = ExportState.Running(null, "Preparazione…")
        startedAt = SystemClock.elapsedRealtime()

        val provider = speed.provider(src.durationMs * 1000)
        // Audio di qualità: lo allunghiamo noi con Signalsmith Stretch.
        val nativeAudio = keepAudio && keepPitch && provider != null
        viewModelScope.launch {
            var audioFile: File? = null
            if (nativeAudio) {
                exportState = ExportState.Running(null, "Elaborazione audio…")
                val r = withContext(Dispatchers.Default) {
                    runCatching { prepareStretchedAudio(context, uri, provider!!, src.durationMs * 1000) }
                }
                if (r.isFailure) {
                    exportState = ExportState.Failed("Errore nell'elaborazione dell'audio: ${r.exceptionOrNull()}")
                    return@launch
                }
                audioFile = r.getOrNull()
            }
            // Lascia all'anteprima il tempo di liberare il decoder hardware.
            kotlinx.coroutines.delay(400)
            if (exportState !is ExportState.Running) { audioFile?.delete(); return@launch }
            runAttempt(uri, src, provider, nativeAudio, audioFile, attempt = 0, log = mutableListOf())
        }
    }

    /** Tentativi di encoder dal più veloce al più prudente (servono per l'8K). */
    private val maxAttempts = 4

    private fun runAttempt(
        uri: Uri,
        src: VideoInfo,
        provider: androidx.media3.common.audio.SpeedProvider?,
        nativeAudio: Boolean,
        audioFile: File?,
        attempt: Int,
        log: MutableList<String>,
    ) {
        val context = getApplication<Application>()

        // --- Effetti video ---
        val videoEffects = mutableListOf<Effect>()
        videoEffects += colorEffects()
        size.shortSide?.let { videoEffects += Presentation.createForShortSide(it) }

        val itemBuilder = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), videoEffects))
            .setRemoveAudio(!keepAudio || nativeAudio)
        provider?.let {
            // Effetto nastro: Media3 cambia la velocità anche dell'audio (tono incluso).
            itemBuilder.setSpeed(SpeedParameters(it, false))
        }
        fps.max?.let { itemBuilder.setFrameRate(it) }
        val edited = itemBuilder.build()

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences += if (keepAudio && !nativeAudio) {
            EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        } else {
            EditedMediaItemSequence.withVideoFrom(listOf(edited))
        }
        if (audioFile != null) {
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audioFile))).build()
            sequences += EditedMediaItemSequence.withAudioFrom(listOf(audioItem))
        }

        // L'H.264 non trasporta bene l'HDR: in quel caso si converte sempre in SDR.
        val toSdr = src.isHdr && (toneMapHdr || codec == OutputCodec.H264)
        val composition = Composition.Builder(sequences)
            .setHdrMode(
                if (toSdr) Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                else Composition.HDR_MODE_KEEP_HDR
            )
            .build()

        // --- Bitrate proporzionale a pixel × fotogrammi (4K30 ≈ 45 Mbps in H.265) ---
        val srcShort = minOf(src.displayWidth, src.displayHeight).coerceAtLeast(1)
        val srcLong = maxOf(src.displayWidth, src.displayHeight).coerceAtLeast(1)
        val outShort = minOf(size.shortSide ?: srcShort, srcShort)
        val outLong = srcLong.toLong() * outShort / srcShort
        val srcFps = src.fps ?: 30f
        val outFps = fps.max?.let { minOf(it.toFloat(), srcFps) } ?: srcFps
        val pixelsPerSec = outShort.toDouble() * outLong * outFps
        val maxBitrate = if (attempt >= 1) 100_000_000.0 else 160_000_000.0
        var bitrate = (45_000_000 * pixelsPerSec / (3840.0 * 2160 * 30))
            .coerceIn(8_000_000.0, maxBitrate).toInt()
        if (codec == OutputCodec.H264) bitrate = minOf((bitrate * 1.4).toInt(), maxBitrate.toInt())

        // Con sorgenti oltre il 4K, decoder ed encoder insieme superano il budget
        // hardware se si chiede la massima velocità: si parte già in modalità prudente.
        val heavy = src.width.toLong() * src.height * srcFps >= 3840L * 2160 * 120
        // Scala dei tentativi: 0 massima velocità, 1 standard, 2 tempo reale (+ decoder senza
        // operating rate), 3 decoder software (lento ma senza limiti hardware, per l'8K).
        val step = if (heavy) attempt + 1 else attempt
        val settings = VideoEncoderSettings.Builder().setBitrate(bitrate)
        var softwareDecoder = false
        val modeLabel = when (step) {
            0 -> "massima velocità"
            1 -> { settings.setEncoderPerformanceParameters(VideoEncoderSettings.RATE_UNSET, VideoEncoderSettings.RATE_UNSET); "velocità standard" }
            2 -> { settings.setEncoderPerformanceParameters(outFps.toInt().coerceAtLeast(1), 1); "tempo reale" }
            else -> {
                settings.setEncoderPerformanceParameters(outFps.toInt().coerceAtLeast(1), 1)
                softwareDecoder = true
                "decoder software (lento)"
            }
        }
        val decoderBuilder = androidx.media3.transformer.DefaultDecoderFactory.Builder(context)
        if (softwareDecoder) {
            decoderBuilder.setMediaCodecSelector { mime, secure, tunneling ->
                val all = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                all.filter { it.softwareOnly }.ifEmpty { all }
            }
        }
        val assetLoaderFactory = androidx.media3.transformer.DefaultAssetLoaderFactory(
            context,
            decoderBuilder.build(),
            androidx.media3.common.util.Clock.DEFAULT,
            null,
        )

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(settings.build())
            .build()

        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                handler.removeCallbacksAndMessages(null)
                audioFile?.delete()
                val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                viewModelScope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { saveToGallery(outFile) }
                    }
                    saved.onSuccess { galleryUri ->
                        val details = buildString {
                            append("Durata: ${"%.1f".format(exportResult.durationMs / 1000.0)} s")
                            append(" · ${"%.1f".format(exportResult.fileSizeBytes / 1_048_576.0)} MB")
                            append("\nTempo di esportazione: ${"%.1f".format(seconds)} s")
                            append("\nCodec: ${codec.label}, ${bitrate / 1_000_000} Mbps, max ${"%.0f".format(outFps)} fps")
                            if (src.isHdr) append(if (toSdr) " · convertito in SDR" else " · HDR mantenuto")
                            exportResult.videoEncoderName?.let { append("\nEncoder: $it ($modeLabel)") }
                            if (logMode) append("\nColore: LOG convertito")
                            customLut?.let { append("\nLUT: ${it.name}") }
                            if (highlights > 0) append("\nAlte luci / cielo: $highlights")
                            if (nativeAudio) append("\nAudio: tono naturale (Signalsmith)")
                            if (log.isNotEmpty()) append("\nTentativi falliti prima: ${log.size}")
                        }
                        exportState = ExportState.Done(galleryUri, details)
                    }.onFailure {
                        exportState = ExportState.Failed("Video creato ma non salvato in galleria: ${it.message}")
                    }
                    transformer = null
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                handler.removeCallbacksAndMessages(null)
                outFile.delete()
                transformer = null
                val cause = exportException.cause
                val codecInfo = (cause as? android.media.MediaCodec.CodecException)?.let {
                    " [codice ${it.errorCode}, ${it.diagnosticInfo}]"
                } ?: ""
                log += "• $modeLabel, ${bitrate / 1_000_000} Mbps: ${exportException.errorCodeName}$codecInfo"
                val retryable = exportException.errorCode == ExportException.ERROR_CODE_ENCODER_INIT_FAILED ||
                    exportException.errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED
                if (retryable && attempt + 1 < maxAttempts && step < 3 && exportState is ExportState.Running) {
                    exportState = ExportState.Running(null, "Nuovo tentativo con impostazioni più prudenti…")
                    handler.post { runAttempt(uri, src, provider, nativeAudio, audioFile, attempt + 1, log) }
                    return
                }
                audioFile?.delete()
                val causeText = cause?.let { "\nCausa: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                exportState = ExportState.Failed(
                    "Errore ${exportException.errorCode} (${exportException.errorCodeName})$causeText" +
                        "\n\nTentativi:\n" + log.joinToString("\n")
                )
            }
        }

        val t = Transformer.Builder(context)
            .setVideoMimeType(codec.mime)
            .setEncoderFactory(encoderFactory)
            .setAssetLoaderFactory(assetLoaderFactory)
            .addListener(listener)
            .build()
        transformer = t
        if (exportState !is ExportState.Running) exportState = ExportState.Running(null)
        try {
            t.start(composition, outFile.absolutePath)
        } catch (e: Exception) {
            transformer = null
            audioFile?.delete()
            exportState = ExportState.Failed("Impossibile avviare l'esportazione: ${e.message}")
            return
        }
        pollProgress()
    }

    private fun pollProgress() {
        val holder = ProgressHolder()
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                val state = t.getProgress(holder)
                if (exportState is ExportState.Running) {
                    exportState = ExportState.Running(
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null,
                        (exportState as ExportState.Running).label?.takeIf { it.startsWith("Nuovo") },
                    )
                }
                handler.postDelayed(this, 300)
            }
        })
    }

    fun cancelExport() {
        transformer?.cancel()
        transformer = null
        handler.removeCallbacksAndMessages(null)
        exportState = ExportState.Idle
    }

    /** Copia il file esportato in Film/VivoVideo, visibile in galleria. */
    private fun saveToGallery(file: File): Uri {
        val resolver = getApplication<Application>().contentResolver
        val name = "VivoVideo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VivoVideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("MediaStore non disponibile")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { it.copyTo(out, bufferSize = 1 shl 20) }
            }
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

    override fun onCleared() {
        transformer?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
