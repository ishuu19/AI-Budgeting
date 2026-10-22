package com.ledgerai.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ledgerai.app.domain.assistant.AssistantTurn
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.assistant.StepState
import com.ledgerai.app.domain.assistant.TurnStep

/**
 * What the assistant did or wants to do. An answer is plain text. A change shows the executor's
 * message once it ran, and Confirm / Skip while it waits for a tap. Nothing here says "done" unless
 * the step is APPLIED.
 */
@Composable
fun AssistantTurnCard(
    turn: AssistantTurn,
    onApprove: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = L.Radius, topEnd = L.Radius, bottomStart = 8.dp, bottomEnd = L.Radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(L.Box)
            .border(1.dp, L.Line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (turn.steps.isEmpty() && turn.reply.isNotBlank()) {
            Text(turn.reply, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
        }
        turn.steps.forEach { step -> Step(step, onApprove, onSkip, onUndo) }
        turn.unknowns.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted) }
        if (turn.isEmpty) Text("I didn't find anything to do with that.", style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
    }
}

@Composable
private fun Step(step: TurnStep, onApprove: (Int) -> Unit, onSkip: (Int) -> Unit, onUndo: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (step.state) {
            StepState.APPLIED -> {
                Text(step.message, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
                if (step.undo != null) LTextLink("Undo", { onUndo(step.index) })
            }
            StepState.PENDING -> {
                Text(step.label, style = MaterialTheme.typography.bodyLarge, color = L.OnBox)
                if (step.risk == Risk.HIGH) {
                    Text("This changes your money records.", style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    LButton("Confirm", { onApprove(step.index) }, Modifier.weight(1f))
                    LGhostButton("Skip", { onSkip(step.index) }, Modifier.weight(1f))
                }
            }
            StepState.REJECTED, StepState.FAILED ->
                Text("Not done: ${step.message}", style = MaterialTheme.typography.bodyMedium, color = L.Danger)
            StepState.SKIPPED, StepState.UNDONE ->
                Text("${step.label}: ${step.message.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = L.OnBoxMuted)
        }
    }
}
