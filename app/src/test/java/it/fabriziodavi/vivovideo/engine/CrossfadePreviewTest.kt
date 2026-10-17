package it.fabriziodavi.vivovideo.engine

import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.Project
import it.fabriziodavi.vivovideo.model.TransitionSpec
import it.fabriziodavi.vivovideo.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CrossfadePreviewTest {
    @Test fun jobsCutHalvesAroundTheCut() {
        val x = TransitionSpec(TransitionType.CROSSFADE, 1_000_000)
        val a = Clip(uri = "a", sourceDurationUs = 10_000_000, trimStartUs = 1_000_000, trimEndUs = 5_000_000, transitionOut = x)
        val b = Clip(uri = "b", sourceDurationUs = 10_000_000, trimStartUs = 2_000_000, trimEndUs = 6_000_000, transitionOut = x)
        val c = Clip(uri = "c", sourceDurationUs = 10_000_000, trimStartUs = 2_000_000, trimEndUs = 6_000_000)
        val p = Project(name = "t", clips = listOf(a, b, c))
        val jobs = CrossfadePreview.jobs(p, 1080, 1920)
        assertEquals(2, jobs.size)
        jobs.forEach { j ->
            assertEquals(2, j.sub.clips.size)
            assertTrue(abs(j.sub.clips[0].outputUs - 500_000) < 5_000)
            assertTrue(abs(j.sub.clips[1].outputUs - 500_000) < 5_000)
            // La mezza clip prima del taglio finisce dove finiva la clip (stessa coda per la dissolvenza).
            assertEquals(p.clips[j.original!!.index].trimEndUs, j.sub.clips[0].trimEndUs)
            assertEquals(p.clips[j.original!!.index + 1].trimStartUs, j.sub.clips[1].trimStartUs)
        }
        // Chiavi diverse per tagli diversi, uguali se nulla cambia.
        assertTrue(jobs[0].key != jobs[1].key)
        assertEquals(jobs.map { it.key }, CrossfadePreview.jobs(p, 1080, 1920).map { it.key })
    }
}
