package dev.janken.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppModal(title: String, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f))
        .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }.safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth()
            .heightIn(max = (maxHeight - 40.dp).coerceAtLeast(160.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { onDismiss(); true } else false }
            .semantics { paneTitle = title; isTraversalGroup = true },
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
            }
        }
    }
}
