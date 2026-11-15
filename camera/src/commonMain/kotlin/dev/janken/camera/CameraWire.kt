package dev.janken.camera

import dev.janken.prototype.camera.HandPoint
import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.game.Move
import kotlinx.serialization.json.*

internal object GestureLabels {
    fun toMove(label: String?): Move? = when (label?.lowercase()) {
        "closed_fist", "rock" -> Move.ROCK
        "open_palm", "paper" -> Move.PAPER
        "victory", "scissors" -> Move.SCISSORS
        else -> null
    }
}

sealed interface CameraMessage {
    data object Ready : CameraMessage
    data class Observation(val frame: RecognitionFrame, val jpegBase64: String? = null) : CameraMessage
    data class Failure(val message: String) : CameraMessage
}

object CameraWire {
    const val MAX_PACKET_CHARS = 2_000_000

    fun decode(json: String, receivedAt: Long? = null): CameraMessage {
        require(json.length <= MAX_PACKET_CHARS) { "Camera packet too large" }
        val data = Json.parseToJsonElement(json).jsonObject
        return when (data["type"]?.jsonPrimitive?.content) {
            "ready" -> CameraMessage.Ready
            "error" -> CameraMessage.Failure(data["message"]?.jsonPrimitive?.content?.take(500) ?: "Камера недоступна.")
            "frame" -> {
                val width = data.int("width").also { require(it in 1..8192) }
                val height = data.int("height").also { require(it in 1..8192) }
                val age = data["captureAgeMs"]?.jsonPrimitive?.longOrNull
                val timestamp = if (receivedAt != null && age != null) {
                    require(age >= 0 && age <= receivedAt)
                    receivedAt - age
                } else data["timestampMs"]?.jsonPrimitive?.longOrNull
                    ?.also { require(it >= 0) } ?: error("Missing frame timestamp")
                val confidence = data["score"]?.jsonPrimitive?.floatOrNull ?: 0f
                require(confidence.isFinite() && confidence in 0f..1f)
                val points = data["points"]?.jsonArray.orEmpty().also { require(it.size == 0 || it.size == 21) }.map { item ->
                    val point = item.jsonObject
                    val x = point["x"]!!.jsonPrimitive.float
                    val y = point["y"]!!.jsonPrimitive.float
                    require(x.isFinite() && y.isFinite() && x in -4f..4f && y in -4f..4f)
                    HandPoint(x, y)
                }
                val label = data["label"]?.jsonPrimitive?.contentOrNull
                data["worldPoints"]?.jsonArray.orEmpty().also { require(it.size == 0 || it.size == 21) }.forEach { item ->
                    val point = item.jsonObject
                    val x = point["x"]!!.jsonPrimitive.float
                    val y = point["y"]!!.jsonPrimitive.float
                    val z = point["z"]!!.jsonPrimitive.float
                    require(x.isFinite() && y.isFinite() && z.isFinite() && x in -10f..10f && y in -10f..10f && z in -10f..10f)
                }
                val prediction = HandShapeRecognition.predict(label, confidence, points, width, height)
                val inference = data["inferenceMs"]?.jsonPrimitive?.longOrNull ?: 0L
                require(inference >= 0)
                CameraMessage.Observation(RecognitionFrame(if (points.isEmpty()) null else prediction.move, prediction.score, points,
                    width, height, timestamp, inference, prediction.method), data["jpeg"]?.jsonPrimitive?.contentOrNull)
            }
            else -> error("Unknown camera packet")
        }
    }

    private fun JsonObject.int(key: String) = get(key)?.jsonPrimitive?.intOrNull ?: error("Missing $key")
}
