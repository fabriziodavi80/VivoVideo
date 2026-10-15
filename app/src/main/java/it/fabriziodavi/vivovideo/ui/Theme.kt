package it.fabriziodavi.vivovideo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.fabriziodavi.vivovideo.R

/*
 * Grafite neutra perché a colori ci sia solo il video; un unico accento ambra,
 * come la luce "tally" delle telecamere, per ciò che è attivo: indicatore, selezione, Esporta.
 */
object VV {
    val Canvas = Color(0xFF121418)
    val Panel = Color(0xFF181B21)
    val Raised = Color(0xFF22262E)
    val Raised2 = Color(0xFF343944)
    val Line = Color(0xFF2C313A)
    val Text = Color(0xFFE6E8EC)
    val Dim = Color(0xFF8B92A0)
    val Faint = Color(0xFF4A505B)
    val Tally = Color(0xFFF2B33D)
    val TallyInk = Color(0xFF1A1300)
    val Wave = Color(0xFF5D8A8F)
    val WaveBg = Color(0xFF1F2A2C)
    val Danger = Color(0xFFE5484D)
}

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
)

private val base = TextStyle(fontFamily = Plex, color = VV.Text)

val VVTypography = Typography(
    titleLarge = base.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    titleSmall = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 15.sp),
    bodyMedium = base.copy(fontSize = 14.sp),
    bodySmall = base.copy(fontSize = 12.sp, color = VV.Dim),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = base.copy(fontSize = 11.sp),
)

@Composable
fun VivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VV.Tally,
            onPrimary = VV.TallyInk,
            background = VV.Canvas,
            onBackground = VV.Text,
            surface = VV.Panel,
            onSurface = VV.Text,
            surfaceVariant = VV.Raised,
            onSurfaceVariant = VV.Dim,
            surfaceContainerHigh = VV.Raised,
            outline = VV.Line,
            secondaryContainer = VV.Raised2,
            onSecondaryContainer = VV.Text,
            error = VV.Danger,
        ),
        typography = VVTypography,
        content = content,
    )
}
