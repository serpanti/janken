package dev.janken.data

import dev.janken.prototype.game.MatchSummary
import dev.janken.prototype.game.Move
import dev.janken.prototype.game.Round
import dev.janken.prototype.game.SavedHistory
import dev.janken.prototype.game.SavedMatch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomMatchHistoryTest {
    @Test
    fun clearingHistoryKeepsOnlyTheCurrentMatchAcrossReopen() = runTest {
        val directory = Files.createTempDirectory("janken-history-clear").toFile()
        val win = Round(Move.PAPER, Move.ROCK)
        val completed = SavedMatch("completed", List(3) { win })
        val active = SavedMatch("active", listOf(win))
        val empty = SavedMatch("fresh", emptyList())
        try {
            val database = openMatchDatabase(directory.toPath())
            try {
                val history = RoomMatchHistory(database.matches())
                history.save(SavedMatch("abandoned", listOf(win)))
                history.save(completed)
                history.save(active)
                history.clearCompleted(active)
            } finally {
                database.close()
            }

            val reopened = openMatchDatabase(directory.toPath())
            try {
                val history = RoomMatchHistory(reopened.matches())
                assertEquals(SavedHistory(active, emptyList()), history.load())
                history.save(active.copy(rounds = completed.rounds))
                assertEquals(listOf(MatchSummary(active.id, 3, 0)), history.load().completed)
                history.clearCompleted(empty)
                assertEquals(SavedHistory(empty, emptyList()), history.load())
            } finally {
                reopened.close()
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reopensCurrentMatchAndRetainsCompletedMatchesAfterRestart() = runTest {
        val directory = Files.createTempDirectory("janken-history").toFile()
        val win = Round(Move.PAPER, Move.ROCK)
        val loss = Round(Move.ROCK, Move.PAPER)
        val first = SavedMatch("first", List(3) { win })
        val second = SavedMatch("second", listOf(loss))
        try {
            val database = openMatchDatabase(directory.toPath())
            try {
                val history = RoomMatchHistory(database.matches())
                history.save(first)
                history.save(second)
            } finally {
                database.close()
            }

            val reopened = openMatchDatabase(directory.toPath())
            val secondFinished = second.copy(rounds = List(3) { loss })
            val third = SavedMatch("third", emptyList())
            try {
                val history = RoomMatchHistory(reopened.matches())
                assertEquals(second, history.load().current)
                assertEquals(listOf(MatchSummary("first", 3, 0)), history.load().completed)
                history.save(secondFinished)
                history.save(secondFinished)
                history.save(third)
            } finally {
                reopened.close()
            }

            val restarted = openMatchDatabase(directory.toPath())
            try {
                val saved = RoomMatchHistory(restarted.matches()).load()
                assertEquals(third, saved.current)
                assertEquals(listOf(MatchSummary("second", 0, 3), MatchSummary("first", 3, 0)), saved.completed)
            } finally {
                restarted.close()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
