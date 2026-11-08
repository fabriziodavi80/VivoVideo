package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Dati della pedalata: lettura dei file (FIT, GPX, TCX), campioni, abbinamento ai video e posizione degli indicatori. */
class TelemetryTest {
    /** 1/11/2026 10:00:00 UTC. */
    private val t0 = 1_793_527_200_000L
    private val fitEpoch = 631065600L

    /** Un piccolo file FIT: 60 punti, uno al secondo, a 36 km/h verso nord, in salita, con battito, potenza e cadenza. */
    private fun fit(compressedAt: Int = -1): ByteArray {
        val data = ByteArrayOutputStream()
        fun u8(v: Int) = data.write(v and 0xFF)
        fun u16(v: Int) { u8(v); u8(v shr 8) }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        // Definizione: messaggio locale 0 = record (20), little endian.
        u8(0x40); u8(0); u8(0); u16(20)
        val fields = listOf(intArrayOf(253, 4, 0x86), intArrayOf(0, 4, 0x85), intArrayOf(1, 4, 0x85), intArrayOf(2, 2, 0x84),
            intArrayOf(3, 1, 0x02), intArrayOf(4, 1, 0x02), intArrayOf(6, 2, 0x84), intArrayOf(7, 2, 0x84))
        u8(fields.size); fields.forEach { f -> u8(f[0]); u8(f[1]); u8(f[2]) }
        // Seconda definizione senza il tempo (per l'intestazione compressa): messaggio locale 1.
        u8(0x41); u8(0); u8(0); u16(20)
        val noTs = fields.drop(1)
        u8(noTs.size); noTs.forEach { f -> u8(f[0]); u8(f[1]); u8(f[2]) }
        val semi = 2147483648.0 / 180.0
        for (i in 0 until 60) {
            val ts = t0 / 1000 - fitEpoch + i
            val compressed = i == compressedAt
            if (compressed) u8(0x80 or (1 shl 5) or (ts and 0x1F).toInt()) else { u8(0x00); u32(ts) }
            val lat = 45.0 + i * 10.0 / 111_320.0 // 10 m al secondo verso nord
            u32((lat * semi).toLong() and 0xFFFFFFFFL); u32((9.0 * semi).toLong() and 0xFFFFFFFFL)
            u16(((300.0 + i * 0.5 + 500) * 5).toInt())
            u8(140 + i / 2); u8(85); u16(10_000); u16(if (i == 30) 0xFFFF else 250 + i)
        }
        val body = data.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(14, 0x20, 0x08, 0x08))
        val n = body.size
        out.write(byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte()))
        out.write(".FIT".toByteArray()); out.write(byteArrayOf(0, 0))
        out.write(body); out.write(byteArrayOf(0, 0))
        return out.toByteArray()
    }

    @Test fun readsFitRecords() {
        val t = TeleParser.parse(fit())!!
        assertEquals(60, t.size)
        assertEquals(t0, t.startMs)
        assertEquals(t0 + 59_000, t.endMs)
        assertEquals(36f, t.speedKmh[10], 0.01f)
        assertEquals(145f, t.heart[10], 0.01f)
        assertEquals(85f, t.cadence[10], 0.01f)
        assertEquals(260f, t.power[10], 0.01f)
        assertTrue("potenza non valida = assente", t.power[30].isNaN())
        assertEquals(45.0, t.lat[0], 1e-6)
        assertEquals(9.0, t.lon[0], 1e-6)
        assertTrue(t.hasGps && t.hasHeart && t.hasPower && t.hasCadence && t.hasAltitude && !t.hasTemp)
        // Distanza dal GPS (il file non la ha): ~10 m al secondo.
        assertEquals(590f, t.totalDistanceM, 5f)
        // Pendenza calcolata: 0,5 m ogni 10 m = 5%.
        assertEquals(5f, t.grade[30], 0.6f)
        assertTrue(t.climbM in 25f..32f)
    }

    @Test fun fitCompressedTimestampAndGzip() {
        val t = TeleParser.parse(fit(compressedAt = 5))!!
        assertEquals(60, t.size)
        assertEquals(t0 + 5_000, t.timeMs[5])
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(fit()) } }.toByteArray()
        assertEquals(60, TeleParser.parse(gz)!!.size)
        // Lo .zip di Garmin Connect con dentro il .fit.
        val zip = ByteArrayOutputStream().also { o ->
            java.util.zip.ZipOutputStream(o).use { z -> z.putNextEntry(java.util.zip.ZipEntry("12345_ACTIVITY.fit")); z.write(fit()); z.closeEntry() }
        }.toByteArray()
        assertEquals(60, TeleParser.parse(zip)!!.size)
        assertNull(TeleParser.parse("non è un'attività".toByteArray()))
    }

    @Test fun readsGpxWithExtensions() {
        val pts = (0 until 30).joinToString("") { i ->
            val time = java.time.Instant.ofEpochMilli(t0 + i * 1000L).toString()
            """<trkpt lat="${45 + i * 0.0001}" lon="9.0"><ele>${200 + i}</ele><time>$time</time><extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>${120 + i}</gpxtpx:hr><gpxtpx:cad>90</gpxtpx:cad></gpxtpx:TrackPointExtension><power>300</power></extensions></trkpt>"""
        }
        val gpx = """<?xml version="1.0"?><gpx><trk><trkseg>$pts</trkseg></trk></gpx>"""
        val t = TeleParser.parse(gpx.toByteArray())!!
        assertEquals(30, t.size)
        assertEquals(125f, t.heart[5], 0.01f)
        assertEquals(90f, t.cadence[5], 0.01f)
        assertEquals(300f, t.power[5], 0.01f)
        // Velocità dal GPS: 0,0001° ≈ 11,1 m al secondo ≈ 40 km/h.
        assertEquals(40f, t.speedKmh[15], 1.5f)
        assertTrue(t.grade[15] > 5f)
    }

    @Test fun readsTcx() {
        val pts = (0 until 10).joinToString("") { i ->
            val time = java.time.Instant.ofEpochMilli(t0 + i * 1000L).toString()
            "<Trackpoint><Time>$time</Time><Position><LatitudeDegrees>45.0</LatitudeDegrees><LongitudeDegrees>${9 + i * 0.0001}</LongitudeDegrees></Position>" +
                "<AltitudeMeters>100</AltitudeMeters><DistanceMeters>${i * 8}</DistanceMeters><HeartRateBpm><Value>150</Value></HeartRateBpm>" +
                "<Cadence>80</Cadence><Extensions><ns3:TPX><ns3:Speed>8</ns3:Speed><ns3:Watts>210</ns3:Watts></ns3:TPX></Extensions></Trackpoint>"
        }
        val t = TeleParser.parse("<TrainingCenterDatabase>$pts</TrainingCenterDatabase>".toByteArray())!!
        assertEquals(10, t.size)
        assertEquals(28.8f, t.speedKmh[5], 0.5f)
        assertEquals(210f, t.power[5], 0.01f)
        assertEquals(72f, t.totalDistanceM, 0.01f)
    }

    @Test fun samplesInterpolateAndCountBeats() {
        val t = TeleParser.parse(fit())!!
        val s = t.sample(t0 + 10_500)!!
        assertEquals(145.5f, s.heart, 0.6f)
        assertEquals(10.5f, s.index, 0.01f)
        assertNull(t.sample(t0 - 60_000))
        assertNull(t.sample(t0 + 120_000))
        // Battiti: ~145 al minuto → in 10 s circa 24.
        val a = t.sample(t0 + 10_000)!!.beats; val b = t.sample(t0 + 20_000)!!.beats
        assertEquals(24.5, b - a, 1.5)
        // Giri di pedivella: 85 al minuto → in 12 s 17 giri.
        assertEquals(17.0, t.sample(t0 + 24_000)!!.revs - t.sample(t0 + 12_000)!!.revs, 0.2)
        // FTP stimata su un giro breve e frequenza massima.
        assertTrue(t.ftpAuto in 200..300)
        assertEquals(169, t.hrMaxAuto)
    }

    @Test fun videoDateAndLocation() {
        assertEquals(t0, TeleSync.parseVideoDate("20261101T100000.000Z"))
        assertEquals(t0 + 5_000, TeleSync.parseVideoDate("20261101T100005Z"))
        assertNull(TeleSync.parseVideoDate("19040101T000000.000Z"))
        assertNull(TeleSync.parseVideoDate(null))
        assertEquals(45.0005 to 9.0, TeleSync.parseVideoLocation("+45.0005+009.0000/"))
        assertNull(TeleSync.parseVideoLocation("+00.0000+000.0000/"))
    }

    @Test fun refineStartFixesTimeZoneAndUsesPosition() {
        val t = TeleParser.parse(fit())!!
        // Ora locale scritta come UTC (un'ora avanti): si riporta dentro l'attività.
        assertEquals(t0 + 10_000, TeleSync.refineStart(t, t0 + 10_000 + 3_600_000, 5_000, null))
        // Con la posizione: il punto a 200 m dalla partenza (20 s), anche se l'ora del file dice 25 s.
        val loc = (45.0 + 200.0 / 111_320.0) to 9.0
        assertEquals(t0 + 20_000, TeleSync.refineStart(t, t0 + 25_000, 5_000, loc))
        // Posizione lontana dal giro: resta l'ora del file.
        assertEquals(t0 + 25_000, TeleSync.refineStart(t, t0 + 25_000, 5_000, 46.0 to 9.0))
    }

    @Test fun timelineMapsThroughTrimSpeedAndOrder() {
        val a = Clip(id = "a", uri = "content://a", sourceDurationUs = 10_000_000, trimStartUs = 2_000_000, trimEndUs = 6_000_000)
        val b = Clip(id = "b", uri = "content://b", sourceDurationUs = 10_000_000, speed = ClipSpeed.entries.first { it != ClipSpeed.NORMAL && it != ClipSpeed.RAMP })
        val photo = Clip(id = "p", uri = "content://p", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true)
        val tel = Telemetry(file = "x", name = "giro", clipStartMs = mapOf("content://a" to t0, "content://b" to t0 + 30_000), offsetMs = 500)
        val clips = listOf(a, photo, b)
        // 1 s nel montaggio = 3 s nel file di a.
        assertEquals(t0 + 3_000 + 500, TeleSync.epochAt(tel, clips, 1_000_000))
        // Sulla foto niente dati.
        assertNull(TeleSync.epochAt(tel, clips, 5_000_000))
        // In b la velocità cambia il tempo del file.
        val speed = b.speedCurve().speedAt(0)
        val e = TeleSync.epochAt(tel, clips, 7_000_000 + 1_000_000)!!
        assertTrue(kotlin.math.abs(t0 + 30_000 + (1_000 * speed).toLong() + 500 - e) <= 2)
        // Video senza ora: niente dati.
        assertNull(TeleSync.epochAt(tel.copy(clipStartMs = emptyMap()), clips, 1_000_000))
        val track = TeleParser.parse(fit())!!
        assertEquals(2, TeleSync.covered(tel, track, clips))
        assertEquals(1, TeleSync.covered(tel.copy(clipStartMs = mapOf("content://a" to t0, "content://b" to t0 + 3_600_000)), track, clips))
    }

    @Test fun reversedClipReadsTheOriginalTimes() {
        val r = Clip(id = "r", uri = "content://rev", sourceDurationUs = 4_000_000,
            reversed = ReversedSource("content://orig", 10_000_000, 2_000_000, 6_000_000, 1920, 1080))
        val tel = Telemetry(file = "x", name = "giro", clipStartMs = mapOf("content://orig" to t0))
        // Inizio della clip al contrario = fine del tratto originale.
        assertEquals(t0 + 6_000, TeleSync.epochAt(tel, listOf(r), 0))
        assertEquals(t0 + 5_000, TeleSync.epochAt(tel, listOf(r), 1_000_000))
    }

    @Test fun zones() {
        assertEquals(1, TeleZones.heart(100f, 180)); assertEquals(4, TeleZones.heart(155f, 180)); assertEquals(5, TeleZones.heart(175f, 180))
        assertEquals(2, TeleZones.power(150f, 250)); assertEquals(4, TeleZones.power(250f, 250)); assertEquals(5, TeleZones.power(400f, 250))
        assertEquals(6, TeleZones.colors.size)
    }

    @Test fun widgetsStayInsideTheFrameAndDoNotOverlap() {
        for ((w, h) in listOf(1080 to 1920, 1920 to 1080, 2160 to 3840, 1080 to 1080)) {
            for (layout in TeleLayout.entries) for (style in TeleStyle.entries) {
                val t = Telemetry(file = "x", name = "g", items = layout.items, style = style)
                val pos = TeleLayoutCalc.positions(t, w, h)
                val rects = t.items.mapIndexed { i, it ->
                    val (sw, sh) = TeleLayoutCalc.sizePx(it.widget, t, it, w, h)
                    intArrayOf(pos[i].first, pos[i].second, pos[i].first + sw, pos[i].second + sh)
                }
                rects.forEach { r -> assertTrue("$layout $style ${w}x$h fuori: ${r.toList()}", r[0] >= 0 && r[1] >= 0 && r[2] <= w && r[3] <= h) }
                for (i in rects.indices) for (j in i + 1 until rects.size) {
                    val a = rects[i]; val b = rects[j]
                    assertFalse("$layout $style ${w}x$h sovrapposti $i $j", a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3])
                }
            }
        }
        // Zona sicura nei verticali: in basso resta libero il 20% (pulsanti di Instagram e TikTok).
        val t = Telemetry(file = "x", name = "g", items = listOf(TeleItem(TeleWidget.SPEED, TeleSlot.BOTTOM_LEFT)))
        val y = TeleLayoutCalc.positions(t, 1080, 1920)[0].second + TeleLayoutCalc.sizePx(TeleWidget.SPEED, t, t.items[0], 1080, 1920).second
        assertTrue(y <= 1920 * 0.8f + 1)
        assertTrue(TeleLayoutCalc.positions(t.copy(safeZone = false), 1080, 1920)[0].second > TeleLayoutCalc.positions(t, 1080, 1920)[0].second)
    }

    @Test fun projectKeepsTelemetryInJson() {
        val p = Project(name = "giro", telemetry = Telemetry(file = "/x.dat", name = "Giro.fit", clipStartMs = mapOf("u" to t0), offsetMs = -1200,
            items = listOf(TeleItem(TeleWidget.MAP, TeleSlot.TOP_RIGHT, 1.3f)), style = TeleStyle.HUD, ftp = 280))
        val back = Project.fromJson(p.toJson())
        assertEquals(p.telemetry, back.telemetry)
        assertNull(back.telemetryClips)
        assertNotNull(Project.fromJson(Project(name = "vecchio").toJson()).let { it.telemetry == null })
    }
}
