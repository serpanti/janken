package dev.janken.prototype.game

import kotlin.random.Random

/** Sees only past rounds; the selected move is committed at StartRound. */
fun chooseBot(rounds: List<Round>, mode: BotMode, random: Random): Move {
    val history = rounds.map { it.player }
    if (mode == BotMode.RANDOM || history.size < 3 || random.nextFloat() < .35f) return Move.entries.random(random)
    val counts = history.takeLast(12).groupingBy { it }.eachCount()
    val mostCommon = counts.values.max()
    return counts.filterValues { it == mostCommon }.keys.toList().random(random).counter()
}
