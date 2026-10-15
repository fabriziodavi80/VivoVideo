# Note per lo sviluppo di VivoVideo

- A ogni nuova versione aggiornare `app/src/main/assets/guida.md`: la sezione "## Novità di questa versione"
  (finisce anche nelle note della release su GitHub) e le sezioni delle funzioni toccate.
- L'utente non scrive codice: si compila solo su GitHub Actions (release v0.3.<run>), log nel ramo `build-logs`.
- Anteprima: niente compositore a più ingressi (CompositionPlayer si blocca sul telefono). Le dissolvenze incrociate
  si preparano in anticipo con Transformer (`CrossfadePreview`) e l'anteprima riproduce i pezzi pronti;
  i video sovrapposti in anteprima sono immagini ferme, veri solo in esportazione.
- Media3 passa agli effetti il tempo del montaggio intero: usare `localTimeUs(t, startUs)`.
- Prove: `app/src/test` (logica, Robolectric, schermate) nel job principale; `app/src/androidTest/DeviceTest.kt`
  su emulatore x86_64 nel job `emulatore` (esportazioni vere, anteprima che scorre), esito nel ramo `emulator-logs`.
  Aggiungere prove quando si aggiungono funzioni.
