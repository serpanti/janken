package dev.janken.prototype.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import dev.janken.prototype.game.Move
import dev.janken.camera.HandShapeRecognition
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CameraFeed(
    modifier: Modifier = Modifier,
    onFrame: (RecognitionFrame) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
    DisposableEffect(previewView, owner) {
        val session = CameraSession(context, owner, previewView, onFrame, onReady, onError)
        previewView.post { session.start() }
        onDispose { session.close() }
    }
}

private class CameraSession(
    private val context: Context,
    private val owner: androidx.lifecycle.LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrame: (RecognitionFrame) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private val closed = AtomicBoolean(false)
    private val inFlight = AtomicBoolean(false)
    private val pending = AtomicReference<Packet?>(null)
    private var recognizer: GestureRecognizer? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var lastFrameAt = 0L

    fun start() {
        if (closed.get()) return
        worker.execute {
            if (closed.get()) return@execute
            try {
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(JANKEN_GESTURE_ASSET).setDelegate(Delegate.CPU).build())
                    .setCannedGesturesClassifierOptions(jankenCannedClassifierOptions())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.55f)
                    .setMinHandPresenceConfidence(0.55f)
                    .setMinTrackingConfidence(0.55f)
                    .setResultListener { result, _ ->
                        val packet = pending.getAndSet(null)
                        try {
                            if (!closed.get() && packet != null) {
                                val category = result.gestures().firstOrNull()?.maxByOrNull { it.score() }
                                val points = result.landmarks().firstOrNull().orEmpty().map { HandPoint(it.x(), it.y()) }
                                val prediction = HandShapeRecognition.predict(category?.categoryName(), category?.score() ?: 0f,
                                    points, packet.bitmap.width, packet.bitmap.height)
                                onFrame(RecognitionFrame(
                                    move = if (points.isEmpty()) null else prediction.move,
                                    confidence = prediction.score,
                                    points = points,
                                    width = packet.bitmap.width,
                                    height = packet.bitmap.height,
                                    timestamp = packet.timestamp,
                                    inferenceMs = SystemClock.uptimeMillis() - packet.timestamp,
                                    method = prediction.method,
                                ))
                            }
                        } finally {
                            packet?.release()
                            inFlight.set(false)
                        }
                    }
                    .setErrorListener {
                        pending.getAndSet(null)?.release()
                        inFlight.set(false)
                        fail("Ошибка распознавания. Перезапустите камеру.")
                    }
                    .build()
                recognizer = GestureRecognizer.createFromOptions(context, options)
                main.execute { if (!closed.get()) bindCamera() }
            } catch (_: Throwable) {
                fail("Не удалось загрузить модель. Попробуйте перезапустить камеру.")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (closed.get()) return@addListener
            try {
                provider = future.get()
                val cameraProvider = provider ?: return@addListener
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> { fail("Камера не найдена. Можно попробовать режим без камеры."); return@addListener }
                }
                val mirror = selector == CameraSelector.DEFAULT_FRONT_CAMERA
                preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(worker) { image -> analyze(image, mirror) } }
                val group = UseCaseGroup.Builder().addUseCase(preview!!).addUseCase(analysis!!)
                previewView.viewPort?.let { group.setViewPort(it) }
                cameraProvider.bindToLifecycle(owner, selector, group.build())
                onReady()
            } catch (_: Exception) {
                fail("Камера недоступна. Закройте другие приложения с камерой и повторите.")
            }
        }, main)
    }

    private fun analyze(image: ImageProxy, mirror: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (closed.get() || recognizer == null || now - lastFrameAt < 75 || !inFlight.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastFrameAt = now
        var raw: Bitmap? = null
        var transformed: Bitmap? = null
        try {
            val rotation = image.imageInfo.rotationDegrees
            val crop = android.graphics.Rect(image.cropRect)
            raw = image.toBitmap()
            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                if (mirror) postScale(-1f, 1f)
            }
            transformed = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width(), crop.height(), matrix, true)
            image.close()
            if (transformed !== raw) raw.recycle()
            raw = null
            val mpImage = BitmapImageBuilder(transformed).build()
            pending.set(Packet(mpImage, transformed, now))
            recognizer?.recognizeAsync(mpImage, now)
        } catch (_: Exception) {
            image.close()
            val packet = pending.getAndSet(null)
            if (packet != null) packet.release()
            else {
                transformed?.takeIf { !it.isRecycled }?.recycle()
                raw?.takeIf { !it.isRecycled }?.recycle()
            }
            inFlight.set(false)
            fail("Не удалось обработать кадр. Попробуйте перезапустить камеру.")
        }
    }

    private fun fail(message: String) { if (!closed.get()) main.execute { if (!closed.get()) onError(message) } }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        analysis?.clearAnalyzer()
        val useCases = listOfNotNull(preview, analysis).toTypedArray()
        if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        worker.execute {
            recognizer?.close()
            recognizer = null
            pending.getAndSet(null)?.release()
        }
        worker.shutdown()
    }

    private data class Packet(val image: MPImage, val bitmap: Bitmap, val timestamp: Long) {
        fun release() {
            image.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
