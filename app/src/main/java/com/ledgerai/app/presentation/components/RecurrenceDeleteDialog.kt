package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        title = if (isRecurring) "Remove recurring" else "Remove",
        onDismiss = onDismiss,
        primary = "Cancel",
        onPrimary = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("“$title”", color = L.Ink, style = MaterialTheme.typography.bodyMedium)
            if (isRecurring) {
                LButton("This event only", onClick = onThisOnly)
                LButton("This and future events", onClick = onThisAndFuture)
                LGhostButton("All events in the series", onClick = onAll)
            } else {
                LButton("Remove from calendar", onClick = onThisOnly)
            }
        }
    }
}

@Composable
fun SimpleDeleteConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = L.Box) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = L.InkMuted) }
        },
        containerColor = L.Page
    )
}
