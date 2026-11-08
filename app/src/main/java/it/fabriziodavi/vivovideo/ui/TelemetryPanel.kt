package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.TeleRenderer
import it.fabriziodavi.vivovideo.engine.TeleStore
import it.fabriziodavi.vivovideo.model.TeleItem
import it.fabriziodavi.vivovideo.model.TeleLayout
import it.fabriziodavi.vivovideo.model.TeleLayoutCalc
import it.fabriziodavi.vivovideo.model.TeleSlot
import it.fabriziodavi.vivovideo.model.TeleStyle
import it.fabriziodavi.vivovideo.model.TeleSync
import it.fabriziodavi.vivovideo.model.TeleTrack
import it.fabriziodavi.vivovideo.model.TeleWidget
import it.fabriziodavi.vivovideo.model.Telemetry
import java.util.Locale

val SPORT_TABS = listOf("Attività", "Indicatori", "Stile")

/** Frecce corte per i posti nel quadro. */
private fun TeleSlot.arrow() = when (this) {
    TeleSlot.TOP_LEFT -> "↖"; TeleSlot.TOP -> "↑"; TeleSlot.TOP_RIGHT -> "↗"; TeleSlot.LEFT -> "←"
    TeleSlot.RIGHT -> "→"; TeleSlot.BOTTOM_LEFT -> "↙"; TeleSlot.BOTTOM -> "↓"; TeleSlot.BOTTOM_RIGHT -> "↘"
}

/** Il dato c'è nel file? (velocità, distanza e ora ci sono sempre). */
private fun TeleTrack.supports(w: TeleWidget) = when (w) {
    TeleWidget.HEART -> hasHeart; TeleWidget.POWER -> hasPower; TeleWidget.CADENCE -> hasCadence
    TeleWidget.GRADE, TeleWidget.ALTITUDE -> hasAltitude; TeleWidget.MAP -> hasGps; TeleWidget.TEMP -> hasTemp
    else -> true
}

/**
 * Dati della pedalata: file dell'attività (Garmin, Strava…), abbinamento ai video, indicatori, stile.
 * L'anteprima sopra mostra già il risultato.
 */
@Composable
fun TelemetryPanel(vm: EditorViewModel, running: Boolean, tab: Int, onPick: () -> Unit) {
    val p = vm.project ?: return
    val t = p.telemetry
    val enabled = !running
    if (t == null) {
        Hint("Metti sul video i dati del giro registrati dal Garmin (o dal telefono): velocità, battito, potenza, cadenza, pendenza, quota e la mappa che si disegna mentre pedali. Si abbinano da soli ai video grazie all'ora delle riprese.")
        PrimaryButton("Scegli il file dell'attività", Modifier.fillMaxWidth(), enabled = enabled, onClick = onPick)
        Hint("File .fit (Garmin Connect: attività → ⚙ → Esporta originale), .gpx o .tcx (Strava: attività → ⋯ → Esporta GPX). Va bene anche lo .zip di Garmin così com'è, e i .gz.")
        return
    }
    val track = remember(t.file) { TeleStore.load(t.file) }
    LaunchedEffect(p.clips.map { it.uri }) { vm.syncTelemetry() }
    if (track == null) {
        Hint("Il file dell'attività non si legge più. Sceglilo di nuovo.")
        PrimaryButton("Scegli il file dell'attività", Modifier.fillMaxWidth(), enabled = enabled, onClick = onPick)
        return
    }
    when (tab) {
        0 -> ActivityTab(vm, t, track, enabled, onPick)
        1 -> WidgetsTab(vm, t, track, enabled)
        else -> StyleTab(vm, t, track, enabled)
    }
}

@Composable
private fun ActivityTab(vm: EditorViewModel, t: Telemetry, track: TeleTrack, enabled: Boolean, onPick: () -> Unit) {
    val p = vm.project ?: return
    val start = java.time.Instant.ofEpochMilli(track.startMs).atZone(java.time.ZoneId.systemDefault())
    val durS = (track.endMs - track.startMs) / 1000
    Text(t.name, style = MaterialTheme.typography.titleSmall)
    Text(
        String.format(Locale.ITALIAN, "%02d/%02d/%d alle %02d:%02d · %.1f km · %d:%02d h · +%.0f m",
            start.dayOfMonth, start.monthValue, start.year, start.hour, start.minute, track.totalDistanceM / 1000f, durS / 3600, (durS / 60) % 60, track.climbM),
        style = MaterialTheme.typography.bodySmall,
    )
    val have = TeleWidget.entries.filter { track.supports(it) && it != TeleWidget.DISTANCE && it != TeleWidget.TIME }.joinToString(", ") { it.label.lowercase() }
    Hint("Nel file: $have.")
    val videos = p.clips.count { !it.isPhoto }
    val n = TeleSync.covered(t, track, p.clips)
    PanelLabel("Abbinamento ai video")
    Text(
        if (n == videos) "Tutti i video ($n) cadono dentro l'attività." else "$n video su $videos cadono dentro l'attività.",
        style = MaterialTheme.typography.bodyMedium, color = if (n == 0) VV.Danger else VV.Text,
    )
    Hint("L'app usa l'ora delle riprese e, se il video ha la posizione, il punto del giro in cui eri. Se i numeri sono in anticipo o in ritardo (ad esempio la velocità sale prima che parti), spostali qui.")
    val off = t.offsetMs
    Text("Spostamento: " + String.format(Locale.ITALIAN, "%+.1f s", off / 1000f), style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-10_000L, -1_000L, -200L, 200L, 1_000L, 10_000L).forEach { d ->
            SecondaryButton(String.format(Locale.ITALIAN, "%+.1f s", d / 1000f).replace(",0", "")) {
                if (enabled) vm.updateTelemetry { it.copy(offsetMs = it.offsetMs + d) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Azzera", onClick = { if (enabled) vm.updateTelemetry { it.copy(offsetMs = 0) } })
        SecondaryButton("Rifai l'abbinamento", onClick = { if (enabled) vm.syncTelemetry(force = true) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Cambia file", onClick = { if (enabled) onPick() })
        SecondaryButton("Togli i dati", onClick = { if (enabled) vm.removeTelemetry() })
    }
}

@Composable
private fun WidgetsTab(vm: EditorViewModel, t: Telemetry, track: TeleTrack, enabled: Boolean) {
    PanelLabel("Schemi pronti")
    PillRow(TeleLayout.entries, null as TeleLayout?, { it?.label ?: "" }, enabled) { l ->
        if (l != null) vm.updateTelemetry { it.copy(items = l.items.filter { i -> track.supports(i.widget) }) }
    }
    Hint("Poi accendi, sposta e ingrandisci ogni indicatore. Quelli nello stesso posto si mettono uno sopra l'altro.")
    TeleWidget.entries.forEach { w ->
        val item = t.items.firstOrNull { it.widget == w }
        val ok = track.supports(w)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (item != null) VV.Raised2 else VV.Raised).padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            ToggleRow(w.label + if (ok) "" else " (non nel file)", item != null, enabled && ok) { on ->
                vm.updateTelemetry { tt ->
                    if (on) tt.copy(items = tt.items + TeleItem(w, defaultSlot(w, tt)))
                    else tt.copy(items = tt.items.filter { it.widget != w })
                }
            }
            if (item == null) {
                Text(w.hint, style = MaterialTheme.typography.bodySmall, color = VV.Dim)
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeleSlot.entries.forEach { s ->
                        val on = s == item.slot
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) VV.Tally else VV.Raised)
                                .clickable(enabled = enabled) { vm.updateTelemetry { tt -> tt.copy(items = tt.items.map { if (it.widget == w) it.copy(slot = s) else it }) } },
                            contentAlignment = Alignment.Center,
                        ) { Text(s.arrow(), color = if (on) VV.TallyInk else VV.Text) }
                    }
                }
                LabeledSlider("Grandezza", null, (item.scale * 100).toInt(), enabled, range = 50..200, format = { "$it%" }) { v ->
                    vm.updateTelemetry { tt -> tt.copy(items = tt.items.map { if (it.widget == w) it.copy(scale = v / 100f) else it }) }
                }
            }
        }
    }
}

/** Posto libero di partenza per un indicatore appena acceso. */
private fun defaultSlot(w: TeleWidget, t: Telemetry): TeleSlot {
    val pref = when (w) {
        TeleWidget.SPEED -> TeleSlot.BOTTOM_LEFT; TeleWidget.MAP -> TeleSlot.TOP_RIGHT; TeleWidget.ALTITUDE -> TeleSlot.BOTTOM
        TeleWidget.GRADE -> TeleSlot.TOP_LEFT; TeleWidget.TIME, TeleWidget.DISTANCE -> TeleSlot.TOP
        else -> TeleSlot.BOTTOM_RIGHT
    }
    return pref.takeIf { s -> t.items.count { it.slot == s } < 2 } ?: TeleSlot.entries.minBy { s -> t.items.count { it.slot == s } }
}

@Composable
private fun StyleTab(vm: EditorViewModel, t: Telemetry, track: TeleTrack, enabled: Boolean) {
    val ctx = LocalContext.current
    PanelLabel("Stile")
    // Ogni stile con un'anteprima vera: contagiri e battito al momento più veloce del giro.
    val peak = remember(track) {
        var best = 0; for (i in 0 until track.size) if (!track.speedKmh[i].isNaN() && track.speedKmh[i] > track.speedKmh[best].let { if (it.isNaN()) -1f else it }) best = i
        track.sample(track.timeMs[best])
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeleStyle.entries.forEach { st ->
            val on = st == t.style
            val img = remember(st, t.accent, track, peak) {
                peak?.let { s -> stylePreview(ctx, t.copy(style = st), track, s) }
            }
            Column(
                Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                    .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { vm.updateTelemetry { it.copy(style = st) } }.padding(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A4A3F)), contentAlignment = Alignment.Center) {
                    if (img != null) Image(img.asImageBitmap(), st.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().padding(4.dp))
                }
                Text(st.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text, modifier = Modifier.padding(top = 6.dp))
                Text(st.hint, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
    }
    PanelLabel("Colore di risalto")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Telemetry.ACCENTS.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(c))
                    .border(if (c == t.accent) 3.dp else 1.dp, if (c == t.accent) VV.Tally else VV.Line, RoundedCornerShape(15.dp))
                    .clickable(enabled = enabled) { vm.updateTelemetry { it.copy(accent = c) } }
            )
        }
    }
    LabeledSlider("Grandezza di tutti", null, (t.size * 100).toInt(), enabled, range = 50..180, format = { "$it%" }) { v -> vm.updateTelemetry { it.copy(size = v / 100f) } }
    LabeledSlider("Opacità", null, (t.opacity * 100).toInt(), enabled, range = 20..100, format = { "$it%" }) { v -> vm.updateTelemetry { it.copy(opacity = v / 100f) } }
    ToggleRow("Badge MAX quando tocchi il massimo del giro", t.peaks, enabled) { v -> vm.updateTelemetry { it.copy(peaks = v) } }
    ToggleRow("Zona sicura per reel e TikTok (video verticali)", t.safeZone, enabled) { v -> vm.updateTelemetry { it.copy(safeZone = v) } }
    PanelLabel("Zone di potenza e battito")
    LabeledSlider("FTP", if (t.ftp == 0) "Stimata dal giro: ${track.ftpAuto} W" else null, t.ftp, enabled, range = 0..500,
        format = { if (it == 0) "auto" else "$it W" }) { v -> vm.updateTelemetry { it.copy(ftp = v) } }
    LabeledSlider("Frequenza massima", if (t.hrMax == 0) "Dal giro: ${track.hrMaxAuto} bpm" else null, t.hrMax, enabled, range = 0..220,
        format = { if (it == 0) "auto" else "$it bpm" }) { v -> vm.updateTelemetry { it.copy(hrMax = v) } }
}

/** Contagiri e battito disegnati affiancati, per scegliere lo stile. */
private fun stylePreview(ctx: android.content.Context, t: Telemetry, track: TeleTrack, s: it.fabriziodavi.vivovideo.model.TeleSample): Bitmap? = runCatching {
    val r = TeleRenderer(ctx, t, track)
    val u = 6f
    val a = TeleLayoutCalc.sizeU(TeleWidget.SPEED, t.style)
    val b = TeleLayoutCalc.sizeU(TeleWidget.HEART, t.style)
    val w = ((a.first + b.first + 3f) * u).toInt(); val h = (maxOf(a.second, b.second) * u).toInt()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val one = Bitmap.createBitmap((a.first * u).toInt(), (a.second * u).toInt(), Bitmap.Config.ARGB_8888)
    r.draw(Canvas(one), TeleWidget.SPEED, TeleItem(TeleWidget.SPEED, TeleSlot.BOTTOM_LEFT), s, u, 400)
    c.drawBitmap(one, 0f, (h - one.height) / 2f, null)
    val two = Bitmap.createBitmap((b.first * u).toInt(), (b.second * u).toInt(), Bitmap.Config.ARGB_8888)
    r.draw(Canvas(two), TeleWidget.HEART, TeleItem(TeleWidget.HEART, TeleSlot.BOTTOM_LEFT), s, u, 400)
    c.drawBitmap(two, one.width + 3f * u, (h - two.height) / 2f, null)
    out
}.getOrNull()
