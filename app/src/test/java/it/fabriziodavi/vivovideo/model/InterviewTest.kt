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

    /** Parlato senza punto di domanda (come lo scrive a volte il modello Massima precisione), con pause brevi. */
    private fun plain(vararg sentences: String): List<SpokenWord> {
        val out = mutableListOf<SpokenWord>()
        var t = 300_000L
        sentences.forEach { s ->
            s.split(" ").forEach { w -> out += SpokenWord(t, t + 250_000, w); t += 300_000 }
            t += 700_000
        }
        return out
    }

    @Test fun questionsWithoutQuestionMarkAreFoundBecauseBothHeardThem() {
        val a = plain("Ciao come ti chiami.", "Mi chiamo Marco.", "Qual è il tuo piatto preferito.", "La pizza margherita.", "Che lavoro fai.", "Faccio il contabile.")
        val b = plain("Ciao, come ti chiami.", "Mi chiamo Giulia.", "Qual e il tuo piatto preferito.", "Il risotto ai funghi.", "Che lavoro fai.", "Insegno matematica.")
        val qas = Interview.match(a, b, 30_000_000, 30_000_000)
        // "Ciao" resta fuori: la domanda parte dalla parola da domanda.
        assertEquals(listOf("Come ti chiami?", "Qual è il tuo piatto preferito?", "Che lavoro fai?"), qas.map { it.question })
        assertTrue(qas.all { it.a != null && it.b != null })
        // La risposta uguale ("Mi chiamo…") non diventa una domanda e resta nella risposta.
        fun said(words: List<SpokenWord>, s: Span) = words.filter { it.startUs >= s.startUs - 1 && it.endUs <= s.endUs + 1 }.joinToString(" ") { it.text }
        assertEquals("Mi chiamo Marco.", said(a, qas[0].a!!))
        assertEquals("Il risotto ai funghi.", said(b, qas[1].b!!))
    }

    @Test fun withoutPunctuationAtAllThePausesSplitTheTurns() {
        val a = plain("ciao come ti chiami", "mi chiamo Marco", "dove abiti", "a Brescia")
        val b = plain("ciao come ti chiami", "sono Giulia", "dove abiti adesso", "a Verona")
        val qas = Interview.match(a, b, 20_000_000, 20_000_000)
        assertEquals(2, qas.size)
        assertEquals("Come ti chiami?", qas[0].question)
        assertTrue(qas.all { it.a != null && it.b != null })
    }

    /** Parlato con le pause scelte: coppie (frase, pausa dopo in µs). */
    private fun paused(vararg parts: Pair<String, Long>): List<SpokenWord> {
        val out = mutableListOf<SpokenWord>()
        var t = 300_000L
        parts.forEach { (s, pause) -> s.split(" ").forEach { w -> out += SpokenWord(t, t + 250_000, w); t += 300_000 }; t += pause }
        return out
    }

    @Test fun questionWrittenDifferentlyInTheTwoVideosStaysWhole() {
        // A e B: la stessa domanda scritta un po' diversa dal riconoscimento; in A una pausa di 0,9 s a metà domanda.
        val a = paused("Allora, qual è il tuo piatto" to 900_000L, "preferito in assoluto." to 700_000L, "La pizza margherita." to 700_000L,
            "E che sport fai di solito." to 700_000L, "Corro." to 700_000L)
        val b = plain("Allora qual'è il tuo piatto preferita in assoluto.", "Il risotto.", "E che sport fai di solito.", "Nuoto.")
        val qas = Interview.match(a, b, 30_000_000, 30_000_000)
        // "Allora" resta fuori: la domanda parte dalla parola da domanda.
        assertEquals(listOf("Qual è il tuo piatto preferito in assoluto?", "E che sport fai di solito?"), qas.map { it.question })
        fun said(words: List<SpokenWord>, s: Span) = words.filter { it.startUs >= s.startUs - 1 && it.endUs <= s.endUs + 1 }.joinToString(" ") { it.text }
        // La risposta comincia dopo la domanda intera, in tutti e due i video.
        assertEquals("La pizza margherita.", said(a, qas[0].a!!))
        assertEquals("Il risotto.", said(b, qas[0].b!!))
        assertEquals("Nuoto.", said(b, qas[1].b!!))
        // Pausa a metà domanda in un video solo, con le parole uguali nei due: la domanda resta intera.
        val a2 = paused("Qual è il tuo piatto" to 900_000L, "preferito in assoluto." to 700_000L, "La pizza." to 700_000L)
        val b2 = plain("Qual è il tuo piatto preferito in assoluto.", "Il risotto.")
        assertEquals(listOf("Qual è il tuo piatto preferito in assoluto?"), Interview.match(a2, b2, 20_000_000, 20_000_000).map { it.question })
    }

    @Test fun theThreeQuestionsOfTheTestStayWholeWithoutPunctuation() {
        // Collaudo dell'11/11/2026: le tre domande vere, scritte un po' diverse nei due video ("qual'è"/"qual è",
        // "fra 10"/"tra dieci"), senza punteggiatura, con una pausa a metà domanda e risposte che cominciano uguali.
        val a = paused("qual è il tuo" to 800_000L, "piatto preferito" to 900_000L, "mi piace la pizza" to 900_000L,
            "cosa faresti con una giornata libera" to 1_000_000L, "sì andrei al mare" to 900_000L,
            "come ti vedi" to 700_000L, "fra 10 anni" to 1_100_000L, "spero in pensione" to 500_000L)
        val b = paused("qual'è il tuo piatto preferito" to 800_000L, "mi piace il risotto" to 1_000_000L,
            "cosa faresti con una giornata" to 600_000L, "libera" to 900_000L, "sì dormirei" to 800_000L,
            "come ti vedi tra dieci anni" to 900_000L, "spero in barca" to 500_000L)
        val qas = Interview.match(a, b, 30_000_000, 30_000_000)
        assertEquals(listOf("Qual è il tuo piatto preferito?", "Cosa faresti con una giornata libera?", "Come ti vedi fra 10 anni?"), qas.map { it.question })
        fun said(words: List<SpokenWord>, s: Span) = words.filter { it.startUs >= s.startUs - 1 && it.endUs <= s.endUs + 1 }.joinToString(" ") { it.text }
        assertEquals("mi piace la pizza", said(a, qas[0].a!!))
        assertEquals("mi piace il risotto", said(b, qas[0].b!!))
        assertEquals("sì andrei al mare", said(a, qas[1].a!!))
        assertEquals("sì dormirei", said(b, qas[1].b!!))
        assertEquals("spero in pensione", said(a, qas[2].a!!))
        assertEquals("spero in barca", said(b, qas[2].b!!))
    }

    @Test fun answersEndingWithTheSameWordDoNotStartTheNextQuestion() {
        // Tutte e due le risposte finiscono con "passeggiata" subito prima della domanda dopo.
        val a = paused("Cosa faresti con una giornata libera?" to 900_000L, "Andrei a fare una passeggiata." to 800_000L,
            "Come ti vedi fra 10 anni?" to 900_000L, "Sposato." to 500_000L)
        val b = paused("Cosa faresti con una giornata libera?" to 900_000L, "Una bella passeggiata." to 700_000L,
            "Come ti vedi tra dieci anni?" to 900_000L, "In barca." to 500_000L)
        assertEquals(listOf("Cosa faresti con una giornata libera?", "Come ti vedi fra 10 anni?"), Interview.match(a, b, 20_000_000, 20_000_000).map { it.question })
        // Anche senza punteggiatura.
        val a2 = paused("cosa faresti con una giornata libera" to 900_000L, "andrei a fare una passeggiata" to 800_000L,
            "come ti vedi fra 10 anni" to 900_000L, "sposato" to 500_000L)
        val b2 = paused("cosa faresti con una giornata libera" to 900_000L, "una bella passeggiata" to 700_000L,
            "come ti vedi tra dieci anni" to 900_000L, "in barca" to 500_000L)
        assertEquals(listOf("Cosa faresti con una giornata libera?", "Come ti vedi fra 10 anni?"), Interview.match(a2, b2, 20_000_000, 20_000_000).map { it.question })
    }

    /** Parlato come lo dà il riconoscimento sul telefono: "parola@secondi", fine di ogni parola = inizio della dopo. */
    private fun heard(s: String): List<SpokenWord> {
        val p = s.trim().split(" ").map { val (w, t) = it.split("@"); w to (t.replace(',', '.').toDouble() * 1e6).toLong() }
        return p.mapIndexed { i, (w, t) -> SpokenWord(t, maxOf(p.getOrNull(i + 1)?.second ?: (t + 400_000), t + 60_000), w) }
    }

    @Test fun realTestVideosOfNovember11() {
        // Il parlato vero dei due video del collaudo (diario dell'11/11/2026, modello Massima precisione): niente
        // punteggiatura, tempi spostati ("anni" e "ancora" insieme a 16,0 s), risposte che cominciano con "la".
        val a = heard("qual@0,1 è@0,5 il@0,7 tuo@1,2 piatto@2,3 preferito@3,0 la@4,5 pasta@5,1 cosa@7,4 faresti@7,9 con@8,0 una@8,2 giornata@9,1 libera@9,7 se@11,9 c'è@12,1 il@12,4 sole@12,8 una@13,1 bella@13,5 passeggiata@13,9 come@14,4 ti@15,4 vedi@17,0 tra@17,7 dieci@17,9 anni@18,4 male@21,6 male@22,1")
        val b = heard("qual@0,1 è@0,6 il@0,7 tuo@1,0 piatto@2,1 preferito@3,5 la@4,7 pizza@5,3 cosa@7,5 faresti@7,8 in@8,5 una@8,7 giornata@8,9 libera@9,5 andrei@10,6 al@11,2 mare@11,6 come@11,9 ti@13,0 vedi@13,0 tra@13,6 dieci@15,5 anni@16,0 ancora@16,0 al@20,2 lavoro@20,7")
        val qas = Interview.match(a, b, 23_000_000, 22_000_000)
        assertEquals(listOf("Qual è il tuo piatto preferito?", "Cosa faresti con una giornata libera?", "Come ti vedi tra dieci anni?"), qas.map { it.question })
        fun said(words: List<SpokenWord>, s: Span) = words.filter { it.startUs >= s.startUs - 1 && it.startUs < s.endUs }.joinToString(" ") { it.text }
        assertEquals("la pasta", said(a, qas[0].a!!))
        assertEquals("la pizza", said(b, qas[0].b!!))
        assertEquals("se c'è il sole una bella passeggiata", said(a, qas[1].a!!))
        assertEquals("andrei al mare", said(b, qas[1].b!!))
        assertEquals("male male", said(a, qas[2].a!!))
        // "anni" e "ancora" risultano iniziare insieme: senza audio il taglio va subito dopo l'inizio di "anni"
        // (con l'audio lo decide la pausa vera, vedi cutsGoInTheRealPauseOfTheAudio).
        assertTrue(qas[2].b!!.startUs in 16_000_000..16_100_000 && qas[2].b!!.endUs >= 20_700_000)
    }

    @Test fun cutsGoInTheRealPauseOfTheAudio() {
        // Audio finto a 16 kHz: voce (tono) da 0 a 2,0 s (domanda), silenzio fino a 2,8 s, voce fino a 5,0 s (risposta),
        // silenzio fino a 6,0 s, voce fino a 7,0 s (domanda dopo). I tempi delle parole sono sbagliati di mezzo secondo.
        val rate = 16_000
        val pcm = FloatArray(8 * rate) { i ->
            val t = i.toDouble() / rate
            val voice = t < 2.0 || (t >= 2.8 && t < 5.0) || (t >= 6.0 && t < 7.0)
            if (voice) (0.3 * kotlin.math.sin(2 * Math.PI * 220 * t)).toFloat() else 0.001f
        }
        val w = listOf(
            SpokenWord(0, 900_000, "come"), SpokenWord(900_000, 1_700_000, "ti"), SpokenWord(1_700_000, 2_300_000, "chiami"),
            SpokenWord(2_300_000, 3_600_000, "mi"), SpokenWord(3_600_000, 4_400_000, "chiamo"), SpokenWord(4_400_000, 5_600_000, "Marco"),
            SpokenWord(5_600_000, 6_500_000, "dove"), SpokenWord(6_500_000, 7_000_000, "abiti"),
        )
        val r = Interview.refine(w, pcm, rate, qEnd = 2, nextStart = 6, span = Span(2_150_000, 5_550_000), dur = 8_000_000)!!
        assertTrue("inizio ${r.startUs}", r.startUs in 2_600_000..2_800_000)
        assertTrue("fine ${r.endUs}", r.endUs in 5_100_000..5_300_000)
        // Senza audio restano i tempi delle parole.
        assertEquals(Span(2_150_000, 5_550_000), Interview.refine(w, null, rate, 2, 6, Span(2_150_000, 5_550_000), 8_000_000))
    }

    @Test fun aSingleVideoStillFindsQuestionWords() {
        // Nessun pezzo uguale nell'altro video e nessun "?": valgono le frasi che cominciano con una parola da domanda.
        val a = plain("Come ti chiami.", "Marco.", "Quanti anni hai.", "Quarantasei.")
        assertEquals(listOf("Come ti chiami?", "Quanti anni hai?"), Interview.questionsIn(a, plain("Boh.")))
        // Risposte uguali senza parole da domanda e corte non sono domande.
        assertTrue(Interview.shared(plain("Mi piace molto."), plain("Mi piace molto.")).isEmpty())
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
