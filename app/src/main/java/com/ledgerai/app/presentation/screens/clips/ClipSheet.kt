package com.ledgerai.app.presentation.screens.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.clips.ClipChoice
import com.ledgerai.app.domain.clips.ClipRead
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LGhostButton

/** Bottom sheet over the app the text was copied from. About 45% of the height. */
@Composable
fun ClipSheet(
    text: String,
    sourceLabel: String,
    read: ClipRead,
    choices: List<ClipChoice>,
    selectedId: String,
    sensitive: Boolean,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
    onAsk: () -> Unit,
    onTrackJob: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Close Add to Notes" }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(L.Page)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Add to Notes", style = MaterialTheme.typography.titleLarge, color = L.Ink)
                Text(
                    "from $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = L.InkMuted,
                    modifier = Modifier.semantics { contentDescription = "Captured from $sourceLabel" },
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = L.Ink,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.lines().size > 3 || text.length > 140) {
                Text(
                    if (expanded) "Show less" else "Show all",
                    color = L.Primary,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .semantics { contentDescription = if (expanded) "Show less" else "Show all" },
                )
            }
            Text("Add to", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { choice ->
                    LChip(choice.label, selected = choice.id == selectedId, onClick = { onSelect(choice.id) })
                }
            }
            read.hints.forEach { hint ->
                Text("✦ $hint", color = L.Highlight, style = MaterialTheme.typography.bodyMedium)
            }
            if (read.jobTrack) {
                LGhostButton("Looks like a job · Track it", onTrackJob, fill = false)
            }
            if (sensitive) {
                Text("This looks sensitive. It stays on this phone and is not sent to AI.", color = L.Highlight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!sensitive) {
                    LGhostButton("Ask AI to file it", onAsk, modifier = Modifier.weight(1f))
                }
                LButton(if (sensitive) "Save anyway" else "Save", onSave, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ClipStrip(message: String, onUndo: () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(L.Box)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, color = L.OnBox, modifier = Modifier.weight(1f))
            Text(
                "Undo",
                color = L.Primary,
                modifier = Modifier
                    .clickable(onClick = onUndo)
                    .semantics { contentDescription = "Undo" }
                    .padding(8.dp),
            )
        }
    }
}
