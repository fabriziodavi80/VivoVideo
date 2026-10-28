package it.fabriziodavi.vivovideo.engine

import android.content.Context
import it.fabriziodavi.vivovideo.model.Project
import java.io.File

/**
 * Pulizia della cache all'avvio. Con i progetti lunghi e i video 4K/8K la cache cresceva senza limiti:
 * copie leggere di video tolti dai progetti, colonne sonore e video esportati rimasti a metà (un'esportazione
 * interrotta lasciava fino a qualche GB). Le regole stanno in [plan], che non tocca file e si può provare.
 */
object CacheJanitor {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24 * HOUR_MS

    /** Temporanei (mix, colonne sonore, esportazioni, pezzi a metà): via se non toccati da un'ora. */
    const val TEMP_AGE_MS = HOUR_MS

    /** Copie leggere che nessun progetto usa più: via dopo due settimane. */
    const val UNUSED_PROXY_AGE_MS = 14 * DAY_MS

    /** Spazio massimo per le copie leggere non usate (le più vecchie se ne vanno per prime). */
    const val UNUSED_PROXY_BUDGET = 4L * 1024 * 1024 * 1024

    data class Entry(val path: String, val name: String, val size: Long, val modifiedMs: Long, val proxy: Boolean)

    private val tempPrefixes = listOf("timeline_", "mix_", "export_", "contrario_")

    fun isTemp(name: String): Boolean = tempPrefixes.any { name.startsWith(it) } || name.contains(".tmp")

    /**
     * Cosa cancellare (i percorsi). [inUse] sono i nomi delle copie leggere dei progetti salvati: quelle non si
     * toccano mai. Dei temporanei si guarda solo l'età: un'esportazione in corso li aggiorna di continuo.
     */
    fun plan(entries: List<Entry>, inUse: Set<String>, nowMs: Long): List<String> {
        val out = mutableListOf<String>()
        entries.filter { isTemp(it.name) && nowMs - it.modifiedMs > TEMP_AGE_MS }.forEach { out += it.path }
        val unused = entries.filter { it.proxy && !isTemp(it.name) && it.name !in inUse }
            .sortedByDescending { it.modifiedMs }
        var kept = 0L
        for (e in unused) {
            if (nowMs - e.modifiedMs > UNUSED_PROXY_AGE_MS || kept + e.size > UNUSED_PROXY_BUDGET) out += e.path
            else kept += e.size
        }
        return out
    }

    /** Nomi delle copie leggere (video e foto) che servono ai progetti. */
    fun inUse(ctx: Context, projects: List<Project>): Set<String> = projects.flatMap { p ->
        p.clips.flatMap { listOf(Proxies.file(ctx, it.uri).name, Proxies.photoFile(ctx, it.uri).name) } +
            p.layers.map { Proxies.file(ctx, it.uri).name }
    }.toSet()

    /** Fa la pulizia (lenta: fuori dal thread principale). Restituisce i byte liberati. */
    fun run(ctx: Context, projects: List<Project>, nowMs: Long = System.currentTimeMillis()): Long {
        fun entries(dir: File, proxy: Boolean) = dir.listFiles().orEmpty().filter { it.isFile }
            .map { Entry(it.absolutePath, it.name, it.length(), it.lastModified(), proxy) }
        // Dei tratti di anteprima si guardano solo i pezzi a metà: gli altri li gestisce già CrossfadePreview.trim.
        val all = entries(ctx.cacheDir, false) + entries(Proxies.dir(ctx), true) +
            entries(CrossfadePreview.dir(ctx), false).filter { isTemp(it.name) }
        val sizes = all.associate { it.path to it.size }
        return plan(all, inUse(ctx, projects), nowMs).sumOf { path ->
            if (File(path).delete()) sizes[path] ?: 0L else 0L
        }
    }
}
