package com.triposr.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.triposr.mobile.ui.Actions
import com.triposr.mobile.ui.BottomNav
import com.triposr.mobile.ui.C
import com.triposr.mobile.ui.CreateScreen
import com.triposr.mobile.ui.DiagnosticsScreen
import com.triposr.mobile.ui.HomeScreen
import com.triposr.mobile.ui.ProcessingScreen
import com.triposr.mobile.ui.ProjectsScreen
import com.triposr.mobile.ui.ResultScreen
import com.triposr.mobile.ui.SettingsScreen
import com.triposr.mobile.ui.TripoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** Set by the widget's "create" button: open the gallery as soon as the UI is up. */
    private var pendingPick by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { TripoTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    Actions.loadBitmap(this, uri)?.let { AppState.setImage(it); AppState.navigate(Screen.Create) }
                }
            }
            CreateWidget.ACTION_CREATE -> { AppState.navigate(Screen.Create); pendingPick = AppState.input == null }
            CreateWidget.ACTION_PROJECTS -> AppState.navigateRoot(Screen.Projects)
        }
    }

    @androidx.compose.runtime.Composable
    private fun App() {
        val ctx = LocalContext.current
        val onImage: (Uri?) -> Unit = { uri ->
            if (uri != null) Actions.loadBitmap(ctx, uri)?.let { AppState.setImage(it) }
                ?: Actions.toast(ctx, "לא הצלחתי לפתוח את התמונה")
        }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onImage)
        val files = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), onImage)
        val cameraUri = remember { Actions.cameraUri(ctx) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) onImage(cameraUri)
        }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            AppState.update = runCatching { withContext(Dispatchers.IO) { Updater.check() } }
                .onFailure { CrashLog.log("Update check failed: ${it.message}") }.getOrNull()
        }
        LaunchedEffect(pendingPick) {
            if (pendingPick) {
                pendingPick = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        val screen = AppState.screen
        BackHandler(enabled = AppState.backStack.size > 1) { AppState.back() }

        Column(Modifier.fillMaxSize().background(C.Bg).systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.Home -> HomeScreen()
                    Screen.Create -> CreateScreen(
                        onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onCamera = { camera.launch(cameraUri) },
                        onFiles = { files.launch("image/*") },
                    )
                    Screen.Processing -> ProcessingScreen()
                    Screen.Result -> ResultScreen()
                    Screen.Projects -> ProjectsScreen()
                    Screen.Settings -> SettingsScreen()
                    Screen.Diagnostics -> DiagnosticsScreen()
                }
            }
            if (screen in listOf(Screen.Home, Screen.Projects, Screen.Settings, Screen.Diagnostics)) BottomNav(screen)
        }
    }
}
