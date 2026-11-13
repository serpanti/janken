package dev.janken.prototype

import android.content.Context
import dev.janken.prototype.mvi.*

class AndroidSettingsStorage(context: Context) : SettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences("janken_settings", Context.MODE_PRIVATE)
    fun exists() = preferences.contains("settings")
    override fun load() = AppSettings.decode(preferences.getString("settings", null))
    override fun save(settings: AppSettings) { preferences.edit().putString("settings", settings.encode()).apply() }
}
