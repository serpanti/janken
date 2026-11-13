package dev.janken.prototype

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import dev.janken.prototype.game.Move
import dev.janken.camera.HandShapeRecognition
import dev.janken.prototype.camera.JANKEN_GESTURE_ASSET
import dev.janken.prototype.camera.jankenCannedClassifierOptions
import dev.janken.prototype.camera.HandPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureModelTest {
    @Test fun bundledModelReturnsTrainedCategoriesAndRejectsUprightThumb() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(JANKEN_GESTURE_ASSET).build())
            .setCannedGesturesClassifierOptions(jankenCannedClassifierOptions())
            .setRunningMode(RunningMode.IMAGE).setNumHands(1).build()
        GestureRecognizer.createFromOptions(instrumentation.targetContext, options).use { recognizer ->
            val fixtures = mapOf("fist.jpg" to "rock", "victory.jpg" to "scissors",
                "right_hands.jpg" to "paper", "thumb_up.jpg" to "None")
            for ((file, label) in fixtures) {
                val bitmap = instrumentation.context.assets.open(file).use { BitmapFactory.decodeStream(it) }
                BitmapImageBuilder(bitmap).build().use { image ->
                    val result = recognizer.recognize(image)
                    val top = result.gestures().firstOrNull()?.maxByOrNull { it.score() }
                    assertEquals(file, label, top?.categoryName())
                    val points = result.landmarks().firstOrNull().orEmpty().map { HandPoint(it.x(), it.y()) }
                    assertEquals(file, 21, points.size)
                    val prediction = HandShapeRecognition.predict(top?.categoryName(), top?.score() ?: 0f,
                        points, bitmap.width, bitmap.height)
                    assertEquals(file, if (label == "None") null else Move.valueOf(label.uppercase()), prediction.move)
                }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    @Test fun shapeRecognitionWorksWithRealSdkImagesAtQuarterTurns() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(JANKEN_GESTURE_ASSET).build())
            .setCannedGesturesClassifierOptions(jankenCannedClassifierOptions())
            .setRunningMode(RunningMode.IMAGE).setNumHands(1).build()
        val recognizer = GestureRecognizer.createFromOptions(instrumentation.targetContext, options)
        try {
            for ((file, expected) in mapOf("fist.jpg" to Move.ROCK, "victory.jpg" to Move.SCISSORS,
                "right_hands.jpg" to Move.PAPER, "thumb_up.jpg" to null)) {
                val original = instrumentation.context.assets.open(file).use { BitmapFactory.decodeStream(it) }
                try {
                    for (angle in listOf(0f, 90f, 180f, 270f)) {
                        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(angle) }, true)
                        // MPImage.close() owns/recycles its bitmap; keep the source
                        // alive for the remaining rotations, including identity.
                        val bitmap = if (rotated === original) original.copy(Bitmap.Config.ARGB_8888, false) else rotated
                        val image = BitmapImageBuilder(bitmap).build()
                        try {
                            val result = recognizer.recognize(image)
                            val points = result.landmarks().firstOrNull().orEmpty().map { HandPoint(it.x(), it.y()) }
                            assertEquals("$file at $angle: landmarks", 21, points.size)
                            assertEquals("$file at $angle", expected, HandShapeRecognition.classify(points, bitmap.width, bitmap.height))
                        } finally { image.close(); if (!bitmap.isRecycled) bitmap.recycle() }
                    }
                } finally { original.recycle() }
            }
        } finally { recognizer.close() }
    }

    @Test fun dorsalRockPaperAndScissorsWorkWithActualAndroidSdk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(JANKEN_GESTURE_ASSET).build())
            .setCannedGesturesClassifierOptions(jankenCannedClassifierOptions())
            .setRunningMode(RunningMode.IMAGE).setNumHands(1).build()
        val recognizer = GestureRecognizer.createFromOptions(instrumentation.targetContext, options)
        try {
            for ((label, expected) in mapOf("rock" to Move.ROCK, "paper" to Move.PAPER, "scissors" to Move.SCISSORS)) {
                for (model in 1..4) {
                    val file = "dorsal/test${label}0$model-00.png"
                    val original = instrumentation.context.assets.open(file).use { BitmapFactory.decodeStream(it) }
                    try {
                        for (angle in listOf(0f, 90f, 180f, 270f)) {
                            val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(angle) }, true)
                            val bitmap = if (rotated === original) original.copy(Bitmap.Config.ARGB_8888, false) else rotated
                            val image = BitmapImageBuilder(bitmap).build()
                            try {
                                val result = recognizer.recognize(image)
                                val category = result.gestures().firstOrNull()?.maxByOrNull { it.score() }
                                val points = result.landmarks().firstOrNull().orEmpty().map { HandPoint(it.x(), it.y()) }
                                assertEquals("$file at $angle: landmarks", 21, points.size)
                                val prediction = HandShapeRecognition.predict(category?.categoryName(), category?.score() ?: 0f,
                                    points, bitmap.width, bitmap.height)
                                assertEquals("$file at $angle", expected, prediction.move)
                            } finally { image.close(); if (!bitmap.isRecycled) bitmap.recycle() }
                        }
                    } finally { original.recycle() }
                }
            }
        } finally { recognizer.close() }
    }

    @Test fun emptyImageDoesNotProduceAPlayerMove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(JANKEN_GESTURE_ASSET).build())
            .setCannedGesturesClassifierOptions(jankenCannedClassifierOptions())
            .setRunningMode(RunningMode.IMAGE).build()
        val recognizer = GestureRecognizer.createFromOptions(context, options)
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val image = BitmapImageBuilder(bitmap).build()
        try {
            val result = recognizer.recognize(image)
            assertTrue(result.landmarks().isEmpty())
            assertTrue(result.gestures().isEmpty())
        } finally { recognizer.close(); image.close(); bitmap.recycle() }
    }
}
