package dev.janken.prototype.mvi

import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.game.*
import kotlin.test.*
import kotlin.random.Random

class GameReducerTest {
    private class Harness(snapshot: GameSnapshot = GameSnapshot()) {
        var state = GameState.restore(snapshot)
        val effects = mutableListOf<GameEffect>()
        fun send(intent: GameIntent, at: Long, bot: Move = Move.ROCK) {
            val reduction = GameReducer.reduce(state, intent, at, bot)
            state = reduction.state
            effects += reduction.effects
        }
        fun frame(move: Move?, at: Long, deliveredAt: Long = at, confidence: Float = .9f) =
            send(GameIntent.FrameReceived(RecognitionFrame(move = move, confidence = confidence, timestamp = at)), deliveredAt)
        fun start(at: Long = 1_000, bot: Move = Move.ROCK) {
            send(GameIntent.CameraReady, at)
            send(GameIntent.StartRound, at, bot)
        }
        fun hold(move: Move, start: Long) {
            frame(move, start); frame(move, start + 120); frame(move, start + 240); frame(move, start + 360)
        }
    }

    @Test fun cannotStartWithoutCameraOrDemo() {
        val h = Harness()
        h.send(GameIntent.StartRound, 1_000)
        assertEquals(Phase.READY, h.state.ui.match.phase)
        h.send(GameIntent.SetInputMethod(InputMethod.BUTTONS), 1_000)
        h.send(GameIntent.StartRound, 1_000)
        assertEquals(Phase.COUNTDOWN, h.state.ui.match.phase)
    }

    @Test fun reselectingCurrentModeDoesNotInterruptARoundOrResetInput() {
        val camera = Harness()
        camera.start()
        camera.frame(Move.PAPER, 2_000)
        val cameraBefore = camera.state
        camera.send(GameIntent.SetInputMethod(InputMethod.CAMERA), 2_100)
        assertSame(cameraBefore, camera.state)

        val buttons = Harness(GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS)))
        buttons.send(GameIntent.SelectManualMove(Move.SCISSORS), 500)
        buttons.send(GameIntent.StartRound, 1_000)
        val buttonsBefore = buttons.state
        buttons.send(GameIntent.SetInputMethod(InputMethod.BUTTONS), 1_100)
        assertSame(buttonsBefore, buttons.state)
    }

    @Test fun switchingInputKeepsRoundBotAndFinishedResultsWhileWaitingForCamera() {
        val completed = Round(Move.ROCK, Move.SCISSORS)
        val h = Harness(GameSnapshot(rounds = listOf(completed), settings = AppSettings(botMode = BotMode.ADAPTIVE, skeleton = false)))
        h.start(bot = Move.SCISSORS)
        val committed = h.state.activeRound!!
        h.send(GameIntent.SetInputMethod(InputMethod.BUTTONS), 2_100)
        assertEquals(Phase.COUNTDOWN, h.state.ui.match.phase)
        assertEquals(committed, h.state.activeRound)
        assertEquals(listOf(completed), h.state.ui.match.rounds)
        h.send(GameIntent.CameraReady, 2_200)
        h.send(GameIntent.CameraFailed("late callback"), 2_200)
        h.frame(Move.PAPER, 2_300)
        assertFalse(h.state.ui.cameraReady)
        assertNull(h.state.ui.cameraError)
        assertEquals(RecognitionFrame(), h.state.ui.frame)
        h.send(GameIntent.SetInputMethod(InputMethod.CAMERA), 2_400)
        h.send(GameIntent.Tick, 20_000)
        assertTrue(h.state.ui.paused)
        assertEquals(Phase.COUNTDOWN, h.state.ui.match.phase)
        assertEquals(committed, h.state.activeRound)
        h.send(GameIntent.CameraReady, 20_400)
        assertFalse(h.state.ui.paused)
        assertEquals(committed.startedAt + 18_000, h.state.activeRound!!.startedAt)
        assertEquals(committed.botMove, h.state.activeRound!!.botMove)
        assertEquals(BotMode.ADAPTIVE, h.state.ui.botMode)
        assertFalse(h.state.ui.skeleton)
        assertTrue(h.effects.isEmpty())
    }

    @Test fun retryClearsErrorButKeepsThePausedRoundAndTimeBudget() {
        val h = Harness()
        h.start()
        h.send(GameIntent.CameraFailed("device unavailable"), 2_000)
        h.send(GameIntent.RetryCamera, 2_100)
        h.frame(Move.PAPER, 2_200)
        assertNull(h.state.ui.cameraError)
        assertFalse(h.state.ui.cameraReady)
        assertEquals(RecognitionFrame(), h.state.ui.frame)
        h.send(GameIntent.StartRound, 2_300)
        assertNotNull(h.state.activeRound)
        assertTrue(h.state.ui.paused)
        h.send(GameIntent.CameraReady, 2_400)
        assertTrue(h.state.ui.cameraReady)
        assertFalse(h.state.ui.paused)
        assertEquals(1_400L, h.state.activeRound!!.startedAt)
    }

    @Test fun settingsPausePreservesCountdownAndBotThroughInputThemeAndLanguageChanges() {
        val h = Harness()
        h.start(bot = Move.ROCK)
        h.send(GameIntent.Tick, 2_200)
        val match = h.state.ui.match
        h.send(GameIntent.SetDialogVisible(true), 2_200)
        h.send(GameIntent.SetInputMethod(InputMethod.BUTTONS), 2_300)
        h.send(GameIntent.SetTheme(ThemeMode.LIGHT), 2_400)
        h.send(GameIntent.SetLanguage(AppLanguage.EN), 2_500)
        h.send(GameIntent.Tick, 20_000)
        assertEquals(match, h.state.ui.match)
        assertEquals(Move.ROCK, h.state.activeRound!!.botMove)
        h.send(GameIntent.SetDialogVisible(false), 20_200)
        assertEquals(19_000L, h.state.activeRound!!.startedAt)
        h.send(GameIntent.SelectManualMove(Move.PAPER), 20_300)
        for (time in listOf(22_000L, 22_120L, 22_240L, 22_360L)) h.send(GameIntent.Tick, time)
        assertEquals(listOf(Round(Move.PAPER, Move.ROCK)), h.state.ui.match.rounds)
        assertEquals(ThemeMode.LIGHT, h.state.ui.settings.theme)
        assertEquals(AppLanguage.EN, h.state.ui.settings.language)
        assertEquals(1, h.effects.size)
    }

    @Test fun overlappingSettingsBackgroundAndCameraWaitResumeOnlyAfterAllAreReady() {
        val h = Harness()
        h.start()
        h.send(GameIntent.SetDialogVisible(true), 1_500)
        h.send(GameIntent.Pause, 1_600)
        h.send(GameIntent.SetDialogVisible(false), 2_000)
        h.send(GameIntent.CameraReady, 3_000)
        assertFalse(h.state.ui.cameraReady)
        h.send(GameIntent.Resume, 10_000)
        assertTrue(h.state.ui.paused)
        h.send(GameIntent.CameraReady, 10_500)
        assertFalse(h.state.ui.paused)
        assertEquals(10_000L, h.state.activeRound!!.startedAt)
        assertEquals(Phase.COUNTDOWN, h.state.ui.match.phase)
    }

    @Test fun stableGestureCompletesExactlyOneRoundAndOneEffect() {
        val h = Harness()
        h.start(bot = Move.ROCK)
        h.hold(Move.PAPER, 4_000)
        h.hold(Move.SCISSORS, 4_500)
        assertEquals(Phase.RESULT, h.state.ui.match.phase)
        assertEquals(listOf(Round(Move.PAPER, Move.ROCK)), h.state.ui.match.rounds)
        assertEquals(1, h.effects.size)
        assertNull(h.state.activeRound)
    }

    @Test fun countdownFramesNeverContributeToCaptureHold() {
        val h = Harness()
        h.start()
        h.hold(Move.PAPER, 3_500)
        h.frame(Move.PAPER, 4_000)
        assertEquals(0f, h.state.ui.stable.progress)
        assertTrue(h.state.ui.match.rounds.isEmpty())
        h.frame(Move.PAPER, 3_950, deliveredAt = 4_020)
        assertEquals(4_000, h.state.ui.frame.timestamp)
        h.frame(Move.PAPER, 4_120)
        h.frame(Move.PAPER, 4_240)
        h.frame(Move.PAPER, 4_360)
        assertEquals(1, h.state.ui.match.rounds.size)
    }

    @Test fun largeClockJumpAndLateFramesDoNotReopenCapture() {
        val h = Harness()
        h.start()
        h.send(GameIntent.Tick, 30_000)
        h.hold(Move.PAPER, 30_100)
        assertEquals(Phase.READY, h.state.ui.match.phase)
        assertTrue(h.state.ui.match.rounds.isEmpty())
        h.start(31_000)
        h.frame(Move.PAPER, 30_400, deliveredAt = 34_000)
        assertFalse(h.state.ui.stable.ready)
        assertTrue(h.state.ui.match.rounds.isEmpty())
    }

    @Test fun delayedResultAtDeadlineCannotAwardPointsEvenWithoutTimerTick() {
        val h = Harness()
        h.start()
        h.frame(Move.PAPER, 8_600)
        h.frame(Move.PAPER, 8_720)
        h.frame(Move.PAPER, 8_840)
        h.frame(Move.PAPER, 8_960, deliveredAt = 9_000)
        assertTrue(h.state.ui.match.rounds.isEmpty())
        assertEquals(Phase.READY, h.state.ui.match.phase)
    }

    @Test fun pauseIgnoresInflightCameraResultsAndKeepsFinishedRounds() {
        val first = Round(Move.ROCK, Move.SCISSORS)
        val h = Harness(GameSnapshot(rounds = listOf(first)))
        h.start()
        h.send(GameIntent.Pause, 4_100)
        h.hold(Move.PAPER, 4_200)
        assertEquals(listOf(first), h.state.ui.match.rounds)
        assertFalse(h.state.ui.cameraReady)
        assertEquals(RecognitionFrame(), h.state.ui.frame)
        assertTrue(h.effects.isEmpty())
    }

    @Test fun cameraFailurePausesRoundAndLateResultsCannotScore() {
        val h = Harness()
        h.start()
        h.send(GameIntent.CameraFailed("test error"), 4_100)
        h.hold(Move.PAPER, 4_200)
        assertEquals("test error", h.state.ui.cameraError)
        assertEquals(Phase.CAPTURING, h.state.ui.match.phase)
        assertTrue(h.state.ui.paused)
        assertTrue(h.state.ui.match.rounds.isEmpty())
    }

    @Test fun thirdWinEndsMatchAndRestartClearsIt() {
        val h = Harness(GameSnapshot(rounds = List(2) { Round(Move.ROCK, Move.SCISSORS) }))
        h.start(bot = Move.SCISSORS)
        h.hold(Move.ROCK, 4_000)
        assertEquals(Phase.FINISHED, h.state.ui.match.phase)
        h.send(GameIntent.StartRound, 5_000)
        assertNull(h.state.activeRound)
        h.send(GameIntent.Restart, 5_100)
        assertEquals(0, h.state.ui.match.playerScore)
        assertEquals(Phase.READY, h.state.ui.match.phase)
    }

    @Test fun drawDoesNotAwardPoints() {
        val h = Harness()
        h.start(bot = Move.PAPER)
        h.hold(Move.PAPER, 4_000)
        assertEquals(Outcome.DRAW, h.state.ui.match.lastRound!!.outcome)
        assertEquals(0, h.state.ui.match.playerScore)
        assertEquals(0, h.state.ui.match.botScore)
    }

    @Test fun demoUsesSameHoldAndRoundRulesAndIgnoresCameraCallbacks() {
        val h = Harness(GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS)))
        h.send(GameIntent.SelectManualMove(Move.PAPER), 1_000)
        h.send(GameIntent.CameraReady, 1_000)
        h.send(GameIntent.CameraFailed("ignored"), 1_000)
        h.send(GameIntent.StartRound, 1_000, Move.ROCK)
        listOf(4_000L, 4_120L, 4_240L, 4_360L).forEach { h.send(GameIntent.Tick, it) }
        assertEquals(1, h.state.ui.match.playerScore)
        assertNull(h.state.ui.cameraError)
        assertEquals(1, h.effects.size)
    }

    @Test fun restoreKeepsSettingsAndResultsButNotTransientStateOrEffects() {
        val h = Harness(GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS, botMode = BotMode.ADAPTIVE, skeleton = false)))
        h.send(GameIntent.StartRound, 1_000)
        val restored = GameState.restore(h.state.snapshot())
        assertEquals(Phase.READY, restored.ui.match.phase)
        assertNull(restored.activeRound)
        assertEquals(BotMode.ADAPTIVE, restored.ui.botMode)
        assertFalse(restored.ui.skeleton)
        assertTrue(restored.ui.usesButtons)
        assertEquals(RecognitionFrame(), restored.ui.frame)
    }

    @Test fun botChoiceIsCommittedBeforeCaptureAndModeIsLockedDuringRound() {
        val h = Harness()
        h.start(bot = Move.SCISSORS)
        h.send(GameIntent.SetBotMode(BotMode.ADAPTIVE), 2_000)
        h.send(GameIntent.StartRound, 2_000, Move.PAPER)
        h.hold(Move.ROCK, 4_000)
        assertEquals(BotMode.RANDOM, h.state.ui.botMode)
        assertEquals(Move.SCISSORS, h.state.ui.match.lastRound!!.bot)
    }

    @Test fun staleFutureAndOutOfOrderFramesAreIgnored() {
        val h = Harness()
        h.start()
        h.frame(Move.PAPER, 4_000)
        h.frame(Move.PAPER, 3_900, deliveredAt = 4_010)
        h.frame(Move.PAPER, 4_500, deliveredAt = 4_010)
        h.frame(Move.PAPER, 4_010, deliveredAt = 4_600)
        assertEquals(4_000, h.state.ui.frame.timestamp)
        assertTrue(h.state.ui.match.rounds.isEmpty())
    }

    @Test fun reducerDoesNotMutateItsInputAndReplaysDeterministically() {
        val original = GameState.restore(GameSnapshot(settings = AppSettings(inputMethod = InputMethod.BUTTONS)))
        val first = GameReducer.reduce(original, GameIntent.StartRound, 1_000, Move.PAPER)
        val second = GameReducer.reduce(original, GameIntent.StartRound, 1_000, Move.PAPER)
        assertEquals(first, second)
        assertEquals(Phase.READY, original.ui.match.phase)
        assertNull(original.activeRound)
    }

    @Test fun adaptiveBotSelectionIsReproducibleFromPastRoundsOnly() {
        val history = List(5) { Round(Move.ROCK, Move.SCISSORS) }
        assertEquals(chooseBot(history, BotMode.ADAPTIVE, Random(91)), chooseBot(history, BotMode.ADAPTIVE, Random(91)))
    }
}
