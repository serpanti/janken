package dev.janken.prototype

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.janken.prototype.di.GameStoreFactory
import dev.janken.prototype.game.*
import dev.janken.prototype.mvi.*

class GameViewModel(
    saved: SavedStateHandle,
    preferences: AndroidSettingsStorage,
    storeFactory: GameStoreFactory,
) : ViewModel() {
    private val store = storeFactory.create(viewModelScope, GameSnapshot(
        rounds = saved.get<ArrayList<String>>("rounds").orEmpty().mapNotNull { record ->
            runCatching {
                val (player, bot) = record.split(':', limit = 2)
                Round(Move.valueOf(player), Move.valueOf(bot))
            }.getOrNull()
        },
        settings = if (preferences.exists()) preferences.load() else AppSettings(
            inputMethod = if (saved["demo"] ?: false) InputMethod.BUTTONS else InputMethod.CAMERA,
            botMode = runCatching { BotMode.valueOf(saved["botMode"] ?: "RANDOM") }.getOrDefault(BotMode.RANDOM),
            skeleton = saved["skeleton"] ?: true,
        ),
    ))
    val ui = store.state
    val effects = store.effects

    init {
        if (!preferences.exists()) preferences.save(store.state.value.settings)
    }

    fun accept(intent: GameIntent) = store.accept(intent)
    override fun onCleared() { store.close() }
}
