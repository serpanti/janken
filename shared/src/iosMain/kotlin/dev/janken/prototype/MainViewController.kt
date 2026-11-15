@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.janken.prototype

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import dev.janken.camera.CameraMessage
import dev.janken.camera.CameraWire
import dev.janken.data.iosMatchHistory
import dev.janken.prototype.di.gameModule
import dev.janken.prototype.mvi.GameClock
import dev.janken.prototype.mvi.SettingsStorage
import dev.janken.prototype.mvi.AppSettings
import dev.janken.prototype.ui.CameraCallbacks
import dev.janken.prototype.ui.CameraGameApp
import org.koin.dsl.koinApplication
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController

/** Swift owns AVFoundation/MediaPipe; Kotlin owns UI, lifecycle policy and the MVI store. */
interface NativeCameraController {
    val viewController: UIViewController
    fun stop()
}

class IosCameraSink internal constructor(private val callbacks: CameraCallbacks) {
    private var closed = false
    fun ready() { if (!closed) callbacks.onReady() }
    fun failure(message: String) { if (!closed) callbacks.onError(message) }
    fun result(json: String) {
        if (closed) return
        runCatching {
            when (val message = CameraWire.decode(json)) {
                is CameraMessage.Observation -> callbacks.onFrame(message.frame)
                is CameraMessage.Failure -> callbacks.onError(message.message)
                CameraMessage.Ready -> callbacks.onReady()
            }
        }.onFailure { callbacks.onError("Ошибка данных распознавания. Повторите запуск камеры.") }
    }
    internal fun close() { closed = true }
}

private object IosClock : GameClock {
    override fun nowMillis() = (NSProcessInfo.processInfo.systemUptime * 1_000).toLong()
}

private val iosDependencies by lazy {
    val preferences = object : SettingsStorage {
        override fun load() = AppSettings.decode(NSUserDefaults.standardUserDefaults.stringForKey("dev.janken.settings.v1"))
        override fun save(settings: AppSettings) {
            NSUserDefaults.standardUserDefaults.setObject(settings.encode(), forKey = "dev.janken.settings.v1")
        }
    }
    koinApplication { modules(gameModule(IosClock, preferences, ::iosMatchHistory)) }
}

fun MainViewController(cameraFactory: (IosCameraSink) -> NativeCameraController): UIViewController = ComposeUIViewController {
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { active = false }
        val foreground = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { active = true }
        onDispose { center.removeObserver(background); center.removeObserver(foreground) }
    }
    CameraGameApp(iosDependencies.koin.get(), active) { modifier, callbacks ->
        val sink = remember(callbacks) { IosCameraSink(callbacks) }
        val native = remember(sink) { cameraFactory(sink) }
        DisposableEffect(native) { onDispose { sink.close(); native.stop() } }
        UIKitViewController(factory = { native.viewController }, modifier = modifier)
    }
}
