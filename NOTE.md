# VivoVideo · note per riprendere il lavoro

Aggiornate al 28/10/2026, ultima versione **v0.3.200** (build 200 riuscita). Da leggere insieme a `CLAUDE.md`.

## 1. Stato attuale dell'app

App Android nativa di montaggio video in stile CapCut, pensata per il vivo X300 Ultra (Android 16, Qualcomm SM8850).
Base tecnica: Kotlin, Jetpack Compose, Media3 1.11.1, ML Kit, whisper.cpp e Signalsmith (JNI), minSdk 33, solo arm64-v8a in release.

### Collaudo
- Collaudo guidato (Progetto → Collaudo, `model/TestPlan.kt`, **37 prove**): al 28/10/2026 **tutte provate e tutte OK**.
  L'ultima ("Progetto lungo": 15:57, 368 clip, esportato in 6 minuti, 1,9 GB) è da segnare OK sul telefono dopo la v0.3.200.
- Accettate con riserva (da migliorare dopo la 1.0): taglio degli **ehm**, **scontorno** (bordi e capelli), **stabilizzazione** ("non sensazionale").
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
  - Job `build`: prove unitarie (logica, Robolectric con `@Config(sdk = [35])`, schermate Roborazzi) e release `v0.3.<run>`; log nel ramo `build-logs` (`status.txt`, `errors.txt`, `build.log`, `screens/`).
  - Job `emulatore`: `DeviceTest` su x86_64 (esportazioni vere, anteprima); log nel ramo `emulator-logs`. Non sempre parte (stato "skipped").
  - Attesa della build: `git fetch -q --depth 1 origin +build-logs:refs/remotes/origin/build-logs` finché `status.txt` contiene lo sha.
- **Repository pubblico**: niente chiavi API in chat o nel codice (le chiavi Jamendo e Freesound si mettono solo nelle impostazioni della Libreria; il rapporto le cancella). **Niente video o audio dell'utente nel repository** (la sua ripresa per gli ehm è stata usata solo nella sessione).
- A ogni versione si aggiorna `app/src/main/assets/guida.md` ("## Novità di questa versione", che finisce nelle note della release, e le sezioni toccate; righe dell'elenco con "- " a inizio riga).
- Ogni nuova funzione arriva con le sue prove; se cambia una prova del collaudo, si aggiorna `TestPlan`.
- I commit terminano con le righe Co-Authored-By e Claude-Session; i commit di sole note con `[skip ci]`.
- Prove di logica in locale (senza Gradle): `java -cp "/opt/gradle-8.14.3/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp kotlin-stdlib-2.0.21.jar:junit-4.13.2.jar` su copie dei file nella cartella temporanea (togliendo `@Serializable`).

### Vincoli tecnici
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
1. L'utente segna OK "Progetto lungo" con la v0.3.200 e verifica che dopo "Dividi i video sui battiti" i tagli compaiano in tutta la timeline (con la 0.3.196 tagliava solo nel brano selezionato); che le dissolvenze dei brani si sentano in anteprima; che i tratti di anteprima di un progetto lungo finiscano di prepararsi.
2. Con il collaudo completo: versione **1.0** (rifinitura dell'interfaccia, stabilità con progetti lunghi e 8K, prestazioni dell'anteprima).

### Dopo la 1.0, in quest'ordine
1. **Jump cut pronti** (deciso il 24/10/2026, prima novità): Audio → Clip → Jump cut, usa `SpeechCuts` (pause ed ehm) e a ogni taglio alterna l'inquadratura (normale / +12–15% centrata sul volto). Stili Pulito, Dinamico (punch-in sulle frasi importanti), Social (lampo o whoosh su qualche taglio, sottotitoli a parole). Micro-dissolvenze audio sui tagli, riepilogo prima di confermare, un solo Annulla.
2. **Rimescola clip** (24/10/2026): da Altro / Seleziona più clip. Se le clip sono tagliate a ritmo si rimescola il contenuto tenendo le durate (i tagli restano sul battito); niente due pezzi dello stesso video vicini; "Rimescola ancora"; un solo Annulla.
3. **Transizioni dal video dell'utente** (https://youtube.com/watch?v=0putmJCyM1I, "12 Essential Transitions…", DaVinci Resolve): fade, dissolve, iris, wipe, invisible cut, speed ramp, steps, mask passing, whip pan, luma fade, overlay, preset. Già presenti in parte (dissolvenza, iris, luma, whip, speed ramp); da fare invisible cut, mask passing (con lo scontorno), steps, wipe moderni, overlay. Chiedere all'utente quali gli sono piaciute.
4. **Intervista doppia** (25/10/2026, stile Le Iene): due video con le stesse domande; per ogni domanda formato **alternato** (cartello con la domanda, poi A, poi B) o **schermo diviso** (A e B affiancati o sopra/sotto). Risposte trovate dai sottotitoli automatici, cartelli in stile TV, nome e didascalia, una voce alla volta. Schermo diviso preparato in anticipo per l'anteprima.
5. **Riconoscitore di ehm** (scelta dell'utente del 25/10/2026): piccolo modello dedicato (classificatore su spettrogramma mel, addestrato fuori dal telefono su un dataset pubblico di filler come PodcastFillers più esempi italiani), al posto o accanto alle regole di `Hesitations.kt`; ehm attaccati alle parole; anteprima del risultato nel lettore. Taratura attuale fatta su una ripresa vera (voce ~90 Hz, microfono ovattato sotto 1,2 kHz, ronzio a 480 Hz).
6. **Scontorno**: bordi e capelli (pennello di ritocco con propagazione, filtro guidato a colori, modello di matting più pesante).
7. **Stabilizzazione**: correzione rolling shutter, zoom variabile nel tempo, omografia.

### Da tenere d'occhio
- `PreviewLoudness` misura il mix in secondo piano dopo ogni modifica all'audio: verificare i tempi su progetti lunghi.
- `scanDense` legge tutto il video: con molti video lunghi nel video automatico la prima analisi richiede tempo (poi è in cache).
- Copie leggere con fotogramma chiave ogni secondo: se in anteprima i tagli a ritmo sembrano in ritardo con centinaia di clip, fare le copie con fotogrammi chiave più fitti.
