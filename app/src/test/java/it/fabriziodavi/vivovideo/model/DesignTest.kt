package it.fabriziodavi.vivovideo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** I modelli grafici: misure, ancoraggi, scritte automatiche, numeri che contano, nomi dei colori. */
class DesignTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        DesignTexts.cal(0).apply { set(y, m - 1, d, h, min, 0) }.timeInMillis

    @Test fun everyDesignIsComplete() {
        assertEquals(52, Designs.all.size)
        assertEquals(Designs.all.size, Designs.all.map { it.id }.toSet().size)
        for (d in Designs.all) {
            assertTrue("${d.id}: almeno una foto", d.slots >= 1)
            val ids = d.texts.map { it.id }
            assertTrue("${d.id}: chiavi delle scritte uniche $ids", ids.size == ids.toSet().size && ids.none { it.isEmpty() })
            assertTrue("${d.id}: colori", d.accents.isNotEmpty())
            // Ogni foto ha il suo posto (una cella vera) in tutti i formati in cui il modello si usa.
            val aspects = CollageAspect.entries.filter { CollageTemplates.fits(d, it) }
            assertTrue("${d.id}: nessun formato", aspects.isNotEmpty())
            for (a in aspects) {
                val cells = DLayout.cells(d, a.ratio)
                assertEquals(d.slots, cells.size)
                cells.forEachIndexed { i, c -> assertTrue("${d.id} $a foto $i: $c", c.w > 0.03f && c.h > 0.02f) }
            }
        }
        // Formati da stampa: tutti i verticali vanno su 10×15 e A4, i quadrati su 1:1, il biglietto su 15×10.
        assertEquals(Designs.all.count { it.orient == DOrient.PORTRAIT }, CollageTemplates.designs(CollageAspect.PAPER).size)
        assertEquals(Designs.all.count { it.orient == DOrient.SQUARE }, CollageTemplates.designs(CollageAspect.SQUARE).size)
        assertTrue(CollageTemplates.designs(CollageAspect.PHOTO_LAND).any { it.id == "x-ticket" })
        // I modelli grafici vanno bene con qualsiasi numero di foto.
        for (n in 1..9) assertEquals(Designs.all.count { it.orient == DOrient.PORTRAIT }, CollageTemplates.forCount(n, CollageAspect.PAPER).count { it.design != null })
    }

    @Test fun anchorsFollowTheSheet() {
        val d = Designs.byId("x-cover")!!
        // A4 (riferimento) e 10×15 (più alto): in alto resta in alto, in basso resta alla stessa distanza dal fondo.
        val w = 794f
        for (h in listOf(1123f, 1191f, 1411f)) {
            val top = DLayout.rect(d, DBox(40f, 40f, 100f, 20f), w, h)
            assertEquals(40f, top[1], 1e-3f)
            val bottom = DLayout.rect(d, DBox(44f, 1020f, 100f, 20f, ay = Anchor.END), w, h)
            assertEquals(1123f - 1020f, h - bottom[1], 1e-3f)
            val full = DLayout.rect(d, DBox(0f, 0f, 794f, 1123f, ay = Anchor.STRETCH), w, h)
            assertEquals(h, full[3], 1e-3f)
            // In proporzione: metà foglio resta metà foglio.
            val half = DLayout.rect(d, DBox(0f, 561.5f, 10f, 561.5f, ay = Anchor.SCALE), w, h)
            assertEquals(h / 2, half[1], 1e-2f); assertEquals(h / 2, half[3], 1e-2f)
        }
        // Più grande o più piccolo: tutto in scala.
        val big = DLayout.rect(d, DBox(40f, 40f, 100f, 20f), 2480f, 3508f)
        assertEquals(40f * 2480f / 794f, big[0], 1e-2f)
    }

    @Test fun textsComeFromThePhotos() {
        val m = DesignMeta(listOf(at(2026, 8, 3, 18, 42), 0L, at(2026, 8, 14, 9, 5)), listOf("Santorini", "", "Naxos"), 3)
        assertEquals("2026", DesignTexts.fill("{ANNO}", m))
        assertEquals("Agosto 2026", DesignTexts.fill("{Mese} {ANNO}", m))
        assertEquals("Lunedì 3 agosto", DesignTexts.fill("{giorno}", m))
        assertEquals("3 · VIII · 2026", DesignTexts.fill("{data_romana}", m))
        assertEquals("03 → 14.08.2026", DesignTexts.fill("{dal_al}", m))
        assertEquals("12", DesignTexts.fill("{giorni}", m))
        assertEquals("18:42", DesignTexts.fill("{ora}", m))
        assertEquals("estate", DesignTexts.fill("{stagione|inverno}", m))
        assertEquals("Santorini · Naxos", DesignTexts.fill("{luoghi}", m))
        assertEquals("Naxos", DesignTexts.fill("{luogo2|Paros}", m))
        assertEquals("Paros", DesignTexts.fill("{luogo1|Paros}", m))
        assertEquals("03 AGO", DesignTexts.fill("{giorno_breve}", m))
        // Senza date né luoghi valgono le parole del modello.
        val none = DesignMeta(now = at(2026, 1, 10))
        assertEquals("Perù → Bali, 12 giorni", DesignTexts.fill("{luogo|Perù} → {luogo2|Bali}, {giorni|12} giorni", none))
        assertEquals("inverno", DesignTexts.fill("{stagione}", none))
        assertEquals("Gennaio 2026", DesignTexts.fill("{Mese} {ANNO}", none))
        // Le scritte cambiate da te vincono; i mesi delle foto nell'album dell'anno.
        val d = Designs.byId("x-year")!!
        val spec = CollageSpec(d.id, CollageAspect.PAPER, listOf(CollageItem("a", takenMs = at(2026, 3, 5)), CollageItem("b")), texts = mapOf("title" to "Il nostro anno"))
        val title = d.texts.first { it.id == "title" }
        assertEquals("Il nostro anno", DesignTexts.text(title, spec, DesignMeta.of(spec.items)))
        val chips = d.els.filterIsInstance<DText>().filter { it.slotMonth >= 0 }
        assertEquals("Mar", DesignTexts.text(chips[0], spec, DesignMeta.of(spec.items)))
        assertEquals("Feb", DesignTexts.text(chips[1], spec, DesignMeta.of(spec.items)))
        assertEquals("Mar", DesignTexts.text(chips[2], spec, DesignMeta.of(spec.items)))
        // Parola con le foto: maiuscola, senza spazi.
        val w = Designs.byId("x-letters")!!.words.first()
        assertEquals("ROMA", DesignTexts.word(w, spec.copy(texts = mapOf("word" to "ro ma")), DesignMeta()))
    }

    @Test fun numbersCountUp() {
        assertEquals("2.340", DesignCount.at("2.340", 1f))
        assertEquals("1.170", DesignCount.at("2.340", 0.5f))
        assertEquals("13,7", DesignCount.at("27,4", 0.5f))
        assertEquals("0,0", DesignCount.at("27,4", 0f))
        assertEquals("2:6", DesignCount.at("5:12", 0.5f))
        assertEquals("455 m", DesignCount.at("1.820 m", 0.25f))
        assertEquals("Giro dei Colli", DesignCount.at("Giro dei Colli", 0.3f))
    }

    @Test fun colorsHaveNames() {
        assertEquals("Latte", ColorNames.name(0xFFCAC3B8.toInt()))
        assertEquals("Salvia", ColorNames.name(0xFF8B9D9A.toInt()))
        assertEquals("Caffè", ColorNames.name(0xFF372720.toInt()))
        assertEquals("Blu", ColorNames.name(0xFF1D4ED8.toInt()))
        assertEquals("Bianco", ColorNames.name(0xFFFAFAFA.toInt()))
        assertEquals("#1D4ED8", ColorNames.hex(0xFF1D4ED8.toInt()))
        assertEquals(Calendar.AUGUST, DesignTexts.cal(at(2026, 8, 1)).get(Calendar.MONTH))
    }

    @Test fun printSizes() {
        // 10×15 a 300 dpi: 1181×1772 pixel; con 3 mm di taglio per lato 1250×1842.
        assertEquals(Triple(1180, 1770, 300), PrintFormat.P10x15.pixels(false))
        assertEquals(Triple(1250, 1842, 300), PrintFormat.P10x15.pixels(true))
        assertEquals(Triple(2480, 3506, 300), PrintFormat.A4.pixels(false))
        // Poster: la risoluzione scende perché il lato lungo resti entro 5400 pixel.
        val (w, h, dpi) = PrintFormat.P50x70.pixels(true)
        assertTrue("$w×$h a $dpi", h <= 5400 && dpi in 150..200 && w > 3800)
        assertEquals(listOf(PrintFormat.S15, PrintFormat.S20, PrintFormat.S30), PrintFormat.forAspect(CollageAspect.SQUARE))
        assertTrue(PrintFormat.forAspect(CollageAspect.PHOTO).containsAll(listOf(PrintFormat.P10x15, PrintFormat.A4, PrintFormat.P50x70)))
    }
}
