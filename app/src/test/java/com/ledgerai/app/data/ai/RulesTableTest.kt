package com.ledgerai.app.data.ai

import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionCategory.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Table-driven utterance cases for the rule engine. Today is Thursday 2026-10-08, 10:00. */
class RulesTableTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val now = LocalDateTime.of(today, LocalTime.of(10, 0))

    private class Case(
        val text: String,
        val kind: String,
        val amount: Double? = null,
        val category: TransactionCategory? = null,
        val title: String? = null,
        val hour: Int? = null,
        val minute: Int? = null,
        val date: LocalDate? = null,
        val dir: DebtDirection? = null,
        val name: String? = null,
    )

    private fun check(c: Case): String? {
        val items = QuickParse.parseVoiceIntents(c.text, today, now)
        val first = items.first()
        val kind = first.resultKind().name
        if (kind != c.kind) return "kind $kind != ${c.kind}"
        c.amount?.let { a ->
            val got = when (first) {
                is ParsedIntent.Transaction -> first.amount
                is ParsedIntent.Bill -> first.amount
                is ParsedIntent.Debt -> first.amount
                is ParsedIntent.Goal -> first.targetAmount
                is ParsedIntent.Budget -> first.limit
                else -> null
            }
            if (got == null || Math.abs(got - a) > 0.001) return "amount $got != $a"
        }
        c.category?.let { cat ->
            val got = when (first) {
                is ParsedIntent.Transaction -> first.category
                is ParsedIntent.Budget -> first.category
                is ParsedIntent.Bill -> first.category
                else -> null
            }
            if (got != cat) return "category $got != $cat"
        }
        if (first is ParsedIntent.Event) {
            c.title?.let { if (!first.title.contains(it, true)) return "title '${first.title}' lacks '$it'" }
            c.hour?.let { if (first.startAt.hour != it) return "hour ${first.startAt.hour} != $it" }
            c.minute?.let { if (first.startAt.minute != it) return "minute ${first.startAt.minute} != $it" }
            c.date?.let { if (first.startAt.toLocalDate() != it) return "date ${first.startAt.toLocalDate()} != $it" }
        }
        c.dir?.let { if ((first as? ParsedIntent.Debt)?.direction != it) return "direction" }
        c.name?.let {
            val got = when (first) {
                is ParsedIntent.Debt -> first.friendName
                is ParsedIntent.Goal -> first.name
                is ParsedIntent.Bill -> first.name
                is ParsedIntent.Job -> first.company
                is ParsedIntent.Transaction -> first.merchant
                is ParsedIntent.Note -> first.title
                else -> ""
            }
            if (!got.contains(it, true)) return "name '$got' lacks '$it'"
        }
        return null
    }

    private val cases: List<Case> by lazy { buildCases() }

    private fun buildCases(): List<Case> {
        val out = ArrayList<Case>()
        val amounts = listOf("5" to 5.0, "12.50" to 12.5, "40" to 40.0, "120" to 120.0, "1500" to 1500.0)
        val merchants = listOf(
            "Starbucks" to FOOD, "McDonald's" to FOOD, "Uber" to TRANSPORT, "Netflix" to SUBSCRIPTIONS,
            "Amazon" to SHOPPING, "Walmart" to SHOPPING, "Spotify" to SUBSCRIPTIONS, "Subway" to FOOD,
        )
        for ((m, cat) in merchants) for ((a, v) in amounts) {
            out += Case("spent $a at $m", "Spend", amount = v, category = cat)
            out += Case("paid $a dollars to $m", "Spend", amount = v, category = cat)
            out += Case("$m $a", "Spend", amount = v, category = cat)
        }
        val keywords = listOf(
            "lunch" to FOOD, "dinner" to FOOD, "coffee" to FOOD, "groceries" to FOOD, "taxi" to TRANSPORT, "bus fare" to TRANSPORT,
            "movie" to ENTERTAINMENT, "gym" to HEALTH, "doctor" to HEALTH, "rent" to RENT, "electricity" to UTILITIES, "tuition" to EDUCATION,
        )
        for ((k, cat) in keywords) for ((a, v) in amounts) out += Case("spent $a on $k", "Spend", amount = v, category = cat)
        val words = listOf(
            "spent twenty five on lunch" to 25.0, "spent a hundred and fifty on groceries" to 150.0,
            "paid two grand for rent" to 2000.0, "spent one point five k on shoes" to 1500.0, "spent fifty bucks on dinner" to 50.0,
            "spent 2k on a phone" to 2000.0, "spent 1.5k on shoes" to 1500.0, "paid forty dollars for gas" to 40.0,
            "spent three hundred on clothes" to 300.0, "spent two thousand five hundred on rent" to 2500.0,
            "paid thirty taka for rickshaw" to 30.0, "spent five lakh on a car" to 500000.0, "spent 1,250 on rent" to 1250.0,
            "spent ninety nine on a game" to 99.0, "bought shoes for sixty" to 60.0, "spent seventy five cents on candy" to 0.75,
            "paid eleven dollars for lunch" to 11.0, "spent twelve on coffee" to 12.0,
            "spent 3 hundred on food" to 300.0, "paid 15 bucks for a movie" to 15.0,
        )
        for ((t, v) in words) out += Case(t, "Spend", amount = v)
        for ((a, v) in amounts) {
            out += Case("got paid $a", "Income", amount = v)
            out += Case("received $a from a client", "Income", amount = v)
            out += Case("refund of $a from amazon", "Income", amount = v)
            out += Case("salary $a", "Income", amount = v, category = SALARY)
            out += Case("earned $a freelancing", "Income", amount = v)
            out += Case("sold my old bike for $a", "Income", amount = v)
        }
        for (h in 1..12) for (ap in listOf("am", "pm")) {
            val h24 = if (ap == "pm") (if (h == 12) 12 else h + 12) else (if (h == 12) 0 else h)
            out += Case("set an alarm for $h $ap", "Alarm", hour = h24, minute = 0)
            out += Case("wake me up at $h:30 $ap", "Alarm", hour = h24, minute = 30)
        }
        out += Case("alarm at half past six pm", "Alarm", hour = 18, minute = 30)
        out += Case("alarm at quarter to seven pm", "Alarm", hour = 18, minute = 45)
        out += Case("alarm at quarter past five pm", "Alarm", hour = 17, minute = 15)
        out += Case("alarm at noon tomorrow", "Alarm", hour = 12, minute = 0)
        out += Case("alarm tomorrow at midnight", "Alarm", hour = 0, minute = 0)
        out += Case("alarm at five thirty pm", "Alarm", hour = 17, minute = 30)
        out += Case("alarm weekdays at 6 am", "Alarm", hour = 6)
        out += Case("alarm every day at 5:45 am", "Alarm", hour = 5, minute = 45)
        val tasks = listOf("call mom", "pay rent", "buy milk", "take pills", "submit the report", "water the plants", "book a flight", "email the landlord")
        for (t in tasks) for (h in listOf(9, 12, 3, 6)) {
            val h24 = if (h == 9) 9 else if (h == 12) 12 else h + 12
            val ap = if (h == 9) "am" else "pm"
            out += Case("remind me to $t tomorrow at $h $ap", "Reminder", title = t, hour = h24, date = today.plusDays(1))
        }
        val dates = listOf(
            "tomorrow" to today.plusDays(1), "day after tomorrow" to today.plusDays(2), "next friday" to LocalDate.of(2026, 10, 16),
            "in two weeks" to today.plusWeeks(2), "in 3 days" to today.plusDays(3), "at the end of the month" to LocalDate.of(2026, 10, 31),
            "this weekend" to LocalDate.of(2026, 10, 10), "next monday" to LocalDate.of(2026, 10, 12), "on the 20th" to LocalDate.of(2026, 10, 20),
            "on friday" to LocalDate.of(2026, 10, 9), "next week" to today.plusWeeks(1), "in a week" to today.plusWeeks(1),
            "on october 25" to LocalDate.of(2026, 10, 25), "in 2 months" to LocalDate.of(2026, 12, 8), "next month" to LocalDate.of(2026, 11, 8),
            "on 15 november" to LocalDate.of(2026, 11, 15),
        )
        for ((p, d) in dates) {
            out += Case("remind me to call the bank $p at 4 pm", "Reminder", title = "call the bank", hour = 16, date = d)
            out += Case("add task renew passport $p", "Task", title = "renew passport", date = d)
            out += Case("meeting with the team $p at 2 pm", "Event", hour = 14, date = d)
        }
        val people = listOf("Sarah", "David", "the dentist", "my advisor")
        for (p in people) for (h in listOf(10, 3)) {
            val h24 = if (h == 10) 10 else 15
            val ap = if (h == 10) "am" else "pm"
            out += Case("meeting with $p tomorrow at $h $ap", "Event", hour = h24, date = today.plusDays(1))
            out += Case("lunch with $p tomorrow at $h $ap", "Event", hour = h24, date = today.plusDays(1))
        }
        for (s in listOf("math", "physics", "chemistry", "history", "biology")) {
            out += Case("$s exam on october 20 at 2 pm", "Exam", hour = 14, date = LocalDate.of(2026, 10, 20))
            out += Case("I have a $s test tomorrow at 9 am", "Exam", hour = 9, date = today.plusDays(1))
        }
        out += Case("dentist appointment friday at 11 am", "Event", hour = 11, date = LocalDate.of(2026, 10, 9))
        out += Case("book a meeting from 3 to 5 pm tomorrow", "Event", hour = 15, date = today.plusDays(1))
        for (r in listOf("meditate", "stretch", "read", "go for a run", "journal")) {
            out += Case("daily routine to $r", "Routine")
            out += Case("every morning $r", "Routine")
            out += Case("weekly routine to $r", "Routine")
            out += Case("add a habit to $r every weekday", "Routine")
        }
        for (n in listOf("buy a gift for dad", "call the plumber about the leak", "a split bill tracker app", "the wifi password is on the fridge", "ask the boss about leave")) {
            out += Case("note $n", "Note")
            out += Case("write a note $n", "Note")
            out += Case("remember that $n", "Note")
            out += Case("idea $n", "Note")
            out += Case("note to self $n", "Note")
        }
        out += Case("remember to buy eggs", "Note")
        val bills = listOf("electricity" to UTILITIES, "internet" to UTILITIES, "netflix" to SUBSCRIPTIONS, "rent" to RENT, "water" to UTILITIES, "spotify" to SUBSCRIPTIONS)
        for ((b, cat) in bills) for ((a, v) in amounts.take(3)) {
            out += Case("$b bill $a due tomorrow", "Bill", amount = v, category = cat)
            out += Case("$b $a monthly bill", "Bill", amount = v, category = cat)
        }
        out += Case("yearly domain bill 120", "Bill", amount = 120.0)
        out += Case("insurance bill 300 due on the 20th", "Bill", amount = 300.0)
        out += Case("gym membership 40 every month", "Bill", amount = 40.0)
        val names = listOf("Alex", "Maya", "Tom", "Priya", "Jordan")
        for (n in names) for ((a, v) in amounts.take(3)) {
            out += Case("I owe $n $a", "Debt", amount = v, dir = DebtDirection.I_OWE, name = n)
            out += Case("$n owes me $a", "Debt", amount = v, dir = DebtDirection.THEY_OWE, name = n)
            out += Case("I lent $n $a", "Debt", amount = v, dir = DebtDirection.THEY_OWE, name = n)
            out += Case("I borrowed $a from $n", "Debt", amount = v, dir = DebtDirection.I_OWE, name = n)
        }
        for (g in listOf("vacation", "a new laptop", "a car", "wedding", "emergency fund")) for ((a, v) in amounts.take(3)) {
            out += Case("save $a for $g", "Goal", amount = v)
        }
        out += Case("savings goal 5000 for a house", "Goal", amount = 5000.0)
        out += Case("I want to save 2000 for a bike", "Goal", amount = 2000.0)
        val budgetCats = listOf("food" to FOOD, "transport" to TRANSPORT, "shopping" to SHOPPING, "entertainment" to ENTERTAINMENT, "health" to HEALTH)
        for ((c, cat) in budgetCats) for ((a, v) in amounts.take(3)) {
            out += Case("set a $c budget of $a", "Budget", amount = v, category = cat)
            out += Case("$c budget $a", "Budget", amount = v, category = cat)
        }
        out += Case("do not spend more than 300 on food", "Budget", amount = 300.0, category = FOOD)
        val companies = listOf("Google", "Stripe", "Shopify", "Spotify", "Canva", "Meta", "Uber", "Amazon")
        for (co in companies) {
            out += Case("applied to $co for Android engineer", "Job", name = co)
            out += Case("interview at $co on friday at 3 pm", "Job", name = co)
            out += Case("got an offer from $co", "Job", name = co)
        }
        for (x in listOf("gym alarm", "dentist meeting", "lunch expense", "stripe job", "math exam")) {
            out += Case("delete the $x", "Edit")
            out += Case("cancel my $x", "Edit")
            out += Case("please remove the $x", "Edit")
        }
        out += Case("change the gym alarm to 7 am", "Edit")
        out += Case("reschedule the dentist meeting to friday", "Edit")
        out += Case("rename the stripe job to stripe android", "Edit")
        out += Case("৫০০ টাকা খরচ করেছি", "Spend", amount = 500.0)
        out += Case("আমি ২০০ টাকা খরচ করেছি লাঞ্চে", "Spend", amount = 200.0)
        out += Case("অ্যালার্ম সকাল ৭টা", "Alarm", hour = 7)
        out += Case("অ্যালার্ম রাত ১০টা", "Alarm", hour = 22)
        out += Case("মনে করিয়ে দিও আগামীকাল বিকাল ৫টা", "Reminder", hour = 17, date = today.plusDays(1))
        out += Case("বেতন ৫০০০০ টাকা পেয়েছি", "Income", amount = 50000.0)
        out += Case("দুই হাজার টাকা খরচ", "Spend", amount = 2000.0)
        out += Case("ajke 300 taka khoroch korechi", "Spend", amount = 300.0)
        out += Case("meeting agamikal 3 pm", "Event", hour = 15, date = today.plusDays(1))
        return out
    }

    @Test
    fun table() {
        val failures = cases.mapNotNull { c -> runCatching { check(c) }.getOrElse { "threw $it" }?.let { "${c.text} -> $it" } }
        assertTrue("${failures.size}/${cases.size} failed:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun hasAtLeast600Cases() {
        assertTrue("only ${cases.size} cases", cases.size >= 600)
    }
}
