package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.Bill
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.Budget
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.Debt
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RuleAnswersTest {

    private val now = LocalDateTime.of(2026, 10, 14, 12, 0) // Wednesday
    private val fmt: (Double) -> String = { "$" + String.format("%.0f", it) }

    private fun tx(
        y: Int, m: Int, d: Int, amount: Double, cat: TransactionCategory, merchant: String = "",
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(amount = amount, type = type, category = cat, merchant = merchant, date = LocalDate.of(y, m, d))

    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min)

    private val snap = RuleSnapshot(
        now = now,
        transactions = listOf(
            tx(2026, 10, 1, 1000.0, TransactionCategory.SALARY, "Employer", TransactionType.INCOME),
            tx(2026, 10, 2, 50.0, TransactionCategory.FOOD, "Shwapno"),
            tx(2026, 10, 5, 30.0, TransactionCategory.FOOD, "Starbucks"),
            tx(2026, 10, 10, 20.0, TransactionCategory.TRANSPORT, "Uber"),
            tx(2026, 10, 12, 200.0, TransactionCategory.SHOPPING, "Amazon"),
            tx(2026, 10, 14, 15.0, TransactionCategory.FOOD, "Starbucks"),
            tx(2026, 9, 1, 1000.0, TransactionCategory.SALARY, "Employer", TransactionType.INCOME),
            tx(2026, 9, 3, 100.0, TransactionCategory.FOOD, "Shwapno"),
            tx(2026, 9, 10, 40.0, TransactionCategory.TRANSPORT, "Uber"),
            tx(2026, 9, 12, 60.0, TransactionCategory.SHOPPING, "Amazon"),
            tx(2026, 9, 20, 25.0, TransactionCategory.FOOD, "Shwapno"),
        ),
        budgets = listOf(
            Budget(category = TransactionCategory.FOOD, monthlyLimit = 100.0, month = 10, year = 2026),
            Budget(category = TransactionCategory.SHOPPING, monthlyLimit = 150.0, month = 10, year = 2026),
            Budget(category = TransactionCategory.TRANSPORT, monthlyLimit = 100.0, month = 10, year = 2026),
        ),
        bills = listOf(
            Bill(name = "Rent", amount = 500.0, nextDueDate = LocalDate.of(2026, 10, 16)),
            Bill(name = "Netflix", amount = 15.0, nextDueDate = LocalDate.of(2026, 10, 20)),
            Bill(name = "Internet", amount = 30.0, nextDueDate = LocalDate.of(2026, 10, 10)),
        ),
        debts = listOf(
            Debt(friendName = "Rahim", amount = 100.0, direction = DebtDirection.THEY_OWE, dueDate = LocalDate.of(2026, 10, 18)),
            Debt(friendName = "Karim", amount = 50.0, direction = DebtDirection.I_OWE, dueDate = LocalDate.of(2026, 10, 15)),
        ),
        goals = listOf(
            Goal(name = "Laptop", targetAmount = 1000.0, savedAmount = 450.0, targetDate = LocalDate.of(2027, 2, 14)),
            Goal(name = "Trip", targetAmount = 500.0, savedAmount = 100.0),
        ),
        events = listOf(
            CalendarEvent(title = "Math", startAt = at(15, 10), endAt = at(15, 11, 30), kind = CalendarEventKind.CLASS, location = "R101"),
            CalendarEvent(title = "Physics", startAt = at(15, 13), endAt = at(15, 14), kind = CalendarEventKind.CLASS),
            CalendarEvent(title = "Chemistry", startAt = at(20, 9), endAt = at(20, 11), kind = CalendarEventKind.EXAM),
            CalendarEvent(title = "Wake up", startAt = at(15, 6, 30), kind = CalendarEventKind.ALARM),
            CalendarEvent(title = "Submit report", startAt = at(14, 18), kind = CalendarEventKind.TASK),
            CalendarEvent(title = "Buy gift", startAt = at(13, 9), kind = CalendarEventKind.TASK),
            CalendarEvent(title = "Dentist", startAt = at(14, 15), endAt = at(14, 16), kind = CalendarEventKind.EVENT),
        ),
        cash = 500.0,
    )

    private fun ask(q: String) = RuleAnswers.answer(q, snap, fmt)

    // ─── intent table: 170 phrasings, English and Banglish ───────────────────

    private val intentCases: List<Pair<String, AskIntent?>> = listOf(
        // spent
        "how much did I spend on food this month" to AskIntent.SPENT,
        "how much have I spent this week" to AskIntent.SPENT,
        "what did I spend yesterday" to AskIntent.SPENT,
        "total spending today" to AskIntent.SPENT,
        "spent on transport last month" to AskIntent.SPENT,
        "my expenses this month" to AskIntent.SPENT,
        "how much on groceries" to AskIntent.SPENT,
        "how much did I spend at Starbucks" to AskIntent.SPENT,
        "spending on shopping in September" to AskIntent.SPENT,
        "expense for the last 7 days" to AskIntent.SPENT,
        "how much for transport this week" to AskIntent.SPENT,
        "how much did I pay for coffee" to AskIntent.SPENT,
        "ajke koto khoroch korsi" to AskIntent.SPENT,
        "ei mash e koto khoroch hoyeche" to AskIntent.SPENT,
        "gato mash e food e koto khoroch" to AskIntent.SPENT,
        "koto taka khoroch holo" to AskIntent.SPENT,
        "ei shoptaho koto kharcha" to AskIntent.SPENT,
        "gotokal koto khoroch korlam" to AskIntent.SPENT,
        "transport e koto" to AskIntent.SPENT,
        "food a koto gelo" to AskIntent.SPENT,
        "spend on entertainment this year" to AskIntent.SPENT,
        "show me spending for rent" to AskIntent.SPENT,
        "cost of subscriptions this month" to AskIntent.SPENT,
        "total expense last month" to AskIntent.SPENT,
        "I want to know my spending on health" to AskIntent.SPENT,
        // safe today
        "how much can I spend today" to AskIntent.SAFE_TODAY,
        "safe to spend today" to AskIntent.SAFE_TODAY,
        "what is my daily limit" to AskIntent.SAFE_TODAY,
        "daily allowance" to AskIntent.SAFE_TODAY,
        "can I spend 50 today" to AskIntent.SAFE_TODAY,
        "how much should I spend per day" to AskIntent.SAFE_TODAY,
        "what can I spend now" to AskIntent.SAFE_TODAY,
        "spend guide" to AskIntent.SAFE_TODAY,
        "ajke koto khoroch korte pari" to AskIntent.SAFE_TODAY,
        "ajker limit koto" to AskIntent.SAFE_TODAY,
        "ajke koto kharch kora jabe" to AskIntent.SAFE_TODAY,
        "today limit" to AskIntent.SAFE_TODAY,
        "what is todays budget" to AskIntent.SAFE_TODAY,
        "how much could I spend a day" to AskIntent.SAFE_TODAY,
        "can i afford something today" to AskIntent.SAFE_TODAY,
        // budget left
        "how much is left in my budget" to AskIntent.BUDGET_LEFT,
        "budget remaining" to AskIntent.BUDGET_LEFT,
        "food budget left" to AskIntent.BUDGET_LEFT,
        "how much is left in transport budget" to AskIntent.BUDGET_LEFT,
        "remaining budget this month" to AskIntent.BUDGET_LEFT,
        "budget e koto baki" to AskIntent.BUDGET_LEFT,
        "food budget koto baki ache" to AskIntent.BUDGET_LEFT,
        "baki budget" to AskIntent.BUDGET_LEFT,
        "how much do I have left" to AskIntent.BUDGET_LEFT,
        "koto baki" to AskIntent.BUDGET_LEFT,
        "what is left of my limit" to AskIntent.BUDGET_LEFT,
        "limit left for shopping" to AskIntent.BUDGET_LEFT,
        "show my budget" to AskIntent.BUDGET_LEFT,
        // over budget
        "am I over budget" to AskIntent.OVER_BUDGET,
        "did I go over budget" to AskIntent.OVER_BUDGET,
        "am I overspending" to AskIntent.OVER_BUDGET,
        "have I exceeded my limit" to AskIntent.OVER_BUDGET,
        "which budget is over" to AskIntent.OVER_BUDGET,
        "am I within my budget" to AskIntent.OVER_BUDGET,
        "am I spending too much" to AskIntent.OVER_BUDGET,
        "is any category over the limit" to AskIntent.OVER_BUDGET,
        "budget cross korechi" to AskIntent.OVER_BUDGET,
        "limit cross hoyeche" to AskIntent.OVER_BUDGET,
        "budget shesh" to AskIntent.OVER_BUDGET,
        "overspent anywhere" to AskIntent.OVER_BUDGET,
        "I crossed my budget" to AskIntent.OVER_BUDGET,
        "am i on track with budget" to AskIntent.OVER_BUDGET,
        // next event
        "when is my next class" to AskIntent.NEXT_EVENT,
        "next exam" to AskIntent.NEXT_EVENT,
        "what is my next alarm" to AskIntent.NEXT_EVENT,
        "next task" to AskIntent.NEXT_EVENT,
        "upcoming exams" to AskIntent.NEXT_EVENT,
        "whats next" to AskIntent.NEXT_EVENT,
        "when is my exam" to AskIntent.NEXT_EVENT,
        "when is my next meeting" to AskIntent.NEXT_EVENT,
        "next lecture" to AskIntent.NEXT_EVENT,
        "first class tomorrow" to AskIntent.NEXT_EVENT,
        "porer class kokhon" to AskIntent.NEXT_EVENT,
        "porer exam kobe" to AskIntent.NEXT_EVENT,
        "next event" to AskIntent.NEXT_EVENT,
        "my next appointment" to AskIntent.NEXT_EVENT,
        "when is my next lab" to AskIntent.NEXT_EVENT,
        "upcoming alarm" to AskIntent.NEXT_EVENT,
        // agenda
        "what do I have today" to AskIntent.AGENDA,
        "what do i have tomorrow" to AskIntent.AGENDA,
        "schedule for today" to AskIntent.AGENDA,
        "agenda tomorrow" to AskIntent.AGENDA,
        "how many classes today" to AskIntent.AGENDA,
        "ajke ki ki ache" to AskIntent.AGENDA,
        "kalke ki class ache" to AskIntent.AGENDA,
        "my day" to AskIntent.AGENDA,
        "what is on today" to AskIntent.AGENDA,
        "whats planned for tomorrow" to AskIntent.AGENDA,
        "any plans tomorrow" to AskIntent.AGENDA,
        "classes today" to AskIntent.AGENDA,
        "my week" to AskIntent.AGENDA,
        "timetable for tomorrow" to AskIntent.AGENDA,
        // due
        "what bills are due" to AskIntent.DUE,
        "bills due this week" to AskIntent.DUE,
        "any overdue bills" to AskIntent.DUE,
        "what is due today" to AskIntent.DUE,
        "upcoming bills" to AskIntent.DUE,
        "who owes me money" to AskIntent.DUE,
        "what do I owe" to AskIntent.DUE,
        "any debts" to AskIntent.DUE,
        "pending tasks" to AskIntent.DUE,
        "tasks due today" to AskIntent.DUE,
        "deadlines this week" to AskIntent.DUE,
        "what should I pay this week" to AskIntent.DUE,
        "next bill" to AskIntent.DUE,
        "bill ache kono" to AskIntent.DUE,
        "dhar koto" to AskIntent.DUE,
        "kar kache taka pabo" to AskIntent.DUE,
        "baki bill gulo" to AskIntent.DUE,
        "unpaid bills" to AskIntent.DUE,
        "when is rent due" to AskIntent.DUE,
        "loan status" to AskIntent.DUE,
        // goals
        "how are my goals" to AskIntent.GOALS,
        "goal progress" to AskIntent.GOALS,
        "how is my laptop goal" to AskIntent.GOALS,
        "what am I saving for" to AskIntent.GOALS,
        "my targets" to AskIntent.GOALS,
        "lokkho koto dur" to AskIntent.GOALS,
        "goals status" to AskIntent.GOALS,
        "savings goal progress" to AskIntent.GOALS,
        "how close am I to my trip goal" to AskIntent.GOALS,
        // income vs expense
        "income vs expense this month" to AskIntent.INCOME_EXPENSE,
        "how much did I earn this month" to AskIntent.INCOME_EXPENSE,
        "my income" to AskIntent.INCOME_EXPENSE,
        "net this week" to AskIntent.INCOME_EXPENSE,
        "earnings last month" to AskIntent.INCOME_EXPENSE,
        "cash flow" to AskIntent.INCOME_EXPENSE,
        "koto income hoyeche" to AskIntent.INCOME_EXPENSE,
        "ei mash e aay koto" to AskIntent.INCOME_EXPENSE,
        "did I earn more than I spent" to AskIntent.INCOME_EXPENSE,
        "profit this month" to AskIntent.INCOME_EXPENSE,
        "salary received" to AskIntent.INCOME_EXPENSE,
        // biggest
        "what was my biggest expense" to AskIntent.BIGGEST,
        "largest purchase this month" to AskIntent.BIGGEST,
        "highest spending category" to AskIntent.BIGGEST,
        "top categories" to AskIntent.BIGGEST,
        "where is my money going" to AskIntent.BIGGEST,
        "what am I spending the most on" to AskIntent.BIGGEST,
        "most expensive thing I bought" to AskIntent.BIGGEST,
        "sobcheye boro khoroch ki" to AskIntent.BIGGEST,
        "kothay beshi khoroch hocche" to AskIntent.BIGGEST,
        "kon category te beshi khoroch" to AskIntent.BIGGEST,
        "category wise spending" to AskIntent.BIGGEST,
        "biggest expense last month" to AskIntent.BIGGEST,
        // compare
        "compare to last month" to AskIntent.COMPARE,
        "am I spending more than last month" to AskIntent.COMPARE,
        "this month vs last month" to AskIntent.COMPARE,
        "spending compared to last week" to AskIntent.COMPARE,
        "difference from last month" to AskIntent.COMPARE,
        "is my food spending higher than last month" to AskIntent.COMPARE,
        "gato mash er sathe tulona" to AskIntent.COMPARE,
        "less than last month?" to AskIntent.COMPARE,
        "how does this week compare" to AskIntent.COMPARE,
        // free time
        "when am I free today" to AskIntent.FREE_TIME,
        "free time tomorrow" to AskIntent.FREE_TIME,
        "do I have any free slots" to AskIntent.FREE_TIME,
        "am I free at 3" to AskIntent.FREE_TIME,
        "when can I study" to AskIntent.FREE_TIME,
        "free hours today" to AskIntent.FREE_TIME,
        "kokhon free thakbo" to AskIntent.FREE_TIME,
        "ajke free time ache" to AskIntent.FREE_TIME,
        "khali somoy kokhon" to AskIntent.FREE_TIME,
        "any gaps in my day" to AskIntent.FREE_TIME,
        "availability tomorrow" to AskIntent.FREE_TIME,
        // savings rate
        "what is my savings rate" to AskIntent.SAVINGS_RATE,
        "how much did I save this month" to AskIntent.SAVINGS_RATE,
        "am I saving enough" to AskIntent.SAVINGS_RATE,
        "did I save anything" to AskIntent.SAVINGS_RATE,
        "koto sonchoy korlam" to AskIntent.SAVINGS_RATE,
        "saving rate last month" to AskIntent.SAVINGS_RATE,
        "sonchoy kemon hocche" to AskIntent.SAVINGS_RATE,
        // runway
        "how long will my money last" to AskIntent.RUNWAY,
        "what is my runway" to AskIntent.RUNWAY,
        "how many days of money are left" to AskIntent.RUNWAY,
        "when will I run out of money" to AskIntent.RUNWAY,
        "koto din cholbe taka" to AskIntent.RUNWAY,
        "will my cash last the month" to AskIntent.RUNWAY,
        // balance
        "what is my balance" to AskIntent.BALANCE,
        "how much money do I have" to AskIntent.BALANCE,
        "koto taka ache" to AskIntent.BALANCE,
        "cash left" to AskIntent.BALANCE,
        // summary
        "how am I doing this month" to AskIntent.SUMMARY,
        "give me a summary" to AskIntent.SUMMARY,
        "kemon cholche" to AskIntent.SUMMARY,
        "overview" to AskIntent.SUMMARY,
        "how are my finances" to AskIntent.SUMMARY,
        // not answerable
        "tell me a joke" to null,
        "how can I save more" to null,
        "what is inflation" to null,
        "hello" to null,
        "should I buy a house" to null,
        "write me a poem about money" to null,
        "" to null,
        "   " to null,
        "explain compound interest to me" to null,
    )

    @Test
    fun intentTableHasEnoughPhrasings() {
        assertTrue("need 150+ phrasings, have ${intentCases.size}", intentCases.size >= 150)
    }

    @Test
    fun everyPhrasingMapsToItsIntent() {
        val failures = intentCases.filter { (q, expected) -> RuleAnswers.detect(q) != expected }
            .map { (q, expected) -> "'$q' expected $expected got ${RuleAnswers.detect(q)}" }
        assertTrue("Wrong intent for:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyAnsweredPhrasingProducesText() {
        val empty = intentCases.filter { it.second != null }
            .filter { (q, _) -> ask(q)?.text.isNullOrBlank() }
            .map { it.first }
        assertTrue("No answer for:\n" + empty.joinToString("\n"), empty.isEmpty())
    }

    @Test
    fun unansweredPhrasingsReturnNull() {
        listOf("tell me a joke", "how can I save more", "what is inflation", "hello", "", "write me a poem about money")
            .forEach { assertNull(it, ask(it)) }
    }

    @Test
    fun everyAnswerIsLabelledRules() {
        intentCases.filter { it.second != null }.forEach { (q, _) ->
            assertEquals(q, SOURCE_RULES, ask(q)?.source)
        }
    }

    // ─── content ─────────────────────────────────────────────────────────────

    private fun assertAnswer(q: String, vararg parts: String) {
        val a = ask(q)
        assertNotNull("no answer for '$q'", a)
        parts.forEach { assertTrue("'$q' -> '${a!!.text}' should contain '$it'", a.text.contains(it)) }
    }

    @Test
    fun spentCategoryThisMonth() = assertAnswer("how much did I spend on food this month", "\$95", "food", "this month")

    @Test
    fun spentCategoryLastMonth() = assertAnswer("spent on transport last month", "\$40", "transport", "last month")

    @Test
    fun spentToday() = assertAnswer("how much did I spend today", "\$15", "today")

    @Test
    fun spentMerchant() = assertAnswer("how much did I spend at starbucks", "\$45", "Starbucks")

    @Test
    fun spentNothingYesterday() = assertAnswer("how much did I spend yesterday", "No spending recorded yesterday")

    @Test
    fun spentWholeMonth() = assertAnswer("my expenses this month", "\$315", "shopping")

    @Test
    fun spentBanglish() = assertAnswer("ajke koto khoroch korsi", "\$15", "today")

    @Test
    fun spentLastSevenDays() = assertAnswer("expense for the last 7 days", "last 7 days", "\$")

    @Test
    fun spentInNamedMonth() = assertAnswer("spending on shopping in september", "\$60", "September")

    @Test
    fun budgetLeftCategory() = assertAnswer("food budget left", "Food: \$5 left of \$100", "95%")

    @Test
    fun budgetLeftOverCategory() = assertAnswer("shopping budget left", "over by \$50")

    @Test
    fun budgetLeftTotal() = assertAnswer("how much is left in my budget", "\$35 left across 3 budgets")

    @Test
    fun budgetLinkOpensBudget() = assertEquals("budget", ask("budget remaining")?.link)

    @Test
    fun overBudgetNamesShopping() = assertAnswer("am i over budget", "Over budget", "Shopping by \$50")

    @Test
    fun safeToday() = assertAnswer("how much can i spend today", "past today's limit")

    @Test
    fun nextClass() = assertAnswer("when is my next class", "Math", "Thu 15 Oct", "10:00", "R101")

    @Test
    fun nextExam() = assertAnswer("next exam", "Chemistry", "Tue 20 Oct")

    @Test
    fun nextAlarm() = assertAnswer("what is my next alarm", "Wake up", "06:30")

    @Test
    fun nextTaskToday() = assertAnswer("next task", "Submit report", "today at 18:00")

    @Test
    fun firstClassTomorrow() = assertAnswer("first class tomorrow", "Math")

    @Test
    fun agendaTomorrow() = assertAnswer("what do i have tomorrow", "3 items", "06:30", "Math", "Physics")

    @Test
    fun agendaClassesTomorrow() = assertAnswer("classes tomorrow", "2 classes", "Math")

    @Test
    fun agendaTodayEvents() = assertAnswer("what is on today", "Dentist")

    @Test
    fun dueAll() = assertAnswer("what is due", "Bills:", "Internet", "overdue", "Rent", "Debts:", "Tasks:", "Buy gift")

    @Test
    fun dueBillsOnly() = assertAnswer("bills due this week", "Rent", "Netflix", "total \$545")

    @Test
    fun dueBillsLinkIsBills() = assertEquals("bills", ask("bills due this week")?.link)

    @Test
    fun whoOwesMe() = assertAnswer("who owes me money", "Rahim owes you \$100")

    @Test
    fun whatIOwe() = assertAnswer("what do i owe", "you owe Karim \$50")

    @Test
    fun goalsOverview() = assertAnswer("how are my goals", "2 active goals", "Laptop: 45%", "Trip: 20%")

    @Test
    fun goalByName() = assertAnswer("how is my laptop goal", "Laptop: 45%", "\$550 to go", "a month")

    @Test
    fun incomeVersusExpense() = assertAnswer("income vs expense this month", "income \$1000", "spending \$315", "kept \$685")

    @Test
    fun incomeLastMonth() = assertAnswer("earnings last month", "income \$1000", "spending \$225")

    @Test
    fun biggestExpense() = assertAnswer("what was my biggest expense", "\$200", "Amazon", "Oct")

    @Test
    fun biggestCategory() = assertAnswer("top categories", "Shopping \$200", "Food \$95")

    @Test
    fun whereMoneyGoes() = assertAnswer("where is my money going", "Top categories", "63%")

    @Test
    fun compareToLastMonth() = assertAnswer("compare to last month", "\$315", "\$200", "57%", "more")

    @Test
    fun compareFood() = assertAnswer("is my food spending higher than last month", "food", "\$95", "\$100")

    @Test
    fun freeTimeToday() = assertAnswer("when am i free today", "12:00-15:00", "16:00-22:00")

    @Test
    fun freeTimeTomorrow() = assertAnswer("free time tomorrow", "08:00-10:00", "11:30-13:00", "14:00-22:00")

    @Test
    fun savingsRate() = assertAnswer("what is my savings rate", "69%", "\$685")

    @Test
    fun runway() = assertAnswer("how long will my money last", "104 days", "\$1185")

    @Test
    fun balance() = assertAnswer("what is my balance", "\$1185")

    @Test
    fun summary() = assertAnswer("how am i doing this month", "income \$1000", "1 of 3 budgets are over")

    @Test
    fun emptySnapshotStillAnswersSpend() {
        val a = RuleAnswers.answer("how much did I spend this month", RuleSnapshot(now), fmt)
        assertEquals("No spending recorded this month.", a?.text)
    }

    @Test
    fun emptySnapshotNoGoals() {
        assertTrue(RuleAnswers.answer("how are my goals", RuleSnapshot(now), fmt)!!.text.contains("no goals"))
    }

    @Test
    fun emptySnapshotNothingDue() {
        assertTrue(RuleAnswers.answer("what is due", RuleSnapshot(now), fmt)!!.text.startsWith("Nothing is due"))
    }

    @Test
    fun emptySnapshotNoNextClass() {
        assertTrue(RuleAnswers.answer("next class", RuleSnapshot(now), fmt)!!.text.contains("no upcoming class"))
    }

    @Test
    fun emptySnapshotFreeAllDay() {
        val a = RuleAnswers.answer("free time today", RuleSnapshot(now), fmt)!!
        assertTrue(a.text, a.text.contains("12:00-22:00"))
    }

    @Test
    fun normalizeStripsPunctuation() {
        assertEquals("whats my balance", RuleAnswers.normalize("What's my balance?!"))
    }

    @Test
    fun categoryDetection() {
        listOf(
            "lunch" to TransactionCategory.FOOD, "uber rides" to TransactionCategory.TRANSPORT,
            "movies" to TransactionCategory.ENTERTAINMENT, "clothes" to TransactionCategory.SHOPPING,
            "medicine" to TransactionCategory.HEALTH, "house rent" to TransactionCategory.RENT,
            "electricity" to TransactionCategory.UTILITIES, "netflix" to TransactionCategory.SUBSCRIPTIONS,
            "tuition" to TransactionCategory.EDUCATION, "bazar" to TransactionCategory.FOOD,
            "rickshaw" to TransactionCategory.TRANSPORT, "nothing here" to null,
        ).forEach { (text, cat) -> assertEquals(text, cat, RuleAnswers.categoryIn(RuleAnswers.normalize(text))) }
    }
}
