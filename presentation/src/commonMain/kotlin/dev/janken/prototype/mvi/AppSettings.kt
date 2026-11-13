package dev.janken.prototype.mvi

import dev.janken.prototype.game.BotMode

enum class InputMethod { CAMERA, BUTTONS }
enum class ThemeMode { SYSTEM, DARK, LIGHT;
    fun isDark(systemDark: Boolean) = this == DARK || this == SYSTEM && systemDark
}
enum class AppLanguage { RU, EN }

data class AppSettings(
    val inputMethod: InputMethod = InputMethod.CAMERA,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU,
    val botMode: BotMode = BotMode.RANDOM,
    val skeleton: Boolean = true,
) {
    fun encode() = listOf("inputMethod=${inputMethod.name}", "theme=${theme.name}", "language=${language.name}",
        "botMode=${botMode.name}", "skeleton=$skeleton").joinToString("\n")
    companion object {
        fun decode(text: String?): AppSettings {
            val values = text.orEmpty().lineSequence().mapNotNull {
                it.split('=', limit = 2).takeIf { parts -> parts.size == 2 }?.let { parts -> parts[0] to parts[1] }
            }.toMap()
            return AppSettings(
                inputMethod = runCatching { InputMethod.valueOf(values["inputMethod"].orEmpty()) }.getOrDefault(InputMethod.CAMERA),
                theme = runCatching { ThemeMode.valueOf(values["theme"].orEmpty()) }.getOrDefault(ThemeMode.SYSTEM),
                language = runCatching { AppLanguage.valueOf(values["language"].orEmpty()) }.getOrDefault(AppLanguage.RU),
                botMode = runCatching { BotMode.valueOf(values["botMode"].orEmpty()) }.getOrDefault(BotMode.RANDOM),
                skeleton = values["skeleton"]?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}

interface SettingsStorage {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}
class MemorySettingsStorage(private var settings: AppSettings = AppSettings()) : SettingsStorage {
    override fun load() = settings
    override fun save(settings: AppSettings) { this.settings = settings }
}
