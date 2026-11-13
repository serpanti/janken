@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.janken.web

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.janken.camera.CameraMessage
import dev.janken.camera.CameraWire
import dev.janken.prototype.mvi.GameClock
import dev.janken.prototype.ui.CameraCallbacks
import dev.janken.prototype.ui.LocalAppStrings
import org.jetbrains.skia.Image as SkiaImage
import kotlin.io.encoding.Base64
import kotlin.js.JsAny

object WebClock : GameClock { override fun nowMillis() = performanceNow().toLong() }

private fun performanceNow(): Double = js("performance.now()")

private external interface CameraSession : JsAny { fun stop() }

private fun startCamera(onReady: () -> Unit, onObservation: (String) -> Unit,
    onPreview: (String) -> Unit, onError: (String) -> Unit): CameraSession =
    js("globalThis.JankenCamera.start({onReady,onObservation,onPreview,onError})")

private fun pageVisible(): Boolean = js("document.visibilityState !== 'hidden'")

private fun watchVisibility(callback: (Boolean) -> Unit): CameraSession =
    js("{ const listener=()=>callback(document.visibilityState!=='hidden'); document.addEventListener('visibilitychange',listener); return {stop:()=>document.removeEventListener('visibilitychange',listener)}; }")

@Composable
fun rememberPageVisible(): Boolean {
    var active by remember { mutableStateOf(pageVisible()) }
    DisposableEffect(Unit) {
        val session = watchVisibility { active = it }
        onDispose { session.stop() }
    }
    return active
}

@Composable
fun WebCamera(modifier: Modifier, callbacks: CameraCallbacks) {
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val latest by rememberUpdatedState(callbacks)
    DisposableEffect(Unit) {
        var closed = false
        val session = startCamera(
            onReady = { if (!closed) latest.onReady() },
            onObservation = { json ->
                if (!closed) runCatching {
                    when (val packet = CameraWire.decode(json)) {
                        is CameraMessage.Observation -> latest.onFrame(packet.frame)
                        is CameraMessage.Failure -> latest.onError(packet.message)
                        CameraMessage.Ready -> latest.onReady()
                    }
                }.onFailure { latest.onError("Ошибка данных распознавания. Повторите запуск камеры.") }
            },
            onPreview = { encoded ->
                if (!closed) runCatching { SkiaImage.makeFromEncoded(Base64.decode(encoded)).toComposeImageBitmap() }
                    .onSuccess { preview = it }
            },
            onError = { if (!closed) latest.onError(it) },
        )
        onDispose { closed = true; session.stop() }
    }
    preview?.let { Image(it, LocalAppStrings.current.cameraPreview, modifier, contentScale = ContentScale.Crop) }
}
