package com.ledgerai.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates compact on-device summaries for AI prompts.
 * Stub only — no DB reads/writes; callers pass already-fetched summaries.
 */
@Singleton
class ContextBuilder @Inject constructor() {

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

    companion object {
        private const val MAX_CHARS = 1500
    }
}
