package it.fabriziodavi.vivovideo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.media.Album
import it.fabriziodavi.vivovideo.media.Gallery
import it.fabriziodavi.vivovideo.media.GalleryItem
import it.fabriziodavi.vivovideo.model.AutoOptions
import it.fabriziodavi.vivovideo.model.AutoStyle
import it.fabriziodavi.vivovideo.model.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Durate proposte per il video automatico (0 = tutto quello che c'è negli album). */
private val DURATIONS = listOf(30_000_000L, 60_000_000L, 120_000_000L, 180_000_000L, 0L)
private const val FIT_MUSIC = -1L

/** "Agosto 2026", "Luglio – Agosto 2026", "2024 – 2026". */
fun dateRangeLabel(firstMs: Long, lastMs: Long): String {
    val loc = Locale.ITALIAN
    fun f(p: String, ms: Long) = SimpleDateFormat(p, loc).format(Date(ms)).replaceFirstChar { c -> c.uppercase() }
    val y1 = f("yyyy", firstMs); val y2 = f("yyyy", lastMs)
    val m1 = f("MMMM", firstMs); val m2 = f("MMMM", lastMs)
    return when {
        y1 != y2 -> "$y1 – $y2"
        m1 != m2 -> "$m1 – $m2 $y2"
        else -> "$m2 $y2"
    }
}

/** I temi del video automatico divisi per gruppo (nel passo "Stile"). */
internal val STYLE_GROUPS: List<Pair<String, List<AutoStyle>>> = listOf(
    "Dinamici" to listOf(AutoStyle.ENERGICO, AutoStyle.VIRALE, AutoStyle.AZIONE, AutoStyle.ESTREMO, AutoStyle.BLOCKBUSTER, AutoStyle.NEON, AutoStyle.ESTATE),
    "Viaggi e natura" to listOf(AutoStyle.VIAGGIO, AutoStyle.DIARIO, AutoStyle.MARE, AutoStyle.MONTAGNA, AutoStyle.NATURA),
    "Emozioni" to listOf(AutoStyle.CINEMA, AutoStyle.ELEGANTE, AutoStyle.ROMANTICO, AutoStyle.MATRIMONIO, AutoStyle.SOGNO, AutoStyle.RICORDI),
    "Rétro" to listOf(AutoStyle.SUPER8, AutoStyle.ANNI90, AutoStyle.GRUNGE),
)

/** Da dove prendere foto e video: scelti uno per uno o album interi. */
private enum class Source(val label: String) { ITEMS("Foto e video"), ALBUMS("Album interi") }

private val STEPS = listOf("Cosa usare", "Stile", "Musica e dettagli")

/**
 * Video automatico in tre passi: 1) foto e video (scelti uno per uno o album interi), 2) stile, 3) musica, durata e titolo.
 * L'app sceglie le scene, le taglia a ritmo e aggiunge transizioni, effetti e titolo.
 */
@Composable
fun AutoScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = vm.busy
    var step by remember { mutableIntStateOf(0) }
    BackHandler(enabled = busy == null) { if (step > 0) step-- else vm.showAuto = false }

    var permTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val full = remember(permTick) { Gallery.fullAccess(ctx) }
    val any = remember(permTick) { Gallery.anyAccess(ctx) }
    LaunchedEffect(Unit) { if (!Gallery.anyAccess(ctx)) permLauncher.launch(Gallery.permissions) }

    val albums by produceState<List<Album>?>(null, permTick) {
        value = if (Gallery.anyAccess(ctx)) withContext(Dispatchers.IO) { runCatching { Gallery.albums(ctx) }.getOrDefault(emptyList()) } else emptyList()
    }
    // Tutta la galleria, dal più recente (per la scelta una per una).
    val gallery by produceState<List<GalleryItem>?>(null, permTick) {
        value = if (Gallery.anyAccess(ctx)) withContext(Dispatchers.IO) { runCatching { Gallery.items(ctx).asReversed() }.getOrDefault(emptyList()) } else emptyList()
    }
    var source by remember { mutableStateOf(Source.ITEMS) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var picked by remember { mutableStateOf(listOf<String>()) }
    var filterAlbum by remember { mutableStateOf<Album?>(null) }
    var style by remember { mutableStateOf(AutoStyle.CINEMA) }
    var group by remember { mutableStateOf(STYLE_GROUPS.first { AutoStyle.CINEMA in it.second }.first) }
    var duration by remember { mutableStateOf(60_000_000L) }
    var format by remember { mutableStateOf(VideoFormat.AUTO) }
    var smart by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var titleTouched by remember { mutableStateOf(false) }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { musicUri = uri; if (duration == 0L) duration = FIT_MUSIC }
    }
    // Brano scelto nella libreria in rete.
    LaunchedEffect(vm.autoMusicPicked) {
        vm.autoMusicPicked?.let { musicUri = it; vm.autoMusicPicked = null; if (duration == 0L) duration = FIT_MUSIC }
    }
    val musicName = remember(musicUri) {
        musicUri?.let { u ->
            runCatching { ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }
                .getOrNull() ?: "Brano scelto"
        }
    }
    // Elementi degli album scelti (servono per contare e per il periodo del sottotitolo).
    val albumItems by produceState<List<GalleryItem>>(emptyList(), selected, permTick) {
        value = if (selected.isEmpty()) emptyList() else withContext(Dispatchers.IO) { runCatching { Gallery.items(ctx, selected) }.getOrDefault(emptyList()) }
    }
    // Quelli che entreranno nel video, dal più vecchio.
    val chosen: List<GalleryItem> = if (source == Source.ALBUMS) albumItems else {
        val set = picked.toSet()
        gallery.orEmpty().filter { it.uri.toString() in set }.sortedBy { it.dateMs }
    }
    val subtitle = if (chosen.isEmpty()) "" else dateRangeLabel(chosen.first().dateMs, chosen.last().dateMs)
    // Titolo proposto: i nomi degli album (finché non lo si cambia a mano).
    LaunchedEffect(selected, albums, source) {
        if (!titleTouched) title = if (source == Source.ALBUMS) albums.orEmpty().filter { it.id in selected }.joinToString(" · ") { it.name }.take(40) else ""
    }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { if (step > 0) step-- else vm.showAuto = false }
            Text("Video automatico", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        StepBar(step) { if (it < step && busy == null) step = it }
        if (busy != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = VV.Tally, trackColor = VV.Raised2)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (step) {
                0 -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segmented(Source.entries, source, { it.label }) { source = it }
                    when {
                        !any -> {
                            Hint("Per vedere foto e album serve il permesso di leggere foto e video.")
                            PrimaryButton("Consenti l'accesso alla galleria", Modifier.fillMaxWidth()) { permLauncher.launch(Gallery.permissions) }
                            SecondaryButton("Apri le impostazioni dell'app", Modifier.fillMaxWidth(), onClick = {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
                            })
                        }
                        source == Source.ITEMS -> {
                            if (!full) Hint("Hai dato accesso solo ad alcune foto: si vedono solo quelle.")
                            val list = gallery
                            if (list == null) Hint("Lettura della galleria…") else {
                                if (!albums.isNullOrEmpty()) PillRow(listOf<Album?>(null) + albums!!, filterAlbum, { it?.name ?: "Tutte" }) { filterAlbum = it }
                                val shown = filterAlbum?.let { a -> list.filter { it.albumId == a.id } } ?: list
                                if (shown.isEmpty()) Hint("Nessuna foto o video.")
                                MediaGrid(shown, picked, { item ->
                                    val u = item.uri.toString()
                                    picked = if (u in picked) picked - u else picked + u
                                }, Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                        else -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Hint("L'app prende le foto e i video migliori lungo tutto il periodo degli album scelti.")
                            if (!full) {
                                Hint("Hai dato accesso solo ad alcune foto: si vedono solo quelle. Per gli album interi scegli \"Consenti tutto\".")
                                SecondaryButton("Consenti l'accesso a tutto", Modifier.fillMaxWidth(), onClick = { permLauncher.launch(Gallery.permissions) })
                            }
                            when {
                                albums == null -> Hint("Lettura degli album…")
                                albums!!.isEmpty() -> Hint("Nessun album con foto o video.")
                                else -> albums!!.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { a ->
                                            AlbumCard(a, a.id in selected, Modifier.weight(1f)) {
                                                selected = if (a.id in selected) selected - a.id else selected + a.id
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillRow(STYLE_GROUPS.map { it.first }, group, { it }) { group = it }
                    val list = STYLE_GROUPS.first { it.first == group }.second
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { s -> StyleCard(s, s == style, Modifier.weight(1f)) { style = s } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(style.label, style = MaterialTheme.typography.titleSmall, color = VV.Tally)
                        Text(style.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PanelLabel("Musica")
                    if (musicUri == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("Libreria online", Modifier.weight(1f)) { vm.openLibrary(effects = false, forAuto = true) }
                            SecondaryButton("Dal telefono", Modifier.weight(1f), onClick = { musicPicker.launch(arrayOf("audio/*")) })
                        }
                        Hint("Con la musica i tagli cadono sui battiti e l'audio dei video si spegne. Senza, restano i suoni originali.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(VVIcons.Music, null, tint = VV.Music, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(musicName ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconBtn(VVIcons.Close, "Togli il brano") { musicUri = null; if (duration == FIT_MUSIC) duration = 60_000_000L }
                        }
                    }
                    PanelLabel("Durata")
                    val options = (if (musicUri != null) listOf(FIT_MUSIC) else emptyList()) + DURATIONS
                    PillRow(options, duration, { d ->
                        when (d) { FIT_MUSIC -> "Come il brano"; 0L -> "Tutto"; else -> if (d < 60_000_000L) "${d / 1_000_000} s" else "${d / 60_000_000} min" }
                    }) { duration = it }
                    if (source == Source.ITEMS && duration != 0L) Hint("Con \"Tutto\" entrano tutte le foto e i video scelti; con una durata fissa, se sono troppi, l'app ne usa una parte lungo tutto il periodo.")
                    PanelLabel("Titolo")
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; titleTouched = true },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Titolo del video (facoltativo)", color = VV.Faint) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
                            focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
                        ),
                    )
                    if (subtitle.isNotEmpty() && title.isNotBlank()) Hint("Sotto il titolo: $subtitle")
                    // Le scelte meno usate restano chiuse finché non servono.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (more) "Meno opzioni" else "Altre opzioni: formato e regia", style = MaterialTheme.typography.labelLarge, color = VV.Tally, modifier = Modifier.weight(1f))
                        Text(if (more) "▲" else "▼", color = VV.Tally)
                    }
                    if (more) {
                        PanelLabel("Formato")
                        PillRow(VideoFormat.entries, format, { if (it == VideoFormat.AUTO) "Automatico" else it.label }) { format = it }
                        Hint(
                            if (format == VideoFormat.AUTO) "Automatico: verticale o orizzontale secondo le foto e i video più numerosi. Le foto nell'altro verso si vedono intere sullo sfondo sfocato."
                            else "Le foto e i video nell'altro verso avranno lo sfondo sfocato invece delle bande nere."
                        )
                        ToggleRow("Regia intelligente", smart) { smart = it }
                        Hint(
                            if (smart) "L'app guarda ogni video e sceglie il tratto migliore: nitido, ben illuminato, con volti e sorrisi. Ci vuole un po' di più (la seconda volta è subito pronto)."
                            else "Dai video si prende un tratto centrale, senza analizzarli: più veloce."
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        // Riepilogo e pulsante del passo.
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chosen.isNotEmpty()) {
                val v = chosen.count { it.isVideo }
                Text(
                    "${chosen.size - v} foto, $v video" + (if (subtitle.isNotEmpty()) " · $subtitle" else "") + if (step > 0) " · ${style.label}" else "",
                    style = MaterialTheme.typography.labelLarge, color = VV.Tally,
                )
            }
            val ready = chosen.isNotEmpty() && busy == null
            when (step) {
                0 -> PrimaryButton(
                    if (chosen.isEmpty()) (if (source == Source.ITEMS) "Scegli foto e video" else "Scegli almeno un album") else "Avanti: stile",
                    Modifier.fillMaxWidth(), enabled = ready,
                ) { step = 1 }
                1 -> PrimaryButton("Avanti: musica e dettagli", Modifier.fillMaxWidth(), enabled = ready) { step = 2 }
                else -> PrimaryButton("Crea il video", Modifier.fillMaxWidth(), enabled = ready) {
                    val handPicked = source == Source.ITEMS
                    scope.launch {
                        val all = if (handPicked) chosen else withContext(Dispatchers.IO) { Gallery.items(ctx, selected) }
                        vm.autoCreate(
                            all, musicUri,
                            AutoOptions(style = style, format = format, targetUs = duration.coerceAtLeast(0), title = title, subtitle = if (title.isBlank()) "" else subtitle,
                                seed = (System.currentTimeMillis() % 10_000).toInt(), smart = smart),
                            fitMusic = duration == FIT_MUSIC, handPicked = handPicked,
                        )
                    }
                }
            }
        }
    }
}

/** I tre passi in alto: quello in corso in ambra; si torna indietro toccando un passo già fatto. */
@Composable
private fun StepBar(step: Int, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STEPS.forEachIndexed { i, label ->
            val on = i == step
            val done = i < step
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = done) { onStep(i) }.padding(vertical = 4.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (on || done) VV.Tally else VV.Raised2))
                Text(
                    "${i + 1}. $label", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) VV.Tally else if (done) VV.Text else VV.Dim, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StyleCard(s: AutoStyle, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
            .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(s.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
        Text(s.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AlbumCard(a: Album, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, a.coverUri) { value = withContext(Dispatchers.IO) { Gallery.thumbnail(ctx, a.coverUri) } }
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(VV.Raised)
                .border(if (on) 3.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(10.dp)),
        ) {
            thumb?.let { Image(it.asImageBitmap(), a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (on) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(VV.Tally),
                    contentAlignment = Alignment.Center,
                ) { Icon(VVIcons.Check, "Scelto", tint = VV.TallyInk, modifier = Modifier.size(16.dp)) }
            }
        }
        Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) VV.Tally else VV.Text,
            modifier = Modifier.padding(top = 4.dp))
        Text("${a.count}" + if (a.videos > 0) " · ${a.videos} video" else "", style = MaterialTheme.typography.bodySmall)
    }
}
