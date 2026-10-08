package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Offline heuristic parse for voice/text stubs.
 * Kept public so unit tests can cover extraction without Android deps.
 */
object QuickParse {

    /** Weekday bits matching AlarmItem.repeatDays (Sun=1 … Sat=64). */
    const val BIT_SUN = 1
    const val BIT_MON = 2
    const val BIT_TUE = 4
    const val BIT_WED = 8
    const val BIT_THU = 16
    const val BIT_FRI = 32
    const val BIT_SAT = 64
    const val MASK_WEEKDAYS = BIT_MON or BIT_TUE or BIT_WED or BIT_THU or BIT_FRI
    const val MASK_WEEKENDS = BIT_SUN or BIT_SAT
    const val MASK_EVERY_DAY = 127

    private val STOP_NAMES = setOf(
        "today", "tomorrow", "yesterday", "morning", "night", "lunch", "dinner",
        "breakfast", "coffee", "dollars", "bucks", "cash", "card", "week", "next"
    )

    private val AMOUNT_REGEX = Regex("""[$£€]?\s*(\d+(?:[.,]\d{1,2})?)""")

    private val BILL_WORD = Regex("""(?i)\b(bills?|subscriptions?)\b""")
    private val DUE_WORD = Regex("""(?i)\bdue\b""")
    private val DEBT_I_OWE = Regex("""(?i)\b(?:i\s+owe|borrowed|borrow)\b""")
    private val DEBT_THEY_OWE = Regex("""(?i)\b(?:they\s+owe|owes\s+me|lent|lend)\b""")
    private val GOAL_HINT = Regex("""(?i)\b(save|goal|savings)\b""")
    private val MONEY_VERB = Regex("""(?i)\b(spent|paid|bought|cost|dollars|bucks)\b""")
    private val FRIEND_AFTER_VERB = Regex(
        """(?i)\b(?:owe(?:s|d)?(?:\s+me)?|lent|lend|borrowed|borrow)\s+(?:me\s+)?(?:from\s+)?([A-Za-z][\w'-]*)"""
    )
    private val NAME_FILLER = setOf(
        "today", "tomorrow", "yesterday", "morning", "night", "week", "next",
        "bill", "bills", "subscription", "subscriptions", "due", "weekly",
        "monthly", "yearly", "annual", "annually", "quarterly", "save", "goal",
        "savings", "owe", "owes", "owed", "lent", "lend", "borrowed", "borrow",
        "they", "from", "dollars", "dollar", "bucks", "me", "my", "a", "an",
        "the", "for", "to", "on", "of", "add", "i", "paid", "pay"
    )

    fun extractAmount(input: String): Double? =
        AMOUNT_REGEX.find(input)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()

    fun parseRelativeDate(input: String, today: LocalDate): LocalDate {
        val lower = input.lowercase()
        return when {
            lower.contains("yesterday") -> today.minusDays(1)
            lower.contains("tomorrow") -> today.plusDays(1)
            lower.contains("next week") -> today.plusWeeks(1)
            else -> today
        }
    }

    /** When no date is in the utterance, uses [today]; time defaults to 9:00 if not spoken. */
    fun resolveEventDateTimeFromText(input: String, today: LocalDate = LocalDate.now()): LocalDateTime {
        val date = parseRelativeDate(input, today)
        val time = parseClockTimeFromText(input.lowercase()) ?: LocalTime.of(9, 0)
        return LocalDateTime.of(date, time)
    }

    fun parseBillFrequency(input: String): BillFrequency {
        val lower = input.lowercase()
        return when {
            lower.containsAny("weekly", "every week", "once a week") -> BillFrequency.WEEKLY
            lower.containsAny("yearly", "annual", "annually", "every year") -> BillFrequency.YEARLY
            lower.containsAny("quarterly") -> BillFrequency.QUARTERLY
            else -> BillFrequency.MONTHLY
        }
    }

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedTransaction {
        val amount = extractAmount(input)
        val lower = input.lowercase()
        val category = when {
            lower.containsAny(
                "food", "lunch", "dinner", "breakfast", "coffee",
                "restaurant", "grocery", "groceries", "eat", "meal"
            ) -> TransactionCategory.FOOD
            lower.containsAny(
                "uber", "lyft", "gas", "fuel", "taxi", "bus", "train",
                "transport", "metro", "fare"
            ) -> TransactionCategory.TRANSPORT
            lower.containsAny(
                "netflix", "spotify", "subscription", "hulu",
                "disney", "apple tv", "prime"
            ) -> TransactionCategory.SUBSCRIPTIONS
            lower.containsAny(
                "movie", "game", "concert", "entertainment",
                "cinema", "theatre"
            ) -> TransactionCategory.ENTERTAINMENT
            lower.containsAny(
                "amazon", "shopping", "clothes", "shoes",
                "store", "mall", "buy"
            ) -> TransactionCategory.SHOPPING
            lower.containsAny(
                "doctor", "pharmacy", "gym", "health",
                "medicine", "hospital"
            ) -> TransactionCategory.HEALTH
            lower.containsAny(
                "electric", "water", "internet", "utility",
                "bill", "wifi"
            ) -> TransactionCategory.UTILITIES
            lower.containsAny("rent", "mortgage", "housing") -> TransactionCategory.RENT
            lower.containsAny(
                "salary", "paycheck", "income", "paid me",
                "received", "earned"
            ) -> TransactionCategory.SALARY
            else -> TransactionCategory.OTHER
        }
        val type = if (category == TransactionCategory.SALARY ||
            lower.containsAny("received", "earned", "income", "got paid", "deposit")
        ) TransactionType.INCOME else TransactionType.EXPENSE

        val merchant = extractName(input)
        val note = if (merchant.isBlank()) input.trim() else ""

        return ParsedTransaction(
            amount = amount,
            category = category,
            merchant = merchant,
            date = parseRelativeDate(input, today),
            note = note,
            type = type
        )
    }

    /**
     * Pulls a person, shop, or place out of a sentence.
     * Prefers "at/from/with/to Name" and known brands over dumping the whole line into a note.
     */
    fun extractName(input: String): String {
        val brands = listOf(
            "starbucks", "mcdonald's", "mcdonalds", "amazon", "netflix", "spotify",
            "uber", "lyft", "walmart", "target", "costco", "shell", "apple"
        )
        brands.firstOrNull { input.contains(it, ignoreCase = true) }?.let { brand ->
            return brand.replaceFirstChar { it.uppercase() }
        }
        val named = Regex(
            """(?i)\b(?:at|from|with|to|for)\s+([A-Za-z][\w'&.-]*(?:\s+[A-Za-z][\w'&.-]*){0,2})"""
        ).findAll(input).map { it.groupValues[1].trim() }.lastOrNull()
        if (!named.isNullOrBlank() && named.lowercase() !in STOP_NAMES) {
            return named.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }
        return input.split(" ").lastOrNull { word ->
            word.length > 2 && word[0].isUpperCase() && word.none { it.isDigit() }
        }.orEmpty()
    }

    /**
     * Offline multi-intent router.
     * Alarm / reminder / routine / task win over money. Bills, debts, and goals
     * need their own keywords so "spent 15 on netflix" stays a transaction.
     */
    fun parseVoiceIntent(transcript: String, today: LocalDate = LocalDate.now()): ParsedIntent {
        val trimmed = transcript.trim()
        val lower = trimmed.lowercase()
        val amount = extractAmount(trimmed)
        return when {
            lower.containsAny("alarm", "wake me", "set an alarm") -> {
                val time = parseClockTimeFromText(lower) ?: LocalTime.of(7, 0)
                ParsedIntent.Alarm(
                    label = "Alarm",
                    time = time,
                    repeatDays = parseRepeatDays(trimmed),
                    rawTranscript = trimmed,
                )
            }
            lower.containsAny("remind me", "reminder") ->
                ParsedIntent.Reminder(
                    title = trimmed.take(80),
                    label = "Reminder",
                    remindAt = resolveEventDateTimeFromText(trimmed, today),
                    rawTranscript = trimmed,
                )
            lower.containsAny("routine", "habit") -> {
                val title = trimmed
                    .replace(Regex("(?i)^(add\\s+)?(a\\s+)?(daily\\s+|weekly\\s+)?(routine|habit)\\s*(to\\s+|for\\s+)?"), "")
                    .trim()
                    .ifBlank { trimmed }
                ParsedIntent.Routine(
                    title = title.take(120),
                    notes = "",
                    repeatRule = parseRepeatRule(trimmed),
                    rawTranscript = trimmed,
                )
            }
            lower.containsAny("add task", "to-do", "todo", "task:") -> {
                val stripped = trimmed
                    .replace(Regex("(?i)^(add\\s+task|todo|to-do|task:)\\s*"), "")
                    .trim()
                    .ifBlank { trimmed }
                ParsedIntent.Task(
                    title = stripped,
                    dueAt = resolveEventDateTimeFromText(stripped, today),
                    rawTranscript = trimmed
                )
            }
            amount != null && isBillUtterance(trimmed) -> {
                val parsed = parse(trimmed, today)
                ParsedIntent.Bill(
                    name = extractBillName(trimmed).ifBlank { "Bill" },
                    amount = amount,
                    frequency = parseBillFrequency(trimmed),
                    nextDueDate = parseRelativeDate(trimmed, today),
                    category = parsed.category.takeUnless { it == TransactionCategory.OTHER }
                        ?: TransactionCategory.SUBSCRIPTIONS,
                    rawTranscript = trimmed,
                )
            }
            amount != null && isDebtUtterance(trimmed) -> {
                val dueMentioned = lower.containsAny("yesterday", "tomorrow", "next week", "due")
                ParsedIntent.Debt(
                    friendName = extractFriendName(trimmed).ifBlank { "Friend" },
                    amount = amount,
                    direction = if (DEBT_I_OWE.containsMatchIn(trimmed)) {
                        DebtDirection.I_OWE
                    } else {
                        DebtDirection.THEY_OWE
                    },
                    dueDate = if (dueMentioned) parseRelativeDate(trimmed, today) else null,
                    rawTranscript = trimmed,
                )
            }
            amount != null && GOAL_HINT.containsMatchIn(trimmed) ->
                ParsedIntent.Goal(
                    name = extractGoalName(trimmed).ifBlank { "Goal" },
                    targetAmount = amount,
                    rawTranscript = trimmed,
                )
            amount != null && MONEY_VERB.containsMatchIn(trimmed) ->
                ParsedIntent.Transaction.from(parse(trimmed, today), trimmed)
            lower.containsAny("note:", "write note", "remember that") ->
                ParsedIntent.Note(
                    title = "Note",
                    body = trimmed,
                    rawTranscript = trimmed,
                )
            else -> ParsedIntent.Transaction.from(parse(trimmed, today), trimmed)
        }
    }

    /**
     * Alarm weekday bitmask from natural language.
     * Defaults to 0 (one-shot). Recognizes weekdays / every day / weekends / day names.
     */
    fun parseRepeatDays(transcript: String): Int {
        val lower = transcript.lowercase()
        when {
            lower.containsAny("every day", "everyday", "daily", "all week") -> return MASK_EVERY_DAY
            lower.containsAny("weekdays", "weekday", "monday to friday", "mon to fri", "mon-fri") ->
                return MASK_WEEKDAYS
            lower.containsAny("weekends", "weekend") -> return MASK_WEEKENDS
        }

        var mask = 0
        if (lower.contains("sunday") || Regex("""\bsun\b""").containsMatchIn(lower)) mask = mask or BIT_SUN
        if (lower.contains("monday") || Regex("""\bmon\b""").containsMatchIn(lower)) mask = mask or BIT_MON
        if (lower.contains("tuesday") || Regex("""\btue\b""").containsMatchIn(lower)) mask = mask or BIT_TUE
        if (lower.contains("wednesday") || Regex("""\bwed\b""").containsMatchIn(lower)) mask = mask or BIT_WED
        if (lower.contains("thursday") || Regex("""\bthu\b""").containsMatchIn(lower)) mask = mask or BIT_THU
        if (lower.contains("friday") || Regex("""\bfri\b""").containsMatchIn(lower)) mask = mask or BIT_FRI
        if (lower.contains("saturday") || Regex("""\bsat\b""").containsMatchIn(lower)) mask = mask or BIT_SAT
        return mask
    }

    /** Maps transcript phrases to routine repeatRule tokens used by RoutinesScreen. */
    fun parseRepeatRule(transcript: String): String {
        val lower = transcript.lowercase()
        return when {
            lower.containsAny("weekdays", "weekday", "monday to friday", "mon-fri") -> "WEEKDAYS"
            lower.containsAny("weekly", "every week", "once a week") -> "WEEKLY"
            lower.containsAny("every day", "everyday", "daily") -> "DAILY"
            else -> "DAILY"
        }
    }

    fun parseClockTimeFromText(lower: String): LocalTime? {
        return Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
            .find(lower)
            ?.let { m ->
                var h = m.groupValues[1].toIntOrNull() ?: return@let null
                val min = m.groupValues[2].toIntOrNull() ?: 0
                val ampm = m.groupValues[3]
                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0
                runCatching { LocalTime.of(h.coerceIn(0, 23), min.coerceIn(0, 59)) }.getOrNull()
            }
    }

    private fun isDebtUtterance(text: String): Boolean =
        DEBT_I_OWE.containsMatchIn(text) || DEBT_THEY_OWE.containsMatchIn(text)

    /** Bills need bill/subscription, or "due" when the line is not a debt. */
    private fun isBillUtterance(text: String): Boolean {
        if (BILL_WORD.containsMatchIn(text)) return true
        return DUE_WORD.containsMatchIn(text) && !isDebtUtterance(text)
    }

    private fun extractBillName(input: String): String {
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private fun extractFriendName(input: String): String {
        val fromVerb = FRIEND_AFTER_VERB.find(input)?.groupValues?.get(1)
        if (!fromVerb.isNullOrBlank() && fromVerb.lowercase() !in NAME_FILLER) {
            return fromVerb.replaceFirstChar { it.uppercase() }
        }
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private fun extractGoalName(input: String): String {
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private fun remainingName(input: String): String {
        val withoutAmount = AMOUNT_REGEX.replace(input, " ")
        return withoutAmount.split(Regex("""\s+"""))
            .map { it.trim(',', '.', '!', '?', '"', '\'') }
            .filter { it.isNotBlank() && it.lowercase() !in NAME_FILLER && it.none(Char::isDigit) }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .trim()
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
}
