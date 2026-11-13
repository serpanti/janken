package dev.janken.prototype.mvi

import dev.janken.prototype.game.BotMode
import kotlin.test.*

class AppSettingsTest {
    @Test fun choicesRoundTripAcrossPlatformStorageFormats() {
        for (theme in ThemeMode.entries) for (language in AppLanguage.entries) for (input in InputMethod.entries) {
            val settings = AppSettings(input, theme, language, BotMode.ADAPTIVE, false)
            assertEquals(settings, AppSettings.decode(settings.encode()))
        }
    }
    @Test fun corruptOrMissingPreferencesKeepValidChoicesAndUseSafeDefaults() {
        assertEquals(AppSettings(), AppSettings.decode(null))
        val loaded = AppSettings.decode("theme=obsolete\nlanguage=EN\ninputMethod=garbage\nskeleton=invalid")
        assertEquals(ThemeMode.SYSTEM, loaded.theme)
        assertEquals(AppLanguage.EN, loaded.language)
        assertEquals(InputMethod.CAMERA, loaded.inputMethod)
        assertTrue(loaded.skeleton)
    }
    @Test fun systemThemeTracksSystemOnlyWhenChosen() {
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertTrue(ThemeMode.DARK.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true))
    }
}
