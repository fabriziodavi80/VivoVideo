package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Intervista doppia: domande trovate nel parlato, risposte ritagliate, montaggio alternato e a schermo diviso. */
class InterviewTest {
    /** Parlato finto: ogni parola dura 0,3 s, 0,1 s fra le parole; una pausa di 1 s dopo ogni frase. */
    private fun speech(vararg sentences: String): List<SpokenWord> {
        val out = mutableListOf<SpokenWord>()
        var t = 500_000L
        sentences.forEach { s ->
            s.split(" ").forEach { w -> out += SpokenWord(t, t + 300_000, w); t += 400_000 }
            t += 1_000_000
        }
        return out
    }

    private val wordsA = speech(
        "Ciao, come ti chiami?", "Mi chiamo Marco e vengo da Brescia.",
        "Qual è il tuo piatto preferito?", "La pizza, senza dubbio. Margherita.",
        "Che lavoro fai?", "Faccio il contabile in un'azienda.",
    )
    // B: stesse domande dette un po' diversamente dal riconoscimento vocale (accenti, una parola in più).
    private val wordsB = speech(
        "Ciao come ti chiami?", "Sono Giulia.",
        "Qual e il tuo piatto preferito, dimmi?", "Il risotto ai funghi, quello di mia nonna.",
        "Che lavoro fai?", "Insegno matematica.",
    )

    @Test fun questionsAreTheSentencesWithAQuestionMark() {
        assertEquals(listOf("Ciao, come ti chiami?", "Qual è il tuo piatto preferito?", "Che lavoro fai?"), Interview.questionsIn(wordsA))
        // Le domande di una o due parole ("Davvero?") non contano.
        assertTrue(Interview.questionsIn(speech("Davvero?", "Sì.")).isEmpty())
    }

    @Test fun answersAreFoundInBothVideos() {
        val qas = Interview.match(wordsA, wordsB, 60_000_000, 60_000_000)
        assertEquals(3, qas.size)
        assertTrue(qas.all { it.a != null && it.b != null })
        fun said(words: List<SpokenWord>, s: Span) = words.filter { it.startUs >= s.startUs - 1 && it.endUs <= s.endUs + 1 }.joinToString(" ") { it.text }
        assertEquals("Mi chiamo Marco e vengo da Brescia.", said(wordsA, qas[0].a!!))
        assertEquals("Il risotto ai funghi, quello di mia nonna.", said(wordsB, qas[1].b!!))
        assertEquals("Insegno matematica.", said(wordsB, qas[2].b!!))
        // La risposta non si mangia la domanda dopo.
        val q2 = wordsA.first { it.text == "Qual" }
        assertTrue(qas[0].a!!.endUs < q2.startUs)
    }

    @Test fun typedQuestionsWinAndMissingOnesAreNull() {
        val qas = Interview.match(wordsA, wordsB, 60_000_000, 60_000_000, typed = listOf("Che lavoro fai", "Hai animali in casa?"))
        assertEquals(2, qas.size)
        assertNotNull(qas[0].a); assertNotNull(qas[0].b)
        assertNull(qas[1].a); assertNull(qas[1].b)
        assertTrue(!qas[1].usable)
    }

    private val clipA = Clip(id = "A", uri = "content://v/a", sourceDurationUs = 60_000_000, width = 1080, height = 1920, fileName = "a.mp4")
    private val clipB = Clip(id = "B", uri = "content://v/b", sourceDurationUs = 60_000_000, width = 1080, height = 1920)
    private val black = Clip(uri = "file:///nero.jpg", sourceDurationUs = Clip.PHOTO_MAX_US, trimEndUs = 5_000_000, isPhoto = true, width = 1080, height = 1920, fill = true)

    @Test fun alternateIsCardThenAThenB() {
        val qas = Interview.match(wordsA, wordsB, 60_000_000, 60_000_000)
        val p = Interview.build("Doppia", clipA, clipB, qas, InterviewOptions(nameA = "Marco", nameB = "Giulia", captionA = "contabile"), black, 1080, 1920)
        assertEquals(9, p.clips.size)
        assertEquals(listOf(true, false, false), p.clips.take(3).map { it.isPhoto })
        assertEquals(listOf(clipA.uri, clipB.uri), p.clips.subList(1, 3).map { it.uri })
        assertEquals(qas[0].a!!.startUs, p.clips[1].trimStartUs)
        assertEquals(qas[0].b!!.endUs, p.clips[2].trimEndUs)
        // Cartello: "Domanda 1" e la domanda; nomi e didascalia all'inizio di ogni risposta.
        assertTrue(p.texts.any { it.text == "Domanda 1" } && p.texts.any { it.text == "Che lavoro fai?" })
        assertEquals(3, p.texts.count { it.text == "Marco" })
        assertEquals(3, p.texts.count { it.text == "contabile" })
        val marco = p.texts.first { it.text == "Marco" }
        assertEquals(p.clipStartsUs()[1], marco.startUs)
        assertEquals(VideoFormat.VERTICAL, p.format)
        assertEquals(listOf(clipA, clipB), p.originals)
        assertTrue(p.layers.isEmpty())
        assertEquals(null, p.clips.last().transitionOut)
    }

    @Test fun splitShowsBothOneVoiceAtATime() {
        val qas = Interview.match(wordsA, wordsB, 60_000_000, 60_000_000)
        val stills = mutableListOf<Pair<String, Long>>()
        val p = Interview.build("Doppia", clipA, clipB, qas, InterviewOptions(format = InterviewFormat.SPLIT, nameA = "Marco", nameB = "Giulia"), black, 1080, 1920) { who, at ->
            stills += who.uri to at
            Layer(uri = "file:///fermo_${who.id}_$at.jpg", width = 540, height = 960)
        }
        // Per domanda: cartello e un tratto nero con sopra i due video uno dopo l'altro, e chi ascolta fermo.
        assertEquals(6, p.clips.size)
        assertTrue(p.clips.all { it.isPhoto })
        val videos = p.layers.filter { it.isVideo }
        assertEquals(6, videos.size)
        val first = videos.take(2)
        assertEquals(listOf(clipA.uri, clipB.uri), first.map { it.uri })
        assertEquals(first[0].endUs, first[1].startUs)
        assertEquals(qas[0].a!!.startUs, first[0].sourceStartUs)
        // A sopra, B sotto (video verticale); chi ascolta è più scuro.
        assertTrue(first[0].pose.y < 0 && first[1].pose.y > 0)
        val listeners = p.layers.filter { !it.isVideo }
        assertEquals(6, listeners.size)
        assertTrue(listeners.all { it.pose.opacity == Interview.LISTENER_OPACITY })
        // Mentre parla A, B è fermo nella sua metà, per tutta la risposta di A.
        val bStill = listeners.first { it.startUs == first[0].startUs }
        assertEquals(first[0].durationUs, bStill.durationUs)
        assertTrue(bStill.pose.y > 0)
        assertEquals(clipB.uri to qas[0].b!!.startUs, stills[0])
        // Il tratto nero dura quanto le due risposte.
        assertEquals(first[0].durationUs + first[1].durationUs, p.clips[1].outputUs)
    }

    @Test fun halvesFitTheirSide() {
        // Verticale 1080×1920, video verticale: in metà altezza la scala è 0,5; video orizzontale ci sta già intero.
        val v = Interview.half(1080, 1920, 1080, 1920, first = true)
        assertEquals(0.5f, v.scale, 1e-4f); assertEquals(-0.25f, v.y, 0f); assertEquals(0f, v.x, 0f)
        assertEquals(1f, Interview.half(1920, 1080, 1080, 1920, first = false).scale, 1e-4f)
        // Orizzontale 1920×1080: affiancati.
        val h = Interview.half(1920, 1080, 1920, 1080, first = false)
        assertEquals(0.5f, h.scale, 1e-4f); assertEquals(0.25f, h.x, 0f); assertEquals(0f, h.y, 0f)
    }
}
