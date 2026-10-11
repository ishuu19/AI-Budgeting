package com.ledgerai.app.presentation.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ledgerai.app.service.AlarmToneHelper
import kotlinx.coroutines.delay

/** Plays an alarm tone for a few seconds so the user can hear it before saving. */
@Stable
class TonePreview internal constructor(private val context: android.content.Context) {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf(false)
        private set

    fun toggle(toneUri: String?) {
        if (playing) {
            stop()
            return
        }
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, AlarmToneHelper.resolvePlayableUri(context, toneUri))
                setOnCompletionListener { stop() }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()
        playing = player != null
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = false
    }
}

@Composable
fun rememberTonePreview(): TonePreview {
    val context = LocalContext.current
    val preview = remember { TonePreview(context) }
    DisposableEffect(preview) { onDispose { preview.stop() } }
    LaunchedEffect(preview.playing) {
        if (preview.playing) {
            delay(6000)
            preview.stop()
        }
    }
    return preview
}

/** Preview button for a tone. */
@Composable
fun TonePreviewButton(toneUri: String?, preview: TonePreview, modifier: Modifier = Modifier) {
    LIconTextChip(
        text = if (preview.playing) "Stop" else "Preview",
        icon = if (preview.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
        onClick = { preview.toggle(toneUri) },
        modifier = modifier
    )
}

/** Built-in tones, one custom tone and a preview. */
@Composable
fun ToneChips(toneUri: String?, onTone: (String) -> Unit) {
    val context = LocalContext.current
    val defaultTone = AlarmToneHelper.builtInTones.first().uriString
    val selected = toneUri ?: defaultTone
    val isCustom = AlarmToneHelper.builtInTones.none { it.uriString == selected }
    val preview = rememberTonePreview()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        AlarmToneHelper.copyCustomTone(context, uri)
            .onSuccess { path -> onTone(path) }
            .onFailure { e -> Toast.makeText(context, e.message ?: "Import failed", Toast.LENGTH_LONG).show() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tone", style = MaterialTheme.typography.titleSmall, color = L.Ink)
        ChipsRow {
            if (isCustom) LChip(AlarmToneHelper.displayName(selected), selected = true, onClick = {})
            AlarmToneHelper.builtInTones.forEach { tone ->
                LChip(tone.displayName, selected = selected == tone.uriString, onClick = { onTone(tone.uriString) })
            }
            LChip("Custom", selected = false, onClick = { picker.launch(arrayOf("audio/*")) })
        }
        ChipsRow { TonePreviewButton(selected, preview) }
    }
}

/** Outlined chip with a leading icon (48dp tall). */
@Composable
fun LIconTextChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, L.Primary.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = L.Primary, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = L.Primary)
        }
    }
}
