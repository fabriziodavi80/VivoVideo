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
    data class Running(val percent: Int?) : ExportState
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
            }.onFailure { infoError = it.message ?: it.toString() }
        }
    }

    fun startExport() {
        val uri = sourceUri ?: return
        val src = info ?: return
        if (exportState is ExportState.Running) return
        val context = getApplication<Application>()

        // --- Effetti video ---
        val videoEffects = mutableListOf<Effect>()
        videoEffects += look.effects()
        size.shortSide?.let { videoEffects += Presentation.createForShortSide(it) }

        val itemBuilder = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), videoEffects))
            .setRemoveAudio(!keepAudio)
        speed.provider(src.durationMs * 1000)?.let {
            // Con keepPitch l'audio si allunga/accorcia senza cambiare tono.
            itemBuilder.setSpeed(SpeedParameters(it, keepPitch && keepAudio))
        }
        fps.max?.let { itemBuilder.setFrameRate(it) }
        val edited = itemBuilder.build()

        val sequence = if (keepAudio) {
            EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        } else {
            EditedMediaItemSequence.withVideoFrom(listOf(edited))
        }

        // L'H.264 non trasporta bene l'HDR: in quel caso si converte sempre in SDR.
        val toSdr = src.isHdr && (toneMapHdr || codec == OutputCodec.H264)
        val composition = Composition.Builder(sequence)
            .setHdrMode(
                if (toSdr) Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                else Composition.HDR_MODE_KEEP_HDR
            )
            .build()

        // --- Encoder hardware con bitrate adatto alla risoluzione ---
        // Bitrate proporzionale a pixel × fotogrammi (riferimento: 4K30 ≈ 45 Mbps in H.265).
        val srcShort = minOf(src.displayWidth, src.displayHeight).coerceAtLeast(1)
        val srcLong = maxOf(src.displayWidth, src.displayHeight).coerceAtLeast(1)
        val outShort = minOf(size.shortSide ?: srcShort, srcShort)
        val outLong = srcLong.toLong() * outShort / srcShort
        val srcFps = src.fps ?: 30f
        val outFps = fps.max?.let { minOf(it.toFloat(), srcFps) } ?: srcFps
        val pixelsPerSec = outShort.toDouble() * outLong * outFps
        var bitrate = (45_000_000 * pixelsPerSec / (3840.0 * 2160 * 30))
            .coerceIn(8_000_000.0, 160_000_000.0).toInt()
        if (codec == OutputCodec.H264) bitrate = (bitrate * 1.4).toInt()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(bitrate).build()
            )
            .build()

        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                handler.removeCallbacksAndMessages(null)
                val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
                viewModelScope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { saveToGallery(outFile) }
                    }
                    saved.onSuccess { galleryUri ->
                        val details = buildString {
                            append("Durata: ${formatDuration(exportResult.durationMs)}")
                            append(" · ${"%.1f".format(exportResult.fileSizeBytes / 1_048_576.0)} MB")
                            append("\nTempo di esportazione: ${"%.1f".format(seconds)} s")
                            append("\nCodec: ${codec.label}, ${bitrate / 1_000_000} Mbps, max ${"%.0f".format(outFps)} fps")
                            if (src.isHdr) append(if (toSdr) " · convertito in SDR" else " · HDR mantenuto")
                            exportResult.videoEncoderName?.let { append("\nEncoder: $it") }
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
                val cause = exportException.cause?.let { "\nCausa: ${it.javaClass.simpleName}: ${it.message}" } ?: ""
                exportState = ExportState.Failed(
                    "Errore ${exportException.errorCode} (${exportException.errorCodeName}): " +
                        "${exportException.message}$cause"
                )
            }
        }

        val t = Transformer.Builder(context)
            .setVideoMimeType(codec.mime)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()
        transformer = t
        startedAt = SystemClock.elapsedRealtime()
        exportState = ExportState.Running(null)
        try {
            t.start(composition, outFile.absolutePath)
        } catch (e: Exception) {
            transformer = null
            exportState = ExportState.Failed("Impossibile avviare l'esportazione: ${e.message}")
            return
        }
        pollProgress()
    }

    private fun pollProgress() {
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                val state = t.getProgress(holder)
                if (exportState is ExportState.Running) {
                    exportState = ExportState.Running(
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
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
