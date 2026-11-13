package dev.janken.prototype.mvi

import dev.janken.prototype.game.Move
import dev.janken.prototype.game.chooseBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

fun interface GameClock { fun nowMillis(): Long }

/** One consumer serializes UI, camera and timer events on every platform. */
class GameStore(
    parentScope: CoroutineScope,
    private val clock: GameClock,
    private val random: Random = Random.Default,
    initial: GameSnapshot = GameSnapshot(),
    autoTick: Boolean = true,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val intents = Channel<GameIntent>(Channel.UNLIMITED)
    private val mutableEffects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var model = GameState.restore(initial)
    private val mutableState = MutableStateFlow(model.ui)
    private val mutableSnapshot = MutableStateFlow(model.snapshot())
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()
    val snapshot: StateFlow<GameSnapshot> = mutableSnapshot.asStateFlow()
    val effects: SharedFlow<GameEffect> = mutableEffects.asSharedFlow()

    init {
        scope.launch {
            for (intent in intents) {
                val bot = if (intent == GameIntent.StartRound) chooseBot(model.ui.match.rounds, model.ui.botMode, random) else Move.ROCK
                val result = GameReducer.reduce(model, intent, clock.nowMillis(), bot)
                model = result.state
                mutableState.value = model.ui
                mutableSnapshot.value = model.snapshot()
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

    fun accept(intent: GameIntent) {
        intents.trySend(intent)
    }

    fun close() {
        intents.close()
        job.cancel()
    }
}
