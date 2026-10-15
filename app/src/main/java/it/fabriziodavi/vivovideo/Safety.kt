package it.fabriziodavi.vivovideo

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Protezioni contro i crash: registra l'ultimo crash (da mostrare al riavvio) e ricorda se una
 * funzione sperimentale era in corso quando l'app è caduta, per disattivarla da sola.
 */
object Safety {
    private const val PREFS = "safety"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun crashFile(c: Context) = File(c.filesDir, "ultimo_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val pending = listOfNotNull(
                    "anteprima avanzata".takeIf { prefs(app).getBoolean("pending_preview", false) },
                    "esportazione avanzata".takeIf { prefs(app).getBoolean("pending_export", false) },
                )
                crashFile(app).writeText(
                    "Versione ${BuildConfig.VERSION_NAME} · thread ${thread.name}" +
                        (if (pending.isNotEmpty()) " · in corso: ${pending.joinToString()}" else "") +
                        "\n" + sw.toString().take(6000)
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Al riavvio: se l'app è caduta durante una funzione sperimentale, la spegne. Restituisce il rapporto del crash, se c'è. */
    fun checkAfterStart(context: Context): String? {
        val p = prefs(context)
        val notes = mutableListOf<String>()
        if (p.getBoolean("pending_preview", false)) {
            p.edit().putBoolean("pending_preview", false).putBoolean("multi_preview_v2", false).commit()
            notes += "L'anteprima con dissolvenze incrociate e video sovrapposti è stata disattivata (si riattiva da Esporta)."
        }
        if (p.getBoolean("pending_export", false)) {
            p.edit().putBoolean("pending_export", false).putBoolean("multi_export", false).commit()
            notes += "L'esportazione con dissolvenze incrociate e video sovrapposti è stata disattivata (si riattiva da Esporta)."
        }
        val f = crashFile(context)
        val report = if (f.exists()) f.readText().also { f.delete() } else null
        if (report == null && notes.isEmpty()) return null
        return buildString {
            append("L'app si era chiusa per un errore.")
            notes.forEach { append("\n\n").append(it) }
            if (report != null) append("\n\nDettaglio (mandamelo con uno screenshot):\n").append(report.lines().take(14).joinToString("\n"))
        }
    }

    // Chiave nuova: l'anteprima avanzata si blocca sul telefono, quindi riparte spenta per tutti.
    fun multiPreview(c: Context) = prefs(c).getBoolean("multi_preview_v2", false)
    fun setMultiPreview(c: Context, on: Boolean) = prefs(c).edit().putBoolean("multi_preview_v2", on).apply()
    fun multiExport(c: Context) = prefs(c).getBoolean("multi_export", true)
    fun setMultiExport(c: Context, on: Boolean) = prefs(c).edit().putBoolean("multi_export", on).apply()

    /** Segna l'inizio/fine di una funzione sperimentale (scrittura immediata: deve sopravvivere al crash). */
    fun pendingPreview(c: Context, on: Boolean) = prefs(c).edit().putBoolean("pending_preview", on).commit()
    fun pendingExport(c: Context, on: Boolean) = prefs(c).edit().putBoolean("pending_export", on).commit()
}
