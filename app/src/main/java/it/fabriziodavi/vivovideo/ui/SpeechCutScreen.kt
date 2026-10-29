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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.engine.Whisper
import it.fabriziodavi.vivovideo.model.JumpCuts
import it.fabriziodavi.vivovideo.model.JumpStyle

/**
 * Pulizia del parlato: toglie le pause lunghe e (se si vuole) gli "ehm", come negli editor per podcast.
 * In modalità jump cut ([EditorViewModel.jumpMode]) a ogni taglio cambia anche l'inquadratura, con stili pronti.
 */
@Composable
fun SpeechCutScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val busy = vm.busy
    BackHandler { if (busy != null) vm.cancelSubtitles() else { vm.closeSpeechCut() } }
    var model by remember { mutableStateOf(if (Whisper.isDownloaded(ctx, Whisper.Model.FAST) && !Whisper.isDownloaded(ctx, Whisper.Model.ACCURATE)) Whisper.Model.FAST else Whisper.Model.ACCURATE) }
    var lang by remember { mutableStateOf("it") }
    val jump = vm.jumpMode
    // Jump cut: ritmo serrato di serie (0,4 s), come nei video parlati sui social.
    var minPause by remember { mutableStateOf(if (jump) 400_000L else 700_000L) }
    var fillers by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(JumpStyle.CLEAN) }
    var whoosh by remember { mutableStateOf(true) }
    // Con Social servono le parole (sottotitoli): il riconoscimento vocale si usa anche senza togliere gli ehm.
    val transcribe = fillers || (jump && style == JumpStyle.SOCIAL)
    val plan = vm.speechPlan
    val total = vm.project?.durationUs ?: 0L
    val summary = remember(plan, style, whoosh, vm.speechWords) { if (jump && plan != null) vm.jumpPreview(style, whoosh) else null }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro", enabled = busy == null) { vm.closeSpeechCut() }
            Text(if (jump) "Jump cut" else "Taglia pause e ehm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (jump) {
                Hint("Per i video parlati: l'app toglie le pause (e, se vuoi, gli ehm) e a ogni taglio cambia l'inquadratura, normale e stretta a turno, centrata sul volto. Così il salto del taglio sembra voluto. Prima vedi il riepilogo, poi decidi; un solo Annulla toglie tutto.")
                PanelLabel("Stile")
                JumpStyle.entries.forEach { st ->
                    val on = st == style
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                            .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(enabled = busy == null) {
                                style = st
                                // Social ha bisogno delle parole: se l'analisi fatta non le ha, si rifà.
                                if (st == JumpStyle.SOCIAL && vm.speechWords.isEmpty()) vm.speechPlan = null
                            }.padding(12.dp),
                    ) {
                        Text(st.label, style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                        Text(st.hint, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (style == JumpStyle.SOCIAL) ToggleRow("Whoosh sui lampi", whoosh, busy == null) { whoosh = it }
            } else {
                Hint("L'app ascolta l'audio delle clip e toglie i silenzi lunghi, lasciando un attimo di respiro fra le frasi. Le foto, le clip mute, la musica e le voci fuori campo non si toccano. Prima vedi quanto si toglie, poi decidi.")
            }
            PanelLabel("Pause più lunghe di")
            PillRow(listOf(0L, 400_000L, 700_000L, 1_000_000L), minPause, { if (it == 0L) "Lasciale" else String.format(java.util.Locale.ITALIAN, "%.1f s", it / 1e6) }, busy == null) {
                minPause = it; vm.speechPlan = null
            }
            Hint("0,4 s: ritmo serrato da video social. 0,7 s: naturale. 1 s: toglie solo i silenzi davvero lunghi.")
            ToggleRow("Togli anche ehm, eh, uhm, mmm", fillers, busy == null) { fillers = it; vm.speechPlan = null }
            if (transcribe) {
                Hint(
                    if (fillers) "Per le parole di riempimento serve il riconoscimento vocale (sul telefono; internet solo per scaricare il modello la prima volta)."
                    else "Per i sottotitoli dello stile Social serve il riconoscimento vocale (sul telefono; internet solo per scaricare il modello la prima volta).",
                )
                Whisper.Model.entries.filter { it != Whisper.Model.TINY }.forEach { m ->
                    val on = m == model
                    val ready = Whisper.isDownloaded(ctx, m)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) VV.Raised2 else VV.Raised)
                            .border(if (on) 2.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(enabled = busy == null) { model = m; vm.speechPlan = null }.padding(12.dp),
                    ) {
                        Text(m.label + if (ready) "  ·  pronto" else "  ·  ${m.sizeMb} MB da scaricare", style = MaterialTheme.typography.titleSmall, color = if (on) VV.Tally else VV.Text)
                        Text(if (m == Whisper.Model.FAST) "Più rapido." else "Più preciso con l'italiano.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Segmented(listOf("it", "en", "auto"), lang, { when (it) { "it" -> "Italiano"; "en" -> "Inglese"; else -> "Automatica" } }, busy == null) { lang = it; vm.speechPlan = null }
            }
            if (plan != null) {
                PanelLabel("Cosa si toglie")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (plan.isEmpty) Text("Niente da togliere con queste impostazioni.", style = MaterialTheme.typography.bodyMedium)
                    else {
                        if (minPause > 0) Text("${plan.pauses} pause · ${sec(plan.pauseUs)}", style = MaterialTheme.typography.bodyMedium)
                        if (fillers) Text("${plan.fillers} ehm e simili · ${sec(plan.fillerUs)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Il video passa da ${formatTime(total)} a ${formatTime(total - plan.totalUs)}", style = MaterialTheme.typography.titleSmall, color = VV.Tally)
                    }
                }
                if (jump && summary != null) {
                    PanelLabel("Riepilogo dei jump cut")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${summary.cuts} tagli con cambio d'inquadratura", style = MaterialTheme.typography.bodyMedium)
                        Text("${summary.zoomed} pezzi stretti (+${((JumpCuts.ZOOM - 1) * 100).toInt()}%) sul volto", style = MaterialTheme.typography.bodyMedium)
                        if (style == JumpStyle.DYNAMIC) Text("${summary.punches} punch-in sulle frasi più forti", style = MaterialTheme.typography.bodyMedium)
                        if (style == JumpStyle.SOCIAL) {
                            Text("${summary.flashes} lampi" + if (summary.whooshes > 0) " con whoosh" else "", style = MaterialTheme.typography.bodyMedium)
                            Text(if (summary.subtitles > 0) "${summary.subtitles} sottotitoli parola per parola (sostituiscono quelli che ci sono)" else "Nessuna parola sentita: niente sottotitoli", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Micro-dissolvenze dell'audio su ogni taglio, niente clic.", style = MaterialTheme.typography.bodySmall, color = VV.Dim)
                        Text("Il video passa da ${formatTime(summary.beforeUs)} a ${formatTime(summary.afterUs)}", style = MaterialTheme.typography.titleSmall, color = VV.Tally)
                    }
                    if (summary.cuts == 0) Hint("Nessun taglio dentro una stessa ripresa: l'inquadratura non cambia. Prova pause più corte.")
                }
                if (fillers && plan.fillerCuts.isNotEmpty()) {
                    PanelLabel("Gli ehm trovati")
                    Hint("Ascolta ognuno: ▶ com'è adesso, ✂ come sarà dopo il taglio. Togli la spunta a quelli da lasciare. Quelli «da controllare» o «da ascoltare» sono dubbi (forse una parola tenuta a lungo): di serie restano, metti la spunta se sono ehm.")
                    plan.fillerCuts.forEachIndexed { i, f ->
                        FillerRow(
                            at = f.span.startUs, lenUs = f.span.lengthUs,
                            label = when {
                                f.doubtful && f.word == null -> "possibile ehm · da ascoltare"
                                f.doubtful -> "«${f.word}»? da controllare"
                                f.word != null -> "«${f.word}»"
                                else -> "suono di esitazione"
                            },
                            on = f.on,
                            onToggle = { vm.toggleFiller(i) },
                            onPlay = { vm.playFiller(i, after = false) },
                            onPlayAfter = { vm.playFiller(i, after = true) },
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy != null) {
                Text(busy, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = VV.Tally, trackColor = VV.Raised2)
                SecondaryButton("Annulla", Modifier.fillMaxWidth(), onClick = { vm.cancelSubtitles() })
            } else if (plan != null && !plan.isEmpty) {
                if (jump) PrimaryButton("Fai i jump cut", Modifier.fillMaxWidth()) { vm.applyJumpCuts(style, whoosh) }
                else PrimaryButton("Taglia ${sec(plan.totalUs)}", Modifier.fillMaxWidth()) { vm.applySpeechCuts() }
                SecondaryButton("Rifai l'analisi", Modifier.fillMaxWidth(), onClick = { vm.analyzeSpeech(model, lang, minPause, fillers, transcribe) })
            } else {
                PrimaryButton("Analizza", Modifier.fillMaxWidth(), enabled = minPause > 0 || fillers) { vm.analyzeSpeech(model, lang, minPause, fillers, transcribe) }
            }
        }
    }
}

/** Un ehm della lista: spunta, quando e quanto dura, ascolto prima e dopo il taglio. */
@Composable
private fun FillerRow(at: Long, lenUs: Long, label: String, on: Boolean, onToggle: () -> Unit, onPlay: () -> Unit, onPlayAfter: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = VV.Tally, checkmarkColor = VV.TallyInk))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (on) VV.Text else VV.Dim)
            Text("${formatTime(at)} · ${sec(lenUs)}", style = MaterialTheme.typography.bodySmall, color = VV.Dim)
        }
        TextButton(onClick = onPlay) { Text("▶", color = VV.Text) }
        TextButton(onClick = onPlayAfter) { Text("✂ ▶", color = VV.Tally) }
    }
}

private fun sec(us: Long) = String.format(java.util.Locale.ITALIAN, "%.1f s", us / 1e6)
