package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SpeechCutsTest {
    /** Audio finto a 16 kHz: [parti] = (secondi, parla?) con un filo di rumore di fondo. */
    private fun pcm(vararg parts: Pair<Double, Boolean>): FloatArray {
        val out = ArrayList<Float>()
        var seed = 1
        for ((sec, talk) in parts) {
            val n = (sec * 16_000).toInt()
            repeat(n) { i ->
                seed = seed * 1103515245 + 12345
                val noise = ((seed ushr 16) and 0x7fff) / 32768f - 0.5f
                out += if (talk) (0.4 * sin(i * 0.12) * (0.6 + 0.4 * sin(i * 0.0007))).toFloat() + noise * 0.002f else noise * 0.002f
            }
        }
        return out.toFloatArray()
    }

    @Test fun findsLongPausesAndKeepsBreath() {
        // 2 s parlato, 1,5 s silenzio, 2 s parlato, 0,3 s silenzio, 2 s parlato.
        val a = pcm(2.0 to true, 1.5 to false, 2.0 to true, 0.3 to false, 2.0 to true)
        val s = SpeechCuts.silences(a, minUs = 700_000)
        assertEquals("solo la pausa lunga: $s", 1, s.size)
        // Resta il respiro ai due lati (0,15 s): si toglie circa 1,2 s.
        assertTrue("inizio ${s[0].startUs}", abs(s[0].startUs - 2_150_000) < 60_000)
        assertTrue("fine ${s[0].endUs}", abs(s[0].endUs - 3_350_000) < 60_000)
        // Con la soglia più bassa si prende anche la pausa breve? 0,3 s no (meno di 0,4).
        assertEquals(1, SpeechCuts.silences(a, minUs = 400_000).size)
        // Tutto silenzio (nessun parlato riconoscibile): non si taglia nulla.
        assertTrue(SpeechCuts.silences(pcm(5.0 to false), minUs = 400_000).isEmpty())
    }

    @Test fun recognisesFillersButNotTheWordE() {
        // "M": così il riconoscimento vocale scrive spesso "ehm" in italiano.
        listOf("ehm", "Ehm,", "eh", "ehhh", "uhm", "um", "mmm", "eee", "emm", "mhm", "M", "M.", "hm").forEach { assertTrue(it, SpeechCuts.isFiller(it)) }
        listOf("e", "è", "ecco", "allora", "uno", "mamma", "ma", "mi").forEach { assertFalse(it, SpeechCuts.isFiller(it)) }
        val words = listOf(SpokenWord(0, 300_000, "Allora"), SpokenWord(400_000, 800_000, "ehm,"), SpokenWord(900_000, 1_200_000, "e"))
        val f = SpeechCuts.fillers(words)
        assertEquals(1, f.size)
        assertEquals(370_000L, f[0].startUs)
    }

    private fun video(id: String, lenUs: Long) = Clip(id = id, uri = "content://v/$id", sourceDurationUs = 30_000_000, trimEndUs = lenUs, width = 1920, height = 1080, fps = 30f)

    @Test fun cutsTheTimelineAndShiftsTextsAndMusic() {
        val p = Project(
            name = "parlato",
            clips = listOf(video("a", 10_000_000), video("b", 6_000_000).copy(transitionOut = null)),
            texts = listOf(
                TextItem(text = "prima", startUs = 1_000_000, durationUs = 1_000_000),
                TextItem(text = "dentro", startUs = 3_200_000, durationUs = 500_000, subtitle = true),
                TextItem(text = "dopo", startUs = 12_000_000, durationUs = 1_000_000, subtitle = true, wordsUs = listOf(0, 500_000)),
                TextItem(text = "a cavallo", startUs = 2_500_000, durationUs = 2_000_000),
            ),
            music = listOf(MusicTrack(uri = "m", name = "M", sourceDurationUs = 60_000_000), MusicTrack(uri = "v", name = "V", sourceDurationUs = 5_000_000, startUs = 11_000_000)),
        )
        // Si tolgono 3–4 s (nella prima clip) e 11–11,5 s (nella seconda).
        val q = p.cutTimeline(listOf(Span(3_000_000, 4_000_000), Span(11_000_000, 11_500_000)))
        assertEquals(p.durationUs - 1_500_000, q.durationUs)
        assertEquals(4, q.clips.size) // a1, a2, b1, b2
        assertTrue(q.clips.all { it.transitionOut == null })
        assertEquals(listOf("prima", "dopo", "a cavallo"), q.texts.map { it.text }) // il sottotitolo dentro il taglio sparisce
        assertEquals(1_000_000L, q.texts[0].startUs)
        assertEquals(10_500_000L, q.texts[1].startUs) // 12 s − 1,5 s
        assertEquals(listOf(0L, 500_000L), q.texts[1].wordsUs)
        assertEquals(2_500_000L, q.texts[2].startUs); assertEquals(1_000_000L, q.texts[2].durationUs) // accorciato di 1 s
        assertEquals(0L, q.music[0].startUs) // la musica di sottofondo non si sposta
        assertEquals(10_000_000L, q.music[1].startUs) // la voce partiva a 11 s: ora parte dove finisce il primo taglio
        assertEquals(500_000L, q.music[1].offsetUs)
    }

    @Test fun planOnlyTouchesVideosWithSound() {
        val photo = Clip(id = "p", uri = "file:///p.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true)
        val p = Project(name = "x", clips = listOf(photo, video("a", 6_000_000), video("m", 4_000_000).copy(muted = true)))
        // Foto (0–3 s, silenziosa), video parlato con una pausa (3–9 s), video muto (9–13 s).
        val a = pcm(3.0 to false, 1.5 to true, 2.0 to false, 2.5 to true, 4.0 to false)
        val plan = SpeechCuts.plan(p, a, emptyList(), minPauseUs = 700_000)
        assertEquals(1, plan.pauses)
        assertTrue(plan.spans.all { it.startUs >= 3_000_000 && it.endUs <= 9_000_000 })
        val q = p.cutTimeline(plan.spans)
        assertEquals(3_000_000L, q.clips.first().outputUs) // la foto resta com'era
        assertTrue(q.durationUs < p.durationUs)
    }
}
