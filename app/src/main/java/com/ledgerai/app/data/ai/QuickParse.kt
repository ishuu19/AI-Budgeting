package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.RecurrenceFrequency
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.domain.schedule.AlarmDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Offline heuristic parse for voice/text stubs.
 * Kept public so unit tests can cover extraction without Android deps.
 */
object QuickParse {

    /** Weekday bits matching CalendarEvent.alarmRepeatDays (Sun=1 ... Sat=64). */
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

    private val WEEKDAY_NAMES = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY
    )
    private val MONTH_NAMES = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    )
    private const val MONTH_ALT =
        "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|" +
            "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val ISO_DATE =Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val IN_N = Regex("""(?i)\bin\s+(\d{1,2})\s+(day|days|week|weeks)\b""")
    private val MONTH_DAY = Regex(
        """(?i)\b($MONTH_ALT)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b"""
    )
    private val DAY_MONTH = Regex(
        """(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH_ALT)\b"""
    )

    /**
     * Date for calendar items. Understands today, tomorrow, next week, "in 3 days", weekday names,
     * "october 12", "12 october" and ISO dates. Falls back to [today] when no date is spoken.
     */
    fun parseEventDate(input: String, today: LocalDate = LocalDate.now()): LocalDate {
        val lower = input.lowercase()
        ISO_DATE.find(lower)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                .getOrNull()?.let { return it }
        }
        IN_N.find(lower)?.let { m ->
            val n = m.groupValues[1].toLong()
            return if (m.groupValues[2].startsWith("week")) today.plusWeeks(n) else today.plusDays(n)
        }
        val monthDay = MONTH_DAY.find(lower) ?: DAY_MONTH.find(lower)
        if (monthDay != null) {
            val values = monthDay.groupValues
            val monthToken = if (values[1].all { it.isDigit() }) values[2] else values[1]
            val dayToken = if (values[1].all { it.isDigit() }) values[1] else values[2]
            val month = MONTH_NAMES.indexOf(monthToken.take(3)) + 1
            val day = dayToken.toIntOrNull()
            if (month > 0 && day != null) {
                runCatching { LocalDate.of(today.year, month, day) }.getOrNull()?.let { d ->
                    return if (d.isBefore(today)) d.plusYears(1) else d
                }
            }
        }
        return when {
            lower.contains("tomorrow") -> today.plusDays(1)
            lower.contains("next week") -> today.plusWeeks(1)
            else -> WEEKDAY_NAMES.entries.firstOrNull { Regex("""\b${it.key}\b""").containsMatchIn(lower) }
                ?.let { (_, dow) ->
                    var d = today.plusDays(1)
                    while (d.dayOfWeek != dow) d = d.plusDays(1)
                    d
                }
                ?: today
        }
    }

    /** Spoken date and time; time defaults to 9:00 if not spoken. */
    fun resolveEventDateTimeFromText(input: String, today: LocalDate = LocalDate.now()): LocalDateTime {
        val date = parseEventDate(input, today)
        val time = parseClockTimeFromText(input.lowercase()) ?: LocalTime.of(9, 0)
        return LocalDateTime.of(date, time)
    }

    private val TRIGGER_PREFIX = Regex(
        """(?i)^\s*(?:please\s+)?(?:set\s+)?(?:a\s+|an\s+)?(?:remind\s+me(?:\s+(?:to|about|that|of))?|reminder(?:\s+(?:to|about|for|that))?|add\s+task|add\s+a\s+task|todo|to-do|task|schedule(?:\s+an?)?|add(?:\s+an?)?\s+(?:event|exam|meeting|appointment))\s*:?\s*"""
    )
    private val WHEN_PHRASES = listOf(
        Regex("""(?i)\b(?:on\s+)?(?:next\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b"""),
        Regex("""(?i)\bin\s+\d{1,2}\s+(?:days?|weeks?)\b"""),
        Regex("""(?i)\b(?:today|tonight|tomorrow|next\s+week)\b"""),
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),
        Regex("""(?i)\b(?:on\s+)?$MONTH_ALT\.?\s+\d{1,2}(?:st|nd|rd|th)?\b"""),
        Regex("""(?i)\b(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm)\b"""),
        Regex("""(?i)\bat\s+\d{1,2}(?::\d{2})?\b"""),
        Regex("""(?i)\b(?:at\s+)?(?:noon|midnight)\b"""),
    )

    /** The thing to do, with the trigger words and the spoken date and time removed. */
    fun eventTitleFromText(input: String, fallback: String): String {
        var t = TRIGGER_PREFIX.replace(input.trim(), "")
        WHEN_PHRASES.forEach { t = it.replace(t, " ") }
        t = t.replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '!', '?', '-')
        t = t.replace(Regex("""(?i)\s+(?:on|at|for|by)$"""), "").trim()
        return t.replaceFirstChar { it.uppercase() }.ifBlank { fallback }
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

    private val BUDGET_WORD = Regex("""(?i)\bbudget\b""")
    private val NOTE_PREFIX = Regex("""(?i)^\s*(?:(?:please\s+)?(?:write|add|make)\s+(?:a\s+)?note|note|remember\s+that)\s*[:\-]?\s*(?:that\s+)?""")

    /** Longer separators first so "and then" is not cut at "then". */
    private val HARD_SPLIT = Regex("""(?i)\s*(?:;|,?\s*and\s+then\b|,\s*then\b|\bthen\b|\balso\b)\s*""")
    private val AND_SPLIT = Regex("""(?i)\s*,?\s+and\s+""")
    private const val MAX_ITEMS = 5

    /**
     * Offline multi-intent router for one clause.
     * Alarm / reminder / routine / task win over money. Bills, debts, goals and budgets
     * need their own keywords so "spent 15 on netflix" stays a transaction.
     * Text that matches nothing becomes [ParsedIntent.Unmatched]; it is never guessed into a transaction
     * unless an amount was spoken.
     */
    fun parseVoiceIntent(transcript: String, today: LocalDate = LocalDate.now()): ParsedIntent {
        val trimmed = transcript.trim()
        return matchIntent(trimmed, today, loose = true) ?: ParsedIntent.Unmatched(rawTranscript = trimmed)
    }

    /**
     * One utterance, several items: "spent 12 on lunch then remind me to call mom at 5 pm".
     * Splits on ";", "then", "and then", "also", and on "and" when both sides are clear intents.
     * Always returns at least one element.
     */
    fun parseVoiceIntents(transcript: String, today: LocalDate = LocalDate.now()): List<ParsedIntent> {
        val trimmed = transcript.trim()
        val parts = trimmed.split(HARD_SPLIT).map { it.trim(' ', ',', '.') }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return parseClause(trimmed, today).take(MAX_ITEMS)
        val all = parts.flatMap { parseClause(it, today) }
        val matched = all.filter { it !is ParsedIntent.Unmatched }
        val kept = if (matched.isEmpty()) emptyList() else all.filter {
            it !is ParsedIntent.Unmatched || it.rawTranscript.split(' ').size > 2
        }
        return kept.ifEmpty { listOf(ParsedIntent.Unmatched(rawTranscript = trimmed)) }.take(MAX_ITEMS)
    }

    private fun parseClause(clause: String, today: LocalDate): List<ParsedIntent> {
        for (m in AND_SPLIT.findAll(clause)) {
            val left = clause.substring(0, m.range.first).trim()
            val right = clause.substring(m.range.last + 1).trim()
            if (left.split(' ').size < 2 || right.split(' ').size < 2) continue
            if (matchIntent(left, today, loose = false) != null && matchIntent(right, today, loose = false) != null) {
                return parseClause(left, today) + parseClause(right, today)
            }
        }
        return listOf(parseVoiceIntent(clause, today))
    }

    private fun matchIntent(trimmed: String, today: LocalDate, loose: Boolean): ParsedIntent? {
        val lower = trimmed.lowercase()
        val amount = extractAmount(trimmed)
        return when {
            lower.containsAny("alarm", "wake me", "set an alarm") -> {
                val time = parseAlarmTime(lower) ?: LocalTime.of(7, 0)
                val days = parseRepeatDays(trimmed)
                val start = LocalDateTime.of(parseEventDate(trimmed, today), time)
                ParsedIntent.Event(
                    title = "Alarm",
                    startAt = start,
                    kind = CalendarEventKind.ALARM,
                    repeat = if (days == 0) null else repeatFromAlarmMask(days),
                    rawTranscript = trimmed,
                )
            }
            lower.containsAny("remind me", "reminder") -> {
                val start = resolveEventDateTimeFromText(trimmed, today)
                ParsedIntent.Event(
                    title = eventTitleFromText(trimmed, "Reminder").take(120),
                    startAt = start,
                    kind = CalendarEventKind.TASK,
                    reminders = listOf(EventReminder(label = "At time", offsetMinutes = 0)),
                    rawTranscript = trimmed,
                )
            }
            lower.containsAny("routine", "habit") -> {
                val title = trimmed
                    .replace(Regex("(?i)^(add\\s+)?(a\\s+)?(daily\\s+|weekly\\s+)?(routine|habit)\\s*(to\\s+|for\\s+)?"), "")
                    .trim()
                    .ifBlank { trimmed }
                val start = resolveEventDateTimeFromText(trimmed, today)
                ParsedIntent.Event(
                    title = eventTitleFromText(title, title).take(120),
                    startAt = start,
                    endAt = start.plusMinutes(30),
                    kind = CalendarEventKind.ROUTINE,
                    repeat = repeatFromRule(parseRepeatRule(trimmed), start.toLocalDate()),
                    rawTranscript = trimmed,
                )
            }
            lower.containsAny("add task", "to-do", "todo", "task:") -> {
                val stripped = trimmed
                    .replace(Regex("(?i)^(add\\s+task|todo|to-do|task:)\\s*"), "")
                    .trim()
                    .ifBlank { trimmed }
                ParsedIntent.Event(
                    title = eventTitleFromText(stripped, stripped),
                    startAt = resolveEventDateTimeFromText(stripped, today),
                    kind = CalendarEventKind.TASK,
                    rawTranscript = trimmed
                )
            }
            lower.containsAny("exam", "meeting", "appointment", "schedule a", "schedule an") -> {
                val start = resolveEventDateTimeFromText(trimmed, today)
                ParsedIntent.Event(
                    title = eventTitleFromText(trimmed, trimmed).take(120),
                    startAt = start,
                    endAt = start.plusHours(1),
                    kind = if (lower.contains("exam")) CalendarEventKind.EXAM else CalendarEventKind.EVENT,
                    rawTranscript = trimmed,
                )
            }
            amount != null && BUDGET_WORD.containsMatchIn(trimmed) ->
                ParsedIntent.Budget(
                    category = parse(trimmed, today).category,
                    limit = amount,
                    rawTranscript = trimmed,
                )
            amount != null && isBillUtterance(trimmed) -> billFrom(trimmed, amount, today)
            amount != null && isDebtUtterance(trimmed) -> debtFrom(trimmed, amount, today)
            amount != null && GOAL_HINT.containsMatchIn(trimmed) -> goalFrom(trimmed, amount)
            amount != null && MONEY_VERB.containsMatchIn(trimmed) ->
                ParsedIntent.Transaction.from(parse(trimmed, today), trimmed)
            lower.containsAny("note:", "write note", "write a note", "remember that") -> noteFrom(trimmed)
            loose && amount != null -> ParsedIntent.Transaction.from(parse(trimmed, today), trimmed)
            else -> null
        }
    }

    private fun billFrom(text: String, amount: Double, today: LocalDate): ParsedIntent.Bill {
        val parsed = parse(text, today)
        return ParsedIntent.Bill(
            name = extractBillName(text).ifBlank { "Bill" },
            amount = amount,
            frequency = parseBillFrequency(text),
            nextDueDate = parseRelativeDate(text, today),
            category = parsed.category.takeUnless { it == TransactionCategory.OTHER }
                ?: TransactionCategory.SUBSCRIPTIONS,
            rawTranscript = text,
        )
    }

    private fun debtFrom(text: String, amount: Double, today: LocalDate): ParsedIntent.Debt {
        val dueMentioned = text.lowercase().containsAny("yesterday", "tomorrow", "next week", "due")
        return ParsedIntent.Debt(
            friendName = extractFriendName(text).ifBlank { "Friend" },
            amount = amount,
            direction = debtDirection(text),
            dueDate = if (dueMentioned) parseRelativeDate(text, today) else null,
            rawTranscript = text,
        )
    }

    private fun goalFrom(text: String, amount: Double) = ParsedIntent.Goal(
        name = extractGoalName(text).ifBlank { "Goal" },
        targetAmount = amount,
        rawTranscript = text,
    )

    private fun noteFrom(text: String): ParsedIntent.Note {
        val body = NOTE_PREFIX.replace(text, "").trim().ifBlank { text }
        val title = body.split(' ').take(6).joinToString(" ").trim(',', '.', '!', '?')
            .replaceFirstChar { it.uppercase() }.ifBlank { "Note" }
        return ParsedIntent.Note(title = title, body = body, rawTranscript = text)
    }

    /** "I owe" and "borrowed" mean I owe; everything else is owed to me. */
    fun debtDirection(text: String): DebtDirection =
        if (DEBT_I_OWE.containsMatchIn(text)) DebtDirection.I_OWE else DebtDirection.THEY_OWE

    /**
     * Forces [transcript] into [kind] (used by "change result, redo" on a history row).
     * Fields the text does not provide stay empty or zero, so the confirm card asks for them.
     */
    fun asKind(kind: VoiceResultKind, transcript: String, today: LocalDate = LocalDate.now()): ParsedIntent {
        val text = transcript.trim()
        val natural = parseVoiceIntent(text, today)
        if (natural !is ParsedIntent.Unmatched && natural.resultKind() == kind) return natural
        val amount = extractAmount(text) ?: 0.0
        return when (kind) {
            VoiceResultKind.Spend, VoiceResultKind.Income -> {
                val tx = ParsedIntent.Transaction.from(parse(text, today), text)
                tx.copy(type = if (kind == VoiceResultKind.Income) TransactionType.INCOME else TransactionType.EXPENSE)
            }
            VoiceResultKind.Task, VoiceResultKind.Reminder, VoiceResultKind.Event, VoiceResultKind.Exam,
            VoiceResultKind.Routine, VoiceResultKind.Alarm -> {
                val start = resolveEventDateTimeFromText(text, today)
                val ek = when (kind) {
                    VoiceResultKind.Event -> CalendarEventKind.EVENT
                    VoiceResultKind.Exam -> CalendarEventKind.EXAM
                    VoiceResultKind.Routine -> CalendarEventKind.ROUTINE
                    VoiceResultKind.Alarm -> CalendarEventKind.ALARM
                    else -> CalendarEventKind.TASK
                }
                val timed = ek == CalendarEventKind.EVENT || ek == CalendarEventKind.EXAM
                val days = if (ek == CalendarEventKind.ALARM) parseRepeatDays(text) else 0
                ParsedIntent.Event(
                    title = if (ek == CalendarEventKind.ALARM) "Alarm" else eventTitleFromText(text, text).take(120),
                    startAt = start,
                    endAt = when {
                        timed -> start.plusHours(1)
                        ek == CalendarEventKind.ROUTINE -> start.plusMinutes(30)
                        else -> null
                    },
                    kind = ek,
                    repeat = when {
                        ek == CalendarEventKind.ROUTINE -> repeatFromRule(parseRepeatRule(text), start.toLocalDate())
                        days != 0 -> repeatFromAlarmMask(days)
                        else -> null
                    },
                    reminders = if (kind == VoiceResultKind.Reminder) {
                        listOf(EventReminder(label = "At time", offsetMinutes = 0))
                    } else emptyList(),
                    rawTranscript = text,
                )
            }
            VoiceResultKind.Budget -> ParsedIntent.Budget(parse(text, today).category, amount, rawTranscript = text)
            VoiceResultKind.Bill -> billFrom(text, amount, today)
            VoiceResultKind.Debt -> debtFrom(text, amount, today)
            VoiceResultKind.Goal -> goalFrom(text, amount)
            VoiceResultKind.Note, VoiceResultKind.Unsorted -> noteFrom(text)
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

    /** Repeat rule for a weekly alarm from a Sun=1 ... Sat=64 mask. */
    fun repeatFromAlarmMask(mask: Int): EventRecurrence =
        EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = AlarmDays.toIsoDays(mask))

    /** Maps a DAILY | WEEKLY | WEEKDAYS token to a recurrence anchored at [start]. */
    fun repeatFromRule(rule: String, start: LocalDate): EventRecurrence = when (rule.uppercase()) {
        "WEEKDAYS" -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5))
        "WEEKLY" -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(start.dayOfWeek.value))
        else -> EventRecurrence(frequency = RecurrenceFrequency.DAILY)
    }

    private val CLOCK_AMPM = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
    private val CLOCK_COLON = Regex("""\b(\d{1,2}):(\d{2})\b""")
    private val CLOCK_AT = Regex("""\b(?:at|for|by|around)\s+(\d{1,2})\b""")

    /** A spoken clock time: "7:30 am", "7 pm", "at 14:00", "at 7", "noon", "midnight". Null if none. */
    fun parseClockTimeFromText(lower: String): LocalTime? {
        if (Regex("""\bnoon\b""").containsMatchIn(lower)) return LocalTime.NOON
        if (Regex("""\bmidnight\b""").containsMatchIn(lower)) return LocalTime.MIDNIGHT
        CLOCK_AMPM.find(lower)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val pm = m.groupValues[3] == "pm"
            if (pm && h < 12) h += 12
            if (!pm && h == 12) h = 0
            return runCatching { LocalTime.of(h, min) }.getOrNull()
        }
        CLOCK_COLON.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: return@let
            return runCatching { LocalTime.of(h, min) }.getOrNull()
        }
        CLOCK_AT.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            return runCatching { LocalTime.of(h, 0) }.getOrNull()
        }
        return null
    }

    /** Alarms also accept a bare number ("alarm 7") when no clearer time is spoken. */
    fun parseAlarmTime(lower: String): LocalTime? =
        parseClockTimeFromText(lower)
            ?: Regex("""\b(\d{1,2})\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { h -> runCatching { LocalTime.of(h, 0) }.getOrNull() }

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
