package it.fabriziodavi.vivovideo.engine

import android.content.Context
import it.fabriziodavi.vivovideo.model.SpokenWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Riconoscimento vocale sul telefono (whisper.cpp): niente internet, tranne il primo
 * scaricamento del modello. Audio mono a 16 kHz in ingresso, parole con i loro tempi in uscita.
 */
object Whisper {
    /** Modelli: più grande = più preciso ma più lento. Si scaricano una volta sola. */
    enum class Model(val label: String, val file: String, val sizeMb: Int) {
        FAST("Veloce", "ggml-base-q5_1.bin", 60),
        ACCURATE("Preciso", "ggml-small-q5_1.bin", 190),
        /** Solo per le prove sull'emulatore. */
        TINY("Minimo", "ggml-tiny-q5_1.bin", 32);

        val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
    }

    private val loaded by lazy { runCatching { System.loadLibrary("vivowhisper") }.isSuccess }

    private external fun nativeInit(path: String): Long
    private external fun nativeFree(ctx: Long)
    private external fun nativeTranscribe(ctx: Long, pcm: FloatArray, lang: String, threads: Int): ByteArray
    private external fun nativeProgress(): Int
    private external fun nativeAbort()

    fun progress(): Int = if (loaded) nativeProgress() else 0
    fun abort() { if (loaded) nativeAbort() }

    fun modelFile(ctx: Context, m: Model) = File(File(ctx.filesDir, "whisper").apply { mkdirs() }, m.file)
    fun isDownloaded(ctx: Context, m: Model) = modelFile(ctx, m).let { it.exists() && it.length() > 1_000_000 }

    /** Scarica il modello (con avanzamento 0…1); se c'è già non fa nulla. */
    suspend fun download(ctx: Context, m: Model, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val f = modelFile(ctx, m)
        if (isDownloaded(ctx, m)) return@withContext f
        val tmp = File(f.parentFile, f.name + ".part")
        val c = URL(m.url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "VivoVideo/0.3 (Android)")
        try {
            if (c.responseCode !in 200..299) error("Scaricamento del modello non riuscito (${c.responseCode}).")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: (m.sizeMb * 1_000_000L)
            var done = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { c.disconnect() }
        if (tmp.length() < 1_000_000) { tmp.delete(); error("Il modello scaricato è incompleto.") }
        tmp.renameTo(f)
        f
    }

    /** Trascrive: [pcm] mono 16 kHz, [lang] "it", "en" o "auto". */
    suspend fun transcribe(model: File, pcm: FloatArray, lang: String): List<SpokenWord> = withContext(Dispatchers.Default) {
        if (!loaded) error("Il riconoscimento vocale non è disponibile su questo telefono.")
        val ctx = nativeInit(model.absolutePath)
        if (ctx == 0L) error("Modello non leggibile: prova a scaricarlo di nuovo.")
        try {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            parseWords(nativeTranscribe(ctx, pcm, lang, threads))
        } finally { nativeFree(ctx) }
    }

    /** Righe "inizio_ms \t fine_ms \t parola" → parole (UTF-8 tollerante: un carattere spezzato non blocca nulla). */
    fun parseWords(bytes: ByteArray): List<SpokenWord> =
        String(bytes, Charsets.UTF_8).lineSequence().mapNotNull { line ->
            val p = line.split('\t', limit = 3)
            if (p.size < 3) return@mapNotNull null
            val t0 = p[0].trim().toLongOrNull() ?: return@mapNotNull null
            val t1 = p[1].trim().toLongOrNull() ?: return@mapNotNull null
            val w = p[2].replace("�", "").trim()
            if (w.isEmpty()) null else SpokenWord(t0 * 1000, maxOf(t1, t0) * 1000, w)
        }.toList()

    /** WAV 48 kHz stereo 16 bit (come lo scrive renderTimelineAudio) → mono 16 kHz. */
    fun wavTo16kMono(wav: File): FloatArray {
        RandomAccessFile(wav, "r").use { raf ->
            val frames = ((raf.length() - 44) / 4).toInt()
            val out = FloatArray(frames / 3)
            val buf = ByteArray(3 * 4 * 4096)
            var o = 0
            raf.seek(44)
            while (o < out.size) {
                val want = minOf(4096, out.size - o)
                raf.readFully(buf, 0, want * 12)
                val bb = ByteBuffer.wrap(buf, 0, want * 12).order(ByteOrder.LITTLE_ENDIAN)
                for (k in 0 until want) {
                    var s = 0f
                    for (j in 0 until 6) s += bb.getShort((k * 6 + j) * 2) / 32768f
                    out[o++] = s / 6f
                }
            }
            return out
        }
    }
}
