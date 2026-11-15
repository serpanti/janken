package dev.janken.camera

import dev.janken.prototype.game.Move
import kotlin.test.*

class CameraWireTest {
    private fun packet(label: String = "Closed_Fist", extra: String = "", points: Int = 21) = """
        {"type":"frame","label":"$label","score":0.9,"width":640,"height":480,
         "timestampMs":100,"inferenceMs":18,"points":[${List(points) { "{\"x\":0.2,\"y\":0.3}" }.joinToString(",")}]$extra}
    """.trimIndent()

    @Test fun mapsModelResultAndKeepsLandmarksInImageCoordinates() {
        val message = CameraWire.decode(packet()) as CameraMessage.Observation
        assertEquals(Move.ROCK, message.frame.move)
        assertEquals(21, message.frame.points.size)
        assertEquals(640, message.frame.width)
        assertEquals(100L, message.frame.timestamp)
    }
    @Test fun unrelatedModelCategoryNeverBecomesAMove() {
        val message = CameraWire.decode(packet("Thumb_Up")) as CameraMessage.Observation
        assertNull(message.frame.move)
        assertEquals(21, message.frame.points.size)
    }
    @Test fun customRpsModelLabelsAreMappedWithoutChangingDomainRules() {
        for ((label, move) in listOf("rock" to Move.ROCK, "paper" to Move.PAPER, "scissors" to Move.SCISSORS)) {
            assertEquals(move, (CameraWire.decode(packet(label)) as CameraMessage.Observation).frame.move)
        }
        assertNull((CameraWire.decode(packet("none")) as CameraMessage.Observation).frame.move)
    }
    @Test fun translatesProcessClockWithoutMakingOldFramesFresh() {
        val message = CameraWire.decode(packet(extra = ",\"captureAgeMs\":600"), 1_000) as CameraMessage.Observation
        assertEquals(400L, message.frame.timestamp)
    }
    @Test fun invalidDimensionsScoresAndLandmarksAreRejected() {
        assertFails { CameraWire.decode(packet().replace("\"width\":640", "\"width\":0")) }
        assertFails { CameraWire.decode(packet().replace("\"score\":0.9", "\"score\":2")) }
        assertFails { CameraWire.decode(packet().replace("\"score\":0.9", "\"score\":\"NaN\"")) }
        assertFails { CameraWire.decode(packet(points = 1)) }
        assertFails { CameraWire.decode(packet(extra = ",\"captureAgeMs\":-1"), 1_000) }
    }
    @Test fun emptyImageAndFailureDoNotFabricateAHand() {
        val observation = CameraWire.decode(packet("None", points = 0)) as CameraMessage.Observation
        assertTrue(observation.frame.points.isEmpty())
        assertNull(observation.frame.move)
        assertNull((CameraWire.decode(packet("Closed_Fist", points = 0)) as CameraMessage.Observation).frame.move)
        assertEquals(CameraMessage.Ready, CameraWire.decode("""{"type":"ready"}"""))
        assertIs<CameraMessage.Failure>(CameraWire.decode("""{"type":"error","message":"Denied"}"""))
    }
    @Test fun oversizedAndUnknownPacketsAreRejected() {
        assertFails { CameraWire.decode("x".repeat(CameraWire.MAX_PACKET_CHARS + 1)) }
        assertFails { CameraWire.decode("""{"type":"unknown"}""") }
    }
}
