package dev.janken.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.janken.prototype.ui.CameraGameApp
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() { ComposeViewport(document.body!!) {
    CameraGameApp(WebClock, rememberPageVisible(), WebSettingsStorage) { modifier, callbacks -> WebCamera(modifier, callbacks) }
} }
