package it.fabriziodavi.vivovideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Scelta dell'oggetto da oscurare: si tocca nel fotogramma (gli oggetti trovati sono già riquadrati). */
@Composable
fun ObjectPickScreen(vm: EditorViewModel) {
    val bmp = vm.objectPick ?: return
    BackHandler { vm.closeObjectPick() }
    Column(Modifier.fillMaxSize().background(VV.Canvas).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(VVIcons.Back, "Indietro") { vm.closeObjectPick() }
            Text("Tocca l'oggetto da oscurare", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height, matchHeightConstraintsFirst = bmp.height > bmp.width)) {
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { o -> vm.pickObjectAt(o.x / size.width, o.y / size.height) }
                }) {
                    vm.objectPickBoxes.forEach { b ->
                        drawRoundRect(Color(0x99FFFFFF), Offset(b.x * size.width, b.y * size.height), Size(b.w * size.width, b.h * size.height),
                            CornerRadius(8f, 8f), style = Stroke(width = 3f))
                    }
                    vm.redactSeed?.let { b ->
                        drawRoundRect(Color(0x55F2B33D), Offset(b.x * size.width, b.y * size.height), Size(b.w * size.width, b.h * size.height), CornerRadius(10f, 10f))
                        drawRoundRect(VV.Tally, Offset(b.x * size.width, b.y * size.height), Size(b.w * size.width, b.h * size.height),
                            CornerRadius(10f, 10f), style = Stroke(width = 6f))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint(if (vm.objectPickBoxes.isEmpty()) "Non ho riconosciuto oggetti qui: tocca il punto, l'app lo copre con un riquadro e prova a seguirlo."
                else "I riquadri bianchi sono gli oggetti che l'app sa seguire: tocca quello da oscurare (oppure un altro punto).")
            PrimaryButton("Usa questo oggetto", Modifier.fillMaxWidth(), enabled = vm.redactSeed != null) { vm.closeObjectPick() }
        }
    }
}
