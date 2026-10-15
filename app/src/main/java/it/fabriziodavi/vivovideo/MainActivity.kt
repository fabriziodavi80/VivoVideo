package it.fabriziodavi.vivovideo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.ui.Accent
import it.fabriziodavi.vivovideo.ui.Bg
import it.fabriziodavi.vivovideo.ui.EditorScreen
import it.fabriziodavi.vivovideo.ui.EditorViewModel
import it.fabriziodavi.vivovideo.ui.HomeScreen
import it.fabriziodavi.vivovideo.ui.Surface

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    background = Bg,
                    surface = Surface,
                    surfaceVariant = Surface,
                    secondaryContainer = Accent.copy(alpha = 0.25f),
                )
            ) {
                // Schermo acceso durante l'esportazione.
                LaunchedEffect(vm.exportState) {
                    if (vm.exportState is ExportState.Running) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                if (vm.project == null) HomeScreen(vm) else EditorScreen(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Mette in pausa l'anteprima quando l'app va in secondo piano.
        vm.pause()
    }
}
