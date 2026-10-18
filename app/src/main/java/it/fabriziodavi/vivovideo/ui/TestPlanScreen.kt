package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.model.TestPlan

/** Collaudo guidato: le prove da fare prima della versione ufficiale, con esito e segnalazione. */
@Composable
fun TestPlanScreen(vm: EditorViewModel) {
    val ctx = LocalContext.current
    val store = remember { TestPlan.Store(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val results = remember(tick) { store.all() }
    var noteFor by remember { mutableStateOf<TestPlan.Item?>(null) }
    var report by remember { mutableStateOf(false) }
    BackHandler { vm.showTestPlan = false }
    val done = TestPlan.items.count { results[it.id]?.state != null }
    val ko = TestPlan.items.count { results[it.id]?.state == false }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.showTestPlan = false }
            Text("Collaudo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            PrimaryButton("Segnala un problema") { report = true }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Provati $done di ${TestPlan.items.size}" + if (ko > 0) " · $ko con problemi" else "",
                style = MaterialTheme.typography.labelLarge, color = if (ko > 0) VV.Danger else VV.Tally,
            )
            // Barra semplice (senza animazioni): quante prove sono fatte.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(VV.Raised2)) {
                Box(Modifier.fillMaxWidth(done.toFloat() / TestPlan.items.size).height(4.dp).background(VV.Tally))
            }
            Hint("Fai le prove con i tuoi video veri. Se qualcosa non va, tocca Problema e scrivi due parole: poi \"Segnala un problema\" prepara un file da mandarmi (con l'esito del collaudo, i dettagli del telefono e del progetto aperto; niente video e niente chiavi dei servizi).")
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val groups = TestPlan.items.groupBy { it.area }
            groups.forEach { (area, list) ->
                item(key = "h_$area") { PanelLabel(area) }
                items(list, key = { it.id }) { item ->
                    val r = results[item.id] ?: TestPlan.Result()
                    val border = when (r.state) { true -> VV.Tally; false -> VV.Danger; null -> Color.Transparent }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VV.Raised)
                            .border(if (r.state != null) 2.dp else 0.dp, border, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = VV.Text)
                        Text("Come: " + item.how, style = MaterialTheme.typography.bodySmall, color = VV.Dim)
                        Text("Deve succedere: " + item.expect, style = MaterialTheme.typography.bodySmall, color = VV.Text)
                        if (r.note.isNotBlank()) Text("Nota: " + r.note, style = MaterialTheme.typography.bodySmall, color = VV.Danger)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            SecondaryButton("Funziona", Modifier.weight(1f), selected = r.state == true, onClick = {
                                store.set(item.id, if (r.state == true) null else true); tick++
                            })
                            SecondaryButton("Problema", Modifier.weight(1f), selected = r.state == false, onClick = { noteFor = item })
                        }
                    }
                }
            }
            item(key = "fine") {
                TextButton(onClick = { store.reset(); tick++ }, modifier = Modifier.padding(vertical = 12.dp)) { Text("Ricomincia il collaudo", color = VV.Dim) }
            }
        }
    }

    noteFor?.let { item ->
        var note by remember(item.id) { mutableStateOf(store.get(item.id).note) }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            containerColor = VV.Raised,
            title = { Text(item.title, style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cosa non va? (anche poche parole)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, focusedTextColor = VV.Text, unfocusedTextColor = VV.Text),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { store.set(item.id, false, note); tick++; noteFor = null }) { Text("Salva", color = VV.Tally) }
            },
            dismissButton = {
                TextButton(onClick = {
                    store.set(item.id, false, note); tick++; noteFor = null
                    vm.shareReport("Collaudo · ${item.area} · ${item.title}: $note")
                }) { Text("Salva e segnala", color = VV.Danger) }
            },
        )
    }
    if (report) ReportDialog(vm, onDismiss = { report = false })
}

/** Chiede due parole su cosa è successo e prepara la segnalazione da mandare. */
@Composable
fun ReportDialog(vm: EditorViewModel, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VV.Raised,
        title = { Text("Segnala un problema", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cosa è successo? Cosa ti aspettavi?", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, focusedTextColor = VV.Text, unfocusedTextColor = VV.Text),
                )
                Hint("Si prepara un file di testo: mandalo a Claude (allegalo nella chat). Dentro ci sono versione, telefono, collaudo, il progetto aperto (senza video) e gli ultimi errori. Le chiavi dei servizi musicali vengono tolte.")
            }
        },
        confirmButton = { TextButton(onClick = { vm.shareReport(note); onDismiss() }) { Text("Prepara e manda", color = VV.Tally) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = VV.Dim) } },
    )
}
