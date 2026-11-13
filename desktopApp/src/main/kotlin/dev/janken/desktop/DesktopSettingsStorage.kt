package dev.janken.desktop

import dev.janken.prototype.mvi.*
import java.nio.file.Files
import java.nio.file.Path

class DesktopSettingsStorage : SettingsStorage {
    private val directory: Path = when {
        System.getProperty("os.name").startsWith("Windows") -> Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Janken")
        System.getProperty("os.name").startsWith("Mac") -> Path.of(System.getProperty("user.home"), "Library", "Application Support", "Janken")
        else -> Path.of(System.getenv("XDG_CONFIG_HOME") ?: Path.of(System.getProperty("user.home"), ".config").toString(), "janken")
    }
    private val file = directory.resolve("settings.txt")
    override fun load() = runCatching { AppSettings.decode(Files.readString(file)) }.getOrDefault(AppSettings())
    override fun save(settings: AppSettings) { Files.createDirectories(directory); Files.writeString(file, settings.encode()) }
}
