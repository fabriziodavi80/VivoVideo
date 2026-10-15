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
    val color: String? = null,
    val tenBit: Boolean = false,
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
        append(" · ${"%.1f".format(durationMs / 1000.0)} s")
        bitrate?.let { append(" · ${it / 1_000_000} Mbps") }
        color?.let { append("\nColore: $it") }
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
    var color: String? = null
    var tenBit = false
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
            color = describeColor(format, mime)
            tenBit = color?.contains("10 bit") == true
            break
        }
    } catch (_: Exception) {
        // Le informazioni sul codec sono solo diagnostiche.
    } finally {
        extractor.release()
    }

    return VideoInfo(width, height, rotation, durationMs, fps, codec, hdr, bitrate, color, tenBit)
}

/** Spazio colore, curva e profondità: serve a riconoscere i video LOG. */
private fun describeColor(format: MediaFormat, mime: String): String {
    fun int(key: String) = if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrNull() else null
    val standard = when (int(MediaFormat.KEY_COLOR_STANDARD)) {
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601"
        null -> "standard ?"
        else -> "standard ${int(MediaFormat.KEY_COLOR_STANDARD)}"
    }
    val transfer = when (int(MediaFormat.KEY_COLOR_TRANSFER)) {
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
        MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ"
        MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        MediaFormat.COLOR_TRANSFER_LINEAR -> "lineare"
        null -> "curva ?"
        else -> "curva ${int(MediaFormat.KEY_COLOR_TRANSFER)}"
    }
    val range = when (int(MediaFormat.KEY_COLOR_RANGE)) {
        MediaFormat.COLOR_RANGE_FULL -> "full"
        MediaFormat.COLOR_RANGE_LIMITED -> "limited"
        else -> null
    }
    val profile = int(MediaFormat.KEY_PROFILE)
    val tenBit = mime == MediaFormat.MIMETYPE_VIDEO_HEVC && profile != null &&
        profile != MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
    return listOfNotNull(standard, transfer, range, if (tenBit) "10 bit" else if (profile != null) "8 bit" else null)
        .joinToString(" · ")
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
        fun ok(w: Int, h: Int, fps: Double): String {
            val c = caps ?: return "?"
            val declared = runCatching { c.areSizeAndRateSupported(w, h, fps) }.getOrDefault(false)
            // I "performance point" dicono cosa l'encoder regge davvero in tempo reale.
            val points = runCatching { c.supportedPerformancePoints }.getOrNull()
            val real = points?.let { list ->
                val wanted = MediaCodecInfo.VideoCapabilities.PerformancePoint(w, h, fps.toInt())
                list.any { it.covers(wanted) }
            }
            return when {
                real == true -> "sì"
                declared && real == null -> "sì"
                declared -> "dichiarato"
                else -> "no"
            }
        }
        val kind = if (hw.isHardwareAccelerated) "hardware" else "software"
        lines += "${codecLabel(mime)} ($kind, ${hw.name})\n" +
            "   4K60: ${ok(3840, 2160, 60.0)} · 4K120: ${ok(3840, 2160, 120.0)} · 8K30: ${ok(7680, 4320, 30.0)}"
    }
    return lines.joinToString("\n")
}

/** Nome leggibile (senza estensione) e durata in µs di un file audio. */
fun readAudioInfo(context: Context, uri: Uri): Pair<String, Long> {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Musica"
    val r = MediaMetadataRetriever()
    var durUs = try {
        r.setDataSource(context, uri)
        (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
    } catch (e: Exception) { 0L } finally { r.release() }
    if (durUs <= 0) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true && f.containsKey(MediaFormat.KEY_DURATION)) {
                    durUs = f.getLong(MediaFormat.KEY_DURATION); break
                }
            }
        } finally { ex.release() }
    }
    return name to durUs
}
