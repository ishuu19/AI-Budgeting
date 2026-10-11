package com.ledgerai.app.presentation.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerai.app.data.capture.AskResult
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LProgress
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LocalPhoto
import java.io.File

/**
 * The one input. Used in the floating quick-capture window.
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AskComposer(
            text = ui.text,
            onText = vm::setText,
            images = ui.images,
            listening = ui.listening,
            canSend = !ui.busy && (ui.text.isNotBlank() || ui.images.isNotEmpty()),
            notice = ui.notice,
            showStarters = ui.text.isBlank() && ui.images.isEmpty(),
            showBusy = ui.busy,
            onMic = ::toggleMic,
            onSend = vm::submit,
            onCamera = ::openCamera,
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = vm::removeImage,
            onStarter = vm::quickAsk,
            onManual = onManual,
        )
        AnswerCards(ui.answer, vm)
    }
}

/** The AI's reply to a meal or outfit question. Used by the composer and the chat. */
@Composable
internal fun AnswerCards(answer: AskResult?, vm: CaptureViewModel) {
    when (val answer = answer) {
        is AskResult.Meals -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            answer.ideas.forEachIndexed { index, idea ->
                var open by rememberSaveable(index, idea.title) { mutableStateOf(false) }
                val have = idea.uses.size
                val total = have + idea.missing.size
                LCard {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium, color = L.OnBox, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (idea.minutes != null || idea.missing.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            idea.minutes?.let { minutes ->
                                Text("$minutes min", style = MaterialTheme.typography.labelMedium, color = L.OnBoxMuted)
                            }
                            if (idea.missing.isEmpty()) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Have everything",
                                    tint = L.Primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (total > 0) {
                        Text("$have of $total ingredients", style = MaterialTheme.typography.labelMedium, color = L.OnBoxMuted)
                        LProgress(have.toFloat() / total.toFloat())
                    }
                    if (idea.uses.isNotEmpty() || idea.missing.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            idea.uses.forEach { name -> LChip(name, selected = true, onClick = {}) }
                            idea.missing.forEach { name -> LChip(name, selected = false, onClick = {}) }
                        }
                    }
                    if (idea.steps.isNotEmpty()) {
                        LTextLink(if (open) "Hide steps" else "Steps", { open = !open })
                        if (open) {
                            idea.steps.forEachIndexed { stepIndex, step ->
                                Text("${stepIndex + 1}. $step", style = MaterialTheme.typography.bodyMedium, color = L.OnBox)
                            }
                        }
                    }
                }
            }
        }
        is AskResult.Outfits -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            answer.ideas.forEachIndexed { index, idea ->
                var showWhy by rememberSaveable(index, idea.title) { mutableStateOf(false) }
                LCard {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium, color = L.OnBox)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        idea.itemIds.mapNotNull { answer.garments[it] }.forEach { garment ->
                            Column(Modifier.size(width = 84.dp, height = 112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LocalPhoto(
                                    file = garment.photoPath?.let { vm.thumb(it) } ?: File(""),
                                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(L.RadiusSm)),
                                )
                                Text(garment.name, style = MaterialTheme.typography.labelMedium, color = L.OnBoxMuted, maxLines = 1)
                            }
                        }
                    }
                    if (idea.why.isNotBlank()) {
                        Text(
                            idea.why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = L.OnBoxMuted,
                            maxLines = if (showWhy) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { showWhy = !showWhy }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
        is AskResult.Shopping -> if (answer.items.isNotEmpty()) {
            var added by rememberSaveable(answer) { mutableStateOf<Int?>(null) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Worth buying", style = MaterialTheme.typography.titleMedium, color = L.OnBox)
                LGroup {
                    answer.items.forEachIndexed { index, item ->
                        if (index > 0) LGroupDivider()
                        LGroupRow(title = item.name, sub = item.reason.ifBlank { null })
                    }
                }
                LButton(
                    if (added == null) "Add all" else "Added $added",
                    onClick = { if (added == null) vm.addToShopping(answer.items.map { it.name }) { added = it } },
                    enabled = added == null,
                )
            }
        }
        is AskResult.Empty -> Text(answer.reason, style = MaterialTheme.typography.bodyMedium, color = L.Ink)
        AskResult.LogIt, null -> Unit
    }
}
