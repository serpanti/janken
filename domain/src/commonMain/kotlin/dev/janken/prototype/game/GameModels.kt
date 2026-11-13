package dev.janken.prototype.game

enum class Move {
    ROCK, PAPER, SCISSORS;

    fun beats(other: Move): Boolean = when (this) {
        ROCK -> other == SCISSORS
        PAPER -> other == ROCK
        SCISSORS -> other == PAPER
    }

    fun counter(): Move = when (this) {
        ROCK -> PAPER
        PAPER -> SCISSORS
        SCISSORS -> ROCK
    }


}

enum class Outcome { WIN, LOSS, DRAW }
enum class Phase { READY, COUNTDOWN, CAPTURING, RESULT, FINISHED }
enum class BotMode { RANDOM, ADAPTIVE }
data class Round(val player: Move, val bot: Move) {
    val outcome: Outcome get() = when {
        player == bot -> Outcome.DRAW
        player.beats(bot) -> Outcome.WIN
        else -> Outcome.LOSS
    }
}

data class MatchState(
    val phase: Phase = Phase.READY,
    val rounds: List<Round> = emptyList(),
    val countdown: Int = 3,
    val captureRemainingMs: Long = 5_000,
) {
    val playerScore get() = rounds.count { it.outcome == Outcome.WIN }
    val botScore get() = rounds.count { it.outcome == Outcome.LOSS }
    val matchOver get() = playerScore >= TARGET || botScore >= TARGET
    val lastRound get() = rounds.lastOrNull()
    companion object { const val TARGET = 3 }
}
