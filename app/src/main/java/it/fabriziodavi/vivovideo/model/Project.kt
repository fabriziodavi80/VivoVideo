package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/*
 * Modello del progetto: descrive COSA c'è nel video, non come si disegna.
 * Kotlin puro, senza Android: si testa sul PC. Tutti i tempi sono in microsecondi.
 */

@Serializable
enum class ClipSpeed(val label: String) {
    NORMAL("1×"), SLOW("0,5×"), FAST("2×"),
    /** Curva disegnata a mano (nei progetti vecchi era la "Rampa" fissa). */
    RAMP("Curva");
}

/** Punto della curva di velocità: [t] posizione lungo la clip (0 inizio, 1 fine), [speed] velocità. */
@Serializable
data class SpeedPoint(val t: Float, val speed: Float)

object SpeedLimits {
    const val MIN = 0.2f
    const val MAX = 5f
}

/** Curve pronte, come negli editor professionali. */
enum class SpeedPreset(val label: String, val points: List<SpeedPoint>) {
    VALLEY("Rallenty centrale", listOf(SpeedPoint(0f, 2f), SpeedPoint(0.5f, 0.35f), SpeedPoint(1f, 2f))),
    HERO("Eroe", listOf(SpeedPoint(0f, 1f), SpeedPoint(0.38f, 1f), SpeedPoint(0.5f, 0.25f), SpeedPoint(0.62f, 1f), SpeedPoint(1f, 1f))),
    BULLET("Proiettile", listOf(SpeedPoint(0f, 3f), SpeedPoint(0.42f, 0.3f), SpeedPoint(0.58f, 0.3f), SpeedPoint(1f, 3f))),
    MONTAGE("Montaggio", listOf(SpeedPoint(0f, 0.5f), SpeedPoint(0.3f, 3f), SpeedPoint(0.7f, 3f), SpeedPoint(1f, 0.5f))),
    FLASH_IN("Entrata lampo", listOf(SpeedPoint(0f, 4f), SpeedPoint(0.3f, 1f), SpeedPoint(1f, 1f))),
    SLOW_END("Rallenty finale", listOf(SpeedPoint(0f, 1f), SpeedPoint(0.6f, 1f), SpeedPoint(1f, 0.25f))),
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
    /** Punti della curva quando [speed] è RAMP ("Curva"); vuota = preset "Rallenty centrale". */
    val speedPoints: List<SpeedPoint> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 30f,
) {
    val trimmedUs: Long get() = trimEndUs - trimStartUs

    /** Curva di velocità sul tempo della clip tagliata (0 = inizio del taglio). */
    val curvePoints: List<SpeedPoint> get() = speedPoints.ifEmpty { SpeedPreset.VALLEY.points }

    fun speedCurve(): SpeedCurve = SpeedCurve.forClip(speed, trimmedUs, curvePoints)

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
        val offsetSrc = clip.speedCurve().sourceTimeAt(offsetOut)
        val cut = clip.trimStartUs + offsetSrc
        if (cut - clip.trimStartUs < minPieceUs || clip.trimEndUs - cut < minPieceUs) return null
        var first = clip.copy(trimEndUs = cut)
        var second = clip.copy(id = UUID.randomUUID().toString(), trimStartUs = cut)
        if (clip.speed == ClipSpeed.RAMP) {
            // La curva si divide in due: ogni metà conserva la sua parte di curva.
            val f = offsetSrc.toFloat() / clip.trimmedUs
            val (a, b) = splitPoints(clip.curvePoints, f)
            first = first.copy(speedPoints = a)
            second = second.copy(speedPoints = b)
        }
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

/** Divide i punti di una curva nella posizione f (0..1), riscalando le due metà. */
fun splitPoints(points: List<SpeedPoint>, f: Float): Pair<List<SpeedPoint>, List<SpeedPoint>> {
    // Oltre ai punti esistenti, campiona la curva originale dentro ogni metà:
    // così ciascuna metà mantiene la stessa forma (e quindi la stessa durata).
    fun half(from: Float, to: Float): List<SpeedPoint> {
        val inner = points.filter { it.t > from && it.t < to }.map { it.t }
        val samples = (1..3).map { from + (to - from) * it / 4f }.filter { s -> inner.none { abs(it - s) < (to - from) * 0.08f } }
        val ts = (listOf(from, to) + inner + samples).sorted()
        return ts.map { t -> SpeedPoint((t - from) / (to - from), SpeedInterp.valueAt(points, t)) }
    }
    return half(0f, f) to half(f, 1f)
}

/**
 * Interpolazione morbida della curva (cubica monotona sul logaritmo della velocità):
 * passa esattamente dai punti e non "rimbalza" mai oltre i loro valori.
 */
object SpeedInterp {
    fun valueAt(points: List<SpeedPoint>, t: Float): Float {
        val p = points.sortedBy { it.t }
        if (p.isEmpty()) return 1f
        if (p.size == 1 || t <= p.first().t) return p.first().speed
        if (t >= p.last().t) return p.last().speed
        val xs = p.map { it.t.toDouble() }
        val ys = p.map { kotlin.math.ln(it.speed.toDouble()) }
        val n = p.size
        val d = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-6) }
        val m = DoubleArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
        // Fritsch–Carlson: evita sovraelongazioni.
        for (i in 0 until n - 1) {
            if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9) { val k = 3 / kotlin.math.sqrt(s); m[i] = k * a * d[i]; m[i + 1] = k * b * d[i] }
        }
        var k = 0
        while (k < n - 2 && t > xs[k + 1]) k++
        val h = (xs[k + 1] - xs[k]).coerceAtLeast(1e-6)
        val u = (t - xs[k]) / h
        val h00 = 2 * u * u * u - 3 * u * u + 1
        val h10 = u * u * u - 2 * u * u + u
        val h01 = -2 * u * u * u + 3 * u * u
        val h11 = u * u * u - u * u
        val y = h00 * ys[k] + h10 * h * m[k] + h01 * ys[k + 1] + h11 * h * m[k + 1]
        return kotlin.math.exp(y).toFloat().coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
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

        /** Curva disegnata, campionata a gradini di 40 ms (al centro di ogni gradino). */
        fun fromPoints(points: List<SpeedPoint>, durationUs: Long): SpeedCurve {
            val n = ((durationUs + RAMP_STEP_US - 1) / RAMP_STEP_US).toInt().coerceAtLeast(1)
            val ends = LongArray(n)
            val sp = FloatArray(n)
            for (i in 0 until n) {
                val start = i * RAMP_STEP_US
                ends[i] = minOf(start + RAMP_STEP_US, durationUs)
                val mid = (start + ends[i]) / 2.0 / durationUs
                sp[i] = SpeedInterp.valueAt(points, mid.toFloat())
            }
            return SpeedCurve(ends, sp, durationUs)
        }

        fun forClip(speed: ClipSpeed, durationUs: Long, points: List<SpeedPoint> = SpeedPreset.VALLEY.points): SpeedCurve = when (speed) {
            ClipSpeed.NORMAL -> constant(1f, durationUs)
            ClipSpeed.SLOW -> constant(0.5f, durationUs)
            ClipSpeed.FAST -> constant(2f, durationUs)
            ClipSpeed.RAMP -> fromPoints(points, durationUs)
        }
    }
}
