package com.ledgerai.app.presentation.screens.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.records.PasswordGuard
import com.ledgerai.app.domain.records.RecordDraft
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LTextLink

/** One suggestion for a birthday, trip, appointment, achievement or wish. Nothing is saved until Save. */
@Composable
fun RecordCard(draft: RecordDraft, onSave: () -> Unit, onDismiss: () -> Unit, onOpenPasswords: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(draft.title, style = MaterialTheme.typography.titleMedium, color = L.Ink)
        Text(draft.detail, style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft === PasswordGuard) LTextLink("Open Passwords", { onDismiss(); onOpenPasswords() })
            else LTextLink("Save", onSave)
            LTextLink("Dismiss", onDismiss)
        }
    }
}
