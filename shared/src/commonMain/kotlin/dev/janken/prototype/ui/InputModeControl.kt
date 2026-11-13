package dev.janken.prototype.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.janken.prototype.mvi.InputMethod

@Composable
internal fun InputModeControl(method: InputMethod, cameraSupported: Boolean, onSelect: (InputMethod) -> Unit) {
    val strings = LocalAppStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            InputMethod.entries.forEachIndexed { index, input ->
                val label = if (input == InputMethod.CAMERA) strings.cameraTab else strings.buttonsTab
                SegmentedButton(selected = method == input, onClick = { onSelect(input) },
                    enabled = input == InputMethod.BUTTONS || cameraSupported,
                    modifier = Modifier.semantics { contentDescription = label },
                    shape = SegmentedButtonDefaults.itemShape(index, InputMethod.entries.size)) {
                    Text(label)
                }
            }
        }
        Text(strings.swipeInputHint, Modifier.fillMaxWidth().padding(top = 5.dp),
            color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Modifier.inputSwipes(method: InputMethod, enabled: Boolean, onSelect: (InputMethod) -> Unit): Modifier {
    val latestMethod by rememberUpdatedState(method)
    val latestSelect by rememberUpdatedState(onSelect)
    val distance = with(LocalDensity.current) { 56.dp.toPx() }
    return pointerInput(enabled, distance) {
        if (!enabled) return@pointerInput
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f }, onDragCancel = { dragged = 0f },
            onDragEnd = {
                when {
                    dragged <= -distance && latestMethod == InputMethod.CAMERA -> latestSelect(InputMethod.BUTTONS)
                    dragged >= distance && latestMethod == InputMethod.BUTTONS -> latestSelect(InputMethod.CAMERA)
                }
                dragged = 0f
            },
            onHorizontalDrag = { change, amount -> change.consume(); dragged += amount },
        )
    }
}
