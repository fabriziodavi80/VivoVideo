package it.fabriziodavi.vivovideo.model

import it.fabriziodavi.vivovideo.model.Anchor.CENTER as C
import it.fabriziodavi.vivovideo.model.Anchor.END as E
import it.fabriziodavi.vivovideo.model.Anchor.SCALE as K
import it.fabriziodavi.vivovideo.model.Anchor.START as S
import it.fabriziodavi.vivovideo.model.Anchor.STRETCH as F
import it.fabriziodavi.vivovideo.model.DColor.ACC
import it.fabriziodavi.vivovideo.model.DColor.ACC2
import it.fabriziodavi.vivovideo.model.DColor.BLACK
import it.fabriziodavi.vivovideo.model.DColor.WHITE

/**
 * I modelli grafici, provati prima sulla tavola "Collage da stampare" (stesse misure in pixel CSS):
 * 1 Copertina · 2 Reportage · 3 Lettere-foto · 4 Bento viaggio · 5 Bento uscita in bici · 6 Il mio anno ·
 * 7 Diario ritagliato · 8 Adesivi · 9 Moodboard · 10 Duotono · 11 Pop · 12 Vetro · 13 Sport · 14 Romantico ·
 * 15 Carta d'imbarco · 16 Itinerario · 17 Street · 18 Cucina.
 */
object Designs {
    val all: List<Design> by lazy {
        listOf(cover(), reportage(), letters(), bentoTrip(), bentoRide(), year(), scrap(), stickers(), mood(), duotone(), pop(), glass(),
            sport(), love(), ticket(), route(), street(), food())
    }

    fun byId(id: String): Design? = all.firstOrNull { it.id == id }

    private fun b(x: Number, y: Number, w: Number, h: Number, ay: Anchor = S, ax: Anchor = S) =
        DBox(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), ax, ay)

    private fun pts(vararg v: Float) = v.toList().chunked(2).map { Pt(it[0], it[1]) }

    private const val TAPE = 0xB8FFD666L

    // 1 ------------------------------------------------------------------------------------------------
    private fun cover() = Design(
        "x-cover", "Copertina", DesignFamily.MAGAZINE, DOrient.PORTRAIT, 794f, 1123f, 0xFF1A1714,
        accents = listOf(WHITE, 0xFFFFD23F, 0xFFFF5A36, 0xFF111111), motion = CollageMotion.STILL,
        hint = "Una foto a tutta pagina con il titolo enorme: la persona passa davanti alle lettere",
        els = listOf(
            DPhoto(b(0, 0, 794, 1123, F), 0),
            DRect(b(0, 0, 794, 1123, F), 0, grad = listOf(0f to 0x40000000L, 0.28f to 0L, 0.55f to 0L, 1f to 0x99000000L)),
            DText(b(40, 40, 357, 22), "issue", "Numero", "N° {mese_n}", DFont.MONO, 15f, WHITE, track = 0.13f, upper = true),
            DText(b(397, 40, 357, 22), "date", "Data", "{Mese} {ANNO}", DFont.MONO, 15f, WHITE, DAlign.RIGHT, track = 0.13f, upper = true),
            DText(b(30, 58, 740, 216), "title", "Titolo", "{stagione|estate}", DFont.SERIF, 268f, ACC, line = 0.8f, track = -0.037f, behind = 0),
            DText(b(44, 520, 270, 92, C), "line1", "Titolo interno", "Il mare di {mese|settembre}", DFont.SERIF_IT, 46f, WHITE, wrap = true, shadow = true),
            DRect(b(44, 634, 60, 2, C), WHITE),
            DText(b(44, 658, 270, 50, C), "line2", "Sottotitolo", "{foto} foto e un tramonto\nche non finiva più", DFont.SANS, 18f, WHITE, line = 1.35f, shadow = true),
            DText(b(44, 1020, 420, 20, E), "kicker", "Rubrica", "Diario di viaggio", DFont.SANS, 15f, WHITE, track = 0.2f, upper = true),
            DText(b(44, 1046, 420, 20, E), "vol", "Sottotitolo 2", "Vol. 3 — {luogo|Sardegna}", DFont.MONO, 13f, WHITE),
            DText(b(500, 966, 250, 113, E), "num", "Numero grande", "{AA}", DFont.SERIF, 150f, WHITE, DAlign.RIGHT, line = 0.75f, stroke = 2f),
        ),
    )

    // 2 ------------------------------------------------------------------------------------------------
    private fun reportage(): Design {
        val ink = 0xFF0E1A2BL
        return Design(
            "x-report", "Reportage", DesignFamily.MAGAZINE, DOrient.PORTRAIT, 480f, 672f, WHITE,
            accents = listOf(0xFFE8452C, ink, 0xFF1D4ED8), motion = CollageMotion.SLIDE,
            hint = "Pagina di rivista: titolo che morde la foto, due righe di racconto e una foto ad arco",
            els = listOf(
                DPhoto(b(0, 0, 480, 400, F), 0),
                DText(b(24, 24, 400, 16), "kicker", "Rubrica", "Reportage · {luogo|Liguria}", DFont.MONO, 11f, WHITE, track = 0.18f, upper = true, shadow = true),
                DText(b(22, 300, 440, 172, E), "title", "Titolo", "Cinque\nTerre", DFont.SERIF, 104f, ACC, line = 0.82f, track = -0.03f, font2 = DFont.SERIF_IT),
                DText(b(24, 512, 214, 96, E), "body", "Racconto", "Tre giorni a piedi lungo il Sentiero Azzurro, da Riomaggiore a Monterosso, con il mare sempre a sinistra.",
                    DFont.SANS, 13f, ink, line = 1.45f, wrap = true),
                DText(b(24, 614, 214, 16, E), "date", "Date", "{dal_al|03 → 05.06.2026}", DFont.MONO, 11f, ink, track = 0.09f),
                DPhoto(b(260, 430, 196, 196, E), 1, DShape.ARCH),
                DText(b(380, 636, 76, 14, E), "", "", "p. 14", DFont.MONO, 10f, ink, DAlign.RIGHT),
            ),
        )
    }

    // 3 ------------------------------------------------------------------------------------------------
    private fun letters() = Design(
        "x-letters", "Lettere-foto", DesignFamily.MAGAZINE, DOrient.PORTRAIT, 1134f, 1701f, ACC,
        accents = listOf(0xFFF2F4F7, WHITE, 0xFFFFE9D6, 0xFF101828), accents2 = listOf(0xFF13306B, 0xFF111111, WHITE, 0xFFE8452C),
        motion = CollageMotion.CASCADE, hint = "Una parola gigante, con una foto dentro ogni lettera (scrivi la tua: ROMA, 2026, NOI…)",
        els = listOf(
            DText(b(56, 64, 500, 30), "place", "Luogo", "{luogo|Sardegna}", DFont.MONO, 26f, ACC2, track = 0.115f, upper = true),
            DText(b(578, 64, 500, 30), "date", "Data", "{Mese} {ANNO}", DFont.MONO, 26f, ACC2, DAlign.RIGHT, track = 0.115f, upper = true),
            DPhotoWord(b(56, 150, 1022, 1320, F), "word", "Parola", "MARE", DFont.ANTON, 0, gap = 14f),
            DText(b(56, 1545, 620, 92, E), "line", "Frase", "Quattro calette,\nuna per ogni lettera.", DFont.GROTESK, 40f, ACC2, line = 1.1f, bottom = true),
            DText(b(700, 1505, 378, 132, E), "list", "Elenco", "Cala Goloritzé\nCala Mariolu\nCala Luna\nCapo Testa", DFont.MONO, 22f, ACC2, DAlign.RIGHT, line = 1.5f, bottom = true),
        ),
    )

    // 4 ------------------------------------------------------------------------------------------------
    private fun bentoTrip(): Design {
        fun x(c: Int) = 24 + c * 180
        val els = listOf(
            DPhoto(b(x(0), x(0), 348, 348), 0, radius = 28f),
            DText(b(38, 330, 200, 28), "chip", "Etichetta", "{luogo0|Oia}", DFont.GROTESK, 14f, BLACK, pill = WHITE, pad = 14f),
            DRect(b(x(2), x(0), 348, 168), ACC, radius = 28f),
            DText(b(408, 44, 300, 16), "region", "Zona", "Cicladi", DFont.MONO, 12f, WHITE, track = 0.17f, upper = true),
            DText(b(408, 107, 316, 66), "title", "Titolo", "{luogo|Grecia}", DFont.GROTESK, 76f, WHITE, line = 0.85f, track = -0.04f, bottom = true),
            DRect(b(x(2), x(1), 168, 168), WHITE, radius = 28f),
            DText(b(404, 220, 140, 74), "days", "Giorni", "{giorni|12}", DFont.GROTESK, 92f, BLACK, line = 0.8f, track = -0.043f, count = true),
            DText(b(404, 336, 140, 20), "daysL", "Sotto i giorni", "giorni", DFont.GROTESK_MED, 15f, BLACK),
            DPhoto(b(x(3), x(1), 168, 348), 1, radius = 28f),
            DPhoto(b(x(0), x(2), 348, 168), 2, radius = 28f),
            DPhoto(b(x(2), x(2), 168, 168), 3, radius = 28f),
            DPhoto(b(x(0), x(3), 168, 168), 4, radius = 28f),
            DRect(b(x(1), x(3), 168, 168), 0xFFFFD23F, radius = 28f),
            DText(b(224, 580, 130, 20), "tempL", "Sopra la temperatura", "acqua", DFont.GROTESK_MED, 15f, BLACK),
            DText(b(224, 650, 130, 66), "temp", "Temperatura", "26°", DFont.GROTESK, 80f, BLACK, line = 0.8f, track = -0.04f, count = true, bottom = true),
            DRect(b(x(2), x(3), 348, 168), 0xFF0F172A, radius = 28f),
            DText(b(408, 582, 300, 16), "month", "Mese", "{Mese} {ANNO}", DFont.MONO, 12f, WHITE, track = 0.17f, upper = true),
            DText(b(408, 650, 300, 64), "places", "Luoghi", "{luoghi|Santorini · Naxos · Paros · Milos}", DFont.GROTESK, 30f, WHITE, wrap = true, bottom = true),
        )
        return Design("x-bento-trip", "Bento viaggio", DesignFamily.BENTO, DOrient.SQUARE, 756f, 756f, 0xFFE9EEF4,
            accents = listOf(0xFF1D4ED8, 0xFFE8452C, 0xFF0F766E, 0xFF7C3AED), els = els, motion = CollageMotion.CASCADE,
            hint = "Il viaggio in riquadri: luogo, giorni e mese li prende dalle foto")
    }

    // 5 ------------------------------------------------------------------------------------------------
    private fun bentoRide(): Design {
        val ink = 0xFF121212L
        fun label(x: Int, y: Int, id: String, t: String, c: Long = ink) = DText(b(x + 18, y + 14, 140, 14), id, "Etichetta", t, DFont.MONO, 11f, c, track = 0.18f, upper = true)
        val els = listOf(
            DPhoto(b(24, 24, 528, 348), 0, radius = 24f),
            DRect(b(24, 24, 528, 348), 0, radius = 24f, grad = listOf(0.45f to 0L, 1f to 0x8C000000L)),
            DText(b(46, 287, 480, 16), "date", "Data", "{giorno}", DFont.MONO, 12f, WHITE, track = 0.17f, upper = true),
            DText(b(46, 307, 480, 47), "title", "Titolo", "Giro dei Colli", DFont.GROTESK, 52f, WHITE, line = 0.9f, track = -0.04f),
            DRect(b(564, 24, 168, 168), ACC, radius = 24f),
            label(564, 24, "l1", "Distanza · km"),
            DText(b(582, 127, 140, 51), "km", "Distanza", "142", DFont.GROTESK, 64f, ink, line = 0.8f, track = -0.045f, count = true, bottom = true),
            DRect(b(564, 204, 168, 168), ink, radius = 24f),
            label(564, 204, "l2", "Dislivello · m", WHITE),
            DText(b(582, 318, 140, 40), "up", "Dislivello", "2.340", DFont.GROTESK, 50f, WHITE, line = 0.8f, track = -0.04f, count = true, bottom = true),
            DRect(b(24, 384, 348, 168), WHITE, radius = 24f),
            label(24, 384, "l3", "Altimetria"),
            DText(b(200, 398, 154, 14), "max", "Quota massima", "max 1.214 m", DFont.MONO, 11f, ink, DAlign.RIGHT, track = 0.1f),
            DPath(b(38, 446, 320, 92), "M0 82 L18 78 L34 70 L52 72 L70 55 L86 40 L98 22 L110 30 L124 46 L140 50 L156 36 L170 18 L182 8 L196 24 L212 44 L230 52 L246 60 L262 58 L280 72 L300 80 L300 90 L0 90 Z",
                300f, 90f, fill = ACC),
            DPath(b(38, 446, 320, 92), "M0 82 L18 78 L34 70 L52 72 L70 55 L86 40 L98 22 L110 30 L124 46 L140 50 L156 36 L170 18 L182 8 L196 24 L212 44 L230 52 L246 60 L262 58 L280 72 L300 80",
                300f, 90f, stroke = ink, width = 2.5f, draw = true),
            DPhoto(b(384, 384, 168, 168), 1, radius = 24f),
            DRect(b(564, 384, 168, 168), WHITE, radius = 24f),
            label(564, 384, "l4", "Tempo"),
            DText(b(582, 493, 140, 45), "time", "Tempo", "5:12", DFont.GROTESK, 56f, ink, line = 0.8f, track = -0.035f, count = true, bottom = true),
            DPhoto(b(24, 564, 168, 168), 2, radius = 24f),
            DRect(b(204, 564, 348, 168), WHITE, radius = 24f),
            DText(b(224, 580, 300, 14), "l5", "Etichetta", "Con", DFont.MONO, 11f, ink, track = 0.18f, upper = true),
            DText(b(224, 652, 310, 64), "with", "Con chi", "Luca, Marta\ne il vento contro", DFont.GROTESK, 30f, ink, line = 1.05f, bottom = true),
            DRect(b(564, 564, 168, 168), ACC, radius = 24f),
            label(564, 564, "l6", "Media · km/h"),
            DText(b(582, 678, 140, 40), "avg", "Media", "27,3", DFont.GROTESK, 50f, ink, line = 0.8f, track = -0.04f, count = true, bottom = true),
        )
        return Design("x-bento-ride", "Uscita in bici", DesignFamily.BENTO, DOrient.SQUARE, 756f, 756f, 0xFFF1F1EC,
            accents = listOf(0xFFFF6B1A, 0xFFFFD23F, 0xFF5EEAD4, 0xFFF472B6), els = els, motion = CollageMotion.CASCADE,
            hint = "Bici, corsa, montagna: chilometri, dislivello e tempo che contano nel video, il profilo che si disegna")
    }

    // 6 ------------------------------------------------------------------------------------------------
    private fun year(): Design {
        val els = arrayListOf<DEl>(
            DText(b(40, 40, 560, 226), "year", "Anno", "{ANNO}", DFont.GROTESK, 250f, ACC, line = 0.74f, track = -0.056f, count = true, bottom = true),
            DText(b(560, 212, 194, 26), "title", "Titolo", "Il mio anno", DFont.GROTESK, 26f, BLACK, DAlign.RIGHT),
            DText(b(560, 244, 194, 16), "sub", "Sottotitolo", "12 mesi · 12 foto", DFont.MONO, 12f, BLACK, DAlign.RIGHT, line = 1.3f, track = 0.08f),
        )
        val months = DesignTexts.monthShort
        for (i in 0 until 12) {
            val x = 40 + (i % 3) * 242; val y = 282f + (i / 3) * 203.25f
            els += DPhoto(b(x, y, 230, 191.25f, K), i, radius = 18f)
            els += DText(b(x + 10, y + 16, 90, 13, K), "", "", months[i], DFont.GROTESK, 13f, BLACK, pill = WHITE, pad = 11f, slotMonth = i)
        }
        return Design("x-year", "Il mio anno", DesignFamily.BENTO, DOrient.PORTRAIT, 794f, 1123f, WHITE,
            accents = listOf(0xFF111111, 0xFFE8452C, 0xFF1D4ED8, 0xFF0F766E), els = els, motion = CollageMotion.CASCADE,
            hint = "Dodici foto, una per mese: se le foto hanno la data, il mese giusto lo scrive da solo")
    }

    // 7 ------------------------------------------------------------------------------------------------
    private fun scrap(): Design {
        val blue = 0xFF2B59FFL
        return Design(
            "x-scrap", "Diario ritagliato", DesignFamily.SCRAP, DOrient.PORTRAIT, 794f, 1123f, 0xFFFBFBF8,
            accents = listOf(0xFFFF3B30, blue, 0xFF111111, 0xFF16A34A), motion = CollageMotion.DROP,
            hint = "Foto strappate e attaccate col nastro su carta a quadretti, con le scritte a mano",
            els = listOf(
                DPattern(b(0, 0, 794, 1123, F), DPatternKind.GRID, 0x1A2B59FF, 24f),
                DPhoto(b(56, 72, 450, 330), 0, DShape.TORN_BOTTOM, rot = -3f, border = 10f, bottomPad = 20f, shadow = 10f),
                DPhoto(b(526, 128, 206, 246), 1, rot = 6f, border = 10f, bottomPad = 30f, shadow = 12f),
                DText(b(500, 384, 226, 30), "cap1", "Didascalia", "{luogo1|Ulun Danu}", DFont.HAND, 26f, 0xFF222222, DAlign.CENTER, rot = 6f),
                DRect(b(562, 98, 120, 34), TAPE, rot = -8f),
                DText(b(62, 470, 680, 94), "title", "Titolo", "{luogo|Perù} → {luogo2|Bali}", DFont.HAND, 104f, blue, line = 0.9f),
                DText(b(62, 564, 520, 60), "sub", "Sottotitolo", "il giro lungo", DFont.HAND, 60f, ACC),
                DPath(b(66, 612, 310, 30), "M4 18 C60 6 120 26 180 14 C240 2 270 10 296 20", 300f, 30f, stroke = ACC, width = 5f, draw = true),
                DPath(b(470, 410, 140, 120), "M8 110 C40 70 80 60 120 18 M100 14 L122 16 L118 38", 140f, 120f, stroke = blue, width = 4f, draw = true),
                DPhoto(b(68, 684, 280, 378), 2, DShape.TORN_TOP, rot = -2f, border = 10f, shadow = 10f),
                DPhoto(b(326, 648, 174, 174), 3, DShape.CIRCLE, rot = -10f, border = 8f, shadow = 12f),
                DPhoto(b(448, 670, 280, 360), 4, rot = 3f, border = 10f, shadow = 14f),
                DRect(b(404, 650, 90, 34), TAPE, rot = -40f),
                DRect(b(664, 1010, 90, 34), TAPE, rot = -40f),
                DText(b(392, 884, 160, 18), "day", "Etichetta", "DAY {giorni|07}", DFont.MONO_BOLD, 15f, WHITE, track = 0.13f, rot = -4f, pill = BLACK, pad = 12f, square = true),
                DText(b(372, 936, 320, 50), "note", "Nota", "e poi {luogo4|Parigi}!", DFont.HAND, 40f, ACC, rot = -6f),
            ),
        )
    }

    // 8 ------------------------------------------------------------------------------------------------
    private fun stickers() = Design(
        "x-stickers", "Adesivi", DesignFamily.SCRAP, DOrient.PORTRAIT, 384f, 576f, ACC,
        accents = listOf(0xFF2F5BFF, 0xFFFF5A36, 0xFF16A34A, 0xFF111111), motion = CollageMotion.DROP,
        hint = "Foto come adesivi col bordo bianco su un colore pieno, con stella e bollino",
        els = listOf(
            DPhoto(b(31, 43, 192, 232), 0, radius = 13f, rot = -7f, border = 9f, shadow = 10f),
            DPhoto(b(185, 159, 172, 142), 1, radius = 13f, rot = 8f, border = 9f, shadow = 10f),
            DPhoto(b(69, 309, 182, 132), 2, DShape.PILL, rot = -3f, border = 9f, shadow = 10f),
            DPath(b(250, 22, 104, 104), "M50 2 L61 34 L95 34 L68 55 L78 90 L50 69 L22 90 L32 55 L5 34 L39 34 Z", 100f, 100f, stroke = WHITE, width = 5f, fill = 0xFFFFD23F, rot = 12f),
            DText(b(250, 62, 104, 26), "star", "Stella", "wow", DFont.HAND, 23f, BLACK, DAlign.CENTER, rot = 12f),
            DRect(b(262, 336, 96, 96), 0xFFFF7AB8, circle = true, rot = -12f),
            DRect(b(262, 336, 96, 96), WHITE, circle = true, stroke = 5f),
            DText(b(262, 362, 96, 44), "badge", "Bollino", "{stagione}\n'{AA}", DFont.HAND, 24f, BLACK, DAlign.CENTER, line = 0.9f, rot = -12f),
            DText(b(24, 464, 336, 30, E), "sub", "Sottotitolo", "tre giorni, zero piani", DFont.HAND, 30f, WHITE),
            DText(b(24, 494, 336, 56, E), "title", "Titolo", "Weekend!", DFont.GROTESK, 62f, WHITE, line = 0.9f, track = -0.03f),
        ),
    )

    // 9 ------------------------------------------------------------------------------------------------
    private fun mood(): Design {
        val ink = 0xFF372720L
        return Design(
            "x-mood", "Moodboard", DesignFamily.SCRAP, DOrient.PORTRAIT, 480f, 672f, WHITE,
            accents = listOf(ink, 0xFF111111, 0xFF1F2A44), motion = CollageMotion.SLIDE,
            hint = "Quattro foto e la palette: i colori (con nome e codice) li prende dalle tue foto",
            els = listOf(
                DText(b(28, 28, 330, 50), "title", "Titolo", "{Mese|Settembre} lento", DFont.SERIF_IT, 50f, ACC),
                DText(b(352, 46, 100, 14), "tag", "Etichetta", "Mood {mese_n|09}", DFont.MONO, 10f, ACC, DAlign.RIGHT, track = 0.2f, upper = true),
                DPhoto(b(28, 90, 207, 298), 0),
                DPhoto(b(245, 90, 207, 144), 1),
                DPhoto(b(245, 244, 207, 144), 2),
                DPhoto(b(28, 398, 424, 132, F), 3),
                DText(b(28, 546, 424, 14, E), "palL", "Sopra la palette", "Palette presa dalle foto", DFont.MONO, 10f, ACC, track = 0.2f, upper = true),
                DPalette(b(28, 570, 424, 74, E), 5, ACC),
            ),
        )
    }

    // 10 -----------------------------------------------------------------------------------------------
    private fun duotone(): Design {
        val cols = listOf("Dove" to "{luogo|Fuerteventura}", "Spot" to "El Cotillo", "Quando" to "{Mese} {ANNO}", "Onda" to "2,1 m")
        val els = arrayListOf<DEl>(
            DPhoto(b(56, 56, 1022, 1120, F), 0, filter = DFilter.DUOTONE),
            DText(b(40, 1010, 1060, 400, E), "title", "Titolo", "SURF", DFont.ANTON, 470f, ACC2, line = 0.8f, track = -0.013f, upper = true),
            DRect(b(56, 1531, 1022, 3, E), ACC2),
        )
        cols.forEachIndexed { i, (l, v) ->
            val x = 56 + i * 261.5f
            els += DText(b(x, 1552, 237, 20, E), "l$i", "Etichetta ${i + 1}", l, DFont.SANS, 16f, ACC2, track = 0.125f, upper = true)
            els += DText(b(x, 1580, 237, 70, E), "v$i", "Valore ${i + 1}", v, DFont.GROTESK_MED, 30f, ACC2, line = 1.1f, wrap = true)
        }
        return Design("x-duo", "Duotono", DesignFamily.POSTER, DOrient.PORTRAIT, 1134f, 1701f, ACC,
            accents = listOf(0xFFFF6A3D, 0xFFFFD23F, 0xFFF9A8D4, 0xFFA7F3D0), accents2 = listOf(0xFF10206B, 0xFF111111, 0xFF7C2D12, 0xFF14532D),
            els = els, motion = CollageMotion.WIPE, hint = "Una foto in due colori, il titolo enorme e le informazioni in colonna, come un poster svizzero")
    }

    // 11 -----------------------------------------------------------------------------------------------
    private fun pop() = Design(
        "x-pop", "Pop", DesignFamily.POSTER, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFFFFD60A, 0xFFFF8FAB, 0xFF7DD3FC, 0xFFBEF264), motion = CollageMotion.CASCADE,
        hint = "Foto in bianco e nero dentro forme, una sola a colori, su un colore acceso",
        els = listOf(
            DText(b(44, 40, 520, 260), "title", "Titolo", "fuori\nporta", DFont.ARCHIVO, 156f, BLACK, line = 0.82f, track = -0.045f),
            DPhoto(b(434, 60, 400, 400), 0, DShape.CIRCLE, filter = DFilter.BW),
            DPhoto(b(44, 400, 230, 460), 1, DShape.PILL, filter = DFilter.BW),
            DPhoto(b(310, 500, 440, 330), 2, DShape.ARCH, outline = 12f),
            DPhoto(b(310, 860, 210, 210), 3, filter = DFilter.BW),
            DPhoto(b(550, 860, 200, 200), 4, DShape.CIRCLE, filter = DFilter.BW),
            DRect(b(196, 800, 140, 140), BLACK, circle = true),
            DText(b(196, 846, 140, 48), "sticker", "Bollino", "{ANNO}", DFont.ARCHIVO, 34f, ACC, DAlign.CENTER, rot = -14f),
            DText(b(44, 1007, 300, 68, E), "caption", "Didascalia", "{foto} foto\n1 lago\n0 Wi-Fi", DFont.MONO, 15f, BLACK, line = 1.5f, bottom = true),
        ),
    )

    // 12 -----------------------------------------------------------------------------------------------
    private fun glass() = Design(
        "x-glass", "Vetro", DesignFamily.POSTER, DOrient.PORTRAIT, 384f, 576f, 0xFF8B7D74,
        accents = listOf(WHITE, 0xFFFFE4C7, 0xFF111111), motion = CollageMotion.SLIDE,
        hint = "La foto sfocata fa da sfondo, sopra una lastra di vetro con la foto nitida",
        els = listOf(
            DPhoto(b(-60, -60, 504, 696, F), 0, filter = DFilter.BLUR),
            DRect(b(0, 0, 384, 576, F), 0x1F28140A),
            DRect(b(28, 34, 328, 508, F), 0x29FFFFFF, radius = 30f, shadow = 20f),
            DRect(b(28, 34, 328, 508, F), 0x73FFFFFF, radius = 30f, stroke = 1f),
            DPhoto(b(42, 48, 300, 340, F), 0, radius = 20f),
            DText(b(50, 404, 284, 44, E), "title", "Titolo", "L'ultima alba", DFont.SERIF, 44f, ACC, line = 0.95f),
            DText(b(50, 452, 140, 18, E), "place", "Luogo", "{luogo|Tarifa}", DFont.SANS, 13f, ACC, track = 0.08f),
            DText(b(194, 452, 140, 18, E), "time", "Ora", "{ora|06:52}", DFont.SANS, 13f, ACC, DAlign.RIGHT, track = 0.08f),
        ),
    )

    // 13 -----------------------------------------------------------------------------------------------
    private fun sport(): Design {
        val stats = listOf("Dislivello" to "1.820 m", "Tempo" to "4:38", "Passo" to "10'09\"", "Quota max" to "2.412 m")
        val els = arrayListOf<DEl>(
            DPhoto(b(0, 0, 794, 700, F), 0, poly = pts(0f, 0f, 1f, 0f, 1f, 0.76f, 0f, 1f)),
            DRect(b(0, 0, 794, 280), 0, grad = listOf(0f to 0x73000000L, 1f to 0L)),
            DRect(b(0, 520, 794, 240, E), ACC, poly = pts(0f, 0.75f, 1f, 0f, 1f, 0.28f, 0f, 1f)),
            DText(b(40, 36, 520, 18), "kicker", "Rubrica", "Trail · {data}", DFont.MONO_BOLD, 14f, WHITE, track = 0.21f, upper = true),
            DText(b(40, 62, 600, 250), "title", "Titolo", "Alta\nVia", DFont.ANTON, 150f, WHITE, line = 0.82f, track = -0.013f, upper = true),
            DText(b(36, 712, 400, 200, E), "km", "Numero grande", "27,4", DFont.ANTON, 250f, BLACK, line = 0.8f, track = -0.024f, count = true),
            DText(b(446, 820, 110, 60, E), "unit", "Unità", "KM", DFont.ANTON, 54f, ACC),
            DPhoto(b(564, 690, 190, 132, E), 1, radius = 14f),
            DPhoto(b(564, 834, 190, 132, E), 2, radius = 14f),
            DRect(b(40, 981, 714, 102, E), 0xFF111111, radius = 20f),
        )
        stats.forEachIndexed { i, (l, v) ->
            val x = 66 + i * 169.5f
            els += DText(b(x, 1001, 150, 14, E), "l$i", "Etichetta ${i + 1}", l, DFont.MONO, 11f, WHITE, track = 0.18f, upper = true)
            els += DText(b(x, 1019, 150, 44, E), "s$i", l, v, DFont.ANTON, 44f, if (i == 3) ACC else WHITE, count = true)
        }
        return Design("x-sport", "Sport", DesignFamily.THEMES, DOrient.PORTRAIT, 794f, 1123f, 0xFFF4F4F1,
            accents = listOf(0xFFFF5B14, 0xFF2F5BFF, 0xFFE11D48, 0xFFFFD60A), els = els, motion = CollageMotion.SLIDE,
            hint = "Trail, bici, corsa: taglio obliquo, il numero gigante e la barra dei dati (i numeri contano nel video)")
    }

    // 14 -----------------------------------------------------------------------------------------------
    private fun love(): Design {
        val ink = 0xFF3B2A2AL; val rose = 0xFFB0485BL
        return Design(
            "x-love", "Romantico", DesignFamily.THEMES, DOrient.PORTRAIT, 480f, 672f, ACC,
            accents = listOf(0xFFF8EFEC, WHITE, 0xFFEEF2EA, 0xFFF3EEE6), motion = CollageMotion.WIPE,
            hint = "Matrimonio, anniversario, noi due: foto ad arco, i nomi e la data in numeri romani",
            els = listOf(
                DPhoto(b(95, 62, 290, 330, C), 0, DShape.ARCH),
                DText(b(300, 86, 170, 46, C), "hand", "Scritta a mano", "per sempre", DFont.HAND, 40f, rose, rot = -8f),
                DText(b(20, 406, 440, 56, C), "names", "Nomi", "Giulia & Marco", DFont.SERIF, 52f, ink, DAlign.CENTER),
                DText(b(20, 474, 440, 16, C), "date", "Data", "{data_romana}", DFont.SANS, 12f, ink, DAlign.CENTER, track = 0.42f),
                DRect(b(220, 502, 40, 1, C), ink),
                DPhoto(b(82, 517, 96, 96, C), 1, DShape.CIRCLE),
                DPhoto(b(192, 517, 96, 96, C), 2, DShape.CIRCLE),
                DPhoto(b(302, 517, 96, 96, C), 3, DShape.CIRCLE),
            ),
        )
    }

    // 15 -----------------------------------------------------------------------------------------------
    private fun ticket(): Design {
        val ink = 0xFF0B2545L; val grey = 0xFF5B6B80L
        val fields = listOf(Triple("Data", "{giorno_breve}", 0), Triple("Imbarco", "{ora|06:40}", 1), Triple("Posto", "12A", 2), Triple("Passeggeri", "2", 3))
        val els = arrayListOf<DEl>(
            DRect(b(22, 22, 532, 340, F, F), WHITE, radius = 20f, shadow = 12f),
            DPhoto(b(22, 22, 300, 340, F, F), 0, radius = 20f, corners = 1 or 8),
            DText(b(40, 296, 280, 52, E), "title", "Titolo", "{luogo|Santorini}", DFont.SERIF, 54f, WHITE, line = 0.9f, bottom = true, shadow = true),
            DPath(b(322, 36, 2, 312, F, E), "M1 0 L1 312", 2f, 312f, stroke = 0xFFB9C6D6, width = 2f, dash = listOf(6f, 6f)),
            DText(b(344, 40, 190, 14, S, E), "", "", "Carta d'imbarco", DFont.MONO, 10f, ink, track = 0.2f, upper = true),
            DText(b(344, 58, 190, 40, S, E), "route", "Tratta", "FCO → JTR", DFont.MONO_BOLD, 36f, ink, track = -0.026f),
            DText(b(344, 100, 190, 16, S, E), "cities", "Città", "Roma — {luogo|Santorini}", DFont.MONO, 11f, ink),
            DBarcode(b(344, 310, 190, 34, E, E), ink),
        )
        fields.forEach { (l, v, i) ->
            val x = 344 + (i % 2) * 101; val y = 170 + (i / 2) * 46
            els += DText(b(x, y, 95, 12, S, E), "", "", l, DFont.MONO, 9f, grey, track = 0.11f, upper = true)
            els += DText(b(x, y + 13, 95, 20, S, E), "f$i", l, v, DFont.MONO_BOLD, 15f, ink)
        }
        return Design("x-ticket", "Carta d'imbarco", DesignFamily.THEMES, DOrient.LANDSCAPE, 576f, 384f, ACC,
            accents = listOf(0xFFD6E6F5, 0xFFFDE2C8, 0xFFE2F0D9, 0xFF0B2545), els = els, motion = CollageMotion.SLIDE,
            hint = "Il viaggio come un biglietto aereo: la meta la prende dal luogo della foto")
    }

    // 16 -----------------------------------------------------------------------------------------------
    private fun route(): Design {
        val ink = 0xFF18322AL
        val stops = listOf(
            listOf(98, 338, 370, 380, 0, "Roma", "2 giorni"), listOf(628, 528, 270, 580, 1, "Cinque Terre", "3 giorni"),
            listOf(158, 838, 430, 880, 0, "Parigi", "4 giorni"), listOf(668, 1088, 300, 1140, 1, "Berlino", "2 giorni"),
        )
        val els = arrayListOf<DEl>(
            DText(b(64, 60, 800, 136), "title", "Titolo", "Interrail", DFont.GROTESK, 170f, ink, line = 0.8f, track = -0.047f, bottom = true),
            DText(b(859, 160, 200, 30), "year", "Anno", "{ANNO}", DFont.MONO_BOLD, 24f, ink, DAlign.RIGHT),
            DPath(b(0, 0, 1123, 1587, F), "M215 455 C500 420 700 500 745 645 C790 800 400 780 275 955 C170 1100 600 1150 785 1205", 1123f, 1587f,
                stroke = ACC2, width = 7f, dash = listOf(1f, 20f), draw = true),
        )
        stops.forEachIndexed { i, st ->
            val px = st[0] as Int; val py = st[1] as Int; val lx = st[2] as Int; val ly = st[3] as Int; val right = st[4] == 1
            els += DPhoto(b(px, py, 234, 234, K), i, DShape.CIRCLE, border = 8f, shadow = 10f)
            val al = if (right) DAlign.RIGHT else DAlign.LEFT
            val w = if (right) 320 else 400
            els += DText(b(lx, ly, w, 22, K), "", "", "0${i + 1}", DFont.MONO_BOLD, 18f, ACC2, al)
            els += DText(b(lx, ly + 26, w, 52, K), "c$i", "Tappa ${i + 1}", "{luogo$i|${st[5]}}", DFont.GROTESK, 56f, ink, al, line = 0.9f)
            els += DText(b(lx, ly + 82, w, 20, K), "d$i", "Giorni tappa ${i + 1}", st[6] as String, DFont.MONO, 16f, ink, al)
        }
        els += DRect(b(64, 1481, 995, 3, E), ink)
        els += DText(b(64, 1502, 330, 26, E), "f1", "Piede sinistra", "{n_luoghi|4} città", DFont.MONO, 20f, ink)
        els += DText(b(396, 1502, 330, 26, E), "f2", "Piede centro", "{giorni|11} giorni", DFont.MONO, 20f, ink, DAlign.CENTER)
        els += DText(b(729, 1502, 330, 26, E), "f3", "Piede destra", "2.134 km in treno", DFont.MONO, 20f, ink, DAlign.RIGHT)
        return Design("x-route", "Itinerario", DesignFamily.THEMES, DOrient.PORTRAIT, 1123f, 1587f, ACC,
            accents = listOf(0xFFE6EEE3, WHITE, 0xFFF6E7D8, 0xFFDBE7F5), accents2 = listOf(0xFFE8452C, 0xFF1D4ED8, ink),
            els = els, motion = CollageMotion.CASCADE, hint = "Le tappe del viaggio unite dal percorso a puntini (che si disegna nel video); i nomi li prende dalle foto")
    }

    // 17 -----------------------------------------------------------------------------------------------
    private fun street(): Design {
        val bg = 0xFFECECEAL; val strip = 0xFF141414L; val num = 0xFFFF9A3CL
        val els = arrayListOf<DEl>(
            DText(b(40, 40, 400, 64), "title", "Titolo", "Notturno", DFont.MONO_BOLD, 58f, BLACK, track = -0.034f, upper = true),
            DText(b(440, 62, 314, 20), "roll", "Sottotitolo", "Roll 03 · Ilford HP5", DFont.MONO, 13f, BLACK, DAlign.RIGHT, track = 0.15f, upper = true),
        )
        for ((row, y0, rot) in listOf(Triple(0, 140, -1f), Triple(1, 380, 0.6f))) {
            els += DRect(b(40, y0, 704, 216), strip, rot = rot)
            els += DPattern(b(40, y0, 704, 216), DPatternKind.SPROCKETS, bg, 24f, rot = rot)
            for (k in 0 until 3) {
                els += DPhoto(b(56 + k * 228, y0 + 30, 216, 156), row * 3 + k, rot = rot, filter = DFilter.BW)
                els += DText(b(60 + k * 228, y0 - 2, 40, 14), "", "", "${12 + row * 3 + k}", DFont.MONO_BOLD, 11f, num)
            }
        }
        els += DPath(b(270, 382, 280, 210), "M140 12 C220 6 272 50 268 108 C264 170 200 204 132 200 C60 196 10 160 12 104 C14 52 66 18 150 18", 280f, 210f, stroke = ACC, width = 6f, draw = true)
        els += DPhoto(b(112, 656, 428, 340), 4, rot = -2f, border = 16f, bottomPad = 30f, shadow = 16f, filter = DFilter.BW)
        els += DText(b(118, 1006, 428, 16), "caption", "Didascalia", "{luogo4|Milano}, {ora|02:14} — fotogramma 16", DFont.MONO, 13f, BLACK, rot = -2f)
        els += DText(b(590, 700, 200, 150), "note", "Nota", "questa!\nstampare\n30×40", DFont.HAND, 46f, ACC, line = 0.95f, rot = -6f)
        return Design("x-street", "Street, provini", DesignFamily.THEMES, DOrient.PORTRAIT, 794f, 1123f, bg,
            accents = listOf(0xFFFF2D2D, 0xFFFFD60A, 0xFF2F5BFF), els = els, motion = CollageMotion.CASCADE, grain = true,
            hint = "Città di notte in bianco e nero: due strisce di pellicola, il fotogramma scelto cerchiato e stampato grande")
    }

    // 18 -----------------------------------------------------------------------------------------------
    private fun food(): Design {
        val cream = 0xFFF6EFE2L
        val menu = listOf("Pakora di verdure" to "Antipasto", "Biryani di pollo" to "Primo", "Dal makhani" to "Secondo", "Chai alle spezie" to "Fine")
        val els = arrayListOf<DEl>(
            DPhoto(b(366, -40, 480, 480), 0, DShape.CIRCLE),
            DText(b(40, 40, 330, 16), "kicker", "Rubrica", "Cena da noi · {data}", DFont.MONO, 12f, ACC, track = 0.25f, upper = true),
            DText(b(40, 66, 360, 130), "title", "Titolo", "Sabato sera,\ncucina indiana", DFont.SERIF, 70f, cream, line = 0.92f, font2 = DFont.SERIF_IT, color2 = ACC),
        )
        menu.forEachIndexed { i, (dish, course) ->
            val y = 410 + i * 70
            els += DPhoto(b(40, y, 54, 54, C), 1 + i, DShape.CIRCLE)
            els += DText(b(108, y + 8, 214, 38, C), "m$i", "Piatto ${i + 1}", dish, DFont.SERIF, 30f, cream)
            els += DPath(b(328, y + 34, 76, 2, C), "M0 1 L76 1", 76f, 2f, stroke = 0x80F6EFE2, width = 2f, dash = listOf(2f, 6f))
            els += DText(b(380, y + 20, 120, 14, C), "c$i", "Portata ${i + 1}", course, DFont.MONO, 11f, cream, DAlign.RIGHT, track = 0.18f, upper = true)
        }
        els += DPhoto(b(546, 496, 170, 220, E), 5, DShape.ARCH)
        return Design("x-food", "Cucina", DesignFamily.THEMES, DOrient.SQUARE, 756f, 756f, ACC2,
            accents = listOf(0xFFF2A541, 0xFFFF7A59, 0xFFE9C46A), accents2 = listOf(0xFF12372A, 0xFF3B1F2B, 0xFF1F2A44, 0xFF2B2B2B),
            els = els, motion = CollageMotion.CASCADE, hint = "Una cena, una ricetta, un ristorante: il piatto grande e il menù con le foto")
    }
}
