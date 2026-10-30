package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.Whisper
import it.fabriziodavi.vivovideo.model.Clip
import it.fabriziodavi.vivovideo.model.InterviewFormat
import it.fabriziodavi.vivovideo.model.InterviewOptions

/**
 * Intervista doppia (stile Le Iene): due video con le stesse domande. L'app ascolta i due video, trova domande e risposte
 * e monta per ogni domanda il cartello e le due risposte, alternate o a schermo diviso.
 */
@Composable
fun InterviewScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val busy = vm.busy
    BackHandler { if (busy != null) vm.cancelSubtitles() else vm.closeInterview() }
    var pickFirst by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickInterviewVideo(pickFirst, uri)
    }
    fun pick(first: Boolean) {
        pickFirst = first
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }
    var nameA by remember { mutableStateOf("") }
    var nameB by remember { mutableStateOf("") }
    var capA by remember { mutableStateOf("") }
    var capB by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(InterviewFormat.ALTERNATE) }
    var cards by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(Whisper.defaultModel(ctx)) }
    val qas = vm.interviewQas
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
        focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
    )

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { vm.closeInterview() }
            Text("Intervista doppia", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hint("Due persone, le stesse domande, due video. L'app li ascolta, trova le domande e ritaglia la risposta di ciascuno, poi monta tutto domanda per domanda. Il risultato si apre nell'editor, da ritoccare come gli altri progetti.")
            PanelLabel("I due video")
            VideoRow("A", vm.interviewA, busy == null) { pick(true) }
            VideoRow("B", vm.interviewB, busy == null) { pick(false) }
            PanelLabel("Nomi e didascalie (in basso, come in TV)")
            listOf(Triple("A", nameA, capA), Triple("B", nameB, capB)).forEach { (who, n, c) ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = n, onValueChange = { v -> if (who == "A") nameA = v else nameB = v }, singleLine = true, colors = colors,
                        modifier = Modifier.weight(1f), placeholder = { Text("Nome $who", color = VV.Faint) },
                    )
                    OutlinedTextField(
                        value = c, onValueChange = { v -> if (who == "A") capA = v else capB = v }, singleLine = true, colors = colors,
                        modifier = Modifier.weight(1.3f), placeholder = { Text("Didascalia (es. 46 anni, Brescia)", color = VV.Faint) },
                    )
                }
            }
            PanelLabel("Formato")
            InterviewFormat.entries.forEach { f ->
                val on = f == format
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(enabled = busy == null) { format = f }.padding(12.dp),
                ) {
                    Text(f.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                    Text(f.hint, style = MaterialTheme.typography.bodySmall)
                }
            }
            ToggleRow("Cartello con la domanda prima delle risposte", cards, busy == null) { cards = it }
            PanelLabel("Domande")
            ToggleRow("Trovale da sola (le frasi con il punto di domanda)", auto, busy == null) { auto = it; vm.resetInterviewAnalysis() }
            if (!auto) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, colors = colors, minLines = 3,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("Una domanda per riga, come la dice chi intervista", color = VV.Faint) },
                )
            }
            Hint("Le domande devono sentirsi nei video (chi intervista le fa a voce). Il riconoscimento vocale è sul telefono; internet serve solo per scaricare il modello la prima volta.")
            Whisper.Model.entries.filter { it != Whisper.Model.TINY }.forEach { m ->
                val on = m == model
                val ready = Whisper.isDownloaded(ctx, m)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(enabled = busy == null) { model = m }.padding(12.dp),
                ) {
                    Text(m.label + if (ready) "  ·  pronto" else "  ·  ${m.sizeMb} MB da scaricare", style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                    Text(m.hint, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (qas != null && qas.isNotEmpty()) {
                PanelLabel("Domande e risposte trovate")
                Hint("Togli con ✕ le domande che non vuoi nel video. Le risposte non trovate restano fuori da sole.")
                qas.forEachIndexed { i, qa ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("${i + 1}. ${qa.question}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "A: " + (qa.a?.let { sec(it.lengthUs) } ?: "non trovata") + "   ·   B: " + (qa.b?.let { sec(it.lengthUs) } ?: "non trovata"),
                                style = MaterialTheme.typography.bodySmall, color = if (qa.usable) VV.Dim else VV.Danger,
                            )
                        }
                        IconBtn(VVIcons.Close, "Togli la domanda", enabled = busy == null) { vm.removeInterviewQuestion(i) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ready = vm.interviewA != null && vm.interviewB != null
            val questions = typed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = VV.Tally, trackColor = VV.Raised2)
                SecondaryButton("Annulla", Modifier.fillMaxWidth(), onClick = { vm.cancelSubtitles() })
            } else if (qas != null && qas.any { it.usable }) {
                PrimaryButton("Crea l'intervista", Modifier.fillMaxWidth()) {
                    vm.createInterview(InterviewOptions(format, nameA, nameB, capA, capB, cardUs = if (cards) 2_500_000L else 0L))
                }
                SecondaryButton("Rifai l'analisi", Modifier.fillMaxWidth(), onClick = { vm.analyzeInterview(model, "it", if (auto) emptyList() else questions) })
            } else {
                PrimaryButton("Analizza i due video", Modifier.fillMaxWidth(), enabled = ready && (auto || questions.isNotEmpty())) {
                    vm.analyzeInterview(model, "it", if (auto) emptyList() else questions)
                }
                if (!ready) Hint("Scegli prima il video A e il video B.")
            }
        }
    }
}

/** Un video scelto (nome e durata) o il pulsante per sceglierlo. */
@Composable
private fun VideoRow(who: String, c: Clip?, enabled: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).clickable(enabled = enabled, onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(who, style = MaterialTheme.typography.titleMedium, color = VV.Tally, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(c?.fileName?.ifBlank { "Video $who" } ?: "Scegli il video $who", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c != null) Text("${formatTime(c.sourceDurationUs)} · ${c.width}×${c.height}", style = MaterialTheme.typography.bodySmall, color = VV.Dim)
        }
        Text(if (c == null) "Scegli" else "Cambia", style = MaterialTheme.typography.labelLarge, color = VV.Tally)
    }
}

private fun sec(us: Long) = String.format(java.util.Locale.ITALIAN, "%.1f s", us / 1e6)
