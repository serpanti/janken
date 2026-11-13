package dev.janken.camera

import dev.janken.prototype.camera.HandPoint
import dev.janken.prototype.camera.RecognitionMethod
import dev.janken.prototype.game.Move
import kotlinx.serialization.json.*
import kotlin.test.*

class HandShapeRecognitionTest {
    private val cases = Json.parseToJsonElement(recordedHandCases).jsonArray.map { it.jsonObject }
    private fun image(case: JsonObject) = case.getValue("points").jsonArray.map {
        val p = it.jsonObject
        HandPoint(p.getValue("x").jsonPrimitive.float, p.getValue("y").jsonPrimitive.float)
    }
    @Test fun actualSdkLandmarksRecognizeAllThreeShapesAcrossImageRotations() {
        assertEquals(16, cases.size)
        for (case in cases) {
            val name = case.getValue("name").jsonPrimitive.content
            val expected = when {
                name.startsWith("fist_") -> Move.ROCK
                name.startsWith("victory_") -> Move.SCISSORS
                name.startsWith("right_hands_") -> Move.PAPER
                else -> null // Thumb up/down, including sideways, must not become rock.
            }
            assertEquals(expected, HandShapeRecognition.classify(image(case),
                case.getValue("width").jsonPrimitive.int, case.getValue("height").jsonPrimitive.int), name)
        }
    }

    @Test fun sidewaysScissorsUseImageShapeWhenCannedClassifierReturnsNone() {
        val sideways = cases.first { it.getValue("name").jsonPrimitive.content == "victory_90" }
        assertEquals("None", sideways.getValue("label").jsonPrimitive.content)
        val prediction = HandShapeRecognition.predict(
            "None", sideways.getValue("score").jsonPrimitive.float, image(sideways),
            sideways.getValue("width").jsonPrimitive.int, sideways.getValue("height").jsonPrimitive.int,
        )
        assertEquals(Move.SCISSORS, prediction.move)
        assertEquals(RecognitionMethod.HAND_SHAPE, prediction.method)
    }

    @Test fun modelFallbackRequiresAGameGestureAndKeepsItsScore() {
        val ambiguous = List(21) { HandPoint(.5f, .5f) }
        val fallback = HandShapeRecognition.predict("scissors", .81f, ambiguous, 640, 480)
        assertEquals(Move.SCISSORS, fallback.move)
        assertEquals(.81f, fallback.score)
        assertEquals(RecognitionMethod.MODEL, fallback.method)
        assertNull(HandShapeRecognition.predict("None", .99f, ambiguous, 640, 480).move)
    }

}
