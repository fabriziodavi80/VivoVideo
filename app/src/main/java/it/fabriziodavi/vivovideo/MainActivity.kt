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
import it.fabriziodavi.vivovideo.ui.AutoScreen
import it.fabriziodavi.vivovideo.ui.LibraryScreen
import it.fabriziodavi.vivovideo.ui.AlbumPickerScreen
import androidx.compose.foundation.layout.fillMaxSize
import it.fabriziodavi.vivovideo.ui.GuideScreen
import it.fabriziodavi.vivovideo.ui.EditorViewModel
import it.fabriziodavi.vivovideo.ui.HomeScreen
import it.fabriziodavi.vivovideo.ui.VivoTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Safety.install(this)
        super.onCreate(savedInstanceState)
        // Se l'app era caduta, lo si dice (con il dettaglio) e si spengono le funzioni sperimentali coinvolte.
        if (savedInstanceState == null) Safety.checkAfterStart(this)?.let { vm.message = it }
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
                // In orizzontale nell'editor le barre di sistema si nascondono (riappaiono scorrendo dal bordo).
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val immersive = vm.project != null && (landscape || vm.fullscreen)
                LaunchedEffect(immersive) {
                    val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (immersive) c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
                // La libreria si apre sopra alla schermata da cui la si chiama (che resta com'era).
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    when {
                        vm.showGuide -> GuideScreen { vm.showGuide = false }
                        vm.project == null && vm.showAuto -> AutoScreen(vm)
                        vm.project == null -> HomeScreen(vm)
                        else -> EditorScreen(vm)
                    }
                    if (vm.library != null) LibraryScreen(vm)
                    if (vm.albumPick != null) AlbumPickerScreen(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStart()
    }

    override fun onStop() {
        super.onStop()
        // Mette in pausa l'anteprima quando l'app va in secondo piano.
        vm.pause()
        vm.onAppStop()
    }
}
