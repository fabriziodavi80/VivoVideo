package it.fabriziodavi.vivovideo.engine

import android.content.Context
import android.net.Uri
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import java.io.File
import java.security.MessageDigest

/**
 * Copie leggere dei video pesanti (oltre il 1080p o sopra i 60 fotogrammi al secondo), solo per l'anteprima:
 * 720p con la stessa durata, gli stessi fotogrammi e l'audio. L'esportazione usa sempre gli originali.
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

    /** Il progetto per l'anteprima: dove la copia leggera è pronta, si usa quella. */
    fun forPreview(context: Context, p: Project): Project {
        fun swap(uri: String) = if (ready(context, uri)) Uri.fromFile(file(context, uri)).toString() else uri
        return p.copy(
            clips = p.clips.map { if (needs(it)) it.copy(uri = swap(it.uri)) else it },
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
