package dev.janken.prototype.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.janken.prototype.mvi.*

@Composable
internal fun SettingsDialog(settings: AppSettings, cameraSupported: Boolean, onIntent: (GameIntent) -> Unit,
    onRules: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalAppStrings.current
    AppModal(s.settings, onDismiss, actions = { TextButton(onClick = onDismiss) { Text(s.done) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.theme, fontWeight = FontWeight.Bold)
            ThemeMode.entries.forEach { mode -> SettingChoice(s.theme(mode), settings.theme == mode) { onIntent(GameIntent.SetTheme(mode)) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(s.languageLabel, fontWeight = FontWeight.Bold)
            AppLanguage.entries.forEach { language ->
                SettingChoice(if (language == AppLanguage.RU) "Русский" else "English", settings.language == language) { onIntent(GameIntent.SetLanguage(language)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.skeleton, Modifier.weight(1f))
                Switch(checked = settings.skeleton, enabled = cameraSupported,
                    onCheckedChange = { onIntent(GameIntent.SetSkeleton(it)) }, modifier = Modifier.semantics { contentDescription = s.skeleton })
            }
            TextButton(onClick = onRules) { Text(s.rules) }
            Text(s.privacy, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
