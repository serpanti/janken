package dev.janken.desktop

import dev.janken.camera.CameraMessage
import kotlinx.coroutines.*
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess

/** An explicit diagnostic command. Never opens the camera; requires a fixture. */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
internal fun checkDistribution(reportPath: String) {
    val report = Properties()
    var exitCode = 0
    try {
        val fixture = System.getenv("JANKEN_CAMERA_TEST_IMAGE")
        require(!fixture.isNullOrBlank() && Files.isRegularFile(Path.of(fixture))) {
            "Set JANKEN_CAMERA_TEST_IMAGE to a hand image; this diagnostic never opens a webcam"
        }
        require(!System.getProperty("compose.application.resources.dir").isNullOrBlank()) {
            "Run the packaged application, not the development classpath"
        }
        runBlocking {
            val observed = CompletableDeferred<CameraMessage.Observation>()
            lateinit var worker: DesktopCameraProcess
            worker = DesktopCameraProcess(DesktopClock, onMessage = {
                when (it) {
                    is CameraMessage.Observation -> if (it.frame.move != null) { observed.complete(it); worker.close() }
                    is CameraMessage.Failure -> observed.completeExceptionally(IllegalStateException(it.message))
                    else -> Unit
                }
            }, onError = { observed.completeExceptionally(IllegalStateException(it)) })
            val job = launch(Dispatchers.IO) { worker.run() }
            try {
                val packet = withTimeout(30_000) { observed.await() }
                check(packet.frame.points.size == 21 && packet.frame.confidence >= .65f)
                Image.makeFromEncoded(Base64.decode(checkNotNull(packet.jpegBase64))).use { image ->
                    check(image.width == packet.frame.width && image.height == packet.frame.height)
                }
                report.setProperty("move", packet.frame.move.toString())
                report.setProperty("points", packet.frame.points.size.toString())
                report.setProperty("preview", "decoded")
                withTimeout(5_000) { job.join() }
            } finally { worker.close(); job.cancel() }
        }
        report.setProperty("status", "ok")
    } catch (error: Exception) {
        report.setProperty("status", "failed")
        report.setProperty("error", "${error.javaClass.simpleName}: ${error.message}")
        exitCode = 1
    }
    report.setProperty("java.home", System.getProperty("java.home"))
    report.setProperty("resources", System.getProperty("compose.application.resources.dir", ""))
    Files.newOutputStream(Path.of(reportPath)).use { report.store(it, "Janken distribution check") }
    exitProcess(exitCode)
}
