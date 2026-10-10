package com.ledgerai.app.data.ai

import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.DebtRepository
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** A chat reply and where it came from ([SOURCE_RULES] or [SOURCE_AI]). */
data class AskReply(val text: String, val source: String, val link: String? = null)

/** Reads the local data the rule answerer needs. */
@Singleton
class RuleSnapshotLoader @Inject constructor(
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val bills: BillRepository,
    private val debts: DebtRepository,
    private val goals: GoalRepository,
    private val calendar: CalendarRepository,
    private val prefs: UserPreferences,
) {
    suspend fun load(now: LocalDateTime = LocalDateTime.now()): RuleSnapshot {
        val today = now.toLocalDate()
        return RuleSnapshot(
            now = now,
            transactions = transactions.getAllTransactions().first(),
            budgets = budgets.getBudgetsForMonth(today.monthValue, today.year).first(),
            bills = bills.getActiveBills().first(),
            debts = debts.getActiveDebts().first(),
            goals = goals.getActiveGoals().first(),
            events = runCatching { calendar.listNextDays(14) }.getOrDefault(emptyList()),
            cash = runCatching { prefs.cashOnHand.first().toDoubleOrNull() }.getOrNull(),
        )
    }
}

/** Chat entry point: rules first, the cloud only when no rule can answer. */
@Singleton
class AskService @Inject constructor(
    private val loader: RuleSnapshotLoader,
    private val ai: AiRepository,
) {
    suspend fun ask(message: String, history: List<Pair<String, String>>, context: String): Result<AskReply> {
        val snapshot = runCatching { loader.load() }.getOrNull()
        if (snapshot != null) {
            val rule = runCatching { RuleAnswers.answer(message, snapshot) }.getOrNull()
            if (rule != null) return Result.success(AskReply(rule.text, SOURCE_RULES, rule.link))
        }
        return ai.chat(message, history, context).map { AskReply(it, SOURCE_AI) }
    }
}
