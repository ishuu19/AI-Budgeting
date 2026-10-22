package com.ledgerai.app.data.assistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.assistant.Risk

/** Turns raw model text into an [ActionPlan]. Returns null if it is not a usable plan. */
object PlanParser {
    private const val MAX_ACTIONS = 8

    fun parse(inputRef: String, raw: String): ActionPlan? {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = try {
            JsonParser.parseString(cleaned).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        } ?: return null

        // A pure answer may omit "actions"; an object with neither is not a plan.
        val actions = root.getAsJsonArray("actions")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.toAction() }
            ?.take(MAX_ACTIONS)
            ?: if (root.get("reply").asStringOrNull().isNullOrBlank()) return null else emptyList()

        return ActionPlan(
            inputRef = inputRef,
            actions = actions,
            unknowns = root.getAsJsonArray("unknowns")?.mapNotNull { it.asStringOrNull() } ?: emptyList(),
            reply = root.get("reply").asStringOrNull().orEmpty().take(1500),
        )
    }

    private fun JsonObject.toAction(): ProposedAction? {
        val type = get("type").asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val args = getAsJsonObject("args")?.entrySet()?.associate { (k, v) -> k to v.toPlain() } ?: emptyMap()
        val risk = get("risk").asStringOrNull()?.let { r -> Risk.entries.find { it.name.equals(r, true) } }
        return ProposedAction(type, args, risk)
    }

    private fun com.google.gson.JsonElement?.asStringOrNull(): String? =
        if (this != null && isJsonPrimitive) asString else null

    private fun com.google.gson.JsonElement.toPlain(): Any? = when {
        isJsonNull -> null
        isJsonPrimitive -> asJsonPrimitive.let { p ->
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asDouble
                else -> p.asString
            }
        }
        else -> toString()
    }
}
