package it.fabriziodavi.vivovideo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.media.Gallery
import it.fabriziodavi.vivovideo.media.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Griglia della galleria per scegliere foto e video uno per uno (il numero sul riquadro è l'ordine di scelta).
 * [items] nell'ordine in cui mostrarli (di solito dal più recente).
 */
@Composable
internal fun MediaGrid(items: List<GalleryItem>, picked: List<String>, onToggle: (GalleryItem) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            val n = picked.indexOf(item.uri.toString())
            MediaCell(item, if (n >= 0) n + 1 else 0) { onToggle(item) }
        }
    }
}

@Composable
private fun MediaCell(item: GalleryItem, number: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val thumb by produceState<Bitmap?>(null, item.uri) { value = withContext(Dispatchers.IO) { Gallery.thumbnail(ctx, item.uri, 220) } }
    val on = number > 0
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(VV.Raised)
            .border(if (on) 3.dp else 0.dp, if (on) VV.Tally else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (on) Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        if (item.isVideo) {
            Text(
                formatTime(item.durationMs * 1000),
                style = MaterialTheme.typography.labelSmall, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape)
                .background(if (on) VV.Tally else Color(0x55000000))
                .border(1.5.dp, if (on) VV.Tally else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Text("$number", color = VV.TallyInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
