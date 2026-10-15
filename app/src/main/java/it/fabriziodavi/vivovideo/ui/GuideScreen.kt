package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
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

/** **grassetto** in arancione chiaro. */
private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = VV.Tally)) { append(p) } else append(p)
    }
}

private fun parse(md: String): List<GuideBlock> = md.lines().mapNotNull { raw ->
    val l = raw.trimEnd().replace("{VERSIONE}", BuildConfig.VERSION_NAME)
    when {
        l.isBlank() -> null
        l.startsWith("# ") -> GuideBlock.Title(l.removePrefix("# "))
        l.startsWith("## ") -> GuideBlock.Section(l.removePrefix("## "))
        l.startsWith("- ") -> GuideBlock.Bullet(inline(l.removePrefix("- ")))
        else -> GuideBlock.Para(inline(l))
    }
}

/** Guida dell'app (assets/guida.md, aggiornata a ogni versione). */
@Composable
fun GuideScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        parse(runCatching { context.assets.open("guida.md").bufferedReader().use { it.readText() } }.getOrDefault("# Guida non disponibile"))
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 2.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Chiudi la guida", onClick = onClose)
            Text("Guida", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(blocks) { b ->
                when (b) {
                    is GuideBlock.Title -> Text(b.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    is GuideBlock.Section -> Text(
                        b.text, style = MaterialTheme.typography.titleSmall, color = VV.Tally,
                        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                    )
                    is GuideBlock.Bullet -> Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = VV.Dim)
                        Spacer(Modifier.width(10.dp))
                        Text(b.text, style = MaterialTheme.typography.bodyMedium, color = VV.Text)
                    }
                    is GuideBlock.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium, color = VV.Dim)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
