package dev.janken.prototype.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.janken.prototype.game.*
import dev.janken.prototype.mvi.*

val LocalAppStrings = staticCompositionLocalOf { AppStrings(AppLanguage.RU) }

class AppStrings(val language: AppLanguage) {
    private fun t(ru: String, en: String) = if (language == AppLanguage.RU) ru else en
    val subtitle get() = t("камень, ножницы, бумага", "rock, paper, scissors")
    val settings get() = t("Настройки", "Settings")
    val rules get() = t("Правила игры", "How to play")
    val done get() = t("Готово", "Done")
    val input get() = t("Способ ввода", "Input method")
    val cameraTab get() = t("Камера", "Camera")
    val buttonsTab get() = t("Кнопки", "Buttons")
    val swipeInputHint get() = t("Смахните по превью, чтобы сменить ввод", "Swipe the preview to switch input")
    val cameraInput get() = t("Жесты с камеры", "Camera gestures")
    val buttonInput get() = t("Кнопки на экране", "On-screen buttons")
    val inputHint get() = t("Способ ввода можно менять в ходе игры. Счёт и раунд сохраняются.", "Change input during a game. Your score and current round are kept.")
    val theme get() = t("Тема", "Theme")
    val languageLabel get() = t("Язык", "Language")
    val skeleton get() = t("Скелет кисти", "Hand skeleton")
    val privacy get() = t("Распознавание работает на устройстве. Кадры не записываются и не отправляются на сервер.", "Recognition runs on your device. Camera frames are not recorded or uploaded.")
    val rulesBody get() = t("Матч до трёх побед. После отсчёта покажите один жест и удерживайте его около 0,35 секунды. На ход есть 5 секунд.", "First to three wins. After the countdown, hold one gesture for about 0.35 seconds. You have 5 seconds to make a move.")
    val beatsRules get() = t("✊  Камень бьёт ножницы\n✌  Ножницы режут бумагу\n✋  Бумага накрывает камень", "✊  Rock beats scissors\n✌  Scissors beat paper\n✋  Paper beats rock")
    val shapeRules get() = t("Бумага: четыре выпрямленных пальца, вместе или врозь. Ножницы: указательный и средний выпрямлены, остальные согнуты. Большой палец держите удобно. Кисть должна быть видна целиком.", "Paper: extend four fingers, together or apart. Scissors: extend index and middle fingers, curl the other two. Keep your thumb comfortable and your whole hand visible.")
    val botRules get() = t("Бот выбирает ход до отсчёта. Адаптивный бот учитывает только предыдущие раунды и начинает учиться после трёх ходов.", "The bot commits before the countdown. The adaptive bot uses only past rounds and starts learning after three moves.")
    val cameraPreview get() = t("Превью камеры", "Camera preview")
    val cameraStatus get() = t("КАМЕРА · НА УСТРОЙСТВЕ", "CAMERA · ON DEVICE")
    val buttonsStatus get() = t("ВВОД КНОПКАМИ", "BUTTON INPUT")
    val playWithHand get() = t("Играй движением руки", "Play with hand gestures")
    val grantHint get() = t("Разрешите камеру, чтобы увидеть руку и её цифровой скелет.", "Allow camera access to see your hand and its digital skeleton.")
    val allowCamera get() = t("Разрешить камеру", "Allow camera")
    val useButtons get() = t("Использовать кнопки", "Use buttons")
    val cameraUnavailable get() = t("Камера недоступна", "Camera unavailable")
    val retry get() = t("Повторить", "Retry")
    val paused get() = t("Раунд на паузе", "Round paused")
    val waitingCamera get() = t("Ожидаем готовности камеры", "Waiting for the camera")
    val pausedHint get() = t("Закройте окно, чтобы продолжить", "Close the dialog to continue")
    val committed get() = t("БОТ УЖЕ ВЫБРАЛ ХОД", "THE BOT HAS COMMITTED")
    val getReady get() = t("Приготовьтесь", "Get ready")
    val showGesture get() = t("Покажите жест", "Show a gesture")
    val accepted get() = t("Ход принят", "Move accepted")
    val oneOfThree get() = t("Покажите один из трёх жестов", "Show one of the three gestures")
    val selectGesture get() = t("Выберите жест", "Choose a move")
    val handInFrame get() = t("Поместите кисть в кадр", "Place your hand in the frame")
    val byShape get() = t("По форме", "By shape")
    val afterCountdown get() = t("Покажите жест после отсчёта", "Show your gesture after the countdown")
    val buttonsHint get() = t("Кнопки ниже · камера выключена", "Use the buttons below · camera is off")
    val gestureReady get() = t("Жест распознан · можно начинать раунд", "Gesture recognized · ready to play")
    val holdStill get() = t("Удерживайте руку неподвижно", "Hold your hand still")
    val lightingHint get() = t("Кисть целиком в кадре · при хорошем свете", "Keep your whole hand visible · use good lighting")
    val you get() = t("ВЫ", "YOU")
    val bot get() = t("БОТ", "BOT")
    val firstToThree get() = t("ДО 3 ПОБЕД", "FIRST TO 3")
    val opponent get() = t("Соперник", "Opponent")
    val start get() = t("Начать раунд", "Start round")
    val continueRound get() = t("Продолжить", "Continue")
    val getReadyButton get() = t("Приготовьтесь…", "Get ready…")
    val showGestureButton get() = t("Покажите жест…", "Show a gesture…")
    val nextRound get() = t("Следующий раунд", "Next round")
    val playAgain get() = t("Сыграть ещё матч", "Play another match")
    val beatsCaption get() = t("✊  бьёт  ✌     ·     ✌  бьёт  ✋     ·     ✋  бьёт  ✊", "✊  beats  ✌     ·     ✌  beats  ✋     ·     ✋  beats  ✊")
    val history get() = t("История", "History")
    val matchHistory get() = t("История матчей", "Match history")
    val clearHistory get() = t("Очистить историю", "Clear history")
    val clearHistoryTitle get() = t("Очистить историю?", "Clear history?")
    val clearHistoryMessage get() = t(
        "Результаты завершённых матчей будут удалены. Счёт незавершённого матча сохранится.",
        "Completed matches will be removed. Your current match can still be resumed.",
    )
    val clear get() = t("Очистить", "Clear")
    val cancel get() = t("Отмена", "Cancel")
    val recentRounds get() = t("Раунды", "Rounds")
    val noMatches get() = t("Завершённые матчи появятся здесь.", "Completed matches will appear here.")
    val loadingHistory get() = t("Загружаем историю…", "Loading history…")
    val historyError get() = t("Не удалось прочитать или сохранить историю матчей.", "Could not read or save match history.")
    val win get() = t("Победа", "Win")
    val loss get() = t("Поражение", "Loss")
    val reset get() = t("Сбросить", "Reset")
    val matchWin get() = t("Матч за вами!", "You win the match!")
    val matchLoss get() = t("Бот выиграл матч", "Bot wins the match")
    val roundWin get() = t("Ваш раунд!", "You win this round!")
    val roundLoss get() = t("Раунд за ботом", "Bot wins this round")
    val draw get() = t("Ничья — ещё раз", "Draw — try again")
    fun round(number: Int) = t("Раунд $number", "Round $number")
    fun remaining(seconds: Long) = t("Удерживайте · осталось $seconds с", "Hold · $seconds s remaining")
    fun inputSummary(method: InputMethod) = t("Ввод: ", "Input: ") + if (method == InputMethod.CAMERA) t("камера", "camera") else t("кнопки", "buttons")
    fun move(move: Move) = when (move) { Move.ROCK -> t("Камень", "Rock"); Move.PAPER -> t("Бумага", "Paper"); Move.SCISSORS -> t("Ножницы", "Scissors") }
    fun botMode(mode: BotMode) = if (mode == BotMode.RANDOM) t("Случайный", "Random") else t("Адаптивный", "Adaptive")
    fun theme(mode: ThemeMode) = when (mode) { ThemeMode.SYSTEM -> t("Системная", "System"); ThemeMode.DARK -> t("Тёмная", "Dark"); ThemeMode.LIGHT -> t("Светлая", "Light") }
    fun roundMoves(round: Round) = t("Вы: ${move(round.player).lowercase()} · бот: ${move(round.bot).lowercase()}", "You: ${move(round.player).lowercase()} · bot: ${move(round.bot).lowercase()}")
    fun message(message: GameMessage) = when (message) { GameMessage.TIMED_OUT -> t("Жест не определён. Повторите раунд.", "No gesture detected. Try the round again.") }
    fun cameraError(raw: String): String {
        if (language == AppLanguage.RU) return raw
        return when {
            "запрещ" in raw || "Разрешите" in raw -> "Camera access denied. Allow access in your device or browser settings, then retry."
            "HTTPS" in raw || "localhost" in raw -> "Camera access requires HTTPS or localhost."
            "модель" in raw || "модел" in raw || "компонент" in raw || "файлы" in raw -> "Could not start recognition. Check the application files and retry."
            "кадр" in raw || "данных" in raw || "изображение" in raw -> "Could not process a camera frame. Retry or use buttons."
            "не найдена" in raw -> "No camera found. Connect a camera or use buttons."
            else -> "Camera unavailable. Check access, close other camera apps and retry, or use buttons."
        }
    }
}
