package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import it.fabriziodavi.vivovideo.model.Credits
import it.fabriziodavi.vivovideo.model.creditsRoll

/** Titoli di coda: titolo, righe "ruolo e nomi", crediti dei brani, durata e dove metterli. */
@Composable
fun CreditsScreen(vm: EditorViewModel) {
    val p = vm.project ?: return
    BackHandler { vm.showCredits = false }
    val music = p.credits()
    val text = vm.creditsText()
    val suggested = (Credits.suggestedUs(text) / 1_000_000).toInt()
    val seconds = if (vm.creditsSeconds > 0) vm.creditsSeconds else suggested
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
        focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
    )

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.showCredits = false }
            Text("Titoli di coda", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hint("Un rullo di testo che sale piano alla fine del video, come al cinema. Dopo li trovi nella timeline come un testo: puoi spostarli o allungarli.")
            PanelLabel("Titolo")
            OutlinedTextField(
                value = vm.creditsTitle, onValueChange = { vm.creditsTitle = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Per esempio: Grazie per la visione", color = VV.Faint) }, colors = colors,
            )
            PanelLabel("Righe")
            vm.creditsRows.forEachIndexed { i, r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = r.role, onValueChange = { v -> vm.creditsRows = vm.creditsRows.toMutableList().also { it[i] = r.copy(role = v) } },
                        modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Ruolo", color = VV.Faint) }, colors = colors,
                    )
                    OutlinedTextField(
                        value = r.names, onValueChange = { v -> vm.creditsRows = vm.creditsRows.toMutableList().also { it[i] = r.copy(names = v) } },
                        modifier = Modifier.weight(1.3f), singleLine = true, placeholder = { Text("Nomi", color = VV.Faint) }, colors = colors,
                    )
                    IconBtn(VVIcons.Close, "Togli la riga") { vm.creditsRows = vm.creditsRows.filterIndexed { k, _ -> k != i } }
                }
            }
            if (vm.creditsRows.size < 20) SecondaryButton("Aggiungi una riga", Modifier.fillMaxWidth(), onClick = { vm.creditsRows = vm.creditsRows + Credits.Row("", "") })
            if (music.isNotEmpty()) {
                ToggleRow("Aggiungi i crediti dei brani (${music.size})", vm.creditsMusic) { vm.creditsMusic = it }
                Hint("I brani della libreria online chiedono di citare l'autore: così è già fatto.")
            }
            PanelLabel("Dove")
            Segmented(listOf(true, false), vm.creditsOnBlack, { if (it) "Su nero, dopo il video" else "Sopra le ultime scene" }) { vm.creditsOnBlack = it }
            LabeledSlider("Durata", null, seconds, range = 6..30, format = { "$it s" }) { vm.creditsSeconds = it }
            if (vm.creditsSeconds == 0) Hint("Durata proposta in base alle righe: circa un secondo a riga.")
            if (text.isNotBlank()) {
                PanelLabel("Anteprima del testo")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.Black).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    text.lines().forEach { l -> Text(l, style = MaterialTheme.typography.bodySmall, color = VV.Text) }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (p.creditsRoll != null) "Rifai i titoli di coda" else "Metti i titoli di coda", Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                if (vm.creditsSeconds == 0) vm.creditsSeconds = suggested
                vm.applyCredits()
            }
            if (p.creditsRoll != null) SecondaryButton("Togli i titoli di coda", Modifier.fillMaxWidth(), onClick = { vm.removeCredits() })
        }
    }
}
