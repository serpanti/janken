package dev.janken.prototype.mvi

import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.game.*

object GameReducer {
    fun reduce(state: GameState, intent: GameIntent, now: Long, botMove: Move = Move.ROCK): Reduction {
        val ui = state.ui
        return when (intent) {
            GameIntent.StartRound -> {
                if (ui.paused || ui.match.matchOver || state.activeRound != null ||
                    !ui.usesButtons && (!ui.cameraReady || ui.cameraError != null)) return Reduction(state)
                val transition = GameRules.start(ui.match, state.activeRound, now, botMove)
                Reduction(state.copy(ui = ui.copy(match = transition.match, message = null, stable = StableGesture()),
                    activeRound = transition.activeRound, hold = GestureHold()))
            }
            GameIntent.Restart -> Reduction(state.copy(
                ui = ui.copy(match = MatchState(), message = null, stable = StableGesture(), manualMove = null),
                activeRound = null, hold = GestureHold(), pausedAt = null,
            ).let { setPaused(it, PauseCause.CAMERA, false, now) })
            GameIntent.ClearMatchHistory -> Reduction(state)
            GameIntent.Pause -> Reduction(setPaused(state, PauseCause.BACKGROUND, true, now).let {
                val cleared = it.copy(ui = it.ui.copy(cameraReady = false, frame = RecognitionFrame(), stable = StableGesture()), hold = GestureHold())
                if (cleared.activeRound != null && !ui.usesButtons) setPaused(cleared, PauseCause.CAMERA, true, now) else cleared
            })
            GameIntent.Resume -> Reduction(setPaused(state, PauseCause.BACKGROUND, false, now))
            is GameIntent.SetDialogVisible -> Reduction(setPaused(state, PauseCause.DIALOG, intent.visible, now))
            GameIntent.CameraReady -> Reduction(if (ui.usesButtons || PauseCause.BACKGROUND in state.pauseCauses) state else
                setPaused(state.copy(ui = ui.copy(cameraReady = true, cameraError = null)), PauseCause.CAMERA, false, now))
            GameIntent.RetryCamera -> Reduction(if (ui.usesButtons) state else waitForCamera(state, null, now))
            is GameIntent.CameraFailed -> Reduction(if (ui.usesButtons) state else waitForCamera(state, intent.message, now))
            is GameIntent.SetInputMethod -> {
                if (ui.settings.inputMethod == intent.method) return Reduction(state)
                val advanced = advance(state, now)
                val switched = advanced.copy(ui = advanced.ui.copy(settings = ui.settings.copy(inputMethod = intent.method),
                    manualMove = null, cameraReady = false, cameraError = null, frame = RecognitionFrame(), stable = StableGesture()), hold = GestureHold())
                Reduction(setPaused(switched, PauseCause.CAMERA,
                    intent.method == InputMethod.CAMERA && switched.activeRound != null, now))
            }
            is GameIntent.SelectManualMove -> Reduction(if (ui.usesButtons && !ui.paused) state.copy(ui = ui.copy(manualMove = intent.move)) else state)
            is GameIntent.SetTheme -> Reduction(state.copy(ui = ui.copy(settings = ui.settings.copy(theme = intent.mode))))
            is GameIntent.SetLanguage -> Reduction(state.copy(ui = ui.copy(settings = ui.settings.copy(language = intent.language))))
            is GameIntent.SetBotMode -> Reduction(if (state.activeRound == null) state.copy(ui = ui.copy(settings = ui.settings.copy(botMode = intent.mode))) else state)
            is GameIntent.SetSkeleton -> Reduction(state.copy(ui = ui.copy(settings = ui.settings.copy(skeleton = intent.enabled))))
            is GameIntent.FrameReceived -> if (ui.usesButtons || !ui.cameraReady || ui.paused) Reduction(state)
                else receiveFrame(advance(state, now), intent.frame, now)
            GameIntent.Tick -> {
                if (ui.paused) return Reduction(state)
                val advanced = advance(state, now)
                val current = advanced.ui
                if (current.usesButtons && current.manualMove != null) {
                    receiveFrame(advanced, RecognitionFrame(move = current.manualMove, confidence = 1f, timestamp = now), now)
                } else if (current.frame.timestamp != 0L && now - current.frame.timestamp > 450L) {
                    Reduction(advanced.copy(ui = current.copy(frame = RecognitionFrame(), stable = StableGesture()), hold = GestureHold()))
                } else Reduction(advanced)
            }
        }
    }

    private fun waitForCamera(state: GameState, error: String?, now: Long): GameState {
        val next = state.copy(ui = state.ui.copy(cameraReady = false, cameraError = error,
            frame = RecognitionFrame(), stable = StableGesture()), hold = GestureHold())
        return if (next.activeRound != null) setPaused(next, PauseCause.CAMERA, true, now) else next
    }

    private fun setPaused(state: GameState, cause: PauseCause, enabled: Boolean, now: Long): GameState {
        val causes = if (enabled) state.pauseCauses + cause else state.pauseCauses - cause
        if (causes == state.pauseCauses) return state
        val entering = state.pauseCauses.isEmpty() && causes.isNotEmpty()
        val leaving = state.pauseCauses.isNotEmpty() && causes.isEmpty()
        val before = if (entering) advance(state, now) else state
        val active = if (leaving && before.pausedAt != null) before.activeRound?.let {
            it.copy(startedAt = it.startedAt + (now - before.pausedAt).coerceAtLeast(0))
        } else before.activeRound
        return before.copy(activeRound = active, pauseCauses = causes,
            pausedAt = when { leaving -> null; entering && active != null -> now; else -> before.pausedAt },
            hold = if (entering) GestureHold() else before.hold,
            ui = before.ui.copy(paused = causes.isNotEmpty(), frame = if (entering) RecognitionFrame() else before.ui.frame,
                stable = if (entering) StableGesture() else before.ui.stable))
    }

    private fun advance(state: GameState, now: Long): GameState {
        if (state.ui.paused) return state
        val transition = GameRules.advance(state.ui.match, state.activeRound, now)
        val entering = transition.match.phase == Phase.CAPTURING && state.ui.match.phase != Phase.CAPTURING
        val timedOut = transition.notice == MatchNotice.TIMED_OUT
        val reset = entering || timedOut
        return state.copy(activeRound = transition.activeRound, hold = if (reset) GestureHold() else state.hold,
            ui = state.ui.copy(match = transition.match, message = if (timedOut) GameMessage.TIMED_OUT else state.ui.message,
                stable = if (reset) StableGesture() else state.ui.stable))
    }

    private fun receiveFrame(state: GameState, frame: RecognitionFrame, now: Long): Reduction {
        if (frame.timestamp > now || now - frame.timestamp > 450L || frame.timestamp < state.ui.frame.timestamp) return Reduction(state)
        val active = state.activeRound
        if (state.ui.match.phase == Phase.CAPTURING && active != null && frame.timestamp < active.captureStartedAt) return Reduction(state)
        val observation = observeGesture(state.hold, frame.move, frame.confidence, frame.timestamp)
        val ui = state.ui.copy(frame = frame, stable = observation.stable)
        val next = state.copy(ui = ui, hold = observation.hold)
        val move = observation.stable.move
        if (!observation.stable.ready || move == null) return Reduction(next)
        val transition = GameRules.acceptMove(ui.match, active, move, frame.timestamp)
        if (transition.match.rounds.size == ui.match.rounds.size) return Reduction(next)
        val round = transition.match.lastRound!!
        return Reduction(next.copy(ui = ui.copy(match = transition.match, message = null), activeRound = transition.activeRound),
            listOf(GameEffect.RoundCompleted(round)))
    }
}
