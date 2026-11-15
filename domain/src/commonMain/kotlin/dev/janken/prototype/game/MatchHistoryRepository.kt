package dev.janken.prototype.game

data class SavedMatch(val id: String, val rounds: List<Round>)

data class MatchSummary(val id: String, val playerScore: Int, val botScore: Int)

data class SavedHistory(val current: SavedMatch?, val completed: List<MatchSummary>)

interface MatchHistoryRepository {
    suspend fun load(): SavedHistory
    suspend fun save(match: SavedMatch)
}
