package dev.janken.prototype.game

import kotlin.test.*

class GestureStabilizerTest {
    private var hold = GestureHold()

    private fun observe(move: Move?, score: Float, timestamp: Long): StableGesture {
        val observation = observeGesture(hold, move, score, timestamp)
        hold = observation.hold
        return observation.stable
    }

    @Test fun duplicateFrameIsNotAnAdditionalObservation() {
        observe(Move.ROCK, .9f, 0)
        observe(Move.ROCK, .9f, 200)
        assertFalse(observe(Move.ROCK, .9f, 200).ready)
        assertFalse(observe(Move.ROCK, .9f, 200).ready)
        assertTrue(observe(Move.ROCK, .9f, 400).ready)
    }
    @Test fun `a confident continuous hold becomes ready`() {
        assertFalse(observe(Move.ROCK, .9f, 0).ready)
        observe(Move.ROCK, .9f, 120)
        observe(Move.ROCK, .9f, 240)
        assertTrue(observe(Move.ROCK, .9f, 360).ready)
    }

    @Test fun `changing gesture restarts the hold`() {
        observe(Move.ROCK, .9f, 0)
        observe(Move.ROCK, .9f, 200)
        assertEquals(0f, observe(Move.PAPER, .9f, 400).progress)
        assertFalse(observe(Move.PAPER, .9f, 500).ready)
    }

    @Test fun `an absent hand or uncertain result cancels the candidate`() {
        observe(Move.ROCK, .9f, 0)
        observe(Move.ROCK, .9f, 200)
        observe(null, 0f, 300)
        assertEquals(0f, observe(Move.ROCK, .9f, 400).progress)
        observe(Move.ROCK, .2f, 500)
        assertEquals(0f, observe(Move.ROCK, .9f, 600).progress)
    }

    @Test fun `a long gap cannot be mistaken for a stable hold`() {
        observe(Move.ROCK, .9f, 0)
        assertFalse(observe(Move.ROCK, .9f, 2_000).ready)
        assertEquals(0f, observe(Move.ROCK, .9f, 2_400).progress)
    }

    @Test fun `out of order and non-finite predictions are never accepted`() {
        observe(Move.ROCK, .9f, 200)
        assertFalse(observe(Move.ROCK, .9f, 100).ready)
        assertFalse(observe(Move.ROCK, Float.NaN, 400).ready)
    }
}
