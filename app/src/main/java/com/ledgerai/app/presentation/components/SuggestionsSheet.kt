package com.ledgerai.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import com.ledgerai.app.data.ai.ScheduleDraftDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SuggestionsSheet(
    drafts: List<ScheduleDraftDto>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onAccept: (ScheduleDraftDto) -> Unit,
    onDismissDraft: (ScheduleDraftDto) -> Unit
) {
    LSheet(
        title = "Suggestions",
        onDismiss = onDismiss,
        primary = null,
    ) {
        if (loading) {
            LLoading()
        } else if (drafts.isEmpty()) {
            LEmpty(Icons.Filled.Lightbulb, "No suggestions")
        } else {
            LGroup {
                drafts.forEachIndexed { index, draft ->
                    if (index > 0) LGroupDivider()
                    LGroupRow(
                        title = draft.title.orEmpty().ifBlank { "Suggestion" },
                        sub = draftWhen(draft.startAt),
                        onClick = { onAccept(draft) },
                        end = { LToolButton(Icons.Filled.Add, "Add", { onAccept(draft) }) },
                    )
                }
            }
        }
    }
}

private fun draftWhen(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val parsed = runCatching { LocalDateTime.parse(iso) }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(iso, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        }.getOrNull()
        ?: return null
    val day = parsed.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$day ${localeTime(parsed.toLocalTime())}"
}
