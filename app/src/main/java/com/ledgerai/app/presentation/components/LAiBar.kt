package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one mic. 52dp, amber, navy icon. Hold callbacks cover the tab-bar press-and-hold.
 * The icon has no content description; the button semantics announce [label] once.
 */
@Composable
fun LMicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Speak",
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: (() -> Unit)? = null,
) {
    val gesture = if (onHoldStart != null) {
        Modifier.pointerInput(onClick, onHoldStart, onHoldEnd) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown()
                    val tap = withTimeoutOrNull(200) { waitForUpOrCancellation() }
                    if (tap != null) onClick()
                    else {
                        onHoldStart()
                        waitForUpOrCancellation()
                        onHoldEnd?.invoke()
                    }
                }
            }
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        modifier
            .size(L.Mic)
            .clip(CircleShape)
            .background(L.Highlight)
            .semantics { role = Role.Button; contentDescription = label }
            .then(gesture),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = L.OnHighlight, modifier = Modifier.size(24.dp))
    }
}

/**
 * The AI-first way to add anything on a list screen. Type or say one sentence; Home turns it into a
 * card to confirm. [hint] shows an example for this screen, such as "Dentist Friday at 3".
 * Manual forms stay on each screen as the second choice.
 */
@Composable
fun LAiBar(
    hint: String,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    onImage: (() -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank()
    val shape = RoundedCornerShape(L.Radius)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(L.Box)
            .border(1.dp, L.Line, shape)
            .padding(start = L.Space.x16, end = L.Space.x8, top = L.Space.x8, bottom = L.Space.x8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = L.Space.x8)) {
            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = L.InkMuted, maxLines = 1)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                cursorBrush = SolidColor(L.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) { onSend(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onImage != null) LToolButton(Icons.Filled.Image, "Upload image", onImage)
        if (canSend) {
            Box(
                Modifier
                    .size(L.Target)
                    .clip(CircleShape)
                    .background(L.Accent)
                    .semantics { role = Role.Button; contentDescription = "Send" }
                    .clickable { onSend(text.trim()); text = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = L.OnAccent, modifier = Modifier.size(24.dp))
            }
        } else {
            LMicButton(onClick = onMic)
        }
    }
}
