package dev.janken.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.janken.prototype.ui.CameraGameApp
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--check-distribution") {
        require(args.size == 2) { "Usage: Janken --check-distribution report.properties" }
        checkDistribution(args[1])
    }
    application {
    Window(onCloseRequest = ::exitApplication, title = "Janken",
        state = rememberWindowState(width = 480.dp, height = 850.dp)) {
        var active by remember { mutableStateOf(true) }
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowStateChanged(event: WindowEvent) { active = event.newState and java.awt.Frame.ICONIFIED == 0 }
            }
            window.addWindowStateListener(listener)
            onDispose { window.removeWindowStateListener(listener) }
        }
        val preferences = remember { DesktopSettingsStorage() }
        CameraGameApp(DesktopClock, active, preferences) { modifier, callbacks -> DesktopCamera(modifier, callbacks) }
    }
    }
}
