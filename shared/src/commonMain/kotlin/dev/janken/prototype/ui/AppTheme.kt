package dev.janken.prototype.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import dev.janken.prototype.mvi.*

internal data class GamePalette(val background: Color, val panel: Color, val edge: Color, val accent: Color,
    val cyan: Color, val muted: Color, val warm: Color, val rose: Color, val text: Color)
internal val DarkPalette = GamePalette(Color(0xFF0B0F12), Color(0xFF151C21), Color(0xFF2A353C), Color(0xFFADFF70),
    Color(0xFF78DBE7), Color(0xFF95A5AE), Color(0xFFFFC777), Color(0xFFFF8A91), Color(0xFFF0F4F5))
internal val LightPalette = GamePalette(Color(0xFFF4F7F5), Color(0xFFFFFFFF), Color(0xFFCFDCD4), Color(0xFF216D35),
    Color(0xFF006574), Color(0xFF52635A), Color(0xFF865300), Color(0xFFB32635), Color(0xFF15251B))
internal val LocalGamePalette = staticCompositionLocalOf { DarkPalette }

internal val Ink @Composable get() = LocalGamePalette.current.background
internal val Panel @Composable get() = LocalGamePalette.current.panel
internal val Edge @Composable get() = LocalGamePalette.current.edge
internal val Lime @Composable get() = LocalGamePalette.current.accent
internal val Cyan @Composable get() = LocalGamePalette.current.cyan
internal val Muted @Composable get() = LocalGamePalette.current.muted
internal val Warm @Composable get() = LocalGamePalette.current.warm
internal val Rose @Composable get() = LocalGamePalette.current.rose
internal val Snow @Composable get() = LocalGamePalette.current.text

private fun scheme(p: GamePalette) = if (p == DarkPalette) darkColorScheme(
    primary = p.accent, onPrimary = p.background, secondary = p.cyan, background = p.background,
    surface = p.panel, onSurface = p.text, onBackground = p.text, outline = p.edge,
    secondaryContainer = p.edge, onSecondaryContainer = p.text,
) else lightColorScheme(primary = p.accent, onPrimary = Color.White, secondary = p.cyan,
    background = p.background, surface = p.panel, onSurface = p.text, onBackground = p.text,
    outline = p.edge, secondaryContainer = Color(0xFFD9EFDD), onSecondaryContainer = p.text)

@Composable
fun JankenTheme(theme: ThemeMode = ThemeMode.SYSTEM, language: AppLanguage = AppLanguage.RU, content: @Composable () -> Unit) {
    val palette = if (theme.isDark(isSystemInDarkTheme())) DarkPalette else LightPalette
    val strings = remember(language) { AppStrings(language) }
    CompositionLocalProvider(LocalGamePalette provides palette, LocalAppStrings provides strings) {
        MaterialTheme(colorScheme = scheme(palette)) { Surface(color = palette.background, contentColor = palette.text, content = content) }
    }
}

/** Camera imagery keeps a readable dark overlay in either application theme. */
@Composable
internal fun CameraPalette(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGamePalette provides DarkPalette, LocalContentColor provides DarkPalette.text) {
        MaterialTheme(colorScheme = scheme(DarkPalette), content = content)
    }
}
