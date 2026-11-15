package dev.janken.prototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.janken.prototype.camera.CameraFeed
import dev.janken.prototype.mvi.GameEffect
import dev.janken.prototype.mvi.GameIntent
import dev.janken.prototype.mvi.InputMethod
import dev.janken.prototype.ui.JankenScreen
import dev.janken.prototype.ui.JankenTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val model: GameViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars(dark = true)
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            val dark = ui.settings.theme.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                updateSystemBars(dark)
                onDispose { }
            }
            JankenTheme(ui.settings.theme, ui.settings.language) {
                val context = LocalContext.current
                val owner = LocalLifecycleOwner.current
                val haptic = LocalHapticFeedback.current
                LaunchedEffect(model, owner) {
                    owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        model.effects.collect { effect ->
                            if (effect is GameEffect.RoundCompleted) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                }
                var granted by remember { mutableStateOf(hasCameraPermission()) }
                var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
                var permissionRequested by rememberSaveable { mutableStateOf(false) }
                var cameraAttempt by remember { mutableIntStateOf(0) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
                DisposableEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            granted = hasCameraPermission()
                            resumed = true
                            model.accept(GameIntent.Resume)
                        }
                        if (event == Lifecycle.Event.ON_PAUSE) {
                            resumed = false
                            model.accept(GameIntent.Pause)
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(Unit) {
                    if (savedInstanceState == null && BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
                        model.accept(GameIntent.SetInputMethod(InputMethod.BUTTONS))
                    }
                }
                JankenScreen(
                    state = ui,
                    permissionGranted = granted,
                    cameraActive = resumed,
                    cameraAttempt = cameraAttempt,
                    onPermission = {
                        if (permissionRequested && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        } else {
                            permissionRequested = true
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onRetry = { cameraAttempt++ },
                    onIntent = model::accept,
                    cameraContent = { modifier ->
                        CameraFeed(
                            modifier = modifier,
                            onFrame = { model.accept(GameIntent.FrameReceived(it)) },
                            onReady = { model.accept(GameIntent.CameraReady) },
                            onError = { model.accept(GameIntent.CameraFailed(it)) },
                        )
                    },
                )
            }
        }
    }

    private fun updateSystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
