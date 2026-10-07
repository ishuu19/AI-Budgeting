package com.ledgerai.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.ledgerai.app.data.ai.AiConfig
import com.ledgerai.app.data.ai.AiProviderRouter
import com.ledgerai.app.data.ai.ContextBuilder
import com.ledgerai.app.data.ai.ForecastItemDto
import com.ledgerai.app.data.ai.ForecastListDto
import com.ledgerai.app.data.ai.NetworkAvailability
import com.ledgerai.app.data.ai.ParsedTransactionDto
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.FinancialHealthScore
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI entry point: cascade via [AiProviderRouter] when online and keys exist (debug BuildConfig).
 * Local fallbacks for health score, budget advice, parse, and forecast.
 * Release BuildConfig AI keys are empty — production should use a Supabase Edge Function.
 */
@Singleton
class AiRepository @Inject constructor(
    private val router: AiProviderRouter,
    private val config: AiConfig,
    private val contextBuilder: ContextBuilder,
    private val network: NetworkAvailability,
    private val gson: Gson,
) {

    suspend fun parseVoiceTransaction(transcript: String): Result<ParsedTransaction> {
        if (canCallCloud()) {
            val system = """
                You extract a single personal finance transaction from user text.
                Reply with ONLY compact JSON (no markdown):
                {"amount":number|null,"category":"FOOD|TRANSPORT|SUBSCRIPTIONS|ENTERTAINMENT|SHOPPING|HEALTH|UTILITIES|RENT|SALARY|OTHER","merchant":string,"note":string,"type":"INCOME|EXPENSE","confidence":0.0-1.0}
            """.trimIndent()
            val cloud = router.complete(system, transcript)
            cloud.getOrNull()?.let { raw ->
                parseTransactionJson(raw, transcript)?.let { return Result.success(it) }
            }
        }
        return Result.success(quickParse(transcript))
    }

    suspend fun generateForecast(
        recentTransactions: List<Transaction>,
        currentBudgets: Map<TransactionCategory, Double>
    ): Result<List<FinancialForecast>> {
        if (recentTransactions.isEmpty()) {
            return Result.failure(IllegalStateException("Need recent transactions to forecast"))
        }

        if (canCallCloud()) {
            val expenseTotal = recentTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            val byCategory = recentTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category.displayName }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            val compact = contextBuilder.buildCompactSummary(
                monthlyExpenses = expenseTotal,
                categoryTotals = byCategory,
                budgetUsage = currentBudgets.map { (cat, limit) ->
                    "${cat.displayName} limit ${"%.0f".format(limit)}"
                },
                recent = recentTransactions.take(5).map {
                    "${it.category.displayName} ${"%.0f".format(it.amount)}"
                },
            )
            val system = """
                You are LedgerAI. Produce a 3-month spending forecast.
                Reply with ONLY JSON (no markdown):
                {"forecasts":[{"month":"Month Year","predicted_spend":number,"recommended_budget":number,"risk_level":"LOW|MEDIUM|HIGH","insight":"short string"}]}
            """.trimIndent()
            router.complete(system, compact).getOrNull()?.let { raw ->
                parseForecastJson(raw)?.let { return Result.success(it) }
            }
        }

        return Result.success(localForecast(recentTransactions, currentBudgets))
    }

    suspend fun calculateHealthScore(
        monthlyIncome: Double,
        monthlyExpenses: Double,
        totalDebt: Double,
        budgetAdherence: Double
    ): Result<FinancialHealthScore> {
        val savingsRate = if (monthlyIncome > 0)
            ((monthlyIncome - monthlyExpenses) / monthlyIncome * 100).toInt().coerceIn(0, 100)
        else 0
        val debtRatio = if (monthlyIncome > 0)
            (100 - (totalDebt / monthlyIncome * 100).toInt()).coerceIn(0, 100)
        else 50
        val adherence = budgetAdherence.toInt().coerceIn(0, 100)
        val score = (savingsRate + debtRatio + adherence) / 3
        return Result.success(
            FinancialHealthScore(
                score = score,
                savingsRate = savingsRate,
                debtRatio = debtRatio,
                budgetAdherence = adherence,
                spendingVolatility = 0,
                summary = when {
                    score >= 80 -> "Strong local score — keep tracking."
                    score >= 50 -> "Decent progress. Watch categories near their limits."
                    else -> "Focus on cutting top expense categories this month."
                }
            )
        )
    }

    suspend fun chat(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>,
        financialContext: String = ""
    ): Result<String> {
        if (!canCallCloud()) {
            return Result.failure(
                IllegalStateException(
                    "Cloud AI unavailable offline or unconfigured. " +
                        "Local tips still work via health score and budget advice."
                )
            )
        }

        val contextBlock = contextBuilder.fromFinancialContextString(financialContext)
        val historyBlock = conversationHistory
            .takeLast(8)
            .joinToString("\n") { (role, content) -> "$role: $content" }

        val system = buildString {
            appendLine("You are LedgerAI, a concise personal finance assistant.")
            appendLine("Be practical, short, and avoid inventing account balances.")
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine(contextBlock)
            }
        }.trim()

        val user = buildString {
            if (historyBlock.isNotBlank()) {
                appendLine("Recent conversation:")
                appendLine(historyBlock)
                appendLine()
            }
            append("User: $userMessage")
        }.trim()

        return router.complete(system, user)
    }

    suspend fun getBudgetAdvice(
        category: String,
        spent: Double,
        limit: Double,
        usagePercent: Int
    ): Result<String> = Result.success(
        when {
            usagePercent >= 100 ->
                "You've hit the $category limit (\$${"%.0f".format(spent)} / \$${"%.0f".format(limit)}). Pause non-essential spend here."
            usagePercent >= 80 ->
                "You're at $usagePercent% of the $category budget. Slow down for the rest of the month."
            else ->
                "You're at $usagePercent% of $category — still on track."
        }
    )

    private fun canCallCloud(): Boolean =
        network.isOnline() && config.hasAnyConfiguredProvider()

    private fun parseTransactionJson(raw: String, fallbackNote: String): ParsedTransaction? {
        return try {
            val json = extractJsonObject(raw) ?: return null
            val dto = gson.fromJson(json, ParsedTransactionDto::class.java) ?: return null
            val category = TransactionCategory.entries.find {
                it.name.equals(dto.category, ignoreCase = true) ||
                    it.displayName.equals(dto.category, ignoreCase = true)
            } ?: TransactionCategory.OTHER
            val type = when (dto.type?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            ParsedTransaction(
                amount = dto.amount,
                category = category,
                merchant = dto.merchant.orEmpty(),
                date = LocalDate.now(),
                note = dto.note?.ifBlank { fallbackNote } ?: fallbackNote,
                type = type,
                confidence = dto.confidence ?: 0.7f,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseForecastJson(raw: String): List<FinancialForecast>? {
        return try {
            val json = extractJsonObject(raw) ?: return null
            val listDto = gson.fromJson(json, ForecastListDto::class.java)
            val items = listDto?.forecasts
            if (items.isNullOrEmpty()) {
                // Allow a bare array
                val arr = JsonParser.parseString(extractJsonArray(raw) ?: return null).asJsonArray
                arr.mapNotNull { el ->
                    gson.fromJson(el, ForecastItemDto::class.java)?.toDomain()
                }.takeIf { it.isNotEmpty() }
            } else {
                items.mapNotNull { it.toDomain() }.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ForecastItemDto.toDomain(): FinancialForecast? {
        val month = month?.takeIf { it.isNotBlank() } ?: return null
        val predicted = predictedSpend ?: return null
        val recommended = recommendedBudget ?: predicted
        val risk = when (riskLevel?.uppercase()) {
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return FinancialForecast(
            month = month,
            predictedSpend = predicted,
            recommendedBudget = recommended,
            riskLevel = risk,
            insight = insight ?: "Cloud forecast",
        )
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return trimmed.substring(start, end + 1)
    }

    private fun extractJsonArray(raw: String): String? {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return trimmed.substring(start, end + 1)
    }

    private fun localForecast(
        recentTransactions: List<Transaction>,
        currentBudgets: Map<TransactionCategory, Double>,
    ): List<FinancialForecast> {
        val monthlySpend = recentTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
            .coerceAtLeast(1.0)
        val budgetTotal = currentBudgets.values.sum().takeIf { it > 0 } ?: monthlySpend
        val now = LocalDate.now()
        return (1..3).map { offset ->
            val month = now.plusMonths(offset.toLong())
            val predicted = monthlySpend * (1.0 + offset * 0.02)
            FinancialForecast(
                month = "${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}",
                predictedSpend = predicted,
                recommendedBudget = budgetTotal,
                riskLevel = when {
                    predicted > budgetTotal * 1.1 -> RiskLevel.HIGH
                    predicted > budgetTotal * 0.9 -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                },
                insight = "Local estimate from recent spending."
            )
        }
    }

    private fun quickParse(input: String): ParsedTransaction {
        val amountRegex = Regex("""[$£€]?\s*(\d+(?:[.,]\d{1,2})?)""")
        val amount = amountRegex.find(input)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()
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

        val merchantCandidates = input.split(" ").filter { w ->
            w.length > 3 && w[0].isUpperCase() && !w.matches(Regex("\\d.*"))
        }
        val merchant = merchantCandidates.lastOrNull() ?: ""

        return ParsedTransaction(
            amount = amount,
            category = category,
            merchant = merchant,
            date = LocalDate.now(),
            note = input,
            type = type
        )
    }
}

private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }
