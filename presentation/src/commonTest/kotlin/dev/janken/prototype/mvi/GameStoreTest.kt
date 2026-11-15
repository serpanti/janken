package dev.janken.prototype.mvi

import dev.janken.prototype.game.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GameStoreTest {
    @Test fun queuedIntentsAreSerializedAndEffectIsNotReplayed() = runTest {
        var now = 1_000L
        val store = GameStore(backgroundScope, GameClock { now }, Random(9), GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS)), autoTick = false)
        val effects = mutableListOf<GameEffect>()
        store.effects.onEach { effects += it }.launchIn(backgroundScope)
        runCurrent()
        store.accept(GameIntent.SelectManualMove(Move.ROCK))
        store.accept(GameIntent.StartRound)
        runCurrent()
        assertEquals(Phase.COUNTDOWN, store.state.value.match.phase)
        for (time in listOf(4_000L, 4_120L, 4_240L, 4_360L)) {
            now = time
            store.accept(GameIntent.Tick)
            runCurrent()
        }
        assertEquals(1, store.state.value.match.rounds.size)
        assertEquals(1, effects.size)
        val lateEffects = mutableListOf<GameEffect>()
        store.effects.onEach { lateEffects += it }.launchIn(backgroundScope)
        runCurrent()
        assertTrue(lateEffects.isEmpty())
        store.close()
    }

    @Test fun pausedSnapshotCanBeRestoredInAnotherPlatformStore() = runTest {
        val clock = GameClock { 1_000 }
        val original = GameSnapshot(rounds = listOf(Round(Move.PAPER, Move.ROCK)), settings = AppSettings(inputMethod = InputMethod.BUTTONS, botMode = BotMode.ADAPTIVE))
        val first = GameStore(backgroundScope, clock, initial = original, autoTick = false)
        first.accept(GameIntent.StartRound)
        first.accept(GameIntent.Pause)
        runCurrent()
        val snapshot = GameSnapshot(first.state.value.match.rounds, first.state.value.settings)
        val second = GameStore(backgroundScope, clock, initial = snapshot, autoTick = false)
        assertEquals(1, second.state.value.match.playerScore)
        assertEquals(Phase.READY, second.state.value.match.phase)
        assertEquals(BotMode.ADAPTIVE, second.state.value.botMode)
        first.close(); second.close()
    }

    @Test fun restoresAndSavesRoundBeforePublishingResult() = runTest {
        var now = 1_000L
        val firstRound = Round(Move.PAPER, Move.ROCK)
        val written = mutableListOf<SavedMatch>()
        val writing = CompletableDeferred<Unit>()
        val storage = object : MatchHistoryRepository {
            override suspend fun load() = SavedHistory(SavedMatch("current", listOf(firstRound)), emptyList())
            override suspend fun save(match: SavedMatch) {
                writing.await()
                written += match
            }
        }
        val store = GameStore(
            backgroundScope, GameClock { now },
            initial = GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS)),
            autoTick = false, openHistory = { storage },
        )
        assertTrue(store.state.value.history.loading)
        runCurrent()
        assertEquals(listOf(firstRound), store.state.value.match.rounds)
        assertEquals(Phase.READY, store.state.value.match.phase)

        store.accept(GameIntent.SelectManualMove(Move.ROCK))
        store.accept(GameIntent.StartRound)
        runCurrent()
        for (time in listOf(4_000L, 4_120L, 4_240L, 4_360L)) {
            now = time
            store.accept(GameIntent.Tick)
            runCurrent()
        }
        assertEquals(listOf(firstRound), store.state.value.match.rounds)
        writing.complete(Unit)
        runCurrent()
        assertEquals(Phase.RESULT, store.state.value.match.phase)
        assertEquals(listOf(SavedMatch("current", store.state.value.match.rounds)), written)
        store.close()
    }
}
