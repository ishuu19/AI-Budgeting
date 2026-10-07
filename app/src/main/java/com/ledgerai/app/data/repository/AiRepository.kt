package com.ledgerai.app.data.repository

import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.remote.OpenRouterService
import com.ledgerai.app.data.remote.model.OpenRouterRequest
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.FinancialHealthScore
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val service: OpenRouterService,
    private val preferences: UserPreferences,
    private val gson: Gson
) {
    private suspend fun getModel(): String = preferences.aiModel.first()

    // ─── Voice / Text Transaction Parsing ────────────────────────────────────

    suspend fun parseVoiceTransaction(transcript: String): Result<ParsedTransaction> = runCatching {
        val prompt = """
            Parse this voice/text input into a structured transaction.
            Input: "$transcript"
            
            Categories available: ${TransactionCategory.entries.map { it.displayName }.joinToString(", ")}
            
            Respond with ONLY valid JSON in this exact format:
            {
              "amount": 12.50,
              "category": "Food",
              "merchant": "McDonald's",
              "date": "today",
              "note": "Lunch",
              "type": "expense"
            }
            
            Rules:
            - amount must be a number (no currency symbols)
            - category must match one from the list above
            - date: use "today", "yesterday", or ISO-8601 (YYYY-MM-DD)
            - type: "expense" or "income"
        """.trimIndent()

        val response = service.chatCompletion(
            OpenRouterRequest(
                model = getModel(),
                messages = listOf(
                    OpenRouterRequest.Message("system", "You are a financial transaction parser. Always respond with valid JSON only."),
                    OpenRouterRequest.Message("user", prompt)
                ),
                maxTokens = 256
            )
        )

        val content = response.getContent() ?: throw Exception("Empty AI response")
        val json = gson.fromJson(extractJson(content), JsonObject::class.java)

        ParsedTransaction(
            amount = json.get("amount")?.asDouble,
            category = TransactionCategory.fromDisplayName(json.get("category")?.asString ?: ""),
            merchant = json.get("merchant")?.asString ?: "",
            date = parseDateString(json.get("date")?.asString ?: "today"),
            note = json.get("note")?.asString ?: "",
            type = if (json.get("type")?.asString?.lowercase() == "income")
                TransactionType.INCOME else TransactionType.EXPENSE
        )
    }

    // ─── Spending Forecast ────────────────────────────────────────────────────

    suspend fun generateForecast(
        recentTransactions: List<Transaction>,
        currentBudgets: Map<TransactionCategory, Double>
    ): Result<List<FinancialForecast>> = runCatching {
        val summary = buildTransactionSummary(recentTransactions)
        val budgetSummary = currentBudgets.entries.joinToString("\n") {
            "- ${it.key.displayName}: \$${it.value} budget"
        }

        val prompt = """
            Analyze this user's recent spending and generate a 3-month financial forecast.
            
            Recent spending summary (last 3 months):
            $summary
            
            Current monthly budgets:
            $budgetSummary
            
            Respond with ONLY a JSON array of 3 forecast objects:
            [
              {
                "month": "April 2025",
                "predictedSpend": 1850.00,
                "recommendedBudget": 1700.00,
                "riskLevel": "MEDIUM",
                "insight": "Your food spending tends to increase 15% in spring."
              }
            ]
            riskLevel must be: LOW, MEDIUM, or HIGH
        """.trimIndent()

        val response = service.chatCompletion(
            OpenRouterRequest(
                model = getModel(),
                messages = listOf(
                    OpenRouterRequest.Message("system", "You are a financial forecasting AI. Respond with valid JSON only."),
                    OpenRouterRequest.Message("user", prompt)
                ),
                maxTokens = 512
            )
        )

        val content = response.getContent() ?: throw Exception("Empty AI response")
        val jsonArray = gson.fromJson(extractJson(content), com.google.gson.JsonArray::class.java)

        jsonArray.map { element ->
            val obj = element.asJsonObject
            FinancialForecast(
                month = obj.get("month")?.asString ?: "",
                predictedSpend = obj.get("predictedSpend")?.asDouble ?: 0.0,
                recommendedBudget = obj.get("recommendedBudget")?.asDouble ?: 0.0,
                riskLevel = try { RiskLevel.valueOf(obj.get("riskLevel")?.asString ?: "MEDIUM") } catch (e: Exception) { RiskLevel.MEDIUM },
                insight = obj.get("insight")?.asString ?: ""
            )
        }
    }

    // ─── Financial Health Score ───────────────────────────────────────────────

    suspend fun calculateHealthScore(
        monthlyIncome: Double,
        monthlyExpenses: Double,
        totalDebt: Double,
        budgetAdherence: Double
    ): Result<FinancialHealthScore> = runCatching {
        val savingsRate = if (monthlyIncome > 0)
            ((monthlyIncome - monthlyExpenses) / monthlyIncome * 100).toInt().coerceIn(0, 100)
        else 0

        val debtRatio = if (monthlyIncome > 0)
            (100 - (totalDebt / monthlyIncome * 100).toInt()).coerceIn(0, 100)
        else 50

        val adherence = budgetAdherence.toInt().coerceIn(0, 100)

        val prompt = """
            Calculate a financial health score (0-100) and provide a brief summary.
            
            Metrics:
            - Monthly income: $${monthlyIncome}
            - Monthly expenses: $${monthlyExpenses}
            - Savings rate: ${savingsRate}%
            - Debt ratio score: ${debtRatio}/100
            - Budget adherence: ${adherence}%
            
            Respond with ONLY JSON:
            {
              "score": 72,
              "summary": "You're making good progress! Focus on reducing entertainment spending."
            }
        """.trimIndent()

        val response = service.chatCompletion(
            OpenRouterRequest(
                model = getModel(),
                messages = listOf(
                    OpenRouterRequest.Message("system", "You are a personal finance advisor. Respond with valid JSON only."),
                    OpenRouterRequest.Message("user", prompt)
                ),
                maxTokens = 256
            )
        )

        val content = response.getContent() ?: throw Exception("Empty AI response")
        val json = gson.fromJson(extractJson(content), JsonObject::class.java)
        val score = json.get("score")?.asInt ?: ((savingsRate + debtRatio + adherence) / 3)

        FinancialHealthScore(
            score = score.coerceIn(0, 100),
            savingsRate = savingsRate,
            debtRatio = debtRatio,
            budgetAdherence = adherence,
            spendingVolatility = 0,
            summary = json.get("summary")?.asString ?: "Keep tracking your finances!"
        )
    }

    // ─── AI Chat ──────────────────────────────────────────────────────────────

    suspend fun chat(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>,
        financialContext: String = ""
    ): Result<String> = runCatching {
        val systemPrompt = """
            You are BudgetAI, a friendly and knowledgeable personal finance assistant.
            You help users track spending, save money, and make smart financial decisions.
            Keep responses concise (2-4 sentences max unless detail is requested).
            Always be encouraging and practical.
            ${if (financialContext.isNotEmpty()) "\nUser's financial context:\n$financialContext" else ""}
        """.trimIndent()

        val messages = mutableListOf(
            OpenRouterRequest.Message("system", systemPrompt)
        )
        conversationHistory.forEach { (role, content) ->
            messages.add(OpenRouterRequest.Message(role, content))
        }
        messages.add(OpenRouterRequest.Message("user", userMessage))

        val response = service.chatCompletion(
            OpenRouterRequest(
                model = getModel(),
                messages = messages,
                maxTokens = 512,
                temperature = 0.7
            )
        )

        response.getContent() ?: "Sorry, I couldn't generate a response. Please try again."
    }

    // ─── Budget Advice ────────────────────────────────────────────────────────

    suspend fun getBudgetAdvice(
        category: String,
        spent: Double,
        limit: Double,
        usagePercent: Int
    ): Result<String> = runCatching {
        val prompt = "The user has spent \$$spent of their \$$limit ${category} budget (${usagePercent}%). Give one short, actionable tip in 1-2 sentences."

        val response = service.chatCompletion(
            OpenRouterRequest(
                model = getModel(),
                messages = listOf(
                    OpenRouterRequest.Message("system", "You are a concise financial advisor."),
                    OpenRouterRequest.Message("user", prompt)
                ),
                maxTokens = 128
            )
        )
        response.getContent() ?: "Try to reduce spending in this category for the rest of the month."
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun extractJson(text: String): String {
        val jsonStart = text.indexOfFirst { it == '{' || it == '[' }
        val jsonEnd = text.indexOfLast { it == '}' || it == ']' }
        return if (jsonStart >= 0 && jsonEnd > jsonStart) text.substring(jsonStart, jsonEnd + 1) else text
    }

    private fun parseDateString(dateStr: String): LocalDate = when (dateStr.lowercase()) {
        "today" -> LocalDate.now()
        "yesterday" -> LocalDate.now().minusDays(1)
        else -> try { LocalDate.parse(dateStr) } catch (e: Exception) { LocalDate.now() }
    }

    private fun buildTransactionSummary(transactions: List<Transaction>): String {
        val byCategory = transactions.groupBy { it.category }
        return byCategory.entries.joinToString("\n") { (cat, txns) ->
            val total = txns.sumOf { it.amount }
            "- ${cat.displayName}: \$${"%.2f".format(total)} (${txns.size} transactions)"
        }
    }
}
