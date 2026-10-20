# VivoVideo · note per riprendere il lavoro

Aggiornate al 19/10/2026, ultima versione **v0.3.147**. Da leggere insieme a `CLAUDE.md`.

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

### Ultime modifiche (v0.3.146–v0.3.147)
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
