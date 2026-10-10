package com.ledgerai.app.domain.assistant

/** Result of applying one action. [undo] is set only when the change can be reversed. */
data class ActionOutcome(
    val summary: String,
    val undo: (suspend () -> Unit)? = null,
)

/** A closed-set action: its risk, arg validation and the code that applies it. */
class ActionSpec(
    val type: String,
    val risk: Risk,
    /** Shown to the model so it knows when to use this action. */
    val description: String = "",
    /** Compact args description for the model, e.g. `text:string`. */
    val argsHint: String = "",
    /** Returns an error message, or null when the args are acceptable. */
    val validate: (Map<String, Any?>) -> String?,
    val apply: suspend (Map<String, Any?>) -> ActionOutcome,
)

/** Only registered types can run. Unknown types from the model are rejected by the executor. */
class ActionRegistry(specs: List<ActionSpec>) {
    private val byType = specs.associateBy { it.type }

    init {
        require(byType.size == specs.size) { "Duplicate action type in registry" }
    }

    operator fun get(type: String): ActionSpec? = byType[type]

    val all: Collection<ActionSpec> get() = byType.values
}
