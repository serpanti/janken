package dev.janken.prototype.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.janken.prototype.mvi.*
import dev.janken.prototype.camera.RecognitionFrame
import dev.janken.prototype.camera.RecognitionMethod
import dev.janken.prototype.camera.handConnections
import dev.janken.prototype.game.*
import kotlin.math.max
import org.jetbrains.compose.resources.Font
import dev.janken.resources.Res
import dev.janken.resources.gesture_symbols
import dev.janken.resources.japanese_subtitle

@Composable
fun JankenScreen(
    state: GameUiState,
    permissionGranted: Boolean,
    cameraActive: Boolean,
    cameraAttempt: Int,
    onPermission: () -> Unit,
    onRetry: () -> Unit,
    onIntent: (GameIntent) -> Unit,
    cameraSupported: Boolean = true,
    cameraContent: @Composable (Modifier) -> Unit = {},
) {
    val s = LocalAppStrings.current
    var help by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val dialogOpen = settingsOpen || help || historyOpen
    LaunchedEffect(dialogOpen) { onIntent(GameIntent.SetDialogVisible(dialogOpen)) }
    val openSettings = { onIntent(GameIntent.SetDialogVisible(true)); settingsOpen = true }
    val openHistory = { onIntent(GameIntent.SetDialogVisible(true)); historyOpen = true }
    val closeDialogs = { settingsOpen = false; help = false; historyOpen = false; onIntent(GameIntent.SetDialogVisible(false)) }
    val onStart = { onIntent(GameIntent.StartRound) }
    val onRestart = { onIntent(GameIntent.Restart) }
    val onButtons: (Boolean) -> Unit = { onIntent(GameIntent.SetInputMethod(if (it) InputMethod.BUTTONS else InputMethod.CAMERA)) }
    val onInput: (InputMethod) -> Unit = { onIntent(GameIntent.SetInputMethod(it)) }
    val swipeInput = Modifier.inputSwipes(state.settings.inputMethod, cameraSupported && !dialogOpen, onInput)
    val onManualMove: (Move) -> Unit = { onIntent(GameIntent.SelectManualMove(it)) }
    val onBotMode: (BotMode) -> Unit = { onIntent(GameIntent.SetBotMode(it)) }
    val retryCamera = { onIntent(GameIntent.RetryCamera); onRetry() }
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()
        .semantics { if (dialogOpen) hideFromAccessibility() }) {
        val landscape = maxWidth > maxHeight && maxWidth >= 720.dp
        val scrollLayout = !landscape && maxHeight < 680.dp
        val compact = maxHeight < 750.dp
        val showControls = true
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)
            .then(if (scrollLayout) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Lime), contentAlignment = Alignment.Center) {
                    Text("J", color = Ink, fontWeight = FontWeight.Black, fontSize = 25.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("JANKEN", fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, fontSize = 20.sp)
                    Row {
                        Text("じゃんけん", fontFamily = FontFamily(Font(Res.font.japanese_subtitle)), color = Muted, fontSize = 10.sp)
                        Text(" · ${s.subtitle}", color = Muted, fontSize = 10.sp)
                    }
                }
                TextButton(
                    onClick = openHistory,
                    modifier = Modifier.semantics { contentDescription = s.matchHistory },
                ) {
                    Text(s.history, color = if (state.history.storageError) Warm else Snow)
                }
                Box(Modifier.clip(CircleShape).background(Panel).clickable(onClick = openSettings).padding(horizontal = 14.dp, vertical = 8.dp).semantics { contentDescription = s.settings }) {
                    Text("•••", color = Snow, letterSpacing = 2.sp)
                }
            }
            InputModeControl(state.settings.inputMethod, cameraSupported, onInput)
            if (landscape) {
                Row(Modifier.weight(1f).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CameraPanel(state, permissionGranted, cameraActive, cameraAttempt, onPermission, cameraContent, retryCamera, onButtons, Modifier.weight(1f).fillMaxHeight().then(swipeInput))
                    if (showControls) Column(Modifier.width(340.dp).verticalScroll(rememberScrollState())) {
                        Controls(state, onStart, onRestart, onManualMove, onBotMode, compact = true)
                    }
                }
            } else {
                CameraPanel(state, permissionGranted, cameraActive, cameraAttempt, onPermission, cameraContent, retryCamera, onButtons,
                    (if (scrollLayout) Modifier.height(280.dp).fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()).then(swipeInput))
                if (showControls) {
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                    Controls(state, onStart, onRestart, onManualMove, onBotMode, compact)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (settingsOpen) SettingsDialog(state.settings, cameraSupported, onIntent,
        onRules = { settingsOpen = false; help = true }, onDismiss = closeDialogs)
    if (historyOpen) MatchHistoryDialog(state.history, closeDialogs)
    if (help) AppModal(s.rules, closeDialogs, actions = {
        TextButton(onClick = { help = false; settingsOpen = true }) { Text(s.settings) }
        TextButton(onClick = closeDialogs) { Text(s.done) }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.rulesBody)
            Text(gestureText(s.beatsRules), lineHeight = 26.sp)
            Text(s.shapeRules, color = Muted, fontSize = 12.sp)
            Text(s.botRules, color = Muted)
        }
    }
    }
}

@Composable
private fun CameraPanel(state: GameUiState, permissionGranted: Boolean, active: Boolean, attempt: Int,
    onPermission: () -> Unit, cameraContent: @Composable (Modifier) -> Unit, onRetry: () -> Unit,
    onButtons: (Boolean) -> Unit, modifier: Modifier) = CameraPalette {
    CameraPanelContent(state, permissionGranted, active, attempt, onPermission, cameraContent, onRetry, onButtons, modifier)
}

@Composable
private fun CameraPanelContent(
    state: GameUiState,
    permissionGranted: Boolean,
    active: Boolean,
    attempt: Int,
    onPermission: () -> Unit,
    cameraContent: @Composable (Modifier) -> Unit,
    onRetry: () -> Unit,
    onButtons: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val s = LocalAppStrings.current
    val running = state.match.phase == Phase.COUNTDOWN || state.match.phase == Phase.CAPTURING
    val recognized = state.stable.move != null
    val color = if (state.stable.ready) Lime else Cyan
    BoxWithConstraints(modifier.clip(RoundedCornerShape(28.dp)).background(Panel).border(1.dp, Edge, RoundedCornerShape(28.dp))) {
        val smallPreview = maxHeight < 260.dp
        val tightPreview = maxHeight < 360.dp
        if (!state.usesButtons && permissionGranted && active) {
            key(attempt) { cameraContent(Modifier.fillMaxSize()) }
        }
        if (state.usesButtons) {
            DemoBackdrop(state.manualMove, state.match.phase != Phase.COUNTDOWN, Modifier.fillMaxSize())
        } else if (state.skeleton && state.frame.points.isNotEmpty()) {
            HandOverlay(state.frame, color, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Transparent, Ink.copy(alpha = .94f)))))
        if (state.frame.points.isEmpty() && !state.usesButtons && permissionGranted && state.cameraError == null) {
            AimCorners(Modifier.align(Alignment.Center).fillMaxWidth(.62f).aspectRatio(.84f), Cyan.copy(alpha = .65f))
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (state.usesButtons) Warm else if (state.cameraReady) Lime else Muted, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(if (state.usesButtons) s.buttonsStatus else s.cameraStatus, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!state.usesButtons && state.frame.timestamp != 0L) {
                Text(if (s.language == AppLanguage.RU) "${state.frame.inferenceMs} мс" else "${state.frame.inferenceMs} ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Cyan)
            }
        }
        when {
            !state.usesButtons && !permissionGranted -> {
                Column(Modifier.align(Alignment.Center).padding(if (tightPreview) 16.dp else 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (tightPreview) 6.dp else 12.dp)) {
                    if (!tightPreview) Text("✋", fontSize = 48.sp, fontFamily = gestureFont())
                    Text(s.playWithHand, fontSize = if (tightPreview) 18.sp else 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(s.grantHint, color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
                    Button(onClick = onPermission, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text(s.allowCamera) }
                    TextButton(onClick = { onButtons(true) }) { Text(s.useButtons, color = Cyan) }
                }
            }
            !state.usesButtons && state.cameraError != null -> {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.cameraUnavailable, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(s.cameraError(state.cameraError.orEmpty()), color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text(s.retry) }
                    TextButton(onClick = { onButtons(true) }) { Text(s.useButtons) }
                }
            }
            state.paused -> {
                Column(Modifier.align(Alignment.Center).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.paused, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (!state.usesButtons && !state.cameraReady) s.waitingCamera else s.pausedHint, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
                }
            }
            state.match.phase == Phase.COUNTDOWN -> {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.match.countdown}", fontSize = if (smallPreview) 68.sp else 112.sp,
                        fontWeight = FontWeight.Black, color = Lime, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(s.committed, fontSize = 10.sp, letterSpacing = 2.sp)
                }
            }
            !state.usesButtons && !state.cameraReady && active -> {
                CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center), color = Lime, strokeWidth = 2.dp)
            }
        }
        if ((state.usesButtons || permissionGranted && state.cameraError == null) && state.match.phase !in listOf(Phase.COUNTDOWN, Phase.RESULT, Phase.FINISHED)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                val title = when {
                    state.match.phase == Phase.COUNTDOWN -> s.getReady
                    state.match.phase == Phase.CAPTURING && !recognized -> s.showGesture
                    state.match.phase in listOf(Phase.RESULT, Phase.FINISHED) -> s.accepted
                    recognized -> state.stable.move!!.title
                    state.frame.points.isNotEmpty() -> s.oneOfThree
                    state.usesButtons -> s.selectGesture
                    else -> s.handInFrame
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Snow, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (recognized && !state.usesButtons) Text(
                        if (state.frame.method == RecognitionMethod.HAND_SHAPE) s.byShape else "${(state.frame.confidence * 100).toInt()}%",
                        color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        state.match.phase == Phase.CAPTURING -> s.remaining((state.match.captureRemainingMs + 999) / 1000)
                        state.match.phase == Phase.COUNTDOWN -> s.afterCountdown
                        state.usesButtons -> s.buttonsHint
                        state.stable.ready -> s.gestureReady
                        recognized -> s.holdStill
                        else -> s.lightingHint
                    }, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                ProgressLine(if (running && state.match.phase == Phase.COUNTDOWN) 0f else state.stable.progress, color)
            }
        }
    }
}

@Composable
private fun Controls(
    state: GameUiState,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onManualMove: (Move) -> Unit,
    onBotMode: (BotMode) -> Unit,
    compact: Boolean,
) {
    val s = LocalAppStrings.current
    val match = state.match
    val running = match.phase == Phase.COUNTDOWN || match.phase == Phase.CAPTURING
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        run {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Move.entries.forEach { move ->
                    val selected = state.manualMove == move
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (selected) Lime.copy(alpha = .14f) else Panel)
                            .border(1.dp, if (selected) Lime else Edge, RoundedCornerShape(14.dp))
                            .clickable(enabled = state.usesButtons && !state.paused) { onManualMove(move) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(move.symbol, fontSize = 23.sp, fontFamily = gestureFont())
                        Text(move.title, color = if (selected) Lime else Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Score(s.you, match.playerScore, Lime, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.firstToThree, color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Text("${match.playerScore} : ${match.botScore}", fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(s.round(match.rounds.size + if (match.phase in listOf(Phase.RESULT, Phase.FINISHED)) 0 else 1), color = Muted, fontSize = 10.sp)
            }
            Score(s.bot, match.botScore, Cyan, Modifier.weight(1f))
        }
        if (match.phase in listOf(Phase.RESULT, Phase.FINISHED)) {
            ResultCard(match)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.opponent, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                BotMode.entries.forEach { mode ->
                    val selected = state.botMode == mode
                    Text(mode.title, color = if (selected) Lime else Muted, fontSize = 11.sp,
                        modifier = Modifier.clip(CircleShape).background(if (selected) Lime.copy(alpha = .1f) else Panel)
                            .clickable(enabled = !running) { onBotMode(mode) }.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
            if (state.message != null) {
                Text(s.message(state.message!!), color = Warm, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Button(
            onClick = if (match.phase == Phase.FINISHED) onRestart else onStart,
            enabled = !state.history.loading && !state.paused && !running &&
                (match.matchOver || state.usesButtons || state.cameraReady && state.cameraError == null),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink, disabledContainerColor = Edge, disabledContentColor = Muted),
        ) {
            Text(when (match.phase) {
                Phase.READY -> if (match.rounds.isEmpty()) s.start else s.continueRound
                Phase.COUNTDOWN -> s.getReadyButton
                Phase.CAPTURING -> s.showGestureButton
                Phase.RESULT -> s.nextRound
                Phase.FINISHED -> s.playAgain
            }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(gestureText(s.beatsCaption), color = Muted, fontSize = 11.sp)
        }
        if (match.rounds.isNotEmpty() && match.phase != Phase.FINISHED) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.recentRounds, color = Muted, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                match.rounds.takeLast(6).forEach { round ->
                    val color = when (round.outcome) { Outcome.WIN -> Lime; Outcome.LOSS -> Rose; Outcome.DRAW -> Muted }
                    Text(when (round.outcome) { Outcome.WIN -> "+"; Outcome.LOSS -> "−"; Outcome.DRAW -> "=" }, color = color, fontSize = 11.sp,
                        modifier = Modifier.padding(end = 5.dp).size(19.dp).background(color.copy(alpha = .12f), CircleShape), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                Text(s.reset, color = Muted, fontSize = 10.sp, modifier = Modifier.clickable(enabled = !running, onClick = onRestart).padding(4.dp))
            }
        }
    }
}

@Composable
private fun Score(label: String, score: Int, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i -> Box(Modifier.size(9.dp).background(if (i < score) color else Edge, CircleShape)) }
        }
    }
}

@Composable
private fun ResultCard(match: MatchState) {
    val s = LocalAppStrings.current
    val round = match.lastRound ?: return
    val color = when (round.outcome) { Outcome.WIN -> Lime; Outcome.LOSS -> Rose; Outcome.DRAW -> Cyan }
    val title = if (match.phase == Phase.FINISHED) {
        if (match.playerScore >= 3) s.matchWin else s.matchLoss
    } else when (round.outcome) { Outcome.WIN -> s.roundWin; Outcome.LOSS -> s.roundLoss; Outcome.DRAW -> s.draw }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .09f)).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(16.dp)).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(s.roundMoves(round), color = Muted, fontSize = 10.sp)
        }
        Text("${round.player.symbol}  ${round.bot.symbol}", fontSize = 25.sp, fontFamily = gestureFont())
    }
}

@Composable
private fun ProgressLine(progress: Float, color: Color) {
    val animated by animateFloatAsState(progress, label = "hold-progress")
    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
private fun HandOverlay(frame: RecognitionFrame, color: Color, modifier: Modifier) {
    val tipColor = Snow
    Canvas(modifier) {
        val scale = max(size.width / frame.width, size.height / frame.height)
        val dx = (size.width - frame.width * scale) / 2f
        val dy = (size.height - frame.height * scale) / 2f
        val points = frame.points.map { Offset(it.x * frame.width * scale + dx, it.y * frame.height * scale + dy) }
        handConnections.forEach { (a, b) ->
            if (a < points.size && b < points.size) {
                drawLine(color.copy(alpha = .15f), points[a], points[b], 10.dp.toPx(), StrokeCap.Round)
                drawLine(color.copy(alpha = .92f), points[a], points[b], 2.dp.toPx(), StrokeCap.Round)
            }
        }
        points.forEachIndexed { index, point ->
            drawCircle(color.copy(alpha = .18f), 8.dp.toPx(), point)
            drawCircle(if (index in listOf(4, 8, 12, 16, 20)) tipColor else color, if (index == 8) 4.dp.toPx() else 3.dp.toPx(), point)
        }
    }
}

@Composable
private fun AimCorners(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val line = 24.dp.toPx()
        val stroke = 2.dp.toPx()
        listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { corner ->
            val xSign = if (corner.x == 0f) 1 else -1
            val ySign = if (corner.y == 0f) 1 else -1
            drawLine(color, corner, corner + Offset(line * xSign, 0f), stroke, StrokeCap.Round)
            drawLine(color, corner, corner + Offset(0f, line * ySign), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun DemoBackdrop(move: Move?, showHand: Boolean, modifier: Modifier) {
    val gridColor = Cyan
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(Color(0xFF172C31), Color(0xFF0C141B))))) {
        val small = maxHeight < 260.dp
        val tiny = maxHeight < 150.dp
        Canvas(Modifier.fillMaxSize()) {
            val step = 28.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(gridColor.copy(alpha = .045f), Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(gridColor.copy(alpha = .045f), Offset(0f, y), Offset(size.width, y)); y += step }
            drawRoundRect(gridColor.copy(alpha = .12f), Offset(size.width * .18f, size.height * .18f), Size(size.width * .64f, size.height * .55f), CornerRadius(32.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
        if (showHand) Text(move?.symbol ?: "✋", fontSize = if (tiny) 36.sp else if (small) 56.sp else 100.sp, fontFamily = gestureFont(), modifier = Modifier.align(Alignment.Center).offset(y = if (tiny) 14.dp else if (small) (-24).dp else (-14).dp))
    }
}

@Composable
private fun gestureFont(): FontFamily {
    val font = Font(Res.font.gesture_symbols)
    return remember(font) { FontFamily(font) }
}

@Composable
private fun gestureText(text: String): AnnotatedString {
    val symbols = gestureFont()
    return remember(text, symbols) {
        buildAnnotatedString {
            text.forEach { char ->
                if (char in "✊✋✌") withStyle(SpanStyle(fontFamily = symbols)) { append(char) }
                else append(char)
            }
        }
    }
}
