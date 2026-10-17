package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.Whisper
import it.fabriziodavi.vivovideo.model.SubPosition
import it.fabriziodavi.vivovideo.model.SubStyle
import it.fabriziodavi.vivovideo.model.Subtitles

/** Sottotitoli automatici: modello, lingua, stile e posizione, poi l'app ascolta il video. */
@Composable
fun SubtitlesScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val busy = vm.busy
    BackHandler { if (busy != null) vm.cancelSubtitles() else vm.showSubtitles = false }
    var model by remember { mutableStateOf(if (Whisper.isDownloaded(ctx, Whisper.Model.FAST) && !Whisper.isDownloaded(ctx, Whisper.Model.ACCURATE)) Whisper.Model.FAST else Whisper.Model.ACCURATE) }
    var lang by remember { mutableStateOf("it") }
    var style by remember { mutableStateOf(SubStyle.KARAOKE) }
    var pos by remember { mutableStateOf(SubPosition.BOTTOM) }
    val existing = vm.project?.texts?.count { it.subtitle } ?: 0

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { vm.showSubtitles = false }
            Text("Sottotitoli automatici", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hint("L'app ascolta il parlato del video (audio delle clip, voce fuori campo, audio staccato; la musica no) e scrive i sottotitoli con i tempi di ogni parola. Tutto avviene sul telefono: serve internet solo per scaricare il modello la prima volta.")
            PanelLabel("Riconoscimento")
            Whisper.Model.entries.filter { it != Whisper.Model.TINY }.forEach { m ->
                val on = m == model
                val ready = Whisper.isDownloaded(ctx, m)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(enabled = busy == null) { model = m }.padding(12.dp),
                ) {
                    Text(m.label + if (ready) "  ·  pronto" else "  ·  ${m.sizeMb} MB da scaricare", style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                    Text(if (m == Whisper.Model.FAST) "Più rapido, qualche errore in più." else "Più preciso con l'italiano, un po' più lento.", style = MaterialTheme.typography.bodySmall)
                }
            }
            PanelLabel("Lingua del parlato")
            Segmented(listOf("it", "en", "auto"), lang, { when (it) { "it" -> "Italiano"; "en" -> "Inglese"; else -> "Automatica" } }, busy == null) { lang = it }
            PanelLabel("Stile")
            PillRow(SubStyle.entries, style, { it.label }, busy == null) { style = it }
            // Esempio dello stile scelto.
            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2F38)), contentAlignment = Alignment.Center) {
                val sample = when (style) {
                    SubStyle.KARAOKE -> buildAnnotatedString {
                        append("CHE BELLA ")
                        withStyle(SpanStyle(color = Color(Subtitles.HIGHLIGHT))) { append("GIORNATA") }
                        append(" OGGI")
                    }
                    SubStyle.REVEAL -> buildAnnotatedString { append("CHE BELLA GIORNATA…") }
                    else -> buildAnnotatedString { append("Che bella giornata oggi") }
                }
                Text(
                    sample, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = if (style == SubStyle.BOX) Modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp) else Modifier,
                )
            }
            PanelLabel("Posizione")
            Segmented(SubPosition.entries, pos, { it.label }, busy == null) { pos = it }
            if (existing > 0) Hint("Ci sono già $existing sottotitoli automatici: verranno sostituiti dai nuovi (Annulla per tornare indietro).")
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = VV.Tally, trackColor = VV.Raised2)
                SecondaryButton("Annulla", Modifier.fillMaxWidth(), onClick = { vm.cancelSubtitles() })
            } else {
                PrimaryButton("Crea i sottotitoli", Modifier.fillMaxWidth()) { vm.autoSubtitles(model, lang, style, pos) }
                if (existing > 0) SecondaryButton("Togli i sottotitoli automatici", Modifier.fillMaxWidth(), onClick = { vm.removeSubtitles(); vm.showSubtitles = false })
            }
        }
    }
}
