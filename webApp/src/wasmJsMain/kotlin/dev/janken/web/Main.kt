package dev.janken.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.janken.data.webMatchHistory
import dev.janken.prototype.di.gameModule
import dev.janken.prototype.ui.CameraGameApp
import kotlinx.browser.document
import org.koin.dsl.koinApplication
import org.w3c.dom.Worker
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
private fun historyWorker(): Worker = js("new Worker(new URL('history-worker.js', document.baseURI), {type: 'module'})")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val dependencies = koinApplication {
        modules(gameModule(WebClock, WebSettingsStorage) { webMatchHistory(historyWorker()) })
    }
    ComposeViewport(document.body!!) {
        CameraGameApp(dependencies.koin.get(), rememberPageVisible()) { modifier, callbacks -> WebCamera(modifier, callbacks) }
    }
}
