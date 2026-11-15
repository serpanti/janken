package dev.janken.prototype.mvi

import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.game.*

data class GameUiState(
    val match: MatchState = MatchState(),
    val message: GameMessage? = null,
    val frame: RecognitionFrame = RecognitionFrame(),
    val stable: StableGesture = StableGesture(),
    val cameraReady: Boolean = false,
    val cameraError: String? = null,
    val settings: AppSettings = AppSettings(),
    val manualMove: Move? = null,
    val paused: Boolean = false,
    val history: MatchHistoryUiState = MatchHistoryUiState(),
) {
    val usesButtons get() = settings.inputMethod == InputMethod.BUTTONS
    val botMode get() = settings.botMode
    val skeleton get() = settings.skeleton
}

data class MatchHistoryUiState(
    val completed: List<MatchSummary> = emptyList(),
    val loading: Boolean = false,
    val clearing: Boolean = false,
    val storageError: Boolean = false,
)

enum class GameMessage { TIMED_OUT }
enum class PauseCause { BACKGROUND, DIALOG, CAMERA }

/** Only durable state is restored; a round in progress is always cancelled. */
data class GameSnapshot(
    val rounds: List<Round> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

sealed interface GameIntent {
    data object StartRound : GameIntent
    data object Restart : GameIntent
    data object ClearMatchHistory : GameIntent
    data object Pause : GameIntent
    data object Resume : GameIntent
    data object Tick : GameIntent
    data object CameraReady : GameIntent
    data object RetryCamera : GameIntent
    data class CameraFailed(val message: String) : GameIntent
    data class FrameReceived(val frame: RecognitionFrame) : GameIntent
    data class SetInputMethod(val method: InputMethod) : GameIntent
    data class SelectManualMove(val move: Move) : GameIntent
    data class SetTheme(val mode: ThemeMode) : GameIntent
    data class SetLanguage(val language: AppLanguage) : GameIntent
    data class SetDialogVisible(val visible: Boolean) : GameIntent
    data class SetBotMode(val mode: BotMode) : GameIntent
    data class SetSkeleton(val enabled: Boolean) : GameIntent
}

sealed interface GameEffect {
    data class RoundCompleted(val round: Round) : GameEffect
}

/** Internal model includes the committed bot move, which is excluded from UI state. */
data class GameState(
    val ui: GameUiState = GameUiState(),
    val activeRound: ActiveRound? = null,
    val hold: GestureHold = GestureHold(),
    val pauseCauses: Set<PauseCause> = emptySet(),
    val pausedAt: Long? = null,
) {
    fun snapshot() = GameSnapshot(ui.match.rounds, ui.settings)

    companion object {
        fun restore(snapshot: GameSnapshot): GameState {
            val match = MatchState(rounds = snapshot.rounds.toList())
            return GameState(ui = GameUiState(
                match = match.copy(phase = if (match.matchOver) Phase.FINISHED else Phase.READY),
                settings = snapshot.settings,
            ))
        }
    }
}

data class Reduction(val state: GameState, val effects: List<GameEffect> = emptyList())
