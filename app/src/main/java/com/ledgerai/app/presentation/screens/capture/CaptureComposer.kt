package com.ledgerai.app.presentation.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.capture.AskResult
import com.ledgerai.app.presentation.components.AsyncUriThumb
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LocalPhoto
import java.io.File

private val Prompts = listOf("What can I cook today?", "I have a party tonight", "What should I wear today?")

/**
 * The one input. Used in the Capture tab and in the floating quick-capture window.
 * Mic, camera and gallery all feed the same box; the AI decides what the input is.
 */
@Composable
fun CaptureComposer(
    vm: CaptureViewModel,
    modifier: Modifier = Modifier,
    autoMic: Boolean = false,
    autoCamera: Boolean = false,
    onManual: (() -> Unit)? = null,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.onCameraResult(it) }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CaptureViewModel.MAX_IMAGES)
    ) { vm.addImages(it) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(vm.newCameraUri())
    }

    fun toggleMic() {
        if (ui.listening) {
            vm.stopListening()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startListening()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(vm.newCameraUri())
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (autoMic) toggleMic() else if (autoCamera) openCamera()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = ui.text,
            onValueChange = vm::setText,
            placeholder = {
                Text(
                    if (ui.listening) "Listening…" else "Say, type or snap anything",
                    color = L.InkMuted,
                )
            },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(L.Radius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = L.Primary,
                unfocusedBorderColor = L.Line,
                focusedContainerColor = L.Box,
                unfocusedContainerColor = L.Box,
                cursorColor = L.Primary,
                focusedTextColor = L.Ink,
                unfocusedTextColor = L.Ink,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (ui.images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.images.forEach { uri -> Attachment(uri) { vm.removeImage(uri) } }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundAction(
                icon = if (ui.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                label = if (ui.listening) "Stop listening" else "Speak",
                filled = ui.listening,
                onClick = ::toggleMic,
            )
            RoundAction(Icons.Filled.PhotoCamera, "Take a photo", onClick = ::openCamera)
            RoundAction(
                Icons.Filled.PhotoLibrary, "Choose photos",
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Box(Modifier.weight(1f))
            val canSend = !ui.busy && (ui.text.isNotBlank() || ui.images.isNotEmpty())
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (canSend) L.Highlight else L.BoxDeep)
                    .semantics { role = Role.Button; contentDescription = "Send" }
                    .clickable(enabled = canSend, onClick = vm::submit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = if (canSend) L.Ink else L.InkMuted)
            }
        }

        if (ui.text.isBlank() && ui.images.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Prompts.forEach { p -> LChip(p, selected = false, onClick = { vm.quickAsk(p) }) }
            }
        }

        if (onManual != null) {
            Text(
                "Add manually",
                style = MaterialTheme.typography.labelLarge,
                color = L.Primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onManual).padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }

        ui.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
        if (ui.busy) Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)

        AnswerCards(ui.answer, vm)
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (filled) L.Primary else L.Box)
            .border(BorderStroke(1.dp, L.Line), CircleShape)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) L.OnPrimary else L.Primary)
    }
}

@Composable
private fun Attachment(uri: Uri, onRemove: () -> Unit) {
    Box(Modifier.size(72.dp)) {
        AsyncUriThumb(uri, Modifier.size(72.dp).clip(RoundedCornerShape(L.RadiusSm)))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(L.Ink.copy(alpha = 0.75f))
                .semantics { role = Role.Button; contentDescription = "Remove photo" }
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, contentDescription = null, tint = L.Page, modifier = Modifier.size(14.dp)) }
    }
}

/** The AI's reply to a meal or outfit question. Used by the composer and the chat. */
@Composable
internal fun AnswerCards(answer: AskResult?, vm: CaptureViewModel) {
        when (val answer = answer) {
            is AskResult.Meals -> answer.ideas.forEach { idea ->
                LCard {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium, color = L.OnBox)
                    Text(
                        listOfNotNull(idea.minutes?.let { "$it min" }, if (idea.missing.isEmpty()) "You have everything" else null).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = L.Gold,
                    )
                    if (idea.uses.isNotEmpty()) {
                        Text("From your pantry: ${idea.uses.joinToString()}", style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
                    }
                    if (idea.missing.isNotEmpty()) {
                        Text("You need: ${idea.missing.joinToString()}", style = MaterialTheme.typography.bodyMedium, color = L.Highlight)
                    }
                    idea.steps.forEachIndexed { i, step ->
                        Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = L.OnBox)
                    }
                }
            }
            is AskResult.Outfits -> answer.ideas.forEach { idea ->
                LCard {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium, color = L.OnBox)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        idea.itemIds.mapNotNull { answer.garments[it] }.forEach { g ->
                            Column(Modifier.size(width = 84.dp, height = 112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Only the picked garments load a photo, and only from this phone.
                                LocalPhoto(
                                    file = g.photoPath?.let { vm.thumb(it) } ?: File(""),
                                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(L.RadiusSm)),
                                )
                                Text(g.name, style = MaterialTheme.typography.labelMedium, color = L.OnBoxMuted, maxLines = 1)
                            }
                        }
                    }
                    if (idea.why.isNotBlank()) {
                        Text(idea.why, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
                    }
                }
            }
            is AskResult.Empty -> Text(answer.reason, style = MaterialTheme.typography.bodyMedium, color = L.Ink)
            AskResult.LogIt, null -> Unit
        }
}
