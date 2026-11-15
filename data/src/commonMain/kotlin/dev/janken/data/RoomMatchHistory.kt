package dev.janken.data

import dev.janken.prototype.game.MatchHistoryRepository
import dev.janken.prototype.game.MatchState
import dev.janken.prototype.game.MatchSummary
import dev.janken.prototype.game.Move
import dev.janken.prototype.game.Round
import dev.janken.prototype.game.SavedHistory
import dev.janken.prototype.game.SavedMatch

internal class RoomMatchHistory(private val dao: MatchDao) : MatchHistoryRepository {
    override suspend fun load(): SavedHistory {
        val matches = dao.currentAndCompleted()
        val current = matches.firstOrNull()?.let { SavedMatch(it.id, decodeRounds(it.rounds)) }
        val completed = matches.filter { it.finished }.map { MatchSummary(it.id, it.playerScore, it.botScore) }
        return SavedHistory(current, completed)
    }

    override suspend fun save(match: SavedMatch) {
        dao.save(match.toEntity())
    }

    override suspend fun clearCompleted(current: SavedMatch) {
        dao.clearCompleted(current.toEntity())
    }

    private fun SavedMatch.toEntity(): MatchEntity {
        val score = MatchState(rounds = rounds)
        return MatchEntity(
            id = id,
            rounds = rounds.joinToString(",") { "${it.player.name}:${it.bot.name}" },
            playerScore = score.playerScore,
            botScore = score.botScore,
            finished = score.matchOver,
        )
    }

    private fun decodeRounds(value: String): List<Round> =
        if (value.isEmpty()) emptyList() else value.split(',').map { entry ->
            val (player, bot) = entry.split(':', limit = 2)
            Round(Move.valueOf(player), Move.valueOf(bot))
        }
}
