package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Jump cut pronti: tagli, inquadratura che alterna, punch-in, lampi, whoosh e sottotitoli. */
class JumpCutsTest {
    private fun talk(id: String = "a", lenUs: Long = 10_000_000, w: Int = 1080, h: Int = 1920) =
        Clip(id = id, uri = "content://v/$id", sourceDurationUs = lenUs, width = w, height = h, fps = 30f)

    /** Tre pause in un video parlato di 10 s. */
    private val pauses = listOf(Span(2_000_000, 2_600_000), Span(5_000_000, 5_500_000), Span(7_000_000, 7_800_000))

    @Test fun piecesAlternateNormalAndTight() {
        val p = Project(name = "x", clips = listOf(talk()))
        val pieces = JumpCuts.pieces(p, pauses, JumpStyle.CLEAN, null)
        assertEquals(4, pieces.size)
        assertEquals(listOf(1f, JumpCuts.ZOOM, 1f, JumpCuts.ZOOM), pieces.map { it.zoom })
        // Tratti nel file: tra un pezzo e l'altro c'è la pausa tolta.
        assertEquals(2_000_000L, pieces[0].srcEndUs)
        assertEquals(2_600_000L, pieces[1].srcStartUs)
    }

    @Test fun cutsChangeTheFramingAndShortenTheVideo() {
        val p = Project(name = "x", clips = listOf(talk()))
        val o = JumpCuts.apply(p, pauses, JumpStyle.CLEAN)
        val q = o.project
        assertEquals(4, q.clips.size)
        assertEquals(p.durationUs - 1_900_000, q.durationUs)
        assertEquals(listOf(1f, JumpCuts.ZOOM, 1f, JumpCuts.ZOOM), q.clips.map { it.frame.scale })
        assertTrue(q.clips.all { it.jumpCut })
        assertEquals(3, o.summary.cuts)
        assertEquals(2, o.summary.zoomed)
        assertEquals(1_900_000L, o.summary.removedUs)
        // Pulito: niente lampi, niente whoosh, niente sottotitoli.
        assertTrue(q.clips.all { it.transitionOut == null })
        assertTrue(o.whooshAtUs.isEmpty())
    }

    @Test fun tightFramingIsCenteredOnTheFaceWithoutShowingEdges() {
        val c = talk().copy(fill = true)
        // Volto in alto a destra in un video verticale nel quadro verticale: si sposta verso il centro…
        val t = JumpCuts.punchFrame(c, JumpCuts.ZOOM, 0.7f to 0.3f, 9f / 16f)
        assertEquals(JumpCuts.ZOOM, t.scale)
        assertTrue(t.x < 0f && t.y > 0f)
        // …ma mai oltre quanto permette lo zoom (i bordi restano coperti).
        val lim = (JumpCuts.ZOOM - 1f) / 2f
        val far = JumpCuts.punchFrame(c, JumpCuts.ZOOM, 1f to 0f, 9f / 16f)
        assertEquals(-lim, far.x, 1e-4f)
        assertEquals(lim, far.y, 1e-4f)
        // Volto al centro: niente spostamento. Senza volto: zoom al centro.
        val mid = JumpCuts.punchFrame(c, JumpCuts.ZOOM, 0.5f to 0.5f, 9f / 16f)
        assertEquals(0f, mid.x, 1e-5f); assertEquals(0f, mid.y, 1e-5f)
        assertEquals(Transform(scale = JumpCuts.ZOOM), JumpCuts.punchFrame(c, JumpCuts.ZOOM, null, 9f / 16f))
        // Inquadratura fatta a mano: si stringe soltanto, il resto resta.
        val hand = c.copy(frame = Transform(x = 0.1f, scale = 1.2f))
        assertEquals(Transform(x = 0.1f, scale = 1.2f * JumpCuts.ZOOM), JumpCuts.punchFrame(hand, JumpCuts.ZOOM, 0.7f to 0.3f, 9f / 16f))
    }

    @Test fun subjectLookupUsesTheFileTimeOfEachPiece() {
        val p = Project(name = "x", clips = listOf(talk().copy(fill = true)))
        val asked = mutableListOf<Long>()
        JumpCuts.apply(p, pauses, JumpStyle.CLEAN, outAspect = 9f / 16f) { _, src -> asked += src; 0.6f to 0.5f }
        // Si chiede il volto solo per i pezzi stretti, a metà di ciascun pezzo nel file.
        assertEquals(listOf((2_600_000L + 5_000_000L) / 2, (7_800_000L + 10_000_000L) / 2), asked)
    }

    @Test fun dynamicPunchesInOnTheLoudPhrases() {
        val p = Project(name = "x", clips = listOf(talk()))
        // Parlato a –30 dB, il terzo pezzo (5,5–7 s) detto con più forza (–20 dB).
        val rate = 16_000
        val pcm = FloatArray(10 * rate) { i ->
            val t = i.toFloat() / rate
            val amp = if (t in 5.5f..7f) 0.14f else 0.045f
            amp * kotlin.math.sin(2 * Math.PI * 180 * t).toFloat()
        }
        val pieces = JumpCuts.pieces(p, pauses, JumpStyle.DYNAMIC, pcm)
        assertEquals(listOf(false, false, true, false), pieces.map { it.punch })
        assertEquals(JumpCuts.PUNCH, pieces[2].zoom)
        val o = JumpCuts.apply(p, pauses, JumpStyle.DYNAMIC, pcm)
        assertEquals(1, o.summary.punches)
        assertEquals(JumpCuts.PUNCH, o.project.clips[2].frame.scale)
        // Pulito con lo stesso audio: nessun punch-in.
        assertTrue(JumpCuts.pieces(p, pauses, JumpStyle.CLEAN, pcm).none { it.punch })
    }

    @Test fun socialAddsFlashesWhooshesAndWordSubtitles() {
        // Video di 30 s con 12 pause: 13 pezzi, 12 tagli, un lampo ogni 4 tagli.
        val p = Project(name = "x", clips = listOf(talk(lenUs = 30_000_000)))
        val cuts = (1..12).map { k -> Span(k * 2_300_000L, k * 2_300_000L + 300_000) }
        val words = listOf(
            SpokenWord(100_000, 500_000, "Ciao"), SpokenWord(600_000, 900_000, "a"), SpokenWord(1_000_000, 1_500_000, "tutti"),
            SpokenWord(1_600_000, 1_900_000, "ehm"), SpokenWord(2_350_000, 2_550_000, "via"), SpokenWord(2_700_000, 3_200_000, "oggi"),
        )
        val o = JumpCuts.apply(p, cuts, JumpStyle.SOCIAL, words = words, whoosh = true)
        assertEquals(12, o.summary.cuts)
        assertEquals(3, o.summary.flashes)
        assertEquals(3, o.project.clips.count { it.transitionOut?.type == TransitionType.FLASH })
        assertEquals(3, o.whooshAtUs.size)
        // Il whoosh culmina sul taglio: il taglio è l'inizio di una clip.
        val starts = o.project.clipStartsUs()
        assertTrue(o.whooshAtUs.all { w -> starts.any { abs(it - w) < 1_000 } })
        val tr = JumpCuts.whooshTrack("file:///w.wav", o.whooshAtUs[0])
        assertEquals(o.whooshAtUs[0] - JumpCuts.WHOOSH_PEAK_US, tr.startUs)
        assertTrue(tr.dialog)
        // Sottotitoli parola per parola, senza l'ehm e senza la parola caduta nella pausa tolta.
        val subs = o.project.texts.filter { it.subtitle }
        assertTrue(subs.isNotEmpty())
        val all = subs.joinToString(" ") { it.text }
        assertFalse(all.contains("ehm"))
        assertFalse(all.contains("via"))
        assertTrue(all.contains("Ciao") && all.contains("oggi"))
        assertTrue(subs.all { it.wordMode == 2 })
        // Senza whoosh: i lampi restano, i suoni no.
        assertTrue(JumpCuts.apply(p, cuts, JumpStyle.SOCIAL, words = words, whoosh = false).whooshAtUs.isEmpty())
    }

    @Test fun eachTakeStartsNormalAndPhotosMutedAndUncutClipsStayUntouched() {
        val photo = Clip(id = "f", uri = "file:///f.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 3_000_000, isPhoto = true)
        val muted = talk("m", lenUs = 4_000_000).copy(muted = true)
        val a = talk("a", lenUs = 6_000_000)
        val b = talk("b", lenUs = 6_000_000)
        val p = Project(name = "x", clips = listOf(a, photo, b, muted))
        // Una pausa in a (1–1,5 s) e una in b (9+2…9+2,5 s); niente nel video senza audio.
        val o = JumpCuts.apply(p, listOf(Span(1_000_000, 1_500_000), Span(11_000_000, 11_500_000)), JumpStyle.CLEAN)
        val q = o.project
        assertEquals(listOf("a", "a", "f", "b", "b", "m").map { if (it == "f") "file:///f.jpg" else "content://v/$it" }, q.clips.map { it.uri })
        // Ogni ripresa riparte dall'inquadratura normale.
        assertEquals(listOf(1f, JumpCuts.ZOOM, 1f, 1f, JumpCuts.ZOOM, 1f), q.clips.map { it.frame.scale })
        assertFalse(q.clips[2].jumpCut)
        assertFalse(q.clips[5].jumpCut)
        assertEquals(2, o.summary.cuts)
    }

    @Test fun videoUsedTwiceFindsItsOwnPieces() {
        val a1 = talk("a")
        val a2 = a1.copy(id = "a2")
        val p = Project(name = "x", clips = listOf(a1, talk("b", lenUs = 2_000_000), a2))
        // La stessa pausa del file, tolta in tutte e due le copie; dopo la pausa restano 5,5 s di parlato senza pause:
        // l'inquadratura cambia anche a metà (7,25 s del file), senza tagliare.
        val o = JumpCuts.apply(p, listOf(Span(4_000_000, 4_500_000), Span(16_000_000, 16_500_000)), JumpStyle.CLEAN)
        val z = JumpCuts.ZOOM
        assertEquals(listOf(1f, z, 1f, 1f, 1f, z, 1f), o.project.clips.map { it.frame.scale })
        assertEquals(2, o.summary.cuts)
        assertEquals(2, o.summary.reframes)
        assertEquals(p.durationUs - 1_000_000, o.project.durationUs)
    }

    @Test fun splitClipsOfTheSameTakeKeepAlternating() {
        // La ripresa era già divisa in due a 3 s (senza pausa lì): il conteggio continua.
        val a = talk()
        val p = Project(name = "x", clips = listOf(a.copy(trimEndUs = 3_000_000), a.copy(id = "a2", trimStartUs = 3_000_000)))
        val o = JumpCuts.apply(p, pauses, JumpStyle.CLEAN)
        val scales = o.project.clips.map { it.frame.scale }
        // Pezzi: 0–2 | 2,6–3 + 3–5 (stesso pezzo, diviso) | 5,5–7 | 7,8–10.
        assertEquals(listOf(1f, JumpCuts.ZOOM, JumpCuts.ZOOM, 1f, JumpCuts.ZOOM), scales)
        assertEquals(3, o.summary.cuts)
    }

    @Test fun noPausesStillChangesTheFramingInLongPhrases() {
        // 10 s di parlato senza pause: niente si toglie, ma l'inquadratura cambia ogni 2,5 s (mai oltre 4,5 s).
        val p = Project(name = "x", clips = listOf(talk()))
        val o = JumpCuts.apply(p, emptyList(), JumpStyle.SOCIAL)
        assertEquals(0, o.summary.cuts)
        assertEquals(3, o.summary.reframes)
        assertEquals(0L, o.summary.removedUs)
        assertEquals(p.durationUs, o.project.durationUs)
        val z = JumpCuts.ZOOM
        assertEquals(listOf(1f, z, 1f, z), o.project.clips.map { it.frame.scale })
        assertEquals(listOf(0L, 2_500_000L, 5_000_000L, 7_500_000L), o.project.clipStartsUs())
        // Sui cambi senza taglio niente lampi né whoosh.
        assertEquals(0, o.summary.flashes)
        assertTrue(o.project.clips.all { it.transitionOut == null })
        // Un video corto resta com'è.
        val short = Project(name = "x", clips = listOf(talk(lenUs = 4_000_000)))
        assertEquals(short.clips.map { it.frame }, JumpCuts.apply(short, emptyList(), JumpStyle.CLEAN).project.clips.map { it.frame })
    }

    @Test fun longPhrasesChangeFramingBetweenSentences() {
        // Parole fino a 9 s: a 3,6 s finisce una frase ("oggi."), a 5,1 s c'è una virgola, il resto è parlato fitto.
        val words = (0 until 30).map { k ->
            val s = k * 300_000L
            val text = when (k) { 11 -> "oggi."; 16 -> "dopo,"; else -> "parola" }
            SpokenWord(s, s + 250_000, text)
        }
        val splits = JumpCuts.holdSplits(0, 9_000_000, words, null)
        // Il primo cambio cade fra le due frasi (dopo "oggi."), il secondo sulla virgola: pezzi tutti fra 1,5 e 4,5 s.
        assertEquals(1, splits.count { kotlin.math.abs(it - 3_575_000) < 30_000 })
        val bounds = listOf(0L) + splits + 9_000_000L
        bounds.zipWithNext { a, b -> assertTrue("${b - a}", b - a in JumpCuts.MIN_HOLD_US..JumpCuts.MAX_HOLD_US) }
    }

    @Test fun withoutWordsTheChangeFallsInTheQuietestMoment() {
        // Voce continua con un calo a 4 s (un respiro): il cambio d'inquadratura cade lì, non a metà (3,5 s).
        val rate = 16_000
        val pcm = FloatArray(7 * rate) { i ->
            val t = i.toFloat() / rate
            val amp = if (t in 3.9f..4.1f) 0.002f else 0.1f
            amp * kotlin.math.sin(2 * Math.PI * 180 * t).toFloat()
        }
        val splits = JumpCuts.holdSplits(0, 7_000_000, emptyList(), pcm)
        assertEquals(1, splits.size)
        assertEquals(4_000_000.0, splits[0].toDouble(), 40_000.0)
    }
}
