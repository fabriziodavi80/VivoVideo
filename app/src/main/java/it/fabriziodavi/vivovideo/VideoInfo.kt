package it.fabriziodavi.vivovideo

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Dati essenziali del video sorgente, letti senza decodificarlo. */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationMs: Long,
    val fps: Float?,
    val codec: String?,
    val hdr: String?,
    val bitrate: Int?,
) {
    /** Dimensioni come appaiono a schermo (tenendo conto della rotazione). */
    val displayWidth get() = if (rotation % 180 == 0) width else height
    val displayHeight get() = if (rotation % 180 == 0) height else width
    val isHdr get() = hdr != null

    fun summary(): String = buildString {
        append("${displayWidth}×${displayHeight}")
        fps?.let { append(" · ${"%.0f".format(it)} fps") }
        codec?.let { append(" · ${codecLabel(it)}") }
        hdr?.let { append(" · $it") }
        append(" · ${formatDuration(durationMs)}")
        bitrate?.let { append(" · ${it / 1_000_000} Mbps") }
    }
}

fun codecLabel(mime: String): String = when (mime) {
    MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265"
    MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264"
    MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "Dolby Vision"
    MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
    MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
    else -> mime.removePrefix("video/")
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
    var width = 0
    var height = 0
    var rotation = 0
    var durationMs = 0L
    var bitrate: Int? = null
    var fps: Float? = null

    val mmr = MediaMetadataRetriever()
    try {
        mmr.setDataSource(context, uri)
        width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        val frames = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
        if (frames != null && durationMs > 0) fps = frames * 1000f / durationMs
    } finally {
        mmr.release()
    }

    var codec: String? = null
    var hdr: String? = null
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            codec = mime
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                fps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull() }
                    ?: fps
            }
            if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                hdr = when (format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)) {
                    MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                    MediaFormat.COLOR_TRANSFER_HLG -> "HDR HLG"
                    else -> null
                }
            }
            if (mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) hdr = "Dolby Vision"
            break
        }
    } catch (_: Exception) {
        // Le informazioni sul codec sono solo diagnostiche.
    } finally {
        extractor.release()
    }

    return VideoInfo(width, height, rotation, durationMs, fps, codec, hdr, bitrate)
}

/** Riassunto delle capacità degli encoder video del telefono. */
fun describeEncoders(): String {
    val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
    val lines = mutableListOf<String>()
    for (mime in listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)) {
        val encoders = list.codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
        val hw = encoders.firstOrNull { it.isHardwareAccelerated } ?: encoders.firstOrNull()
        if (hw == null) {
            lines += "${codecLabel(mime)}: nessun encoder"
            continue
        }
        val caps: MediaCodecInfo.VideoCapabilities? =
            runCatching { hw.getCapabilitiesForType(mime).videoCapabilities }.getOrNull()
        val k60 = caps?.let { c ->
            runCatching { c.areSizeAndRateSupported(3840, 2160, 60.0) }.getOrDefault(false)
        } ?: false
        val k30 = caps?.let { c ->
            runCatching { c.areSizeAndRateSupported(3840, 2160, 30.0) }.getOrDefault(false)
        } ?: false
        val support = when {
            k60 -> "4K 60fps"
            k30 -> "4K 30fps"
            else -> "sotto il 4K"
        }
        val kind = if (hw.isHardwareAccelerated) "hardware" else "software"
        lines += "${codecLabel(mime)}: $support ($kind, ${hw.name})"
    }
    return lines.joinToString("\n")
}
