package com.ledgerai.app.domain.assistant

/** How much friction an action needs. Defined by the registry, never by the model. */
enum class Risk { LOW, MEDIUM, HIGH }

/** One step proposed by the assistant. [risk] is what the model claimed and is advisory only. */
data class ProposedAction(
    val type: String,
    val args: Map<String, Any?> = emptyMap(),
    val risk: Risk? = null,
)

/** Validated output of the assistant. It is a proposal: nothing has been written yet. */
data class ActionPlan(
    val inputRef: String,
    val actions: List<ProposedAction>,
    val unknowns: List<String> = emptyList(),
    val reply: String = "",
)
