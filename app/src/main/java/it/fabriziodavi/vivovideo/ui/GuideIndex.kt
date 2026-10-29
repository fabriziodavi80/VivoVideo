package it.fabriziodavi.vivovideo.ui

import java.text.Normalizer

/** Un argomento della guida: il titolo (riga "## ") e le sue righe. */
data class GuideSection(val title: String, val lines: List<String>)

/** Un argomento trovato dalla ricerca: quante volte compare e la prima riga che la contiene (senza segni **). */
data class GuideHit(val index: Int, val count: Int, val snippet: String)

/**
 * Indice della guida: argomenti, gruppi e ricerca (senza Android, così si prova facilmente).
 * I gruppi si riconoscono dall'inizio del titolo: un argomento nuovo senza gruppo finisce in "Altro".
 */
object GuideIndex {
    private val GROUPS: List<Pair<String, List<String>>> = listOf(
        "Per iniziare" to listOf("Novità", "Primi passi", "Selezionare", "Tagliare e ordinare", "Progetto"),
        "Creare un video" to listOf("Video automatico", "Slideshow automatico", "Fare un tutorial"),
        "Montare" to listOf("Fermo immagine", "Velocità", "Inquadra", "Keyframe", "Foto", "Transizioni", "Effetti", "Colore",
            "Funzioni intelligenti", "Privacy", "Livelli"),
        "Audio" to listOf("Musica", "Taglio a ritmo", "Voce fuori campo", "Mixer", "Audio delle clip", "Jump cut"),
        "Testi" to listOf("Testo", "Sottotitoli", "Titoli di coda", "Font"),
        "Esportare e aiuto" to listOf("Esportare", "Collaudo", "Se qualcosa va storto", "Come provare"),
    )

    /** Titolo della guida (riga "# "), righe prima del primo argomento, argomenti. */
    fun parse(md: String): Triple<String, List<String>, List<GuideSection>> {
        var title = "Guida"
        val intro = mutableListOf<String>()
        val sections = mutableListOf<GuideSection>()
        var cur: Pair<String, MutableList<String>>? = null
        md.lines().forEach { raw ->
            val l = raw.trimEnd()
            when {
                l.startsWith("## ") -> { cur?.let { sections += GuideSection(it.first, it.second) }; cur = l.removePrefix("## ").trim() to mutableListOf() }
                l.startsWith("# ") -> title = l.removePrefix("# ").trim()
                l.isBlank() -> {}
                cur != null -> cur!!.second += l
                else -> intro += l
            }
        }
        cur?.let { sections += GuideSection(it.first, it.second) }
        return Triple(title, intro, sections)
    }

    /** Gruppo di un argomento. */
    fun groupOf(title: String): String =
        GROUPS.firstOrNull { (_, starts) -> starts.any { title.startsWith(it, ignoreCase = true) } }?.first ?: "Altro"

    /** Argomenti divisi per gruppo, nell'ordine dei gruppi (indici nella lista originale). */
    fun grouped(sections: List<GuideSection>): List<Pair<String, List<Int>>> {
        val names = GROUPS.map { it.first } + "Altro"
        return names.map { g -> g to sections.indices.filter { groupOf(sections[it].title) == g } }.filter { it.second.isNotEmpty() }
    }

    /** Minuscolo e senza accenti: "Velocità" si trova anche scrivendo "velocita". */
    fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Testo senza i segni di grassetto. */
    fun plain(s: String): String = s.removePrefix("- ").replace("**", "")

    /** Argomenti che contengono tutte le parole di [query] (nel titolo o nel testo); prima quelli col titolo giusto. */
    fun search(sections: List<GuideSection>, query: String): List<GuideHit> {
        val words = fold(query).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return sections.mapIndexedNotNull { i, s ->
            val all = fold(s.title + "\n" + s.lines.joinToString("\n") { plain(it) })
            if (!words.all { it in all }) return@mapIndexedNotNull null
            val count = words.sumOf { w -> Regex(Regex.escape(w)).findAll(all).count() }
            val line = s.lines.firstOrNull { l -> fold(plain(l)).let { f -> words.any { it in f } } }?.let { plain(it) } ?: ""
            val inTitle = words.any { it in fold(s.title) }
            Triple(i, count + if (inTitle) 1000 else 0, line)
        }.sortedByDescending { it.second }.map { (i, c, l) -> GuideHit(i, c % 1000, l) }
    }

    /** Argomento da aprire per uno strumento dell'editor (null = indice). */
    fun topicFor(tool: Tool?): String? = when (tool) {
        Tool.SPLIT -> "Tagliare e ordinare"
        Tool.SPEED -> "Velocità"
        Tool.DURATION -> "Foto"
        Tool.FRAME, Tool.FORMAT -> "Inquadra"
        Tool.KEYFRAME -> "Keyframe"
        Tool.TRANSITION -> "Transizioni"
        Tool.SLIDESHOW -> "Slideshow automatico"
        Tool.MORE -> "Fermo immagine"
        Tool.MUSIC -> "Musica"
        Tool.TEXT -> "Testo"
        Tool.LAYER -> "Livelli"
        Tool.EFFECTS -> "Effetti"
        Tool.COLOR -> "Colore"
        Tool.EXPORT -> "Esportare"
        null -> null
    }

    /** Indice dell'argomento che comincia con [topic], o -1. */
    fun find(sections: List<GuideSection>, topic: String?): Int =
        if (topic == null) -1 else sections.indexOfFirst { it.title.startsWith(topic, ignoreCase = true) }
}
