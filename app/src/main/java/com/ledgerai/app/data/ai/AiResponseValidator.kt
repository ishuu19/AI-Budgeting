package com.ledgerai.app.data.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

sealed class ValidatedAiResponse {
    data class Transaction(val dto: ParsedTransactionDto) : ValidatedAiResponse()
    data class Insight(val dto: InsightDto) : ValidatedAiResponse()
    data class NoteSummary(val dto: NoteSummaryDto) : ValidatedAiResponse()
    data class Chat(val reply: String) : ValidatedAiResponse()
}

/**
 * Validates AI JSON against fixed schemas:
 * transaction | insight | note_summary | chat.
 * Accepts bare payloads or Edge envelopes `{ "type", "data" }`.
 * Never persists — writes happen only in app code after user confirm.
 */
@Singleton
class AiResponseValidator @Inject constructor(
    private val gson: Gson,
) {

    fun validate(type: AiResponseType, rawOrJson: String): ValidatedAiResponse? {
        return try {
            val payload = unwrapPayload(rawOrJson, type) ?: return null
            when (type) {
                AiResponseType.TRANSACTION -> validateTransaction(payload)
                AiResponseType.INSIGHT -> validateInsight(payload)
                AiResponseType.NOTE_SUMMARY -> validateNoteSummary(payload)
                AiResponseType.CHAT -> validateChat(payload)?.let { ValidatedAiResponse.Chat(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Plain-text or JSON chat reply helper for cascade fallbacks. */
    fun parseChatReply(raw: String): String? {
        val fromJson = validate(AiResponseType.CHAT, raw) as? ValidatedAiResponse.Chat
        if (fromJson != null) return fromJson.reply
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return trimmed.takeIf { it.isNotBlank() && !it.startsWith("{") }
    }

    /**
     * Voice reply: a bare array, an `items` array or one object (the Edge `voice_intent` shape).
     * Unknown intents are dropped. Amounts must be positive and sane, text is capped, at most [MAX_VOICE_ITEMS] items.
     */
    fun validateVoiceItems(raw: String): List<ParsedVoiceIntentDto> {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (cleaned.isBlank()) return emptyList()
        val elements = try {
            val root = JsonParser.parseString(cleaned)
            when {
                root.isJsonArray -> root.asJsonArray.toList()
                root.isJsonObject && root.asJsonObject.get("items")?.isJsonArray == true ->
                    root.asJsonObject.getAsJsonArray("items").toList()
                root.isJsonObject -> listOf(root)
                else -> emptyList()
            }
        } catch (_: Exception) {
            // Text around the JSON: use the first object.
            listOfNotNull(extractJsonObject(cleaned)?.let { runCatching { JsonParser.parseString(it) }.getOrNull() })
        }
        return elements.asSequence()
            .filter { it.isJsonObject }
            .mapNotNull { runCatching { gson.fromJson(it, ParsedVoiceIntentDto::class.java) }.getOrNull() }
            .mapNotNull { sanitizeVoiceItem(it) }
            .take(MAX_VOICE_ITEMS)
            .toList()
    }

    private fun sanitizeVoiceItem(dto: ParsedVoiceIntentDto): ParsedVoiceIntentDto? {
        val intent = dto.intent?.trim()?.uppercase()
        if (intent != null && intent.isNotEmpty() && intent !in VOICE_INTENTS) return null
        val amount = dto.amount?.takeIf { it.isFinite() && it > 0.0 && it < MAX_VOICE_AMOUNT }
        return dto.copy(
            intent = intent,
            amount = amount,
            confidence = (dto.confidence ?: 0.7f).coerceIn(0f, 1f),
            merchant = dto.merchant?.take(MAX_VOICE_TEXT),
            note = dto.note?.take(MAX_VOICE_BODY),
            title = dto.title?.take(MAX_VOICE_TEXT),
            body = dto.body?.take(MAX_VOICE_BODY),
            label = dto.label?.take(MAX_VOICE_TEXT),
            name = dto.name?.take(MAX_VOICE_TEXT),
            tags = dto.tags?.map { it.trim().take(40) }?.filter { it.isNotEmpty() }?.take(8),
        )
    }

    private fun unwrapPayload(raw: String, expected: AiResponseType): String? {
        val json = extractJsonObject(raw) ?: run {
            // Chat may be plain text
            return if (expected == AiResponseType.CHAT) raw.trim().takeIf { it.isNotBlank() } else null
        }
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            if (obj.has("data") && (obj.get("data").isJsonObject || obj.get("data").isJsonPrimitive)) {
                val wireType = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString
                if (wireType != null &&
                    AiResponseType.fromWire(wireType) != null &&
                    !wireType.equals(expected.wireName, ignoreCase = true)
                ) {
                    return null
                }
                val data = obj.get("data")
                when {
                    data.isJsonObject -> data.asJsonObject.toString()
                    data.isJsonPrimitive && data.asJsonPrimitive.isString -> data.asString
                    else -> json
                }
            } else {
                json
            }
        } catch (_: Exception) {
            json
        }
    }

    private fun validateTransaction(json: String): ValidatedAiResponse? {
        val dto = gson.fromJson(json, ParsedTransactionDto::class.java) ?: return null
        val category = dto.category?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        val txType = dto.type?.uppercase()?.takeIf { it == "INCOME" || it == "EXPENSE" } ?: return null
        val confidence = (dto.confidence ?: 0.7f).coerceIn(0f, 1f)
        return ValidatedAiResponse.Transaction(
            dto.copy(
                category = category,
                type = txType,
                merchant = dto.merchant.orEmpty(),
                note = dto.note.orEmpty(),
                confidence = confidence,
            )
        )
    }

    private fun validateInsight(json: String): ValidatedAiResponse? {
        val dto = gson.fromJson(json, InsightDto::class.java) ?: return null
        if (dto.title.isNullOrBlank() || dto.body.isNullOrBlank()) return null
        val severity = when (dto.severity?.lowercase()) {
            "watch", "warn", "tip" -> "watch"
            "alert" -> "alert"
            else -> "info"
        }
        return ValidatedAiResponse.Insight(
            dto.copy(
                title = dto.title.trim(),
                body = dto.body.trim(),
                severity = severity,
                actions = dto.actions.orEmpty().take(5),
            )
        )
    }

    private fun validateNoteSummary(json: String): ValidatedAiResponse? {
        val dto = gson.fromJson(json, NoteSummaryDto::class.java) ?: return null
        if (dto.summary.isNullOrBlank()) return null
        return ValidatedAiResponse.NoteSummary(
            dto.copy(
                summary = dto.summary.trim(),
                tags = dto.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(8),
                highlights = dto.highlights.orEmpty().take(5),
            )
        )
    }

    private fun validateChat(raw: String): String? {
        val trimmed = raw.trim()
        return try {
            val el = JsonParser.parseString(trimmed)
            when {
                el.isJsonObject -> {
                    val obj = el.asJsonObject
                    when {
                        obj.has("reply") -> obj.get("reply")?.asString
                        obj.has("message") -> obj.get("message")?.asString
                        else -> gson.fromJson(trimmed, ChatReplyDto::class.java)?.reply
                    }?.takeIf { it.isNotBlank() }
                }
                el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            trimmed.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return trimmed.substring(start, end + 1)
    }

    companion object {
        const val MAX_VOICE_ITEMS = 5
        private const val MAX_VOICE_TEXT = 120
        private const val MAX_VOICE_BODY = 2000
        private const val MAX_VOICE_AMOUNT = 1_000_000_000.0
        private val VOICE_INTENTS = setOf(
            "TRANSACTION", "EXPENSE", "INCOME", "EVENT", "TASK", "EXAM", "REMINDER", "ALARM", "ROUTINE",
            "NOTE", "BILL", "DEBT", "GOAL", "BUDGET"
        )
    }
}
