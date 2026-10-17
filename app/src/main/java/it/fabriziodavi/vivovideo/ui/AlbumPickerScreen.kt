package it.fabriziodavi.vivovideo.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.media.Album
import it.fabriziodavi.vivovideo.media.Gallery
import it.fabriziodavi.vivovideo.media.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Album interi da mettere in un progetto (nuovo o aperto), in ordine di data. */
@Composable
fun AlbumPickerScreen(vm: EditorViewModel) {
    val forNew = vm.albumPick ?: return
    val ctx = LocalContext.current
    BackHandler { vm.albumPick = null }
    var permTick by remember { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val full = remember(permTick) { Gallery.fullAccess(ctx) }
    val any = remember(permTick) { Gallery.anyAccess(ctx) }
    LaunchedEffect(Unit) { if (!Gallery.anyAccess(ctx)) permLauncher.launch(Gallery.permissions) }
    val albums by produceState<List<Album>?>(null, permTick) {
        value = if (Gallery.anyAccess(ctx)) withContext(Dispatchers.IO) { runCatching { Gallery.albums(ctx) }.getOrDefault(emptyList()) } else emptyList()
    }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val items by produceState<List<GalleryItem>>(emptyList(), selected, permTick) {
        value = if (selected.isEmpty()) emptyList() else withContext(Dispatchers.IO) { runCatching { Gallery.items(ctx, selected) }.getOrDefault(emptyList()) }
    }
    var confirmMany by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.albumPick = null }
            Text(if (forNew) "Nuovo progetto da album" else "Aggiungi album", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hint("Tocca uno o più album: tutte le foto e i video entrano nel progetto in ordine di data. Poi li tagli, riordini o togli come vuoi.")
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
                        Hint("Hai dato accesso solo ad alcune foto: si vedono solo quelle.")
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
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            val v = items.count { it.isVideo }
            PrimaryButton(
                if (items.isEmpty()) "Scegli almeno un album" else "Aggiungi ${items.size - v} foto e $v video",
                Modifier.fillMaxWidth(), enabled = items.isNotEmpty(),
            ) { if (items.size > 150) confirmMany = true else vm.addAlbumItems(items) }
        }
    }
    if (confirmMany) {
        AlertDialog(
            onDismissRequest = { confirmMany = false },
            containerColor = VV.Raised,
            title = { Text("${items.size} elementi", style = MaterialTheme.typography.titleSmall) },
            text = { Text("Sono tanti: la lettura richiederà un po' e il progetto sarà lungo. Per scegliere solo i momenti migliori c'è il Video automatico.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { confirmMany = false; vm.addAlbumItems(items) }) { Text("Aggiungi tutto", color = VV.Tally) } },
            dismissButton = { TextButton(onClick = { confirmMany = false }) { Text("Annulla", color = VV.Dim) } },
        )
    }
}

/** Da dove prendere foto e video: scelta singola o album interi. */
@Composable
fun AddMediaDialog(onPick: () -> Unit, onAlbums: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Raised,
        title = { Text("Aggiungi foto e video", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Scegli foto e video", Modifier.fillMaxWidth()) { onDismiss(); onPick() }
                SecondaryButton("Album interi", Modifier.fillMaxWidth(), onClick = { onDismiss(); onAlbums() })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = VV.Dim) } },
    )
}
