package it.fabriziodavi.vivovideo.model

import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Dati della pedalata sul video (01/11/2026, "come faceva Garmin con le action cam, ma molto meglio").
 * Il file dell'attività (.fit di Garmin, .gpx o .tcx di Strava e degli altri) si legge qui; l'ora di ogni video
 * dice quale punto dell'attività mostrare in ogni istante del montaggio.
 */

/** Cosa si può mostrare. */
@Serializable
enum class TeleWidget(val label: String, val hint: String) {
    SPEED("Velocità", "Contagiri con lancetta luminosa e scie quando vai forte"),
    HEART("Battito", "Cuore che batte al tuo ritmo, zone di frequenza"),
    POWER("Potenza", "Watt con le zone (FTP)"),
    CADENCE("Cadenza", "Pedalate al minuto, la pedivella gira a tempo"),
    GRADE("Pendenza", "Percentuale e salita disegnata"),
    ALTITUDE("Altimetria", "Profilo del giro, con il punto in cui sei"),
    MAP("Mappa", "Il percorso che si disegna mentre pedali"),
    DISTANCE("Distanza", "Chilometri fatti e barra del giro"),
    TIME("Ora e tempo", "Ora del giorno e tempo trascorso"),
    TEMP("Temperatura", "Gradi dal sensore"),
}

/** Stile grafico di tutti gli indicatori. */
@Serializable
enum class TeleStyle(val label: String, val hint: String) {
    GLASS("Vetro liquido", "Pannelli trasparenti con riflessi, puliti e attuali"),
    HUD("Visore", "Linee sottili e tacche, come il casco di un pilota del futuro"),
    RACE("Gara", "Numeri inclinati e pieni, barre a LED come in TV"),
    NEON("Neon", "Contorni che si illuminano nel colore scelto"),
    MINIMAL("Essenziale", "Solo i numeri, grandi, con un'ombra"),
}

/** Dove sta un indicatore nel quadro. */
@Serializable
enum class TeleSlot(val label: String, val col: Int, val row: Int) {
    TOP_LEFT("In alto a sinistra", 0, 0), TOP("In alto al centro", 1, 0), TOP_RIGHT("In alto a destra", 2, 0),
    LEFT("A sinistra", 0, 1), RIGHT("A destra", 2, 1),
    BOTTOM_LEFT("In basso a sinistra", 0, 2), BOTTOM("In basso al centro", 1, 2), BOTTOM_RIGHT("In basso a destra", 2, 2),
}

@Serializable
data class TeleItem(val widget: TeleWidget, val slot: TeleSlot, val scale: Float = 1f)

/** Schemi pronti: un tocco e gli indicatori vanno al loro posto. */
enum class TeleLayout(val label: String, val items: List<TeleItem>) {
    ACTION("Azione", listOf(
        TeleItem(TeleWidget.SPEED, TeleSlot.BOTTOM_LEFT, 1.15f), TeleItem(TeleWidget.HEART, TeleSlot.BOTTOM_RIGHT),
        TeleItem(TeleWidget.POWER, TeleSlot.BOTTOM_RIGHT), TeleItem(TeleWidget.MAP, TeleSlot.TOP_RIGHT),
        TeleItem(TeleWidget.GRADE, TeleSlot.TOP_LEFT),
    )),
    CLIMB("Salita", listOf(
        TeleItem(TeleWidget.GRADE, TeleSlot.TOP_LEFT, 1.15f), TeleItem(TeleWidget.ALTITUDE, TeleSlot.BOTTOM, 1.1f),
        TeleItem(TeleWidget.POWER, TeleSlot.TOP_RIGHT), TeleItem(TeleWidget.HEART, TeleSlot.TOP_RIGHT),
    )),
    FULL("Completo", listOf(
        TeleItem(TeleWidget.SPEED, TeleSlot.BOTTOM_LEFT), TeleItem(TeleWidget.POWER, TeleSlot.BOTTOM_RIGHT),
        TeleItem(TeleWidget.HEART, TeleSlot.BOTTOM_RIGHT), TeleItem(TeleWidget.CADENCE, TeleSlot.BOTTOM_LEFT),
        TeleItem(TeleWidget.MAP, TeleSlot.TOP_RIGHT), TeleItem(TeleWidget.ALTITUDE, TeleSlot.TOP_LEFT),
        TeleItem(TeleWidget.GRADE, TeleSlot.LEFT), TeleItem(TeleWidget.DISTANCE, TeleSlot.TOP),
    )),
    CLEAN("Pulito", listOf(TeleItem(TeleWidget.SPEED, TeleSlot.BOTTOM, 1.2f))),
}

/** Impostazioni dei dati sportivi del progetto (il file è una copia interna dell'app). */
@Serializable
data class Telemetry(
    val file: String,
    val name: String,
    /** Per ogni video (uri): ora del suo primo fotogramma, in millisecondi UTC. */
    val clipStartMs: Map<String, Long> = emptyMap(),
    /** Spostamento a mano (se l'ora del telefono e quella del Garmin non coincidono). */
    val offsetMs: Long = 0,
    val items: List<TeleItem> = TeleLayout.ACTION.items,
    val style: TeleStyle = TeleStyle.GLASS,
    /** Colore di risalto (ARGB). */
    val accent: Int = ACCENTS[0],
    val size: Float = 1f,
    val opacity: Float = 1f,
    /** Per le zone: 0 = calcolate dal file. */
    val ftp: Int = 0,
    val hrMax: Int = 0,
    /** Badge "MAX" quando raggiungi il massimo del giro. */
    val peaks: Boolean = true,
    /** Video verticali: lascia libero il bordo che Instagram e TikTok coprono con i loro pulsanti. */
    val safeZone: Boolean = true,
) {
    companion object {
        val ACCENTS = listOf(0xFF2EF2C5.toInt(), 0xFFFF3D6E.toInt(), 0xFFFFC23D.toInt(), 0xFF4DA3FF.toInt(), 0xFFB36BFF.toInt(), 0xFFFFFFFF.toInt())
    }
}

/** Un istante dell'attività (NaN = dato assente). */
data class TeleSample(
    val timeMs: Long, val lat: Double, val lon: Double, val altitude: Float, val speedKmh: Float, val heart: Float,
    val power: Float, val cadence: Float, val distanceM: Float, val grade: Float, val temp: Float,
    /** Battiti e giri di pedivella dall'inizio (per far battere il cuore e girare la pedivella a tempo). */
    val beats: Double, val revs: Double,
    /** Indice (con frazione) nel tracciato: serve alla mappa e all'altimetria. */
    val index: Float,
)

/** L'attività letta dal file: colonne di dati con lo stesso numero di punti. */
class TeleTrack(
    val timeMs: LongArray,
    val lat: DoubleArray, val lon: DoubleArray,
    val altitude: FloatArray, val speedKmh: FloatArray, val heart: FloatArray, val power: FloatArray,
    val cadence: FloatArray, val distanceM: FloatArray, val grade: FloatArray, val temp: FloatArray,
) {
    val size get() = timeMs.size
    val startMs get() = timeMs.firstOrNull() ?: 0L
    val endMs get() = timeMs.lastOrNull() ?: 0L
    val beats = DoubleArray(size)
    val revs = DoubleArray(size)

    fun has(a: FloatArray) = a.any { !it.isNaN() }
    val hasGps get() = lat.any { !it.isNaN() }
    val hasHeart get() = has(heart)
    val hasPower get() = has(power)
    val hasCadence get() = has(cadence)
    val hasAltitude get() = has(altitude)
    val hasTemp get() = has(temp)

    fun max(a: FloatArray): Float = a.filter { !it.isNaN() }.maxOrNull() ?: 0f
    val maxSpeed by lazy { max(speedKmh) }
    val maxHeart by lazy { max(heart) }
    val maxPower by lazy { max(power) }
    val totalDistanceM by lazy { distanceM.lastOrNull { !it.isNaN() } ?: 0f }
    val minAltitude by lazy { altitude.filter { !it.isNaN() }.minOrNull() ?: 0f }
    val maxAltitude by lazy { max(altitude) }
    val climbM by lazy {
        var up = 0f; var last = Float.NaN
        // Su un'altitudine levigata, per non contare il rumore.
        smooth(altitude, 5).forEach { a -> if (!a.isNaN()) { if (!last.isNaN() && a > last) up += a - last; last = a } }
        up
    }

    /** FTP stimata: 95% della migliore potenza media su 20 minuti (o su quanto dura il giro, un po' meno). */
    val ftpAuto: Int by lazy {
        if (!hasPower) return@lazy 250
        val window = 20 * 60_000L
        var best = 0.0
        var j = 0; var sum = 0.0
        val dur = endMs - startMs
        val w = if (dur >= window) window else max(dur / 2, 60_000L)
        for (i in 0 until size) {
            sum += pw(i) * dt(i)
            while (timeMs[i] - timeMs[j] > w) { sum -= pw(j + 1) * dt(j + 1); j++ }
            val span = (timeMs[i] - timeMs[j]).toDouble()
            if (span >= w * 0.9) best = max(best, sum / span)
        }
        val k = if (dur >= window) 0.95 else 0.85
        (best * k).toInt().takeIf { it > 50 } ?: 250
    }
    val hrMaxAuto: Int by lazy { max(maxHeart.toInt(), 160) }

    private fun pw(i: Int) = if (i < size && !power[i].isNaN()) power[i].toDouble() else 0.0
    private fun dt(i: Int) = if (i in 1 until size) (timeMs[i] - timeMs[i - 1]).coerceIn(0, 10_000).toDouble() else 0.0

    /** Posizione nel tracciato dell'istante [t]: indice del punto prima e frazione verso il successivo. */
    fun locate(t: Long): Pair<Int, Float>? {
        if (size == 0 || t < startMs - 1_000 || t > endMs + 1_000) return null
        var lo = 0; var hi = size - 1
        while (lo < hi) { val m = (lo + hi + 1) / 2; if (timeMs[m] <= t) lo = m else hi = m - 1 }
        if (lo >= size - 1) return (size - 1) to 0f
        val span = (timeMs[lo + 1] - timeMs[lo]).coerceAtLeast(1)
        return lo to ((t - timeMs[lo]).toFloat() / span).coerceIn(0f, 1f)
    }

    fun sample(t: Long): TeleSample? {
        val (i, f) = locate(t) ?: return null
        val j = min(i + 1, size - 1)
        // In una pausa lunga della registrazione (oltre 30 s) non si inventa niente: si tiene il punto prima.
        val k = if (timeMs[j] - timeMs[i] > 30_000) 0f else f
        fun lf(a: FloatArray): Float {
            val x = a[i]; val y = a[j]
            return when { x.isNaN() -> if (k > 0.5f) y else Float.NaN; y.isNaN() -> x; else -> x + (y - x) * k }
        }
        fun ld(a: DoubleArray): Double {
            val x = a[i]; val y = a[j]
            return when { x.isNaN() -> y; y.isNaN() -> x; else -> x + (y - x) * k }
        }
        return TeleSample(t, ld(lat), ld(lon), lf(altitude), lf(speedKmh), lf(heart), lf(power), lf(cadence), lf(distanceM),
            lf(grade), lf(temp), ld(beats), ld(revs), i + k)
    }

    internal fun finish(): TeleTrack {
        for (i in 1 until size) {
            val d = (timeMs[i] - timeMs[i - 1]).coerceIn(0, 10_000) / 60_000.0
            beats[i] = beats[i - 1] + (if (heart[i].isNaN()) 0.0 else heart[i] * d)
            revs[i] = revs[i - 1] + (if (cadence[i].isNaN()) 0.0 else cadence[i] * d)
        }
        return this
    }

    companion object {
        fun smooth(a: FloatArray, r: Int): FloatArray = FloatArray(a.size) { i ->
            var s = 0f; var n = 0
            for (k in max(0, i - r)..min(a.size - 1, i + r)) if (!a[k].isNaN()) { s += a[k]; n++ }
            if (n == 0 || a[i].isNaN()) Float.NaN else s / n
        }
    }
}

/** Lettura dei file delle attività. */
object TeleParser {
    /** Secondi fra l'1/1/1970 e il 31/12/1989, l'inizio del tempo nei file FIT. */
    private const val FIT_EPOCH_S = 631065600L

    private class Point(
        val t: Long, var lat: Double = Double.NaN, var lon: Double = Double.NaN, var alt: Float = Float.NaN,
        var speed: Float = Float.NaN, var hr: Float = Float.NaN, var power: Float = Float.NaN, var cad: Float = Float.NaN,
        var dist: Float = Float.NaN, var grade: Float = Float.NaN, var temp: Float = Float.NaN,
    )

    /** Legge un file .fit, .gpx o .tcx (anche compresso .gz); null se non è un'attività. */
    fun parse(bytes: ByteArray): TeleTrack? {
        var b = bytes
        if (b.size > 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()) b = GZIPInputStream(ByteArrayInputStream(b)).readBytes()
        // Garmin Connect ("Esporta originale") dà uno .zip con dentro il .fit.
        if (b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2] == 3.toByte() && b[3] == 4.toByte()) {
            java.util.zip.ZipInputStream(ByteArrayInputStream(b)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name.lowercase()
                    if (!e.isDirectory && (n.endsWith(".fit") || n.endsWith(".gpx") || n.endsWith(".tcx") || n.endsWith(".gz"))) return parse(z.readBytes())
                    e = z.nextEntry
                }
            }
            return null
        }
        val pts = when {
            b.size > 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() && b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte() -> fit(b)
            else -> {
                val s = String(b, Charsets.UTF_8)
                when {
                    s.contains("<trkpt", ignoreCase = true) -> gpx(s)
                    s.contains("<Trackpoint", ignoreCase = true) -> tcx(s)
                    else -> emptyList()
                }
            }
        }
        return build(pts)
    }

    // ---- FIT (Garmin): messaggi "record" (numero globale 20) ----
    private fun fit(b: ByteArray): List<Point> {
        val headerSize = b[0].toInt() and 0xFF
        val dataSize = (b[4].toInt() and 0xFF) or ((b[5].toInt() and 0xFF) shl 8) or ((b[6].toInt() and 0xFF) shl 16) or ((b[7].toInt() and 0xFF) shl 24)
        val end = min(b.size, headerSize + dataSize)
        class Field(val num: Int, val size: Int, val base: Int)
        class Def(val global: Int, val big: Boolean, val fields: List<Field>, val devSize: Int)
        val defs = arrayOfNulls<Def>(16)
        val out = ArrayList<Point>()
        var lastTs = 0L
        var p = headerSize
        fun u(off: Int, size: Int, big: Boolean): Long {
            var v = 0L
            for (k in 0 until size) {
                val by = (b[off + (if (big) k else size - 1 - k)].toLong() and 0xFF)
                v = (v shl 8) or by
            }
            return v
        }
        try {
            while (p < end) {
                val h = b[p].toInt() and 0xFF; p++
                if (h and 0x80 != 0) {
                    // Intestazione compressa: tempo relativo all'ultimo.
                    val local = (h shr 5) and 0x3
                    val off = (h and 0x1F).toLong()
                    var ts = (lastTs and 0x1F.inv().toLong()) + off
                    if (off < (lastTs and 0x1F)) ts += 0x20
                    lastTs = ts
                    val d = defs[local] ?: return out
                    p = readData(b, p, d.global, d.big, d.fields.map { Triple(it.num, it.size, it.base) }, d.devSize, ts, out) { lastTs = it }
                } else if (h and 0x40 != 0) {
                    val local = h and 0x0F
                    val dev = h and 0x20 != 0
                    val big = b[p + 1].toInt() == 1
                    val global = u(p + 2, 2, big).toInt()
                    val n = b[p + 4].toInt() and 0xFF
                    p += 5
                    val fields = (0 until n).map { k -> Field(b[p + 3 * k].toInt() and 0xFF, b[p + 3 * k + 1].toInt() and 0xFF, b[p + 3 * k + 2].toInt() and 0xFF) }
                    p += 3 * n
                    var devSize = 0
                    if (dev) {
                        val nd = b[p].toInt() and 0xFF; p++
                        for (k in 0 until nd) devSize += b[p + 3 * k + 1].toInt() and 0xFF
                        p += 3 * nd
                    }
                    defs[local] = Def(global, big, fields, devSize)
                } else {
                    val d = defs[h and 0x0F] ?: return out
                    p = readData(b, p, d.global, d.big, d.fields.map { Triple(it.num, it.size, it.base) }, d.devSize, -1, out) { lastTs = it }
                }
            }
        } catch (_: IndexOutOfBoundsException) {
            // File troncato: si tiene quel che c'è.
        }
        return out
    }

    private fun readData(
        b: ByteArray, start: Int, global: Int, big: Boolean, fields: List<Triple<Int, Int, Int>>, devSize: Int, compressedTs: Long,
        out: MutableList<Point>, onTs: (Long) -> Unit,
    ): Int {
        var p = start
        val values = HashMap<Int, Long>()
        for ((num, size, base) in fields) {
            val bt = base and 0x1F
            val elem = when (bt) { 0, 1, 2, 7, 10, 13 -> 1; 3, 4, 11 -> 2; 5, 6, 8, 12 -> 4; else -> 8 }
            if (size == elem && elem <= 4) {
                var v = 0L
                for (k in 0 until size) v = (v shl 8) or (b[p + (if (big) k else size - 1 - k)].toLong() and 0xFF)
                val invalid = when (bt) {
                    1 -> v == 0x7FL; 2, 0 -> v == 0xFFL; 3 -> v == 0x7FFFL; 4 -> v == 0xFFFFL
                    5 -> v == 0x7FFFFFFFL; 6 -> v == 0xFFFFFFFFL; 10, 11, 12 -> v == 0L
                    else -> false
                }
                if (!invalid) {
                    // Interi con segno.
                    val sv = when (bt) { 1 -> v.toByte().toLong(); 3 -> v.toShort().toLong(); 5 -> v.toInt().toLong(); else -> v }
                    values[num] = sv
                }
            }
            p += size
        }
        p += devSize
        values[253]?.let { onTs(it) }
        if (global == 20) {
            val ts = values[253] ?: compressedTs
            if (ts > 0) {
                val pt = Point((ts + FIT_EPOCH_S) * 1000)
                values[0]?.let { pt.lat = it * (180.0 / 2147483648.0) }
                values[1]?.let { pt.lon = it * (180.0 / 2147483648.0) }
                (values[78] ?: values[2])?.let { pt.alt = it / 5f - 500f }
                values[3]?.let { pt.hr = it.toFloat() }
                values[4]?.let { pt.cad = it.toFloat() }
                values[5]?.let { pt.dist = it / 100f }
                (values[73] ?: values[6])?.let { pt.speed = it / 1000f * 3.6f }
                values[7]?.let { pt.power = it.toFloat() }
                values[9]?.let { pt.grade = it / 100f }
                values[13]?.let { pt.temp = it.toFloat() }
                if (pt.lat.isNaN() || pt.lon.isNaN()) { pt.lat = Double.NaN; pt.lon = Double.NaN }
                out += pt
            }
        }
        return p
    }

    // ---- GPX e TCX (Strava, Komoot, Wahoo, …) ----
    private fun num(block: String, tag: String): Float? =
        Regex("<(?:[\\w-]+:)?$tag>\\s*(-?[\\d.]+)\\s*<", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.toFloatOrNull()

    private fun time(s: String?): Long? {
        if (s == null) return null
        return runCatching { java.time.OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(s.trim()).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(s.trim()).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun gpx(s: String): List<Point> = Regex("<trkpt\\b([^>]*)>(.*?)</trkpt>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .findAll(s).mapNotNull { m ->
            val attrs = m.groupValues[1]; val body = m.groupValues[2]
            val t = time(Regex("<time>([^<]+)</time>").find(body)?.groupValues?.get(1)) ?: return@mapNotNull null
            Point(t).apply {
                lat = Regex("lat=\"(-?[\\d.]+)\"").find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: Double.NaN
                lon = Regex("lon=\"(-?[\\d.]+)\"").find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: Double.NaN
                alt = num(body, "ele") ?: Float.NaN
                hr = num(body, "hr") ?: num(body, "heartrate") ?: Float.NaN
                cad = num(body, "cad") ?: num(body, "cadence") ?: Float.NaN
                power = num(body, "power") ?: num(body, "PowerInWatts") ?: Float.NaN
                temp = num(body, "atemp") ?: num(body, "temp") ?: Float.NaN
                speed = num(body, "speed")?.let { it * 3.6f } ?: Float.NaN
            }
        }.toList()

    private fun tcx(s: String): List<Point> = Regex("<Trackpoint>(.*?)</Trackpoint>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .findAll(s).mapNotNull { m ->
            val body = m.groupValues[1]
            val t = time(Regex("<Time>([^<]+)</Time>").find(body)?.groupValues?.get(1)) ?: return@mapNotNull null
            Point(t).apply {
                lat = num(body, "LatitudeDegrees")?.toDouble() ?: Double.NaN
                lon = num(body, "LongitudeDegrees")?.toDouble() ?: Double.NaN
                alt = num(body, "AltitudeMeters") ?: Float.NaN
                dist = num(body, "DistanceMeters") ?: Float.NaN
                hr = Regex("<HeartRateBpm[^>]*>\\s*<Value>(\\d+)</Value>").find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: Float.NaN
                cad = num(body, "Cadence") ?: num(body, "RunCadence") ?: Float.NaN
                speed = num(body, "Speed")?.let { it * 3.6f } ?: Float.NaN
                power = num(body, "Watts") ?: Float.NaN
            }
        }.toList()

    /** Metri fra due punti. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Ordina, completa (distanza, velocità, pendenza dove mancano) e leviga. */
    private fun build(raw: List<Point>): TeleTrack? {
        val pts = raw.sortedBy { it.t }.fold(ArrayList<Point>()) { acc, p -> if (acc.isEmpty() || p.t > acc.last().t) acc += p; acc }
        if (pts.size < 2) return null
        val n = pts.size
        // Distanza: quella del file, altrimenti dal GPS.
        if (pts.none { !it.dist.isNaN() }) {
            var d = 0.0; var last: Point? = null
            pts.forEach { p ->
                val l = last
                if (!p.lat.isNaN() && l != null) d += haversine(l.lat, l.lon, p.lat, p.lon)
                if (!p.lat.isNaN()) last = p
                p.dist = d.toFloat()
            }
        } else {
            var last = 0f
            pts.forEach { p -> if (p.dist.isNaN()) p.dist = last else last = p.dist }
        }
        // Velocità: dove manca, dalla distanza percorsa in ±2 s.
        if (pts.none { !it.speed.isNaN() }) {
            for (i in 0 until n) {
                var a = i; var b = i
                while (a > 0 && pts[i].t - pts[a].t < 2_000) a--
                while (b < n - 1 && pts[b].t - pts[i].t < 2_000) b++
                val dt = (pts[b].t - pts[a].t) / 1000f
                pts[i].speed = if (dt > 0) ((pts[b].dist - pts[a].dist) / dt * 3.6f).coerceIn(0f, 150f) else 0f
            }
        }
        val alt = FloatArray(n) { pts[it].alt }
        val track = TeleTrack(
            LongArray(n) { pts[it].t }, DoubleArray(n) { pts[it].lat }, DoubleArray(n) { pts[it].lon },
            TeleTrack.smooth(alt, 2), TeleTrack.smooth(FloatArray(n) { pts[it].speed }, 1), FloatArray(n) { pts[it].hr },
            FloatArray(n) { pts[it].power }, FloatArray(n) { pts[it].cad }, FloatArray(n) { pts[it].dist },
            FloatArray(n) { pts[it].grade }, FloatArray(n) { pts[it].temp },
        )
        // Pendenza: dislivello su ~40 m di strada (non punto per punto, sarebbe solo rumore).
        if (track.grade.none { !it.isNaN() } && track.hasAltitude) {
            val g = FloatArray(n) { Float.NaN }
            for (i in 0 until n) {
                var a = i; var b = i
                while (a > 0 && track.distanceM[i] - track.distanceM[a] < 20f) a--
                while (b < n - 1 && track.distanceM[b] - track.distanceM[i] < 20f) b++
                val dd = track.distanceM[b] - track.distanceM[a]
                if (dd > 10f && !track.altitude[a].isNaN() && !track.altitude[b].isNaN()) g[i] = ((track.altitude[b] - track.altitude[a]) / dd * 100f).coerceIn(-35f, 35f)
            }
            TeleTrack.smooth(g, 2).copyInto(track.grade)
        }
        return track.finish()
    }
}

/** Abbinamento fra i video e l'attività. */
object TeleSync {
    /** Ora di inizio dal file video ("20261101T101530.000Z"); null se non c'è. */
    fun parseVideoDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return runCatching {
            val f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss[.SSS]'Z'")
            java.time.LocalDateTime.parse(t, f).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()?.takeIf { it > 946_684_800_000L } // prima del 2000 = data mancante (1904 o 1970)
    }

    /** Posizione registrata nel video ("+45.1234+009.5678/"). */
    fun parseVideoLocation(s: String?): Pair<Double, Double>? {
        val m = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(s ?: return null) ?: return null
        val la = m.groupValues[1].toDoubleOrNull() ?: return null
        val lo = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (la == 0.0 && lo == 0.0) null else la to lo
    }

    /**
     * Ora vera del primo fotogramma di un video lungo [durationMs], partendo da quella scritta nel file ([dateMs]):
     * - se il telefono ha scritto l'ora locale come se fosse UTC, si corregge di ore intere per cadere dentro l'attività;
     * - se il video ha la posizione, si cerca il punto del giro più vicino (entro 10 minuti) e si usa la sua ora.
     */
    fun refineStart(track: TeleTrack, dateMs: Long, durationMs: Long, location: Pair<Double, Double>?): Long {
        var start = dateMs
        fun inside(s: Long) = s + durationMs >= track.startMs && s <= track.endMs
        if (!inside(start)) {
            (1..14).flatMap { listOf(-it, it) }.map { start + it * 3_600_000L }.firstOrNull { inside(it) }?.let { start = it }
        }
        if (location != null && track.hasGps) {
            var best = Double.MAX_VALUE; var bestT = -1L
            for (i in 0 until track.size) {
                val t = track.timeMs[i]
                if (t < start - 600_000 || t > start + durationMs + 600_000 || track.lat[i].isNaN()) continue
                val d = TeleParser.haversine(location.first, location.second, track.lat[i], track.lon[i])
                if (d < best) { best = d; bestT = t }
            }
            // Solo se il punto è davvero lì e se ci si muoveva (da fermi la posizione non dice l'ora).
            if (best < 35 && bestT >= 0) {
                val s = track.sample(bestT)
                if (s != null && !s.speedKmh.isNaN() && s.speedKmh > 4f) start = bestT
            }
        }
        return start
    }

    /** Il video e il suo tempo nel file per l'istante [timelineUs] del montaggio (tiene conto di tagli, velocità e clip al contrario). */
    fun sourceAt(clips: List<Clip>, timelineUs: Long): Pair<String, Long>? {
        var acc = 0L
        clips.forEachIndexed { i, c ->
            val out = c.outputUs
            if (timelineUs < acc + out || i == clips.lastIndex) {
                if (c.isPhoto || c.baked) return null
                val off = (timelineUs - acc).coerceIn(0, out)
                val fileUs = c.trimStartUs + c.speedCurve().sourceTimeAt(off)
                val r = c.reversed
                return if (r != null) r.uri to (r.trimEndUs - fileUs).coerceAtLeast(0)
                else c.uri to fileUs
            }
            acc += out
        }
        return null
    }

    /** Ora dell'attività (ms UTC) mostrata all'istante [timelineUs]; null dove non c'è un video abbinato. */
    fun epochAt(t: Telemetry, clips: List<Clip>, timelineUs: Long): Long? {
        val (uri, fileUs) = sourceAt(clips, timelineUs) ?: return null
        val start = t.clipStartMs[uri] ?: return null
        return start + fileUs / 1000 + t.offsetMs
    }

    /** Quanti video cadono dentro l'attività. */
    fun covered(t: Telemetry, track: TeleTrack, clips: List<Clip>): Int = clips.filter { !it.isPhoto }.count { c ->
        val s = t.clipStartMs[c.reversed?.uri ?: c.uri] ?: return@count false
        val a = s + c.trimStartUs / 1000 + t.offsetMs
        val b = s + c.trimEndUs / 1000 + t.offsetMs
        b >= track.startMs && a <= track.endMs
    }
}

/** Zone (1…5) di battito e potenza. */
object TeleZones {
    fun heart(bpm: Float, max: Int): Int = when (bpm / max.coerceAtLeast(1)) {
        in 0f..0.6f -> 1; in 0.6f..0.7f -> 2; in 0.7f..0.8f -> 3; in 0.8f..0.9f -> 4; else -> 5
    }
    fun power(w: Float, ftp: Int): Int = when (w / ftp.coerceAtLeast(1)) {
        in 0f..0.55f -> 1; in 0.55f..0.75f -> 2; in 0.75f..0.9f -> 3; in 0.9f..1.05f -> 4; else -> 5
    }
    val heartNames = listOf("", "Recupero", "Fondo", "Medio", "Soglia", "Massimo")
    val powerNames = listOf("", "Recupero", "Fondo", "Tempo", "Soglia", "VO2max")
    /** Colori delle zone, dal blu al rosso. */
    val colors = listOf(0xFF9AA4B2.toInt(), 0xFF4DA3FF.toInt(), 0xFF2EE59D.toInt(), 0xFFFFD43D.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF3D5A.toInt())
}


/** Misure e posizioni degli indicatori (in pixel del quadro), uguali in anteprima, nel video esportato e nelle prove. */
object TeleLayoutCalc {
    /** Unità di misura: 1% del lato corto del quadro, per la grandezza scelta. */
    fun unit(frameW: Int, frameH: Int, t: Telemetry, item: TeleItem): Float = min(frameW, frameH) * 0.01f * t.size * item.scale

    /** Larghezza e altezza di un indicatore, in unità. */
    fun sizeU(w: TeleWidget, style: TeleStyle): Pair<Float, Float> = when (w) {
        TeleWidget.SPEED -> if (style == TeleStyle.RACE) 44f to 15f else 30f to 30f
        TeleWidget.ALTITUDE -> 50f to 15f
        TeleWidget.MAP -> 26f to 26f
        TeleWidget.TEMP -> 19f to 11f
        TeleWidget.DISTANCE -> 30f to 11f
        TeleWidget.TIME -> 36f to 11f
        else -> 31f to 12.5f
    }

    fun sizePx(w: TeleWidget, t: Telemetry, item: TeleItem, frameW: Int, frameH: Int): Pair<Int, Int> {
        val u = unit(frameW, frameH, t, item)
        val (a, b) = sizeU(w, t.style)
        return (a * u).toInt().coerceAtLeast(8) to (b * u).toInt().coerceAtLeast(8)
    }

    /** Margini: nei video verticali con la zona sicura si lascia libero quello che coprono i pulsanti dei social. */
    fun margins(frameW: Int, frameH: Int, safe: Boolean): IntArray {
        val m = (min(frameW, frameH) * 0.04f).toInt()
        return if (safe && frameH > frameW) intArrayOf(m, (frameH * 0.09f).toInt(), (frameW * 0.13f).toInt(), (frameH * 0.2f).toInt())
        else intArrayOf(m, m, m, m)
    }

    /**
     * Angolo in alto a sinistra di ogni indicatore: quelli nello stesso posto si impilano, lontano dal bordo.
     * Se due si toccano (per esempio uno al centro in alto fra due angoli larghi), quello al centro o a metà altezza
     * scivola su o giù quel tanto che basta.
     */
    fun positions(t: Telemetry, frameW: Int, frameH: Int): List<Pair<Int, Int>> {
        val (l, top, r, bottom) = margins(frameW, frameH, t.safeZone).let { listOf(it[0], it[1], it[2], it[3]) }
        val sizes = t.items.map { sizePx(it.widget, t, it, frameW, frameH) }
        val gap = (min(frameW, frameH) * 0.012f).toInt()
        val out = MutableList(t.items.size) { 0 to 0 }
        val placed = ArrayList<IntArray>()
        // Prima gli angoli, poi il centro, poi la metà altezza: questi ultimi si spostano se serve.
        val order = t.items.indices.groupBy { t.items[it].slot }.entries.sortedBy { (slot, _) -> (if (slot.col == 1) 1 else 0) + (if (slot.row == 1) 2 else 0) }
        order.forEach { (slot, idx) ->
            val total = idx.sumOf { sizes[it].second } + gap * (idx.size - 1)
            var y0 = when (slot.row) { 0 -> top; 1 -> (frameH - total) / 2; else -> frameH - bottom - total }
            val x0 = idx.map { i -> when (slot.col) { 0 -> l; 1 -> (frameW - sizes[i].first) / 2; else -> frameW - r - sizes[i].first } }
            fun rects(y: Int): List<IntArray> {
                var yy = y
                return idx.mapIndexed { k, i -> intArrayOf(x0[k], yy, x0[k] + sizes[i].first, yy + sizes[i].second).also { yy += sizes[i].second + gap } }
            }
            fun ok(y: Int) = y >= 0 && y + total <= frameH && rects(y).none { a -> placed.any { b -> a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3] } }
            if (!ok(y0)) {
                val candidates = placed.flatMap { b -> listOf(b[3] + gap, b[1] - gap - total) }
                candidates.filter { ok(it) }.minByOrNull { kotlin.math.abs(it - y0) }?.let { y0 = it }
            }
            rects(y0).forEachIndexed { k, rc -> out[idx[k]] = rc[0] to rc[1]; placed += rc }
        }
        return out
    }
}
