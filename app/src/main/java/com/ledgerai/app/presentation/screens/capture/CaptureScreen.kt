package com.ledgerai.app.presentation.screens.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledgerai.app.data.capture.CaptureFiler
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaKind
import com.ledgerai.app.data.media.UploadState
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LocalPhoto
import com.ledgerai.app.presentation.components.money
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items

/** Capture tab: the composer on top, then everything the AI has filed or is still working on. */
@Composable
fun CaptureScreen(
    onHandOff: (String) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenPantry: () -> Unit,
    links: com.ledgerai.app.presentation.navigation.AppLinks,
    vm: CaptureViewModel = hiltViewModel(),
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    val inbox by vm.inbox.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.events.collect { if (it is CaptureEvent.HandOff) onHandOff(it.text) }
    }

    LScreen(title = "Capture") {
        item { CaptureComposer(vm, onManual = { manual = true }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip("Wardrobe", selected = false, onClick = onOpenWardrobe)
                LChip("Pantry", selected = false, onClick = onOpenPantry)
            }
        }
        item { LSection("Recently captured") }
        if (inbox.isEmpty()) {
            item { LEmpty(Icons.Filled.AutoAwesome, "Photos you add show up here, named and filed by the AI") }
        } else {
            items(inbox, key = { it.id }) { row ->
                InboxCard(row, vm, onOpenReceipt, onOpenPerson)
            }
        }
    }
    if (manual) ManualAddSheet(links, onDismiss = { manual = false })
}

@Composable
internal fun InboxCard(
    row: MediaAssetEntity,
    vm: CaptureViewModel,
    onOpenReceipt: (Long) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val open: (() -> Unit)? = when (row.linkedType) {
        CaptureFiler.LINK_RECEIPT -> row.linkedId?.toLongOrNull()?.let { id -> { onOpenReceipt(id) } }
        CaptureFiler.LINK_PERSON -> row.linkedId?.let { id -> { onOpenPerson(id) } }
        else -> null
    }
    LCard(onClick = open, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            LocalPhoto(vm.thumb(row.id), Modifier.size(72.dp).clip(RoundedCornerShape(L.RadiusSm)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    row.title.ifBlank { statusLine(row) },
                    style = MaterialTheme.typography.titleSmall,
                    color = L.OnBox,
                    maxLines = 2,
                )
                if (row.description.startsWith("!") && row.description.lines().size > 1) {
                    Text(
                        "Error: " + row.description.lines().drop(1).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = L.Danger,
                        maxLines = 4,
                    )
                }
                if (row.description.isNotBlank() && row.title.isNotBlank() && !row.description.startsWith("!")) {
                    Text(row.description, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 3)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (row.kind != MediaKind.UNKNOWN) {
                        Text(row.kind.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = L.Gold)
                    }
                    Icon(
                        if (row.uploadState == UploadState.UPLOADED) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                        contentDescription = if (row.uploadState == UploadState.UPLOADED) "Backed up" else "Waiting to back up",
                        tint = L.OnBoxMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    if (open != null) Text("Open", style = MaterialTheme.typography.labelMedium, color = L.Primary)
                }
            }
        }
        when (row.analysisState) {
            AnalysisState.NEEDS_INPUT -> NeedsInput(row, vm)
            AnalysisState.FAILED -> {
                Text(row.description, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
                LabelByHand(row, vm)
                LGhostButton("Try the AI again", { vm.retry(row.id) })
            }
            else -> Unit
        }
    }
}

@Composable
private fun statusLine(row: MediaAssetEntity): String {
    val online = com.ledgerai.app.presentation.components.rememberOnline()
    return when (row.analysisState) {
        AnalysisState.PENDING -> when {
            row.description.startsWith("!") -> row.description.drop(1).lineSequence().first()
            online -> "Working on it…"
            else -> "Offline. I will continue when you are back online."
        }
        AnalysisState.FAILED -> "Needs a name"
        else -> "Photo"
    }
}

@Composable
private fun NeedsInput(row: MediaAssetEntity, vm: CaptureViewModel) {
    val suggestions = vm.suggestionsOf(row)
    if (suggestions.isEmpty()) {
        LabelByHand(row, vm)
        return
    }
    suggestions.forEach { s ->
        when (s.type) {
            CaptureFiler.SUGGEST_STOCK -> Suggest(
                text = "Add to your pantry: " + s.items.joinToString { it.name },
                yes = "Add ${s.items.size} to pantry",
                onYes = { vm.accept(row.id, s.type) },
                onNo = { vm.dismissSuggestion(row.id, s.type) },
            )
            CaptureFiler.SUGGEST_EXPENSE -> Suggest(
                text = "Log ${s.amount?.let { money(it) }.orEmpty()} at ${s.merchant ?: "this shop"} as spending.",
                yes = "Log expense",
                onYes = { vm.accept(row.id, s.type) },
                onNo = { vm.dismissSuggestion(row.id, s.type) },
            )
            CaptureFiler.SUGGEST_SUBSCRIPTION -> Suggest(
                text = "Looks like a subscription: ${s.merchant}, ${s.amount?.let { money(it) } ?: "amount unknown"} ${s.period}. Renews about ${s.renewsOn}.",
                yes = "Track it",
                onYes = { vm.accept(row.id, s.type) },
                onNo = { vm.dismissSuggestion(row.id, s.type) },
            )
            CaptureFiler.SUGGEST_NAME -> {
                var name by rememberSaveable(row.id) { mutableStateOf("") }
                Text("Who is this? I'll file it under their name on the date it was taken.", style = MaterialTheme.typography.bodyMedium, color = L.OnBox)
                LField(name, { name = it }, "Name")
                LButton("Save", { vm.answerName(row.id, name) }, enabled = name.isNotBlank())
            }
        }
    }
}

@Composable
private fun Suggest(text: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = L.OnBox)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LButton(yes, onYes, Modifier.weight(1f))
        LGhostButton("Not now", onNo, Modifier.weight(1f))
    }
}

@Composable
private fun LabelByHand(row: MediaAssetEntity, vm: CaptureViewModel) {
    var kind by rememberSaveable(row.id) { mutableStateOf(MediaKind.CLOTHING) }
    var name by rememberSaveable(row.id) { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(MediaKind.CLOTHING to "Clothes", MediaKind.FOOD to "Food", MediaKind.PERSON to "Person", MediaKind.OTHER to "Other")
            .forEach { (k, label) -> LChip(label, selected = kind == k, onClick = { kind = k }) }
    }
    LField(name, { name = it }, if (kind == MediaKind.PERSON) "Who is this?" else "What is it?")
    LButton("File it", { vm.fileByHand(row.id, kind, name) }, enabled = name.isNotBlank())
}
