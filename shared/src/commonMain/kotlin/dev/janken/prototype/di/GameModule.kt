package dev.janken.prototype.di

import co.touchlab.kermit.Logger
import dev.janken.prototype.game.MatchHistoryRepository
import dev.janken.prototype.mvi.AppSettings
import dev.janken.prototype.mvi.GameClock
import dev.janken.prototype.mvi.GameSnapshot
import dev.janken.prototype.mvi.GameStore
import dev.janken.prototype.mvi.SettingsStorage
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

fun gameModule(clock: GameClock, settings: SettingsStorage, openHistory: () -> MatchHistoryRepository) = module {
    single { clock }
    single { settings }
    single { openHistory() }
    single { GameStoreFactory(get(), get(), openHistory = { get() }) }
}

class GameStoreFactory internal constructor(
    private val clock: GameClock,
    private val settings: SettingsStorage,
    private val openHistory: () -> MatchHistoryRepository,
) {
    fun create(scope: CoroutineScope, initial: GameSnapshot? = null): GameStore {
        val snapshot = initial ?: GameSnapshot(settings = loadSettings())
        return GameStore(scope, clock, initial = snapshot, settingsStorage = settings, openHistory = openHistory)
    }

    private fun loadSettings(): AppSettings = runCatching { settings.load() }.getOrElse { error ->
        Logger.w(error, tag = "Janken") { "Could not load settings" }
        AppSettings()
    }
}
