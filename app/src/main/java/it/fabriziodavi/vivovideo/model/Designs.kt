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
        listOf(wedding(), saveDate(), booth(), flash(), balloons(), disco(), vip(), laurea(), baby(), badge(), speaker(),
            bib(), topo(), speed(), mud(), hud(), record(), sequence(), skipass(),
            paperTrip(), paperNews(), paperLove(), paperRecipe(), paperWall(), paperRansom(), paperSticker(), paperRiso(),
            notes(), chrome(), leak(), blue(), pearls(), thenNow(), windows(), dump(),
            cover(), reportage(), letters(), bentoTrip(), bentoRide(), year(), scrap(), stickers(), mood(), duotone(), pop(), glass(),
            sport(), love(), ticket(), route(), street(), food())
    }

    fun byId(id: String): Design? = all.firstOrNull { it.id == id }

    private fun b(x: Number, y: Number, w: Number, h: Number, ay: Anchor = S, ax: Anchor = S) =
        DBox(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), ax, ay)

    private fun pts(vararg v: Float) = v.toList().chunked(2).map { Pt(it[0], it[1]) }

    private const val TAPE = 0xB8FFD666L

    /** Riquadro che sale e scende in proporzione al foglio (i collage di carta si stendono su tutta la pagina). */
    private fun bk(x: Number, y: Number, w: Number, h: Number, ax: Anchor = S) = b(x, y, w, h, K, ax)

    private const val SPARKLE = "M50 0 C54 36 64 46 100 50 C64 54 54 64 50 100 C46 64 36 54 0 50 C36 46 46 36 50 0 Z"

    private const val MUD = 0xF0553A24L


    // Eventi: colori pieni, foto grandi, scritte enormi e adesivi (niente avorio e rametti da partecipazione).
    private const val CREAM = 0xFFFFF4E2L
    private const val BUTTER = 0xFFFFE14DL
    private const val HEART = "M50 88 C12 62 0 34 18 16 C34 2 48 14 50 30 C52 14 66 2 82 16 C100 34 88 62 50 88"

    // E1 ---------------------------------------------------------------------------------------------
    private fun wedding(): Design {
        val wave = "M0 60 C130 0 260 0 397 45 C534 90 664 90 794 30 L794 120 L0 120 Z"
        return Design("x-wed", "Sposi!", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC,
            accents = listOf(0xFFE5432DL, 0xFF2340D9L, 0xFF111111L, 0xFFFF6FB5L), accents2 = listOf(CREAM, BUTTER, WHITE),
            motion = CollageMotion.ZOOM, hint = "Matrimonio da rivista: la foto a tutta pagina, «Sposi!» enorme dietro di voi e un'onda di colore pieno",
            els = listOf(
                DPhoto(bk(0, 0, 794, 820, F), 0),
                DText(bk(0, 30, 794, 330, F), "title", "Titolo", "Sposi!", DFont.SERIF_IT, 300f, ACC2, DAlign.CENTER, line = 0.9f, behind = 0, shadow = true),
                DPath(bk(0, 730, 794, 120, F), wave, 794f, 120f, fill = ACC),
                DText(bk(540, 690, 220, 80), "sticker", "Adesivo", "Evviva!", DFont.HAND, 48f, BLACK, DAlign.CENTER, rot = -8f, pill = BUTTER, pad = 22f),
                DPath(bk(60, 870, 70, 70), SPARKLE, 100f, 100f, fill = ACC2),
                DPath(bk(670, 1000, 50, 50), SPARKLE, 100f, 100f, fill = ACC2),
                DText(bk(40, 850, 714, 120), "names", "Nomi", "Anna & Luca", DFont.SERIF, 104f, ACC2, DAlign.CENTER),
                DText(bk(40, 985, 714, 34), "date", "Data", "{data|20.06.2027}", DFont.MONO_BOLD, 26f, ACC2, DAlign.CENTER, track = 0.3f),
                DText(bk(40, 1035, 714, 50), "place", "Luogo", "{luogo|Villa sul lago}", DFont.SERIF_IT, 40f, ACC2, DAlign.CENTER),
            ))
    }

    // E2 ---------------------------------------------------------------------------------------------
    private fun saveDate() = Design("x-save-date", "Save the date", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFF2340D9L, 0xFFE5432DL, 0xFFFF6FB5L, 0xFF1E4D3AL), accents2 = listOf(CREAM, BUTTER, WHITE),
        motion = CollageMotion.DROP, hint = "Save the date a colore pieno: la foto come una stampa appoggiata storta, la data gigante e un cuore a mano",
        els = listOf(
            DPhoto(bk(127, 150, 540, 600), 0, radius = 18f, rot = 3f, border = 16f, borderColor = CREAM, shadow = 24f),
            DText(bk(40, 40, 560, 120), "kicker", "Rubrica", "Save the date", DFont.HAND, 92f, BUTTER, rot = -6f),
            DPath(bk(610, 70, 150, 140), HEART, 100f, 100f, stroke = BUTTER, width = 5f, draw = true),
            DText(bk(0, 760, 794, 280, F), "day", "Giorno", "{gg|20}.{mese_n|06}", DFont.ANTON, 300f, ACC2, DAlign.CENTER, line = 0.9f),
            DText(bk(40, 1000, 714, 70), "names", "Nomi", "Anna & Luca", DFont.SERIF_IT, 66f, ACC2, DAlign.CENTER),
            DText(bk(40, 1075, 714, 26), "place", "Luogo", "{ANNO} · {luogo|Villa sul lago}", DFont.MONO_BOLD, 20f, ACC2, DAlign.CENTER, track = 0.25f, upper = true),
        ))

    // E3 ---------------------------------------------------------------------------------------------
    private fun booth(): Design {
        // La striscia della cabina fototessere, storta: le foto girano attorno al centro della striscia.
        val rot = -4f; val cx = 230f; val cy = 565f
        val a = Math.toRadians(rot.toDouble()); val sn = kotlin.math.sin(a).toFloat(); val cs = kotlin.math.cos(a).toFloat()
        val els = arrayListOf<DEl>(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.CONFETTI, 0, 0f),
            DRect(bk(70, 70, 320, 990), CREAM, rot = rot, shadow = 22f),
        )
        for (i in 0 until 4) {
            val dy = 200f + i * 235f - cy
            val px = cx - dy * sn; val py = cy + dy * cs
            els += DPhoto(bk(px - 140, py - 105, 280, 210), i, rot = rot)
        }
        val sy = 1025f - cy
        els += DText(bk(cx - sy * sn - 140, cy + sy * cs - 15, 280, 30), "stamp", "Sotto le foto", "{giorno_breve} {ANNO}", DFont.MONO_BOLD, 18f, BLACK, DAlign.CENTER, track = 0.2f, rot = rot)
        els += listOf(
            DText(bk(420, 80, 350, 420), "title", "Titolo", "Che\nfesta!", DFont.ANTON, 190f, ACC2, line = 0.88f, upper = true),
            DPath(bk(640, 470, 120, 120), SPARKLE, 100f, 100f, fill = BUTTER),
            DText(bk(430, 560, 330, 60), "tag", "Etichetta", "{luogo|Milano}", DFont.MONO_BOLD, 24f, BLACK, rot = 5f, pill = BUTTER, pad = 18f, upper = true, track = 0.1f),
            DText(bk(430, 650, 340, 90), "note", "Frase", "si balla fino all'alba", DFont.HAND, 52f, ACC2, rot = -4f, wrap = true),
            DPhoto(bk(460, 790, 280, 280), 4, DShape.CIRCLE, rot = 6f, border = 12f, borderColor = CREAM, shadow = 16f),
        )
        return Design("x-booth", "Cabina foto", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC,
            accents = listOf(0xFFE8322BL, 0xFFFF5FA2L, 0xFF2340D9L, 0xFF111111L), accents2 = listOf(CREAM, BUTTER, WHITE),
            motion = CollageMotion.CASCADE, els = els,
            hint = "Festa, matrimonio, addio al celibato: la striscia della cabina fototessere con quattro scatti, scritte grosse e adesivi")
    }

    // E4 ---------------------------------------------------------------------------------------------
    private fun flash(): Design {
        val stamp = "'{AA} {mese_n} {gg}"
        return Design("x-flash", "Usa e getta", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, 0xFF0E0E0E,
            accents = listOf(0xFFFF8A1FL, 0xFFFF3B30L, 0xFFFFD60AL), motion = CollageMotion.CASCADE, grain = true,
            hint = "Le foto col flash della macchinetta usa e getta, con la data arancione stampata nell'angolo: feste, cene, addii al nubilato",
            els = listOf(
                DPhoto(bk(30, 30, 734, 640), 0, radius = 6f),
                DPhoto(bk(30, 690, 357, 403), 1, radius = 6f),
                DPhoto(bk(407, 690, 357, 403), 2, radius = 6f),
                DText(bk(50, 560, 520, 90), "title", "Frase", "che serata", DFont.HAND, 76f, WHITE, rot = -3f, shadow = true),
                DText(bk(430, 620, 314, 36), "stamp", "Data stampata", stamp, DFont.MONO_BOLD, 30f, ACC, DAlign.RIGHT, glow = ACC),
                DText(bk(60, 1044, 307, 30), "", "", stamp, DFont.MONO_BOLD, 24f, ACC, DAlign.RIGHT, glow = ACC),
                DText(bk(437, 1044, 307, 30), "", "", stamp, DFont.MONO_BOLD, 24f, ACC, DAlign.RIGHT, glow = ACC),
            ))
    }

    // E5 ---------------------------------------------------------------------------------------------
    private fun balloons() = Design("x-balloons", "Compleanno", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFFC8F04AL, 0xFFFF6FB5L, 0xFFFF7A1AL, 0xFF7CC8FFL), motion = CollageMotion.CASCADE,
        hint = "Compleanno: gli anni giganti con le foto dentro i numeri, il nome scritto grande e i coriandoli",
        els = listOf(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.CONFETTI, 0, 0f),
            DText(bk(197, 60, 400, 56), "kicker", "Rubrica", "Buon compleanno", DFont.MONO_BOLD, 24f, CREAM, DAlign.CENTER, pill = BLACK, pad = 24f, upper = true, track = 0.15f),
            DPhotoWord(bk(30, 140, 734, 610), "age", "Anni (foto dentro)", "40", DFont.ARCHIVO, 0, gap = 16f),
            DText(bk(40, 770, 520, 220), "name", "Nome", "Marta!", DFont.SERIF_IT, 200f, BLACK, rot = -5f),
            DPhoto(bk(560, 800, 200, 200), 2, DShape.CIRCLE, border = 10f, borderColor = WHITE, shadow = 14f, rot = 8f),
            DPath(bk(520, 760, 70, 70), SPARKLE, 100f, 100f, fill = BLACK),
            DPath(bk(60, 120, 50, 50), SPARKLE, 100f, 100f, fill = BLACK),
            DText(bk(40, 1050, 714, 30), "date", "Data", "{data} · {luogo|Milano}", DFont.MONO_BOLD, 20f, BLACK, DAlign.CENTER, track = 0.2f, upper = true),
        ))

    // E6 ---------------------------------------------------------------------------------------------
    private fun disco() = Design(
        "x-disco", "Disco", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, 0xFF0D0717,
        accents = listOf(0xFFFF3EA5, 0xFF00E5FF, 0xFFB388FF, 0xFFFFE600), motion = CollageMotion.CASCADE,
        hint = "Festa, discoteca, capodanno: la palla a specchi che brilla e le scritte al neon",
        els = listOf(
            DRect(b(-200, -200, 1194, 1200), 0, radial = true, grad = listOf(0f to 0x40FFFFFFL, 1f to 0L)),
            DRect(bk(396, 0, 2, 44), 0xFF8A8A99),
            DPattern(bk(247, 40, 300, 300), DPatternKind.DISCO, WHITE, 0f),
            DPath(bk(150, 120, 60, 60), SPARKLE, 100f, 100f, fill = ACC),
            DPath(bk(600, 90, 70, 70), SPARKLE, 100f, 100f, fill = WHITE),
            DPath(bk(620, 300, 40, 40), SPARKLE, 100f, 100f, fill = ACC),
            DText(bk(30, 370, 734, 200), "title", "Titolo", "Saturday\nnight", DFont.HAND, 110f, WHITE, DAlign.CENTER, line = 0.85f, glow = ACC),
            DPhoto(bk(70, 600, 310, 390), 0, radius = 20f, border = 4f, borderColor = ACC),
            DPhoto(bk(414, 600, 310, 390), 1, radius = 20f, border = 4f, borderColor = ACC),
            DText(b(40, 1024, 714, 40, E), "info", "Informazioni", "{data} · {luogo|Club} · dalle 22", DFont.MONO_BOLD, 18f, ACC, DAlign.CENTER, track = 0.2f, upper = true, glow = ACC),
        ),
    )

    // E7 ---------------------------------------------------------------------------------------------
    private fun vip(): Design {
        val ink = 0xFF101014L
        return Design("x-vip", "Pass VIP", DesignFamily.EVENTS, DOrient.PORTRAIT, 600f, 900f, ink,
            accents = listOf(0xFFFF3EA5, 0xFF2F5BFF, 0xFFFF8C00), motion = CollageMotion.SLIDE,
            hint = "Concerto, festival, evento: il pass da backstage con la striscia olografica e la tua foto",
            els = listOf(
                DRect(bk(270, 0, 60, 170), ACC),
                DRect(bk(280, 150, 40, 44), 0xFF9CA3AF, radius = 6f),
                DRect(bk(90, 180, 420, 640), WHITE, radius = 24f, shadow = 20f),
                DRect(bk(90, 180, 420, 120), 0, radius = 24f, grad = listOf(0f to 0xFFB388FFL, 0.35f to 0xFF7CFFCBL, 0.7f to 0xFFFFD6E8L, 1f to 0xFF82B1FFL)),
                DText(bk(90, 214, 420, 60), "access", "Accesso", "All access", DFont.ANTON, 52f, ink, DAlign.CENTER, upper = true),
                DPhoto(bk(150, 320, 300, 300), 0, DShape.CIRCLE),
                DText(bk(100, 638, 400, 58), "name", "Nome", "Ospite", DFont.GROTESK, 48f, ink, DAlign.CENTER),
                DText(bk(100, 700, 400, 22), "event", "Evento", "{luogo|Festival} · {ANNO}", DFont.MONO_BOLD, 16f, ACC, DAlign.CENTER, track = 0.15f, upper = true),
                DBarcode(bk(150, 742, 300, 50), ink),
            ))
    }

    // E8 ---------------------------------------------------------------------------------------------
    private fun badge(): Design {
        val ink = 0xFF111827L
        return Design("x-badge", "Badge convegno", DesignFamily.EVENTS, DOrient.PORTRAIT, 600f, 900f, 0xFFE9ECF1,
            accents = listOf(0xFF2F5BFF, 0xFF0F766E, 0xFFE63946), accents2 = listOf(0xFF111111, 0xFF2F5BFF, 0xFF6B7280),
            motion = CollageMotion.SLIDE, hint = "Convegno, fiera, corso: il badge con nome, ruolo, azienda e il riquadro tipo QR",
            els = listOf(
                DRect(bk(280, 0, 40, 120), ACC2),
                DRect(bk(80, 110, 440, 700), WHITE, radius = 20f, shadow = 18f),
                DRect(bk(80, 110, 440, 150), ACC, radius = 20f),
                DRect(bk(80, 200, 440, 60), ACC),
                DText(bk(108, 136, 384, 80), "event", "Evento", "Forum {luogo|Milano} {ANNO}", DFont.GROTESK, 38f, WHITE, wrap = true),
                DText(bk(108, 224, 384, 20), "date", "Data", "{giorno}", DFont.MONO, 13f, WHITE, track = 0.1f, upper = true),
                DPhoto(bk(110, 290, 140, 140), 0, DShape.CIRCLE),
                DText(bk(270, 300, 230, 120), "name", "Nome", "Giulia\nRossi", DFont.GROTESK, 40f, ink, line = 1f),
                DText(bk(110, 458, 380, 30), "role", "Ruolo", "Relatrice", DFont.MONO_BOLD, 20f, ACC, track = 0.15f, upper = true),
                DText(bk(110, 494, 380, 30), "company", "Azienda", "Studio Rossi & Partners", DFont.SANS, 20f, ink),
                DPattern(bk(110, 560, 190, 190), DPatternKind.QR, ink, 0f),
                DText(bk(320, 604, 180, 30), "tag", "Etichetta", "Speaker", DFont.MONO_BOLD, 16f, WHITE, pill = ACC, pad = 14f),
            ))
    }

    // E9 ---------------------------------------------------------------------------------------------
    private fun speaker() = Design(
        "x-speaker", "Relatore", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC2,
        accents = listOf(0xFFFFD60A, 0xFF7CFFCB, 0xFFFF5B14), accents2 = listOf(0xFF0F172A, 0xFF1A1033, 0xFF111111),
        motion = CollageMotion.SLIDE, hint = "La locandina di un intervento: la persona scontornata, il titolo, giorno, ora e sala",
        els = listOf(
            DRect(bk(300, 120, 560, 560), ACC, circle = true),
            DText(bk(48, 60, 600, 24), "kicker", "Rubrica", "Keynote · {luogo|Milano}", DFont.MONO_BOLD, 16f, ACC, track = 0.2f, upper = true),
            DPhoto(bk(300, 120, 520, 680), 0, radius = 24f, border = 10f, shadow = 12f, cutout = true),
            DText(bk(48, 690, 698, 210), "title", "Titolo", "Il futuro\nè già qui", DFont.GROTESK, 96f, WHITE, line = 0.95f, shadow = true),
            DText(bk(48, 910, 698, 40), "name", "Nome", "Giulia Rossi", DFont.GROTESK_MED, 34f, WHITE),
            DText(bk(48, 952, 698, 24), "role", "Ruolo", "CEO, Studio Rossi", DFont.SANS, 18f, 0xFFB0B6C3),
            DText(bk(48, 1020, 698, 30), "when", "Quando", "{giorno} · ore 10:30 · Sala A", DFont.MONO_BOLD, 18f, ACC, track = 0.1f, upper = true),
        ),
    )

    // E10 --------------------------------------------------------------------------------------------
    private fun laurea() = Design("x-laurea", "Laurea", DesignFamily.EVENTS, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFFC8102EL, 0xFF2340D9L, 0xFF111111L, 0xFF1E4D3AL), accents2 = listOf(CREAM, BUTTER, WHITE),
        motion = CollageMotion.DROP,
        hint = "Laurea: il voto enorme che conta fino al 110, tu scontornato come un adesivo davanti, il nome su un'etichetta gialla",
        els = listOf(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.CONFETTI, 0, 0f),
            DText(bk(0, 40, 794, 640, F), "grade", "Voto", "110\ne lode", DFont.ANTON, 330f, ACC2, DAlign.CENTER, line = 0.86f, upper = true, count = true),
            DPhoto(bk(147, 330, 500, 640), 0, cutout = true, border = 14f, shadow = 18f),
            DPath(bk(70, 380, 80, 80), SPARKLE, 100f, 100f, fill = BUTTER),
            DPath(bk(660, 300, 60, 60), SPARKLE, 100f, 100f, fill = BUTTER),
            DText(bk(60, 930, 674, 100), "name", "Nome", "Dott.ssa Marta Bianchi", DFont.GROTESK, 50f, BLACK, DAlign.CENTER, rot = -3f, pill = BUTTER, pad = 26f),
            DText(bk(40, 1060, 714, 30), "field", "Corso e luogo", "Ingegneria · {luogo|Politecnico} · {data}", DFont.MONO_BOLD, 20f, ACC2, DAlign.CENTER, track = 0.15f, upper = true),
        ))

    // E11 --------------------------------------------------------------------------------------------
    private fun baby(): Design {
        val els = arrayListOf<DEl>(
            DText(bk(30, 24, 420, 200), "title", "Titolo", "Ciao,\nSofia!", DFont.SERIF_IT, 104f, ACC2, line = 0.86f),
            DPath(bk(370, 40, 80, 80), SPARKLE, 100f, 100f, fill = ACC2),
            DPhoto(bk(170, 210, 280, 360), 0, DShape.PILL, rot = 4f, border = 10f, borderColor = WHITE, shadow = 14f),
            DPath(bk(36, 520, 40, 40), SPARKLE, 100f, 100f, fill = ACC2),
            DText(bk(30, 590, 420, 50), "date", "Data", "{giorno} · {luogo|Milano}", DFont.HAND, 38f, ACC2),
        )
        listOf(Triple("Peso", "3,250 kg", -6f), Triple("Lunghezza", "50 cm", 5f), Triple("Ora", "ore {ora|07:42}", -3f)).forEachIndexed { i, (l, v, r) ->
            els += DText(bk(18, 250 + i * 100, 200, 56), "s$i", l, v, DFont.GROTESK, 30f, WHITE, rot = r, pill = ACC2, pad = 18f, count = true)
        }
        return Design("x-baby", "Benvenuto", DesignFamily.EVENTS, DOrient.PORTRAIT, 480f, 672f, ACC,
            accents = listOf(0xFFFFE27AL, 0xFFBFE3FFL, 0xFFFFC7DDL, 0xFFC9F0D8L), accents2 = listOf(0xFF2340D9L, 0xFFE5432DL, 0xFF111111L),
            els = els, motion = CollageMotion.DROP,
            hint = "Nascita o battesimo: «Ciao, Sofia!» grande, la foto a pastiglia e peso, lunghezza e ora su etichette che contano nel video")
    }

    // A1 -----------------------------------------------------------------------------------------------
    private fun bib(): Design {
        val pin = 0xFFB8BCC4L
        val els = arrayListOf<DEl>(
            DPhoto(b(0, 0, 794, 1123, F), 0),
            DRect(b(0, 0, 794, 1123, F), 0, grad = listOf(0f to 0x66000000L, 0.3f to 0L, 0.6f to 0L, 1f to 0xB3000000L)),
            DSplat(bk(520, 60, 260, 260), MUD, seed = 3),
            DSplat(bk(30, 830, 210, 210), MUD, seed = 5, rot = 40f),
            DSplat(bk(620, 930, 140, 140), MUD, seed = 9),
            DText(bk(40, 40, 500, 22), "kicker", "Rubrica", "{giorno}", DFont.MONO_BOLD, 16f, WHITE, track = 0.15f, upper = true, shadow = true),
            DText(bk(40, 70, 714, 200), "title", "Titolo", "Finisher", DFont.ANTON, 150f, WHITE, line = 0.85f, upper = true, skew = -0.12f, shadow = true),
            DRect(bk(167, 560, 460, 330), WHITE, radius = 10f, rot = -3f, shadow = 16f),
            DRect(bk(167, 560, 460, 330), ACC, rot = -3f, poly = pts(0f, 0f, 1f, 0f, 1f, 0.18f, 0f, 0.18f)),
            DText(bk(167, 560, 460, 330), "race", "Gara", "{luogo|Granfondo} {ANNO}", DFont.ANTON, 34f, WHITE, DAlign.CENTER, line = 1.75f, upper = true, rot = -3f),
            DText(bk(167, 560, 460, 316), "num", "Numero", "127", DFont.ANTON, 200f, BLACK, DAlign.CENTER, line = 0.95f, rot = -3f, bottom = true),
        )
        for ((x, y) in listOf(182 to 618, 596 to 597, 197 to 862, 611 to 840)) {
            els += DRect(bk(x, y, 16, 16), pin, circle = true)
            els += DRect(bk(x, y, 16, 16), 0xFF6B7280, circle = true, stroke = 2f)
        }
        val stats = listOf("Tempo" to "5:12:08", "Posizione" to "214°", "Km" to "142")
        stats.forEachIndexed { i, (l, v) ->
            val x = 40 + i * 245
            els += DText(b(x, 990, 230, 16, E), "l$i", "Etichetta ${i + 1}", l, DFont.MONO, 12f, WHITE, track = 0.18f, upper = true)
            els += DText(b(x, 1010, 230, 60, E), "s$i", l, v, DFont.ANTON, 54f, if (i == 0) ACC else WHITE, count = true)
        }
        return Design("x-bib", "Pettorale", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, BLACK,
            accents = listOf(0xFFE63946, 0xFF2F5BFF, 0xFFFF8C00, 0xFF16A34A), els = els, motion = CollageMotion.STILL,
            hint = "Granfondo, maratona, trail: il numero di gara spillato, fango e i tuoi tempi")
    }

    // A2 -----------------------------------------------------------------------------------------------
    private fun topo(): Design {
        val ink = 0xFF2B2418L
        val els = arrayListOf<DEl>(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.TOPO, 0xFF8B7B5E, 22f),
            DPath(b(0, 0, 794, 1123, F), "M80 980 C160 900 120 820 220 760 C320 700 260 600 380 560 C500 520 460 420 560 380 C660 340 640 240 720 180", 794f, 1123f,
                stroke = ACC, width = 7f, dash = listOf(18f, 10f), draw = true),
            DRect(bk(70, 970, 22, 22), ACC, circle = true),
            DRect(bk(709, 169, 22, 22), ACC, circle = true),
            DText(bk(40, 40, 600, 76), "title", "Titolo", "{luogo|Monte Grappa}", DFont.GROTESK, 64f, ink),
            DText(bk(40, 120, 600, 18), "coords", "Sottotitolo", "45.8701° N · 11.8040° E", DFont.MONO, 13f, ink, track = 0.1f),
            DPhoto(bk(150, 200, 500, 640), 0, radius = 24f, border = 14f, shadow = 12f, cutout = true),
            DRect(b(40, 900, 714, 180, E), 0xE6FFFFFF, radius = 18f, shadow = 10f),
        )
        listOf("Dislivello" to "1.640 m", "Distanza" to "48 km", "Quota" to "1.775 m").forEachIndexed { i, (l, v) ->
            val x = 64 + i * 236
            els += DText(b(x, 920, 220, 14, E), "l$i", "Etichetta ${i + 1}", l, DFont.MONO, 11f, ink, track = 0.18f, upper = true)
            els += DText(b(x, 938, 220, 46, E), "s$i", l, v, DFont.ANTON, 40f, if (i == 0) ACC else ink, count = true)
        }
        els += DPath(b(64, 996, 666, 64, E), "M0 60 L60 52 L110 40 L170 44 L230 26 L300 30 L350 12 L400 6 L460 20 L520 18 L580 34 L640 46 L666 50 L666 64 L0 64 Z", 666f, 64f, fill = ACC)
        els += DPath(b(64, 996, 666, 64, E), "M0 60 L60 52 L110 40 L170 44 L230 26 L300 30 L350 12 L400 6 L460 20 L520 18 L580 34 L640 46 L666 50", 666f, 64f, stroke = ink, width = 2f, draw = true)
        return Design("x-topo", "Topografica", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, 0xFFEDE8DC,
            accents = listOf(0xFFFF5B14, 0xFFE63946, 0xFF1D4ED8), els = els, motion = CollageMotion.CASCADE,
            hint = "Tu scontornato come un adesivo sulle curve di livello, con il percorso e l'altimetria che si disegnano")
    }

    // A3 -----------------------------------------------------------------------------------------------
    private fun speed() = Design(
        "x-speed", "Velocità", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, 0xFF0E0E10,
        accents = listOf(0xFFFFD60A, 0xFF00E5FF, 0xFFFF2D55), motion = CollageMotion.SLIDE,
        hint = "Discesa, sprint, curva: linee di velocità che corrono e la velocità massima gigante e inclinata",
        els = listOf(
            DPhoto(b(0, 0, 794, 1123, F), 0),
            DRect(b(0, 0, 794, 1123, F), 0, grad = listOf(0f to 0x80000000L, 0.35f to 0x1A000000L, 1f to 0xCC000000L)),
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.SPEED, WHITE, 0f),
            DText(bk(40, 40, 714, 120), "title", "Titolo", "Discesa", DFont.ANTON, 110f, WHITE, line = 0.9f, upper = true, skew = -0.2f, shadow = true),
            DText(bk(44, 520, 500, 30), "label", "Etichetta", "Velocità massima", DFont.MONO_BOLD, 18f, ACC, track = 0.2f, upper = true),
            DRect(bk(-50, 650, 900, 160), ACC, rot = -8f),
            DText(bk(30, 556, 480, 260), "speed", "Velocità", "62", DFont.ANTON, 280f, WHITE, line = 0.85f, skew = -0.25f, count = true, shadow = true),
            DText(bk(470, 690, 300, 84), "unit", "Unità", "km/h", DFont.ANTON, 80f, BLACK, upper = true, rot = -8f, skew = -0.25f),
            DPhoto(b(480, 830, 270, 170, E), 1, radius = 10f, rot = -8f, border = 4f),
            DText(b(40, 1040, 714, 30, E), "place", "Luogo", "{luogo|Passo dello Stelvio} · {data}", DFont.MONO_BOLD, 18f, WHITE, track = 0.12f, upper = true),
        ),
    )

    // A4 -----------------------------------------------------------------------------------------------
    private fun mud() = Design(
        "x-mud", "Fango", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, 0xFF2A1F17,
        accents = listOf(MUD, 0xF03B2A1C, 0xF07A6A55), accents2 = listOf(0xFFFFD60A, 0xFFFF5B14, WHITE), motion = CollageMotion.STILL,
        hint = "MTB, enduro, gravel, cross: schizzi di fango sulla foto e una scritta che grida",
        els = listOf(
            DPhoto(b(0, 0, 794, 1123, F), 0),
            DRect(b(0, 0, 794, 1123, F), 0, grad = listOf(0f to 0L, 0.55f to 0L, 1f to 0xCC000000L)),
            DSplat(bk(20, 20, 320, 320), ACC, seed = 21),
            DSplat(bk(500, 110, 280, 280), ACC, seed = 23, rot = 70f),
            DSplat(bk(330, 430, 170, 170), ACC, seed = 25),
            DSplat(bk(580, 650, 240, 240), ACC, seed = 27, rot = 20f),
            DSplat(bk(-40, 600, 220, 220), ACC, seed = 29),
            DText(b(36, 740, 400, 30, E), "tag", "Etichetta", "Enduro · run 3", DFont.MONO_BOLD, 16f, BLACK, pill = ACC2, pad = 12f, square = true),
            DText(b(30, 790, 734, 260, E), "title", "Titolo", "Full\ngas", DFont.ARCHIVO, 170f, WHITE, line = 0.85f, upper = true, skew = -0.08f, shadow = true),
            DText(b(36, 1064, 714, 24, E), "sub", "Sottotitolo", "{luogo|Finale Ligure} · {Mese} {ANNO}", DFont.MONO_BOLD, 16f, WHITE, track = 0.15f, upper = true),
        ),
    )

    // A5 -----------------------------------------------------------------------------------------------
    private fun hud(): Design {
        val els = arrayListOf<DEl>(
            DPhoto(b(0, 0, 600, 900, F), 0),
            DRect(b(-150, -50, 900, 1000, F), 0, radial = true, grad = listOf(0f to 0L, 0.65f to 0L, 1f to 0xA6000000L)),
        )
        for ((x, y, d) in listOf(Triple(24, 24, "M0 40 L0 0 L40 0"), Triple(536, 24, "M0 0 L40 0 L40 40"), Triple(24, 836, "M0 0 L0 40 L40 40"), Triple(536, 836, "M40 0 L40 40 L0 40")))
            els += DPath(bk(x, y, 40, 40), d, 40f, 40f, stroke = ACC, width = 4f)
        els += DRect(bk(80, 42, 14, 14), 0xFFFF2D2D, circle = true)
        els += DText(bk(102, 37, 100, 22), "", "", "REC", DFont.MONO_BOLD, 16f, WHITE)
        els += DText(bk(360, 37, 160, 22), "tc", "Tempo", "01:24:37", DFont.MONO_BOLD, 16f, WHITE, DAlign.RIGHT)
        els += DPath(bk(150, 440, 300, 20), "M0 10 L110 10 M190 10 L300 10 M140 10 L150 18 L160 10", 300f, 20f, stroke = WHITE, width = 2f)
        els += DPath(bk(36, 620, 190, 100), "M10 92 C10 30 60 8 95 8 C130 8 180 30 180 92", 190f, 100f, stroke = 0x66FFFFFF, width = 8f)
        els += DPath(bk(36, 620, 190, 100), "M10 92 C10 30 60 8 95 8 C112 8 128 12 140 18", 190f, 100f, stroke = ACC, width = 8f, draw = true)
        els += DText(bk(36, 662, 190, 70), "speed", "Velocità", "48", DFont.GROTESK, 64f, WHITE, DAlign.CENTER, count = true, shadow = true)
        els += DText(bk(36, 730, 190, 18), "", "", "km/h", DFont.MONO_BOLD, 13f, WHITE, DAlign.CENTER, track = 0.2f, upper = true)
        els += DText(bk(330, 650, 234, 16), "l1", "Etichetta 1", "Quota", DFont.MONO, 12f, WHITE, DAlign.RIGHT, track = 0.18f, upper = true)
        els += DText(bk(330, 668, 234, 44), "alt", "Quota", "1.775 m", DFont.ANTON, 40f, WHITE, DAlign.RIGHT, count = true, shadow = true)
        els += DPath(bk(482, 727, 26, 24), "M13 22 C4 15 1 10 2 6 C3 2 9 1 13 6 C17 1 23 2 24 6 C25 10 22 15 13 22 Z", 26f, 24f, fill = 0xFFFF2D55)
        els += DText(bk(450, 720, 114, 32), "hr", "Battito", "162", DFont.ANTON, 30f, WHITE, DAlign.RIGHT, count = true, shadow = true)
        els += DText(bk(40, 864, 520, 18), "place", "Luogo", "{luogo|Bike park} · gps fix", DFont.MONO, 12f, WHITE, DAlign.CENTER, track = 0.15f, upper = true)
        return Design("x-hud", "Casco", DesignFamily.ACTION, DOrient.PORTRAIT, 600f, 900f, BLACK,
            accents = listOf(WHITE, 0xFFFFD60A, 0xFF00E5FF), els = els, motion = CollageMotion.STILL,
            hint = "La grafica di una action cam sul casco: mirini, REC, velocità che sale, quota e battito")
    }

    // A6 -----------------------------------------------------------------------------------------------
    private fun record(): Design {
        val dim = 0xFFB0B6C3L
        val els = arrayListOf<DEl>(
            DPhoto(bk(0, 0, 794, 520), 0),
            DRect(bk(0, 300, 794, 222), 0, grad = listOf(0f to 0L, 1f to ACC2)),
            DRect(bk(40, 470, 150, 150), ACC, circle = true, shadow = 12f),
            DText(bk(40, 514, 150, 62), "pr", "Bollino", "PR", DFont.ANTON, 64f, BLACK, DAlign.CENTER, rot = -8f),
            DText(bk(220, 480, 534, 64), "seg", "Segmento", "Salita del {luogo|Ghisallo}", DFont.GROTESK, 44f, WHITE, wrap = true),
            DText(bk(220, 550, 534, 20), "date", "Data", "{giorno}", DFont.MONO, 14f, dim, track = 0.1f, upper = true),
            DText(bk(40, 640, 714, 170), "time", "Tempo", "12:47", DFont.ANTON, 180f, WHITE, line = 0.9f, count = true),
            DText(bk(40, 820, 350, 30), "rank", "Posizione", "3° su 4.812", DFont.MONO_BOLD, 22f, ACC),
            DText(bk(404, 820, 350, 30), "gain", "Miglioramento", "−0:38 dal tuo meglio", DFont.MONO_BOLD, 22f, WHITE, DAlign.RIGHT),
        )
        val hs = listOf(120, 150, 95, 170, 140, 180, 130, 160)
        hs.forEachIndexed { i, hh ->
            val x = 40 + i * 89
            els += DRect(bk(x, 1060 - hh, 70, hh), if (hh == hs.max()) ACC else 0xFF3A3F4B, radius = 6f)
            els += DText(bk(x, 1068, 70, 16), "", "", "${i + 1}", DFont.MONO, 12f, dim, DAlign.CENTER)
        }
        return Design("x-pr", "Record", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, ACC2,
            accents = listOf(0xFFFF5B14, 0xFFFFD60A, 0xFF00E5FF), accents2 = listOf(0xFF111318, 0xFF1A1033, 0xFF0B2E2A), els = els,
            motion = CollageMotion.SLIDE, hint = "Il tuo record su una salita o un segmento: tempo che conta, posizione e il ritmo km per km")
    }

    // A7 -----------------------------------------------------------------------------------------------
    private fun sequence(): Design {
        val w = 158.8f
        val els = arrayListOf<DEl>(
            DText(bk(40, 40, 714, 100), "title", "Titolo", "Il salto", DFont.ANTON, 100f, WHITE, line = 0.9f, upper = true, skew = -0.15f),
            DText(bk(44, 132, 714, 20), "sub", "Sottotitolo", "{luogo|Bike park} · {data}", DFont.MONO, 14f, WHITE, track = 0.12f, upper = true),
        )
        for (k in 0 until 5) {
            val bx = k * w - 30
            els += DPhoto(bk(bx, 170, w + 60, 810), k, poly = pts(0.288f, 0f, 0.986f, 0f, 0.712f, 1f, 0.014f, 1f))
            els += DText(bk(k * w + 14, 990, 120, 64), "", "", "${k + 1}", DFont.ANTON, 56f, ACC, skew = -0.15f)
        }
        els += DText(b(40, 1062, 714, 30, E), "stat", "Dato", "Volo: 6,4 m", DFont.MONO_BOLD, 20f, WHITE, track = 0.1f, upper = true)
        return Design("x-sequence", "Sequenza", DesignFamily.ACTION, DOrient.PORTRAIT, 794f, 1123f, BLACK,
            accents = listOf(0xFFFFD60A, 0xFFFF2D55, 0xFF00E5FF), els = els, motion = CollageMotion.WIPE,
            hint = "Cinque foto di un salto, una curva o una virata in strisce inclinate e numerate, come una foto stroboscopica")
    }

    // A8 -----------------------------------------------------------------------------------------------
    private fun skipass(): Design {
        val ink = 0xFF0F172AL; val grey = 0xFF64748BL
        val els = arrayListOf<DEl>(
            DPhoto(b(-40, -40, 680, 980, F), 0, filter = DFilter.BLUR),
            DRect(b(0, 0, 600, 900, F), 0x33FFFFFF),
            DRect(bk(60, 90, 480, 720), WHITE, radius = 28f, shadow = 20f),
            DRect(bk(276, 100, 48, 14), 0xFFDDE6EE, radius = 7f),
            DPhoto(bk(84, 124, 432, 290), 0, radius = 18f),
            DText(bk(84, 430, 432, 24), "kicker", "Rubrica", "Skipass giornaliero", DFont.MONO_BOLD, 16f, ACC, track = 0.15f, upper = true),
            DText(bk(84, 458, 432, 70), "resort", "Comprensorio", "{luogo|Cervinia}", DFont.GROTESK, 60f, ink),
            DText(bk(84, 530, 432, 20), "date", "Data", "{giorno}", DFont.MONO, 14f, grey),
        )
        listOf("Quota max" to "3.480 m", "Pista" to "Nera", "Discese" to "14", "Dislivello" to "12.400 m").forEachIndexed { i, (l, v) ->
            val x = 84 + (i % 2) * 220; val y = 570 + (i / 2) * 64
            els += DText(bk(x, y, 210, 14), "l$i", "Etichetta ${i + 1}", l, DFont.MONO, 11f, grey, track = 0.18f, upper = true)
            els += DText(bk(x, y + 16, 210, 40), "s$i", l, v, DFont.GROTESK, 32f, ink, count = true)
        }
        els += DPath(bk(84, 704, 432, 2), "M0 1 L432 1", 432f, 2f, stroke = 0xFFB0B8C4, width = 2f, dash = listOf(6f, 6f))
        els += DBarcode(bk(84, 724, 432, 60), ink)
        return Design("x-skipass", "Skipass", DesignFamily.ACTION, DOrient.PORTRAIT, 600f, 900f, 0xFFDDE6EE,
            accents = listOf(0xFF1D4ED8, 0xFFE63946, 0xFF0F766E), els = els, motion = CollageMotion.SLIDE,
            hint = "Sci e snowboard: il biglietto dell'impianto con la quota, la pista, le discese e la tua foto")
    }

    // T1 -----------------------------------------------------------------------------------------------
    private fun notes(): Design {
        val grey = 0xFF8E8E93L
        val items = listOf("tuffo all'alba", "gelato doppio", "tramonto dal pontile", "tornare l'anno prossimo")
        val els = arrayListOf<DEl>(
            DText(bk(28, 30, 200, 26), "", "", "‹ Note", DFont.SANS, 20f, ACC),
            DText(bk(372, 30, 200, 26), "", "", "Fine", DFont.SANS, 20f, ACC, DAlign.RIGHT),
            DText(bk(28, 80, 544, 18), "date", "Data", "{giorno} alle {ora|18:42}", DFont.SANS, 14f, grey, DAlign.CENTER),
            DText(bk(28, 112, 544, 54), "title", "Titolo", "{luogo|Weekend al lago}", DFont.GROTESK, 44f, BLACK),
        )
        items.forEachIndexed { i, t ->
            val y = 186 + i * 40
            val done = i < 3
            els += if (done) DRect(bk(30, y + 4, 22, 22), ACC, circle = true) else DRect(bk(30, y + 4, 22, 22), grey, circle = true, stroke = 2f)
            if (done) els += DPath(bk(30, y + 4, 22, 22), "M6 11 L10 15 L16 7", 22f, 22f, stroke = WHITE, width = 2.5f, draw = true)
            els += DText(bk(62, y, 500, 30), "c$i", "Cosa ${i + 1}", t, DFont.SANS, 22f, if (done) grey else BLACK)
        }
        els += DPhoto(bk(28, 360, 544, 300), 0, radius = 14f)
        els += DPhoto(bk(28, 672, 266, 200), 1, radius = 14f)
        els += DPhoto(bk(306, 672, 266, 200), 2, radius = 14f)
        return Design("x-notes", "Appunti", DesignFamily.TREND, DOrient.PORTRAIT, 600f, 900f, 0xFFFFFDF5,
            accents = listOf(0xFFE8A317, 0xFF2F5BFF, 0xFFFF4F79), els = els, motion = CollageMotion.SLIDE,
            hint = "Il ricordo come una nota del telefono: lista spuntata, data e foto incollate (il «Notes App Chic» del 2026)")
    }

    // T2 -----------------------------------------------------------------------------------------------
    private fun chrome(): Design {
        val metal = listOf(0xFFFFFFFFL, 0xFF9AA4B5L, 0xFFF4F6FAL, 0xFF5E6878L, 0xFFE9EDF3L)
        val frame = 0xFFD9DEE7L
        return Design(
            "x-chrome", "Cromo Y2K", DesignFamily.TREND, DOrient.PORTRAIT, 794f, 1123f, ACC2,
            accents = listOf(0xFFFF4FD8, 0xFF7CFFCB, 0xFF7AA2FF), accents2 = listOf(0xFF0B0B1A, 0xFF1A0B2E, 0xFF111111),
            motion = CollageMotion.CASCADE, hint = "Titolo in metallo lucido, foto in bolle cromate, stelline che brillano: lo stile Y2K",
            els = listOf(
                DRect(b(-200, 100, 1194, 900, F), 0, radial = true, grad = listOf(0f to 0x66FFFFFFL, 1f to 0L)),
                DRect(b(-200, 100, 1194, 900, F), ACC, radial = true, grad = listOf(0f to 0x55FFFFFFL, 0.6f to 0x22FFFFFFL, 1f to 0L)),
                DText(bk(30, 50, 734, 220), "title", "Titolo", "{stagione|estate}", DFont.ARCHIVO, 170f, WHITE, DAlign.CENTER, line = 0.9f, shadow = true, gradient = metal),
                DPhoto(bk(80, 300, 300, 480), 0, DShape.PILL, border = 8f, borderColor = frame, shadow = 20f),
                DPhoto(bk(420, 330, 300, 300), 1, DShape.CIRCLE, border = 8f, borderColor = frame, shadow = 20f),
                DPhoto(bk(400, 670, 320, 180), 2, DShape.PILL, border = 8f, borderColor = frame, shadow = 20f),
                DPath(bk(360, 262, 70, 70), SPARKLE, 100f, 100f, fill = WHITE),
                DPath(bk(704, 280, 50, 50), SPARKLE, 100f, 100f, fill = ACC),
                DPath(bk(60, 820, 60, 60), SPARKLE, 100f, 100f, fill = WHITE),
                DPath(bk(640, 880, 80, 80), SPARKLE, 100f, 100f, fill = WHITE),
                DPath(bk(330, 900, 40, 40), SPARKLE, 100f, 100f, fill = ACC),
                DText(bk(40, 990, 714, 60), "sub", "Sottotitolo", "{luogo|Riccione} · {ANNO}", DFont.MONO_BOLD, 28f, ACC, DAlign.CENTER, track = 0.2f, upper = true),
            ),
        )
    }

    // T3 -----------------------------------------------------------------------------------------------
    private fun leak(): Design {
        val ink = 0xFF333333L
        return Design(
            "x-leak", "Luce calda", DesignFamily.TREND, DOrient.PORTRAIT, 794f, 1123f, 0xFFEFE6D8,
            accents = listOf(0xFF3B2A1A, 0xFF8E3B4E, 0xFF1F3A68), motion = CollageMotion.DROP, grain = true,
            hint = "Polaroid sbiadite con le macchie di luce arancio della pellicola: il fascino del rullino vecchio",
            els = listOf(
                DPhoto(bk(70, 90, 420, 420), 0, rot = -4f, border = 18f, bottomPad = 70f, shadow = 14f),
                DText(bk(90, 526, 420, 44), "c0", "Didascalia 1", "{luogo|Positano}", DFont.HAND, 40f, ink, DAlign.CENTER, rot = -4f),
                DPhoto(bk(420, 420, 320, 320), 1, rot = 6f, border = 16f, bottomPad = 56f, shadow = 14f),
                DText(bk(390, 748, 320, 40), "c1", "Didascalia 2", "{data}", DFont.HAND, 34f, ink, DAlign.CENTER, rot = 6f),
                DPhoto(bk(110, 640, 300, 300), 2, rot = -2f, border = 14f, bottomPad = 50f, shadow = 14f),
                DRect(b(300, -260, 800, 800), 0, radial = true, grad = listOf(0f to 0xD9FF7A00L, 0.35f to 0x99FF3D00L, 0.7f to 0x33FF2E00L, 1f to 0L)),
                DRect(b(-320, 640, 760, 760, E), 0, radial = true, grad = listOf(0f to 0xCCFFB000L, 0.45f to 0x66FF8A00L, 1f to 0L)),
                DText(bk(440, 860, 320, 140), "title", "Titolo", "{stagione|estate}\n{ANNO}", DFont.SERIF_IT, 64f, ACC, DAlign.RIGHT, line = 1f),
            ),
        )
    }

    // T4 -----------------------------------------------------------------------------------------------
    private fun blue() = Design(
        "x-blue", "Blu", DesignFamily.TREND, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFF1F4FA8, 0xFF2E5E8C, 0xFF3A3F8F, 0xFF1D6E73), motion = CollageMotion.SLIDE,
        hint = "Fondo blu con la grana del denim e le nuvole, foto con il filo bianco e una scritta pulita",
        els = listOf(
            DRect(b(-200, -100, 1194, 1300, F), 0, radial = true, grad = listOf(0f to 0x33FFFFFFL, 1f to 0L)),
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x22FFFFFF, 0f),
            DText(bk(60, 70, 674, 90), "title", "Titolo", "{luogo|Mare d'inverno}", DFont.SERIF, 80f, WHITE, DAlign.CENTER),
            DText(bk(60, 172, 674, 20), "sub", "Sottotitolo", "{dal_al|Dicembre 2026}", DFont.MONO, 14f, WHITE, DAlign.CENTER, track = 0.3f, upper = true),
            DPhoto(bk(60, 230, 674, 420), 0, border = 4f),
            DPhoto(bk(60, 670, 327, 380), 1, border = 4f),
            DPhoto(bk(407, 670, 327, 380), 2, border = 4f),
            DText(bk(60, 1072, 674, 20), "foot", "Piede", "fotografie e ricordi · {ANNO}", DFont.MONO, 13f, WHITE, DAlign.CENTER, track = 0.2f),
        ),
    )

    // T5 -----------------------------------------------------------------------------------------------
    private fun pearls(): Design {
        val pearl = 0xFFF3EEE4L
        return Design(
            "x-pearls", "Perle", DesignFamily.TREND, DOrient.PORTRAIT, 480f, 672f, ACC,
            accents = listOf(0xFF1C1A24, 0xFF14213D, 0xFF3A1F2B, 0xFF2F3E46), motion = CollageMotion.WIPE,
            hint = "Elegante: titolo serif e un filo di perle attorno alla foto ad arco (che si infila nel video)",
            els = listOf(
                DPhoto(b(110, 80, 260, 330, C), 0, DShape.ARCH),
                DPath(b(96, 66, 288, 358, C), "M0 358 L0 144 C0 64 64 0 144 0 C224 0 288 64 288 144 L288 358", 288f, 358f, stroke = pearl, width = 9f, dash = listOf(0.1f, 16f), draw = true),
                DText(b(20, 440, 440, 60, C), "names", "Nomi", "Sofia & Luca", DFont.SERIF_IT, 50f, pearl, DAlign.CENTER),
                DPath(b(150, 510, 180, 10, C), "M0 5 L180 5", 180f, 10f, stroke = pearl, width = 7f, dash = listOf(0.1f, 14f), draw = true),
                DText(b(20, 530, 440, 18, C), "date", "Data", "{data_romana}", DFont.SANS, 12f, 0xFFD9CBB0, DAlign.CENTER, track = 0.42f),
                DPhoto(b(130, 566, 90, 90, C), 1, DShape.CIRCLE, border = 3f, borderColor = pearl),
                DPhoto(b(260, 566, 90, 90, C), 2, DShape.CIRCLE, border = 3f, borderColor = pearl),
            ),
        )
    }

    // T6 -----------------------------------------------------------------------------------------------
    private fun thenNow() = Design(
        "x-then-now", "Allora e adesso", DesignFamily.TREND, DOrient.PORTRAIT, 600f, 900f, 0xFF111111,
        accents = listOf(0xFFFFD23F, 0xFFFF4FD8, 0xFF7CFFCB), motion = CollageMotion.WIPE,
        hint = "Due foto a confronto, una di allora e una di adesso, con gli anni: nel video si scoprono una dopo l'altra",
        els = listOf(
            DPhoto(bk(0, 0, 600, 450), 0),
            DPhoto(bk(0, 450, 600, 450), 1),
            DRect(bk(0, 446, 600, 8), ACC),
            DText(bk(24, 24, 300, 70), "y0", "Anno di allora", "2016", DFont.ANTON, 64f, WHITE, shadow = true),
            DText(bk(34, 100, 300, 20), "l0", "Etichetta 1", "allora", DFont.MONO_BOLD, 16f, WHITE, pill = 0x99000000, pad = 10f),
            DText(bk(24, 474, 300, 70), "y1", "Anno di adesso", "{ANNO}", DFont.ANTON, 64f, ACC, shadow = true, count = true),
            DText(bk(34, 550, 300, 20), "l1", "Etichetta 2", "adesso", DFont.MONO_BOLD, 16f, WHITE, pill = 0x99000000, pad = 10f),
            DText(bk(276, 846, 300, 40), "hand", "Scritta a mano", "siamo sempre noi", DFont.HAND, 34f, WHITE, DAlign.RIGHT, rot = -3f, shadow = true),
        ),
    )

    // T7 -----------------------------------------------------------------------------------------------
    private fun windows(): Design {
        val grey = 0xFFC0C0C0L
        val els = arrayListOf<DEl>()
        fun window(x: Int, y: Int, w: Int, h: Int, id: String, label: String, title: String, slot: Int) {
            els += DRect(bk(x, y, w, h), grey, shadow = 14f)
            els += DRect(bk(x, y, w, h), 0xFF404040, stroke = 2f)
            els += DRect(bk(x + 6, y + 6, w - 12, 30), ACC2)
            els += DText(bk(x + 14, y + 12, w - 110, 18), id, label, title, DFont.MONO_BOLD, 14f, WHITE)
            for (k in 0 until 3) els += DRect(bk(x + w - 88 + k * 26, y + 10, 22, 22), grey)
            els += DText(bk(x + w - 36, y + 12, 22, 18), "", "", "×", DFont.MONO_BOLD, 16f, BLACK, DAlign.CENTER)
            els += DPhoto(bk(x + 12, y + 42, w - 24, h - 54), slot)
        }
        window(60, 90, 460, 400, "w0", "Finestra 1", "ricordi_{ANNO}.jpg", 0)
        window(360, 420, 380, 330, "w1", "Finestra 2", "{luogo|spiaggia}.jpg", 1)
        window(90, 620, 300, 260, "w2", "Finestra 3", "noi.jpg", 2)
        els += DRect(bk(220, 900, 400, 170), grey, shadow = 14f)
        els += DRect(bk(220, 900, 400, 170), 0xFF404040, stroke = 2f)
        els += DRect(bk(226, 906, 388, 30), ACC2)
        els += DText(bk(236, 912, 300, 18), "", "", "Domanda", DFont.MONO_BOLD, 14f, WHITE)
        els += DText(bk(240, 950, 360, 30), "ask", "Domanda", "Salvare questi ricordi?", DFont.SANS, 22f, BLACK, DAlign.CENTER)
        for ((k, x) in listOf(270, 440).withIndex()) {
            els += DRect(bk(x, 1010, 130, 38), grey)
            els += DRect(bk(x, 1010, 130, 38), 0xFF404040, stroke = 2f)
            els += DText(bk(x, 1018, 130, 22), "b$k", "Pulsante ${k + 1}", "Sì", DFont.SANS, 18f, BLACK, DAlign.CENTER)
        }
        els += DPath(bk(540, 1030, 34, 52), "M0 0 L0 40 L10 30 L18 50 L25 47 L17 28 L32 28 Z", 34f, 52f, stroke = BLACK, width = 2f, fill = WHITE)
        return Design("x-windows", "Finestre", DesignFamily.TREND, DOrient.PORTRAIT, 794f, 1123f, ACC,
            accents = listOf(0xFF008080, 0xFF3A6EA5, 0xFFB565A7), accents2 = listOf(0xFF000080, 0xFF6B0F6B, 0xFF1F1F1F),
            els = els, motion = CollageMotion.CASCADE, hint = "Le foto aperte in finestre di un vecchio computer, con il puntatore e la domanda «Salvare questi ricordi?»")
    }

    // T8 -----------------------------------------------------------------------------------------------
    private fun dump(): Design {
        val els = arrayListOf<DEl>(
            DText(bk(30, 26, 470, 76), "title", "Titolo", "{mese} dump", DFont.HAND, 72f, BLACK),
            DText(bk(32, 106, 540, 18), "sub", "Sottotitolo", "{foto} foto · {ANNO}", DFont.MONO, 13f, BLACK, track = 0.15f, upper = true),
            DPath(bk(500, 34, 56, 56), SPARKLE, 100f, 100f, fill = ACC),
            DPath(bk(548, 96, 26, 26), SPARKLE, 100f, 100f, fill = ACC),
        )
        val rots = listOf(-1.5f, 1f, -0.5f, 2f, -2f, 0.5f, 1.5f, -1f, 0.8f)
        for (i in 0 until 9) {
            val x = 30 + (i % 3) * 183; val y = 140 + (i / 3) * 240
            els += DPhoto(bk(x, y, 174, 232), i, radius = 8f, rot = rots[i])
        }
        els += DPath(bk(210, 250, 60, 60), "M30 4 C38 22 40 24 56 30 C40 36 38 38 30 56 C22 38 20 36 4 30 C20 24 22 22 30 4 Z", 60f, 60f, stroke = ACC, width = 4f, draw = true)
        els += DPath(bk(330, 846, 120, 40), "M110 30 C80 10 50 6 14 20 M28 8 L12 20 L28 32", 120f, 40f, stroke = ACC, width = 4f, draw = true)
        els += DText(bk(30, 860, 300, 34), "note", "Nota", "il migliore? il quinto", DFont.HAND, 30f, ACC)
        return Design("x-dump", "Dump del mese", DesignFamily.TREND, DOrient.PORTRAIT, 600f, 900f, 0xFFFAF7F2,
            accents = listOf(0xFFFF4F79, 0xFF2F5BFF, 0xFFFF8C00), els = els, motion = CollageMotion.CASCADE,
            hint = "Nove foto fitte, un po' storte, con stelline e frecce a mano: il photo dump del mese che entra a ritmo")
    }

    // C1 -----------------------------------------------------------------------------------------------
    private fun paperTrip(): Design {
        val cream = 0xFFF3EBDDL; val kraft = 0xFFC8A57AL
        return Design(
            "x-paper-trip", "Viaggio di carta", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, kraft,
            accents = listOf(0xFF2A7F86, 0xFFE07A5F, 0xFF3D405B, 0xFF81B29A), motion = CollageMotion.SLIDE,
            hint = "Carta kraft, una striscia colorata strappata col nome del posto, etichetta da valigia e timbro del passaporto",
            els = listOf(
                DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x22FFFFFF, 0f),
                DTorn(bk(30, 46, 734, 668), cream, rot = -2f, seed = 3),
                DPhoto(bk(70, 86, 654, 520), 0, DShape.TORN, rot = -2f, border = 6f, seed = 4),
                DRect(bk(330, 30, 130, 36), TAPE, rot = 3f),
                DTorn(bk(-40, 560, 560, 120), ACC, rot = -4f, seed = 9),
                DText(bk(30, 576, 480, 90), "title", "Titolo", "{luogo|Lisbona}", DFont.SERIF, 86f, WHITE, line = 0.95f, rot = -4f),
                DPhoto(bk(440, 650, 310, 290), 1, DShape.TORN, rot = 5f, border = 6f, shadow = 8f, seed = 7),
                DRect(bk(560, 630, 100, 32), TAPE, rot = -30f),
                DPath(bk(70, 690, 60, 110), "M30 0 C10 40 50 70 36 110", 60f, 110f, stroke = 0xFF7A5C3A, width = 2.5f),
                DRect(bk(66, 760, 300, 160), cream, radius = 14f, rot = -6f, shadow = 8f),
                DRect(bk(84, 825, 26, 26), kraft, circle = true),
                DText(bk(126, 784, 220, 16), "tagL", "Etichetta", "Bagaglio · {ANNO}", DFont.MONO, 12f, BLACK, track = 0.15f, upper = true, rot = -6f),
                DText(bk(126, 806, 230, 90), "tag", "Destinazione", "{luogo|LIS}", DFont.ANTON, 84f, BLACK, line = 0.9f, upper = true, rot = -6f),
                DText(bk(70, 975, 440, 66), "hand", "Scritta a mano", "{Mese} {ANNO}", DFont.HAND, 54f, 0xFF3B2A1A, rot = -3f),
                DStamp(bk(540, 940, 200, 200), "stamp", "Timbro", "{luogo|Lisbona}\n{data}", 0xFFC2412D, rot = 12f),
            ),
        )
    }

    // C2 -----------------------------------------------------------------------------------------------
    private fun paperNews(): Design {
        val news = 0xFFEFEBE3L
        return Design(
            "x-paper-news", "Prima pagina", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, 0xFF1C1C1C,
            accents = listOf(0xFFE63946, 0xFF1D4ED8, 0xFFFF8C00), motion = CollageMotion.WIPE,
            hint = "La tua impresa sportiva in prima pagina: titolone, foto a retino da giornale, colonne e numeri",
            els = listOf(
                DTorn(bk(18, 18, 758, 1087), news, seed = 11, shadow = 10f),
                DText(bk(50, 52, 694, 64), "mast", "Testata", "Cronaca della Domenica", DFont.SERIF, 62f, BLACK, DAlign.CENTER),
                DRect(bk(50, 124, 694, 3), BLACK),
                DText(bk(50, 134, 694, 18), "edition", "Sottotestata", "{giorno} · Edizione speciale · {luogo|Lombardia}", DFont.MONO, 12f, BLACK, DAlign.CENTER, track = 0.1f, upper = true),
                DRect(bk(50, 160, 694, 2), BLACK),
                DText(bk(50, 176, 694, 220), "title", "Titolo", "Impresa\nsul colle!", DFont.ANTON, 118f, BLACK, line = 0.88f, upper = true),
                DPhoto(bk(50, 410, 694, 420), 0, filter = DFilter.HALFTONE, ink = BLACK, paper = news),
                DText(bk(50, 838, 694, 22), "cap", "Didascalia", "Il gruppo all'attacco dell'ultima salita", DFont.SERIF_IT, 17f, BLACK),
                DPattern(bk(50, 872, 450, 200), DPatternKind.TEXTLINES, 0xFF8C8A85, 11f),
                DRect(bk(520, 872, 224, 200), BLACK),
                DText(bk(538, 888, 190, 14), "l1", "Etichetta 1", "Distanza", DFont.MONO, 11f, WHITE, track = 0.18f, upper = true),
                DText(bk(538, 904, 190, 48), "s1", "Distanza", "142 km", DFont.ANTON, 44f, WHITE, count = true),
                DText(bk(538, 960, 190, 14), "l2", "Etichetta 2", "Dislivello", DFont.MONO, 11f, WHITE, track = 0.18f, upper = true),
                DText(bk(538, 976, 190, 48), "s2", "Dislivello", "2.340 m", DFont.ANTON, 44f, ACC, count = true),
                DText(bk(538, 1034, 190, 22), "s3", "Tempo", "Tempo 5:12", DFont.MONO_BOLD, 16f, WHITE),
                DTorn(bk(470, 330, 300, 84), ACC, rot = -8f, seed = 21),
                DText(bk(480, 346, 280, 52), "badge", "Bollino", "Record!", DFont.ANTON, 46f, WHITE, DAlign.CENTER, upper = true, rot = -8f),
                DPhoto(bk(560, 600, 200, 170), 1, DShape.TORN, rot = 4f, border = 6f, shadow = 8f, seed = 23),
            ),
        )
    }

    // C3 -----------------------------------------------------------------------------------------------
    private fun paperLove(): Design {
        val rose = 0xFF8E3B4EL
        return Design(
            "x-paper-love", "Carta pastello", DesignFamily.PAPER, DOrient.PORTRAIT, 480f, 672f, 0xFFF7EFE9,
            accents = listOf(0xFFF4C6C9, 0xFFFFD6A5, 0xFFCDB4DB, 0xFFBDE0FE), motion = CollageMotion.DROP,
            hint = "Carte pastello strappate a strati, i nomi scritti a mano e un cuore che si disegna",
            els = listOf(
                DTorn(bk(-30, -30, 540, 250), ACC, rot = -5f, seed = 31, edges = 4),
                DTorn(bk(-30, 440, 540, 280), 0xFFCFDDC8, rot = 4f, seed = 33, edges = 1),
                DTorn(bk(300, 330, 220, 170), 0xFFF3E3C8, rot = 10f, seed = 35),
                DPhoto(bk(60, 70, 330, 380), 0, DShape.TORN, rot = -3f, border = 6f, shadow = 8f, seed = 37),
                DRect(bk(170, 52, 110, 30), TAPE, rot = -4f),
                DPhoto(bk(290, 360, 150, 150), 1, DShape.TORN, rot = 7f, border = 5f, shadow = 8f, seed = 39),
                DText(bk(24, 500, 432, 64), "names", "Nomi", "Giulia & Marco", DFont.HAND, 58f, rose, DAlign.CENTER, rot = -2f),
                DText(bk(24, 572, 432, 18), "date", "Data", "{data_romana}", DFont.SANS, 12f, 0xFF5A3A3A, DAlign.CENTER, track = 0.42f),
                DPath(bk(388, 452, 66, 58), "M33 54 C10 38 2 24 6 14 C10 4 24 2 33 14 C42 2 56 4 60 14 C64 24 56 38 33 54 Z", 66f, 58f, stroke = rose, width = 3f, draw = true),
            ),
        )
    }

    // C4 -----------------------------------------------------------------------------------------------
    private fun paperRecipe(): Design {
        val ink = 0xFF2B2B2BL
        return Design(
            "x-paper-recipe", "Ricetta", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, 0xFFB98B5E,
            accents = listOf(0xFFC0392B, 0xFF2B59FF, 0xFF2D6A4F), motion = CollageMotion.DROP,
            hint = "Il foglio del quaderno strappato, le foto col nastro, ingredienti e procedimento scritti a mano",
            els = listOf(
                DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x22FFFFFF, 0f),
                DTorn(bk(36, 40, 722, 1050), 0xFFFBF8F0, rot = -1f, seed = 41, edges = 1, shadow = 12f),
                DPattern(bk(36, 40, 722, 1050), DPatternKind.LINES, 0x552B6CB0, 34f, rot = -1f),
                DText(bk(110, 80, 560, 190), "title", "Titolo", "La pasta\ndella nonna", DFont.HAND, 86f, ACC, line = 0.95f),
                DPhoto(bk(430, 280, 290, 290), 0, rot = 5f, border = 10f, bottomPad = 34f, shadow = 12f),
                DRect(bk(520, 264, 110, 32), TAPE, rot = -6f),
                DText(bk(110, 300, 300, 40), "ingL", "Etichetta", "Ingredienti", DFont.SERIF_IT, 34f, BLACK),
                DText(bk(110, 346, 300, 200), "ing", "Ingredienti", "· 400 g di farina\n· 4 uova\n· un pizzico di sale\n· tanta pazienza", DFont.HAND, 34f, ink, line = 1.2f),
                DPhoto(bk(110, 620, 280, 220), 1, rot = -4f, border = 10f, shadow = 12f),
                DRect(bk(190, 604, 100, 30), TAPE, rot = 5f),
                DText(bk(430, 600, 300, 40), "stepL", "Etichetta 2", "Procedimento", DFont.SERIF_IT, 34f, BLACK),
                DText(bk(430, 646, 300, 330), "steps", "Procedimento", "Fontana di farina, uova al centro, impasta finché è liscia. Riposo mezz'ora, poi tira la sfoglia sottile.",
                    DFont.HAND, 30f, ink, line = 1.15f, wrap = true),
                DStamp(bk(110, 880, 170, 170), "stamp", "Timbro", "Fatto in casa\n{ANNO}", ACC, rot = -10f),
            ),
        )
    }

    // C5 -----------------------------------------------------------------------------------------------
    private fun paperWall() = Design(
        "x-paper-wall", "Manifesti", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, 0xFF8F8C86,
        accents = listOf(0xFFD93A2B, 0xFF1D4ED8, 0xFF16A34A, 0xFFE91E63), motion = CollageMotion.SLIDE, grain = true,
        hint = "Manifesti incollati al muro e strappati, uno sopra l'altro: città, concerti, notti fuori",
        els = listOf(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x22000000, 0f),
            DTorn(bk(560, 40, 160, 60), 0xFFEDE7DA, rot = 8f, seed = 61),
            DTorn(bk(-30, 30, 560, 560), 0xFFF2C230, rot = -3f, seed = 51),
            DText(bk(20, 80, 360, 260), "title", "Titolo", "Sabato\nnotte", DFont.ANTON, 118f, BLACK, line = 0.86f, upper = true, rot = -3f),
            DPhoto(bk(400, 110, 440, 620), 0, DShape.TORN, rot = 2f, border = 7f, shadow = 10f, filter = DFilter.BW, seed = 53),
            DTorn(bk(-40, 610, 620, 230), ACC, rot = 4f, seed = 55),
            DText(bk(10, 650, 520, 90), "city", "Città", "{luogo|Milano}", DFont.ARCHIVO, 84f, WHITE, upper = true, rot = 4f),
            DText(bk(14, 748, 520, 24), "info", "Informazioni", "{data} · ingresso libero", DFont.MONO_BOLD, 18f, WHITE, track = 0.1f, rot = 4f),
            DPhoto(bk(430, 700, 340, 340), 1, DShape.TORN, rot = -5f, border = 7f, shadow = 10f, seed = 57),
            DPhoto(bk(40, 860, 340, 230), 2, DShape.TORN, rot = -2f, border = 7f, shadow = 10f, filter = DFilter.BW, seed = 59),
            DTorn(bk(370, 1040, 170, 60), 0xFFF2C230, rot = -6f, seed = 63),
        ),
    )

    // C6 -----------------------------------------------------------------------------------------------
    private fun paperRansom() = Design(
        "x-paper-ransom", "Lettere ritagliate", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, 0xFFF2EFE8,
        accents = listOf(0xFFE63946, 0xFF1D4ED8, 0xFF111111), motion = CollageMotion.DROP,
        hint = "La scritta fatta di lettere ritagliate da giornali diversi: ogni lettera arriva da sola nel video",
        els = listOf(
            DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x14000000, 0f),
            DRansom(bk(40, 60, 714, 380), "title", "Lettere ritagliate", "Ciao da\n{luogo|Roma}"),
            DPhoto(bk(60, 470, 420, 540), 0, DShape.TORN, rot = -3f, border = 7f, shadow = 10f, seed = 71),
            DRect(bk(210, 452, 120, 34), TAPE, rot = -5f),
            DPhoto(bk(440, 520, 310, 400), 1, DShape.TORN, rot = 5f, border = 7f, shadow = 10f, seed = 73),
            DPath(bk(520, 920, 200, 120), "M10 100 C60 60 120 50 180 20 M160 12 L182 20 L172 42", 200f, 120f, stroke = ACC, width = 5f, draw = true),
            DText(bk(470, 1000, 300, 70), "hand", "Scritta a mano", "baci, {ANNO}", DFont.HAND, 54f, ACC, rot = -4f),
        ),
    )

    // C7 -----------------------------------------------------------------------------------------------
    private fun paperSticker() = Design(
        "x-paper-sticker", "Adesivo", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, ACC,
        accents = listOf(0xFFFF6B35, 0xFF2F5BFF, 0xFFFFC300, 0xFFEF476F), accents2 = listOf(0xFF1B998B, 0xFF111111, 0xFF7B2CBF, 0xFFF7F3EA),
        motion = CollageMotion.CASCADE,
        hint = "La persona scontornata come un adesivo col bordo bianco, sopra carte colorate strappate e un titolo enorme",
        els = listOf(
            DTorn(bk(-40, 560, 880, 600), ACC2, rot = -6f, seed = 81, edges = 1),
            DTorn(bk(-40, -40, 520, 150), 0xFFF7F3EA, rot = 3f, seed = 83, edges = 6),
            DText(bk(40, 44, 420, 30), "kicker", "Rubrica", "Diario · {Mese} {ANNO}", DFont.MONO_BOLD, 16f, BLACK, track = 0.15f, upper = true, rot = 3f),
            DText(bk(20, 130, 754, 260), "title", "Titolo", "{stagione|estate}", DFont.ANTON, 250f, WHITE, DAlign.CENTER, line = 0.85f, upper = true),
            DPhoto(bk(110, 230, 574, 760), 0, radius = 30f, border = 16f, shadow = 12f, cutout = true),
            DText(bk(470, 980, 300, 44), "tag", "Etichetta", "{luogo|Puglia}", DFont.GROTESK, 30f, BLACK, rot = -4f, pill = WHITE, pad = 18f),
            DText(bk(60, 1000, 400, 70), "hand", "Scritta a mano", "noi, così.", DFont.HAND, 58f, WHITE, rot = -3f),
            DPath(bk(640, 250, 70, 70), SPARKLE, 100f, 100f, fill = WHITE),
            DPath(bk(84, 330, 50, 50), SPARKLE, 100f, 100f, fill = WHITE),
            DPath(bk(560, 900, 170, 44), "M0 22 C20 0 40 44 60 22 C80 0 100 44 120 22 C140 0 160 44 170 22", 170f, 44f, stroke = WHITE, width = 5f, draw = true),
            DPath(bk(70, 860, 120, 110), "M10 10 C40 60 70 80 110 96 M92 80 L112 98 L88 104", 120f, 110f, stroke = WHITE, width = 5f, draw = true),
        ),
    )

    // C8 -----------------------------------------------------------------------------------------------
    private fun paperRiso(): Design {
        val paper = 0xFFF4EFE6L
        return Design(
            "x-paper-riso", "Riso", DesignFamily.PAPER, DOrient.PORTRAIT, 794f, 1123f, paper,
            accents = listOf(0xFFFF48B0, 0xFFFF6C2F, 0xFFFFE800), accents2 = listOf(0xFF0078BF, 0xFF00A95C, 0xFF3D2B56),
            motion = CollageMotion.WIPE, grain = true,
            hint = "Poster stampato in risografia: due inchiostri a retino un po' fuori registro, carta con la grana",
            els = listOf(
                DPattern(b(0, 0, 794, 1123, F), DPatternKind.PAPER, 0x1A000000, 0f),
                DText(bk(50, 40, 700, 150), "title", "Titolo", "{luogo|Tokyo}", DFont.GROTESK, 150f, ACC2, line = 0.85f, track = -0.04f),
                DPhoto(bk(60, 190, 674, 640), 0, filter = DFilter.HALFTONE, ink = ACC, paper = paper),
                DPhoto(bk(60, 190, 674, 640), 0, filter = DFilter.HALFTONE, ink = ACC2, paper = 0L, multiply = true, shiftX = 5f, shiftY = 4f),
                DText(bk(54, 846, 520, 40), "sub", "Sottotitolo", "Notti al neon, {Mese} {ANNO}", DFont.MONO_BOLD, 22f, ACC, upper = true, track = 0.08f),
                DPhoto(bk(60, 900, 200, 200), 1, DShape.CIRCLE, filter = DFilter.HALFTONE, ink = ACC2, paper = 0xFFFFD6E8),
                DText(bk(280, 958, 220, 90), "info", "Informazioni", "{giorni|3} giorni\n2 persone\n1 rullino", DFont.MONO, 16f, ACC2, line = 1.5f),
                DText(bk(470, 860, 280, 220), "num", "Numero grande", "{AA}", DFont.ANTON, 230f, ACC2, DAlign.RIGHT, line = 0.9f, stroke = 3f),
            ),
        )
    }

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
                DText(b(606, 536, 170, 84), "note", "Nota", "e poi\n{luogo4|Parigi}!", DFont.HAND, 40f, ACC, line = 1f, rot = -6f),
            ),
        )
    }

    // 8 ------------------------------------------------------------------------------------------------
    private fun stickers() = Design(
        "x-stickers", "Adesivi", DesignFamily.SCRAP, DOrient.PORTRAIT, 384f, 576f, ACC,
        accents = listOf(0xFF2F5BFF, 0xFFFF5A36, 0xFF16A34A, 0xFF111111), motion = CollageMotion.DROP,
        hint = "Foto come adesivi col bordo bianco su un colore pieno, con stella e bollino",
        els = listOf(
            DPhoto(b(31, 43, 192, 232, K), 0, radius = 13f, rot = -7f, border = 9f, shadow = 10f),
            DPhoto(b(185, 159, 172, 142, K), 1, radius = 13f, rot = 8f, border = 9f, shadow = 10f),
            DPhoto(b(69, 309, 182, 132, K), 2, DShape.PILL, rot = -3f, border = 9f, shadow = 10f),
            DPath(b(250, 22, 104, 104, K), "M50 2 L61 34 L95 34 L68 55 L78 90 L50 69 L22 90 L32 55 L5 34 L39 34 Z", 100f, 100f, stroke = WHITE, width = 5f, fill = 0xFFFFD23F, rot = 12f),
            DText(b(250, 62, 104, 26, K), "star", "Stella", "wow", DFont.HAND, 23f, BLACK, DAlign.CENTER, rot = 12f),
            DRect(b(262, 336, 96, 96, K), 0xFFFF7AB8, circle = true, rot = -12f),
            DRect(b(262, 336, 96, 96, K), WHITE, circle = true, stroke = 5f),
            DText(b(262, 362, 96, 44, K), "badge", "Bollino", "{stagione}\n'{AA}", DFont.HAND, 24f, BLACK, DAlign.CENTER, line = 0.9f, rot = -12f),
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
            DText(b(859, 156, 200, 36), "year", "Anno", "{ANNO}", DFont.MONO_BOLD, 32f, ink, DAlign.RIGHT),
            DPath(b(0, 0, 1123, 1587, F), "M215 455 C500 420 700 500 745 645 C790 800 400 780 275 955 C170 1100 600 1150 785 1205", 1123f, 1587f,
                stroke = ACC2, width = 7f, dash = listOf(1f, 20f), draw = true),
        )
        stops.forEachIndexed { i, st ->
            val px = st[0] as Int; val py = st[1] as Int; val lx = st[2] as Int; val ly = st[3] as Int; val right = st[4] == 1
            els += DPhoto(b(px, py, 234, 234, K), i, DShape.CIRCLE, border = 8f, shadow = 10f)
            val al = if (right) DAlign.RIGHT else DAlign.LEFT
            val w = if (right) 320 else 400
            els += DText(b(lx, ly - 8, w, 30, K), "", "", "0${i + 1}", DFont.MONO_BOLD, 26f, ACC2, al)
            els += DText(b(lx, ly + 26, w, 56, K), "c$i", "Tappa ${i + 1}", "{luogo$i|${st[5]}}", DFont.GROTESK, 62f, ink, al, line = 0.9f)
            els += DText(b(lx, ly + 90, w, 28, K), "d$i", "Giorni tappa ${i + 1}", st[6] as String, DFont.MONO, 24f, ink, al)
        }
        els += DRect(b(64, 1481, 995, 3, E), ink)
        els += DText(b(64, 1502, 330, 32, E), "f1", "Piede sinistra", "{n_luoghi|4} città", DFont.MONO, 26f, ink)
        els += DText(b(396, 1502, 330, 32, E), "f2", "Piede centro", "{giorni|11} giorni", DFont.MONO, 26f, ink, DAlign.CENTER)
        els += DText(b(729, 1502, 330, 32, E), "f3", "Piede destra", "2.134 km in treno", DFont.MONO, 26f, ink, DAlign.RIGHT)
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
