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

/**
 * Video automatico: si scelgono uno o più album, uno stile, un brano e la durata;
 * l'app sceglie le scene, le taglia a ritmo e aggiunge transizioni, effetti e titolo.
 */
@Composable
fun AutoScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = vm.busy
    BackHandler(enabled = busy == null) { vm.showAuto = false }

    var permTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val full = remember(permTick) { Gallery.fullAccess(ctx) }
    val any = remember(permTick) { Gallery.anyAccess(ctx) }
    LaunchedEffect(Unit) { if (!Gallery.anyAccess(ctx)) permLauncher.launch(Gallery.permissions) }

    val albums by produceState<List<Album>?>(null, permTick) {
        value = if (Gallery.anyAccess(ctx)) withContext(Dispatchers.IO) { runCatching { Gallery.albums(ctx) }.getOrDefault(emptyList()) } else emptyList()
    }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var style by remember { mutableStateOf(AutoStyle.CINEMA) }
    var duration by remember { mutableStateOf(60_000_000L) }
    var format by remember { mutableStateOf(VideoFormat.AUTO) }
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
    val items by produceState<List<GalleryItem>>(emptyList(), selected, permTick) {
        value = if (selected.isEmpty()) emptyList() else withContext(Dispatchers.IO) { runCatching { Gallery.items(ctx, selected) }.getOrDefault(emptyList()) }
    }
    val subtitle = if (items.isEmpty()) "" else dateRangeLabel(items.first().dateMs, items.last().dateMs)
    // Titolo proposto: i nomi degli album (finché non lo si cambia a mano).
    LaunchedEffect(selected, albums) {
        if (!titleTouched) title = albums.orEmpty().filter { it.id in selected }.joinToString(" · ") { it.name }.take(40)
    }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { vm.showAuto = false }
            Text("Video automatico", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (busy != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = VV.Tally, trackColor = VV.Raised2)
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("Scegli uno o più album: l'app prende le foto e i video migliori lungo tutto il periodo, li taglia a tempo di musica e aggiunge transizioni, effetti e titolo nello stile che preferisci. Poi puoi ritoccare tutto.")

            Section("1", "Album")
            when {
                !any -> {
                    Hint("Per vedere gli album serve il permesso di leggere foto e video.")
                    PrimaryButton("Consenti l'accesso alla galleria", Modifier.fillMaxWidth()) { permLauncher.launch(Gallery.permissions) }
                    SecondaryButton("Apri le impostazioni dell'app", Modifier.fillMaxWidth(), onClick = {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
                    })
                }
                albums == null -> Hint("Lettura degli album…")
                else -> {
                    if (!full) {
                        Hint("Hai dato accesso solo ad alcune foto: si vedono solo quelle. Per gli album interi scegli \"Consenti tutto\".")
                        SecondaryButton("Consenti l'accesso a tutto", Modifier.fillMaxWidth(), onClick = { permLauncher.launch(Gallery.permissions) })
                    }
                    if (albums!!.isEmpty()) Hint("Nessun album con foto o video.")
                    albums!!.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a ->
                                AlbumCard(a, a.id in selected, Modifier.weight(1f)) {
                                    selected = if (a.id in selected) selected - a.id else selected + a.id
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (selected.isNotEmpty()) {
                        val v = items.count { it.isVideo }
                        Text(
                            "${selected.size} album · ${items.size - v} foto, $v video" + if (subtitle.isNotEmpty()) " · $subtitle" else "",
                            style = MaterialTheme.typography.labelLarge, color = VV.Tally,
                        )
                    }
                }
            }

            Section("2", "Stile")
            AutoStyle.entries.forEach { s ->
                val on = s == style
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { style = s }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(s.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                    Text(s.description, style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("3", "Musica")
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

            Section("4", "Formato")
            PillRow(VideoFormat.entries, format, { if (it == VideoFormat.AUTO) "Automatico" else it.label }) { format = it }
            Hint(
                if (format == VideoFormat.AUTO) "Automatico: verticale o orizzontale secondo le foto e i video più numerosi. Quelli con l'orientamento opposto avranno lo sfondo sfocato."
                else "Le foto e i video con l'orientamento opposto avranno lo sfondo sfocato invece delle bande nere."
            )

            Section("5", "Durata")
            val options = (if (musicUri != null) listOf(FIT_MUSIC) else emptyList()) + DURATIONS
            PillRow(options, duration, { d ->
                when (d) { FIT_MUSIC -> "Come il brano"; 0L -> "Tutto"; else -> if (d < 60_000_000L) "${d / 1_000_000} s" else "${d / 60_000_000} min" }
            }) { duration = it }

            Section("6", "Titolo")
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
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            PrimaryButton(
                if (selected.isEmpty()) "Scegli almeno un album" else "Crea il video",
                Modifier.fillMaxWidth(), enabled = selected.isNotEmpty() && items.isNotEmpty() && busy == null,
            ) {
                scope.launch {
                    val all = withContext(Dispatchers.IO) { Gallery.items(ctx, selected) }
                    vm.autoCreate(
                        all, musicUri,
                        AutoOptions(style = style, format = format, targetUs = duration.coerceAtLeast(0), title = title, subtitle = if (title.isBlank()) "" else subtitle,
                            seed = (System.currentTimeMillis() % 10_000).toInt()),
                        fitMusic = duration == FIT_MUSIC,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(n: String, label: String) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(VV.Tally), contentAlignment = Alignment.Center) {
            Text(n, color = VV.TallyInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AlbumCard(a: Album, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
