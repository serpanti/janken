package dev.janken.web

import dev.janken.prototype.mvi.*

private fun readSettings(): String? = js("{ try { return localStorage.getItem('dev.janken.settings.v1'); } catch (_) { return null; } }")
private fun writeSettings(value: String): Unit = js("{ try { localStorage.setItem('dev.janken.settings.v1', value); } catch (_) {} }")

object WebSettingsStorage : SettingsStorage {
    override fun load() = AppSettings.decode(readSettings())
    override fun save(settings: AppSettings) = writeSettings(settings.encode())
}
