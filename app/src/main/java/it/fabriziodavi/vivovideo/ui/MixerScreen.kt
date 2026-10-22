package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.Meters
import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.BusMix
import it.fabriziodavi.vivovideo.model.Comp
import it.fabriziodavi.vivovideo.model.DuckSpeed
import it.fabriziodavi.vivovideo.model.EqPreset
import it.fabriziodavi.vivovideo.model.LoudTarget
import it.fabriziodavi.vivovideo.model.buses
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mixer audio: un canale per gruppo (clip, voce, musica, audio staccato, video sovrapposti) e l'uscita generale. */
@Composable
fun MixerScreen(vm: EditorViewModel) {
    val p = vm.project ?: return
    BackHandler { vm.closeMixer() }
    val mix = p.mix
    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.closeMixer() }
            Text("Mixer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatTime(vm.playheadUs), style = MaterialTheme.typography.bodySmall, color = VV.Dim)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(VV.Tally).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (vm.isPlaying) VVIcons.Pause else VVIcons.Play, if (vm.isPlaying) "Pausa" else "Ascolta", tint = VV.Canvas) }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hint("Fai partire l'anteprima e regola ogni gruppo ascoltando: gli indicatori mostrano il livello. Quello che senti è quello che esporti (il volume finale per la destinazione si applica nel video esportato).")
            p.buses().forEach { b -> ChannelCard(vm, b, mix.bus(b), mix.audible(b)) }
            MasterCard(vm)
            if (!mix.neutral || mix.target != LoudTarget.SOCIAL || !mix.limiter || mix.duckSpeed != DuckSpeed.NORMAL) {
                SecondaryButton("Azzera il mixer", Modifier.fillMaxWidth(), onClick = { vm.resetMix() })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VV.Panel).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ChannelCard(vm: EditorViewModel, b: Bus, m: BusMix, audible: Boolean) {
    var manual by remember(b) { mutableStateOf(EqPreset.of(m.eq) == null) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.label, style = MaterialTheme.typography.titleSmall, color = if (audible) VV.Text else VV.Faint, modifier = Modifier.weight(1f))
            SmallToggle("M", "Muto: ${b.label}", m.mute, VV.Danger) { vm.setBus(b, m.copy(mute = it)) }
            SmallToggle("S", "Solo: ${b.label}", m.solo, VV.Tally) { vm.setBus(b, m.copy(solo = it)) }
        }
        LevelMeter(b, vm.isPlaying, Modifier.fillMaxWidth().height(8.dp))
        LabeledSlider("Volume", null, m.gainDb.roundToInt(), range = -30..12, format = { if (it > 0) "+$it dB" else "$it dB" }) {
            vm.setBus(b, m.copy(gainDb = it.toFloat()))
        }
        LabeledSlider("Bilanciamento", null, (m.pan * 100).roundToInt(), range = -100..100, format = { v ->
            when { v == 0 -> "Centro"; v < 0 -> "Sinistra ${-v}"; else -> "Destra $v" }
        }) { vm.setBus(b, m.copy(pan = it / 100f)) }
        PanelLabel("Equalizzatore")
        PillRow(EqPreset.entries.map { it.label } + "A mano", EqPreset.of(m.eq)?.takeIf { !manual }?.label ?: "A mano", { it }) { label ->
            val preset = EqPreset.entries.firstOrNull { it.label == label }
            if (preset == null) manual = true else { manual = false; vm.setBus(b, m.copy(eq = preset.eq)) }
        }
        if (manual) {
            val f = { v: Int -> if (v > 0) "+$v dB" else "$v dB" }
            LabeledSlider("Bassi (120 Hz)", null, m.eq.lowDb.roundToInt(), range = -12..12, format = f) { vm.setBus(b, m.copy(eq = m.eq.copy(lowDb = it.toFloat()))) }
            LabeledSlider("Medi (1,5 kHz)", null, m.eq.midDb.roundToInt(), range = -12..12, format = f) { vm.setBus(b, m.copy(eq = m.eq.copy(midDb = it.toFloat()))) }
            LabeledSlider("Alti (8 kHz)", null, m.eq.highDb.roundToInt(), range = -12..12, format = f) { vm.setBus(b, m.copy(eq = m.eq.copy(highDb = it.toFloat()))) }
        }
        PanelLabel("Compressore")
        Segmented(Comp.entries, m.comp, { it.label }) { vm.setBus(b, m.copy(comp = it)) }
    }
}

@Composable
private fun MasterCard(vm: EditorViewModel) {
    val p = vm.project ?: return
    val mix = p.mix
    Card {
        Text("Uscita", style = MaterialTheme.typography.titleSmall)
        LevelMeter(null, vm.isPlaying, Modifier.fillMaxWidth().height(10.dp))
        PanelLabel("Volume finale per")
        PillRow(LoudTarget.entries, mix.target, { it.label }) { vm.setMix { m -> m.copy(target = it) } }
        Hint("Si applica nel video esportato, se in Esporta è acceso \"Volume uniformato\".")
        ToggleRow("Limitatore dei picchi (niente distorsione)", mix.limiter) { vm.setMix { m -> m.copy(limiter = it) } }
        PanelLabel("Musica sotto le voci (ducking)")
        ToggleRow("Abbassa la musica quando si parla", p.duck) { vm.setDuck(on = it) }
        if (p.duck) {
            LabeledSlider("Quanto abbassarla", null, p.duckDb, range = 4..24, format = { "–$it dB" }) { vm.setDuck(db = it) }
            PanelLabel("Velocità")
            Segmented(DuckSpeed.entries, mix.duckSpeed, { it.label }) { vm.setMix { m -> m.copy(duckSpeed = it) } }
        }
    }
}

@Composable
private fun SmallToggle(text: String, desc: String, on: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (on) color else VV.Raised)
            .clickable { onChange(!on) }.semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (on) VV.Canvas else VV.Text) }
}

/** Indicatore di livello (da -60 a 0 dBFS): verde, giallo sopra -12, rosso sopra -3. */
@Composable
private fun LevelMeter(b: Bus?, playing: Boolean, modifier: Modifier) {
    var db by remember { mutableFloatStateOf(-60f) }
    // Si aggiorna solo durante l'ascolto (da fermo l'indicatore scende a zero).
    LaunchedEffect(b, playing) {
        while (playing) { withFrameMillis { }; val v = Meters.db(b); if (abs(v - db) > 0.2f) db = v }
        db = -60f
    }
    Canvas(modifier.clip(RoundedCornerShape(4.dp)).background(VV.Raised).semantics { contentDescription = "Livello ${db.roundToInt()} dB" }) {
        val x = ((db + 60f) / 60f).coerceIn(0f, 1f) * size.width
        val y12 = (48f / 60f) * size.width
        val y3 = (57f / 60f) * size.width
        drawRect(Color(0xFF3FB27F), size = Size(minOf(x, y12), size.height))
        if (x > y12) drawRect(VV.Tally, Offset(y12, 0f), Size(minOf(x, y3) - y12, size.height))
        if (x > y3) drawRect(VV.Danger, Offset(y3, 0f), Size(x - y3, size.height))
    }
}

