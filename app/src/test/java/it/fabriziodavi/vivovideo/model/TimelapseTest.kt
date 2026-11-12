package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conti del time-lapse e passaggio avanti e indietro dall'originale. */
class TimelapseTest {
    @Test fun planSamplesTheRightFrames() {
        // Un minuto a 30 fps, 20×: 3 secondi, 90 fotogrammi; ognuno copre 20 fotogrammi dell'originale.
        val p = TimelapsePlan(60_000_000, 20, 30f, TimelapseTrails.LONG)
        assertEquals(3_000_000, p.outputUs)
        assertEquals(90, p.outFrames)
        assertEquals(666_666.0, p.spanUs, 1.0)
        assertEquals(8, p.samples)
        // I campioni a passo stepUs cadono 8 per gruppo.
        val groups = (0 until 80).map { p.groupOf(it * p.stepUs) }
        assertEquals((0 until 10).flatMap { g -> List(8) { g } }, groups)
        // Senza scie: un fotogramma per gruppo.
        val n = TimelapsePlan(60_000_000, 20, 30f, TimelapseTrails.NONE)
        assertEquals(1, n.samples)
        assertEquals((0 until 5).toList(), (0 until 5).map { n.groupOf(it * n.stepUs) })
        // A 5× in un video a 30 fps ci sono solo 5 fotogrammi per intervallo: le scie lunghe ne usano 5.
        assertEquals(5, TimelapsePlan(10_000_000, 5, 30f, TimelapseTrails.LONG).samples)
        assertTrue(TimelapsePlan.SPEEDS.containsAll(listOf(5, 10, 20, 50, 100)))
    }

    @Test fun timelapseAndBack() {
        val c = Clip(uri = "content://v/1", sourceDurationUs = 90_000_000, trimStartUs = 10_000_000, trimEndUs = 70_000_000, width = 3840, height = 2160, fps = 60f,
            speed = ClipSpeed.FAST, keyframes = listOf(Keyframe(1_000_000, Transform())))
        val t = ClipTools.timelapsed(c, "file:///tl.mp4", 3_000_000, 1920, 1080, 20, TimelapseTrails.LONG, ambient = false, moving = true)
        assertEquals("file:///tl.mp4", t.uri)
        assertEquals(3_000_000, t.outputUs)
        assertEquals(ClipSpeed.NORMAL, t.speed)
        assertTrue(t.muted && t.keyframes.isEmpty())
        assertEquals(20, t.timelapse!!.speed)
        // Rifatto a 50×: l'originale resta quello vero, non il time-lapse di prima.
        val t2 = ClipTools.timelapsed(t, "file:///tl2.mp4", 1_200_000, 1920, 1080, 50, TimelapseTrails.NONE, ambient = true, moving = false)
        assertEquals("content://v/1", t2.timelapse!!.source.uri)
        assertTrue(!t2.muted)
        val back = ClipTools.untimelapsed(t2)
        assertEquals(c.uri, back.uri)
        assertEquals(c.trimStartUs, back.trimStartUs)
        assertEquals(c.trimEndUs, back.trimEndUs)
        assertEquals(null, back.timelapse)
    }
}
