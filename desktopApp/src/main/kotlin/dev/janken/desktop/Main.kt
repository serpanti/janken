package dev.janken.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.janken.data.desktopMatchHistory
import dev.janken.prototype.di.gameModule
import dev.janken.prototype.ui.CameraGameApp
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import org.koin.dsl.koinApplication

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--check-distribution") {
        require(args.size == 2) { "Usage: Janken --check-distribution report.properties" }
        checkDistribution(args[1])
    }
    val directory = desktopDataDirectory()
    val dependencies = koinApplication {
        modules(gameModule(DesktopClock, DesktopSettingsStorage(directory)) { desktopMatchHistory(directory) })
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "Janken",
            state = rememberWindowState(width = 480.dp, height = 850.dp)) {
            var active by remember { mutableStateOf(true) }
            DisposableEffect(window) {
                val listener = object : WindowAdapter() {
                    override fun windowStateChanged(event: WindowEvent) {
                        active = event.newState and java.awt.Frame.ICONIFIED == 0
                    }
                }
                window.addWindowStateListener(listener)
                onDispose { window.removeWindowStateListener(listener) }
            }
            CameraGameApp(dependencies.koin.get(), active) { modifier, callbacks -> DesktopCamera(modifier, callbacks) }
        }
    }
}
