package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun RecurrenceDeleteSheet(
    title: String,
    isRecurring: Boolean,
    onDismiss: () -> Unit,
    onThisOnly: () -> Unit,
    onThisAndFuture: () -> Unit,
    onAll: () -> Unit
) {
    LSheet(
        title = if (isRecurring) "Delete repeating" else "Delete",
        onDismiss = onDismiss,
        primary = "Cancel",
        onPrimary = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("“$title”", color = L.Ink, style = MaterialTheme.typography.bodyMedium)
            if (isRecurring) {
                LButton("This one", onClick = onThisOnly)
                LButton("This and later", onClick = onThisAndFuture)
                LGhostButton("All", onClick = onAll)
            } else {
                LButton("Delete", onClick = onThisOnly)
            }
        }
    }
}
