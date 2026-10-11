package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.domain.util.LeaveByTime
import java.time.LocalDateTime

@Composable
fun LeaveByToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    eventStart: LocalDateTime?,
    travelMinutes: Int = LeaveByRepository.DEFAULT_TRAVEL_MINUTES,
    bufferMinutes: Int = LeaveByRepository.DEFAULT_BUFFER_MINUTES,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        LSwitchRow("Leave by", enabled, onEnabledChange)
        if (enabled && eventStart != null) {
            val leaveAt = LeaveByTime.computeLeaveAt(eventStart, travelMinutes, bufferMinutes)
            Text(
                localeTime(leaveAt.toLocalTime()),
                style = MaterialTheme.typography.bodyMedium,
                color = L.Ink
            )
        }
    }
}
