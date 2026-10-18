package com.ledgerai.app.domain.notebook

import com.google.gson.JsonParser
import com.ledgerai.app.domain.assistant.ProposedAction

data class NotebookAnswer(
    val title: String,
    val answer: String,
    val actions: List<ProposedAction>,
)

object NotebookActions {
    /** The worker stores the plan. The executor runs only after a tap. */
    fun applyNow(userTapped: Boolean): Boolean = userTapped
}

object NotebookJson {
    private val MONEY = Regex("""[$£€৳₹¥]\s*\d[\d,]*(?:\.\d+)?|\b\d[\d,]*(?:\.\d+)?\s*(?:dollars?|yen|euros?|pounds?|usd|eur|gbp|jpy)\b""", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val UNKNOWN = listOf("don't know", "do not know", "unknown", "no price", "not shown", "not in the", "can't tell", "cannot tell")

    fun accept(source: String, raw: String, question: String): NotebookAnswer? {
        val parsed = parse(raw) ?: return null
        if (wordCount(parsed.title) !in 2..5) return null
        if (parsed.answer.isBlank()) return null
        if (!grounded(source, question, parsed)) return null
        return parsed.copy(actions = parsed.actions.map { it.copy(risk = null) })
    }

    fun store(answer: NotebookAnswer): String = buildString {
        append("{\"title\":").append(quote(answer.title))
        append(",\"answer\":").append(quote(answer.answer))
        append(",\"actions\":[")
        answer.actions.forEachIndexed { index, action ->
            if (index > 0) append(',')
            append("{\"type\":").append(quote(action.type))
            append(",\"args\":{")
            action.args.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) append(',')
                append(quote(key)).append(':')
                append(jsonValue(value))
            }
            append("},\"inferred\":true}")
        }
        append("]}")
    }

    fun actionsFrom(raw: String?): List<ProposedAction> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        return readActions(root.getAsJsonArray("actions"))
    }

    private fun parse(raw: String): NotebookAnswer? {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { JsonParser.parseString(cleaned.substring(start, end + 1)).asJsonObject }.getOrNull()
            ?: return null
        val title = root.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val answer = root.get("answer")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (title.isEmpty() || answer.isEmpty()) return null
        return NotebookAnswer(title, answer, readActions(root.getAsJsonArray("actions")))
    }

    private fun grounded(source: String, question: String, answer: NotebookAnswer): Boolean {
        if (inventedMoney(source, answer.answer)) return false
        if (inventedDate(source, answer.answer)) return false
        if (inventedArg(source, answer.actions)) return false
        if (asksPrice(question) && !hasPrice(source)) {
            val lower = answer.answer.lowercase()
            if (UNKNOWN.none { lower.contains(it) }) return false
        }
        return true
    }

    private fun asksPrice(question: String): Boolean {
        val q = question.lowercase()
        return q.contains("price") || q.contains("cost") || q.contains("how much")
    }

    private fun hasPrice(source: String): Boolean =
        source.contains('¥') || source.contains('$') || source.contains('£') || source.contains('€') ||
            Regex("""\b(?:dollars?|yen|euros?|pounds?|usd|eur|gbp|jpy)\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)

    private fun inventedMoney(source: String, text: String): Boolean =
        MONEY.findAll(text).any { match ->
            val digits = match.value.filter { it.isDigit() }
            digits.isNotEmpty() && !source.filter { it.isDigit() }.contains(digits) && !source.contains(digits)
        }

    private fun inventedDate(source: String, text: String): Boolean =
        DATE.findAll(text).any { !source.contains(it.value) }

    private fun inventedArg(source: String, actions: List<ProposedAction>): Boolean =
        actions.any { action ->
            action.args.values.any { value ->
                val digits = when (value) {
                    is Number -> {
                        val number = value.toDouble()
                        if (number % 1.0 == 0.0) number.toLong().toString() else number.toString().filter { it.isDigit() }
                    }
                    else -> value?.toString()?.filter { it.isDigit() }.orEmpty()
                }
                digits.length >= 2 && !source.filter { it.isDigit() }.contains(digits) && !source.contains(digits)
            }
        }

    private fun readActions(array: com.google.gson.JsonArray?): List<ProposedAction> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val args = obj.getAsJsonObject("args")?.entrySet()?.associate { (key, value) ->
                key to when {
                    value.isJsonNull -> null
                    value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble
                    value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                    value.isJsonPrimitive -> value.asString
                    else -> value.toString()
                }
            } ?: emptyMap()
            ProposedAction(type, args, risk = null)
        }
    }

    private fun wordCount(title: String): Int =
        title.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> value.toString()
        else -> quote(value.toString())
    }
}
