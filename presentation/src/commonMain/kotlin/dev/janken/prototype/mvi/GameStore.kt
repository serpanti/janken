package dev.janken.prototype.mvi

import co.touchlab.kermit.Logger
import dev.janken.prototype.game.MatchHistoryRepository
import dev.janken.prototype.game.MatchState
import dev.janken.prototype.game.MatchSummary
import dev.janken.prototype.game.Move
import dev.janken.prototype.game.SavedMatch
import dev.janken.prototype.game.chooseBot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface GameClock { fun nowMillis(): Long }

/** One consumer serializes UI, camera and timer events on every platform. */
class GameStore(
    parentScope: CoroutineScope,
    private val clock: GameClock,
    private val random: Random = Random.Default,
    initial: GameSnapshot = GameSnapshot(),
    autoTick: Boolean = true,
    private val settingsStorage: SettingsStorage? = null,
    private val openHistory: (() -> MatchHistoryRepository)? = null,
) {
    private val log = Logger.withTag("Janken")
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val intents = Channel<GameIntent>(Channel.UNLIMITED)
    private val mutableEffects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var model = GameState.restore(initial)
    private var historyRepository: MatchHistoryRepository? = null
    private var matchId: String? = null
    private val pendingMatches = linkedMapOf<String, SavedMatch>()
    private val mutableState = MutableStateFlow(model.ui.copy(history = MatchHistoryUiState(loading = openHistory != null)))
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()
    val effects: SharedFlow<GameEffect> = mutableEffects.asSharedFlow()

    init {
        scope.launch {
            restoreHistory(initial)
            mutableState.value = model.ui
            for (intent in intents) {
                val bot = if (intent == GameIntent.StartRound) chooseBot(model.ui.match.rounds, model.ui.botMode, random) else Move.ROCK
                val result = GameReducer.reduce(model, intent, clock.nowMillis(), bot)
                var next = result.state
                if (intent is GameIntent.CameraFailed && next.ui.cameraError != model.ui.cameraError) {
                    log.w { "Camera unavailable: ${intent.message}" }
                }
                if (next.ui.settings != model.ui.settings) saveSettings(next.ui.settings)
                if (next.ui.match.rounds != model.ui.match.rounds) {
                    if (intent == GameIntent.Restart) matchId = null
                    next = next.copy(ui = next.ui.copy(history = persistMatch(next.ui.match, next.ui.history)))
                }
                model = next
                mutableState.value = model.ui
                result.effects.forEach { mutableEffects.tryEmit(it) }
            }
        }
        if (autoTick) {
            scope.launch {
                while (isActive) {
                    delay(50)
                    intents.send(GameIntent.Tick)
                }
            }
        }
    }

    private suspend fun restoreHistory(initial: GameSnapshot) {
        if (openHistory == null) return
        try {
            val history = openHistory()
            val saved = history.load()
            historyRepository = history
            matchId = saved.current?.id
            model = GameState.restore(initial.copy(rounds = saved.current?.rounds ?: initial.rounds))
            val restored = MatchHistoryUiState(completed = saved.completed)
            val uiHistory = if (saved.current == null && initial.rounds.isNotEmpty()) {
                persistMatch(model.ui.match, restored)
            } else restored
            model = model.copy(ui = model.ui.copy(history = uiHistory))
            log.d { "Match history loaded" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.e(error) { "Could not load match history" }
            model = model.copy(ui = model.ui.copy(history = MatchHistoryUiState(storageError = true)))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun persistMatch(match: MatchState, history: MatchHistoryUiState): MatchHistoryUiState {
        val repository = historyRepository ?: return history
        val id = matchId ?: Uuid.random().toString().also { matchId = it }
        pendingMatches[id] = SavedMatch(id, match.rounds)
        val writes = pendingMatches.values.iterator()
        var completed = history.completed
        try {
            while (writes.hasNext()) {
                val saved = writes.next()
                repository.save(saved)
                writes.remove()
                val score = MatchState(rounds = saved.rounds)
                if (score.matchOver) {
                    completed = listOf(MatchSummary(saved.id, score.playerScore, score.botScore)) + completed
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.e(error) { "Could not save match history" }
        }
        return MatchHistoryUiState(completed = completed, storageError = pendingMatches.isNotEmpty())
    }

    private fun saveSettings(settings: AppSettings) {
        try {
            settingsStorage?.save(settings)
        } catch (error: Exception) {
            log.w(error) { "Could not save settings" }
        }
    }

    fun accept(intent: GameIntent) {
        intents.trySend(intent)
    }

    fun close() {
        intents.close()
        job.cancel()
    }
}
