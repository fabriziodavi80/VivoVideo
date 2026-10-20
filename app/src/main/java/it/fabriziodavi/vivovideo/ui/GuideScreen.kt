package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.fabriziodavi.vivovideo.BuildConfig

private sealed interface GuideBlock {
    data class Title(val text: String) : GuideBlock
    data class Section(val text: String) : GuideBlock
    data class Bullet(val text: AnnotatedString) : GuideBlock
    data class Para(val text: AnnotatedString) : GuideBlock
}

/** **grassetto** in arancione chiaro; le parole cercate ([marks]) evidenziate. */
private fun inline(s: String, marks: List<String> = emptyList()): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, p ->
        val bold = i % 2 == 1
        // Evidenzia le parole cercate (senza badare ad accenti e maiuscole).
        val folded = GuideIndex.fold(p)
        var k = 0
        while (k < p.length) {
            val hit = marks.mapNotNull { m -> folded.indexOf(m, k).takeIf { it >= 0 }?.let { it to m.length } }.minByOrNull { it.first }
            val end = hit?.first ?: p.length
            val chunk = p.substring(k, end)
            if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = VV.Tally)) { append(chunk) } else append(chunk)
            if (hit == null) break
            val stop = (hit.first + hit.second).coerceAtMost(p.length)
            withStyle(SpanStyle(background = VV.Tally.copy(alpha = 0.35f), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)) { append(p.substring(hit.first, stop)) }
            k = stop
        }
    }
}

private fun blocks(lines: List<String>, marks: List<String>): List<GuideBlock> = lines.map { raw ->
    val l = raw.replace("{VERSIONE}", BuildConfig.VERSION_NAME)
    if (l.startsWith("- ")) GuideBlock.Bullet(inline(l.removePrefix("- "), marks)) else GuideBlock.Para(inline(l, marks))
}

/**
 * Guida dell'app (assets/guida.md, aggiornata a ogni versione): indice per gruppi con ricerca,
 * poi un argomento per pagina con precedente e successivo. [topic] apre direttamente un argomento.
 */
@Composable
fun GuideScreen(topic: String? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    val doc = remember {
        GuideIndex.parse(runCatching { context.assets.open("guida.md").bufferedReader().use { it.readText() } }.getOrDefault("# Guida non disponibile"))
    }
    val (title, intro, sections) = doc
    val groups = remember(sections) { GuideIndex.grouped(sections) }
    var open by remember { mutableIntStateOf(GuideIndex.find(sections, topic)) }
    var query by remember { mutableStateOf("") }
    val marks = remember(query) { GuideIndex.fold(query).split(Regex("\\s+")).filter { it.length >= 2 } }
    BackHandler { if (open >= 0) open = -1 else onClose() }

    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 2.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, if (open >= 0) "Torna all'indice" else "Chiudi la guida") { if (open >= 0) open = -1 else onClose() }
            Text(if (open >= 0) "Indice" else "Guida", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (open >= 0) IconBtn(VVIcons.Close, "Chiudi la guida", onClick = onClose)
        }
        if (open >= 0) {
            val s = sections[open]
            val state = rememberLazyListState()
            LaunchedEffect(open) { state.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(GuideIndex.groupOf(s.title), style = MaterialTheme.typography.labelMedium, color = VV.Dim, modifier = Modifier.padding(top = 4.dp))
                    Text(s.title, style = MaterialTheme.typography.titleLarge, color = VV.Tally, modifier = Modifier.padding(bottom = 6.dp))
                }
                items(blocks(s.lines, marks)) { b -> GuideBlockView(b) }
                item { Spacer(Modifier.height(16.dp)) }
            }
            // Argomento precedente e successivo.
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val prev = sections.getOrNull(open - 1)
                val next = sections.getOrNull(open + 1)
                Box(Modifier.weight(1f)) { if (prev != null) SecondaryButton("‹ ${prev.title}", Modifier.fillMaxWidth(), onClick = { open-- }) }
                Box(Modifier.weight(1f)) { if (next != null) SecondaryButton("${next.title} ›", Modifier.fillMaxWidth(), onClick = { open++ }) }
            }
        } else {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                placeholder = { Text("Cerca nella guida (es. targhe, battiti, 4K)", color = VV.Faint) },
                trailingIcon = { if (query.isNotEmpty()) IconBtn(VVIcons.Close, "Cancella la ricerca") { query = "" } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VV.Tally, unfocusedBorderColor = VV.Line, cursorColor = VV.Tally,
                    focusedTextColor = VV.Text, unfocusedTextColor = VV.Text,
                ),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (marks.isNotEmpty()) {
                    val hits = GuideIndex.search(sections, query)
                    item {
                        Text(if (hits.isEmpty()) "Nessun argomento contiene queste parole." else "${hits.size} argomenti",
                            style = MaterialTheme.typography.labelMedium, color = VV.Dim, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(hits) { h ->
                        TopicRow(sections[h.index].title, inline(h.snippet, marks)) { open = h.index }
                    }
                } else {
                    item {
                        Text(title.replace("{VERSIONE}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        intro.forEach { Text(inline(it.replace("{VERSIONE}", BuildConfig.VERSION_NAME)), style = MaterialTheme.typography.bodySmall, color = VV.Dim) }
                    }
                    groups.forEach { (g, idx) ->
                        item { Text(g, style = MaterialTheme.typography.labelLarge, color = VV.Dim, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) }
                        items(idx) { i ->
                            val s = sections[i]
                            val first = s.lines.firstOrNull()?.let { GuideIndex.plain(it) }.orEmpty()
                            TopicRow(s.title, AnnotatedString(first), highlight = i == 0 && s.title.startsWith("Novità")) { open = i }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** Una riga dell'indice: titolo e la prima frase (o la frase trovata). */
@Composable
private fun TopicRow(title: String, preview: AnnotatedString, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (highlight) VV.Raised2 else VV.Raised)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (highlight) VV.Tally else VV.Text)
        if (preview.isNotEmpty()) Text(preview, style = MaterialTheme.typography.bodySmall, color = VV.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GuideBlockView(b: GuideBlock) {
    when (b) {
        is GuideBlock.Bullet -> Row {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = VV.Dim)
            Spacer(Modifier.width(10.dp))
            Text(b.text, style = MaterialTheme.typography.bodyMedium, color = VV.Text)
        }
        is GuideBlock.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium, color = VV.Dim)
        else -> {}
    }
}
