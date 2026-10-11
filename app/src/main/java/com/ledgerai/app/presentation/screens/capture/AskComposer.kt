package com.ledgerai.app.presentation.screens.capture

import android.net.Uri
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.AsyncUriThumb
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LMicButton
import com.ledgerai.app.presentation.components.LSnackbar
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton

/** Short label on the chip, full prompt sent to the assistant. One list for Home and capture. */
internal data class Starter(val label: String, val prompt: String)

internal val Starters = listOf(
    Starter("Cook", "What can I cook today?"),
    Starter("Outfit", "What should I wear today?"),
    Starter("Log lunch", "I spent 12 on lunch"),
    Starter("Remind me", "Remind me to call mum at 6"),
)

/**
 * The one composer: pill field, camera, gallery, and send.
 * Pass showMic = false on Home. The tab-bar mic is the one that stays.
 */
@Composable
internal fun AskComposer(
    text: String,
    onText: (String) -> Unit,
    images: List<Uri>,
    listening: Boolean,
    canSend: Boolean,
    notice: String?,
    showStarters: Boolean,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onStarter: (String) -> Unit,
    onManual: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showBusy: Boolean = false,
    showMic: Boolean = true,
    placeholder: String = "Ask anything",
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showStarters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Starters.forEach { starter ->
                    LChip(starter.label, selected = false, onClick = { onStarter(starter.prompt) })
                }
            }
        }
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { uri -> PhotoThumb(uri) { onRemoveImage(uri) } }
            }
        }
        notice?.let { LSnackbar(it) }
        AskField(text, onText, listening, canSend, showMic, onMic, onSend, onCamera, onGallery, placeholder)
        if (onManual != null) LTextLink("Add manually", onManual, Modifier.align(Alignment.CenterHorizontally))
        if (showBusy) TypingBubble()
    }
}

@Composable
private fun AskField(
    text: String,
    onText: (String) -> Unit,
    listening: Boolean,
    canSend: Boolean,
    showMic: Boolean,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    placeholder: String,
) {
    val sendMode = canSend && !listening
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(L.Radius))
                .background(L.Box)
                .border(1.dp, L.Line, RoundedCornerShape(L.Radius))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                if (text.isEmpty()) {
                    Text(
                        if (listening) "Listening…" else placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = L.InkMuted,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
                    cursorBrush = SolidColor(L.Accent),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LToolButton(Icons.Filled.PhotoCamera, "Camera", onCamera)
            LToolButton(Icons.Filled.PhotoLibrary, "Gallery", onGallery)
        }
        when {
            sendMode -> ComposerAction(Icons.Filled.Send, "Send", L.Highlight, L.OnHighlight, onSend)
            listening -> ComposerAction(Icons.Filled.Stop, "Stop listening", L.Danger, L.OnAccent, onMic)
            showMic -> LMicButton(onClick = onMic, label = "Speak")
        }
    }
}

@Composable
private fun ComposerAction(
    icon: ImageVector,
    label: String,
    fill: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(L.Mic)
            .clip(CircleShape)
            .background(fill)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PhotoThumb(uri: Uri, onRemove: () -> Unit) {
    Box(Modifier.size(64.dp)) {
        AsyncUriThumb(uri, Modifier.size(64.dp).clip(RoundedCornerShape(L.RadiusSm)))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .semantics { role = Role.Button; contentDescription = "Remove photo" }
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier.padding(4.dp).size(24.dp).clip(CircleShape).background(L.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = L.Page, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val BotShape = RoundedCornerShape(topStart = L.Radius, topEnd = L.Radius, bottomStart = 8.dp, bottomEnd = L.Radius)

/** One typing indicator. Replaces the separate "looking", "reading" and "thinking" lines. */
@Composable
internal fun TypingBubble(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
        label = "phase",
    )
    val active = phase.toInt() % 3
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .widthIn(max = maxWidth * 0.85f)
                .semantics { contentDescription = "Working" }
                .clip(BotShape)
                .background(L.Box)
                .border(1.dp, L.Line, BotShape)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (index == active) L.OnBox else L.OnBoxMuted),
                )
            }
        }
    }
}
