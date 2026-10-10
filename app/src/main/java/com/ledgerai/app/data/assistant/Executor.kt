package com.ledgerai.app.data.assistant

import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.Risk

enum class ActionStatus { APPLIED, NEEDS_APPROVAL, REJECTED, FAILED }

/** Outcome of one action. [undo] is only meaningful for APPLIED. */
data class ActionResult(
    val index: Int,
    val type: String,
    val status: ActionStatus,
    val risk: Risk?,
    val message: String,
    val undo: (suspend () -> Unit)? = null,
)

/** Persistence seam for the audit trail and idempotency (Room-backed in the app). */
interface AuditSink {
    suspend fun find(inputRef: String, index: Int): ActionResult?
    suspend fun record(inputRef: String, result: ActionResult)
}

/**
 * Applies an [ActionPlan]. The only place the assistant's output turns into writes.
 * - Unknown types and invalid args are rejected.
 * - Risk comes from the registry; the model's claim is ignored.
 * - LOW applies immediately. MEDIUM and HIGH apply only if their index is in `approved`.
 * - Re-running the same (inputRef, index) returns the earlier result instead of applying twice.
 */
class Executor(
    private val registry: ActionRegistry,
    private val audit: AuditSink,
) {
    suspend fun execute(plan: ActionPlan, approved: Set<Int> = emptySet()): List<ActionResult> =
        plan.actions.mapIndexed { index, action ->
            val prior = audit.find(plan.inputRef, index)
            if (prior != null && prior.status != ActionStatus.NEEDS_APPROVAL) return@mapIndexed prior

            val spec = registry[action.type]
                ?: return@mapIndexed finish(
                    plan, ActionResult(index, action.type, ActionStatus.REJECTED, null, "Unknown action type"),
                )
            spec.validate(action.args)?.let { error ->
                return@mapIndexed finish(
                    plan, ActionResult(index, action.type, ActionStatus.REJECTED, spec.risk, error),
                )
            }
            if (spec.risk != Risk.LOW && index !in approved) {
                return@mapIndexed finish(
                    plan, ActionResult(index, action.type, ActionStatus.NEEDS_APPROVAL, spec.risk, "Needs your approval"),
                )
            }
            val outcome: ActionOutcome = try {
                spec.apply(action.args)
            } catch (e: Exception) {
                return@mapIndexed finish(
                    plan, ActionResult(index, action.type, ActionStatus.FAILED, spec.risk, e.message ?: "Failed"),
                )
            }
            finish(plan, ActionResult(index, action.type, ActionStatus.APPLIED, spec.risk, outcome.summary, outcome.undo))
        }

    private suspend fun finish(plan: ActionPlan, result: ActionResult): ActionResult {
        audit.record(plan.inputRef, result)
        return result
    }
}
