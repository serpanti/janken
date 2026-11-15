package dev.janken.camera

import dev.janken.prototype.camera.HandPoint
import dev.janken.prototype.camera.RecognitionMethod
import dev.janken.prototype.game.MIN_GESTURE_CONFIDENCE
import dev.janken.prototype.game.Move
import kotlin.math.sqrt

data class GesturePrediction(val move: Move?, val score: Float, val method: RecognitionMethod)

object HandShapeRecognition {
    private enum class Finger { EXTENDED, CURLED, UNCERTAIN }

    private val fingerBases = listOf(5, 9, 13, 17)

    private data class Point(val x: Float, val y: Float) {
        operator fun minus(other: Point) = Point(x - other.x, y - other.y)
        fun dot(other: Point) = x * other.x + y * other.y
        fun length() = sqrt(dot(this))
    }

    fun predict(
        label: String?,
        modelScore: Float,
        imagePoints: List<HandPoint>,
        width: Int,
        height: Int,
    ): GesturePrediction {
        val modelMove = GestureLabels.toMove(label)
        val shape = classify(imagePoints, width, height)
        val modelConfirmsShape = shape == modelMove && modelScore >= MIN_GESTURE_CONFIDENCE
        if (shape != null && !modelConfirmsShape) {
            return GesturePrediction(shape, 1f, RecognitionMethod.HAND_SHAPE)
        }
        return GesturePrediction(modelMove, modelScore, RecognitionMethod.MODEL)
    }

    /** Convert to pixels before measuring so the frame aspect ratio is respected. */
    fun classify(image: List<HandPoint>, width: Int, height: Int): Move? {
        if (image.size != 21 || width <= 0 || height <= 0 || image.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val points = image.map { Point(it.x * width, it.y * height) }
        val wrist = points[0]
        val palmWidth = (points[5] - points[17]).length()
        val knuckleCenter = Point(
            (points[5].x + points[9].x + points[13].x + points[17].x) / 4,
            (points[5].y + points[9].y + points[13].y + points[17].y) / 4,
        )
        val palmAxis = knuckleCenter - wrist
        val palmLength = palmAxis.length()
        if (palmWidth < 1e-6f || palmLength < 1e-6f || palmLength / palmWidth !in .65f..3f) return null
        val forward = Point(palmAxis.x / palmLength, palmAxis.y / palmLength)

        val fingers = fingerBases.map { base ->
            val middleJoint = points[base + 1]
            val tip = points[base + 3]
            val jointDistance = (middleJoint - wrist).length()
            if (jointDistance < 1e-6f) return null
            val reach = (tip - middleJoint).dot(forward) / palmWidth
            val baseReach = (tip - points[base]).dot(forward) / palmWidth
            val wristRatio = (tip - wrist).length() / jointDistance
            when {
                reach >= .14f && baseReach >= .45f && wristRatio >= 1.08f -> Finger.EXTENDED
                reach <= -.12f && wristRatio <= .98f -> Finger.CURLED
                else -> Finger.UNCERTAIN
            }
        }
        // A fist keeps its thumb near the index knuckle.
        val thumbTucked = (points[4] - points[5]).length() / palmWidth <= .8f
        return when {
            fingers.all { it == Finger.EXTENDED } -> Move.PAPER
            fingers[0] == Finger.EXTENDED && fingers[1] == Finger.EXTENDED &&
                fingers[2] == Finger.CURLED && fingers[3] == Finger.CURLED -> Move.SCISSORS
            thumbTucked && fingers.all { it == Finger.CURLED } -> Move.ROCK
            else -> null
        }
    }
}
