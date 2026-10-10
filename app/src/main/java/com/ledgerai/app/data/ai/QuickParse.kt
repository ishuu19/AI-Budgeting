package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.EventRecurrence
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.JobDateNote
import com.ledgerai.app.domain.model.JobDates
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
 * Offline rule engine for voice and typed entries: speech normalisation, amounts, dates, categories and
 * intent kinds, driven by phrase tables ([RuleLexicon]) and regex tables.
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

    /** Phrase tables loaded from assets. Set once at start-up; the built-in tables are always consulted too. */
    @Volatile
    var lexicon: RuleLexicon = RuleLexicon.EMPTY

    private val builtin get() = BuiltinRules.lexicon

    internal fun merchantHit(text: String): RuleLexicon.MerchantHit? = lexicon.merchant(text) ?: builtin.merchant(text)
    internal fun keywordCategory(text: String): TransactionCategory? = lexicon.category(text) ?: builtin.category(text)
    internal fun isRole(text: String): Boolean = lexicon.has(RuleLexicon.PEOPLE, text) || builtin.has(RuleLexicon.PEOPLE, text)
    internal fun isKnownBill(text: String): Boolean = lexicon.has(RuleLexicon.BILLS, text) || builtin.has(RuleLexicon.BILLS, text)
    internal fun startsWithTaskVerb(text: String): Boolean =
        lexicon.table(RuleLexicon.TASK_VERBS).startsWith(text) != null || builtin.table(RuleLexicon.TASK_VERBS).startsWith(text) != null
    internal fun hasJobTerm(text: String): Boolean = lexicon.has(RuleLexicon.JOB_TERMS, text) || builtin.has(RuleLexicon.JOB_TERMS, text)

    private val STOP_NAMES = setOf(
        "today", "tomorrow", "yesterday", "morning", "night", "lunch", "dinner",
        "breakfast", "coffee", "dollars", "bucks", "cash", "card", "week", "next", "tonight", "afternoon", "evening",
        "month", "year", "weekend", "food", "groceries", "grocery", "rent", "gas", "fuel", "taxi", "snacks", "snack",
    )
    private val NAME_CUT = setOf(
        "yesterday", "today", "tomorrow", "tonight", "on", "for", "and", "with", "every", "due", "last", "next", "this",
        "in", "at", "by", "from", "to", "using", "via", "because", "since", "after", "before", "monthly", "weekly",
        "daily", "yearly", "then", "also", "but", "which", "that", "when", "so",
    )
    private val DETERMINERS = setOf("the", "a", "an", "my", "our", "his", "her", "their", "some", "this", "that", "your", "new")

    private val CURRENCY_WORD = "(?:dollars?|bucks?|usd|taka|tk|bdt|rupees?|rs|inr|pounds?|gbp|euros?|eur|quid|cents?)"
    private val NUM_RE = Regex("""(?<![\w.,:/-])(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:[.,]\d{1,2})?)(?!\d)""")
    private val SKIP_AFTER = Regex(
        """(?i)^\s*(?:am\b|pm\b|a\.m|p\.m|:|st\b|nd\b|rd\b|th\b|%|percent|per\s*cent|(?:minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?|seconds?|secs?)\b|people\b|persons?\b|guests?\b|times\b|kg\b|km\b|miles?\b|items?\b|pieces?\b|pcs\b|x\b|o'?clock)"""
    )
    private val MONTH_BEFORE = Regex("""(?i)(?:${DateRules.MONTH_ALT})\.?\s*$""")
    private val MONTH_AFTER = Regex("""(?i)^\s*(?:of\s+)?${DateRules.MONTH_ALT}\b""")
    private val SKIP_BEFORE = Regex("""(?i)(?:\bat|\bby|\baround|#|\bno\.?|\bnumber|\broom|\bflight|\bgate|\bline|\bpage|\bchapter|\bstep|\bversion|\bnext|\blast|\bevery)\s*$""")
    private val CURRENCY_AFTER = Regex("""(?i)^\s*$CURRENCY_WORD\b""")
    private val AMOUNT_BEFORE_WORDS = Regex("""(?i)(?:\bfor|\bof|\bcost|\bcosts|\bpaid|\bspent|\bis|\bwas|\bowe|\bowes|\btotal|\blent|\bborrowed|\bworth|\bpay|\bsend|\bsent|\bgot|\breceived|\bearned)\s*[$£€৳₹]?\s*$""")
    private val SYMBOL_BEFORE = Regex("""[$£€৳₹]\s*$""")

    /**
     * The money amount in the words, or null. Number words, "2k" and "1.5 million" are read; times, dates,
     * durations and ordinals are skipped. A number next to a currency word or symbol wins.
     */
    fun extractAmount(input: String): Double? {
        val text = SpeechNorm.numbers(input, lexicon.numberWords() + BuiltinRules.numberWords)
        data class Cand(val value: Double, val currency: Boolean, val preposition: Boolean)
        val cands = ArrayList<Cand>()
        for (m in NUM_RE.findAll(text)) {
            val before = text.substring(0, m.range.first)
            val after = text.substring(m.range.last + 1)
            if (SKIP_AFTER.containsMatchIn(after) || SKIP_BEFORE.containsMatchIn(before)) continue
            if (MONTH_BEFORE.containsMatchIn(before) || MONTH_AFTER.containsMatchIn(after)) continue
            if (before.endsWith("-") || before.endsWith("/")) continue
            val raw = m.groupValues[1]
            val value = when {
                Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?""").matches(raw) -> raw.replace(",", "").toDoubleOrNull()
                else -> raw.replace(",", ".").toDoubleOrNull()
            } ?: continue
            val cents = Regex("""(?i)^\s*cents?\b""").containsMatchIn(after)
            cands += Cand(
                if (cents) value / 100.0 else value,
                SYMBOL_BEFORE.containsMatchIn(before) || CURRENCY_AFTER.containsMatchIn(after),
                AMOUNT_BEFORE_WORDS.containsMatchIn(before),
            )
        }
        return (cands.firstOrNull { it.currency } ?: cands.firstOrNull { it.preposition } ?: cands.firstOrNull())?.value
    }

    fun parseRelativeDate(input: String, today: LocalDate): LocalDate = DateRules.parseRelativeDate(input, today)

    /** Calendar date: missing parts stay in the current day, week, month or year, then move ahead of [now]. */
    fun parseEventDate(
        input: String,
        today: LocalDate = LocalDate.now(),
        at: LocalTime = LocalTime.MAX,
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): LocalDate = DateRules.parseEventDate(input, today, at, now)

    /** True when the utterance names a day or a clock time. */
    fun hasSpokenWhen(input: String): Boolean = DateRules.hasSpokenWhen(input)

    /**
     * Spoken date and time. A clock without am/pm uses the next 12-hour occurrence.
     * "after 1 min" is one minute from [now]. The result is never before [now].
     */
    fun resolveEventDateTimeFromText(
        input: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): LocalDateTime = DateRules.resolveEventDateTimeFromText(input, today, now)

    /** Clock, relative offset, or null when the utterance has neither. [allowBareHour] lets "alarm 7" mean 7:00. */
    fun resolveSpokenDateTime(
        input: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
        allowBareHour: Boolean = false,
    ): LocalDateTime? = DateRules.resolveSpokenDateTime(input, today, now, allowBareHour)

    /** Moves [time] onto the spoken day, then forward until it is not before [now]. */
    fun futureOnDate(time: LocalTime, input: String, today: LocalDate, now: LocalDateTime): LocalDateTime =
        DateRules.futureOnDate(time, input, today, now)

    /** Repeat when the utterance asks for one. A one-off returns null. */
    fun explicitRepeat(input: String, start: LocalDate): EventRecurrence? = DateRules.explicitRepeat(input, start)

    private val TRIGGER_PREFIX = Regex(
        """(?i)^\s*(?:(?:hey|ok|okay|please|kindly|can you|could you|would you|will you|just)\s+)*(?:set\s+)?(?:a\s+|an\s+)?(?:remind\s+me(?:\s+(?:to|about|that|of))?|remind\s+us(?:\s+to)?|reminder(?:\s+(?:to|about|for|that))?|add\s+(?:a\s+)?(?:new\s+)?task|new\s+task|todo|to-do|to\s+do|task|schedule(?:\s+an?)?|(?:add|book|create|make)(?:\s+an?)?\s+(?=event|exam|meeting|appointment)|don'?t\s+forget\s+(?:to\s+)?|do\s+not\s+forget\s+(?:to\s+)?|remember\s+to|make\s+sure\s+(?:i\s+|to\s+)?|i\s+(?:need|have|must|should|want|got)\s+to|need\s+to|have\s+to|gotta|ping\s+me\s+(?:to\s+)?|notify\s+me\s+(?:to\s+)?|alert\s+me\s+(?:to\s+)?)\s*:?\s*"""
    )
    private val TITLE_TAIL = Regex("""(?i)\s+(?:on|at|for|by|to|in|from|of|the|a|an|and|this|next)$""")

    fun hasWhenWords(input: String): Boolean = DateRules.hasSpokenWhen(input)

    /** The thing to do, with the trigger words, the repeat and the spoken date and time removed. */
    fun eventTitleFromText(input: String, fallback: String): String {
        var t = TRIGGER_PREFIX.replace(input.trim(), "")
        DateRules.REPEAT_PHRASES.forEach { t = it.replace(t, " ") }
        DateRules.WHEN_PHRASES.forEach { t = it.replace(t, " ") }
        t = t.replace(Regex("""\s+"""), " ").trim(' ', ',', '.', '!', '?', '-', ':')
        var guard = 0
        while (guard++ < 3 && TITLE_TAIL.containsMatchIn(t)) t = TITLE_TAIL.replace(t, "").trim(' ', ',', '.')
        t = t.replace(Regex("""(?i)^(?:to|that|about)\s+"""), "").trim()
        return t.replaceFirstChar { it.uppercase() }.ifBlank { fallback }
    }

    fun parseBillFrequency(input: String): BillFrequency {
        val lower = SpeechNorm.numbers(input).lowercase()
        return when {
            lower.containsAny("weekly", "every week", "once a week", "per week", "a week", "each week") -> BillFrequency.WEEKLY
            lower.containsAny("yearly", "annual", "annually", "every year", "per year", "a year", "each year") -> BillFrequency.YEARLY
            lower.containsAny("quarterly", "every 3 months", "every three months", "every quarter", "per quarter") -> BillFrequency.QUARTERLY
            else -> BillFrequency.MONTHLY
        }
    }

    /** True when the words name how often the bill comes. */
    internal fun hasBillFrequency(input: String): Boolean =
        input.lowercase().containsAny(
            "weekly", "every week", "once a week", "per week", "a week", "yearly", "annual", "annually", "every year",
            "per year", "a year", "quarterly", "every quarter", "monthly", "every month", "once a month", "per month",
            "a month", "each month", "each week", "each year", "every 3 months",
        )

    // --- transactions -----------------------------------------------------------------------

    internal val INCOME_RE = Regex(
        """(?i)\b(?:got\s+paid|get\s+paid|paid\s+me|pays?\s+me|received?|earned?|income|salary\s+(?:came|credited)|salary\s+of|deposit(?:ed)?|refund(?:ed)?|reimburse(?:d|ment)?|cashback|cash\s+back|bonus|dividends?|interest\s+(?:earned|received)|sold|commission|payout|credited|won|prize|allowance|stipend|gave\s+me|sent\s+me|transferred\s+(?:to\s+)?me|came\s+in|got\s+(?:an?\s+)?(?:[$£€৳]?\d[\d,.]*\s*\w*\s+)?from|my\s+salary|salary\s+\d|pocket\s+money|tip(?:s)?\s+from|profit|revenue)\b"""
    )
    private val SALARY_WORD = Regex("""(?i)\b(?:salary|paycheck|paycheque|wages?|payroll|stipend|pension)\b""")
    internal val EXPENSE_VERB = Regex(
        """(?i)\b(?:spent|spend|paid|pay|bought|buy|purchased?|cost|costs|charged|sent|gave|donated?|tipped|withdrew|withdrawn|transferred|ordered|treated|shelled\s+out|dropped|lost|fined|booked)\b"""
    )
    private val PAST_PAY = Regex("""(?i)\b(?:paid|spent|bought|purchased|charged|settled|cleared|got\s+charged|cost)\b""")
    private val BUY_WORD = Regex("""(?i)\b(?:buy|bought|purchase|purchased|ordered|shopping)\b""")
    internal val MONEY_VERB = Regex(
        """(?i)\b(?:spent|spend|paid|bought|purchased|cost|costs|charged|sent|gave|donated|tipped|withdrew|transferred|ordered|received|earned|refund(?:ed)?|sold|deposit(?:ed)?|dollars?|bucks?|taka|tk|rupees?|euros?|pounds?|usd|bdt|inr|rs|quid|reimbursed|credited|got\s+paid|treated|salary|bonus)\b|[$£€৳₹]"""
    )

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedTransaction {
        val text = SpeechNorm.numbers(input, lexicon.numberWords() + BuiltinRules.numberWords)
        val amount = extractAmount(text)
        val lower = text.lowercase()
        val hit = merchantHit(text)
        val income = INCOME_RE.containsMatchIn(lower) && !(PAST_PAY.containsMatchIn(lower) && !Regex("""(?i)\bpaid\s+me\b|\bgot\s+paid\b""").containsMatchIn(lower))
        var category = hit?.category ?: keywordCategory(text)
        if (income && category != null && category !in INCOME_CATEGORIES) {
            if (SALARY_WORD.containsMatchIn(lower)) category = TransactionCategory.SALARY
        }
        if (category == null) {
            category = when {
                income && Regex("""(?i)\b(?:received?|earned?|got\s+paid|paid\s+me|income|deposit(?:ed)?|bonus|stipend|salary|credited)\b""").containsMatchIn(lower) -> TransactionCategory.SALARY
                income -> TransactionCategory.OTHER
                SALARY_WORD.containsMatchIn(lower) -> TransactionCategory.SALARY
                BUY_WORD.containsMatchIn(lower) -> TransactionCategory.SHOPPING
                else -> TransactionCategory.OTHER
            }
        }
        val type = if (income || category in INCOME_CATEGORIES && !EXPENSE_VERB.containsMatchIn(lower)) TransactionType.INCOME else TransactionType.EXPENSE
        val merchant = hit?.name ?: extractName(text)
        val note = if (merchant.isBlank()) input.trim() else ""
        return ParsedTransaction(
            amount = amount,
            category = category,
            merchant = merchant,
            date = parseRelativeDate(text, today),
            note = note,
            type = type,
        )
    }

    private val INCOME_CATEGORIES = setOf(TransactionCategory.SALARY, TransactionCategory.FREELANCE)

    private val NAME_AFTER = Regex(
        """(?i)\b(?:at|from|with|to|for|on)\s+([A-Za-z][\w'&.-]*(?:\s+[A-Za-z][\w'&.-]*){0,3})"""
    )

    /**
     * Pulls a person, shop, or place out of a sentence.
     * Prefers a known merchant, then "at/from/with/to Name", over dumping the whole line into a note.
     */
    fun extractName(input: String): String {
        merchantHit(input)?.let { return it.name }
        var best = ""
        for (m in NAME_AFTER.findAll(input)) {
            val words = m.groupValues[1].trim().split(Regex("""\s+"""))
            val taken = ArrayList<String>()
            for ((i, w) in words.withIndex()) {
                val lw = w.lowercase().trim('.', ',')
                if (i == 0 && lw in DETERMINERS) continue
                if (taken.isNotEmpty() && (lw in NAME_CUT || w.any { it.isDigit() })) break
                if (taken.isEmpty() && (lw in NAME_CUT && lw != "to" || lw in STOP_NAMES)) break
                taken += w.trim(',', '.')
            }
            val name = taken.joinToString(" ")
            if (name.isNotBlank() && name.lowercase() !in STOP_NAMES && !name.all { it.isDigit() }) {
                if (m.groupValues[0].lowercase().startsWith("on ") && name.first().isLowerCase()) continue
                best = name
            }
        }
        if (best.isNotBlank()) return best.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        return input.split(" ").lastOrNull { word ->
            word.length > 2 && word[0].isUpperCase() && word.none { it.isDigit() }
        }.orEmpty()
    }

    // --- intent tables ----------------------------------------------------------------------

    internal val BILL_WORD = Regex("""(?i)\b(?:bills?|subscriptions?|invoice|instal+ments?|emi|premium|dues?)\b""")
    internal val DUE_WORD = Regex("""(?i)\b(?:due|renews?|renewal|expires?|payable)\b""")
    internal val DEBT_I_OWE = Regex(
        """(?i)\b(?:i\s+owe|i\s+still\s+owe|i\s+am\s+owing|borrowed|borrow|took\s+(?:a\s+)?loan|loan\s+from|pay\s+back|payback|repay|owe\s+(?!me\b)(?!them\b)|iou\s+to|have\s+to\s+(?:give|return)|need\s+to\s+(?:give|return)|must\s+(?:give|return)|taking\s+a\s+loan)\b"""
    )
    internal val DEBT_THEY_OWE = Regex(
        """(?i)\b(?:they\s+owe|owes?\s+me|owe\s+me|lent|lend|loaned|loan\s+to|gave\s+(?:\w+\s+)?(?:a\s+)?loan|will\s+(?:pay|give)\s+me\s+back|pay\s+me\s+back|paying\s+me\s+back|(?:he|she)\s+owes|owes\s+\w+\s+\d|iou\s+from)\b"""
    )
    private val DEBT_WORD = Regex("""(?i)\b(?:debt|loan|iou)\b""")
    internal val GOAL_HINT = Regex("""(?i)\b(?:save|saving|savings|goal|target|save\s+up|put\s+aside|set\s+aside)\b""")
    internal val BUDGET_WORD = Regex("""(?i)\bbudget\b""")
    internal val LIMIT_WORD = Regex(
        """(?i)\b(?:(?:spending\s+)?limit|cap\b|don'?t\s+spend\s+more\s+than|do\s+not\s+spend\s+more\s+than|not\s+(?:to\s+)?spend\s+more\s+than|no\s+more\s+than|at\s+most|spend\s+(?:less\s+than|at\s+most|up\s+to|only)|maximum|max\s+spend)\b"""
    )
    internal val NOTE_START = Regex(
        """(?i)^\s*(?:(?:please|hey|ok|okay)\s+)*(?:(?:write|make|add|take|save|jot|put|create|start)\s+(?:down\s+)?(?:a\s+|an\s+|the\s+|new\s+)*(?:note|notes|memo)|note\s+to\s+self|note\s+down|notes?\b|memo\b|idea\b|ideas\b|thought\b|jot\s+down|write\s+down|journal\b|diary\b|log\s+this|remember\s+(?:that|to)|keep\s+in\s+mind|just\s+a\s+note)\s*[:\-,]?\s*(?:that\s+)?"""
    )
    internal val NOTE_ANY = Regex("""(?i)\b(?:note:|write\s+note|write\s+a\s+note|make\s+a\s+note|remember\s+that|note\s+to\s+self)\b""")
    internal val REMIND_WORD = Regex(
        """(?i)\b(?:remind\s+me|remind\s+us|reminder|don'?t\s+forget|do\s+not\s+forget|dont\s+forget|ping\s+me|notify\s+me|alert\s+me|nudge\s+me|make\s+sure\s+i|make\s+sure\s+to|don'?t\s+let\s+me\s+forget|remember\s+to)\b"""
    )
    internal val ALARM_WORD = Regex("""(?i)\b(?:alarms?|wake\s+me|wake\s+up\s+call|wakeup)\b""")
    internal val TASK_WORD = Regex("""(?i)\b(?:add\s+(?:a\s+)?(?:new\s+)?task|new\s+task|todo|to-do|to\s+do|task:|add\s+to\s+(?:my\s+)?(?:to-?do|list|tasks))\b""")
    internal val SOFT_TASK = Regex("""(?i)^\s*(?:(?:hey|ok|okay|please)\s+)*(?:i\s+(?:need|have|must|should|got|gotta|want)\s+to|need\s+to|have\s+to|gotta|must|should|i'?ll|i\s+will|don'?t\s+forget\s+to)\b""")
    internal val ROUTINE_WORD = Regex("""(?i)\b(?:routines?|habits?)\b""")
    internal val STRONG_EVENT = Regex("""(?i)\b(?:exams?|meetings?|appointments?|schedule\s+an?|book\s+an?|quiz|midterms?|finals?|viva|interview\s+panel)\b""")
    private val EXAM_WORD = Regex("""(?i)\b(?:exams?|quiz|midterms?|finals?|viva|(?<!blood\s)(?<!drive\s)(?<!covid\s)test)\b""")
    internal val WEAK_EVENT = Regex(
        """(?i)\b(?:classes|class|lectures?|seminar|webinar|conference|presentation|checkup|check-up|party|birthday|wedding|flight|concert|trip|catch\s*up|hang\s*out|hangout|date\s+night|playdate|play\s+date|session|workshop|dentist|doctor|physio|therapy|haircut|barber|salon|(?:lunch|dinner|breakfast|coffee|drinks|call|meet|brunch|tea)\s+with|meet\s+(?:up\s+)?(?:with\s+)?[a-z]+|interview|game|match|practice|rehearsal|recital|ceremony|graduation|reunion|funeral|visit)\b"""
    )
    internal val DEADLINE_WORD = Regex("""(?i)\b(?:deadline|due\s+date|submission|submit\s+by|assignment)\b""")
    internal val MONEY_WORDS = Regex("""(?i)\b(?:dollars?|bucks?|taka|tk|rupees?|euros?|pounds?|usd|bdt|rs|quid)\b|[$£€৳₹]""")
    private val FILLER_START = Regex("""(?i)^\s*(?:(?:hey|ok|okay|please|kindly|um+|uh+|so|well|yeah|yes|just|can you|could you|would you|will you|i want to|i wanna|i would like to|i'd like to|let's|lets|let me|i'm going to|i am going to)\s*,?\s+)+""")
    private val DISFLUENCY = Regex("""(?i)\b(?:um+|uh+|erm|hmm+|you know)\b[,]?\s*""")

    /** Longer separators first so "and then" is not cut at "then". */
    private val HARD_SPLIT = Regex("""(?i)\s*(?:;|,?\s*and\s+then\b|,\s*then\b|\bthen\b|\balso\b|,?\s*after\s+that\b|\bnext\s+one\b)\s*""")
    private val AND_SPLIT = Regex("""(?i)\s*,?\s+and\s+""")
    private const val MAX_ITEMS = 5

    /**
     * Offline router for one clause. Alarm / reminder / routine / task win over money.
     * Bills, debts, goals and budgets need their own keywords so "spent 15 on netflix" stays a transaction.
     * Text that matches nothing becomes [ParsedIntent.Unmatched]; it is never guessed into a transaction
     * unless an amount was spoken.
     */
    fun parseVoiceIntent(
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): ParsedIntent {
        val trimmed = normalizeSpeech(transcript.trim())
        return matchIntent(trimmed, today, now, loose = true) ?: ParsedIntent.Unmatched(rawTranscript = trimmed)
    }

    /**
     * Speech to the English phrases and digits the parser reads: Bangla and Banglish words, number words,
     * "2k", spoken clocks ("half past five"), day parts and fillers.
     */
    fun normalizeSpeech(input: String): String {
        var text = input
        BN_DIGITS.forEachIndexed { index, digit -> text = text.replace(digit, ('0' + index)) }
        text = lexicon.rewrite(RuleLexicon.BANGLA, text)
        text = builtin.rewrite(RuleLexicon.BANGLA, text)
        BN_NUMBERS.forEach { (bn, en) -> text = text.replace(bn, en) }
        BN_PHRASES.forEach { (bn, en) -> text = text.replace(bn, en) }
        text = text.replace(Regex("""(?i)(?<![a-z])a\.?\s*m\.?(?![a-z])"""), "am")
        text = text.replace(Regex("""(?i)(?<![a-z])p\.?\s*m\.?(?![a-z])"""), "pm")
        text = DISFLUENCY.replace(text, "")
        text = SpeechNorm.numbers(text, lexicon.numberWords() + BuiltinRules.numberWords)
        text = SpeechNorm.times(text) { lexicon.rewrite(RuleLexicon.TIME_PHRASES, it) }
        text = text.replace(
            Regex("""(?i)(?<![\d:.])(\d{1,2})(:\d{2})?\s*(?:o'?clock\s*)?(?:in the|at|of the)?\s*(morning|forenoon)\b""")
        ) { m ->
            if (m.groupValues[1].toInt() in 1..12) "${m.groupValues[1]}${m.groupValues[2]} am" else m.value
        }
        text = text.replace(
            Regex("""(?i)(?<![\d:.])(\d{1,2})(:\d{2})?\s*(?:in the|at|of the)?\s*(afternoon|evening|night|tonight)\b""")
        ) { m ->
            val h = m.groupValues[1]
            if (h.toInt() !in 1..12) return@replace m.value
            val night = m.groupValues[3].lowercase().let { it == "night" || it == "tonight" }
            "$h${m.groupValues[2]} ${if (h == "12" && night) "am" else "pm"}"
        }
        text = text.replace(Regex("""(?i)\b(morning|afternoon|evening|night|tonight)\s+(\d{1,2})(:\d{2})?\b(?!\s*(?:am|pm|:|min|hour|dollars|bucks|taka))""")) { m ->
            val part = m.groupValues[1].lowercase()
            val h = m.groupValues[2]
            if (h.toInt() !in 1..12) return@replace m.value
            "$h${m.groupValues[3]} ${if (part != "morning") "pm" else "am"}"
        }
        text = text.replace(Regex("""(?i)(?<![\d:])(?<![\d:]\s)\b(am|pm)\s+(\d{1,2})(?::(\d{2}))?\b"""), "$2:$3 $1")
        text = text.replace(Regex("""(\d{1,2}):(?!\d)"""), "$1")
        text = text.replace(Regex("""\s+"""), " ").trim()
        text = FILLER_START.replace(text, "")
        return text
    }

    /**
     * One utterance, several items: "spent 12 on lunch then remind me to call mom at 5 pm".
     * Splits on ";", "then", "and then", "also", and on "and" when both sides are clear intents.
     * Always returns at least one element.
     */
    fun parseVoiceIntents(
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): List<ParsedIntent> {
        val trimmed = transcript.trim()
        val parts = trimmed.split(HARD_SPLIT).map { it.trim(' ', ',', '.') }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return parseClause(trimmed, today, now).take(MAX_ITEMS)
        val all = parts.flatMap { parseClause(it, today, now) }
        val matched = all.filter { it !is ParsedIntent.Unmatched }
        val kept = if (matched.isEmpty()) emptyList() else all.filter {
            it !is ParsedIntent.Unmatched || it.rawTranscript.split(' ').size > 2
        }
        return kept.ifEmpty { listOf(ParsedIntent.Unmatched(rawTranscript = trimmed)) }.take(MAX_ITEMS)
    }

    private fun parseClause(clause: String, today: LocalDate, now: LocalDateTime): List<ParsedIntent> {
        val norm = normalizeSpeech(clause)
        for (m in AND_SPLIT.findAll(norm)) {
            val left = norm.substring(0, m.range.first).trim()
            val right = norm.substring(m.range.last + 1).trim()
            if (left.split(' ').size < 2 || right.split(' ').size < 2) continue
            if (matchIntent(left, today, now, loose = false) != null && matchIntent(right, today, now, loose = false) != null) {
                return parseClause(left, today, now) + parseClause(right, today, now)
            }
        }
        return listOf(parseVoiceIntent(clause, today, now))
    }

    private class Ctx(
        val text: String,
        val today: LocalDate,
        val now: LocalDateTime,
        val loose: Boolean,
    ) {
        val lower: String = text.lowercase()
        val amount: Double? by lazy { extractAmount(text) }
        val hasWhen: Boolean by lazy { DateRules.hasSpokenWhen(text) }
        val repeat: EventRecurrence? by lazy { DateRules.explicitRepeat(text, today) }
    }

    private class Rule(val name: String, val test: (Ctx) -> Boolean, val build: (Ctx) -> ParsedIntent)

    private fun isDebt(text: String): Boolean =
        DEBT_I_OWE.containsMatchIn(text) || DEBT_THEY_OWE.containsMatchIn(text)

    private val RULES: List<Rule> by lazy {
        listOf(
            Rule("alarm", { ALARM_WORD.containsMatchIn(it.lower) }) { c ->
                val days = parseRepeatDays(c.text)
                val start = resolveSpokenDateTime(c.text, c.today, c.now, allowBareHour = true)
                    ?: futureOnDate(if (DateRules.defaultTime(c.text) != LocalTime.of(9, 0)) DateRules.defaultTime(c.text) else LocalTime.of(7, 0), c.text, c.today, c.now)
                ParsedIntent.Event(
                    title = "Alarm",
                    startAt = start,
                    kind = CalendarEventKind.ALARM,
                    repeat = if (days == 0) c.repeat?.takeIf { it.interval > 1 || it.frequency != RecurrenceFrequency.WEEKLY } else repeatFromAlarmMask(days),
                    rawTranscript = c.text,
                )
            },
            Rule("note-start", { NOTE_START.containsMatchIn(it.lower) && !REMIND_WORD.containsMatchIn(it.lower) && !ALARM_WORD.containsMatchIn(it.lower) }) { noteFrom(it.text) },
            Rule("reminder", { REMIND_WORD.containsMatchIn(it.lower) && !(it.lower.contains("remember to") && !it.hasWhen) }) { c ->
                val start = resolveEventDateTimeFromText(c.text, c.today, c.now)
                eventFrom(c, start, CalendarEventKind.TASK, "Reminder", reminders = listOf(EventReminder(label = "At time", offsetMinutes = 0)))
            },
            Rule("remember-note", { it.lower.contains("remember to") && !it.hasWhen }) { noteFrom(it.text) },
            Rule("routine", { ROUTINE_WORD.containsMatchIn(it.lower) }) { c ->
                val title = c.text
                    .replace(Regex("(?i)^(add\\s+)?(a\\s+)?(daily\\s+|weekly\\s+)?(routine|habit)\\s*(to\\s+|for\\s+)?"), "")
                    .trim()
                    .ifBlank { c.text }
                val start = resolveEventDateTimeFromText(c.text, c.today, c.now)
                val minutes = DateRules.durationMinutes(c.lower) ?: 30
                ParsedIntent.Event(
                    title = eventTitleFromText(title, title).take(120),
                    startAt = start,
                    endAt = start.plusMinutes(minutes.toLong()),
                    kind = CalendarEventKind.ROUTINE,
                    repeat = c.repeat ?: repeatFromRule(parseRepeatRule(c.text), start.toLocalDate()),
                    rawTranscript = c.text,
                )
            },
            Rule("task", { TASK_WORD.containsMatchIn(it.lower) }) { c ->
                val stripped = c.text
                    .replace(Regex("(?i)^(add\\s+(?:a\\s+)?(?:new\\s+)?task|new\\s+task|todo|to-do|to\\s+do|task:)\\s*"), "")
                    .trim()
                    .ifBlank { c.text }
                val start = resolveEventDateTimeFromText(stripped, c.today, c.now)
                ParsedIntent.Event(
                    title = eventTitleFromText(stripped, stripped),
                    startAt = start,
                    endAt = DateRules.durationMinutes(stripped.lowercase())?.let { start.plusMinutes(it.toLong()) },
                    kind = CalendarEventKind.TASK,
                    repeat = explicitRepeat(stripped, start.toLocalDate()),
                    rawTranscript = c.text,
                )
            },
            Rule("job", { isJobUtterance(it.lower) }) { jobFrom(it.text, it.today, it.now) },
            Rule("event", { isEventUtterance(it) }) { c ->
                val start = resolveEventDateTimeFromText(c.text, c.today, c.now)
                val kind = if (EXAM_WORD.containsMatchIn(c.lower)) CalendarEventKind.EXAM else CalendarEventKind.EVENT
                val label = when {
                    kind == CalendarEventKind.EXAM -> "Exam"
                    c.lower.contains("appointment") -> "Appointment"
                    c.lower.contains("meeting") -> "Meeting"
                    else -> "Event"
                }
                eventFrom(c, start, kind, label)
            },
            Rule("budget", { it.amount != null && (BUDGET_WORD.containsMatchIn(it.text) || LIMIT_WORD.containsMatchIn(it.text)) }) { c ->
                ParsedIntent.Budget(
                    category = keywordCategory(c.text) ?: merchantHit(c.text)?.category ?: TransactionCategory.OTHER,
                    limit = c.amount ?: 0.0,
                    rawTranscript = c.text,
                )
            },
            Rule("bill", { it.amount != null && isBillUtterance(it.text) }) { billFrom(it.text, it.amount ?: 0.0, it.today) },
            Rule("debt", { it.amount != null && isDebtUtterance(it.text) }) { debtFrom(it.text, it.amount ?: 0.0, it.today) },
            Rule("goal", { it.amount != null && GOAL_HINT.containsMatchIn(it.text) && !PAST_PAY.containsMatchIn(it.lower) }) { goalFrom(it.text, it.amount ?: 0.0) },
            Rule("money", { it.amount != null && MONEY_VERB.containsMatchIn(it.text) }) { ParsedIntent.Transaction.from(parse(it.text, it.today), it.text) },
            Rule("deadline", { DEADLINE_WORD.containsMatchIn(it.lower) && it.hasWhen }) { c ->
                eventFrom(c, resolveEventDateTimeFromText(c.text, c.today, c.now), CalendarEventKind.TASK, "Deadline")
            },
            Rule("soft-task", { c -> c.amount == null && SOFT_TASK.containsMatchIn(c.lower) && c.lower.split(' ').size >= 3 }) { c ->
                eventFrom(c, resolveEventDateTimeFromText(c.text, c.today, c.now), CalendarEventKind.TASK, "Task")
            },
            Rule("verb-task", { c ->
                c.amount == null && c.hasWhen && !MONEY_WORDS.containsMatchIn(c.lower) && startsWithTaskVerb(c.lower.removePrefix("to "))
            }) { c ->
                eventFrom(c, resolveEventDateTimeFromText(c.text, c.today, c.now), CalendarEventKind.TASK, "Task")
            },
            Rule("recurring-routine", { c ->
                c.amount == null && c.repeat != null && c.repeat?.frequency in setOf(RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY) &&
                    c.lower.split(' ').size >= 3
            }) { c ->
                val start = resolveEventDateTimeFromText(c.text, c.today, c.now)
                ParsedIntent.Event(
                    title = eventTitleFromText(c.text, c.text).take(120),
                    startAt = start,
                    endAt = start.plusMinutes((DateRules.durationMinutes(c.lower) ?: 30).toLong()),
                    kind = CalendarEventKind.ROUTINE,
                    repeat = c.repeat,
                    rawTranscript = c.text,
                )
            },
            Rule("note-any", { NOTE_ANY.containsMatchIn(it.lower) }) { noteFrom(it.text) },
            Rule("loose-money", { it.loose && it.amount != null }) { ParsedIntent.Transaction.from(parse(it.text, it.today), it.text) },
        )
    }

    private fun isEventUtterance(c: Ctx): Boolean {
        if (STRONG_EVENT.containsMatchIn(c.lower)) return true
        if (c.hasWhen && c.amount == null && EXAM_WORD.containsMatchIn(c.lower)) return true
        if (c.amount != null || MONEY_WORDS.containsMatchIn(c.lower)) return false
        return c.hasWhen && WEAK_EVENT.containsMatchIn(c.lower)
    }

    private fun matchIntent(trimmed: String, today: LocalDate, now: LocalDateTime, loose: Boolean): ParsedIntent? {
        changeIntent(trimmed)?.let { return it }
        val ctx = Ctx(trimmed, today, now, loose)
        for (rule in RULES) if (rule.test(ctx)) return rule.build(ctx)
        return null
    }

    /** Builds a calendar item: title without the date words, an end from a range or a duration, and a repeat. */
    private fun eventFrom(
        c: Ctx,
        start: LocalDateTime,
        kind: CalendarEventKind,
        fallbackTitle: String,
        reminders: List<EventReminder> = emptyList(),
    ): ParsedIntent.Event {
        val range = DateRules.timeRange(SpeechNorm.times(c.lower))
        val minutes = DateRules.durationMinutes(c.lower)
        val timed = kind == CalendarEventKind.EVENT || kind == CalendarEventKind.EXAM
        val end = when {
            range != null -> {
                var e = LocalDateTime.of(start.toLocalDate(), range.second)
                if (!e.isAfter(start)) e = e.plusDays(1)
                e
            }
            minutes != null -> start.plusMinutes(minutes.toLong())
            timed -> start.plusHours(1)
            else -> null
        }
        return ParsedIntent.Event(
            title = eventTitleFromText(c.text, fallbackTitle).take(120),
            startAt = start,
            endAt = end,
            kind = kind,
            repeat = explicitRepeat(c.text, start.toLocalDate()),
            reminders = reminders,
            rawTranscript = c.text,
        )
    }

    // --- bills, debts, goals, notes ---------------------------------------------------------

    /** Date for something due: spoken date when there is one, otherwise today. */
    private fun dueDate(text: String, today: LocalDate): LocalDate =
        if (DateRules.hasDateAnchor(text)) {
            DateRules.parseEventDate(text, today, LocalTime.MIN, today.atStartOfDay())
        } else today

    private fun billFrom(text: String, amount: Double, today: LocalDate): ParsedIntent.Bill {
        val category = keywordCategory(text) ?: merchantHit(text)?.category
        return ParsedIntent.Bill(
            name = extractBillName(text).ifBlank { "Bill" },
            amount = amount,
            frequency = parseBillFrequency(text),
            nextDueDate = dueDate(text, today),
            category = category?.takeUnless { it == TransactionCategory.OTHER } ?: TransactionCategory.SUBSCRIPTIONS,
            rawTranscript = text,
        )
    }

    private fun debtFrom(text: String, amount: Double, today: LocalDate): ParsedIntent.Debt {
        val dueMentioned = DUE_WORD.containsMatchIn(text) || Regex("""(?i)\bby\b""").containsMatchIn(text) || text.lowercase().containsAny("until", "till", "back on", "next week", "tomorrow", "next month", "end of")
        return ParsedIntent.Debt(
            friendName = extractFriendName(text).ifBlank { "Friend" },
            amount = amount,
            direction = debtDirection(text),
            dueDate = if (dueMentioned && DateRules.hasDateAnchor(text)) dueDate(text, today) else null,
            rawTranscript = text,
        )
    }

    private fun goalFrom(text: String, amount: Double) = ParsedIntent.Goal(
        name = extractGoalName(text).ifBlank { "Goal" },
        targetAmount = amount,
        rawTranscript = text,
    )

    private fun noteFrom(text: String): ParsedIntent.Note {
        val body = NOTE_START.replace(text, "").replace(NOTE_ANY, "").trim().trimStart(':', '-', ',').trim().ifBlank { text }
        val title = body.split(' ').take(6).joinToString(" ").trim(',', '.', '!', '?')
            .replaceFirstChar { it.uppercase() }.ifBlank { "Note" }
        return ParsedIntent.Note(title = title, body = body, rawTranscript = text)
    }

    /** "I owe" and "borrowed" mean I owe; "lent" and "owes me" mean owed to me. */
    fun debtDirection(text: String): DebtDirection {
        val they = DEBT_THEY_OWE.containsMatchIn(text)
        val mine = DEBT_I_OWE.containsMatchIn(text)
        return when {
            they && !mine -> DebtDirection.THEY_OWE
            mine && !they -> DebtDirection.I_OWE
            they && mine -> {
                val t = DEBT_THEY_OWE.find(text)!!.range.first
                val m = DEBT_I_OWE.find(text)!!.range.first
                if (m < t) DebtDirection.I_OWE else DebtDirection.THEY_OWE
            }
            else -> DebtDirection.THEY_OWE
        }
    }

    // --- forced kinds -----------------------------------------------------------------------

    /**
     * Forces [transcript] into [kind] (used by "change result, redo" on a history row).
     * Fields the text does not provide stay empty or zero, so the confirm card asks for them.
     */
    fun asKind(
        kind: VoiceResultKind,
        transcript: String,
        today: LocalDate = LocalDate.now(),
        now: LocalDateTime = today.atTime(LocalTime.now()),
    ): ParsedIntent {
        val text = normalizeSpeech(transcript.trim())
        val natural = parseVoiceIntent(text, today, now)
        if (natural !is ParsedIntent.Unmatched && natural.resultKind() == kind) return natural
        val amount = extractAmount(text) ?: 0.0
        return when (kind) {
            VoiceResultKind.Spend, VoiceResultKind.Income -> {
                val tx = ParsedIntent.Transaction.from(parse(text, today), text)
                tx.copy(type = if (kind == VoiceResultKind.Income) TransactionType.INCOME else TransactionType.EXPENSE)
            }
            VoiceResultKind.Task, VoiceResultKind.Reminder, VoiceResultKind.Event, VoiceResultKind.Exam,
            VoiceResultKind.Routine, VoiceResultKind.Alarm -> {
                val start = resolveEventDateTimeFromText(text, today, now)
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
                        else -> explicitRepeat(text, start.toLocalDate())
                    },
                    reminders = if (kind == VoiceResultKind.Reminder) {
                        listOf(EventReminder(label = "At time", offsetMinutes = 0))
                    } else emptyList(),
                    rawTranscript = text,
                )
            }
            VoiceResultKind.Job -> jobFrom(text, today, now)
            VoiceResultKind.Budget -> ParsedIntent.Budget(keywordCategory(text) ?: TransactionCategory.OTHER, amount, rawTranscript = text)
            VoiceResultKind.Bill -> billFrom(text, amount, today)
            VoiceResultKind.Debt -> debtFrom(text, amount, today)
            VoiceResultKind.Goal -> goalFrom(text, amount)
            VoiceResultKind.Edit -> changeIntent(text) ?: noteFrom(text)
            VoiceResultKind.Note, VoiceResultKind.Unsorted -> noteFrom(text)
        }
    }

    // --- repeat helpers ---------------------------------------------------------------------

    /**
     * Alarm weekday bitmask from natural language.
     * Defaults to 0 (one-shot). Recognizes weekdays / every day / weekends / day names.
     */
    fun parseRepeatDays(transcript: String): Int {
        val lower = transcript.lowercase()
        when {
            lower.containsAny("every day", "everyday", "daily", "all week", "each day", "every morning", "every night", "every evening", "nightly") ->
                return MASK_EVERY_DAY
            lower.containsAny("weekdays", "weekday", "monday to friday", "mon to fri", "mon-fri", "work days", "workdays") ->
                return MASK_WEEKDAYS
            lower.containsAny("weekends", "every weekend") -> return MASK_WEEKENDS
        }
        var mask = 0
        fun has(full: String, abbr: String) = Regex("""\b${full}s?\b""").containsMatchIn(lower) || Regex("""\b$abbr\b""").containsMatchIn(lower)
        if (has("sunday", "sun")) mask = mask or BIT_SUN
        if (has("monday", "mon")) mask = mask or BIT_MON
        if (has("tuesday", "tue")) mask = mask or BIT_TUE
        if (has("wednesday", "wed")) mask = mask or BIT_WED
        if (has("thursday", "thu")) mask = mask or BIT_THU
        if (has("friday", "fri")) mask = mask or BIT_FRI
        if (has("saturday", "sat")) mask = mask or BIT_SAT
        if (lower.contains("weekend") && mask == 0) mask = MASK_WEEKENDS
        return mask
    }

    /** Maps transcript phrases to routine repeatRule tokens used by RoutinesScreen. */
    fun parseRepeatRule(transcript: String): String = DateRules.parseRepeatRule(transcript)

    /** Repeat rule for a weekly alarm from a Sun=1 ... Sat=64 mask. */
    fun repeatFromAlarmMask(mask: Int): EventRecurrence =
        EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = AlarmDays.toIsoDays(mask))

    /** Maps a DAILY | WEEKLY | WEEKDAYS token to a recurrence anchored at [start]. */
    fun repeatFromRule(rule: String, start: LocalDate): EventRecurrence = when (rule.uppercase()) {
        "WEEKDAYS" -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(1, 2, 3, 4, 5))
        "WEEKLY" -> EventRecurrence(frequency = RecurrenceFrequency.WEEKLY, weekDays = setOf(start.dayOfWeek.value))
        "MONTHLY" -> EventRecurrence(frequency = RecurrenceFrequency.MONTHLY)
        "YEARLY" -> EventRecurrence(frequency = RecurrenceFrequency.YEARLY)
        else -> EventRecurrence(frequency = RecurrenceFrequency.DAILY)
    }

    /** A spoken clock time: "7:30 am", "7 pm", "at 14:00", "at 7", "noon", "midnight", "half past five". Null if none. */
    fun parseClockTimeFromText(lower: String): LocalTime? = DateRules.parseClockTimeFromText(lower)

    /** Alarms also accept a bare number ("alarm 7") when no clearer time is spoken. */
    fun parseAlarmTime(lower: String): LocalTime? =
        parseClockTimeFromText(lower)
            ?: Regex("""\b(\d{1,2})\b""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { h -> runCatching { LocalTime.of(h, 0) }.getOrNull() }

    // --- jobs -------------------------------------------------------------------------------

    /** Role words after "for", "as", "role", or the company. Dates and the company name are left out. */
    private fun jobRole(text: String, company: String): String? {
        val source = listOf(
            Regex("""(?i)\b(?:role|position)\s+(.+)$"""),
            Regex("""(?i)\b(?:for|as)\s+(?:an?\s+)?(.+)$"""),
            Regex("""(?i)\b(?:at|to|with)\s+\S+\s+(.+)$""")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) } ?: return null
        val cleaned = eventTitleFromText(source, "").trim()
        if (cleaned.isBlank() || cleaned.equals(company, true) || cleaned.equals("Role", true)) return null
        return cleaned.take(80)
    }

    internal val JOB_STRONG = Regex(
        """(?i)\b(?:applied|applying|application|recruiter|hiring|job\s+offer|(?:got|received|have)\s+an?\s+offer|offer\s+(?:from|at)|phone\s+screen|screening|onsite|on-site|technical\s+interview|coding\s+interview|job\s+interview|interview(?:s)?|rejected|rejection|rejection\s+email|heard\s+back\s+from|cover\s+letter|resume|submitted\s+(?:my\s+)?(?:cv|resume)|sent\s+(?:my\s+)?(?:cv|resume)|withdrew|withdrawn)\b"""
    )

    private fun isJobUtterance(lower: String): Boolean {
        if (JOB_STRONG.containsMatchIn(lower)) {
            if (Regex("""(?i)\b(?:resume|rejected)\b""").containsMatchIn(lower) &&
                !lower.containsAny("job", "applied", "application", "interview", "company", "position", "role", "offer")
            ) return false
            if (MONEY_WORDS.containsMatchIn(lower) && lower.containsAny("offer") && !lower.containsAny("job", "interview", "applied")) return false
            return true
        }
        return Regex("""\bjobs?\b""").containsMatchIn(lower) &&
            lower.containsAny(" at ", " to ", " for ", " with ", "interview", "applied", "add ")
    }

    private val EDIT_VERB = "edit|change|rename|modify|reschedule|postpone|push\\s+back|(?:update|move|shift|correct)(?=\\s+(?:the|my|that|this)\\b)"
    private val REMOVE_VERB = "delete|remove|cancel|scrap|erase|trash|get\\s+rid\\s+of|drop(?=\\s+(?:the|my)\\b)"
    private val POLITE_HEAD = "(?:(?:please|hey|ok|okay|can\\s+you|could\\s+you|would\\s+you|i\\s+want\\s+to|i\\s+need\\s+to|i'd\\s+like\\s+to|let's|go\\s+ahead\\s+and)\\s+)*"
    private val REMOVE_RE = Regex("""(?i)^\s*$POLITE_HEAD(?:$REMOVE_VERB)\b""")
    private val EDIT_RE = Regex("""(?i)^\s*$POLITE_HEAD(?:$EDIT_VERB)\b""")

    private fun changeIntent(text: String): ParsedIntent.Adjust? {
        val remove = REMOVE_RE.containsMatchIn(text)
        val edit = !remove && EDIT_RE.containsMatchIn(text)
        if (!remove && !edit) return null
        val rest = text.replace(
            Regex("""(?i)^\s*$POLITE_HEAD(?:$REMOVE_VERB|$EDIT_VERB)\s+(?:the\s+|my\s+|that\s+|this\s+|a\s+|an\s+)?"""),
            ""
        ).trim()
        if (rest.isBlank()) return null
        val kind = when {
            Regex("""(?i)\b(?:jobs?|applications?|interviews?)\b""").containsMatchIn(text) -> "job"
            Regex("""(?i)\b(?:alarms?|events?|meetings?|tasks?|reminders?|appointments?|exams?)\b""").containsMatchIn(text) -> "event"
            Regex("""(?i)\b(?:spent|expense|transaction|purchase|payment)\b""").containsMatchIn(text) -> "spend"
            else -> ""
        }
        val parts = Regex("""(?i)\b(?:to|into|as|with)\b""").split(rest, limit = 2)
        val query = parts[0].replace(Regex("""(?i)\b(?:job|application|interview|alarm|event|meeting|task|reminder|expense|transaction|purchase|payment|appointment|exam)\b"""), " ")
            .replace(Regex("""\s+"""), " ").trim().replace(Regex("""(?i)^(?:at|for|with|from|of|to)\s+"""), "").trim()
        if (query.isBlank()) return null
        return ParsedIntent.Adjust(
            remove = remove,
            query = query,
            kindHint = kind,
            replacement = if (remove) "" else parts.getOrNull(1)?.trim().orEmpty(),
            rawTranscript = text
        )
    }

    private fun jobFrom(text: String, today: LocalDate, now: LocalDateTime): ParsedIntent.Job {
        val lower = text.lowercase()
        val interview = Regex("""\binterviews?\b""").containsMatchIn(lower)
        val status = when {
            Regex("""\b(?:reject|rejected|rejection|turned\s+me\s+down|declined)\b""").containsMatchIn(lower) -> JobApplicationStatus.REJECTED
            lower.contains("offer") -> JobApplicationStatus.OFFER
            lower.containsAny("withdraw", "pulled out") -> JobApplicationStatus.WITHDRAWN
            lower.containsAny("screen", "recruiter call", "phone call with the recruiter") -> JobApplicationStatus.SCREENING
            interview -> JobApplicationStatus.INTERVIEW
            else -> JobApplicationStatus.APPLIED
        }
        val companyRegex = if (status == JobApplicationStatus.OFFER || status == JobApplicationStatus.REJECTED)
            Regex("""(?i)\b(?:at|to|with|from|by)\s+([a-z0-9][\w&'.-]*)""")
        else Regex("""(?i)\b(?:at|to|with)\s+([a-z0-9][\w&'.-]*)""")
        val company = companyRegex
            .find(text)?.groupValues?.get(1)
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()
            .ifBlank { "Company" }
        val title = jobRole(text, company) ?: if (interview) "Interview" else "Role"
        val source = Regex("""(?i)\b(?:site|via|through|from)\s+([A-Za-z0-9][\w .&-]{1,40})""")
            .find(text)?.groupValues?.get(1)
            ?.replace(Regex("""(?i)\s+\b(?:for|as|in|at|on|location|located)\b.*$"""), "")
            ?.trim().orEmpty()
            .takeUnless { it.equals(company, true) }.orEmpty()
        val location = jobLocation(text)
        val url = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')').orEmpty()
        val labeled = labeledJobDates(text, today, now)
        val appliedSpoken = labeled.any { it.label == "Applied" } || lower.contains("applied")
        val applied = labeled.firstOrNull { it.label == "Applied" }?.date ?: today
        val others = labeled.filter { it.label != "Applied" }
        val follow = others.firstOrNull { it.label == "Follow up" || it.label == "Interview" }?.date
        return ParsedIntent.Job(
            company = company,
            title = title,
            status = status,
            appliedOn = applied,
            followUpOn = follow,
            notes = text.trim(),
            source = source,
            url = url,
            location = location,
            extraDates = JobDates.format(others),
            appliedSpoken = appliedSpoken,
            rawTranscript = text,
        )
    }

    private val PLACE_TAIL = Regex(
        """(?i)\s+\b(?:for|as|on|via|from|through|at|to|with|interview|deadline|follow|application|applied|role|position)\b.*$"""
    )
    private val PLACE_SKIP = setOf(
        "the", "a", "an", "my", "this", "that", "order", "general", "android", "ios", "software", "remote"
    )

    /** "location Dhaka", "located in Dhaka", or "in Dhaka". Stops before the next clause. */
    private fun jobLocation(text: String): String {
        val explicit = Regex(
            """(?i)\b(?:location(?:\s+is)?|located(?:\s+in)?|based in|office in|city|venue(?:\s+is)?)\s+([A-Za-z][\w .'-]{1,48})"""
        ).find(text)?.groupValues?.get(1)?.replace(Regex("""\s*[.].*$"""), "")
        val phrase = explicit ?: Regex("""(?i)\bin\s+([A-Za-z][\w .'-]{1,40})""")
            .findAll(text)
            .map { it.groupValues[1] }
            .lastOrNull()
            ?: return ""
        val place = phrase.replace(PLACE_TAIL, "").trim().trimEnd(',', '.')
        val first = place.substringBefore(' ').lowercase()
        if (place.length < 2 || first in PLACE_SKIP) return ""
        return place
    }

    /** Each named date keeps the words that say what it is for. */
    private fun labeledJobDates(text: String, today: LocalDate, now: LocalDateTime): List<JobDateNote> {
        val labels = listOf(
            "application date" to "Applied",
            "applied" to "Applied",
            "interview" to "Interview",
            "deadline" to "Deadline",
            "follow up" to "Follow up",
            "follow-up" to "Follow up",
            "offer date" to "Offer",
            "start date" to "Start"
        )
        val found = mutableListOf<JobDateNote>()
        val lower = text.lowercase()
        for ((word, label) in labels) {
            val at = lower.indexOf(word)
            if (at < 0) continue
            val slice = text.substring(at, minOf(text.length, at + 80))
            val date = resolveSpokenDateTime(slice, today, now)?.toLocalDate()
                ?: if (DateRules.hasDateAnchor(slice)) parseEventDate(slice, today, LocalTime.MAX, now) else null
            if (date != null && found.none { it.label == label }) found += JobDateNote(label, date)
        }
        return found
    }

    // --- bill / debt / goal names -----------------------------------------------------------

    private fun isDebtUtterance(text: String): Boolean = isDebt(text)

    /** Bills need bill/subscription, a due word, or a repeat with a known bill name, unless it was just paid. */
    private fun isBillUtterance(text: String): Boolean {
        val lower = text.lowercase()
        val past = PAST_PAY.containsMatchIn(lower)
        val due = DUE_WORD.containsMatchIn(text)
        val freq = hasBillFrequency(text)
        if (isDebtUtterance(text)) return false
        if (past && !due && !freq) return false
        if (BILL_WORD.containsMatchIn(text) && !(past && !due)) return !(past && !freq)
        if (due) return true
        if (freq && (isKnownBill(text) || keywordCategory(text) in BILL_CATEGORIES)) return !past
        return false
    }

    private val BILL_CATEGORIES = setOf(TransactionCategory.SUBSCRIPTIONS, TransactionCategory.UTILITIES, TransactionCategory.RENT)

    private fun extractBillName(input: String): String {
        merchantHit(input)?.let { return it.name }
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private val FRIEND_AFTER_VERB = Regex(
        """(?i)\b(?:owe(?:s|d)?(?:\s+me)?|lent|lend|loaned|borrowed|borrow|pay\s+back|payback|repay|loan\s+(?:to|from)|iou\s+(?:to|from)|gave|give|return)\s+(?:me\s+)?(?:from\s+|to\s+)?(?:my\s+|the\s+|a\s+)?([A-Za-z][\w'-]*)"""
    )
    private val NAME_FILLER = setOf(
        "today", "tomorrow", "yesterday", "morning", "night", "week", "next",
        "bill", "bills", "subscription", "subscriptions", "due", "weekly",
        "monthly", "yearly", "annual", "annually", "quarterly", "save", "goal",
        "savings", "owe", "owes", "owed", "lent", "lend", "borrowed", "borrow",
        "they", "from", "dollars", "dollar", "bucks", "me", "my", "a", "an",
        "the", "for", "to", "on", "of", "add", "i", "paid", "pay", "taka", "tk", "loan", "loaned", "back",
        "set", "per", "every", "each", "month", "year", "is", "am", "was", "still", "have", "need", "must",
        "give", "return", "repay", "that", "it", "he", "she", "we", "you", "and", "amount", "money", "cash",
        "usd", "bdt", "rs", "rupees", "euros", "pounds", "payment", "installment", "instalment", "recurring",
    )

    private fun extractFriendName(input: String): String {
        val fromVerb = FRIEND_AFTER_VERB.find(input)?.groupValues?.get(1)
        if (!fromVerb.isNullOrBlank() && fromVerb.lowercase() !in NAME_FILLER && !fromVerb.first().isDigit()) {
            return fromVerb.replaceFirstChar { it.uppercase() }
        }
        val subject = Regex("""(?i)^\s*([A-Za-z][\w'-]*)\s+(?:owes?|will\s+pay|borrowed|took)\b""").find(input)?.groupValues?.get(1)
        if (!subject.isNullOrBlank() && subject.lowercase() !in NAME_FILLER && subject.lowercase() !in setOf("they", "he", "she", "i", "we")) {
            return subject.replaceFirstChar { it.uppercase() }
        }
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private fun extractGoalName(input: String): String {
        Regex("""(?i)\b(?:for|towards?|to\s+buy|to\s+get|called|named)\s+(?:an?\s+|the\s+|my\s+|a\s+new\s+)?([A-Za-z][\w' -]*)""").find(input)?.let { m ->
            val cut = m.groupValues[1].split(' ').takeWhile { it.lowercase() !in NAME_CUT }.joinToString(" ").trim()
            if (cut.isNotBlank() && cut.lowercase() !in NAME_FILLER) return cut.replaceFirstChar { it.uppercase() }
        }
        val named = extractName(input)
        if (named.isNotBlank() && named.lowercase() !in NAME_FILLER) return named
        return remainingName(input)
    }

    private fun remainingName(input: String): String {
        val withoutAmount = NUM_RE.replace(input, " ")
        return withoutAmount.split(Regex("""\s+"""))
            .map { it.trim(',', '.', '!', '?', '"', '\'', '$', '£', '€') }
            .filter { it.isNotBlank() && it.lowercase() !in NAME_FILLER && it.none(Char::isDigit) }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .trim()
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }

    private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    private val BN_NUMBERS = listOf(
        "একশো" to "100",
        "পঁয়তাল্লিশ" to "45",
        "পনেরো" to "15",
        "এগারো" to "11",
        "বারো" to "12",
        "ত্রিশ" to "30",
        "বিশ" to "20",
        "ষাট" to "60",
        "পাঁচ" to "5",
        "ছয়" to "6",
        "সাত" to "7",
        "আট" to "8",
        "নয়" to "9",
        "দশ" to "10",
        "চার" to "4",
        "তিন" to "3",
        "দুই" to "2",
        "এক" to "1",
    )
    private val BN_PHRASES = listOf(
        "মনে করিয়ে দিও" to "remind me to",
        "মনে করিয়ে দাও" to "remind me to",
        "মনে করিয়ে" to "remind me",
        "অ্যালার্ম" to "alarm",
        "এলার্ম" to "alarm",
        "ইন্টারভিউ" to "interview",
        "মিটিং" to "meeting",
        "আগামীকাল" to "tomorrow",
        "গতকাল" to "yesterday",
        "পরশু" to "day after tomorrow",
        "আজকে" to "today",
        "আজ" to "today",
        "মিনিটের" to "min",
        "মিনিট" to "min",
        "ঘণ্টা" to "hour",
        "ঘন্টা" to "hour",
        "হাজার" to "thousand",
        "লাখ" to "lakh",
        "টাকা" to "taka",
        "খরচ করেছি" to "spent",
        "খরচ" to "spent",
        "কিনেছি" to "bought",
        "বেতন" to "salary",
        "ধার নিয়েছি" to "borrowed",
        "ধার দিয়েছি" to "lent",
        "চাকরি" to "job",
        "কাজ" to "task",
        "রবিবার" to "sunday",
        "সোমবার" to "monday",
        "মঙ্গলবার" to "tuesday",
        "বুধবার" to "wednesday",
        "বৃহস্পতিবার" to "thursday",
        "শুক্রবার" to "friday",
        "শনিবার" to "saturday",
        "সকাল" to "morning",
        "দুপুর" to "afternoon",
        "বিকাল" to "afternoon",
        "সন্ধ্যা" to "evening",
        "রাত" to "night",
        "পরে" to "after",
        "বাদে" to "after",
        "পর" to "after",
        "টায়" to "",
        "টা" to "",
    )
}
