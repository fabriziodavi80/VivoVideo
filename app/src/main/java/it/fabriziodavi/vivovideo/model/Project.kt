package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos

/*
 * Modello del progetto: descrive COSA c'è nel video, non come si disegna.
 * Kotlin puro, senza Android: si testa sul PC. Tutti i tempi sono in microsecondi.
 */

@Serializable
enum class ClipSpeed(val label: String) {
    NORMAL("1×"), SLOW("0,5×"), FAST("2×"), RAMP("Rampa");
}

@Serializable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    /** Durata dell'intero file sorgente. */
    val sourceDurationUs: Long,
    val trimStartUs: Long = 0,
    val trimEndUs: Long = sourceDurationUs,
    val speed: ClipSpeed = ClipSpeed.NORMAL,
    /** Video girato in LOG: applica la conversione colore. */
    val log: Boolean = false,
    val muted: Boolean = false,
    /** Volume dell'audio originale: 1 = invariato, da 0 a 2. */
    val volume: Float = 1f,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 30f,
) {
    val trimmedUs: Long get() = trimEndUs - trimStartUs

    /** Curva di velocità sul tempo della clip tagliata (0 = inizio del taglio). */
    fun speedCurve(): SpeedCurve = SpeedCurve.forClip(speed, trimmedUs)

    /** Durata di questa clip nel video finale. */
    val outputUs: Long get() = speedCurve().outputDurationUs()

    val isHeavy: Boolean get() = width.toLong() * height * fps >= 3840L * 2160 * 120
}

@Serializable
data class ColorSettings(
    val look: String = "NONE",
    val highlights: Int = 0,
    val denoise: Int = 0,
    /** File .cube importato e copiato nella cartella dell'app (percorso), o null. */
    val lutPath: String? = null,
    val lutName: String? = null,
)

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clips: List<Clip> = emptyList(),
    val color: ColorSettings = ColorSettings(),
) {
    val durationUs: Long get() = clips.sumOf { it.outputUs }

    /** Inizio di ogni clip sulla timeline finale. */
    fun clipStartsUs(): List<Long> {
        var acc = 0L
        return clips.map { c -> acc.also { acc += c.outputUs } }
    }

    /** Clip che contiene il tempo t della timeline, con l'offset dentro la clip. */
    fun locate(timeUs: Long): Pair<Int, Long>? {
        if (clips.isEmpty()) return null
        var acc = 0L
        clips.forEachIndexed { i, c ->
            val out = c.outputUs
            if (timeUs < acc + out || i == clips.lastIndex) return i to (timeUs - acc).coerceIn(0, out)
            acc += out
        }
        return null
    }

    fun touch() = copy(updatedAt = System.currentTimeMillis())

    // ---------------- Modifiche (restituiscono un nuovo progetto: undo facile) ----------------

    fun addClips(newClips: List<Clip>, atIndex: Int = clips.size): Project =
        copy(clips = clips.toMutableList().apply { addAll(atIndex.coerceIn(0, size), newClips) })

    fun removeClip(id: String): Project = copy(clips = clips.filterNot { it.id == id })

    fun replaceClip(clip: Clip): Project = copy(clips = clips.map { if (it.id == clip.id) clip else it })

    fun moveClip(id: String, delta: Int): Project {
        val i = clips.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in clips.indices) return this
        val list = clips.toMutableList()
        list.add(j, list.removeAt(i))
        return copy(clips = list)
    }

    /**
     * Divide la clip che si trova al tempo t della timeline.
     * Restituisce null se t cade troppo vicino a un bordo o se la clip ha una rampa
     * (la rampa è definita sull'intera clip e dividerla cambierebbe il risultato).
     */
    fun splitAt(timeUs: Long, minPieceUs: Long = 100_000): Project? {
        val (index, offsetOut) = locate(timeUs) ?: return null
        val clip = clips[index]
        if (clip.speed == ClipSpeed.RAMP) return null
        val offsetSrc = clip.speedCurve().sourceTimeAt(offsetOut)
        val cut = clip.trimStartUs + offsetSrc
        if (cut - clip.trimStartUs < minPieceUs || clip.trimEndUs - cut < minPieceUs) return null
        val first = clip.copy(trimEndUs = cut)
        val second = clip.copy(id = UUID.randomUUID().toString(), trimStartUs = cut)
        val list = clips.toMutableList()
        list[index] = first
        list.add(index + 1, second)
        return copy(clips = list)
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
        fun fromJson(text: String): Project = JSON.decodeFromString(serializer(), text)
    }
}

/**
 * Velocità a tratti costanti sul tempo della clip (sorgente).
 * È la stessa curva che si passa a Media3 per il video e al motore audio nativo,
 * così immagini e suono restano sincronizzati.
 */
class SpeedCurve(val segEndUs: LongArray, val speeds: FloatArray, val durationUs: Long) {

    fun speedAt(tUs: Long): Float {
        for (i in segEndUs.indices) if (tUs < segEndUs[i]) return speeds[i]
        return speeds.lastOrNull() ?: 1f
    }

    /** Fine del tratto che contiene t, o null se è l'ultimo. */
    fun nextChangeUs(tUs: Long): Long? {
        for (i in segEndUs.indices) if (tUs < segEndUs[i]) return if (i == segEndUs.lastIndex) null else segEndUs[i]
        return null
    }

    fun outputDurationUs(): Long = outputTimeAt(durationUs)

    /** Tempo nel video finale corrispondente al tempo sorgente t. */
    fun outputTimeAt(tUs: Long): Long {
        var acc = 0.0
        var start = 0L
        for (i in segEndUs.indices) {
            val end = minOf(segEndUs[i], durationUs)
            if (tUs <= end) return (acc + (tUs - start) / speeds[i]).toLong()
            acc += (end - start) / speeds[i]
            start = end
        }
        return acc.toLong()
    }

    /** Tempo sorgente corrispondente al tempo t del video finale (inversa). */
    fun sourceTimeAt(outUs: Long): Long {
        var acc = 0.0
        var start = 0L
        for (i in segEndUs.indices) {
            val end = minOf(segEndUs[i], durationUs)
            val segOut = (end - start) / speeds[i]
            if (outUs <= acc + segOut) return (start + (outUs - acc) * speeds[i]).toLong().coerceAtMost(durationUs)
            acc += segOut
            start = end
        }
        return durationUs
    }

    companion object {
        private const val RAMP_STEP_US = 40_000L

        fun constant(speed: Float, durationUs: Long) =
            SpeedCurve(longArrayOf(durationUs), floatArrayOf(speed), durationUs)

        /** Rampa veloce → lento → veloce (2× → 0,35× → 2×), a gradini di 40 ms. */
        fun ramp(durationUs: Long, fast: Float = 2f, slow: Float = 0.35f): SpeedCurve {
            val n = ((durationUs + RAMP_STEP_US - 1) / RAMP_STEP_US).toInt().coerceAtLeast(1)
            val ends = LongArray(n)
            val sp = FloatArray(n)
            for (i in 0 until n) {
                val start = i * RAMP_STEP_US
                ends[i] = minOf(start + RAMP_STEP_US, durationUs)
                val t = start.toDouble() / durationUs
                val c = 0.5 + 0.5 * cos(2 * PI * t)
                sp[i] = (slow + (fast - slow) * c).toFloat()
            }
            return SpeedCurve(ends, sp, durationUs)
        }

        fun forClip(speed: ClipSpeed, durationUs: Long): SpeedCurve = when (speed) {
            ClipSpeed.NORMAL -> constant(1f, durationUs)
            ClipSpeed.SLOW -> constant(0.5f, durationUs)
            ClipSpeed.FAST -> constant(2f, durationUs)
            ClipSpeed.RAMP -> ramp(durationUs)
        }
    }
}
