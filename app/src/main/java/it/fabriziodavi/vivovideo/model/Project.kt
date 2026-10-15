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

/** Tipi di transizione. Agiscono sulla fine di una clip e sull'inizio della successiva. */
@Serializable
enum class TransitionType(val label: String) {
    FADE_BLACK("Nero"),
    FLASH("Flash"),
    ZOOM("Zoom"),
    BLUR("Sfocatura"),
    WHIP("Scorrimento"),
    SPIN("Rotazione"),
}

@Serializable
data class TransitionSpec(val type: TransitionType, val durationUs: Long = 500_000)

/** Andamento fra un keyframe e il successivo. */
@Serializable
enum class Easing(val label: String) {
    SMOOTH("Morbido"), LINEAR("Lineare"), EASE_IN("Accelera"), EASE_OUT("Rallenta"), HOLD("Scatto");

    fun apply(u: Float): Float = when (this) {
        LINEAR -> u
        SMOOTH -> u * u * (3 - 2 * u)
        EASE_IN -> u * u * u
        EASE_OUT -> 1 - (1 - u) * (1 - u) * (1 - u)
        HOLD -> 0f
    }
}

/** Stato della trasformazione di una clip in un istante. */
@Serializable
data class Transform(
    /** Spostamento in frazioni del fotogramma (1 = un fotogramma intero). */
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    /** Gradi, in senso orario. */
    val rotation: Float = 0f,
    /** 0 = nero, 1 = pieno. */
    val opacity: Float = 1f,
) {
    val isIdentity get() = x == 0f && y == 0f && scale == 1f && rotation == 0f && opacity == 1f

    fun lerp(o: Transform, u: Float) = Transform(
        x + (o.x - x) * u, y + (o.y - y) * u,
        // lo zoom si interpola in scala logaritmica: velocità di zoom costante a occhio
        kotlin.math.exp(kotlin.math.ln(scale) + (kotlin.math.ln(o.scale) - kotlin.math.ln(scale)) * u).toFloat(),
        rotation + (o.rotation - rotation) * u,
        opacity + (o.opacity - opacity) * u,
    )
}

/** Keyframe: [timeUs] dall'inizio della clip tagliata, sul tempo del video originale. */
@Serializable
data class Keyframe(val timeUs: Long, val value: Transform, val easing: Easing = Easing.SMOOTH)

object Keyframes {
    /** Valore della trasformazione al tempo t (sorgente, relativo all'inizio della clip). */
    fun valueAt(keys: List<Keyframe>, tUs: Long): Transform {
        if (keys.isEmpty()) return Transform()
        val k = keys.sortedBy { it.timeUs }
        if (tUs <= k.first().timeUs) return k.first().value
        if (tUs >= k.last().timeUs) return k.last().value
        val i = k.indexOfLast { it.timeUs <= tUs }
        val a = k[i]; val b = k[i + 1]
        val u = ((tUs - a.timeUs).toFloat() / (b.timeUs - a.timeUs).coerceAtLeast(1)).coerceIn(0f, 1f)
        return a.value.lerp(b.value, a.easing.apply(u))
    }

    /** Mette o aggiorna un keyframe al tempo t (entro [snapUs] riusa quello esistente). */
    fun set(keys: List<Keyframe>, tUs: Long, value: Transform, snapUs: Long = 40_000): List<Keyframe> {
        val existing = keys.firstOrNull { kotlin.math.abs(it.timeUs - tUs) <= snapUs }
        return if (existing != null) keys.map { if (it === existing) it.copy(value = value) else it }
        else (keys + Keyframe(tUs, value)).sortedBy { it.timeUs }
    }
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
    FLAT("Piatta 1×", listOf(SpeedPoint(0f, 1f), SpeedPoint(1f, 1f))),
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
    /** Animazione di posizione, zoom, rotazione e opacità (tempi sul video originale della clip). */
    val keyframes: List<Keyframe> = emptyList(),
    /** Transizione verso la clip successiva (null = taglio netto). */
    val transitionOut: TransitionSpec? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 30f,
) {
    val trimmedUs: Long get() = trimEndUs - trimStartUs

    /** Curva di velocità sul tempo della clip tagliata (0 = inizio del taglio). */
    val curvePoints: List<SpeedPoint> get() = speedPoints.ifEmpty { SpeedPreset.VALLEY.points }

    // Calcolati una volta per istanza (la timeline li chiede molte volte al secondo).
    private val cachedCurve: SpeedCurve by lazy { SpeedCurve.forClip(speed, trimmedUs, curvePoints) }
    private val cachedOutputUs: Long by lazy { cachedCurve.outputDurationUs() }

    fun speedCurve(): SpeedCurve = cachedCurve

    /** Durata di questa clip nel video finale. */
    val outputUs: Long get() = cachedOutputUs

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

    /**
     * Durata effettiva di metà transizione su ciascun lato del taglio fra la clip [i] e la successiva:
     * mai più di metà della clip più corta, così due transizioni non si sovrappongono.
     */
    fun transitionHalfUs(i: Int): Long {
        val spec = clips.getOrNull(i)?.transitionOut ?: return 0
        val next = clips.getOrNull(i + 1) ?: return 0
        val half = spec.durationUs / 2
        return minOf(half, clips[i].outputUs / 2, next.outputUs / 2).coerceAtLeast(0)
    }

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
        // I keyframe seguono il video: ogni metà tiene i suoi, più il valore nel punto di taglio.
        val local = cut - clip.trimStartUs
        val atCut = Keyframes.valueAt(clip.keyframes, local)
        val k1 = if (clip.keyframes.isEmpty()) emptyList() else
            clip.keyframes.filter { it.timeUs < local } + Keyframe(local, atCut)
        val k2 = if (clip.keyframes.isEmpty()) emptyList() else
            listOf(Keyframe(0, atCut)) + clip.keyframes.filter { it.timeUs > local }.map { it.copy(timeUs = it.timeUs - local) }
        var first = clip.copy(trimEndUs = cut, keyframes = k1)
        var second = clip.copy(id = UUID.randomUUID().toString(), trimStartUs = cut, keyframes = k2)
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

object SpeedPoints {
    /** Mette o aggiorna un punto in posizione t (entro [snap] riusa quello vicino; i bordi restano bordi). */
    fun set(points: List<SpeedPoint>, t: Float, speed: Float, snap: Float = 0.02f): List<SpeedPoint> {
        val s = speed.coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
        val near = points.indices.minByOrNull { kotlin.math.abs(points[it].t - t) }
            ?.takeIf { kotlin.math.abs(points[it].t - t) <= snap }
        return if (near != null) points.mapIndexed { i, p -> if (i == near) p.copy(speed = s) else p }
        else (points + SpeedPoint(t.coerceIn(0f, 1f), s)).sortedBy { it.t }
    }

    fun nearest(points: List<SpeedPoint>, t: Float, snap: Float = 0.02f): SpeedPoint? =
        points.minByOrNull { kotlin.math.abs(it.t - t) }?.takeIf { kotlin.math.abs(it.t - t) <= snap }
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
    fun valueAt(points: List<SpeedPoint>, t: Float): Float = sampler(points)(t)

    /** Prepara la curva una volta sola; la funzione restituita si valuta velocemente molte volte. */
    fun sampler(points: List<SpeedPoint>): (Float) -> Float {
        val p = points.sortedBy { it.t }
        if (p.isEmpty()) return { 1f }
        if (p.size == 1) return { p[0].speed }
        val n = p.size
        val xs = DoubleArray(n) { p[it].t.toDouble() }
        val ys = DoubleArray(n) { kotlin.math.ln(p[it].speed.toDouble()) }
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
        return { tf ->
            val t = tf.toDouble()
            when {
                t <= xs[0] -> p[0].speed
                t >= xs[n - 1] -> p[n - 1].speed
                else -> {
                    var k = 0
                    while (k < n - 2 && t > xs[k + 1]) k++
                    val h = (xs[k + 1] - xs[k]).coerceAtLeast(1e-6)
                    val u = (t - xs[k]) / h
                    val h00 = 2 * u * u * u - 3 * u * u + 1
                    val h10 = u * u * u - 2 * u * u + u
                    val h01 = -2 * u * u * u + 3 * u * u
                    val h11 = u * u * u - u * u
                    val y = h00 * ys[k] + h10 * h * m[k] + h01 * ys[k + 1] + h11 * h * m[k + 1]
                    kotlin.math.exp(y).toFloat().coerceIn(SpeedLimits.MIN, SpeedLimits.MAX)
                }
            }
        }
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
            val sampler = SpeedInterp.sampler(points)
            val n = ((durationUs + RAMP_STEP_US - 1) / RAMP_STEP_US).toInt().coerceAtLeast(1)
            val ends = LongArray(n)
            val sp = FloatArray(n)
            for (i in 0 until n) {
                val start = i * RAMP_STEP_US
                ends[i] = minOf(start + RAMP_STEP_US, durationUs)
                val mid = (start + ends[i]) / 2.0 / durationUs
                sp[i] = sampler(mid.toFloat())
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
