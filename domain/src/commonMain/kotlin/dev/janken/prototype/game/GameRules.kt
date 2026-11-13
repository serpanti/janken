package dev.janken.prototype.game

data class ActiveRound(val startedAt: Long, val botMove: Move) {
    val captureStartedAt get() = startedAt + 3_000L
    val deadline get() = captureStartedAt + 5_000L
}

enum class MatchNotice { TIMED_OUT }
data class MatchTransition(val match: MatchState, val activeRound: ActiveRound?, val notice: MatchNotice? = null)

object GameRules {
    fun start(match: MatchState, active: ActiveRound?, now: Long, bot: Move): MatchTransition {
        if (match.matchOver || active != null) return MatchTransition(match, active)
        return MatchTransition(match.copy(phase = Phase.COUNTDOWN, countdown = 3, captureRemainingMs = 5_000), ActiveRound(now, bot))
    }

    fun advance(match: MatchState, active: ActiveRound?, now: Long): MatchTransition {
        if (active == null) return MatchTransition(match, null)
        return when {
            now >= active.deadline -> MatchTransition(match.copy(phase = Phase.READY), null, MatchNotice.TIMED_OUT)
            now >= active.captureStartedAt -> MatchTransition(match.copy(phase = Phase.CAPTURING,
                captureRemainingMs = active.deadline - now), active)
            else -> MatchTransition(match.copy(countdown = ((active.captureStartedAt - now + 999L) / 1_000L).toInt().coerceIn(1, 3)), active)
        }
    }

    fun acceptMove(match: MatchState, active: ActiveRound?, move: Move, timestamp: Long): MatchTransition {
        if (active == null || match.phase != Phase.CAPTURING || timestamp < active.captureStartedAt || timestamp >= active.deadline)
            return MatchTransition(match, active)
        val updated = match.copy(rounds = match.rounds + Round(move, active.botMove))
        return MatchTransition(updated.copy(phase = if (updated.matchOver) Phase.FINISHED else Phase.RESULT), null)
    }

}
