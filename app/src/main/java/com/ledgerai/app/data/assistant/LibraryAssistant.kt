package com.ledgerai.app.data.assistant

import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.AssistantArea
import com.ledgerai.app.domain.assistant.AssistantTurn
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.assistant.StepState
import com.ledgerai.app.domain.assistant.TurnStep
import com.ledgerai.app.domain.assistant.actionInAreas
import java.time.LocalDateTime
import java.util.UUID

/** A problem the user can act on (not signed in), as opposed to a network or model failure. */
class AssistantUserError(message: String) : Exception(message)

/**
 * Text in, answer or changes out, for a Library section or the whole app.
 * One model call turns the message into a plan from the section's allowed actions, with the user's own
 * records as context. The [Executor] applies it: LOW runs now, MEDIUM and HIGH wait as pending steps
 * until [approve]. The model never writes; a step is reported done only when the executor says so.
 */
class LibraryAssistant(
    private val orchestrator: Orchestrator,
    private val executor: Executor,
    private val registry: ActionRegistry,
    private val facts: AssistantFacts,
    /** The signed-in user's id, blank when nobody is signed in. */
    private val currentUser: suspend () -> String,
) {
    /** @param keywords words the AI picked out of the message; only records that mention one are sent.
     * @param areas the parts of the data this call may read and change; [AssistantArea.ALL] for the chat. */
    suspend fun ask(text: String, areas: Set<AssistantArea>, keywords: List<String> = emptyList()): Result<AssistantTurn> {
        val message = text.trim().take(MAX_MESSAGE)
        if (message.isEmpty()) return Result.failure(IllegalArgumentException("Empty message"))
        val userId = currentUser()
        if (userId.isBlank()) return Result.failure(AssistantUserError("Sign in to use this."))

        val context = facts.build(areas, userId, keywords)
        val inputRef = "lib:${UUID.randomUUID()}"
        val plan = orchestrator.plan(
            inputRef = inputRef,
            utterance = message,
            context = context,
            nowIso = LocalDateTime.now().withNano(0).toString(),
            allowed = { actionInAreas(it.type, areas) },
            answers = true,
        ).getOrElse { return Result.failure(it) }

        // The model can name any registered action. Keep only this call's areas, and stamp the real user
        // id so a made-up one is never used.
        val kept = plan.actions.filter { actionInAreas(it.type, areas) }
        val dropped = plan.actions.size - kept.size
        val safe = plan.copy(
            actions = kept.map { it.copy(args = it.args + ("userId" to userId)) },
        )
        val notes = if (dropped > 0) listOf("I can't change that from here.") else emptyList()

        val labels = safe.actions.map { label(it) }
        val results = executor.execute(safe)
        val steps = results.mapIndexed { i, r ->
            val (text, problem) = labels[i]
            // An edit or delete that names a record that is not there must not wait for a tap.
            val missing = problem != null && r.status == ActionStatus.NEEDS_APPROVAL
            TurnStep(
                index = r.index,
                type = r.type.ifBlank { safe.actions[i].type },
                label = text,
                risk = r.risk,
                state = if (missing) StepState.REJECTED else r.status.toStepState(),
                message = if (missing) problem!! else r.message,
                undo = r.undo,
            )
        }
        return Result.success(AssistantTurn(safe, steps, notes))
    }

    /** Runs one pending step after the user's tap. Other steps are left as they are. */
    suspend fun approve(turn: AssistantTurn, index: Int): AssistantTurn {
        val step = turn.steps.firstOrNull { it.index == index && it.state == StepState.PENDING } ?: return turn
        val result = executor.execute(turn.plan, approved = setOf(index)).firstOrNull { it.index == index } ?: return turn
        return turn.withStep(
            step.copy(state = result.status.toStepState(), message = result.message, undo = result.undo, risk = result.risk ?: step.risk)
        )
    }

    fun skip(turn: AssistantTurn, index: Int): AssistantTurn {
        val step = turn.steps.firstOrNull { it.index == index && it.state == StepState.PENDING } ?: return turn
        return turn.withStep(step.copy(state = StepState.SKIPPED, message = "Skipped"))
    }

    suspend fun undo(turn: AssistantTurn, index: Int): AssistantTurn {
        val step = turn.steps.firstOrNull { it.index == index && it.state == StepState.APPLIED } ?: return turn
        val undo = step.undo ?: return turn
        return try {
            undo()
            turn.withStep(step.copy(state = StepState.UNDONE, message = "Undone", undo = null))
        } catch (e: Exception) {
            turn.withStep(step.copy(message = e.message ?: "Could not undo", undo = null))
        }
    }

    private companion object {
        const val MAX_MESSAGE = 1000
    }

    /**
     * The line the user reads (the action's own preview, else its description) and, when the preview
     * could not be built because the target record is gone, the reason.
     */
    private suspend fun label(action: ProposedAction): Pair<String, String?> {
        val spec = registry[action.type] ?: return action.type to null
        val preview = runCatching { spec.preview(action.args) }
        return (preview.getOrNull() ?: spec.description.removeSuffix(".")) to preview.exceptionOrNull()?.message
    }

    private fun ActionStatus.toStepState(): StepState = when (this) {
        ActionStatus.APPLIED -> StepState.APPLIED
        ActionStatus.NEEDS_APPROVAL -> StepState.PENDING
        ActionStatus.REJECTED -> StepState.REJECTED
        ActionStatus.FAILED -> StepState.FAILED
    }
}
