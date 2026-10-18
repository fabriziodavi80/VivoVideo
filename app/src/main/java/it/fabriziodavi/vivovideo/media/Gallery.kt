package it.fabriziodavi.vivovideo.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/** Un album della galleria (una cartella: Fotocamera, WhatsApp, Screenshot…). */
data class Album(val id: String, val name: String, val count: Int, val videos: Int, val coverUri: Uri, val latestMs: Long)

/** Una foto o un video della galleria. */
data class GalleryItem(
    val uri: Uri, val isVideo: Boolean, val durationMs: Long, val dateMs: Long, val albumId: String,
    /** Misure come le vede chi guarda (0 se non note). */
    val width: Int = 0, val height: Int = 0,
)

/** Lettura di album e contenuti dalla galleria del telefono (MediaStore). */
object Gallery {
    val permissions: Array<String> = if (Build.VERSION.SDK_INT >= 34)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)

    /** Accesso completo (tutti gli album) o solo ad alcune foto scelte. */
    fun fullAccess(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

    fun anyAccess(ctx: Context): Boolean = fullAccess(ctx) || (Build.VERSION.SDK_INT >= 34 &&
        ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

    /** Tutti gli elementi (facoltativamente solo di alcuni album), dal più vecchio al più recente. */
    fun items(ctx: Context, albumIds: Set<String>? = null): List<GalleryItem> {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.ORIENTATION,
        )
        val sel = "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val out = ArrayList<GalleryItem>()
        ctx.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(proj[0]); val iType = c.getColumnIndexOrThrow(proj[1])
            val iBucket = c.getColumnIndexOrThrow(proj[2]); val iTaken = c.getColumnIndexOrThrow(proj[3])
            val iAdded = c.getColumnIndexOrThrow(proj[4]); val iDur = c.getColumnIndexOrThrow(proj[5])
            val iW = c.getColumnIndex(proj[6]); val iH = c.getColumnIndex(proj[7]); val iRot = c.getColumnIndex(proj[8])
            while (c.moveToNext()) {
                val bucket = c.getString(iBucket) ?: continue
                if (albumIds != null && bucket !in albumIds) continue
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                val dur = if (video) c.getLong(iDur) else 0L
                if (video && dur < 500) continue // video vuoti o rovinati
                var w = if (iW >= 0) c.getInt(iW) else 0
                var h = if (iH >= 0) c.getInt(iH) else 0
                val rot = if (iRot >= 0) c.getInt(iRot) else 0
                if (rot % 180 != 0) { val t = w; w = h; h = t }
                out += GalleryItem(ContentUris.withAppendedId(base, id), video, dur, taken, bucket, w, h)
            }
        }
        return out.sortedBy { it.dateMs }
    }

    /** Album con quanti elementi contengono, dal più recente. */
    fun albums(ctx: Context): List<Album> {
        val names = HashMap<String, String>()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        ctx.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)",
            arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()), null,
        )?.use { c -> while (c.moveToNext()) { val b = c.getString(0) ?: continue; names.getOrPut(b) { c.getString(1) ?: "Senza nome" } } }
        return items(ctx).groupBy { it.albumId }.map { (id, list) ->
            val latest = list.maxBy { it.dateMs }
            Album(id, names[id] ?: "Senza nome", list.size, list.count { it.isVideo }, latest.uri, latest.dateMs)
        }.sortedByDescending { it.latestMs }
    }

    fun thumbnail(ctx: Context, uri: Uri, px: Int = 256): Bitmap? =
        runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) }.getOrNull()
}
