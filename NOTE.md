# VivoVideo · note per riprendere il lavoro

Aggiornate al 29/10/2026, ultima versione **v1.0.222** (build 222). La 1.0 è la v1.0.201; dalla 202 ci sono i jump cut. Da leggere insieme a `CLAUDE.md`.

## 1. Stato attuale dell'app

App Android nativa di montaggio video in stile CapCut, pensata per il vivo X300 Ultra (Android 16, Qualcomm SM8850).
Base tecnica: Kotlin, Jetpack Compose, Media3 1.11.1, ML Kit, whisper.cpp e Signalsmith (JNI), minSdk 33, solo arm64-v8a in release.

### Collaudo
- Collaudo guidato (Progetto → Collaudo, `model/TestPlan.kt`, **41 prove**): 39 OK al 29/10/2026; `str_trans_new` KO con la 1.0.211 ("non mi piacciono, per alcune non vedo l'effetto": clip intere senza video oltre il taglio → corretto nella 1.0.212 con `makeRoomForTransition`, durate di serie più lunghe, filo di luce sulle tendine) da rifare; da fare `prog_originals` (`aud_jump` accettata dall'utente con la v1.0.206, "qualcosina da migliorare sul riconoscimento vocale e sottotitoli"; nel rapporto risultava ancora KO, da segnare OK sul telefono).
  L'ultima ("Progetto lungo": 15:57, 368 clip, esportato in 6 minuti, 1,9 GB) segnata OK. Dopo "Dividi i video sui battiti" su tutti i brani, dissolvenze dei brani in anteprima e tratti di anteprima dei progetti lunghi: provati poco dall'utente, risultato "comunque accettabile".
- Accettate con riserva (da migliorare dopo la 1.0): **riconoscimento vocale e sottotitoli** (29/10, dopo beam e normalizzazione ancora qualche parola sbagliata: idee → correzione rapida delle parole dalla lista, parole/nomi propri suggeriti al modello, provare Massima precisione e confrontare), taglio degli **ehm** (29/10: "le pause vengono riconosciute ma gli ehm necessitano ancora di migliorie"), **scontorno** (bordi e capelli), **stabilizzazione** ("non sensazionale").
- Il rapporto "Segnala un problema" (diario, esiti, telefono, impostazioni; niente video né chiavi) l'utente lo incolla in chat: è la fonte delle correzioni.

### Editor
- Timeline con clip, testi, livelli e brani; ogni clip è posizionata dal suo tempo esatto (`TimelineGeometry.edgesPx`: niente scarti accumulati con centinaia di clip).
- Taglio, divisione, spostamento (anche trascinando col dito: `Reorder`, `Project.moveClipTo`), Annulla/Ripeti, salvataggio automatico, orizzontale e schermo intero.
- Keyframe (zoom, posizione, rotazione, opacità); Inquadra con Riempi, Adatta, Sfocato.
- Velocità: Normale, Lenta, Veloce, Curva (speed ramp), audio con tono naturale.
- Colore: base, ruote, HSL, istogramma, LOG, LUT.
- Effetti: classici, WOW, di tendenza 2026, a ritmo; transizioni classiche, premium e di tendenza.
- Testi: stili pronti, animazioni per lettera, font personali, sottotitoli automatici (Whisper) con karaoke, titoli di coda.
- Strumenti di clip: fermo immagine, al contrario (fino a 60 s, verso giusto anche per i video verticali), stabilizzazione, selezione multipla (dopo "Copia attributi" si entra subito nella scelta delle clip), tutorial (zoom, frecce, passi).
- Funzioni intelligenti (tutto sul telefono):
  - **Momento migliore** (v0.3.197): lettura completa del video fino a 5 minuti (`SmartAnalyzer.scanDense`: 4 istanti al secondo, tremolio su ogni fotogramma, volti 2 volte al secondo, nitidezza del volto); il tratto deve essere nitido dall'inizio alla fine (`Moments.bestStarts`: 75% media + 25% momento peggiore); il messaggio dice perché (`Moments.describe`). Cache `momenti` versione v3. Raffiche di foto confrontate fra loro (`Moments.burstScores`, `AutoEdit.dedupeBurstsBy`).
  - Segui il soggetto; scontorno della persona (veloce o "Preciso" con la segmentazione di Google); privacy (volti, targhe, oggetti).
- Audio:
  - libreria online (Jamendo, Freesound, Openverse) con credito; effetti sonori;
  - battiti (`BeatDetector`, verificato contro librosa: stesso tempo, ~20 ms prima), Tutti/Ogni 2/Ogni 4, sposta battiti, tap a tempo, **Dividi i video sui battiti di tutti i brani** (analizza da sola i brani senza battiti; v0.3.199);
  - voce fuori campo (`VoiceRecorder` + `VoiceChain`, –14 LUFS) e ducking anche in anteprima;
  - **mixer** (gruppi clip/voce/musica/audio staccato/video sovrapposti, EQ, compressore, volume finale per destinazione, limitatore);
  - **Taglia pause e ehm** (`SpeechCutScreen`, `SpeechCuts`, `Hesitations`): pause OK; ehm da Whisper (anche scritti «M») e dal suono (suoni tenuti dopo una pausa sul tono di base), lista con ascolto ▶ / ✂ ▶ e spunte;
  - **anteprima al volume del video esportato** (`PreviewLoudness`, guadagno per gruppo); **dissolvenze del brano anche in anteprima** (`musicGainCurve`, v0.3.200).
- Progetto: copie di sicurezza, ricollega file spostati (in tempo reale, per nome, peso, durata e risoluzione), modelli, guida con indice e ricerca.

### Versione 1.0 (v1.0.201)
- Numerazione `1.0.<run>` (versionName e tag della release).
- `CacheJanitor` all'avvio (`onAppStart` → `cleanCacheOnce`): via i temporanei (`timeline_`, `mix_`, `export_`, `contrario_`, `.tmp`) fermi da oltre un'ora e le copie leggere non usate dai progetti salvati dopo 14 giorni o oltre 4 GB.
- `renderTimelineAudio(..., isCancelled)`: si ferma (`AudioRenderCancelled`) e non lascia file; `Exporter.cancel` cancella il video a metà e la colonna sonora; la misura di `PreviewLoudness` si interrompe quando l'audio cambia.
- Copie leggere nuove con fotogramma chiave ogni 0,5 s (`Proxies.KEYFRAME_SECONDS`).
- Timeline: etichette delle clip più in alto quando c'è la curva di velocità.

### Jump cut pronti (v1.0.202)
- Audio → Clip → **Jump cut**: `SpeechCutScreen` con `vm.jumpMode`; logica tutta in `model/JumpCuts.kt` (pura, `JumpCutsTest`).
- `JumpCuts.pieces`: riprese (`shots`: clip consecutive dello stesso file, contigue, senza transizione) meno i tagli; in ogni ripresa i pezzi alternano 1 / `ZOOM` 1,14; Dinamico: `PUNCH` 1,28 sui pezzi ≥ mediana + 3 dB (dal pcm a 16 kHz dell'analisi), mai due di fila.
- `JumpCuts.apply`: sottotitoli Social prima del taglio (`subtitleWords` senza ehm e parole nei tratti tolti), `cutTimeline`, poi ogni clip nuova si riconosce dal suo tratto nel file (in ordine); `punchFrame` mette `Clip.frame` (zoom + spostamento verso il volto, limitato a ±(zoom−1)/2), `Clip.jumpCut = true`. Social: lampo `FLASH` 240 ms ogni 4 tagli, whoosh (`Whoosh.kt`, rumore filtrato sintetizzato, `filesDir/effetti/whoosh_v1.wav`) come MusicTrack `dialog` che culmina sul taglio.
- Volti: `SmartAnalyzer.scan` + `Moments.subjectPath` (solo se `hasFaces`), una volta per video.
- `Clip.jumpCut`: micro-dissolvenze di 15 ms in `renderTimelineAudio` e mix nativo forzato nell'`Exporter`.
- Riepilogo prima di confermare (`vm.jumpPreview`), un solo Annulla (`update` unico).
- Frasi lunghe (v1.0.211, chiesto dall'utente: "i jump cut Social a inizio clip e poi più nulla"): `holdSplits` divide i pezzi oltre `MAX_HOLD_US` 4,5 s (mai sotto 1,5 s) fra due frasi / spazio più lungo fra le parole, altrimenti nel punto più basso della voce; `Piece.reframe`, `Summary.reframes`; lampi e whoosh solo sui tagli veri.
- HDR con i jump cut: verificato OK dall'utente il 29/10 (v1.0.208+).

### Torna ai video originali (v1.0.213, chiesto dall'utente)
- `Project.originals` (clip come importate: `addClips` nel ViewModel, video automatico, modelli → i media scelti; i modelli non si portano dietro quelli vecchi), `originalMedia()` ricostruisce per i progetti vecchi, `restoreOriginals()`; Progetto → Torna ai video originali con conferma, un solo Annulla.
- `makeRoomForTransition` (v1.0.212): scegliendo una transizione a due clip (anche l'incrociata) senza video oltre il taglio, accorcia le clip vicine (mai sotto 1 s) e sposta testi/livelli/musica; messaggio una volta sola.

### Esportazione
- Preset YouTube 4K/HD, Reels/TikTok, WhatsApp; H.265/H.264; HDR a 10 bit; fino a 4K120/8K30.
- Qualità: bit al secondo come le riprese del telefono e mai meno dell'originale; "qualità piena" (CBR) con scaletta di ripieghi (`Exporter.Mode`).
- Audio: mix in virgola mobile, volume per destinazione, limitatore.

### Anteprima
- Copie leggere a 720p dei video pesanti e delle foto grandi.
- Dissolvenze e tratti speciali preparati in anticipo (`CrossfadePreview`); dalla v0.3.199 i tratti del progetto aperto non si cancellano più a vicenda (prima con oltre 60 dissolvenze la preparazione non finiva mai) e si preparano prima quelli vicini all'indicatore.
- Volume e dissolvenze dei brani come nel video esportato.

### Video automatico
- 20+ temi in gruppi (Cinema, Energico, Virale, Ricordi, Natura, Rétro…), look 2026 nei temi d'azione, temi calmi distesi.
- Il brano decide il montaggio (energia, drop, intro, finale); regia intelligente (momenti migliori, volti, orientamento, raffiche).
- Video automatico a passi con scelta di singole foto e video; "Altra proposta".

## 2. Decisioni e vincoli

### Come si lavora
- L'utente non scrive codice. Claude scrive tutto e comunica in italiano, in modo semplice.
- Si compila solo su GitHub Actions (repository `fabriziodavi80/VivoVideo`, pubblico); l'utente installa da releases/latest.
  - Job `build`: prove unitarie (logica, Robolectric con `@Config(sdk = [35])`, schermate Roborazzi) e release `v1.0.<run>`; log nel ramo `build-logs` (`status.txt`, `errors.txt`, `build.log`, `screens/`).
  - Job `emulatore`: `DeviceTest` su x86_64 (esportazioni vere, anteprima); log nel ramo `emulator-logs`. Non sempre parte (stato "skipped").
  - Attesa della build: `git fetch -q --depth 1 origin +build-logs:refs/remotes/origin/build-logs` finché `status.txt` contiene lo sha.
- **Repository pubblico**: niente chiavi API in chat o nel codice (le chiavi Jamendo e Freesound si mettono solo nelle impostazioni della Libreria; il rapporto le cancella). **Niente video o audio dell'utente nel repository** (la sua ripresa per gli ehm è stata usata solo nella sessione).
- A ogni versione si aggiorna `app/src/main/assets/guida.md` ("## Novità di questa versione", che finisce nelle note della release, e le sezioni toccate; righe dell'elenco con "- " a inizio riga).
- Ogni nuova funzione arriva con le sue prove; se cambia una prova del collaudo, si aggiorna `TestPlan`.
- I commit terminano con le righe Co-Authored-By e Claude-Session; i commit di sole note con `[skip ci]`.
- Prove di logica in locale (senza Gradle): `java -cp "/opt/gradle-8.14.3/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp kotlin-stdlib-2.0.21.jar:junit-4.13.2.jar` su copie dei file nella cartella temporanea (togliendo `@Serializable`).

### Vincoli tecnici
- Media3: due pezzi **uno dopo l'altro nella stessa sequenza secondaria** del compositore → il secondo esce ~2 fotogrammi avanti (misurato con `DeviceTest.transitionsAreSmoothInExport`, 60 fps). Per questo le transizioni a due clip usano **due righe** (teste NEXT e code PREV, ingressi `lanes.size+1` e `+2`), ognuna con una pausa prima di ogni pezzo. Corretto nella v1.0.216 (utente: "lampi o scatti, esitazioni"; i lampi voluti restano, approvato dall'utente).
- Aperto (29/10 sera): **Passaggio con maschera = taglio netto nel video esportato sul telefono** (in anteprima ok). Sull'emulatore funziona sia da solo (`maskPassInExport`) sia in un progetto come quello del collaudo (`maskPassAmongOtherTransitionsInExport`). Dalla v1.0.222 il diario all'esportazione scrive per ogni transizione a due clip se è fatta e, per la maschera, copertura delle maschere, misure, HDR e velocità della clip: aspettare il rapporto dell'utente. Sospetti: video HDR del telefono (sull'emulatore SDR), maschere vere che non coprono la coda.
- Emulatore: `app/src/androidTest/solo.txt` con `Classe#metodo` fa girare una prova sola (misure in ~10 minuti); toglierlo dopo.
- Media3 e HDR: sui video HDR `OverlayShaderProgram` accetta solo `TextOverlay` (un `BitmapOverlay` semplice è trattato come Ultra HDR e vuole la gainmap → IllegalArgumentException, esportazione HDR ripiegata in SDR). Per questo `TimedTextOverlay` e `LayerOverlay` estendono `TextOverlay` (getText inutilizzato, l'immagine la dà getBitmap). Sistemato nella v1.0.205.
- Whisper: `transcribe(..., prompt, beam)`: sottotitoli con beam 5 e senza prompt; ehm con `FILLER_PROMPT` e beam 1. Audio sempre `normalized` (–20 dBFS, max ×20). Modelli: Veloce (base), Preciso (small), **Massima precisione** (medium q5_0, 539 MB); `Whisper.defaultModel` propone il più preciso scaricato.
- Media3: al massimo **15 overlay per OverlayEffect** (oltre: "more than 15 SDR overlays", anteprima bloccata). Usare sempre `overlayEffects()` in `CompositionFactory` (gruppi da 15). Scoperto il 29/10 con i jump cut Social (35 sottotitoli su una clip di 36 s).
- Anteprima: niente compositore a più ingressi (CompositionPlayer si blocca sul telefono): dissolvenze preparate con `CrossfadePreview`, video sovrapposti come immagini ferme (veri solo nell'esportato).
- Anteprima: **niente effetti audio a livello di Composition** (sperimentali in Media3 1.11, hanno distorto in v0.3.173). Guadagni per brano/gruppo con `ChannelMixingAudioProcessor` o con processori per elemento (`DuckAudioProcessor`, posizione da `StreamMetadata.positionOffsetUs`, relativa all'inizio dell'elemento tagliato).
- Media3 passa agli effetti il tempo del montaggio intero: usare `localTimeUs(t, startUs)`.
- Uniform GL solo se lo shader le usa; HDR: le maschere si leggono con `texture2D (` (con lo spazio).
- Nelle lambda niente nomi completi `it.fabriziodavi...`: servono gli import.
- Nelle prove sull'emulatore ogni `EditorViewModel` va chiuso.
- Robolectric e Compose: niente indicatori animati che girano sempre (`AppNotIdleException`).
- Le sequenze della musica in anteprima arrivano oltre la fine del video (riempite con un vuoto), se no l'anteprima si blocca dopo la fine di un brano corto.
- Rete dalla sessione di Claude: solo registri dei pacchetti e GitHub (niente Hugging Face, librosa.org): i modelli Whisper non si scaricano qui.

### Scelte di prodotto
- Temi con aspetto moderno, "del 2026"; all'utente sono piaciuti Estremo e Blockbuster; i temi calmi restano distesi.
- Il video automatico deve essere quasi perfetto da solo: a tempo di musica, nel verso giusto, con i momenti migliori.
- Quello che si sente e si vede in anteprima deve corrispondere al video esportato (volume, dissolvenze, ducking).
- Le funzioni "intelligenti" spiegano cosa hanno scelto e lasciano decidere (lista degli ehm da riascoltare, motivo del momento migliore).

## 3. Prossimi passi

### Subito
1. Da verificare: esportazione **HDR** con testi/sottotitoli (v1.0.205+): la riga Colore deve dire HDR, e i sottotitoli non devono essere troppo scuri o abbaglianti.
2. L'utente prova **Rimescola clip** (`str_shuffle`) e le **transizioni nuove** (`str_trans_new`). Prossimo in lista: **Intervista doppia**.

### Dopo la 1.0, in quest'ordine
1. ~~Jump cut pronti~~: fatti nella v1.0.202 (vedi sopra). Da migliorare se serve: micro-dissolvenze anche in anteprima (ora solo nell'esportato), "frasi importanti" con il testo di Whisper invece del solo volume.
2. ~~Rimescola clip~~ fatto nella v1.0.207 (`model/Shuffle.kt`, `vm.shuffleClips`, `canShuffleAgain`: "Rimescola ancora" riparte da `shuffleBase` senza nuovo passo di Annulla). Spec originale (24/10/2026): da Altro / Seleziona più clip. Se le clip sono tagliate a ritmo si rimescola il contenuto tenendo le durate (i tagli restano sul battito); niente due pezzi dello stesso video vicini; "Rimescola ancora"; un solo Annulla.
3. ~~Transizioni dal video dell'utente~~: fatte nella v1.0.208 (all'utente "sono piaciute tutte"): WIPE, WIPE_DIAG, CLOCK, STEPS, OVERLAY, MASK_PASS a due clip (`TransitionType.composite`, `Project.crossfades()` con `type`, `TransitionMaskEffect` con ruoli MAIN/NEXT/PREV, compositore alpha piena per i non-incrociata), INVISIBLE su una clip. MASK_PASS: solo con `person` sulla clip prima, tutto dopo il taglio (before = 0). Spec originale: **Transizioni dal video dell'utente** (https://youtube.com/watch?v=0putmJCyM1I, "12 Essential Transitions…", DaVinci Resolve): fade, dissolve, iris, wipe, invisible cut, speed ramp, steps, mask passing, whip pan, luma fade, overlay, preset. Già presenti in parte (dissolvenza, iris, luma, whip, speed ramp); da fare invisible cut, mask passing (con lo scontorno), steps, wipe moderni, overlay. Chiedere all'utente quali gli sono piaciute.
4. **Intervista doppia** (25/10/2026, stile Le Iene): due video con le stesse domande; per ogni domanda formato **alternato** (cartello con la domanda, poi A, poi B) o **schermo diviso** (A e B affiancati o sopra/sotto). Risposte trovate dai sottotitoli automatici, cartelli in stile TV, nome e didascalia, una voce alla volta. Schermo diviso preparato in anticipo per l'anteprima.
5. **Riconoscitore di ehm** (scelta dell'utente del 25/10/2026): piccolo modello dedicato (classificatore su spettrogramma mel, addestrato fuori dal telefono su un dataset pubblico di filler come PodcastFillers più esempi italiani), al posto o accanto alle regole di `Hesitations.kt`; ehm attaccati alle parole; anteprima del risultato nel lettore. Taratura attuale fatta su una ripresa vera (voce ~90 Hz, microfono ovattato sotto 1,2 kHz, ronzio a 480 Hz).
6. **Scontorno**: bordi e capelli (pennello di ritocco con propagazione, filtro guidato a colori, modello di matting più pesante).
7. **Stabilizzazione**: correzione rolling shutter, zoom variabile nel tempo, omografia.

### Da tenere d'occhio
- `PreviewLoudness` misura il mix in secondo piano dopo ogni modifica all'audio: verificare i tempi su progetti lunghi.
- Emulatore v1.0.210: 37 prove su 39 OK, compresa `exportNewestTransitions` (gli shader delle transizioni nuove funzionano); fallite solo Openverse (rete) e l'anteprima con tutti gli effetti (lenta senza GPU), rese più tolleranti.
- Prove sull'emulatore: non si compilavano dalla v0.3.19x (argomenti di `PersonMasks.analyze`), per questo il job risultava "skipped"; sistemato nella v1.0.210. Ora gli errori di compilazione finiscono in `compile-errors.txt` del ramo `emulator-logs`. Gli shader nuovi (TransitionMaskEffect) si provano davvero solo lì o sul telefono.
- `scanDense` legge tutto il video: con molti video lunghi nel video automatico la prima analisi richiede tempo (poi è in cache).
- Copie leggere con fotogramma chiave ogni secondo: se in anteprima i tagli a ritmo sembrano in ritardo con centinaia di clip, fare le copie con fotogrammi chiave più fitti.
