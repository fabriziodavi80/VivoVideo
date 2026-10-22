package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import java.io.File
import java.security.MessageDigest

/**
 * Copie leggere dei video pesanti (oltre il 1080p o sopra i 60 fotogrammi al secondo), solo per l'anteprima:
 * 720p con la stessa durata, gli stessi fotogrammi e l'audio. L'esportazione usa sempre gli originali.
 * Anche le foto grandi hanno una copia leggera (lato lungo al massimo [PHOTO_SIDE]): aprire una foto da 12 MP
 * all'inizio della scena faceva scattare l'anteprima nel passaggio da un video a una foto.
 */
object Proxies {
    const val SHORT_SIDE = 720

    fun needs(c: Clip): Boolean = !c.isPhoto && !c.baked && (minOf(c.width, c.height) > 1200 || c.fps > 61f)

    fun dir(context: Context) = File(context.cacheDir, "copie_leggere").apply { mkdirs() }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    fun file(context: Context, uri: String): File = File(dir(context), sha(uri) + ".mp4")
    fun ready(context: Context, uri: String): Boolean = file(context, uri).let { it.exists() && it.length() > 1000 }

    /** Video del progetto (clip e video sovrapposti) che hanno bisogno di una copia leggera ancora da fare. */
    fun missing(context: Context, p: Project): List<String> =
        (p.clips.filter(::needs).map { it.uri } + p.layers.filter { it.isVideo && minOf(it.width, it.height) > 1200 }.map { it.uri })
            .distinct().filter { !ready(context, it) }

    const val PHOTO_SIDE = 2160

    fun photoNeeds(c: Clip): Boolean = c.isPhoto && maxOf(c.width, c.height) > PHOTO_SIDE
    fun photoFile(context: Context, uri: String): File = File(dir(context), sha(uri) + ".jpg")
    fun photoReady(context: Context, uri: String): Boolean = photoFile(context, uri).let { it.exists() && it.length() > 1000 }

    /** Foto del progetto senza ancora la copia leggera. */
    fun missingPhotos(context: Context, p: Project): List<String> =
        p.clips.filter(::photoNeeds).map { it.uri }.distinct().filter { !photoReady(context, it) }

    /** Fa la copia leggera di una foto (già raddrizzata all'importazione): veloce, si chiama fuori dal thread principale. */
    fun makePhoto(context: Context, uri: String): Boolean {
        val u = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_SIDE) sample *= 2
        var bmp = context.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val long = maxOf(bmp.width, bmp.height)
        if (long > PHOTO_SIDE) {
            val k = PHOTO_SIDE.toFloat() / long
            val r = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt() / 2 * 2, (bmp.height * k).toInt() / 2 * 2, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        val out = photoFile(context, uri)
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return tmp.renameTo(out)
    }

    /** Il progetto per l'anteprima: dove la copia leggera è pronta, si usa quella. */
    fun forPreview(context: Context, p: Project): Project {
        fun swap(uri: String) = if (ready(context, uri)) Uri.fromFile(file(context, uri)).toString() else uri
        fun swapPhoto(uri: String) = if (photoReady(context, uri)) Uri.fromFile(photoFile(context, uri)).toString() else uri
        return p.copy(
            clips = p.clips.map { if (needs(it)) it.copy(uri = swap(it.uri)) else if (photoNeeds(it)) it.copy(uri = swapPhoto(it.uri)) else it },
            layers = p.layers.map { if (it.isVideo) it.copy(uri = swap(it.uri)) else it },
        )
    }

    /** Lato lungo per una copia a 720p con le stesse proporzioni. */
    fun size(w: Int, h: Int): Pair<Int, Int> {
        fun even(v: Int) = v / 2 * 2
        return if (w >= h) even(SHORT_SIDE * w / h.coerceAtLeast(1)) to SHORT_SIDE else SHORT_SIDE to even(SHORT_SIDE * h / w.coerceAtLeast(1))
    }

    fun clear(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }
}
