package com.ledgerai.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
        primary = null,
        secondary = "Cancel",
        onSecondary = onDismiss,
    ) {
        Column {
            Text("“$title”", color = L.Ink, style = MaterialTheme.typography.bodyMedium)
            if (isRecurring) {
                DangerRow("This one", onThisOnly)
                DangerRow("This and later", onThisAndFuture)
                DangerRow("All", onAll)
            } else {
                DangerRow("Delete", onThisOnly)
            }
        }
    }
}

@Composable
private fun DangerRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = L.Danger,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = L.Target)
            .clickable(onClick = onClick)
            .padding(vertical = L.Space.x12),
    )
}
