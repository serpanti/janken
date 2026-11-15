package dev.janken.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.janken.prototype.mvi.MatchHistoryUiState

@Composable
internal fun MatchHistoryDialog(history: MatchHistoryUiState, onClear: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalAppStrings.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val dismiss = { if (confirming) confirming = false else onDismiss() }
    val clearColors = ButtonDefaults.textButtonColors(contentColor = Rose)
    AppModal(if (confirming) s.clearHistoryTitle else s.matchHistory, dismiss, actions = {
        if (confirming) {
            TextButton(onClick = dismiss) { Text(s.cancel) }
            TextButton(onClick = {
                confirming = false
                onClear()
            }, colors = clearColors) { Text(s.clear) }
        } else {
            TextButton(
                onClick = { confirming = true },
                enabled = history.completed.isNotEmpty() && !history.clearing,
                colors = clearColors,
            ) { Text(s.clearHistory) }
            TextButton(onClick = onDismiss) { Text(s.done) }
        }
    }) {
        if (confirming) {
            Text(s.clearHistoryMessage)
            return@AppModal
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (history.loading) Text(s.loadingHistory, color = Muted)
            if (history.storageError) Text(s.historyError, color = Warm)
            if (!history.loading && !history.storageError && history.completed.isEmpty()) {
                Text(s.noMatches, color = Muted)
            }
            history.completed.forEachIndexed { index, match ->
                if (index > 0) HorizontalDivider(color = Edge)
                val won = match.playerScore > match.botScore
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (won) s.win else s.loss, color = if (won) Lime else Rose)
                    Text("${match.playerScore} : ${match.botScore}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
