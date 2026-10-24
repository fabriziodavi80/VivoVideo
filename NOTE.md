# VivoVideo · note per riprendere il lavoro

Aggiornate al 21/10/2026, ultima versione **v0.3.190**. Da leggere insieme a `CLAUDE.md`.

## 1. Stato attuale dell'app

App Android nativa di montaggio video in stile CapCut, pensata per il vivo X300 Ultra.
Base tecnica: Kotlin, Jetpack Compose, Media3 1.11.1, minSdk 33, solo arm64-v8a in release.

### Editor
- Timeline con clip, testi, livelli e brani.
- Taglio, divisione, spostamento, Annulla/Ripeti; salvataggio automatico; uso in orizzontale e a schermo intero.
- Keyframe (zoom, posizione, rotazione, opacità); Inquadra con Riempi, Adatta e Sfocato.
- Velocità: Normale, Lenta, Veloce e Curva (speed ramp), con l'audio che mantiene il tono.
- Colore: base, ruote, HSL, istogramma, LOG.
- Effetti: classici, WOW (28), A ritmo (ZOOMBEAT, SHAKEBEAT, RGBBEAT, IMPACT, GLOWBEAT), natura e romantici.
- Transizioni classiche più 20 premium, da PUNCH a REWIND.
- Testi con stili pronti, animazioni per lettera, font personali (.ttf) e sottotitoli automatici con karaoke.
- Strumenti di clip:
  - fermo immagine;
  - al contrario (massimo 60 s e 1080p);
  - stabilizzazione;
  - selezione multipla con copia e incolla degli attributi.
- Funzioni intelligenti (ML Kit, tutto sul telefono):
  - momento migliore;
  - segui il soggetto;
  - scontorno della persona (sfondo sfocato, a colore o in bianco e nero; persona sola nei livelli).
- Audio:
  - libreria online (Jamendo e Freesound) con il credito del brano;
  - voce fuori campo con ducking;
  - rilevamento dei battiti e dell'energia del brano.
- Progetto:
  - copie di sicurezza ed esportazione/importazione;
  - ricollega i file spostati (ricerca per nome);
  - modelli;
  - "Segnala un problema" (rapporto senza chiavi);
  - Collaudo guidato con 30 prove.

### Esportazione
- Preset YouTube 4K, YouTube HD, Reels/TikTok e WhatsApp, con stima del peso.
- H.265, HDR a 10 bit (se fallisce, riprova in SDR), loudness.

### Anteprima
- Copie leggere a 720p per i video pesanti.
- Dissolvenze preparate in anticipo.
- Schermo sempre acceso durante la riproduzione.

### Video automatico
- 20 temi:
  - Cinema, Energico, Virale, Ricordi, Sogno, Elegante, Viaggio;
  - Blockbuster, Neon, Estate, Azione, Estremo;
  - Romantico, Matrimonio, Super8, Anni90;
  - Natura, Mare, Montagna, Diario.
- Durata "come il brano", con tagli sui battiti.
- Il brano decide il montaggio: scene più corte dove spinge, drop con transizione d'impatto, intro a raffica, momento eroe, finale lento.
- Regia intelligente: momenti migliori, volti, orientamento.
- Data della videocamera nei temi vintage.
- "Altra proposta" e cambio di stile.

### Prima novità dopo la 1.0: Jump cut pronti (deciso dall'utente il 24/10/2026)
- Audio → Clip → Jump cut: usa `SpeechCuts` (pause ed "ehm") e a ogni taglio alterna l'inquadratura (normale / +12–15% centrata sul volto con il riconoscimento volti già presente).
- Stili: Pulito (tagli + zoom alternato), Dinamico (zoom più deciso, punch-in sulle frasi importanti), Social (in più lampo o "whoosh" su qualche taglio e sottotitoli a parole).
- Micro-dissolvenze audio sui tagli, riepilogo prima di confermare, un solo Annulla.

### Dopo la 1.0: transizioni dal video segnalato dall'utente (24/10/2026)
- Link: https://youtube.com/watch?v=0putmJCyM1I — "12 Essential Transitions for Editing Your Videos | DaVinci Resolve Tutorial" (Gianmarco Capri).
- Capitoli: fade in/out, dissolve, iris, wipe, invisible cut, speed ramp, steps, mask passing, whip pan, luma fade, overlay, preset transition.
- Già presenti in qualche forma: dissolvenza/incrociata, iris, luma, whip (SNAPZOOM/DRIFT), speed ramp. Da sviluppare probabilmente: invisible cut (taglio nascosto in un movimento o un oggetto che copre il quadro), mask passing (passaggio attraverso un oggetto in primo piano, con lo scontorno), steps (passi a scatti), wipe moderni, overlay. Chiedere all'utente quali gli sono piaciute.

### Da riprendere dopo il collaudo
- Stabilizzazione (v0.3.183–184: zoom 4–12%, percorso vincolato, misura 384 px sub-pixel Lucas-Kanade, rotazione ±1°): approvata ma "miglioramento non sensazionale". Idee: correzione rolling shutter, zoom variabile nel tempo, omografia invece di spostamento+rotazione.
- Ricollega (v0.3.177–182): osservatore della galleria, ricerca per nome, peso, durata+risoluzione; collaudo OK.
- Scontorno della persona (v0.3.175–176: maschera 256, filtro guidato, smussatura; modello "Preciso" Google subject segmentation). L'utente lo accetta ma "non è il top": non convincono **alcuni bordi della persona e i capelli**. Idee: pennello di ritocco (aggiungi/togli con propagazione ai fotogrammi vicini), filtro guidato a colori/a due passaggi, modello di matting più pesante incluso nell'app.

### Audio, mixer, timeline (v0.3.163–v0.3.174)
- Voce fuori campo: `VoiceRecorder` (AudioRecord PCM, fonte MIC poi VOICE_RECOGNITION) + `VoiceChain` (passa-alto, EQ, espansore, livellatore, compressore, -14 LUFS, limitatore), WAV in `files/voice`.
- Ducking: `AudioDsp.voiceMask`/`clipSpeechMask`/`duckGainsFrom(hold, release)`; in anteprima `DuckPreview` + `DuckAudioProcessor` (posizione da `StreamMetadata.positionOffsetUs`).
- Mixer (`model/Mix.kt`, `engine/BusDsp.kt`, `ui/MixerScreen.kt`): gruppi CLIPS/VOICE/MUSIC/DETACHED/OVERLAY; export con mix float32 (`AudioStretch` → `finish`: LUFS per destinazione, `PeakLimiter`, al massimo +4 dB oltre il picco).
- Anteprima: NIENTE effetti audio a livello di Composition (sperimentali in Media3 1.11, hanno distorto in v0.3.173). Canale del mixer solo se serve: guadagno/bilanciamento con `ChannelMixingAudioProcessor`, EQ/compressore o mixer aperto (`factory.metering`) con `MixAudioProcessor`.
- Prima voce sopra un brano: musica -6 dB e ducking acceso.
- Timeline: trascinamento clip col dito (`Reorder`, `Project.moveClipTo`, testi/livelli seguono), tag `timelineClips` (prove con `useUnmergedTree`).
- Copie leggere delle foto per l'anteprima (`Proxies.makePhoto`, lato 2160). Ricordi senza BURN/FLASH/LEAK/DREAMY.

### Collaudo e correzioni (v0.3.157–v0.3.162)
- Collaudo guidato (Progetto → Collaudo, `TestPlan`, 36 prove): il rapporto si copia per la chat o si salva in Download (`copyReport`, `saveReportToDownloads`); l'utente incolla il rapporto in chat.
- Guida con indice, ricerca e argomenti (`GuideIndex`, `GuideScreen`), dal "?" dell'editor si apre l'argomento dello strumento (`topicFor`).
- Schermo intero: la barra segue il dito (`scrubTo`/`endScrub`, ogni 120 ms).
- Timeline: righe testi/livelli più alte (`OVER_H`), maniglie fuori dal blocco, conta solo lo spostamento orizzontale; Inizia qui/Finisci qui (`snapToPlayhead`).
- Ricordi: tolto il tremolio (FILM35 non varia più la luce per fotogramma; grana FILM35 e GRAIN con hash annidato, prima era lo stesso disegno spostato a ogni fotogramma), FILM35 0,45 e LIGHTLEAK 0,25.
- Esiti collaudo v0.3.161: OK base, schermo intero, dissolvenza, libreria, stili WOW; KO solo tremolio Ricordi (corretto in v0.3.162).

### Modifiche v0.3.148–v0.3.156
- Interfaccia: Home con "Video automatico" e "Monta tu" grandi, "Da un modello" accanto, servizi in fondo. Video automatico in 3 passi (`AutoScreen`: Cosa usare / Stile / Musica e dettagli, `STYLE_GROUPS`), scelta di singole foto e video (`MediaGrid`, `autoCreate(handPicked)`).
- Taglia pause e ehm (Audio → Clip, `SpeechCutScreen`): `SpeechCuts` (silenzi con soglia adattiva, filler da Whisper con `FILLER_PROMPT`/initial_prompt), `Project.cutTimeline` + `shiftAfterCut`.
- Titoli di coda (Testo → Titoli di coda, `CreditsScreen`): `TextItem.roll` + `rollSettings` (ancore dell'overlay), `Credits`, clip nera `Credits.BLACK_ID`.
- Tutorial: `Tutorial.zoomKeyframes` (Inquadra, griglia 3×3), `TutorialMarks` (freccia, cerchio, riquadro, numero come livelli), stile PASSO.
- Tema Grunge (gruppo Rétro) e stile STAMPA.
- Privacy (Effetti → Privacy, `ObjectPickScreen`): `Redactor` (ML Kit volti, testo per le targhe, oggetti in STREAM_MODE, 10 fps, file), `RedactEffect` (12 riquadri, pixel/sfocato/nero), `Clip.redact`.
- GitHub Actions: concorrenza separata per build ed emulatore (l'emulatore vecchio si annulla).
- Emulatore v0.3.156: 31 su 33; falliti `onlineLibraryOpenverse` (timeout di rete) e `exportWowTransitionsAndTexts` (decodificatore dell'emulatore). Tutte le prove nuove passate.
- Da verificare sul telefono: maschere privacy su video veri, taglio degli "ehm", zoom tutorial, titoli di coda.

### Modifiche v0.3.146–v0.3.147
- Natura con zoom morbido e scivolata approvato ("abbastanza delicato, ci sta").
- Foto troppo ingrandite in tutti i temi: MotionStyle ora al massimo +10%; scatti a ritmo a gradini con tetto (10%, 6% temi delicati);
  foto che riempiendo perderebbero oltre il 30% (`AutoEdit.fillKeeps` < `MIN_FILL_KEEP`) vanno su sfondo sfocato, intere; niente più panoramica sulle foto nell'altro verso.
- Exporter: anche `ERROR_CODE_ENCODING_FAILED` si riprova con impostazioni più prudenti.
- Emulatore v0.3.147: 30 su 31; fallisce a volte `previewPlaysWow` (anteprima che rallenta dopo 3 s, grafica software dell'emulatore). Da verificare sul telefono la fluidità dell'anteprima con transizioni WOW.

### Modifiche v0.3.145
- Energico 2026 approvato dall'utente ("bene così").
- Natura: le "onde" (transizione LIQUID) non piacevano. Ora CROSSFADE, SOFTZOOM, DRIFT, BLUR e `cutShare = 0.2`. Tolto LIQUID anche da Sogno e Mare.
- Nuove transizioni SOFTZOOM e DRIFT (`TransitionType.gentleTrend`: di tendenza ma morbide).
- Compilazione, prove unitarie ed emulatore tutti riusciti.

### Modifiche v0.3.141–v0.3.144: look 2026
- Parere ricevuto dall'utente: i temi d'azione avevano un "look da boomer". Ricerca su CapCut, Edits di Meta, tendenze 2026 (velocity edit).
- Temi d'azione (Energico, Virale, Azione, Estremo, Neon, Estate, Blockbuster, Viaggio, Mare, Montagna, Diario) rifatti:
  - `AutoStyle.cutShare`: stacchi netti sul battito (solo con musica); colpi d'impatto più rari dove il brano spinge;
  - `dropFx` (FLASHBEAT + BOUNCE), `introCut` (EXPOSE), `introFx`; Anni '90 tiene i suoi colpi VHS;
  - via CUBE, SWIRL, SPIN, PRISM, STRIPES, PUNCH, BURST, SPEEDLINES, DUOPOP, HOLO, NEON, HIT, ZOOMBEAT; titoli TITOLONE/PULITO/SOCIAL, sottotitolo MINIMAL.
- Effetti nuovi (WowFxEffect): HALATION, DIGICAM, GRAIN, MUTED (categoria `FxCategory.TREND`, prima scheda) e FLASHBEAT, BWBEAT, BOUNCE (a ritmo). `FxType.trendy` raccoglie la scheda.
- Transizioni nuove: BLACKOUT, EXPOSE, CAMSHAKE, SNAPZOOM (solo parametri esistenti dello shader); `TransitionType.trend` in testa al pannello.
- Testi: PULITO, ETICHETTA, TITOLONE, MINIMAL, SOCIAL; `TextRenderer` rimpicciolisce le parole troppo larghe.
- Sottotitoli: stili SOCIAL (di serie, 3 parole) e MINIMAL (minuscolo); Karaoke e Parola per parola senza maiuscolo, giallo burro.
- Prove: `TrendTest`, `exportTrendKit` sull'emulatore; video automatico sull'emulatore ridotto a 12 s (oltre l'ottava prova pesante l'emulatore si esauriva).
- Da raccogliere: parere dell'utente e di chi ha criticato i temi sul nuovo look.

### Modifiche v0.3.137–v0.3.140
- Natura dopo il secondo parere (ritmo ok, ma bagliori e pulsare fastidiosi): niente passaggi con lampo bianco (LUMA, LEAK, BOKEHFADE), niente DREAMY, RAYS, GLOWBEAT; foto con movimenti lenti (`gentlePops = false`).
- `AutoStyle.calm` (= `!shorterOnEnergy`): nei temi calmi niente ZOOMBEAT/HIT sulla scena del drop (HIT pulsa a ogni battito per tutta la scena), niente scatti delle foto nelle parti cariche, passaggi lenti anche sui colpi.
- Esportazione: `ERROR_CODE_DECODING_FAILED` ora si riprova con il decodificatore software (`engine/Decoders.kt`); anche il secondo tentativo delle dissolvenze dell'anteprima.
- Prove sull'emulatore: video automatico diviso in `exportAutoVideoCinema`/`Energico` con le scene nel registro (tag AutoScene); nuova `softwareDecoderExports`; il ramo `emulator-logs` ora ha `logcat-errori.txt` e i registri di ogni prova in `tests/`.

### Modifiche precedenti (v0.3.136), dopo il commento negativo su Natura
- Natura senza foglie che cadono. Passaggi morbidi anche sui colpi: LUMA, BOKEHFADE, LIQUID.
- `AutoStyle.minSceneUs` e `shorterOnEnergy`: i temi calmi hanno una durata minima delle scene (Natura 3,2 s) e non si accorciano dove il brano spinge.
- Verso coerente:
  - `GalleryItem` e `AutoSource` hanno ora larghezza e altezza;
  - `AutoEdit.preferOrientation` usa quelli nell'altro verso solo se non ce ne sono abbastanza.
- Raffiche: `AutoEdit.dedupeBursts` tiene la foto migliore tra quelle scattate a meno di 4 s l'una dall'altra.
- `Moments.bestStarts` evita i primi e gli ultimi 0,6 s e, con `calm`, i tratti molto mossi.

### Prove
- v0.3.144: compilazione, prove unitarie ed emulatore (31 prove) tutti riusciti.
- v0.3.140: compilazione e prove unitarie riuscite; emulatore 29 su 30. Fallita solo `previewPlaysWow` (anteprima lenta dopo 3 s: grafica software dell'emulatore, era passata nel giro prima).
- I fallimenti dei giri 136–139 erano il decodificatore h264 dell'emulatore (`c2.goldfish`, errore -14) che si esaurisce verso l'ottava prova pesante, non il video automatico.
- Per leggere i rami dei log serve `git fetch origin +emulator-logs:...` (con il +): vengono riscritti a ogni giro.
- Mai provati sul telefono vero:
  - il risultato in HDR;
  - la qualità della stabilizzazione e dello scontorno;
  - la fluidità dell'anteprima con gli effetti premium;
  - il nuovo tema Natura.

## 2. Decisioni e vincoli

### Come si lavora
- L'utente non scrive codice. Claude scrive tutto e comunica in italiano.
- Si compila solo su GitHub Actions; l'utente installa da releases/latest.
- Il job `build` esegue le prove unitarie e crea la release `v0.3.<run>`; i log vanno nel ramo `build-logs` (`status.txt`, `build.log`).
- Il job `emulatore` (timeout 100 minuti) esegue `DeviceTest`; i log vanno nel ramo `emulator-logs`.
- Il repository è pubblico, quindi niente chiavi API in chat o nel codice:
  - le chiavi Jamendo e Freesound si inseriscono solo nelle impostazioni della Libreria dell'app;
  - il rapporto le cancella.
- A ogni versione si aggiorna `app/src/main/assets/guida.md`:
  - la sezione "## Novità di questa versione" e le sezioni delle funzioni toccate;
  - le righe dell'elenco cominciano con "- " a inizio riga, senza rientro.
- Ogni nuova funzione arriva con le sue prove (unitarie e, se serve, sull'emulatore).
- I commit terminano con le righe Co-Authored-By e Claude-Session.

### Vincoli tecnici
- In anteprima niente compositore a più ingressi, perché CompositionPlayer si blocca sul telefono. Per questo:
  - le dissolvenze si preparano con `CrossfadePreview`;
  - i video sovrapposti in anteprima sono immagini ferme.
- Media3 passa agli effetti il tempo del montaggio intero: usare `localTimeUs(t, startUs)`.
- Le uniform GL vanno impostate solo se il loro shader le usa, altrimenti GlProgram dà errore. `WowFxEffect` usa un `#define FX_X` per ogni effetto e `runCatching` sulle uniform comuni.
- HDR: `HdrGl.fragment` converte `texture2D(`. Le maschere si leggono con `texture2D (` (con lo spazio) perché non vengano convertite.
- Nelle lambda non usare nomi completi `it.fabriziodavi...`: servono gli import.
- Nelle prove sull'emulatore ogni `EditorViewModel` va chiuso, altrimenti le prove dopo si bloccano.
- `AppNotIdleException` con Robolectric e Compose: si evitano gli indicatori animati e si prova la logica a parte.

### Scelte di prodotto
- I temi devono avere un aspetto moderno, "del 2026". All'utente sono piaciuti Estremo e Blockbuster.
- Il video automatico deve essere quasi perfetto da solo: a tempo di musica, nel verso giusto, con i momenti migliori.
- I temi calmi devono restare distesi, mai frenetici.

## 3. Prossimi passi
1. Se `previewPlaysWow` fallisce di nuovo, capire quale effetto WOW rallenta l'anteprima (dopo 3 s).
2. Raccogliere il parere dell'utente sul Natura della v0.3.137 (senza bagliori né pulsare).
3. Valutare un dittico (due foto affiancate) per le foto nell'altro verso, invece di scartarle o fare una panoramica.
4. Migliorare la forma dell'effetto Foglie d'autunno, oppure toglierlo dai temi.
5. Collaudo sul telefono vero (schermata Collaudo), in particolare HDR, stabilizzazione, scontorno, anteprima con effetti premium e progetti lunghi. Correggere a partire dai rapporti "Segnala un problema".
6. Verso la versione ufficiale:
   - stabilità con progetti lunghi e in 8K;
   - prestazioni dell'anteprima;
   - rifinitura dell'interfaccia;
   - poi la versione 1.0.
