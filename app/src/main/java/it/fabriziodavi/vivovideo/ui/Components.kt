package it.fabriziodavi.vivovideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Tempo in formato m:ss,d (decimi), con cifre a larghezza fissa del font. */
fun formatTime(us: Long): String {
    val totalMs = us / 1000
    val m = totalMs / 60_000
    val s = (totalMs / 1000) % 60
    val d = (totalMs / 100) % 10
    return "%d:%02d,%d".format(m, s, d)
}

@Composable
fun PanelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = VV.Dim)
}

/** Controllo a segmenti, come i selettori degli editor professionali. */
@Composable
fun <T> Segmented(items: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VV.Raised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEach { item ->
            val on = item == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) VV.Tally else VV.Raised)
                    .clickable(enabled = enabled) { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) VV.TallyInk else VV.Dim,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Fila di "pastiglie" scorrevole, per elenchi più lunghi (filtri). */
@Composable
fun <T> PillRow(items: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) VV.Tally else VV.Raised)
                    .clickable(enabled = enabled) { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label(item), style = MaterialTheme.typography.labelLarge, color = if (on) VV.TallyInk else VV.Text)
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VV.TallyInk,
                checkedTrackColor = VV.Tally,
                uncheckedThumbColor = VV.Dim,
                uncheckedTrackColor = VV.Raised,
                uncheckedBorderColor = VV.Line,
            ),
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** Cursore che applica il valore quando si solleva il dito. */
@Composable
fun LabeledSlider(
    label: String,
    hint: String?,
    value: Int,
    enabled: Boolean = true,
    range: IntRange = 0..100,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(local.toInt()), style = MaterialTheme.typography.labelMedium, color = VV.Dim)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = VV.Text,
                activeTrackColor = VV.Tally,
                inactiveTrackColor = VV.Raised2,
            ),
        )
        hint?.let { Hint(it) }
    }
}
