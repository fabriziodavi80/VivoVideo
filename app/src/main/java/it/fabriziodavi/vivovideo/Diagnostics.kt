package it.fabriziodavi.vivovideo

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TestPlan
import it.fabriziodavi.vivovideo.online.LibraryKeys
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diario dell'app: gli ultimi eventi importanti (messaggi, errori dell'anteprima, esportazioni, crash),
 * in un file che sopravvive alla chiusura. Finisce nel rapporto "Segnala un problema".
 */
object Diary {
    private const val MAX_BYTES = 200_000L
    @Volatile private var file: File? = null
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY)

    fun init(ctx: Context) { file = File(ctx.filesDir, "diario.txt") }

    fun log(text: String) {
        val f = file ?: return
        runCatching {
            synchronized(this) {
                f.appendText("${fmt.format(Date())}  ${text.replace('\n', ' ').take(600)}\n")
                if (f.length() > MAX_BYTES) f.writeText(f.readText().takeLast((MAX_BYTES / 2).toInt()).substringAfter('\n'))
            }
        }
    }

    fun tail(lines: Int = 300): List<String> = runCatching { file?.readLines()?.takeLast(lines) }.getOrNull().orEmpty()
}

/** Il rapporto da mandare: telefono, impostazioni, collaudo, progetto, diario, ultimi crash e log dell'app. Mai le chiavi dei servizi. */
object Diagnostics {
    fun crashHistory(ctx: Context) = File(ctx.filesDir, "crash_storia.txt")

    /** Toglie dal testo tutto ciò che somiglia a una chiave o a un codice d'accesso. */
    fun scrub(text: String, secrets: List<String>): String {
        var t = text
        secrets.filter { it.length >= 6 }.forEach { t = t.replace(it, "•••") }
        t = t.replace(Regex("(?i)(client_id|token|api_key|apikey|key)=([^&\\s\"']+)"), "$1=•••")
        t = t.replace(Regex("(?i)(Authorization: ?Token|Bearer) [A-Za-z0-9._-]+"), "$1 •••")
        return t
    }

    private fun section(sb: StringBuilder, title: String) { sb.append("\n\n===== ").append(title).append(" =====\n") }

    /**
     * Rapporto completo; con [compact] è più corto (da incollare nella chat): niente dati completi del progetto,
     * diario breve e solo le righe di errore del log.
     */
    fun report(ctx: Context, project: Project?, note: String, extra: String = "", compact: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("VivoVideo · segnalazione del ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())}\n")
        sb.append("Versione app: ${BuildConfig.VERSION_NAME}\n")
        sb.append("Telefono: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        runCatching { sb.append(" · ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}") }
        sb.append('\n')
        runCatching {
            val mi = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            val st = StatFs(ctx.filesDir.absolutePath)
            sb.append("Memoria: ${mi.totalMem / 1_000_000_000.0} GB (libera ${mi.availMem / 1_000_000} MB) · spazio libero ${st.availableBytes / 1_000_000} MB\n")
        }
        if (note.isNotBlank()) { section(sb, "Cosa è successo (scritto da te)"); sb.append(note.trim()).append('\n') }
        if (extra.isNotBlank()) { section(sb, "Dettagli"); sb.append(extra).append('\n') }

        section(sb, "Collaudo")
        val results = TestPlan.Store(ctx).all()
        val done = TestPlan.items.count { results[it.id]?.state != null }
        sb.append("Provati $done di ${TestPlan.items.size}\n")
        TestPlan.items.forEach { item ->
            val r = results[item.id] ?: return@forEach
            val s = r.state ?: return@forEach
            sb.append(if (s) "OK  " else "KO  ").append(item.area).append(" · ").append(item.title)
            if (r.note.isNotBlank()) sb.append(" — ").append(r.note.replace('\n', ' '))
            sb.append('\n')
        }

        section(sb, "Impostazioni")
        sb.append("Anteprima avanzata: ${Safety.multiPreview(ctx)} · esportazione avanzata: ${Safety.multiExport(ctx)}\n")
        sb.append("Copie leggere: ${ctx.getSharedPreferences("anteprima", Context.MODE_PRIVATE).getBoolean("copie_leggere", true)}\n")
        runCatching { sb.append("Encoder: ").append(describeEncoders().lines().take(6).joinToString(" | ")).append('\n') }

        if (project != null) {
            section(sb, "Progetto")
            sb.append("«${project.name}» · ${project.clips.size} clip · ${"%.1f".format(project.durationUs / 1e6)} s · formato ${project.format}\n")
            sb.append("Testi ${project.texts.size} · livelli ${project.layers.size} (video ${project.layers.count { it.isVideo }}) · brani ${project.music.size}\n")
            project.clips.take(60).forEachIndexed { i, c ->
                sb.append("#${i + 1} ").append(if (c.isPhoto) "foto" else "video").append(" ${c.width}×${c.height} ${"%.0f".format(c.fps)}fps")
                sb.append(" · ${"%.1f".format(c.outputUs / 1e6)} s")
                if (c.hdr > 0) sb.append(" · HDR${if (c.hdr == 2) "10" else " HLG"}")
                if (c.log) sb.append(" · LOG")
                if (c.speed.name != "NORMAL") sb.append(" · velocità ${c.speed.name}")
                if (c.fx.isNotEmpty()) sb.append(" · effetti ${c.fx.joinToString(",") { it.type.name }}")
                if (!c.grade.isIdentity) sb.append(" · colore")
                if (c.transitionOut != null) sb.append(" · transizione ${c.transitionOut.type.name}")
                if (c.stab != null) sb.append(" · stabilizzata")
                if (c.reframe != null) sb.append(" · segue")
                if (c.person != null) sb.append(" · persona ${c.person.mode}")
                if (c.reversed != null) sb.append(" · al contrario")
                if (!c.isPhoto && i < 30) runCatching { sb.append(" · ").append(readVideoInfo(ctx, Uri.parse(c.uri)).summary().replace('\n', ' ')) }
                sb.append('\n')
            }
            if (!compact) {
                section(sb, "Progetto (dati completi)")
                sb.append(project.toJson().take(80_000)).append('\n')
            }
        }

        section(sb, "Ultimi crash")
        sb.append(runCatching { crashHistory(ctx).takeIf { it.exists() }?.readText()?.takeLast(if (compact) 4_000 else 12_000) }.getOrNull() ?: "nessuno").append('\n')

        section(sb, "Diario")
        Diary.tail(if (compact) 80 else 300).forEach { sb.append(it).append('\n') }

        section(sb, if (compact) "Errori nel log dell'app" else "Log dell'app")
        sb.append(runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "1500", "--pid=${android.os.Process.myPid()}"))
            val text = p.inputStream.bufferedReader().readText()
            if (compact) errorLines(text).takeLast(12_000) else text.takeLast(150_000)
        }.getOrDefault("(non disponibile)"))

        val keys = LibraryKeys.load(ctx).let { listOfNotNull(it.jamendo, it.freesound) }
        return scrub(sb.toString(), keys)
    }

    /** Solo le righe di errore e avviso (e le cause) di un log: per il rapporto compatto. */
    fun errorLines(log: String): String = log.lineSequence()
        .filter { l -> Regex(" [EWF] |Exception|Caused by|FATAL").containsMatchIn(l) }
        .joinToString("\n")

    /** Scrive il rapporto in un file da condividere. */
    fun write(ctx: Context, text: String): File {
        val dir = File(ctx.cacheDir, "segnalazioni").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        return File(dir, "VivoVideo_segnalazione_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())}.txt").apply { writeText(text) }
    }
}
