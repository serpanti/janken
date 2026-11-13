package dev.janken.prototype.game

data class StableGesture(val move: Move? = null, val progress: Float = 0f, val ready: Boolean = false)
data class GestureHold(val candidate: Move? = null, val since: Long = 0, val lastAt: Long = Long.MIN_VALUE, val samples: Int = 0)
data class GestureObservation(val hold: GestureHold, val stable: StableGesture)

const val MIN_GESTURE_CONFIDENCE = .65f

fun observeGesture(
    previous: GestureHold, move: Move?, score: Float, timestamp: Long,
    holdMs: Long = 350, maxGapMs: Long = 250, threshold: Float = MIN_GESTURE_CONFIDENCE,
): GestureObservation {
    if (timestamp <= previous.lastAt) return GestureObservation(previous, StableGesture())
    if (move == null || score < threshold || !score.isFinite()) return GestureObservation(GestureHold(), StableGesture())
    val reset = previous.candidate != move || previous.lastAt == Long.MIN_VALUE || timestamp - previous.lastAt > maxGapMs
    val hold = if (reset) GestureHold(move, timestamp, timestamp, 1) else previous.copy(lastAt = timestamp, samples = previous.samples + 1)
    val progress = ((timestamp - hold.since).toFloat() / holdMs).coerceIn(0f, 1f)
    return GestureObservation(hold, StableGesture(move, progress, progress >= 1f && hold.samples >= 3))
}
