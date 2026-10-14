@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.fabriziodavi.vivovideo

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Accent = Color(0xFFFF4D6D)
private val Bg = Color(0xFF0E0F13)
private val Surface = Color(0xFF181A21)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    background = Bg,
                    surface = Surface,
                    surfaceVariant = Surface,
                    secondaryContainer = Accent.copy(alpha = 0.25f),
                )
            ) {
                val vm: MainViewModel = viewModel()
                // Schermo acceso durante l'esportazione.
                LaunchedEffect(vm.exportState) {
                    if (vm.exportState is ExportState.Running) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                MainScreen(vm)
            }
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onVideoPicked(uri)
    }
    val running = vm.exportState is ExportState.Running

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VivoVideo", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.padding(4.dp))
                Text("prova tecnica", fontSize = 13.sp, color = Accent)
            }

            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.sourceUri == null) "Scegli un video" else "Cambia video") }

            vm.infoError?.let { Text("Impossibile leggere il video: $it", color = Accent) }

            val uri = vm.sourceUri
            val info = vm.info
            if (uri != null) {
                Preview(uri, vm.look, vm.speed, info)
            }
            if (info != null) {
                Text(info.summary(), color = Color(0xFFB8BCC8), fontSize = 13.sp)

                Section("Filtro") {
                    ChipRow(Look.entries, vm.look, { it.label }, !running) { vm.look = it }
                }
                Section("Velocità") {
                    ChipRow(SpeedMode.entries, vm.speed, { it.label }, !running) { vm.speed = it }
                    if (vm.speed == SpeedMode.RAMP) {
                        Hint("Veloce (2×) → lento (0,35×) al centro → veloce. Si vede nel video esportato, non nell'anteprima.")
                    }
                }
                Section("Esportazione") {
                    ChipRow(OutputSize.entries, vm.size, { it.label }, !running) { vm.size = it }
                    ChipRow(OutputFps.entries, vm.fps, { "fps: " + it.label }, !running) { vm.fps = it }
                    ChipRow(OutputCodec.entries, vm.codec, { it.label }, !running) { vm.codec = it }
                    ToggleRow("Mantieni l'audio", vm.keepAudio, !running) { vm.keepAudio = it }
                    if (vm.keepAudio && vm.speed != SpeedMode.NORMAL) {
                        ToggleRow("Mantieni il tono dell'audio", vm.keepPitch, !running) { vm.keepPitch = it }
                        Hint(
                            if (vm.keepPitch) "L'audio segue la velocità ma voci e suoni restano naturali."
                            else "Effetto nastro: rallentando l'audio diventa più grave, accelerando più acuto."
                        )
                    }
                    if (info.isHdr) {
                        ToggleRow("Converti HDR in SDR", vm.toneMapHdr, !running) { vm.toneMapHdr = it }
                    }
                }

                when (val s = vm.exportState) {
                    is ExportState.Running -> {
                        Text(
                            s.percent?.let { "Esportazione… $it%" } ?: "Esportazione in preparazione…",
                            color = Color.White,
                        )
                        LinearProgressIndicator(
                            progress = { (s.percent ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = vm::cancelExport, modifier = Modifier.fillMaxWidth()) {
                            Text("Annulla")
                        }
                    }
                    else -> Button(
                        onClick = vm::startExport,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text("Esporta", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                }

                when (val s = vm.exportState) {
                    is ExportState.Done -> ResultCard("Fatto! Salvato in Film/VivoVideo", s.details) {
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(s.uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(intent) }
                        }) { Text("Apri il video") }
                    }
                    is ExportState.Failed -> ResultCard("Qualcosa non ha funzionato", s.message) {
                        Hint("Fai uno screenshot di questo messaggio e mandalo a Claude.")
                    }
                    else -> {}
                }
            }

            Section("Encoder del telefono") {
                Hint(vm.encoderSummary)
            }
            Hint("Versione ${BuildConfigInfo.versionName(context)}")
        }
    }
}

@Composable
private fun Preview(uri: android.net.Uri, look: Look, speed: SpeedMode, info: VideoInfo?) {
    val context = LocalContext.current
    // Il player si ricrea quando cambia il video o il filtro.
    val player = remember(uri, look) {
        ExoPlayer.Builder(context).build().apply {
            setVideoEffects(look.effects())
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, speed) {
        player.setPlaybackSpeed(
            when (speed) {
                SpeedMode.SLOW -> 0.5f
                SpeedMode.FAST -> 2f
                else -> 1f
            }
        )
    }
    val ratio = info?.let {
        if (it.displayWidth > 0 && it.displayHeight > 0) it.displayWidth.toFloat() / it.displayHeight else null
    } ?: (16f / 9f)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(maxOf(ratio, 0.75f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        content()
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFDDE0E8), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Color(0xFF8A8F9C), fontSize = 12.sp)
}

@Composable
private fun ResultCard(title: String, body: String, actions: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            SelectionContainer { Text(body, color = Color(0xFFB8BCC8), fontSize = 13.sp) }
            actions()
        }
    }
}

private object BuildConfigInfo {
    fun versionName(context: android.content.Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
}
