package com.ledgerai.app.presentation.screens.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
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
import com.ledgerai.app.data.capture.CaptureFiler
import com.ledgerai.app.data.capture.Suggestion
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaKind
import com.ledgerai.app.data.media.UploadState
import com.ledgerai.app.domain.subscriptions.SubscriptionPeriod
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.LocalPhoto
import com.ledgerai.app.presentation.components.money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val renewsFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** One captured photo: what it is, and the one tap it still needs. */
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
    val error = errorText(row)
    val fallback = statusLine(row)
    val headline = row.title.ifBlank {
        when {
            row.analysisState == AnalysisState.FAILED -> "Needs a name"
            error != null -> "Photo"
            else -> fallback
        }
    }
    val note = when {
        error != null && error != headline -> error
        row.description.isNotBlank() && row.title.isNotBlank() && row.description != headline -> row.description
        else -> null
    }
    LCard(onClick = open) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalPhoto(vm.thumb(row.id), Modifier.size(64.dp).clip(RoundedCornerShape(L.RadiusSm)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 2)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    kindLabel(row.kind)?.let { label ->
                        Text(label, style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
                    }
                    Icon(
                        if (row.uploadState == UploadState.UPLOADED) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                        contentDescription = if (row.uploadState == UploadState.UPLOADED) "Backed up" else "Waiting to back up",
                        tint = L.OnBoxMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (open != null) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = L.OnBoxMuted)
            }
        }
        when (row.analysisState) {
            AnalysisState.NEEDS_INPUT -> NeedsInput(row, vm)
            AnalysisState.FAILED -> FailedActions(row, vm)
            else -> Unit
        }
    }
}

@Composable
private fun FailedActions(row: MediaAssetEntity, vm: CaptureViewModel) {
    var naming by rememberSaveable(row.id) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LToolButton(Icons.Filled.Refresh, "Retry", onClick = { vm.retry(row.id) })
        LTextLink("Name it", onClick = { naming = true })
    }
    if (naming) LabelByHand(row, vm)
}

@Composable
private fun NeedsInput(row: MediaAssetEntity, vm: CaptureViewModel) {
    val suggestions = vm.suggestionsOf(row)
    if (suggestions.isEmpty()) {
        LabelByHand(row, vm)
        return
    }
    val rows = suggestions.filter { it.type != CaptureFiler.SUGGEST_NAME }
    if (rows.isNotEmpty()) {
        LGroup {
            rows.forEachIndexed { index, suggestion ->
                if (index > 0) LGroupDivider()
                SuggestionRow(suggestion, onYes = { vm.accept(row.id, suggestion.type) }, onNo = { vm.dismissSuggestion(row.id, suggestion.type) })
            }
        }
    }
    if (suggestions.any { it.type == CaptureFiler.SUGGEST_NAME }) {
        var name by rememberSaveable(row.id) { mutableStateOf("") }
        LField(name, { name = it }, "Who is this?")
        LButton("Save", { vm.answerName(row.id, name) }, enabled = name.isNotBlank())
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, onYes: () -> Unit, onNo: () -> Unit) {
    LGroupRow(
        title = suggestion.rowTitle(),
        sub = suggestion.rowSub(),
        trailing = suggestion.rowTrailing(),
        end = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LTextLink(suggestion.actionLabel(), onYes)
                LToolButton(Icons.Filled.Close, "Dismiss", onNo)
            }
        },
    )
}

@Composable
private fun LabelByHand(row: MediaAssetEntity, vm: CaptureViewModel) {
    var kind by rememberSaveable(row.id) { mutableStateOf(MediaKind.CLOTHING) }
    var name by rememberSaveable(row.id) { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(MediaKind.CLOTHING to "Clothes", MediaKind.FOOD to "Food", MediaKind.PERSON to "Person", MediaKind.OTHER to "Other")
            .forEach { (value, label) -> LChip(label, selected = kind == value, onClick = { kind = value }) }
    }
    LField(name, { name = it }, if (kind == MediaKind.PERSON) "Who is this?" else "What is it?")
    LButton("File it", { vm.fileByHand(row.id, kind, name) }, enabled = name.isNotBlank())
}

@Composable
private fun statusLine(row: MediaAssetEntity): String {
    val online = com.ledgerai.app.presentation.components.rememberOnline()
    return when (row.analysisState) {
        AnalysisState.PENDING -> if (online) "Working on it…" else "Offline · queued"
        AnalysisState.FAILED -> "Needs a name"
        else -> "Photo"
    }
}

private fun errorText(row: MediaAssetEntity): String? {
    val raw = row.description
    if (!raw.startsWith("!")) return null
    return raw.removePrefix("!").trim().lineSequence().filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
}

private fun kindLabel(kind: String): String? = when (kind) {
    MediaKind.CLOTHING -> "Clothes"
    MediaKind.FOOD -> "Food"
    MediaKind.PERSON -> "Person"
    MediaKind.RECEIPT -> "Receipt"
    MediaKind.OTHER -> "Other"
    else -> null
}

private fun Suggestion.rowTitle(): String = when (type) {
    CaptureFiler.SUGGEST_STOCK -> items.joinToString { it.name }.ifBlank { "Pantry" }
    CaptureFiler.SUGGEST_EXPENSE -> merchant?.takeIf { it.isNotBlank() } ?: "Spending"
    CaptureFiler.SUGGEST_SUBSCRIPTION -> merchant?.takeIf { it.isNotBlank() } ?: "Subscription"
    else -> "Photo"
}

private fun Suggestion.rowSub(): String? = when (type) {
    CaptureFiler.SUGGEST_EXPENSE -> if (!merchant.isNullOrBlank()) "Spending" else null
    CaptureFiler.SUGGEST_SUBSCRIPTION -> subscriptionSub(period, renewsOn)
    else -> null
}

private fun Suggestion.rowTrailing(): String? = when (type) {
    CaptureFiler.SUGGEST_EXPENSE, CaptureFiler.SUGGEST_SUBSCRIPTION -> amount?.let { money(it) }
    else -> null
}

private fun Suggestion.actionLabel(): String = when (type) {
    CaptureFiler.SUGGEST_STOCK -> "Add"
    CaptureFiler.SUGGEST_EXPENSE -> "Log"
    CaptureFiler.SUGGEST_SUBSCRIPTION -> "Track"
    else -> "Save"
}

private fun subscriptionSub(period: String?, renewsOn: String?): String? {
    val periodLabel = period?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { SubscriptionPeriod.fromWire(raw).displayName }.getOrNull()
    }
    val renewsLabel = renewsOn?.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
            ?.format(renewsFmt)
            ?.let { "renews $it" }
    }
    return listOfNotNull(periodLabel, renewsLabel).joinToString(" · ").ifBlank { null }
}
