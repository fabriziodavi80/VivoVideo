package it.fabriziodavi.vivovideo.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Quando e dove è stata fatta una foto o un video: la data dai dati della foto (o dalla galleria) e il nome del posto
 * dalle coordinate GPS. Serve alle scritte automatiche dei collage (mese, giorni del viaggio, nome del luogo).
 * Il luogo si legge solo con il permesso "posizione dei contenuti multimediali"; senza, resta vuoto.
 */
object PhotoPlace {
    private val names = HashMap<String, String>()

    fun locationAllowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Le foto scelte con il selettore di Android hanno un indirizzo suo: dove si può, si risale a quello della galleria. */
    private fun galleryUri(uri: Uri, photo: Boolean): Uri? {
        if (uri.authority == MediaStore.AUTHORITY && uri.pathSegments.firstOrNull() != "picker") return uri
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        if (uri.toString().contains("picker").not()) return null
        return ContentUris.withAppendedId(if (photo) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    }

    /** (millisecondi dello scatto, 0 se non si sa) e (nome del posto, vuoto se non si sa). */
    fun read(ctx: Context, uri: Uri, photo: Boolean): Pair<Long, String> {
        var taken = 0L
        var lat: Double? = null; var lon: Double? = null
        val gallery = galleryUri(uri, photo)
        val candidates = buildList {
            if (gallery != null && locationAllowed(ctx)) runCatching { add(MediaStore.setRequireOriginal(gallery)) }
            add(uri)
        }
        for (u in candidates) {
            if (photo) runCatching {
                ctx.contentResolver.openInputStream(u)!!.use { s ->
                    val ex = ExifInterface(s)
                    if (taken == 0L) taken = exifDate(ex)
                    val ll = FloatArray(2)
                    if (lat == null && ex.getLatLong(ll) && (ll[0] != 0f || ll[1] != 0f)) { lat = ll[0].toDouble(); lon = ll[1].toDouble() }
                }
            } else runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, u)
                    if (taken == 0L) taken = videoDate(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
                    if (lat == null) parseIso6709(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))?.let { lat = it.first; lon = it.second }
                } finally { r.release() }
            }
            if (taken > 0 && lat != null) break
        }
        if (taken == 0L) taken = runCatching {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        }.getOrDefault(0L)
        val place = lat?.let { la -> lon?.let { lo -> placeName(ctx, la, lo) } } ?: ""
        return taken to place
    }

    private fun exifDate(ex: ExifInterface): Long {
        val s = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: ex.getAttribute(ExifInterface.TAG_DATETIME) ?: return 0L
        return runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).parse(s)?.time ?: 0L }.getOrDefault(0L)
    }

    private fun videoDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return runCatching {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s.take(15))?.time ?: 0L
        }.getOrDefault(0L).takeIf { it > 946_684_800_000L } ?: 0L
    }

    /** "+45.4642+009.1900/" → (45.4642, 9.19). */
    fun parseIso6709(s: String?): Pair<Double, Double>? {
        if (s.isNullOrBlank()) return null
        val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(s) ?: return null
        val la = m.groupValues[1].toDoubleOrNull() ?: return null
        val lo = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (la == 0.0 && lo == 0.0) null else la to lo
    }

    /** Il nome del posto (città o paese, altrimenti la zona) in italiano. */
    @Suppress("DEPRECATION")
    fun placeName(ctx: Context, lat: Double, lon: Double): String? {
        // Stesso posto entro ~1 km: una sola richiesta.
        val key = "${(lat * 100).roundToInt()},${(lon * 100).roundToInt()}"
        synchronized(names) { names[key]?.let { return it } }
        if (!Geocoder.isPresent()) return null
        val a = runCatching { Geocoder(ctx, Locale.ITALY).getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() ?: return null
        val name = a.locality ?: a.subAdminArea ?: a.adminArea ?: a.countryName ?: return null
        synchronized(names) { names[key] = name }
        return name
    }
}
