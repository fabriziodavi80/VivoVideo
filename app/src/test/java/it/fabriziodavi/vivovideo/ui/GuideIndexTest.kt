package it.fabriziodavi.vivovideo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuideIndexTest {
    private val md = listOf("src/main/assets/guida.md", "app/src/main/assets/guida.md").map(::File).first { it.exists() }.readText()

    @Test fun everyTopicHasAGroupAndText() {
        val (title, _, sections) = GuideIndex.parse(md)
        assertEquals("Guida di VivoVideo", title)
        assertTrue(sections.size >= 25)
        assertTrue(sections.first().title.startsWith("Novità"))
        // Ogni argomento sta in un gruppo vero (se se ne aggiunge uno nuovo, va messo nei gruppi).
        sections.forEach { s ->
            assertTrue("${s.title} senza gruppo", GuideIndex.groupOf(s.title) != "Altro")
            assertTrue("${s.title} vuoto", s.lines.isNotEmpty())
        }
        assertEquals(sections.size, GuideIndex.grouped(sections).sumOf { it.second.size })
    }

    @Test fun searchFindsWordsIgnoringAccentsAndCase() {
        val (_, _, sections) = GuideIndex.parse(md)
        val plates = GuideIndex.search(sections, "Targhe")
        assertTrue(plates.isNotEmpty())
        assertTrue(sections[plates.first().index].title.startsWith("Privacy")) // titolo giusto per primo
        assertTrue(plates.first().snippet.isNotBlank() && !plates.first().snippet.contains("**"))
        // Senza accento si trova lo stesso.
        assertTrue(GuideIndex.search(sections, "velocita").any { sections[it.index].title.startsWith("Velocità") })
        // Più parole: devono esserci tutte.
        assertTrue(GuideIndex.search(sections, "zoom tutorial").all { h -> GuideIndex.fold(sections[h.index].lines.joinToString(" ")).let { "zoom" in it } })
        assertTrue(GuideIndex.search(sections, "x").isEmpty())
        assertTrue(GuideIndex.search(sections, "parolachenonesiste").isEmpty())
    }

    @Test fun editorToolsOpenTheirTopic() {
        val (_, _, sections) = GuideIndex.parse(md)
        Tool.entries.forEach { t ->
            val topic = GuideIndex.topicFor(t)
            if (topic != null) assertTrue("$t → $topic", GuideIndex.find(sections, topic) >= 0)
        }
        assertEquals(-1, GuideIndex.find(sections, null))
    }
}
