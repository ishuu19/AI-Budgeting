package com.ledgerai.app.domain.clips

import com.google.gson.JsonParser
import com.ledgerai.app.domain.assistant.Risk

/** One validated suggestion. Every field from the model is [inferred] until the user accepts it. */
data class ClipSuggestion(
    val kind: String,
    val title: String,
    val tags: List<String>,
    val split: List<String>,
    val date: String?,
    val time: String?,
    val place: String?,
    val amount: Double?,
    val currency: String?,
    val company: String?,
    val role: String?,
    val person: String?,
    val item: String?,
    val confidence: String,
    val inferred: Boolean = true,
    val capped: Boolean = false,
)

object ClipFiling {
    const val DAILY_CAP = 100
    const val BATCH = 10
    const val OFF = "off"
    const val SUGGEST = "suggest"
    const val AUTO = "auto"

    private val LOW = setOf("note", "shopping", "place")
    private val MEDIUM = setOf("task", "event", "job", "memory")
    private val HIGH = setOf("bill", "transaction", "debt")

    fun risk(kind: String): Risk? = when (kind) {
        in LOW -> Risk.LOW
        in MEDIUM -> Risk.MEDIUM
        in HIGH -> Risk.HIGH
        else -> null
    }

    /** Auto mode applies low risk only. Suggest never applies. Money never applies on its own. */
    fun autoApplies(kind: String, mode: String): Boolean =
        mode == AUTO && kind in LOW

    fun actionType(kind: String): String? = when (kind) {
        "note" -> "clip.file_note"
        "shopping" -> "clip.file_shopping"
        "task" -> "clip.file_task"
        "event" -> "clip.file_event"
        "job" -> "clip.file_job"
        "memory" -> "clip.file_memory"
        "place" -> "clip.file_place"
        "bill" -> "clip.file_bill"
        "transaction" -> "clip.file_transaction"
        "debt" -> "clip.file_debt"
        else -> null
    }

    fun suggestionLine(kind: String, title: String): String {
        val name = title.ifBlank { kind }
        return when (kind) {
            "event" -> "Suggest: Add event \"$name\""
            "task" -> "Suggest: Add task \"$name\""
            "job" -> "Suggest: Track job \"$name\""
            "shopping" -> "Suggest: Add \"$name\" to shopping"
            "memory" -> "Suggest: Remember \"$name\""
            "place" -> "Suggest: Save place \"$name\""
            "bill" -> "Suggest: Review bill \"$name\""
            "transaction" -> "Suggest: Review transaction \"$name\""
            "debt" -> "Suggest: Review debt \"$name\""
            "note" -> "Suggest: Keep as note \"$name\""
            else -> "Suggest: Keep in notes"
        }
    }
}

object ClipJson {
    private val KINDS = setOf(
        "note", "shopping", "task", "event", "job", "memory", "place", "bill", "transaction", "debt", "other"
    )
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""\d{2}:\d{2}""")
    private val SYMBOL = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "BDT" to "৳", "INR" to "₹", "JPY" to "¥"
    )

    /** Written names that ground a currency code. "40 dollars" is USD, not an invented code. */
    private val CURRENCY_WORDS = mapOf(
        "USD" to listOf("dollar"),
        "EUR" to listOf("euro"),
        "GBP" to listOf("pound"),
        "JPY" to listOf("yen"),
        "BDT" to listOf("taka"),
        "INR" to listOf("rupee"),
    )

    fun parse(raw: String): ClipSuggestion? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject }.getOrNull()
            ?: return null
        return fromObject(root)
    }

    fun parseBatch(raw: String, count: Int): List<ClipSuggestion?> {
        val arrayStart = raw.indexOf('[')
        val objectStart = raw.indexOf('{')
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            val end = raw.lastIndexOf(']')
            if (end <= arrayStart) return List(count) { null }
            val array = runCatching { JsonParser.parseString(raw.substring(arrayStart, end + 1)).asJsonArray }.getOrNull()
                ?: return List(count) { null }
            return List(count) { i ->
                val el = if (i < array.size()) array.get(i) else null
                el?.takeIf { it.isJsonObject }?.asJsonObject?.let { fromObject(it) }
            }
        }
        val one = parse(raw)
        return List(count) { i -> if (i == 0) one else null }
    }

    fun toJson(s: ClipSuggestion): String = buildString {
        append("{\"kind\":").append(quote(s.kind))
        append(",\"title\":").append(quote(s.title))
        append(",\"tags\":[").append(s.tags.joinToString(",") { quote(it) }).append(']')
        append(",\"split\":[").append(s.split.joinToString(",") { quote(it) }).append(']')
        append(",\"fields\":{")
        append("\"date\":").append(s.date?.let { quote(it) } ?: "null")
        append(",\"time\":").append(s.time?.let { quote(it) } ?: "null")
        append(",\"place\":").append(s.place?.let { quote(it) } ?: "null")
        append(",\"amount\":").append(s.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "null")
        append(",\"currency\":").append(s.currency?.let { quote(it) } ?: "null")
        append(",\"company\":").append(s.company?.let { quote(it) } ?: "null")
        append(",\"role\":").append(s.role?.let { quote(it) } ?: "null")
        append(",\"person\":").append(s.person?.let { quote(it) } ?: "null")
        append(",\"item\":").append(s.item?.let { quote(it) } ?: "null")
        append("},\"confidence\":").append(quote(s.confidence))
        append(",\"inferred\":").append(if (s.inferred) "true" else "false")
        if (s.capped) append(",\"capped\":true")
        append('}')
    }

    fun fromStored(raw: String?): ClipSuggestion? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
        if (root.get("capped")?.takeIf { it.isJsonPrimitive }?.asBoolean == true && root.get("kind") == null) {
            return ClipSuggestion(
                kind = "other", title = "", tags = emptyList(), split = emptyList(),
                date = null, time = null, place = null, amount = null, currency = null,
                company = null, role = null, person = null, item = null,
                confidence = "low", inferred = true, capped = true,
            )
        }
        return fromObject(root)
    }

    /**
     * True when every filled field is written in [source]. A null field cannot invent a fact.
     * Currency may be the code for a symbol that is written (¥ → JPY).
     */
    fun grounded(source: String, s: ClipSuggestion): Boolean {
        if (!containsText(source, s.place)) return false
        if (!containsText(source, s.person)) return false
        if (!containsText(source, s.company)) return false
        if (!containsText(source, s.role)) return false
        if (!containsText(source, s.item)) return false
        s.split.forEach { if (!containsText(source, it)) return false }
        if (s.amount != null) {
            val digits = s.amount.toLong().toString()
            if (!source.filter { it.isDigit() }.contains(digits) && !source.contains(digits)) return false
        }
        if (s.date != null) {
            val day = s.date.substringAfterLast('-').trimStart('0').ifBlank { "0" }
            val month = s.date.substring(5, 7).toIntOrNull()
            val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val monthName = month?.let { months.getOrNull(it - 1) }
            val lower = source.lowercase()
            val dayOk = lower.contains(s.date) || Regex("""\b0?$day\b""").containsMatchIn(lower)
            val monthOk = monthName != null && (lower.contains(monthName) || lower.contains(s.date))
            if (!dayOk || !monthOk && !lower.contains(s.date)) return false
        }
        if (s.time != null && !source.contains(s.time) && !source.contains(s.time.trimStart('0'))) {
            val hour = s.time.substringBefore(':').trimStart('0').ifBlank { "0" }
            if (!Regex("""\b0?$hour[:.]\d{2}\b""").containsMatchIn(source)) return false
        }
        if (!s.currency.isNullOrBlank()) {
            val code = s.currency.uppercase()
            val symbol = SYMBOL[code]
            val lower = source.lowercase()
            val words = CURRENCY_WORDS[code].orEmpty()
            val ok = lower.contains(code.lowercase()) ||
                (symbol != null && source.contains(symbol)) ||
                words.any { lower.contains(it) }
            if (!ok) return false
        }
        return true
    }

    private fun fromObject(root: com.google.gson.JsonObject): ClipSuggestion {
        val fields = root.get("fields")?.takeIf { it.isJsonObject }?.asJsonObject
        val kindRaw = root.str("kind")?.trim()?.lowercase().orEmpty()
        val split = strList(root, "split").filter { it.isNotBlank() }.let { if (it.size >= 2) it else emptyList() }
        val date = fields?.str("date")?.trim()?.takeIf { DATE.matches(it) }
        val time = fields?.str("time")?.trim()?.takeIf { TIME.matches(it) }
        return ClipSuggestion(
            kind = if (kindRaw in KINDS) kindRaw else "other",
            title = root.str("title").orEmpty().trim().take(80),
            tags = strList(root, "tags"),
            split = split,
            date = date,
            time = time,
            place = fields.blankToNull("place"),
            amount = fields?.num("amount"),
            currency = fields.blankToNull("currency")?.uppercase(),
            company = fields.blankToNull("company"),
            role = fields.blankToNull("role"),
            person = fields.blankToNull("person"),
            item = fields.blankToNull("item"),
            confidence = if (root.str("confidence") == "high") "high" else "low",
            inferred = root.get("inferred")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
            capped = root.get("capped")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
        )
    }

    private fun containsText(source: String, value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return source.contains(value, ignoreCase = true)
    }

    private fun strList(root: com.google.gson.JsonObject, key: String): List<String> =
        root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
            el.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null }
        }.orEmpty()

    private fun com.google.gson.JsonObject.str(k: String): String? =
        get(k)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString

    private fun com.google.gson.JsonObject.num(k: String): Double? =
        get(k)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun com.google.gson.JsonObject?.blankToNull(k: String): String? =
        this?.str(k)?.trim()?.ifEmpty { null }?.take(200)

    private fun quote(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
        append('"')
    }
}

/** SQL words for "what was that restaurant I copied". Vector recall needs memory_chunks, which is not in Room yet. */
object ClipRecall {
    private val STOP = setOf(
        "what", "was", "that", "the", "a", "an", "i", "copied", "copy", "last", "week", "about", "clip", "clips", "me", "my"
    )

    fun terms(question: String): List<String> =
        question.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP }
            .distinct()
            .take(6)

    /** Null until Stage 3 adds memory_chunks. Callers use [terms] and SQL first. */
    fun vectorQuery(@Suppress("UNUSED_PARAMETER") question: String): String? = null
}
