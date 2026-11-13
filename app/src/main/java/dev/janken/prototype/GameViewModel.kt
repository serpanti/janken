package dev.janken.prototype

import android.os.SystemClock
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.janken.prototype.game.*
import dev.janken.prototype.mvi.*
import kotlinx.coroutines.launch

class GameViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val preferences = AndroidSettingsStorage(application)
    private val restored = saved.get<ArrayList<String>>("rounds").orEmpty().mapNotNull { record ->
        runCatching { record.split(":").let { Round(Move.valueOf(it[0]), Move.valueOf(it[1])) } }.getOrNull()
    }
    private val store = GameStore(viewModelScope, GameClock { SystemClock.uptimeMillis() }, initial = GameSnapshot(
        rounds = restored,
        settings = if (preferences.exists()) preferences.load() else AppSettings(
            inputMethod = if (saved["demo"] ?: false) InputMethod.BUTTONS else InputMethod.CAMERA,
            botMode = runCatching { BotMode.valueOf(saved["botMode"] ?: "RANDOM") }.getOrDefault(BotMode.RANDOM),
            skeleton = saved["skeleton"] ?: true,
        ),
    ))
    val ui = store.state
    val effects = store.effects

    init {
        viewModelScope.launch {
            store.snapshot.collect { snapshot ->
                saved["rounds"] = ArrayList(snapshot.rounds.map { "${it.player.name}:${it.bot.name}" })
                if (snapshot.settings != preferences.load() || !preferences.exists()) preferences.save(snapshot.settings)
            }
        }
    }

    fun accept(intent: GameIntent) = store.accept(intent)
    override fun onCleared() { store.close() }
}
