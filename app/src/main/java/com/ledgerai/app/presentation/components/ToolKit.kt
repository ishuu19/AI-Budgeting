package com.ledgerai.app.presentation.components

import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ledgerai.app.data.voice.AndroidOnDeviceStt

/**
 * A small round icon button for tool rows. 44dp to tap, a 22dp icon, so a row of five still fits
 * and does not look like a toolbar from another app.
 */
@Composable
fun LToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(L.Target)
            .clip(CircleShape)
            .background(if (active) L.BoxDeep else androidx.compose.ui.graphics.Color.Transparent)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) L.Ink else L.InkMuted, modifier = Modifier.size(24.dp))
    }
}

class VoiceInput(val listening: Boolean, val toggle: () -> Unit)

/**
 * Dictate into a field. Tap to start, tap again to stop; the words stream into [onText].
 * This is for filling one box, not the Home chat. The microphone permission is asked for on first use.
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): VoiceInput {
    val context = LocalContext.current
    val stt = remember { AndroidOnDeviceStt(context) }
    var listening by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onText)
    DisposableEffect(stt) { onDispose { stt.destroy() } }

    fun start() {
        listening = true
        stt.start(object : AndroidOnDeviceStt.Listener {
            override fun onPartial(text: String) = latest(text)
            override fun onLevel(level: Float) = Unit
            override fun onFinal(text: String) {
                latest(text)
                listening = false
                stt.destroy()
            }

            override fun onError(message: String, permission: Boolean) {
                listening = false
                stt.destroy()
            }
        }, "en-US")
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }
    return VoiceInput(listening) {
        when {
            listening -> stt.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search toolbar: the one search + mic + tools row every tab uses.

/** The pill search box. One size and style everywhere. */
@Composable
fun LSearchBox(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    Box(
        modifier
            .height(L.Mic)
            .clip(L.RadiusPill)
            .background(L.Box)
            .border(1.dp, L.Line, L.RadiusPill)
            .padding(horizontal = L.Space.x16),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            androidx.compose.material3.Text(hint, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = L.InkMuted, maxLines = 1)
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(color = L.Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(L.Accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Resting: Search, a small gap, the mic, then that tab's own tools, packed to the start.
 * Searching: the row becomes the search box with a mic (and an optional action such as Check).
 * The mic dictates into the box. It lives above the scrolling list so the keyboard never makes the list jump.
 */
@Composable
fun LSearchToolbar(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    submitLabel: String? = null,
    onSubmit: () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val voice = rememberVoiceInput { onQuery(it) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }

    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = L.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        if (searching && showSearch) {
            LToolButton(androidx.compose.material.icons.Icons.Filled.Close, "Close search", { onSearching(false); onQuery("") })
            LSearchBox(query, onQuery, hint, Modifier.weight(1f).focusRequester(focus), onDone = onSubmit)
            if (submitLabel != null) {
                LToolButton(Icons.Filled.Check, submitLabel, onSubmit, enabled = query.isNotBlank())
            }
            LToolButton(Icons.Filled.KeyboardVoice, if (voice.listening) "Stop" else "Dictate", voice.toggle, active = voice.listening)
        } else {
            if (showSearch) {
                LToolButton(androidx.compose.material.icons.Icons.Filled.Search, "Search", { onSearching(true) })
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            }
            LToolButton(
                Icons.Filled.KeyboardVoice,
                "Dictate",
                { if (showSearch) onSearching(true); voice.toggle() },
                active = voice.listening,
            )
            actions()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile button: the same avatar on every tab. It opens the profile and settings.

class ProfileUi(val initial: String, val onOpen: () -> Unit)

val LocalProfile = androidx.compose.runtime.staticCompositionLocalOf<ProfileUi?> { null }

@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val profile = LocalProfile.current ?: return
    Box(
        modifier
            .size(L.Target)
            .semantics { role = Role.Button; contentDescription = "Profile and settings" }
            .clickable(onClick = profile.onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(L.BoxDeep),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                profile.initial,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = L.Ink,
            )
        }
    }
}
