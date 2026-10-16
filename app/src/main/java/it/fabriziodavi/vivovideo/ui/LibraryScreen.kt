package it.fabriziodavi.vivovideo.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import it.fabriziodavi.vivovideo.online.Library
import it.fabriziodavi.vivovideo.online.LibraryKeys
import it.fabriziodavi.vivovideo.online.LibrarySource
import it.fabriziodavi.vivovideo.online.MUSIC_MOODS
import it.fabriziodavi.vivovideo.online.OnlineTrack
import it.fabriziodavi.vivovideo.online.SFX_WORDS
import it.fabriziodavi.vivovideo.online.friendlyError

/** Stato della ricerca (fuori dalla schermata, così le prove possono mostrare risultati finti). */
class LibraryState {
    var results by mutableStateOf<List<OnlineTrack>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    /** Risultati già avuti in questa apertura della libreria. */
    val cache = HashMap<String, List<OnlineTrack>>()
}

/**
 * Libreria di musica ed effetti sonori liberi: si cerca (o si tocca un'atmosfera), si ascolta l'anteprima
 * e "Usa" scarica il file e lo mette nel progetto dall'indicatore. I crediti si salvano col brano.
 */
@Composable
fun LibraryScreen(vm: EditorViewModel, state: LibraryState = remember { LibraryState() }, autoSearch: Boolean = true) {
    val req = vm.library ?: return
    val ctx = LocalContext.current
    val effects = req.effects
    BackHandler { vm.library = null }

    var keys by remember { mutableStateOf(LibraryKeys.load(ctx)) }
    var commercial by remember { mutableStateOf(LibraryKeys.commercialOnly(ctx)) }
    var instrumental by remember { mutableStateOf(!effects) }
    val sources = LibrarySource.entries.filter { keys.has(it) && !(effects && it == LibrarySource.JAMENDO) }
    var source by remember {
        mutableStateOf(
            when {
                effects && keys.has(LibrarySource.FREESOUND) -> LibrarySource.FREESOUND
                !effects && keys.has(LibrarySource.JAMENDO) -> LibrarySource.JAMENDO
                else -> LibrarySource.OPENVERSE
            }
        )
    }
    if (source !in sources) source = LibrarySource.OPENVERSE
    val words = if (effects) SFX_WORDS else MUSIC_MOODS
    var query by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf<String?>(words.first().second) }
    var tick by remember { mutableIntStateOf(0) }
    var showKeys by remember { mutableStateOf(false) }

    // Anteprima: un lettore leggero che suona un brano alla volta.
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED) playing = null }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    fun toggle(t: OnlineTrack) {
        if (playing == t.id) { player.stop(); playing = null; return }
        player.setMediaItem(MediaItem.fromUri(t.previewUrl)); player.prepare(); player.play()
        playing = t.id
    }

    val search = chip?.let { c -> if (query.isBlank()) c else "$query $c" } ?: query
    var forced by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, search, commercial, instrumental, tick) {
        if (!autoSearch) return@LaunchedEffect
        val key = "$source|$search|$effects|$commercial|$instrumental"
        // Già cercato: subito, senza rete (salvo "Riprova" / tasto Cerca).
        if (tick == forced) state.cache[key]?.let { state.results = it; state.error = null; state.searched = true; return@LaunchedEffect }
        forced = tick
        // Si aspetta che l'utente smetta di scrivere/toccare: niente ricerche a ogni lettera.
        kotlinx.coroutines.delay(600)
        state.loading = true; state.error = null
        try {
            val r = Library.search(source, search, effects, keys, commercial, instrumental && !effects)
            state.cache[key] = r
            state.results = r
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // ricerca superata da una nuova: non è un errore
        } catch (e: Exception) {
            state.results = emptyList()
            state.error = friendlyError(e, source)
        } finally {
            state.loading = false; state.searched = true
        }
    }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.library = null }
            Text(if (effects) "Effetti sonori" else "Musica libera", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconBtn(VVIcons.Tune, "Codici dei servizi") { showKeys = true }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(if (effects) "Cerca (in inglese: whoosh, rain…)" else "Cerca (in inglese: summer, epic…)", color = VV.Faint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { tick++ }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
                    focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
                ),
            )
            PillRow(listOf<String?>(null) + words.map { it.second }, chip, { w -> words.firstOrNull { it.second == w }?.first ?: "Tutto" }) { chip = it }
            if (sources.size > 1) PillRow(sources, source, { it.label }) { source = it }
            ToggleRow("Solo licenze per uso commerciale", commercial) { commercial = it; LibraryKeys.setCommercialOnly(ctx, it) }
            if (!effects && source == LibrarySource.JAMENDO) ToggleRow("Solo strumentali (senza voce)", instrumental) { instrumental = it }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = VV.Tally)
                state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = VV.Danger)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Riprova", onClick = { tick++ })
                }
                state.searched && state.results.isEmpty() -> Text("Nessun risultato: prova altre parole.", style = MaterialTheme.typography.bodyMedium,
                    color = VV.Dim, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.results, key = { it.source.name + it.id }) { t ->
                        TrackRow(t, playing == t.id, vm.downloading == t.id, vm.downloading == null, { toggle(t) }) { player.stop(); playing = null; vm.useOnline(t) }
                    }
                    item {
                        Text(
                            "Brani e suoni da ${source.label}, con licenze Creative Commons. Quando usi un brano l'app ne salva autore e licenza: li trovi in Audio → Brano → Crediti, da copiare nella descrizione del video.",
                            style = MaterialTheme.typography.bodySmall, color = VV.Faint, modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showKeys) {
        var j by remember { mutableStateOf(keys.jamendo ?: "") }
        var f by remember { mutableStateOf(keys.freesound ?: "") }
        AlertDialog(
            onDismissRequest = { showKeys = false },
            containerColor = VV.Raised,
            title = { Text("Codici dei servizi", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restano salvati solo su questo telefono. Senza codici funziona Openverse.", style = MaterialTheme.typography.bodySmall)
                    KeyField("Jamendo – Client ID", j) { j = it }
                    LinkText("Crea il codice su devportal.jamendo.com", "https://devportal.jamendo.com")
                    KeyField("Freesound – API key", f) { f = it }
                    LinkText("Crea la chiave su freesound.org/apiv2/apply", "https://freesound.org/apiv2/apply")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    keys = LibraryKeys(j.trim().ifBlank { null }, f.trim().ifBlank { null })
                    LibraryKeys.save(ctx, keys)
                    showKeys = false
                    source = when {
                        effects && keys.has(LibrarySource.FREESOUND) -> LibrarySource.FREESOUND
                        !effects && keys.has(LibrarySource.JAMENDO) -> LibrarySource.JAMENDO
                        else -> LibrarySource.OPENVERSE
                    }
                    tick++
                }) { Text("Salva", color = VV.Tally) }
            },
            dismissButton = { TextButton(onClick = { showKeys = false }) { Text("Annulla", color = VV.Dim) } },
        )
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
            focusedTextColor = VV.Text, unfocusedTextColor = VV.Text, focusedLabelColor = VV.Tally, unfocusedLabelColor = VV.Dim,
        ),
    )
}

@Composable
private fun LinkText(text: String, url: String) {
    val ctx = LocalContext.current
    Text(text, style = MaterialTheme.typography.labelMedium, color = VV.Tally,
        modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } })
}

@Composable
private fun TrackRow(t: OnlineTrack, playing: Boolean, downloading: Boolean, canUse: Boolean, onPlay: () -> Unit, onUse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (playing) VV.Tally else VV.Raised2).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) VVIcons.Pause else VVIcons.Play, if (playing) "Ferma" else "Ascolta", tint = if (playing) VV.TallyInk else VV.Text) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t.artist.takeIf { it.isNotBlank() }, if (t.durationMs > 0) formatTime(t.durationMs * 1000).substringBeforeLast(',') else null, t.license.code)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(if (canUse) VV.Tally else VV.Raised2).clickable(enabled = canUse, onClick = onUse)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (downloading) CircularProgressIndicator(Modifier.size(16.dp), color = VV.TallyInk, strokeWidth = 2.dp)
            else Text("Usa", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (canUse) VV.TallyInk else VV.Faint)
        }
    }
    Spacer(Modifier.width(1.dp))
}
