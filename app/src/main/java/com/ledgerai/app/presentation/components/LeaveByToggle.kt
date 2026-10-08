package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.data.repository.LeaveByRepository
import com.ledgerai.app.domain.util.LeaveByTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LeaveByToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    eventStart: LocalDateTime?,
    travelMinutes: Int = LeaveByRepository.DEFAULT_TRAVEL_MINUTES,
    bufferMinutes: Int = LeaveByRepository.DEFAULT_BUFFER_MINUTES,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Leave by", color = L.InkMuted)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (enabled && eventStart != null) {
        val leaveAt = LeaveByTime.computeLeaveAt(eventStart, travelMinutes, bufferMinutes)
        Text(
            leaveAt.format(timeFmt),
            style = MaterialTheme.typography.bodyMedium,
            color = L.Ink
        )
    }
}
