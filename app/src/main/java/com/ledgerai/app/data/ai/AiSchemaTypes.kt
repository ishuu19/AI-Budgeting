package com.ledgerai.app.data.ai

/**
 * Fixed AI response kinds validated on-device (PLAN §3.2).
 * Wire names must match Edge Function `ai-proxy` schemas.
 */
enum class AiResponseType(val wireName: String) {
    TRANSACTION("transaction"),
    INSIGHT("insight"),
    NOTE_SUMMARY("note_summary"),
    CHAT("chat");

    companion object {
        fun fromWire(name: String): AiResponseType? =
            entries.find { it.wireName.equals(name, ignoreCase = true) }
    }
}

/** Alias used by [AiResponseValidator] and unit tests. */
typealias AiSchemaType = AiResponseType
