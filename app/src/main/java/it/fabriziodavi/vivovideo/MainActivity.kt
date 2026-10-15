package it.fabriziodavi.vivovideo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import it.fabriziodavi.vivovideo.engine.ExportState
import it.fabriziodavi.vivovideo.ui.EditorScreen
import it.fabriziodavi.vivovideo.ui.EditorViewModel
import it.fabriziodavi.vivovideo.ui.HomeScreen
import it.fabriziodavi.vivovideo.ui.VivoTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barre di sistema sempre chiare su fondo scuro, anche se il telefono è in tema chiaro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            VivoTheme {
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
