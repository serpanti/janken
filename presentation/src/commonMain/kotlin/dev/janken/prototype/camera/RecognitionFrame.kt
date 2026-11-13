package dev.janken.prototype.camera

import dev.janken.prototype.game.Move

data class HandPoint(val x: Float, val y: Float)
enum class RecognitionMethod { MODEL, HAND_SHAPE }
data class RecognitionFrame(
    val move: Move? = null,
    val confidence: Float = 0f,
    val points: List<HandPoint> = emptyList(),
    val width: Int = 1,
    val height: Int = 1,
    val timestamp: Long = 0,
    val inferenceMs: Long = 0,
    val method: RecognitionMethod = RecognitionMethod.MODEL,
)

val handConnections = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20,
)
