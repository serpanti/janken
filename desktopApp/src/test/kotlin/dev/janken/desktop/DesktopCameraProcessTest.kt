package dev.janken.desktop

import dev.janken.camera.CameraMessage
import dev.janken.prototype.mvi.GameClock
import kotlinx.coroutines.*
import java.nio.file.Files
import kotlin.test.*
import kotlin.io.encoding.Base64
import org.jetbrains.skia.Image

class DesktopCameraProcessTest {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test fun packagedWorkerRecognizesAndDecodesARealModelFrame() = runBlocking {
        val observed = CompletableDeferred<CameraMessage.Observation>()
        lateinit var process: DesktopCameraProcess
        process = DesktopCameraProcess(DesktopClock, onMessage = {
            if (it is CameraMessage.Observation && it.frame.move != null) { observed.complete(it); process.close() }
        }, onError = { observed.completeExceptionally(IllegalStateException(it)) })
        val job = launch(Dispatchers.IO) { process.run() }
        try {
            val packet = withTimeout(25_000) { observed.await() }
            assertEquals(dev.janken.prototype.game.Move.ROCK, packet.frame.move)
            assertTrue(packet.frame.confidence >= .65f)
            assertEquals(21, packet.frame.points.size)
            Image.makeFromEncoded(Base64.decode(packet.jpegBase64!!)).use { image ->
                assertEquals(packet.frame.width, image.width)
                assertEquals(packet.frame.height, image.height)
            }
            withTimeout(5_000) { job.join() }
        } finally { process.close(); job.cancel() }
    }

    private fun command(script: String): List<String> {
        val file = Files.createTempFile("janken-camera-test", ".py")
        Files.writeString(file, script)
        file.toFile().deleteOnExit()
        val python = System.getenv("JANKEN_TEST_PYTHON") ?: if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
        return listOf(python, "-u", file.toString())
    }

    @Test fun translatesPacketsAndStopsChildWithoutBlockingUi() = runBlocking {
        val observed = CompletableDeferred<CameraMessage.Observation>()
        val errors = mutableListOf<String>()
        lateinit var process: DesktopCameraProcess
        process = DesktopCameraProcess(GameClock { 1_000 }, onMessage = {
            if (it is CameraMessage.Observation) { observed.complete(it); process.close() }
        }, onError = { synchronized(errors) { errors += it } }, overrideCommand = command("""
            import json,sys
            print(json.dumps({'type':'ready'}),flush=True)
            print(json.dumps({'type':'frame','label':'Victory','score':0.9,'width':640,'height':480,'timestampMs':99,'captureAgeMs':20,'points':[]}),flush=True)
            sys.stdin.readline()
        """.trimIndent()))
        val job = launch(Dispatchers.IO) { process.run() }
        try {
            val packet = withTimeout(10_000) { observed.await() }
            assertEquals(980L, packet.frame.timestamp)
            withTimeout(5_000) { job.join() }
            assertTrue(errors.isEmpty())
        } finally { process.close(); job.cancel() }
    }

    @Test fun disposalIgnoresFramesAlreadyBufferedInThePipe() = runBlocking {
        var frames = 0
        lateinit var process: DesktopCameraProcess
        process = DesktopCameraProcess(GameClock { 1_000 }, onMessage = {
            if (it == CameraMessage.Ready) process.close()
            if (it is CameraMessage.Observation) frames++
        }, onError = {}, overrideCommand = command("""
            import json
            print(json.dumps({'type':'ready'}),flush=True)
            print(json.dumps({'type':'frame','width':640,'height':480,'timestampMs':100,'score':0,'points':[]}),flush=True)
        """.trimIndent()))
        withTimeout(10_000) { withContext(Dispatchers.IO) { process.run() } }
        assertEquals(0, frames)
    }
}
