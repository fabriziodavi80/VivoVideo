package it.fabriziodavi.vivovideo.model

import android.content.Context

/**
 * Collaudo guidato: le prove da fare sul telefono prima della versione ufficiale.
 * Ogni prova dice come farla e cosa deve succedere; l'esito (funziona / problema + nota) resta salvato.
 */
object TestPlan {
    data class Item(val id: String, val area: String, val title: String, val how: String, val expect: String)

    val items: List<Item> = listOf(
        // Base
        Item("base_import", "Base", "Progetto con video 4K/8K e foto",
            "Home → Nuovo progetto → scegli 5–6 video (anche 4K a 120 fps o 8K) e qualche foto.",
            "Le clip compaiono nella timeline con le miniature; nessun errore."),
        Item("base_edit", "Base", "Tagliare, dividere, spostare, annullare",
            "Accorcia una clip con le maniglie, Dividi all'indicatore, tieni premuta una clip e trascinala in un altro punto, poi Annulla e Ripeti.",
            "Ogni modifica si vede subito; la clip trascinata va dove la lasci (le altre si aprono); Annulla torna indietro di un passo."),
        Item("base_album", "Base", "Album interi",
            "Nuovo progetto → Album interi → scegli un album.",
            "Arrivano tutte le foto e i video dell'album, in ordine di data."),
        Item("base_auto_pick", "Base", "Video automatico a passi con foto scelte",
            "Home → Video automatico → Foto e video: scegli 8–10 foto una per una (anche qualcuna nell'altro verso) → Avanti → uno stile → Avanti → un brano → Crea il video.",
            "Ci sono tutte le foto scelte, nell'ordine di data; quelle nell'altro verso si vedono intere sullo sfondo sfocato; nessuna troppo ingrandita."),
        // Anteprima
        Item("prev_4k", "Anteprima", "Anteprima fluida con video pesanti",
            "Con un video 4K a 120 fps o 8K, aspetta la scritta \"Preparo le copie leggere…\", poi fai partire l'anteprima.",
            "Scorre senza scatti; nessun messaggio rosso."),
        Item("prev_full", "Anteprima", "Schermo intero e barra",
            "Tocca schermo intero, trascina la barra di avanzamento.",
            "Il video segue il dito; si torna indietro col tasto Indietro."),
        Item("prev_cross", "Anteprima", "Dissolvenza incrociata in anteprima",
            "Transizione Incrociata su un taglio, aspetta qualche secondo (\"Preparo un tratto…\").",
            "In anteprima le due clip sfumano l'una nell'altra senza salti."),
        Item("prev_pip", "Anteprima", "Video sovrapposto che si muove",
            "Livelli → aggiungi un video sopra, aspetta che il tratto sia pronto.",
            "In anteprima il video sovrapposto si muove (prima è un'immagine ferma)."),
        Item("prev_trend", "Anteprima", "Look 2026: velocity edit",
            "Video automatico con tema Energico o Estremo e un brano con un ritornello forte; poi Effetti → Di tendenza su una clip.",
            "Molti tagli netti sul battito, blackout e flash brevi sui colpi, titolo bianco pulito; anteprima fluida, niente cubi o vortici."),
        // Esportazione
        Item("exp_4k", "Esportazione", "Esportazione 4K H.265",
            "Esporta → Pronto per: YouTube 4K → Esporta.",
            "Finisce senza errori; il video in galleria si vede bene, audio a posto e sincronizzato."),
        Item("exp_social", "Esportazione", "Reels e WhatsApp",
            "Esporta con Reels e TikTok, poi con WhatsApp; confronta il peso stimato con quello vero.",
            "Il peso reale è vicino a quello stimato; il file WhatsApp è leggero."),
        Item("exp_hdr", "Esportazione", "HDR a 10 bit",
            "Progetto con un video HDR del telefono (anche Dolby Vision) → Esporta → Risoluzione Originale (o 4K) → HDR 10 bit acceso.",
            "Nel risultato la riga Colore dice HDR; in galleria il video ha luci piene come l'originale."),
        Item("exp_long", "Esportazione", "Progetto lungo",
            "Un progetto di 10 minuti o più (4K) con musica e testi → esporta.",
            "Arriva in fondo senza chiudersi; durata giusta."),
        // Audio
        Item("aud_library", "Audio", "Musica dalla libreria online",
            "Musica → Libreria online → cerca un genere, ascolta, usa un brano.",
            "Risultati in pochi secondi, il brano entra nella timeline con il credito."),
        Item("aud_voice", "Audio", "Voce fuori campo e ducking",
            "Musica → Voce → registra 10 s parlando, con un brano sotto e il ducking acceso.",
            "La voce è nitida e a volume costante (frasi forti e deboli simili, niente fruscio fra le parole); in anteprima (dopo un attimo) e nel video esportato la musica si abbassa quando parli, poi risale."),
        Item("aud_beats", "Audio", "Tagli a ritmo",
            "Musica → Battiti → trova i battiti, poi Ascolta e tocca a tempo.",
            "I tagli cadono sui colpi della musica; nella timeline il bordo di ogni clip sta sotto la sua tacca arancione del righello, anche dopo centinaia di clip e con lo zoom più lontano."),
        Item("aud_mixer", "Audio", "Mixer audio",
            "Progetto con video parlato, una voce fuori campo e un brano → Audio → Mixer audio. Fai partire l'ascolto: abbassa la musica di 6 dB, metti Voce nitida e compressore Medio sulla voce, prova M e S. Esporta.",
            "Gli indicatori si muovono; ogni modifica si sente subito; M toglie il gruppo, S lascia solo quello; il video esportato suona come l'anteprima, senza distorsioni."),
        Item("aud_cut", "Audio", "Taglia pause e ehm",
            "Un video parlato di 1–2 minuti con pause e qualche \"ehm\" → Audio → Clip → Taglia pause e ehm: 0,7 s, Togli anche ehm → Analizza → nella lista ascolta qualche ehm con ▶ e ✂ ▶ → Taglia.",
            "Ogni voce della lista è davvero un ehm e ✂ ▶ lo toglie senza mozzare le parole vicine; dopo il taglio resta un attimo di respiro e gli ehm sono spariti; Annulla torna com'era."),
        // Testo
        Item("txt_styles", "Testo", "Stili e animazioni WOW",
            "Testo → aggiungi → Stili pronti, prova Glitch, Onda, Oro, Cascata.",
            "Lo stile scelto si illumina; le animazioni sono fluide anche in anteprima."),
        Item("txt_subs", "Testo", "Sottotitoli automatici",
            "Su una clip in cui si parla: Testo → Sottotitoli automatici → Veloce, italiano, Karaoke.",
            "Le parole sono giuste e a tempo; la parola detta si colora."),
        Item("txt_font", "Testo", "Font tuoi",
            "Testo → Stile → + Aggiungi font, scegli un .ttf scaricato.",
            "Il testo cambia font in anteprima e nel video esportato."),
        Item("txt_credits", "Testo", "Titoli di coda",
            "Testo → Titoli di coda: un titolo e 6–8 righe, Su nero, dopo il video → Metti i titoli di coda; guarda l'anteprima, poi esporta.",
            "Il rullo sale regolare e si legge bene, l'ultima scena sfuma nel nero; uguale nel video esportato."),
        // Colore
        Item("col_clip", "Colore", "Colore per clip e istogramma",
            "Colore → Base, Ruote, HSL su una clip; Copia/Applica a tutte.",
            "L'istogramma segue le modifiche; il video esportato ha gli stessi colori dell'anteprima."),
        // Strumenti
        Item("tool_speed", "Strumenti", "Velocità a curva",
            "Velocità → Curva su un video a 120 fps, esporta.",
            "Rallenty fluido; l'audio segue (tono naturale)."),
        Item("tool_freeze", "Strumenti", "Fermo immagine",
            "Tieni premuta una clip → Fermo immagine all'indicatore.",
            "Compare una foto di 2 s nitida, uguale al fotogramma."),
        Item("tool_reverse", "Strumenti", "Al contrario",
            "Tieni premuta una clip di 10–20 s → Al contrario.",
            "Il video e l'audio vanno all'indietro; Di nuovo nel verso normale riporta l'originale."),
        Item("tool_stab", "Strumenti", "Stabilizzazione",
            "Una clip girata camminando → Inquadra → Stabilizza la clip; prova la Forza.",
            "L'immagine trema molto meno; lo zoom resta moderato."),
        Item("tool_multi", "Strumenti", "Selezione multipla e incolla attributi",
            "Tieni premuta una clip con effetti e colore → Copia attributi → tocca nella timeline le clip su cui incollare (o Tutte) → Incolla attributi.",
            "Le clip scelte prendono effetti e colore; un Annulla torna indietro."),
        Item("tool_tutorial", "Strumenti", "Tutorial: zoom, frecce, passi",
            "Modifica → Inquadra → Zoom su un punto: 2× su un angolo. Livelli → Freccia e Numero. Testo → Passo numerato.",
            "Lo zoom arriva e torna morbido senza bordi neri; freccia e numero nitidi e spostabili; i numeri vanno avanti (1, 2, 3)."),
        // Intelligenti
        Item("ai_auto", "Intelligenti", "Video automatico con regia intelligente",
            "Video automatico → 2 album → stile → brano online → durata Come il brano.",
            "Il video dura quanto il brano, i tagli sono a tempo, le scene sono le parti migliori; Altra proposta ne fa un'altra."),
        Item("ai_best", "Intelligenti", "Momento migliore",
            "Accorcia una clip lunga a 3 s → tieni premuto → Momento migliore (anche 2–3 volte).",
            "Sceglie tratti nitidi e con persone, non quelli mossi."),
        Item("ai_follow", "Intelligenti", "Segui il soggetto",
            "Progetto verticale con un video orizzontale di una persona che si muove → Inquadra → Segui il soggetto.",
            "La persona resta al centro, il movimento dell'inquadratura è morbido."),
        Item("ai_person", "Intelligenti", "Scontorno della persona",
            "Clip con una persona → Effetti → Persona → Scontorna, poi Sfoca lo sfondo / colore / bianco e nero.",
            "Il bordo della persona è pulito (capelli compresi più o meno), lo sfondo cambia; uguale nel video esportato."),
        Item("ai_privacy", "Intelligenti", "Privacy: volti, targhe, oggetti",
            "Un video di 10–20 s con persone e un'auto con la targa leggibile → Effetti → Privacy → Volti e Targhe → Analizza e oscura; poi Rifai aggiungendo un oggetto toccato nell'immagine. Esporta.",
            "Volti e targhe coperti per tutta la clip anche mentre si muovono, l'oggetto seguito; nessun buco evidente; uguale nel video esportato."),
        // Progetto
        Item("prj_backup", "Progetto", "Copia di sicurezza",
            "Progetto → Copia di sicurezza (leggera), salvala; poi Home → Apri una copia.",
            "Il progetto si riapre identico."),
        Item("prj_relink", "Progetto", "File spostato",
            "Con Gestione file copia uno dei video del progetto in un'altra cartella (per esempio Download), poi cancella l'originale dalla Galleria. Torna nell'app → \"file non trovati · Ricollega\" → Cerca nel telefono.",
            "Compare l'avviso dei file non trovati; il video viene ritrovato per nome e il progetto torna a posto. (Spostando con la Galleria Android di solito segue il file da solo: nessun avviso, ed è giusto così.)"),
        Item("prj_template", "Progetto", "Modello",
            "Progetto riuscito → Progetto → Salva come modello; Home → Da un modello → scegli altri video.",
            "Il nuovo progetto ha lo stesso montaggio con i video nuovi."),
    )

    data class Result(val state: Boolean? = null, val note: String = "")

    /** Esiti salvati sul telefono. */
    class Store(ctx: Context) {
        private val p = ctx.getSharedPreferences("collaudo", Context.MODE_PRIVATE)

        fun get(id: String) = Result(
            state = if (p.contains("s_$id")) p.getBoolean("s_$id", false) else null,
            note = p.getString("n_$id", "") ?: "",
        )

        fun all(): Map<String, Result> = items.associate { it.id to get(it.id) }

        fun set(id: String, state: Boolean?, note: String = get(id).note) {
            val e = p.edit()
            if (state == null) e.remove("s_$id") else e.putBoolean("s_$id", state)
            e.putString("n_$id", note).apply()
        }

        fun reset() = p.edit().clear().apply()
    }
}
