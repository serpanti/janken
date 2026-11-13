package dev.janken.prototype.ui

import androidx.compose.runtime.Composable
import dev.janken.prototype.game.BotMode
import dev.janken.prototype.game.Move

val Move.title: String @Composable get() = LocalAppStrings.current.move(this)
val Move.symbol: String get() = when (this) {
    Move.ROCK -> "\u270A"; Move.PAPER -> "\u270B"; Move.SCISSORS -> "\u270C"
}
val BotMode.title: String @Composable get() = LocalAppStrings.current.botMode(this)
