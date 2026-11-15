package dev.janken.prototype.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.di.GameStoreFactory
import dev.janken.prototype.mvi.*

class CameraCallbacks(
    val onFrame: (RecognitionFrame) -> Unit,
    val onReady: () -> Unit,
    val onError: (String) -> Unit,
)

@Composable
fun CameraGameApp(
    storeFactory: GameStoreFactory,
    active: Boolean = true,
    cameraContent: @Composable (Modifier, CameraCallbacks) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = remember(storeFactory) { storeFactory.create(scope) }
    var requested by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(store) { onDispose { store.close() } }
    LaunchedEffect(active) { store.accept(if (active) GameIntent.Resume else GameIntent.Pause) }
    val state by store.state.collectAsState()
    val callbacks = remember(store) { CameraCallbacks(
        onFrame = { store.accept(GameIntent.FrameReceived(it)) },
        onReady = { store.accept(GameIntent.CameraReady) },
        onError = { store.accept(GameIntent.CameraFailed(it)) },
    ) }
    JankenTheme(state.settings.theme, state.settings.language) {
        JankenScreen(state, permissionGranted = requested, cameraActive = active, cameraAttempt = attempt,
            onPermission = { requested = true }, onRetry = { attempt++ }, onIntent = store::accept,
            cameraContent = { modifier -> cameraContent(modifier, callbacks) })
    }
}
