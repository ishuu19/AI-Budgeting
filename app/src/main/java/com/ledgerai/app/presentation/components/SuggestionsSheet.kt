package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.ai.ScheduleDraftDto

@Composable
fun SuggestionsSheet(
    drafts: List<ScheduleDraftDto>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onAccept: (ScheduleDraftDto) -> Unit,
    onDismissDraft: (ScheduleDraftDto) -> Unit
) {
    LSheet(
        title = "Suggest",
        onDismiss = onDismiss,
        primary = "Close",
        onPrimary = onDismiss
    ) {
        if (loading) {
            LLoading()
        } else if (drafts.isEmpty()) {
            Text("Nothing to add", color = L.InkMuted)
        } else {
            drafts.forEach { draft ->
                LRow(
                    title = draft.title.orEmpty(),
                    sub = draft.reason ?: draft.startAt,
                    trailing = "Add",
                    onClick = { onAccept(draft) }
                )
            }
        }
    }
}
