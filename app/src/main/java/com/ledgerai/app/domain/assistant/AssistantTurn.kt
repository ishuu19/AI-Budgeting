package com.ledgerai.app.domain.assistant

enum class StepState { APPLIED, PENDING, REJECTED, FAILED, SKIPPED, UNDONE }

/** One action of a turn as the user sees it. [label] is read before approving; [message] comes from the executor. */
data class TurnStep(
    val index: Int,
    val type: String,
    val label: String,
    val risk: Risk?,
    val state: StepState,
    val message: String = "",
    val undo: (suspend () -> Unit)? = null,
)

/**
 * What came back for one message. With no steps, [reply] is the answer. With steps, the executor's
 * messages are the truth: nothing is reported as done unless a step is APPLIED.
 */
data class AssistantTurn(
    val plan: ActionPlan,
    val steps: List<TurnStep>,
    val notes: List<String> = emptyList(),
) {
    val reply: String get() = plan.reply
    val unknowns: List<String> get() = plan.unknowns + notes
    val pending: List<TurnStep> get() = steps.filter { it.state == StepState.PENDING }
    val isEmpty: Boolean get() = steps.isEmpty() && reply.isBlank() && unknowns.isEmpty()
    /** Plain text for the chat history: the answer, or what each step did or is waiting for. */
    fun summary(): String =
        if (steps.isEmpty()) reply
        else steps.joinToString("\n") {
            "- " + when (it.state) {
                StepState.APPLIED -> it.message
                StepState.PENDING -> "Waiting for you: ${it.label}"
                else -> "${it.label}: ${it.message}"
            }
        }

    fun withStep(step: TurnStep): AssistantTurn = copy(steps = steps.map { if (it.index == step.index) step else it })
}
