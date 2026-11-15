@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.janken.desktop

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
import kotlinx.coroutines.*
import org.jetbrains.skia.Image as SkiaImage
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64
import kotlin.io.path.*

object DesktopClock : GameClock { override fun nowMillis() = System.nanoTime() / 1_000_000L }

@Composable
fun DesktopCamera(modifier: Modifier, callbacks: CameraCallbacks) {
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val latest by rememberUpdatedState(callbacks)
    DisposableEffect(Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val worker = DesktopCameraProcess(DesktopClock,
            onMessage = { message ->
                when (message) {
                    CameraMessage.Ready -> latest.onReady()
                    is CameraMessage.Failure -> latest.onError(message.message)
                    is CameraMessage.Observation -> {
                        val bitmap = message.jpegBase64?.let { encoded ->
                            SkiaImage.makeFromEncoded(Base64.decode(encoded)).toComposeImageBitmap()
                        }
                        withContext(Dispatchers.Main) {
                            preview = bitmap
                            latest.onFrame(message.frame)
                        }
                    }
                }
            }, onError = { latest.onError(it) })
        scope.launch { worker.run() }
        onDispose { worker.close(); scope.cancel() }
    }
    preview?.let { Image(it, LocalAppStrings.current.cameraPreview, modifier, contentScale = ContentScale.Crop) }
}

/** A local child process, not a remote API. close() stops capture on pause/disposal. */
class DesktopCameraProcess(
    private val clock: GameClock,
    private val onMessage: suspend (CameraMessage) -> Unit,
    private val onError: (String) -> Unit,
    private val overrideCommand: List<String>? = null,
) {
    private val closed = AtomicBoolean(false)
    @Volatile private var process: Process? = null

    suspend fun run() {
        try {
            val command = overrideCommand ?: resolveCommand()
            if (closed.get()) return
            val child = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).apply {
                environment()["PYTHONIOENCODING"] = "utf-8"
            }.start()
            process = child
            if (closed.get()) { child.destroyForcibly(); return }
            var failed = false
            InputStreamReader(child.inputStream, Charsets.UTF_8).use { input ->
                val buffer = StringBuilder()
                val chars = CharArray(8192)
                while (!closed.get()) {
                    val size = input.read(chars)
                    if (size < 0) break
                    for (index in 0 until size) {
                        val char = chars[index]
                        if (char == '\n') {
                            if (buffer.isNotEmpty()) {
                                val message = CameraWire.decode(buffer.toString(), clock.nowMillis())
                                if (!closed.get()) onMessage(message)
                                if (message is CameraMessage.Failure) failed = true
                            }
                            buffer.setLength(0)
                        } else if (char != '\r') {
                            check(buffer.length < CameraWire.MAX_PACKET_CHARS) { "Oversized camera packet" }
                            buffer.append(char)
                        }
                    }
                }
            }
            if (!closed.get() && !failed) onError("Поток камеры завершился. Нажмите «Повторить».")
        } catch (error: Exception) {
            if (!closed.get()) {
                System.err.println("Camera process: ${error.javaClass.simpleName}: ${error.message}")
                onError("Не удалось запустить компонент камеры. Проверьте целостность приложения и повторите.")
            }
        } finally { close() }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val child = process ?: return
        runCatching { child.outputStream.write("stop\n".toByteArray()); child.outputStream.flush(); child.outputStream.close() }
        // Never block the Compose UI while a native driver releases its device.
        Thread({
            if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly()
        }, "janken-camera-close").apply { isDaemon = true; start() }
    }

    private fun resolveCommand(): List<String> {
        val windows = System.getProperty("os.name").startsWith("Windows")
        val resources = System.getProperty("compose.application.resources.dir")?.let { Path.of(it) }
        val bundled = resources?.resolve("camera")
        val executable = bundled?.resolve(if (windows) "camera.exe" else "camera")
        val packagedModel = bundled?.resolve("gesture_recognizer.task")
        if (resources != null) {
            // A distribution must never silently depend on Python from PATH or
            // pick up a developer's virtualenv. Incomplete packages fail clearly.
            check(executable?.isRegularFile() == true && packagedModel?.isRegularFile() == true) {
                "Missing bundled camera runtime in $resources"
            }
            return cameraArguments(listOf(executable.toString(), "--model", packagedModel.toString()))
        }
        val binary = if (windows) ".camera-venv/Scripts/python.exe" else ".camera-venv/bin/python"
        val starts = listOf(Path.of("").toAbsolutePath(), Path.of(javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath())
        val localPython = starts.asSequence().flatMap { generateSequence(it) { directory -> directory.parent }.take(8) }
            .map { it.resolve(binary) }.firstOrNull { it.isRegularFile() }
        val python = System.getenv("JANKEN_PYTHON")?.takeIf { it.isNotBlank() }
            ?: localPython?.toString()
            ?: if (windows) "python" else "python3"
        val (worker, model) = CameraAssets.paths
        return cameraArguments(listOf(python, "-u", worker.toString(), "--model", model.toString()))
    }

    private fun cameraArguments(command: List<String>) = buildList {
            addAll(command)
            System.getenv("JANKEN_CAMERA_SOURCE")?.let { add("--source"); add(it) }
            System.getenv("JANKEN_CAMERA_TEST_IMAGE")?.let { add("--image"); add(it) }
    }

}

private object CameraAssets {
    val paths: Pair<Path, Path> by lazy {
        val directory = Files.createTempDirectory("janken-camera-")
        directory.toFile().deleteOnExit()
        extract("camera_worker.py", directory) to extract("gesture_recognizer.task", directory)
    }

    private fun extract(name: String, directory: Path): Path {
        val file = directory.resolve(name)
        CameraAssets::class.java.getResourceAsStream("/desktop-camera/$name").use { stream ->
            checkNotNull(stream) { "Missing camera resource: $name" }
            Files.copy(stream, file)
        }
        file.toFile().deleteOnExit()
        return file
    }
}
