package it.fabriziodavi.vivovideo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Lato lungo massimo delle foto nel progetto (abbondante per il 4K, leggero per la GPU). */
private const val MAX_SIDE = 4096

data class ImportedPhoto(val file: File, val width: Int, val height: Int)

/**
 * Copia una foto nella cartella dell'app, già raddrizzata (EXIF) e ridotta:
 * le foto da 200 MP non entrerebbero nella memoria video.
 */
fun importPhoto(context: Context, uri: Uri, dirName: String = "photos", keepAlpha: Boolean = false): ImportedPhoto {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "immagine non leggibile" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: error("immagine non leggibile")
    val orientation = runCatching {
        cr.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
    }
    val long = maxOf(bmp.width, bmp.height)
    if (long > MAX_SIDE) m.postScale(MAX_SIDE.toFloat() / long, MAX_SIDE.toFloat() / long)
    if (!m.isIdentity) {
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (r !== bmp) bmp.recycle()
        bmp = r
    }
    // Dimensioni pari: i codificatori video lo richiedono.
    val w = bmp.width / 2 * 2
    val h = bmp.height / 2 * 2
    if (w != bmp.width || h != bmp.height) {
        val r = Bitmap.createBitmap(bmp, 0, 0, w, h)
        if (r !== bmp) bmp.recycle()
        bmp = r
    }
    val dir = File(context.filesDir, dirName).apply { mkdirs() }
    val ext = if (keepAlpha) "png" else "jpg"
    val file = File(dir, "img_${System.currentTimeMillis()}_${(Math.random() * 1e6).toInt()}.$ext")
    FileOutputStream(file).use {
        // PNG conserva la trasparenza (loghi, adesivi); le foto restano JPEG, più leggere.
        if (keepAlpha) bmp.compress(Bitmap.CompressFormat.PNG, 100, it) else bmp.compress(Bitmap.CompressFormat.JPEG, 93, it)
    }
    bmp.recycle()
    return ImportedPhoto(file, w, h)
}

/** Miniatura di una foto (per la timeline). */
fun decodePhotoThumb(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
