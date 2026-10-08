package com.ledgerai.app.data.ai

import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.domain.model.CalendarEvent
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates compact on-device summaries for AI prompts from Room repositories.
 * Raw note bodies are never included unless a caller passes them explicitly.
 */
@Singleton
class ContextBuilder @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val calendarRepo: CalendarRepository,
    private val billRepo: BillRepository,
    private val planRepo: PlanRepository,
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun buildFromRoom(now: LocalDate = LocalDate.now()): String {
        val year = now.year
        val month = now.monthValue
        val monthLabel = "${now.month.getDisplayName(TextStyle.FULL, Locale.US)} $year"

        val income = transactionRepo.getTotalIncomeForMonth(year, month)
        val expenses = transactionRepo.getTotalExpensesForMonth(year, month)
        val recent = transactionRepo.getRecentTransactions(8).first()
        val budgets = budgetRepo.getBudgetsForMonth(month, year).first()
        val events = calendarRepo.listNextDays(7).filter { it.isOpen() }.take(8)
        val bills = billRepo.getActiveBills().first()
            .sortedBy { it.nextDueDate }
            .take(5)

        val categoryTotals = recent
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category.displayName }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .toMutableMap()

        // Prefer month category spend when available from recent mix + budgets
        for (budget in budgets) {
            val spent = transactionRepo.getSpendingForCategoryMonth(budget.category, year, month)
            if (spent > 0) categoryTotals[budget.category.displayName] = spent
        }

        val topMerchants = recent
            .map { it.merchant.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val budgetUsage = budgets.map { b ->
            val spent = transactionRepo.getSpendingForCategoryMonth(b.category, year, month)
            val pct = if (b.monthlyLimit > 0) ((spent / b.monthlyLimit) * 100).toInt() else 0
            "${b.category.displayName} $pct% (${"%.0f".format(spent)}/${"%.0f".format(b.monthlyLimit)})"
        }

        val upcoming = buildList {
            events.forEach { e ->
                add("${e.kind.name.lowercase()}: ${e.title} (${fmt.format(e.startAt)})")
            }
            bills.forEach { b ->
                add("bill: ${b.name} ${"%.0f".format(b.amount)} due ${b.nextDueDate}")
            }
        }

        val recentLines = recent.take(5).map { tx ->
            val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
            "$sign${"%.0f".format(tx.amount)} ${tx.category.displayName}" +
                (if (tx.merchant.isNotBlank()) " @ ${tx.merchant}" else "")
        }

        return buildCompactSummary(
            monthLabel = monthLabel,
            monthlyIncome = income,
            monthlyExpenses = expenses,
            categoryTotals = categoryTotals,
            topMerchants = topMerchants,
            budgetUsage = budgetUsage,
            upcoming = upcoming,
            recent = recentLines,
        )
    }

    /** Calendar events (all kinds) and plan blocks for the next 14 days, one line each. */
    suspend fun build14DaySlice(now: LocalDate = LocalDate.now()): String {
        val events = calendarRepo.listNextDays(14).filter { it.isOpen() }
        val blocks = planRepo.observeBlocks().first()
        val lines = buildList {
            add("Schedule next 14 days from $now:")
            events.forEach { e ->
                add("${e.kind.name.lowercase()}|${fmt.format(e.startAt)}|${e.title}")
            }
            blocks.forEach { b ->
                add("plan|${fmt.format(b.startAt)}|${b.title}|${b.kind}")
            }
        }
        return lines.joinToString("\n")
    }

    private fun CalendarEvent.isOpen(): Boolean =
        isEnabled && !(isCompleted && kind == CalendarEventKind.TASK)

    fun buildCompactSummary(
        monthLabel: String = "",
        monthlyIncome: Double = 0.0,
        monthlyExpenses: Double = 0.0,
        categoryTotals: Map<String, Double> = emptyMap(),
        topMerchants: List<String> = emptyList(),
        budgetUsage: List<String> = emptyList(),
        upcoming: List<String> = emptyList(),
        recent: List<String> = emptyList(),
    ): String = Companion.buildCompactSummary(
        monthLabel = monthLabel,
        monthlyIncome = monthlyIncome,
        monthlyExpenses = monthlyExpenses,
        categoryTotals = categoryTotals,
        topMerchants = topMerchants,
        budgetUsage = budgetUsage,
        upcoming = upcoming,
        recent = recent,
    )

    fun fromFinancialContextString(financialContext: String): String =
        Companion.fromFinancialContextString(financialContext)

    companion object {
        private const val MAX_CHARS = 1500

        /** Pure formatter for unit tests (no Room). */
        fun buildCompactSummary(
            monthLabel: String = "",
            monthlyIncome: Double = 0.0,
            monthlyExpenses: Double = 0.0,
            categoryTotals: Map<String, Double> = emptyMap(),
            topMerchants: List<String> = emptyList(),
            budgetUsage: List<String> = emptyList(),
            upcoming: List<String> = emptyList(),
            recent: List<String> = emptyList(),
        ): String {
            val net = monthlyIncome - monthlyExpenses
            val categories = categoryTotals.entries
                .sortedByDescending { it.value }
                .take(8)
                .joinToString("; ") { "${it.key}: ${"%.0f".format(it.value)}" }
                .ifBlank { "none" }

            return buildString {
                appendLine("LedgerAI financial context (compact, on-device aggregate).")
                if (monthLabel.isNotBlank()) appendLine("Period: $monthLabel")
                appendLine("Income: ${"%.2f".format(monthlyIncome)}")
                appendLine("Expenses: ${"%.2f".format(monthlyExpenses)}")
                appendLine("Net: ${"%.2f".format(net)}")
                appendLine("By category: $categories")
                if (topMerchants.isNotEmpty()) {
                    appendLine("Top merchants: ${topMerchants.take(5).joinToString(", ")}")
                }
                if (budgetUsage.isNotEmpty()) {
                    appendLine("Budget usage: ${budgetUsage.take(6).joinToString("; ")}")
                }
                if (upcoming.isNotEmpty()) {
                    appendLine("Upcoming: ${upcoming.take(5).joinToString("; ")}")
                }
                if (recent.isNotEmpty()) {
                    appendLine("Recent: ${recent.take(5).joinToString("; ")}")
                }
            }.trim().take(MAX_CHARS)
        }

        fun fromFinancialContextString(financialContext: String): String {
            val trimmed = financialContext.trim()
            if (trimmed.isEmpty()) return ""
            return "LedgerAI financial context:\n$trimmed".take(MAX_CHARS)
        }
    }
}
