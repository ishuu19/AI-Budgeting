package com.ledgerai.app.data.ai

import com.ledgerai.app.data.finance.SpendGuideCalculator
import com.ledgerai.app.data.finance.SpendGuideStatus
import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.ledgerai.app.presentation.components.money
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

const val SOURCE_RULES = "Rules"
const val SOURCE_AI = "AI"

/** Everything the answerer needs, loaded once from Room. Pure data so tests can build it by hand. */
data class RuleSnapshot(
    val now: LocalDateTime,
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<Goal> = emptyList(),
    /** Calendar occurrences (already expanded) for roughly the next two weeks. */
    val events: List<CalendarEvent> = emptyList(),
    val cash: Double? = null,
)

/** A reply produced without the cloud. [link] names a screen: budget, bills, debts, goals, calendar, transactions, forecast. */
data class RuleAnswer(val text: String, val link: String? = null, val source: String = SOURCE_RULES)

enum class AskIntent {
    RUNWAY, GOALS, OVER_BUDGET, SAFE_TODAY, BUDGET_LEFT, SAVINGS_RATE, COMPARE, BIGGEST, FREE_TIME,
    NEXT_EVENT, AGENDA, DUE, BALANCE, INCOME_EXPENSE, SUMMARY, SPENT
}

/**
 * Rule-based question answering over local data. English and Banglish phrasings are matched with a
 * pattern table. [answer] returns null when no rule fits, and the caller then asks the cloud.
 * All numbers come from the snapshot, never from a model.
 */
object RuleAnswers {

    // ─── pattern table ───────────────────────────────────────────────────────

    private fun rx(alts: String) = Regex("(?<![a-z0-9])(?:$alts)(?![a-z0-9])")

    /** All groups must be present. Each group is an alternation of whole words or phrases. */
    private class Pat(val groups: List<Regex>) {
        fun ok(n: String) = groups.all { it.containsMatchIn(n) }
    }

    private fun p(vararg groups: String) = Pat(groups.map { rx(it) })

    private class Rule(val intent: AskIntent, val pats: List<Pat>)

    private fun r(intent: AskIntent, vararg pats: Pat) = Rule(intent, pats.toList())

    private const val KINDS = "class|classes|lecture|lectures|exam|exams|event|events|alarm|alarms|task|tasks|" +
        "meeting|meetings|appointment|reminder|routine|quiz|test|schedule|thing|item|lab|assignment"

    private val RULES: List<Rule> = listOf(
        r(
            AskIntent.RUNWAY,
            p("runway"),
            p("how long", "last|lasts|survive|enough|go"),
            p("how many days", "money|cash|balance|left|last|enough"),
            p("days", "money|cash|balance", "left|remaining|last|enough"),
            p("koto din|koyto din|koyta din", "cholbe|choleb|cholbo|chalabo|thakbe|cholte|cholbe"),
            p("koydin|koyodin|kodin", "cholbe|thakbe|cholbo"),
            p("run out of money|run out|out of money|go broke|broke by"),
            p("cash|money|balance|savings", "last|lasts|enough"),
        ),
        r(
            AskIntent.GOALS,
            p("goal|goals|lokkho|lakkho|lokkhyo|target|targets|লক্ষ্য"),
            p("saving for|saving up for|saved for"),
            p("goal|goals", "progress|status|how|koto"),
        ),
        r(
            AskIntent.OVER_BUDGET,
            p("over budget|overbudget|overspent|overspend|overspending|over spent|over limit|went over|gone over|exceeded|exceed|exceeding|crossed|overshot"),
            p("am i", "over|within|under|on track|inside", "budget|limit|budgets"),
            p("am i|are we", "spending|spend", "too much|a lot|too many|more than i should"),
            p("budget|limit|budgets", "cross|par|exceed|shesh|sesh|finish|finished|over|beshi"),
            p("limit|budget", "cross|par", "korechi|korsi|kore|hoye|hoyeche|koreche"),
            p("which|any|kon", "budget|budgets|category|limit", "over|crossed|exceeded|cross|par"),
        ),
        r(
            AskIntent.SAFE_TODAY,
            p("safe", "spend|spending|khoroch|kharch|kharcha"),
            p("can i spend"),
            p("how much", "can|could|should|may|am i able to", "spend"),
            p("daily", "limit|allowance|budget|spend|spending|cap"),
            p("spend guide|spendguide|allowance"),
            p("ajke|aj|today|ajker", "koto", "khoroch|kharch|kharcha|kharoch|spend", "korte|kora|pari|jabe|parbo|parbe|parboi"),
            p("ajker|todays|today s", "limit|budget|allowance|cap"),
            p("limit today|today limit"),
            p("afford", "today|now|ajke"),
            p("what can i spend|what should i spend|how much to spend"),
            p("per day|a day|each day|every day", "spend|can|budget|should"),
            p("koto", "khoroch|kharch|kharcha|kharoch", "kora jabe|korte pari|korte parbo|korbo|korle|kora jay"),
        ),
        r(
            AskIntent.BUDGET_LEFT,
            p("budget|limit|budgets|বাজেট", "left|remaining|remain|remains|baki|baaki|bakii|available|বাকি"),
            p("left|remaining|baki|baaki|বাকি", "budget|limit|budgets|বাজেট"),
            p("budget", "koto|how much|ache|achhe"),
            p("show|see|view|check", "budget|budgets|limit|limits"),
            p("my budget|my budgets"),
            p("koto|কত", "budget"),
        ),
        r(
            AskIntent.SAVINGS_RATE,
            p("saving rate|savings rate|save rate|rate of saving|saving percentage|savings percentage|savings percent"),
            p("how much|koto|কত", "saved|savings|sonchoy|sanchay|joma|bachalam|bachaisi|bachiyechi|bachano|bachate|bachate pari|সঞ্চয়"),
            p("am i saving|did i save|have i saved|do i save"),
            p("sonchoy|sanchay|সঞ্চয়", "hocche|hoyeche|korechi|korsi|kemon|rate|percent"),
            p("savings", "this month|last month|this week|so far"),
        ),
        r(
            AskIntent.COMPARE,
            p("compare|compared|comparison|difference|different"),
            p("vs|versus", "last month|previous month|last week|previous week|last year|gato mash|ager mash|spending|spent|expenses"),
            p("more|less|higher|lower|beshi|kom|increase|increased|decrease|decreased|up|down|better|worse|improve|improved|changed|change", "than last|last month|previous month|gato mash|gato mase|ager mash|ager mase|last week|previous week|gato shoptaho|ager shoptaho|gato soptaho|than before|than previous|last year"),
            p("than last|than previous|against last|from last|since last"),
            p("gato mash|gato mase|ager mash|ager mase|গত মাস", "tulona|compare|theke|er cheye|er chaite"),
        ),
        r(
            AskIntent.BIGGEST,
            p("biggest|largest|highest|maximum|greatest|costliest|priciest|most expensive|sobcheye boro|sobcheye beshi|shobcheye boro|shobcheye beshi|sob cheye boro|boro khoroch|beshi khoroch|bishal khoroch", "expense|expenses|purchase|purchases|spend|spending|spent|transaction|transactions|category|categories|khoroch|kharcha|kharch|bill|buy|bought|payment|item|merchant|shop|place|cost|one|thing|day"),
            p("top", "expense|expenses|category|categories|spending|merchant|merchants|purchases|khoroch"),
            p("most", "spent|spend|spending|money|expensive"),
            p("where", "money|taka|cash|spending", "going|goes|went|gone|spent|khoroch"),
            p("kothay", "khoroch|kharcha|taka|beshi|jacche|gelo|gese|jay|jai"),
            p("what am i spending|what did i spend|what do i spend", "most|too much|the most|mostly"),
            p("kon khate|kon category|kon catagory|konta beshi|kisher pichone|which category|top category|top categories|biggest category|breakdown|category wise|categorywise"),
            p("where is my money|where did my money"),
        ),
        r(
            AskIntent.FREE_TIME,
            p("free", "time|slot|slots|block|blocks|hours|hour|window|gap|gaps|period|somoy|shomoy"),
            p("gap|gaps", "day|today|tomorrow|schedule|calendar|week"),
            p("am i free|when am i free|are you free|will i be free|i free|free achi|free ache|free thakbo|free thakbe|free thakbo|free achhi|free achhe"),
            p("when can i", "study|work|meet|schedule|exercise|gym|read|rest|do|relax|fit"),
            p("available|availability", "time|today|tomorrow|slot|ajke|kal"),
            p("obosor|obshor|khali|kokhon free|free somoy|free shomoy|khali somoy|khali shomoy|khali time|gap in my|অবসর"),
            p("do i have", "free|time|gap|spare|room"),
        ),
        r(
            AskIntent.NEXT_EVENT,
            p("next|upcoming|first", KINDS),
            p("whats next|what is next|whats up next|what s next|what is coming up|whats coming up|what s coming up"),
            p("when", "is|s|my", "class|exam|alarm|meeting|lecture|quiz|test|appointment|lab"),
            p("porer|porer ta|poroborti|porobortti|পরের", KINDS),
            p("next ki|porer ki|kokhon", "class|exam|alarm|lecture|meeting|task|quiz"),
            p("kokhon", "ache|achhe|hobe|shuru|suru"),
        ),
        r(
            AskIntent.DUE,
            p("due|overdue|deadline|deadlines|pending|unpaid|outstanding"),
            p("upcoming|coming up|next|soon", "bill|bills|payment|payments|rent|subscription|subscriptions|emi|installment"),
            p("bill|bills|বিল", "this week|this month|today|tomorrow|soon|ache|achhe|koto|baki|due|pay|ase"),
            p("who owes|owes me|owe me|i owe|do i owe|owed|dhar|dhaar|dhari|loan|loans|debt|debts|ধার|ঋণ"),
            p("what", "should|do|need|must|have to", "pay"),
            p("bill ache|bill achhe|bill koto|baki bill|bill baki|konta bill|kon bill"),
            p("task|tasks|todo|to do|todos", "due|pending|left|remaining|today|ache|achhe|overdue"),
            p("kar kache|kake", "taka|dhar|dite|pabo|pai"),
            p("when", "pay|bill|rent|due|deadline"),
        ),
        r(
            AskIntent.AGENDA,
            p("what do i have|what have i got|whats on|what is on|whats planned|what is planned|what is scheduled|what am i doing|what s on|whats happening|any plans|my plans|what do we have"),
            p("schedule|agenda|timetable|routine|calendar", "today|tomorrow|ajke|kal|kalke|aj|ajker|kaler|this week|tonight"),
            p("ki ki ache|ki ache|ki ki achhe|ki achhe|ki plan|kon kon class|ki class|koita class|koyta class|koyta|koita"),
            p("classes|class|tasks|events|meetings|exams", "today|tomorrow|ajke|kal|kalke|have|ache|achhe|tonight"),
            p("how many", "class|classes|events|tasks|meetings|exams", "today|tomorrow|have|ache|achhe|this week"),
            p("my day|my week|my morning|my evening"),
        ),
        r(
            AskIntent.BALANCE,
            p("balance|net worth|networth|ব্যালেন্স"),
            p("how much", "money|cash|taka", "do i have|have i got|is left|left|i have|remaining|in hand|on hand"),
            p("koto taka", "ache|achhe|asey|ase|baki|hate|aca"),
            p("money|cash|taka", "left|remaining|baki|ache|achhe|in hand|on hand|available"),
            p("what do i have", "money|cash|savings|taka"),
        ),
        r(
            AskIntent.BUDGET_LEFT,
            p("how much", "left|remaining|baki|baaki"),
            p("koto baki|baki koto|koto bakii|koto ache|koto achhe"),
            p("how much do i have left|what is left|whats left|what s left"),
        ),
        r(
            AskIntent.INCOME_EXPENSE,
            p("income|earn|earned|earning|earnings|aay|aoy|upartan|upaarjon|received|salary|paycheck|paycheque|আয়"),
            p("net|cashflow|cash flow|in and out|profit|surplus|deficit"),
            p("income|earn|earned", "expense|expenses|spending|spent|khoroch"),
            p("koto", "income|earn|aay|aoy|ay|upartan|peyechi|payechi|pelam|peyesi"),
        ),
        r(
            AskIntent.SUMMARY,
            p("how am i doing|how is my money|how is my month|how s my month|how is my spending|how s my spending|how are my finances|how are things|am i okay|am i ok"),
            p("overview|summary|summarize|summarise|snapshot|status report|financial status|report"),
            p("kemon cholche|kemon jacche|kemon jachhe|kemon achi|kemon chole"),
            p("month|mash|mase", "summary|overview|status|report"),
        ),
        r(
            AskIntent.SPENT,
            p("spent|spend|spending|expense|expenses|expenditure|khoroch|khorcha|kharcha|kharch|kharoch|khorc|cost|costs|paid|খরচ"),
            p("gelo|gese|geche|gechhe|korechi|korsi|korlam|hoyeche|hoise|holo", "khoroch|kharcha|kharch|taka|koto"),
        ),
    )

    // ─── text helpers ────────────────────────────────────────────────────────

    /** Lowercase, drop punctuation and apostrophes, collapse spaces. Bangla script is kept. */
    internal fun normalize(q: String): String {
        val sb = StringBuilder(q.length)
        for (ch in q.lowercase()) {
            when {
                ch == '\'' || ch == '’' -> {}
                ch in 'a'..'z' || ch in '0'..'9' || ch in 'ঀ'..'৿' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    /** The first intent whose pattern matches. Exposed for tests. */
    fun detect(question: String): AskIntent? {
        val n = normalize(question)
        if (n.isEmpty()) return null
        for (rule in RULES) if (rule.pats.any { it.ok(n) }) return rule.intent
        // "how much on groceries", "transport e koto": an amount question that names a category.
        if (rx("how much|koto|kto|total|কত").containsMatchIn(n) && categoryIn(n) != null) return AskIntent.SPENT
        return null
    }

    private val CATEGORY_WORDS: List<Pair<TransactionCategory, Regex>> = listOf(
        TransactionCategory.FOOD to rx("food|foods|lunch|dinner|breakfast|snack|snacks|coffee|tea|restaurant|restaurants|grocery|groceries|eating|eat|meal|meals|khabar|khabare|bazar|bajar|nasta|dining|takeout|takeaway|খাবার|বাজার"),
        TransactionCategory.TRANSPORT to rx("transport|transportation|travel|uber|pathao|bus|rickshaw|taxi|cab|fuel|petrol|gas|cng|commute|fare|metro|train|jatayat|gari|যাতায়াত"),
        TransactionCategory.ENTERTAINMENT to rx("entertainment|movie|movies|cinema|games|gaming|fun|concert|binodon"),
        TransactionCategory.SHOPPING to rx("shopping|clothes|clothing|shoes|daraz|kenakata|kapor|কেনাকাটা"),
        TransactionCategory.HEALTH to rx("health|medicine|medicines|medical|doctor|pharmacy|hospital|gym|osudh|oshudh|dawai|ঔষধ"),
        TransactionCategory.RENT to rx("rent|housing|bari bhara|bhara|বাড়ি ভাড়া"),
        TransactionCategory.UTILITIES to rx("utilities|utility|electricity|water|internet|wifi|phone bill|mobile bill|gas bill|current bill|recharge"),
        TransactionCategory.SUBSCRIPTIONS to rx("subscription|subscriptions|netflix|spotify|prime|youtube premium"),
        TransactionCategory.EDUCATION to rx("education|tuition|course|courses|books|book|school|fees|fee|porashona|coaching|পড়াশোনা"),
    )

    internal fun categoryIn(n: String): TransactionCategory? =
        CATEGORY_WORDS.mapNotNull { (cat, re) -> re.find(n)?.let { cat to it.range.first } }
            .minByOrNull { it.second }?.first

    private fun merchantIn(n: String, txs: List<Transaction>): String? {
        val names = txs.map { it.merchant.trim() }.filter { it.length >= 3 }.distinctBy { it.lowercase() }
        return names
            .filter { name ->
                val key = normalize(name)
                key.length >= 3 && Regex("(?<![a-z0-9])${Regex.escape(key)}(?![a-z0-9])").containsMatchIn(n)
            }
            .maxByOrNull { it.length }
    }

    private data class Period(val label: String, val from: LocalDate, val to: LocalDate, val explicit: Boolean = true)

    private val MONTHS = listOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7,
        "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private val LAST_N = Regex("(?:last|past|previous|prev|gato|ager)\\s+(\\d{1,3})\\s+(?:days?|din)")

    /** Past-looking period for money questions. [default] is used when nothing is named. */
    private fun periodFor(n: String, today: LocalDate, default: Period): Period {
        LAST_N.find(n)?.let { m ->
            val days = m.groupValues[1].toInt().coerceIn(1, 365)
            return Period("in the last $days days", today.minusDays(days - 1L), today)
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when {
            rx("last year|gato bochor|ager bochor").containsMatchIn(n) ->
                Period("last year", LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
            rx("this year|ei bochor|ei bochore").containsMatchIn(n) ->
                Period("this year", LocalDate.of(today.year, 1, 1), today)
            rx("last month|previous month|gato mash|gato mase|ager mash|ager mase|gato moshe|গত মাস").containsMatchIn(n) -> {
                val first = today.withDayOfMonth(1).minusMonths(1)
                Period("last month", first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            rx("last week|previous week|gato shoptaho|gato soptaho|ager shoptaho|gato saptaho|ager soptaho").containsMatchIn(n) ->
                Period("last week", monday.minusWeeks(1), monday.minusDays(1))
            rx("this week|ei shoptaho|ei soptaho|ei shaptaho|ei saptaho|shoptahe|soptahe").containsMatchIn(n) ->
                Period("this week", monday, today)
            rx("this month|ei mash|ei mase|ei maser|mashe|ei moshe|এই মাস").containsMatchIn(n) ->
                Period("this month", today.withDayOfMonth(1), today)
            rx("yesterday|gotokal|gotokaal|গতকাল|kal").containsMatchIn(n) && !rx("tomorrow|kalke").containsMatchIn(n) ->
                Period("yesterday", today.minusDays(1), today.minusDays(1))
            rx("today|ajke|ajk|aj|ajker|আজ|todays|now").containsMatchIn(n) ->
                Period("today", today, today)
            else -> {
                val hit = MONTHS.firstOrNull { rx(it.first).containsMatchIn(n) }
                if (hit != null) {
                    var year = today.year
                    if (hit.second > today.monthValue) year -= 1
                    val first = LocalDate.of(year, hit.second, 1)
                    Period("in ${first.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)}", first, first.with(TemporalAdjusters.lastDayOfMonth()))
                } else default
            }
        }
    }

    /** Day for schedule style questions: today, tomorrow, or a weekday name. */
    private fun dayFor(n: String, today: LocalDate): LocalDate? {
        if (rx("tomorrow|tmrw|tomorow|kalke|agamikal|পরশু").containsMatchIn(n)) return today.plusDays(1)
        if (rx("day after tomorrow|porshu|parshu").containsMatchIn(n)) return today.plusDays(2)
        if (rx("kal").containsMatchIn(n) && !rx("gotokal|yesterday").containsMatchIn(n)) return today.plusDays(1)
        if (rx("today|tonight|ajke|ajk|aj|ajker|আজ").containsMatchIn(n)) return today
        val names = mapOf(
            "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
        )
        names.entries.firstOrNull { rx(it.key).containsMatchIn(n) }?.let {
            return today.with(TemporalAdjusters.next(it.value))
        }
        return null
    }

    private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    private fun pct(x: Double) = "${Math.round(x)}%"

    private fun lower(c: TransactionCategory) = c.displayName.lowercase(Locale.ENGLISH)

    private fun inPeriod(tx: Transaction, p: Period) = !tx.date.isBefore(p.from) && !tx.date.isAfter(p.to) && tx.deletedAt == null

    private fun expenses(s: RuleSnapshot) = s.transactions.filter { it.type == TransactionType.EXPENSE && it.deletedAt == null }

    private fun incomes(s: RuleSnapshot) = s.transactions.filter { it.type == TransactionType.INCOME && it.deletedAt == null }

    private fun inLine(from: LocalDateTime, to: LocalDateTime): String {
        val m = Duration.between(from, to).toMinutes().coerceAtLeast(0)
        return when {
            m < 1 -> "now"
            m < 60 -> "in ${m}m"
            m < 24 * 60 -> "in ${m / 60}h" + (if (m % 60 != 0L) " ${m % 60}m" else "")
            else -> "in ${m / (24 * 60)}d"
        }
    }

    private fun dur(minutes: Long): String =
        if (minutes < 60) "${minutes}m" else "${minutes / 60}h" + (if (minutes % 60 != 0L) " ${minutes % 60}m" else "")

    // ─── entry point ─────────────────────────────────────────────────────────

    /** Answers [question] from [snap], or returns null when no rule applies. */
    fun answer(question: String, snap: RuleSnapshot, fmt: (Double) -> String = { money(it) }): RuleAnswer? {
        val n = normalize(question)
        if (n.isEmpty()) return null
        var intent = detect(question)
        if (intent == null) {
            // "how much on food", "koto food e" without a spend verb.
            val ask = rx("how much|koto|kto|total|কত").containsMatchIn(n)
            if (ask && (categoryIn(n) != null || merchantIn(n, snap.transactions) != null)) intent = AskIntent.SPENT
        }
        intent ?: return null
        val today = snap.now.toLocalDate()
        return when (intent) {
            AskIntent.SPENT -> spent(n, snap, today, fmt)
            AskIntent.SAFE_TODAY -> safeToday(snap, today, fmt)
            AskIntent.BUDGET_LEFT -> budgetLeft(n, snap, today, fmt)
            AskIntent.OVER_BUDGET -> overBudget(snap, today, fmt)
            AskIntent.NEXT_EVENT -> nextEvent(n, snap)
            AskIntent.AGENDA -> agenda(n, snap)
            AskIntent.DUE -> due(n, snap, fmt)
            AskIntent.GOALS -> goals(n, snap, fmt)
            AskIntent.INCOME_EXPENSE -> incomeExpense(n, snap, today, fmt)
            AskIntent.BIGGEST -> biggest(n, snap, today, fmt)
            AskIntent.COMPARE -> compare(n, snap, today, fmt)
            AskIntent.FREE_TIME -> freeTime(n, snap)
            AskIntent.SAVINGS_RATE -> savingsRate(n, snap, today, fmt)
            AskIntent.RUNWAY -> runway(snap, today, fmt)
            AskIntent.BALANCE -> balance(snap, today, fmt)
            AskIntent.SUMMARY -> summary(snap, today, fmt)
        }
    }

    // ─── handlers ────────────────────────────────────────────────────────────

    private fun spent(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val period = periodFor(n, today, Period("this month", today.withDayOfMonth(1), today, explicit = false))
        val cat = categoryIn(n)
        val merchant = merchantIn(n, s.transactions)
        val matched = expenses(s).filter { tx ->
            inPeriod(tx, period) &&
                (merchant == null || tx.merchant.trim().equals(merchant, ignoreCase = true)) &&
                (merchant != null || cat == null || tx.category == cat)
        }
        val subject = when {
            merchant != null -> merchant
            cat != null -> lower(cat)
            else -> null
        }
        if (matched.isEmpty()) {
            return RuleAnswer("No ${subject?.let { "$it " } ?: ""}spending recorded ${period.label}.", "transactions")
        }
        val total = matched.sumOf { it.amount }
        val count = matched.size
        val base = "You spent ${fmt(total)}" + (subject?.let { " on $it" } ?: "") + " ${period.label} ($count ${if (count == 1) "transaction" else "transactions"})."
        val extra = if (subject == null && count > 1) {
            matched.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .maxByOrNull { it.value }?.let { " Most went to ${lower(it.key)} (${fmt(it.value)})." }.orEmpty()
        } else ""
        return RuleAnswer(base + extra, "transactions")
    }

    private fun monthBudgets(s: RuleSnapshot, today: LocalDate): List<Triple<Budget, Double, Double>> {
        val first = today.withDayOfMonth(1)
        return s.budgets.filter { it.deletedAt == null && it.month == today.monthValue && it.year == today.year }.map { b ->
            val spent = expenses(s).filter { it.category == b.category && !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
            Triple(b, spent, b.monthlyLimit - spent)
        }
    }

    private fun billsInPeriod(s: RuleSnapshot, from: LocalDate, to: LocalDate): Double =
        s.bills.filter { it.isActive && it.deletedAt == null }.sumOf { b ->
            if (b.nextDueDate.isBefore(from)) b.amount
            else SpendGuideCalculator.billAmountDueInPeriod(b.amount, b.frequency, b.nextDueDate, from, to)
        }

    private fun safeToday(s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val (start, end) = SpendGuideCalculator.monthPeriod(today)
        val budgets = monthBudgets(s, today)
        val monthIncome = incomes(s).filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.sumOf { it.amount }
        val monthSpent = expenses(s).filter { !it.date.isBefore(start) && !it.date.isAfter(today) }.sumOf { it.amount }
        val remaining = when {
            budgets.isNotEmpty() -> budgets.sumOf { it.first.monthlyLimit } - monthSpent
            monthIncome > 0.0 || s.cash != null -> (s.cash ?: 0.0) + monthIncome - monthSpent
            else -> return RuleAnswer("Set a budget or log some income and I can tell you what is safe to spend today.", "budget")
        }
        val spentToday = expenses(s).filter { it.date == today }.sumOf { it.amount }
        val weekday = expenses(s).filter { !it.date.isBefore(today.minusDays(56)) && !it.date.isAfter(today) }
            .groupBy { it.date.dayOfWeek }.mapValues { (_, v) -> v.sumOf { it.amount } / 8.0 }
        val debtsDue = s.debts.filter { !it.isPaid && it.deletedAt == null && it.direction == DebtDirection.I_OWE && it.dueDate != null && !it.dueDate.isAfter(end) }.sumOf { it.amount }
        val result = SpendGuideCalculator.compute(
            today = today,
            periodStart = start,
            periodEnd = end,
            remainingBudget = remaining,
            billsDueBeforePeriodEnd = billsInPeriod(s, today, end),
            debtDueBeforePeriodEnd = debtsDue,
            goalContribution = 0.0,
            speculations = emptyList(),
            spentToday = spentToday,
            spentThisPeriod = monthSpent,
            weekdaySpendAvg = weekday,
        )
        val text = when (result.status) {
            SpendGuideStatus.OVER -> "You are past today's limit: spent ${fmt(spentToday)} against ${fmt(result.hardLimit)}. ${result.daysLeft} days left this month."
            else -> {
                val left = (result.hardLimit - spentToday).coerceAtLeast(0.0)
                "Safe to spend today: ${fmt(result.guideAmount)} (hard limit ${fmt(result.hardLimit)}). " +
                    "You have spent ${fmt(spentToday)} so far, ${fmt(left)} of room left. ${result.daysLeft} days left this month."
            }
        }
        return RuleAnswer(text, "budget")
    }

    private fun budgetLeft(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val budgets = monthBudgets(s, today)
        if (budgets.isEmpty()) {
            val spent = expenses(s).filter { !it.date.isBefore(today.withDayOfMonth(1)) && !it.date.isAfter(today) }.sumOf { it.amount }
            return RuleAnswer("You have no budgets set for this month. You have spent ${fmt(spent)} so far.", "budget")
        }
        val cat = categoryIn(n)
        if (cat != null) {
            val hit = budgets.firstOrNull { it.first.category == cat }
                ?: return RuleAnswer("There is no ${lower(cat)} budget this month.", "budget")
            val (b, spent, left) = hit
            val used = if (b.monthlyLimit > 0) spent / b.monthlyLimit * 100 else 0.0
            return RuleAnswer(
                if (left >= 0) "${b.category.displayName}: ${fmt(left)} left of ${fmt(b.monthlyLimit)} (${pct(used)} used)."
                else "${b.category.displayName}: over by ${fmt(-left)} (spent ${fmt(spent)} of ${fmt(b.monthlyLimit)}).",
                "budget"
            )
        }
        val limit = budgets.sumOf { it.first.monthlyLimit }
        val spent = budgets.sumOf { it.second }
        val left = limit - spent
        val days = today.lengthOfMonth() - today.dayOfMonth + 1
        return RuleAnswer(
            if (left >= 0) "You have ${fmt(left)} left across ${budgets.size} ${if (budgets.size == 1) "budget" else "budgets"} (spent ${fmt(spent)} of ${fmt(limit)}), about ${fmt(left / days)} a day for $days days."
            else "You are over your budgets by ${fmt(-left)} (spent ${fmt(spent)} of ${fmt(limit)}).",
            "budget"
        )
    }

    private fun overBudget(s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val budgets = monthBudgets(s, today)
        if (budgets.isEmpty()) return RuleAnswer("You have no budgets set this month, so nothing can be over.", "budget")
        val over = budgets.filter { it.third < 0 }
        if (over.isNotEmpty()) {
            val list = over.sortedBy { it.third }.joinToString("; ") { "${it.first.category.displayName} by ${fmt(-it.third)}" }
            return RuleAnswer("Yes. Over budget: $list.", "budget")
        }
        val near = budgets.filter { it.first.monthlyLimit > 0 && it.second / it.first.monthlyLimit * 100 >= it.first.alertThreshold }
        if (near.isNotEmpty()) {
            val list = near.joinToString("; ") { "${it.first.category.displayName} ${pct(it.second / it.first.monthlyLimit * 100)}" }
            return RuleAnswer("Not over, but close: $list.", "budget")
        }
        val top = budgets.filter { it.first.monthlyLimit > 0 }.maxByOrNull { it.second / it.first.monthlyLimit }
        return RuleAnswer(
            "No, you are within every budget." + (top?.let { " Highest use: ${it.first.category.displayName} at ${pct(it.second / it.first.monthlyLimit * 100)}." } ?: ""),
            "budget"
        )
    }

    private fun eventKindFilter(n: String): Pair<String, (CalendarEvent) -> Boolean> = when {
        rx("class|classes|lecture|lectures|lab|course").containsMatchIn(n) ->
            "class" to { e -> e.kind == CalendarEventKind.CLASS }
        rx("exam|exams|quiz|test").containsMatchIn(n) ->
            "exam" to { e -> e.kind == CalendarEventKind.EXAM }
        rx("alarm|alarms|wake").containsMatchIn(n) ->
            "alarm" to { e -> e.kind == CalendarEventKind.ALARM }
        rx("task|tasks|todo|assignment").containsMatchIn(n) ->
            "task" to { e -> e.kind == CalendarEventKind.TASK }
        rx("routine|habit").containsMatchIn(n) ->
            "routine" to { e -> e.kind == CalendarEventKind.ROUTINE }
        rx("meeting|meetings|appointment|event|events").containsMatchIn(n) ->
            "event" to { e -> e.kind.isPlainEvent }
        else -> "item" to { e -> e.kind != CalendarEventKind.ALARM || e.isEnabled }
    }

    private fun openEvents(s: RuleSnapshot) = s.events.filter { it.isEnabled && it.hasDate && !(it.isCompleted && it.kind == CalendarEventKind.TASK) }

    private fun nextEvent(n: String, s: RuleSnapshot): RuleAnswer {
        val (noun, filter) = eventKindFilter(n)
        val today = s.now.toLocalDate()
        val day = dayFor(n, today)
        val upcoming = openEvents(s).filter(filter).filter { !it.startAt.isBefore(s.now) }.sortedBy { it.startAt }
        val pick = if (day != null && day != today) upcoming.firstOrNull { it.startAt.toLocalDate() == day } else upcoming.firstOrNull()
        if (pick == null) {
            val where = if (day != null && day != today) " on ${day.format(DAY_FMT)}" else " in the next two weeks"
            return RuleAnswer("You have no upcoming $noun$where.", "calendar")
        }
        val whenText = if (pick.startAt.toLocalDate() == today) "today at ${pick.startAt.format(TIME_FMT)}"
        else "${pick.startAt.format(DAY_FMT)} at ${pick.startAt.format(TIME_FMT)}"
        val where = pick.location.takeIf { it.isNotBlank() }?.let { " in $it" }.orEmpty()
        val label = if (noun == "item") pick.kind.name.lowercase(Locale.ENGLISH) else noun
        return RuleAnswer("Your next $label is ${pick.title} $whenText$where (${inLine(s.now, pick.startAt)}).", "calendar")
    }

    private fun agenda(n: String, s: RuleSnapshot): RuleAnswer {
        val today = s.now.toLocalDate()
        val weekAsk = rx("this week|my week").containsMatchIn(n)
        val day = dayFor(n, today) ?: today
        val (noun, filter) = eventKindFilter(n)
        val items = openEvents(s).filter(filter).filter {
            if (weekAsk) !it.startAt.toLocalDate().isBefore(today) && !it.startAt.toLocalDate().isAfter(today.plusDays(6))
            else it.startAt.toLocalDate() == day
        }.sortedBy { it.startAt }
        val label = if (weekAsk) "this week" else when (day) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> "on ${day.format(DAY_FMT)}"
        }
        val kindWord = if (items.size == 1) noun else if (noun.endsWith("s")) "${noun}es" else "${noun}s"
        if (items.isEmpty()) return RuleAnswer("You have no $kindWord $label.", "calendar")
        val lines = items.take(6).joinToString("; ") { e ->
            (if (weekAsk) "${e.startAt.format(DAY_FMT)} " else "") + e.startAt.format(TIME_FMT) + " " + e.title
        }
        val more = if (items.size > 6) " and ${items.size - 6} more" else ""
        return RuleAnswer("You have ${items.size} $kindWord $label: $lines$more.", "calendar")
    }

    private fun due(n: String, s: RuleSnapshot, fmt: (Double) -> String): RuleAnswer {
        val today = s.now.toLocalDate()
        val wantDebt = rx("owes|owe|owed|dhar|dhaar|dhari|loan|loans|debt|debts|ধার|ঋণ|kar kache|kake").containsMatchIn(n)
        val wantTask = rx("task|tasks|todo|to do|todos").containsMatchIn(n)
        val wantBill = rx("bill|bills|rent|subscription|subscriptions|emi|installment|payment|payments|বিল").containsMatchIn(n)
        val all = !wantDebt && !wantTask && !wantBill
        val horizon = when {
            rx("today|ajke|aj|ajker|tonight").containsMatchIn(n) -> today
            rx("tomorrow|kalke|kal").containsMatchIn(n) -> today.plusDays(1)
            rx("this month|ei mash|ei mase|month").containsMatchIn(n) -> today.with(TemporalAdjusters.lastDayOfMonth())
            else -> today.plusDays(7)
        }
        val lines = mutableListOf<String>()
        var link = "bills"
        if (all || wantBill) {
            val bills = s.bills.filter { it.isActive && it.deletedAt == null && !it.nextDueDate.isAfter(horizon) }.sortedBy { it.nextDueDate }
            if (bills.isNotEmpty()) {
                val total = bills.sumOf { it.amount }
                val list = bills.take(4).joinToString(", ") {
                    val late = it.nextDueDate.isBefore(today)
                    "${it.name} ${fmt(it.amount)} " + if (late) "(overdue)" else it.nextDueDate.format(DAY_FMT)
                }
                lines += "Bills: $list" + (if (bills.size > 4) " +${bills.size - 4} more" else "") + " (total ${fmt(total)})"
            }
        }
        if (all || wantDebt) {
            val iOwe = rx("i owe|do i owe|what do i owe|dite hobe|dite|ami dhar").containsMatchIn(n)
            val theyOwe = rx("owes me|owe me|who owes|pabo|pai|amar kache").containsMatchIn(n)
            val debts = s.debts.filter { !it.isPaid && it.deletedAt == null }
                .filter { if (all) it.dueDate != null && !it.dueDate.isAfter(horizon) else true }
                .filter { if (iOwe && !theyOwe) it.direction == DebtDirection.I_OWE else if (theyOwe && !iOwe) it.direction == DebtDirection.THEY_OWE else true }
                .sortedBy { it.dueDate ?: LocalDate.MAX }
            if (debts.isNotEmpty()) {
                val list = debts.take(4).joinToString(", ") {
                    (if (it.direction == DebtDirection.I_OWE) "you owe " else "") + it.friendName +
                        (if (it.direction == DebtDirection.THEY_OWE) " owes you" else "") + " " + fmt(it.amount) +
                        (it.dueDate?.let { d -> " by ${d.format(DAY_FMT)}" } ?: "")
                }
                lines += "Debts: $list"
                if (!all) link = "debts"
            } else if (wantDebt && !all) {
                return RuleAnswer("No open debts. Nobody owes you and you owe nobody.", "debts")
            }
        }
        if (all || wantTask) {
            val tasks = openEvents(s).filter { it.kind == CalendarEventKind.TASK && !it.startAt.toLocalDate().isAfter(horizon) }.sortedBy { it.startAt }
            if (tasks.isNotEmpty()) {
                val list = tasks.take(4).joinToString(", ") { "${it.title} (${if (it.startAt.isBefore(s.now)) "overdue" else it.startAt.format(DAY_FMT)})" }
                lines += "Tasks: $list" + (if (tasks.size > 4) " +${tasks.size - 4} more" else "")
                if (!all) link = "calendar"
            }
        }
        if (lines.isEmpty()) {
            val scope = when (horizon) {
                today -> "today"
                today.plusDays(1) -> "by tomorrow"
                else -> "in that window"
            }
            return RuleAnswer("Nothing is due $scope.", link)
        }
        return RuleAnswer(lines.joinToString(". ") + ".", if (all) "bills" else link)
    }

    private fun goals(n: String, s: RuleSnapshot, fmt: (Double) -> String): RuleAnswer {
        val goals = s.goals.filter { it.deletedAt == null }
        if (goals.isEmpty()) return RuleAnswer("You have no goals yet. Add one to track your progress.", "goals")
        val named = goals.firstOrNull { g -> g.name.length >= 3 && normalize(g.name).let { key -> key.isNotEmpty() && n.contains(key) } }
        if (named != null) return RuleAnswer(goalLine(named, s.now.toLocalDate(), fmt, detailed = true), "goals")
        val active = goals.filter { !it.isCompleted }
        if (active.isEmpty()) return RuleAnswer("All ${goals.size} goals are complete.", "goals")
        val lines = active.sortedByDescending { it.progressPercent }.take(3).joinToString("; ") { goalLine(it, s.now.toLocalDate(), fmt, false) }
        return RuleAnswer("${active.size} active ${if (active.size == 1) "goal" else "goals"}. $lines.", "goals")
    }

    private fun goalLine(g: Goal, today: LocalDate, fmt: (Double) -> String, detailed: Boolean): String {
        val base = "${g.name}: ${g.progressPercent}% (${fmt(g.savedAmount)} of ${fmt(g.targetAmount)})"
        if (!detailed) return base
        val tail = g.targetDate?.let { d ->
            val months = ChronoUnit.MONTHS.between(today, d).coerceAtLeast(1)
            ", ${fmt(g.remaining)} to go, about ${fmt(g.remaining / months)} a month until ${d.format(DAY_FMT)}"
        } ?: ", ${fmt(g.remaining)} to go"
        return base + tail
    }

    private fun incomeExpense(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val period = periodFor(n, today, Period("this month", today.withDayOfMonth(1), today, explicit = false))
        val inc = incomes(s).filter { inPeriod(it, period) }.sumOf { it.amount }
        val exp = expenses(s).filter { inPeriod(it, period) }.sumOf { it.amount }
        if (inc == 0.0 && exp == 0.0) return RuleAnswer("Nothing recorded ${period.label}.", "transactions")
        val net = inc - exp
        val verdict = if (net >= 0) "you kept ${fmt(net)}" else "you are ${fmt(-net)} short"
        return RuleAnswer("${period.label.replaceFirstChar { it.uppercase() }}: income ${fmt(inc)}, spending ${fmt(exp)}, so $verdict.", "transactions")
    }

    private fun biggest(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val period = periodFor(n, today, Period("this month", today.withDayOfMonth(1), today, explicit = false))
        val items = expenses(s).filter { inPeriod(it, period) }
        if (items.isEmpty()) return RuleAnswer("No spending recorded ${period.label}.", "transactions")
        val byCategory = Regex("categor|where|kothay|kon khate|konta|breakdown|going|goes|gone|went").containsMatchIn(n)
        if (byCategory) {
            val total = items.sumOf { it.amount }
            val groups = items.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
            val top = groups.take(3).joinToString(", ") { "${it.key.displayName} ${fmt(it.value)} (${pct(it.value / total * 100)})" }
            return RuleAnswer("${period.label.replaceFirstChar { it.uppercase() }} you spent ${fmt(total)}. Top categories: $top.", "transactions")
        }
        val tx = items.maxBy { it.amount }
        val who = tx.merchant.ifBlank { tx.category.displayName }
        return RuleAnswer(
            "Your biggest expense ${period.label} was ${fmt(tx.amount)} on $who (${tx.category.displayName}) on ${tx.date.format(DAY_FMT)}.",
            "transactions"
        )
    }

    private fun compare(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val cat = categoryIn(n)
        val week = rx("week|shoptaho|soptaho|saptaho").containsMatchIn(n)
        val (curFrom, curTo, prevFrom, prevTo, label) = if (week) {
            val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val elapsed = ChronoUnit.DAYS.between(mon, today)
            Quint(mon, today, mon.minusWeeks(1), mon.minusWeeks(1).plusDays(elapsed), "week")
        } else {
            val first = today.withDayOfMonth(1)
            val prevFirst = first.minusMonths(1)
            val prevDay = minOf(today.dayOfMonth, prevFirst.lengthOfMonth())
            Quint(first, today, prevFirst, prevFirst.withDayOfMonth(prevDay), "month")
        }
        fun sum(from: LocalDate, to: LocalDate) = expenses(s).filter { (cat == null || it.category == cat) && !it.date.isBefore(from) && !it.date.isAfter(to) }.sumOf { it.amount }
        val cur = sum(curFrom, curTo)
        val prev = sum(prevFrom, prevTo)
        val subject = cat?.let { " on ${lower(it)}" }.orEmpty()
        if (prev == 0.0 && cur == 0.0) return RuleAnswer("No spending$subject recorded this $label or last.", "transactions")
        if (prev == 0.0) return RuleAnswer("You spent ${fmt(cur)}$subject this $label so far and nothing at the same point last $label.", "transactions")
        val diff = cur - prev
        val change = diff / prev * 100
        val trend = when {
            Math.abs(change) < 2 -> "about the same"
            diff > 0 -> "${fmt(diff)} (${pct(change)}) more"
            else -> "${fmt(-diff)} (${pct(-change)}) less"
        }
        return RuleAnswer("This $label you spent ${fmt(cur)}$subject so far, $trend than ${fmt(prev)} at the same point last $label.", "transactions")
    }

    private data class Quint(val a: LocalDate, val b: LocalDate, val c: LocalDate, val d: LocalDate, val e: String)

    private fun freeTime(n: String, s: RuleSnapshot): RuleAnswer {
        val today = s.now.toLocalDate()
        val day = dayFor(n, today) ?: today
        val dayStart = LocalDateTime.of(day, LocalTime.of(8, 0))
        val dayEnd = LocalDateTime.of(day, LocalTime.of(22, 0))
        val from = if (day == today) maxOf(dayStart, s.now) else dayStart
        val label = when (day) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> "on ${day.format(DAY_FMT)}"
        }
        if (!from.isBefore(dayEnd)) return RuleAnswer("The day is nearly over, so there is no free time left $label.", "calendar")
        val busy = openEvents(s).filter { it.kind.blocksTime && !it.allDay && it.endAt.isAfter(from) && it.startAt.isBefore(dayEnd) }
            .map { maxOf(it.startAt, from) to minOf(maxOf(it.endAt, it.startAt.plusMinutes(1)), dayEnd) }
            .sortedBy { it.first }
        val gaps = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var cursor = from
        for ((bs, be) in busy) {
            if (bs.isAfter(cursor)) gaps += cursor to bs
            if (be.isAfter(cursor)) cursor = be
        }
        if (cursor.isBefore(dayEnd)) gaps += cursor to dayEnd
        val usable = gaps.filter { Duration.between(it.first, it.second).toMinutes() >= 30 }
        if (usable.isEmpty()) return RuleAnswer("You have no free block of 30 minutes or more $label.", "calendar")
        val total = usable.sumOf { Duration.between(it.first, it.second).toMinutes() }
        val list = usable.take(4).joinToString(", ") {
            "${it.first.format(TIME_FMT)}-${it.second.format(TIME_FMT)} (${dur(Duration.between(it.first, it.second).toMinutes())})"
        }
        return RuleAnswer("You are free $label: $list. About ${dur(total)} in all.", "calendar")
    }

    private fun savingsRate(n: String, s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val period = periodFor(n, today, Period("this month", today.withDayOfMonth(1), today, explicit = false))
        val inc = incomes(s).filter { inPeriod(it, period) }.sumOf { it.amount }
        val exp = expenses(s).filter { inPeriod(it, period) }.sumOf { it.amount }
        if (inc <= 0.0) return RuleAnswer("No income recorded ${period.label}, so there is no savings rate yet.", "transactions")
        val kept = inc - exp
        val rate = kept / inc * 100
        return RuleAnswer(
            if (kept >= 0) "Savings rate ${period.label}: ${pct(rate)}. You kept ${fmt(kept)} of ${fmt(inc)} income."
            else "You spent ${fmt(-kept)} more than you earned ${period.label} (savings rate ${pct(rate)}).",
            "transactions"
        )
    }

    private fun netBalance(s: RuleSnapshot, today: LocalDate): Double {
        val first = today.withDayOfMonth(1)
        val inc = incomes(s).filter { !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
        val exp = expenses(s).filter { !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
        return (s.cash ?: 0.0) + inc - exp
    }

    private fun balance(s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val bal = netBalance(s, today)
        val source = if (s.cash != null) "cash on hand plus this month's income minus spending" else "this month's income minus spending"
        return RuleAnswer("You have about ${fmt(bal)} ($source).", "forecast")
    }

    private fun runway(s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val bal = netBalance(s, today)
        val last30 = expenses(s).filter { !it.date.isBefore(today.minusDays(29)) && !it.date.isAfter(today) }.sumOf { it.amount }
        val daily = last30 / 30.0
        if (bal <= 0.0) return RuleAnswer("Your balance is ${fmt(bal)}, so there is no runway left. Add income or cash on hand to project one.", "forecast")
        if (daily <= 0.0) return RuleAnswer("No spending in the last 30 days, so your ${fmt(bal)} is not shrinking.", "forecast")
        val days = (bal / daily).toLong()
        return RuleAnswer(
            "At ${fmt(daily)} a day, your ${fmt(bal)} lasts about $days days, until ${today.plusDays(days).format(DAY_FMT)}.",
            "forecast"
        )
    }

    private fun summary(s: RuleSnapshot, today: LocalDate, fmt: (Double) -> String): RuleAnswer {
        val first = today.withDayOfMonth(1)
        val inc = incomes(s).filter { !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
        val exp = expenses(s).filter { !it.date.isBefore(first) && !it.date.isAfter(today) }.sumOf { it.amount }
        val budgets = monthBudgets(s, today)
        val over = budgets.count { it.third < 0 }
        val parts = mutableListOf("This month: income ${fmt(inc)}, spending ${fmt(exp)}, net ${fmt(inc - exp)}.")
        if (budgets.isNotEmpty()) {
            parts += if (over > 0) "$over of ${budgets.size} budgets are over." else "All ${budgets.size} budgets are within limit."
        }
        return RuleAnswer(parts.joinToString(" "), "transactions")
    }
}
