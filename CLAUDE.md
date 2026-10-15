# Note per lo sviluppo di VivoVideo

- A ogni nuova versione aggiornare `app/src/main/assets/guida.md`: la sezione "## Novità di questa versione"
  (finisce anche nelle note della release su GitHub) e le sezioni delle funzioni toccate.
- L'utente non scrive codice: si compila solo su GitHub Actions (release v0.3.<run>), log nel ramo `build-logs`.
- Anteprima: niente compositore a più ingressi (CompositionPlayer si blocca sul telefono); dissolvenza incrociata
  e video sovrapposti veri solo in esportazione (Transformer).
- Media3 passa agli effetti il tempo del montaggio intero: usare `localTimeUs(t, startUs)`.
