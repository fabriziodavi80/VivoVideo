package it.fabriziodavi.vivovideo.model

import java.text.Normalizer

/** Come mostrare le due risposte a ogni domanda. */
enum class InterviewFormat(val label: String, val hint: String) {
    ALTERNATE("Alternata", "Cartello con la domanda, poi la risposta di A, poi quella di B, a tutto schermo."),
    SPLIT("Schermo diviso", "A e B insieme sullo schermo (affiancati, o uno sopra l'altro nei video verticali); parla uno alla volta, l'altro resta fermo e più scuro."),
}

/** Nomi e didascalie (in stile TV), formato e cartelli. */
data class InterviewOptions(
    val format: InterviewFormat = InterviewFormat.ALTERNATE,
    val nameA: String = "",
    val nameB: String = "",
    val captionA: String = "",
    val captionB: String = "",
    /** Durata del cartello con la domanda (0 = niente cartelli). */
    val cardUs: Long = 2_500_000,
)

/** Una domanda con le due risposte (tratti nel file di ciascun video; null = non trovata). */
data class InterviewQA(val question: String, val a: Span?, val b: Span?) {
    val usable: Boolean get() = a != null || b != null
}

/**
 * Intervista doppia (stile Le Iene): due video con le stesse domande. Le domande si trovano nel parlato (le frasi con il
 * punto di domanda, o quelle scritte dall'utente), la risposta è il tratto fino alla domanda successiva. Tutto qui è
 * senza Android: il ViewModel ci mette solo il riconoscimento vocale, i fotogrammi fermi e la foto nera.
 */
object Interview {
    /** Margini: la risposta parte poco dopo la fine della domanda e finisce poco prima della successiva. */
    const val LEAD_US = 150_000L
    const val TAIL_US = 400_000L
    const val MIN_ANSWER_US = 600_000L

    /** Nome e didascalia restano così a lungo all'inizio di ogni risposta (come in TV). */
    const val NAME_US = 3_500_000L

    /** Chi ascolta, nello schermo diviso: fermo e più scuro. */
    const val LISTENER_OPACITY = 0.45f

    /** Parola senza accenti, minuscola, solo lettere e cifre. */
    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").filter { it.isLetterOrDigit() }

    private fun tokens(s: String): List<String> = s.split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }

    /** Frasi del parlato (parole consecutive fino a . ? !), con il loro tratto. */
    data class Sentence(val text: String, val span: Span, val firstWord: Int, val lastWord: Int) {
        val isQuestion: Boolean get() = text.trimEnd().endsWith("?")
    }

    fun sentences(words: List<SpokenWord>): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var from = 0
        for (i in words.indices) {
            val end = words[i].text.trimEnd().lastOrNull()
            val gap = words.getOrNull(i + 1)?.let { it.startUs - words[i].endUs } ?: 0L
            if (end == '.' || end == '?' || end == '!' || i == words.lastIndex || gap > 1_500_000) {
                val part = words.subList(from, i + 1)
                out += Sentence(part.joinToString(" ") { it.text.trim() }, Span(part.first().startUs, part.last().endUs), from, i)
                from = i + 1
            }
        }
        return out
    }

    /** Le domande del parlato: frasi con il punto di domanda e almeno tre parole (non "sì?", "davvero?"). */
    fun questionsIn(words: List<SpokenWord>): List<String> =
        sentences(words).filter { it.isQuestion && tokens(it.text).size >= 3 }.map { it.text.trim() }

    /**
     * Dove si dice la domanda [q] nel parlato, a partire dalla parola [from]: indice della prima e dell'ultima parola,
     * o null. Si confrontano le parole (senza accenti né punteggiatura): ne basta più di metà, nell'ordine giusto o quasi.
     */
    internal fun find(words: List<SpokenWord>, q: String, from: Int): Pair<Int, Int>? {
        val qt = tokens(q)
        if (qt.isEmpty()) return null
        val wt = words.map { norm(it.text) }
        val need = qt.toSet()
        var best: Pair<Int, Int>? = null
        var bestScore = 0.0
        val win = qt.size + 3
        for (i in from until wt.size) {
            if (wt[i] !in need) continue
            // La domanda finisce al suo punto interrogativo: le parole dopo sono già la risposta ("il risotto…").
            var end = minOf(wt.size, i + win)
            var mark = -1
            for (j in i until end) if (words[j].text.trimEnd().endsWith("?")) { end = j + 1; mark = j; break }
            val window = (i until end)
            val hit = window.filter { wt[it] in need }
            val score = hit.map { wt[it] }.toSet().size.toDouble() / need.size
            // Fine della domanda: il punto interrogativo, se c'è ("…preferito, dimmi?"), altrimenti l'ultima parola uguale.
            if (score > bestScore + 1e-9) { bestScore = score; best = i to maxOf(hit.last(), mark) }
            if (bestScore >= 0.999) break
        }
        return best?.takeIf { bestScore >= 0.55 }
    }

    /**
     * La risposta a ogni domanda nel parlato [words] di un video lungo [durationUs]: dalla fine della domanda all'inizio
     * della domanda successiva trovata (o alla fine del parlato). Le domande si cercano in ordine.
     */
    fun answers(words: List<SpokenWord>, questions: List<String>, durationUs: Long): List<Span?> {
        var from = 0
        val found = questions.map { q -> find(words, q, from)?.also { from = it.second + 1 } }
        return questions.indices.map { k ->
            val f = found[k] ?: return@map null
            val first = f.second + 1
            if (first > words.lastIndex) return@map null
            val nextStart = (k + 1 until questions.size).firstNotNullOfOrNull { found[it] }?.first
            val last = (nextStart ?: words.size) - 1
            if (last < first) return@map null
            val a = (words[first].startUs - LEAD_US).coerceAtLeast(words[f.second].endUs)
            val b = (words[last].endUs + TAIL_US).let { e -> nextStart?.let { minOf(e, words[it].startUs - 50_000) } ?: e }.coerceAtMost(durationUs)
            Span(a, b).takeIf { it.lengthUs >= MIN_ANSWER_US }
        }
    }

    /** Domande e risposte dei due video. Senza domande scritte, quelle del primo video (o del secondo, se il primo non ne ha). */
    fun match(wordsA: List<SpokenWord>, wordsB: List<SpokenWord>, durA: Long, durB: Long, typed: List<String> = emptyList()): List<InterviewQA> {
        val qs = typed.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { questionsIn(wordsA).ifEmpty { questionsIn(wordsB) } }
        val a = answers(wordsA, qs, durA)
        val b = answers(wordsB, qs, durB)
        return qs.indices.map { InterviewQA(qs[it], a[it], b[it]) }
    }

    /**
     * Dove sta ciascuno nello schermo diviso: centro (x, y) e scala del livello (1 = adattato a tutto il quadro).
     * Video verticale: A sopra e B sotto; orizzontale o quadrato: A a sinistra e B a destra.
     */
    fun half(w: Int, h: Int, outW: Int, outH: Int, first: Boolean): Transform {
        val stacked = outH > outW
        val (rw, rh) = if (stacked) outW.toFloat() to outH / 2f else outW / 2f to outH.toFloat()
        val kFrame = minOf(outW.toFloat() / w.coerceAtLeast(1), outH.toFloat() / h.coerceAtLeast(1))
        val kHalf = minOf(rw / w.coerceAtLeast(1), rh / h.coerceAtLeast(1))
        val s = kHalf / kFrame
        val d = if (first) -0.25f else 0.25f
        return if (stacked) Transform(y = d, scale = s) else Transform(x = d, scale = s)
    }

    /** Cartello con la domanda: numero piccolo sopra, domanda grande al centro. */
    internal fun card(n: Int, q: String, startUs: Long, durUs: Long): List<TextItem> = listOf(
        TextPreset.MINIMAL.applyTo(TextItem(text = "Domanda $n")).copy(startUs = startUs, durationUs = durUs, y = -0.1f, color = 0xFFF2B33D.toInt()),
        TextPreset.PULITO.applyTo(TextItem(text = q)).copy(startUs = startUs, durationUs = durUs, y = 0.02f, size = 0.058f),
    )

    /** Nome (etichetta chiara) e didascalia sotto, in basso a sinistra del tratto [x], [y]. */
    internal fun nameplate(name: String, caption: String, startUs: Long, durUs: Long, x: Float, y: Float): List<TextItem> = buildList {
        if (name.isNotBlank()) add(TextPreset.ETICHETTA.applyTo(TextItem(text = name.trim())).copy(startUs = startUs, durationUs = durUs, x = x, y = y, size = 0.042f))
        if (caption.isNotBlank()) add(TextPreset.DIDASCALIA.applyTo(TextItem(text = caption.trim())).copy(startUs = startUs, durationUs = durUs, x = x, y = y + 0.045f, size = 0.032f))
    }

    /**
     * Il montaggio. [a] e [b]: i due video come importati; [black]: una foto nera grande quanto il video (cartelli e fondo
     * dello schermo diviso); [still]: fotogramma fermo di un video a un istante del file (per chi ascolta nello schermo
     * diviso), come livello immagine già pronto, o null.
     */
    fun build(
        name: String,
        a: Clip,
        b: Clip,
        qas: List<InterviewQA>,
        o: InterviewOptions,
        black: Clip,
        outW: Int,
        outH: Int,
        still: (who: Clip, srcUs: Long) -> Layer? = { _, _ -> null },
    ): Project {
        val clips = mutableListOf<Clip>()
        val texts = mutableListOf<TextItem>()
        val layers = mutableListOf<Layer>()
        var t = 0L
        val vertical = outH > outW
        fun addCard(n: Int, q: String) {
            if (o.cardUs <= 0) return
            clips += black.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = 0, trimEndUs = o.cardUs,
                transitionOut = TransitionSpec(TransitionType.FADE_BLACK, 400_000))
            texts += card(n, q, t, o.cardUs)
            t += o.cardUs
        }
        qas.filter { it.usable }.forEachIndexed { k, qa ->
            addCard(k + 1, qa.question)
            if (o.format == InterviewFormat.ALTERNATE) {
                listOf(Triple(a, qa.a, true), Triple(b, qa.b, false)).forEach { (src, span, isA) ->
                    if (span == null) return@forEach
                    clips += src.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = span.startUs, trimEndUs = span.endUs)
                    val nm = if (isA) o.nameA else o.nameB
                    val cap = if (isA) o.captionA else o.captionB
                    texts += nameplate(nm, cap, t, minOf(NAME_US, span.lengthUs), x = -0.2f, y = if (vertical) 0.3f else 0.32f)
                    t += span.lengthUs
                }
            } else {
                val dA = qa.a?.lengthUs ?: 0L
                val dB = qa.b?.lengthUs ?: 0L
                val seg = dA + dB
                clips += black.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = 0, trimEndUs = seg)
                val pa = half(a.width, a.height, outW, outH, first = true)
                val pb = half(b.width, b.height, outW, outH, first = false)
                fun video(src: Clip, span: Span, at: Long, pose: Transform, nm: String) = Layer(
                    uri = src.uri, name = nm.ifBlank { "Intervista" }, width = src.width, height = src.height, isVideo = true,
                    sourceStartUs = span.startUs, sourceDurationUs = src.sourceDurationUs, startUs = at, durationUs = span.lengthUs,
                    pose = pose, animIn = TextAnim.NONE, animOut = TextAnim.NONE, fileName = src.fileName, fileSize = src.fileSize,
                )
                fun listener(src: Clip, srcUs: Long, at: Long, dur: Long, pose: Transform) {
                    still(src, srcUs)?.let { l ->
                        layers += l.copy(startUs = at, durationUs = dur, pose = pose.copy(opacity = LISTENER_OPACITY), animIn = TextAnim.NONE, animOut = TextAnim.NONE)
                    }
                }
                qa.a?.let { sa ->
                    layers += video(a, sa, t, pa, o.nameA)
                    // B ascolta: fermo sull'inizio della sua risposta (o sul primo fotogramma, se non ha risposto).
                    listener(b, qa.b?.startUs ?: 0L, t, sa.lengthUs, pb)
                }
                qa.b?.let { sb ->
                    layers += video(b, sb, t + dA, pb, o.nameB)
                    // A ascolta: fermo sulla fine della sua risposta.
                    listener(a, qa.a?.let { it.endUs - 40_000 } ?: 0L, t + dA, sb.lengthUs, pa)
                }
                // Nomi su ciascuna metà per tutto il tratto.
                val (ax, ay, bx, by) = if (vertical) listOf(-0.2f, -0.06f, -0.2f, 0.42f) else listOf(-0.42f + 0.12f, 0.38f, 0.08f + 0.12f, 0.38f)
                texts += nameplate(o.nameA, o.captionA, t, seg, ax, ay)
                texts += nameplate(o.nameB, o.captionB, t, seg, bx, by)
                t += seg
            }
        }
        val format = if (vertical) VideoFormat.VERTICAL else if (outW == outH) VideoFormat.SQUARE else VideoFormat.HORIZONTAL
        var p = Project(name = name, clips = clips, texts = texts, layers = layers, format = format, originals = listOf(a, b))
        // Alternata: le risposte riempiono il quadro come le altre clip (riempi o sfondo sfocato secondo l'orientamento).
        if (o.format == InterviewFormat.ALTERNATE) p = p.applyFramePresets(p.clips.filter { !it.isPhoto }.map { it.id }.toSet())
        // Ultima clip: nessuna transizione verso il nulla.
        return p.copy(clips = p.clips.mapIndexed { i, c -> if (i == p.clips.lastIndex) c.copy(transitionOut = null) else c })
    }
}
