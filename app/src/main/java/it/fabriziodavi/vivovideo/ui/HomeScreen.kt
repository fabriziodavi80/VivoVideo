package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.describeEncoders
import it.fabriziodavi.vivovideo.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Project?>(null) }
    var askNew by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) vm.newProject(uris)
    }
    LaunchedEffect(Unit) { vm.refreshProjects() }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val encoders = remember { runCatching { describeEncoders() }.getOrDefault("") }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("VivoVideo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconBtn(VVIcons.Help, "Guida") { vm.showGuide = true }
            PrimaryButton("Nuovo progetto") { askNew = true }
        }
        // Video automatico: in evidenza, sopra ai progetti.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(VV.Raised2, VV.Raised)))
                .clickable { vm.showAuto = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(VV.Tally), contentAlignment = Alignment.Center) {
                Icon(VVIcons.Effects, null, tint = VV.TallyInk)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Video automatico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Scegli uno o più album: musica, tagli a ritmo, transizioni ed effetti li fa l'app.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (vm.projects.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Crea un progetto scegliendo video e foto dalla galleria.",
                    style = MaterialTheme.typography.bodyMedium, color = VV.Dim,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.projects, key = { it.id }) { p -> ProjectCard(p, onOpen = { vm.openProject(p) }, onDelete = { toDelete = p }) }
            }
        }
        Text(
            "Versione $version   ·   " + encoders.lines().firstOrNull().orEmpty(),
            style = MaterialTheme.typography.bodySmall, color = VV.Faint,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }

    if (askNew) {
        AddMediaDialog(
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onAlbums = { vm.openAlbumPicker(newProject = true) },
            onDismiss = { askNew = false },
        )
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = VV.Raised,
            title = { Text("Eliminare “${p.name}”?", style = MaterialTheme.typography.titleSmall) },
            text = { Text("Si cancella solo il progetto: i video originali e quelli esportati restano in galleria.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p.id); toDelete = null }) { Text("Elimina", color = VV.Danger) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla", color = VV.Dim) } },
        )
    }
}

@Composable
private fun ProjectCard(p: Project, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val first = p.clips.firstOrNull()
    val cover by produceState<Bitmap?>(null, first?.uri) {
        if (first != null) value = Thumbnails.get(context, first.uri, first.trimStartUs + 500_000, 320)
    }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
        Box(
            Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(VV.Raised),
            contentAlignment = Alignment.Center,
        ) {
            cover?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } ?: Icon(VVIcons.Movie, null, tint = VV.Faint)
            Text(
                formatTime(p.durationUs),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(androidx.compose.ui.graphics.Color(0x9E000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.clips.size} clip, " + SimpleDateFormat("d MMM HH:mm", Locale.ITALY).format(Date(p.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(VVIcons.Delete, "Elimina progetto", tint = VV.Faint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
