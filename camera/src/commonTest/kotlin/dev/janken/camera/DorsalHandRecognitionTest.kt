package dev.janken.camera

import dev.janken.prototype.camera.HandPoint
import dev.janken.prototype.camera.RecognitionMethod
import dev.janken.prototype.game.Move
import kotlinx.serialization.json.*
import kotlin.test.*

class DorsalHandRecognitionTest {
    private val cases = Json.parseToJsonElement(dorsalHandCases).jsonArray.map { it.jsonObject }
    private fun image(c: JsonObject) = c.getValue("points").jsonArray.map {
        val p = it.jsonObject
        HandPoint(p.getValue("x").jsonPrimitive.float, p.getValue("y").jsonPrimitive.float)
    }
    private fun expected(c: JsonObject) = Move.valueOf(c.getValue("expected").jsonPrimitive.content.uppercase())

    @Test fun realSdkDorsalHandsRecognizeAllThreeMovesAcrossFourCgiHands() {
        assertEquals(12, cases.size)
        for (c in cases) {
            val name = c.getValue("name").jsonPrimitive.content
            assertEquals(expected(c), HandShapeRecognition.classify(image(c), 300, 300), name)
            val packet = JsonObject(c + mapOf("type" to JsonPrimitive("frame"), "timestampMs" to JsonPrimitive(100)))
            val frame = (CameraWire.decode(packet.toString()) as CameraMessage.Observation).frame
            assertEquals(expected(c), frame.move, "adapter: $name")
            assertTrue(frame.confidence >= .65f, name)
        }

    }

    @Test fun projectedRecognitionHandlesMirroringImageRotationAndRectangularFrames() {
        for (c in cases) {
            val original = image(c)
            val transforms = listOf(original, original.map { HandPoint(1 - it.x, it.y) },
                original.map { HandPoint(1 - it.y, it.x) }, original.map { HandPoint(1 - it.x, 1 - it.y) })
            for (points in transforms) {
                assertEquals(expected(c), HandShapeRecognition.classify(points, 300, 300))
                for ((width, height) in listOf(640 to 360, 360 to 640)) {
                    val size = minOf(width, height) * .8f
                    val letterboxed = points.map { HandPoint(((it.x - .5f) * size + width / 2f) / width,
                        ((it.y - .5f) * size + height / 2f) / height) }
                    assertEquals(expected(c), HandShapeRecognition.classify(letterboxed, width, height))
                }
            }
        }
    }

    @Test fun visibleOutwardThumbIsNotRockIncludingSidewaysThumbs() {
        val thumbs = Json.parseToJsonElement(recordedHandCases).jsonArray.map { it.jsonObject }
            .filter { it.getValue("name").jsonPrimitive.content.startsWith("thumb_up_") }
        for (c in thumbs) {
            val width = c.getValue("width").jsonPrimitive.int
            val height = c.getValue("height").jsonPrimitive.int
            assertNull(HandShapeRecognition.classify(image(c), width, height))
            assertNull(HandShapeRecognition.predict("Thumb_Up", .99f, image(c), width, height).move)
        }
    }

    @Test fun incompleteAndNonFiniteImageCoordinatesCannotInventAMove() {
        assertNull(HandShapeRecognition.classify(List(20) { HandPoint(0f, 0f) }, 300, 300))
        assertNull(HandShapeRecognition.classify(List(21) { HandPoint(Float.NaN, .5f) }, 300, 300))
        assertNull(HandShapeRecognition.classify(List(21) { HandPoint(.5f, .5f) }, 300, 300))
        val c = cases.first()
        val prediction = HandShapeRecognition.predict("None", .99f, image(c), 300, 300)
        assertEquals(Move.ROCK, prediction.move)
        assertEquals(RecognitionMethod.HAND_SHAPE, prediction.method)
    }
}
