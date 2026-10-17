package it.fabriziodavi.vivovideo.engine

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import it.fabriziodavi.vivovideo.model.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Un file citato dal progetto: indirizzo, nome e dimensione (per ritrovarlo se si sposta). */
@Serializable
data class MediaRef(val uri: String, val name: String = "", val size: Long = 0)

@Serializable
data class BackupManifest(val version: Int = 1, val app: String = "VivoVideo", val media: List<MediaRef> = emptyList())

/**
 * Riferimenti a file nel progetto (in qualunque campo): indirizzi "content://" e "file://", o percorsi
 * assoluti dentro la cartella dell'app. Si lavora sul JSON, così vale anche per i campi futuri.
 */
object ProjectRefs {
    private const val PACK = "vivo-pack:"

    fun isRef(s: String) = s.startsWith("content://") || s.startsWith("file://") ||
        (s.startsWith("/") && s.length in 4..1024 && '\n' !in s && runCatching { File(s).isFile }.getOrDefault(false))

    fun walk(e: JsonElement, f: (String) -> String?): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.mapValues { (_, v) -> walk(v, f) })
        is JsonArray -> JsonArray(e.map { walk(it, f) })
        is JsonPrimitive -> if (e.isString) e.contentOrNull?.let { s -> f(s)?.let { JsonPrimitive(it) } } ?: e else e
    }

    /** Tutti i riferimenti a file del progetto, senza doppioni. */
    fun refs(p: Project): List<String> {
        val out = LinkedHashSet<String>()
        walk(Project.JSON.encodeToJsonElement(Project.serializer(), p)) { s -> if (isRef(s)) out += s; null }
        return out.toList()
    }

    /** Progetto con ogni riferimento passato da [map] (null = lascia com'è). */
    fun replace(p: Project, map: (String) -> String?): Project {
        val tree = walk(Project.JSON.encodeToJsonElement(Project.serializer(), p)) { s -> if (isRef(s) || s.startsWith(PACK)) map(s) else null }
        return Project.JSON.decodeFromJsonElement(Project.serializer(), tree)
    }

    fun packToken(name: String, isPath: Boolean) = PACK + (if (isPath) "p:" else "u:") + name
    fun parseToken(s: String): Pair<String, Boolean>? =
        if (!s.startsWith(PACK)) null else s.removePrefix(PACK).let { r -> r.substring(2) to r.startsWith("p:") }
}

object Backup {
    const val EXT = "vivo"

    /** File locale per un riferimento (file:// o percorso), o null se è un indirizzo di sistema. */
    private fun localFile(ref: String): File? = when {
        ref.startsWith("file://") -> Uri.parse(ref).path?.let { File(it) }
        ref.startsWith("/") -> File(ref)
        else -> null
    }

    /** Nome e dimensione di un indirizzo. */
    fun describe(ctx: Context, ref: String): MediaRef {
        localFile(ref)?.let { return MediaRef(ref, it.name, if (it.exists()) it.length() else 0) }
        return runCatching {
            ctx.contentResolver.query(Uri.parse(ref), arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) MediaRef(ref, c.getString(0) ?: "", if (c.isNull(1)) 0 else c.getLong(1)) else null
            }
        }.getOrNull() ?: MediaRef(ref)
    }

    /** Il file c'è ed è leggibile? */
    fun exists(ctx: Context, ref: String): Boolean {
        localFile(ref)?.let { return it.exists() && it.length() > 0 }
        return runCatching { ctx.contentResolver.openFileDescriptor(Uri.parse(ref), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    /** File che il progetto cita ma non si trovano più (video, foto, brani, livelli). */
    fun missing(ctx: Context, p: Project): List<String> =
        (p.clips.map { it.uri } + p.layers.map { it.uri } + p.music.map { it.uri }).distinct().filter { !exists(ctx, it) }

    /**
     * Copia di sicurezza (zip .vivo): il progetto e i file creati dall'app (foto importate, voce, fermi immagine,
     * clip al contrario, maschere, LUT, font, brani scaricati); con [withMedia] anche i video originali.
     */
    fun export(ctx: Context, p: Project, withMedia: Boolean, out: OutputStream, onProgress: (Float) -> Unit) {
        val refs = ProjectRefs.refs(p)
        val names = HashMap<String, String>()
        val media = ArrayList<MediaRef>()
        var n = 0
        refs.forEach { r ->
            val local = localFile(r)
            val pack = if (local != null) local.exists() else withMedia && exists(ctx, r)
            if (pack) {
                val ext = (local?.extension ?: describe(ctx, r).name.substringAfterLast('.', "")).take(6)
                names[r] = "f${n++}" + if (ext.isNotEmpty()) ".$ext" else ""
            }
            if (local == null) media += describe(ctx, r)
        }
        val packed = ProjectRefs.replace(p) { r -> names[r]?.let { ProjectRefs.packToken(it, r.startsWith("/")) } }
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("project.json")); z.write(packed.toJson().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(Project.JSON.encodeToString(BackupManifest.serializer(), BackupManifest(media = media)).toByteArray()); z.closeEntry()
            names.entries.forEachIndexed { i, (r, name) ->
                val input: InputStream? = localFile(r)?.inputStream() ?: runCatching { ctx.contentResolver.openInputStream(Uri.parse(r)) }.getOrNull()
                input?.use { s ->
                    z.putNextEntry(ZipEntry("files/$name"))
                    s.copyTo(z, 1 shl 16); z.closeEntry()
                }
                onProgress((i + 1f) / names.size.coerceAtLeast(1))
            }
        }
    }

    class Imported(val project: Project, val media: List<MediaRef>)

    /** Apre una copia di sicurezza: i file vanno in una cartella dell'app, il progetto prende un nuovo nome se c'è già. */
    fun import(ctx: Context, input: InputStream, existingIds: Set<String>): Imported {
        val dir = File(ctx.filesDir, "importati/${System.currentTimeMillis()}").apply { mkdirs() }
        var json: String? = null
        var manifest = BackupManifest()
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                when {
                    e.name == "project.json" -> json = z.readBytes().toString(Charsets.UTF_8)
                    e.name == "manifest.json" -> manifest = runCatching {
                        Project.JSON.decodeFromString(BackupManifest.serializer(), z.readBytes().toString(Charsets.UTF_8))
                    }.getOrDefault(BackupManifest())
                    e.name.startsWith("files/") && !e.name.contains("..") -> File(dir, e.name.removePrefix("files/")).outputStream().use { z.copyTo(it, 1 shl 16) }
                }
            }
        }
        val p0 = Project.fromJson(json ?: error("Questo file non è una copia di VivoVideo."))
        var p = ProjectRefs.replace(p0) { r ->
            ProjectRefs.parseToken(r)?.let { (name, isPath) -> File(dir, name).let { f -> if (isPath) f.absolutePath else Uri.fromFile(f).toString() } }
        }
        if (p.id in existingIds) p = p.copy(id = UUID.randomUUID().toString(), name = p.name + " (copia)")
        return Imported(p.copy(updatedAt = System.currentTimeMillis()), manifest.media)
    }

    /**
     * Cerca nel telefono un file sparito per nome (e dimensione, se nota): restituisce il nuovo indirizzo o null.
     * Vale per video e foto della galleria.
     */
    fun findByName(ctx: Context, ref: MediaRef): String? {
        if (ref.name.isBlank()) return null
        // Per nome; la dimensione serve solo a scegliere fra file con lo stesso nome (Android la aggiorna con calma).
        for (base in listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)) {
            val hits = runCatching {
                ctx.contentResolver.query(
                    base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?", arrayOf(ref.name), MediaStore.MediaColumns.DATE_MODIFIED + " DESC",
                )?.use { c ->
                    val out = ArrayList<Pair<String, Long>>()
                    while (c.moveToNext()) out += ContentUris.withAppendedId(base, c.getLong(0)).toString() to (if (c.isNull(1)) 0L else c.getLong(1))
                    out
                }
            }.getOrNull().orEmpty()
            val best = hits.sortedByDescending { ref.size > 0 && it.second == ref.size }.firstOrNull { exists(ctx, it.first) }
            if (best != null) return best.first
        }
        return null
    }
}
