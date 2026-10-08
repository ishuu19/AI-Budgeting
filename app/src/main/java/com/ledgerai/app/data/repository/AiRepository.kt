package com.ledgerai.app.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.ledgerai.app.data.ai.AiConfig
import com.ledgerai.app.data.ai.AiEdgeClient
import com.ledgerai.app.data.ai.AiProviderRouter
import com.ledgerai.app.data.ai.AiResponseType
import com.ledgerai.app.data.ai.AiResponseValidator
import com.ledgerai.app.data.ai.ContextBuilder
import com.ledgerai.app.data.ai.ForecastItemDto
import com.ledgerai.app.data.ai.ForecastListDto
import com.ledgerai.app.data.ai.InsightDto
import com.ledgerai.app.data.ai.InsightStore
import com.ledgerai.app.data.ai.NetworkAvailability
import com.ledgerai.app.data.ai.NoteNudgeProposalDto
import com.ledgerai.app.data.ai.NoteNudgeScanDto
import com.ledgerai.app.data.ai.NoteSummaryDto
import com.ledgerai.app.data.ai.ScheduleContextBuilder
import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.data.ai.ScheduleDraftListDto
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.ai.ParsedTransactionDto
import com.ledgerai.app.data.ai.ParsedVoiceIntentDto
import com.ledgerai.app.data.ai.QuickParse
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ledgerai.app.data.schedule.ParsedScheduleRow
import com.ledgerai.app.data.schedule.ScheduleTimetableJson
import com.ledgerai.app.data.schedule.ScheduleTimeParser
import java.io.ByteArrayOutputStream
import java.util.Base64
import com.ledgerai.app.data.ai.ValidatedAiResponse
import com.ledgerai.app.domain.model.BillFrequency
import com.ledgerai.app.domain.model.FinancialForecast
import com.ledgerai.app.domain.model.FinancialHealthScore
import com.ledgerai.app.domain.model.ParsedTransaction
import com.ledgerai.app.domain.model.RiskLevel
import com.ledgerai.app.domain.model.Transaction
import com.ledgerai.app.domain.model.TransactionCategory
import com.ledgerai.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI entry point.
 * Production: [AiEdgeClient] → Supabase Edge Function when SUPABASE_URL + JWT.
 * Interim: [AiProviderRouter] cascade (debug BuildConfig keys).
 * All structured replies pass [AiResponseValidator] before use.
 */
@Singleton
class AiRepository @Inject constructor(
    private val edgeClient: AiEdgeClient,
    private val router: AiProviderRouter,
    private val config: AiConfig,
    private val contextBuilder: ContextBuilder,
    private val scheduleContextBuilder: ScheduleContextBuilder,
    private val validator: AiResponseValidator,
    private val insightStore: InsightStore,
    private val network: NetworkAvailability,
    private val gson: Gson,
) {

    suspend fun parseVoiceTransaction(transcript: String): Result<ParsedTransaction> {
        if (canCallCloud()) {
            val system = """
                You extract a single personal finance transaction from user text.
                Put the shop or person name in merchant. Never put the whole sentence in note.
                If a name is said (at Sarah's, from John, Starbucks), merchant is that name.
                note is only extra detail, or empty.
                Reply with ONLY compact JSON (no markdown):
                {"amount":number|null,"category":"FOOD|TRANSPORT|SUBSCRIPTIONS|ENTERTAINMENT|SHOPPING|HEALTH|UTILITIES|RENT|SALARY|OTHER","merchant":string,"note":string,"type":"INCOME|EXPENSE","confidence":0.0-1.0}
            """.trimIndent()
            completeStructured(AiResponseType.TRANSACTION, system, transcript).getOrNull()?.let { validated ->
                val dto = (validated as? ValidatedAiResponse.Transaction)?.dto
                dto?.let { parseTransactionDto(it, transcript)?.let { tx -> return Result.success(tx) } }
            }
        }
        return Result.success(quickParse(transcript))
    }

    /**
     * Multi-intent voice router: TRANSACTION | TASK | REMINDER | ALARM | NOTE | ROUTINE | BILL | GOAL.
     * DEBT falls through to QuickParse offline when the cloud DTO has no direction field.
     * Uses structured Edge types when possible; free-form JSON + local heuristics otherwise.
     */
    suspend fun parseVoiceIntent(transcript: String): Result<ParsedIntent> {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty transcript"))
        }

        if (network.isOnline() && edgeClient.isConfigured()) {
            edgeClient.voiceIntent(nowHint() + "\nUser said: " + trimmed).getOrNull()?.let { raw ->
                mapVoiceIntentJson(raw, trimmed)?.let { return Result.success(it) }
            }
        }

        if (canCallCloud()) {
            val system = """
                Classify the user utterance into one intent and extract fields.
                If money was spent or received, intent is TRANSACTION, not NOTE.
                merchant and title must be the specific person or place name when one is said. Do not copy the whole sentence into note or body.
                Reply with ONLY JSON:
                {"intent":"TRANSACTION|TASK|REMINDER|ALARM|NOTE|ROUTINE","amount":number|null,"category":"FOOD|...","merchant":string,"note":string,"type":"INCOME|EXPENSE","confidence":0-1,"title":string,"body":string,"due_at":"ISO-8601|null","remind_at":"ISO-8601|null","label":string,"time":"HH:mm","tags":["..."],"repeat_rule":"DAILY|WEEKLY|WEEKDAYS|CUSTOM","repeat_days":0}
                For ALARM, repeat_days is a weekday bitmask Sun=1,Mon=2,Tue=4,Wed=8,Thu=16,Fri=32,Sat=64 (0=one-shot; weekdays=62; every day=127).
                For ROUTINE, set title and repeat_rule.
            """.trimIndent()
            completeRaw(system, trimmed).getOrNull()?.let { raw ->
                mapVoiceIntentJson(raw, trimmed)?.let { return Result.success(it) }
            }

            // Prefer transaction schema when utterance looks financial
            completeStructured(
                AiResponseType.TRANSACTION,
                "Extract a transaction as JSON.",
                trimmed
            ).getOrNull()?.let { validated ->
                val dto = (validated as? ValidatedAiResponse.Transaction)?.dto
                parseTransactionDto(dto ?: return@let, trimmed)?.let {
                    return Result.success(ParsedIntent.Transaction.from(it, trimmed))
                }
            }
        }

        return Result.success(QuickParse.parseVoiceIntent(trimmed))
    }

    /** True when a recording can be sent to the cloud (online + edge configured). */
    fun canTranscribeInCloud(): Boolean = network.isOnline() && edgeClient.isConfigured()

    /**
     * Cloud transcription + parsing in one call (Gemini audio).
     * Failure means the caller should fall back to offline Vosk.
     */
    suspend fun parseVoiceAudio(file: java.io.File): Result<Pair<String, ParsedIntent>> {
        if (!canTranscribeInCloud()) return Result.failure(IllegalStateException("Cloud voice unavailable"))
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.isNotEmpty() && bytes.size <= 6_000_000) { "Audio empty or too large" }
            val payload = com.ledgerai.app.data.ai.AudioPayload(
                mimeType = "audio/mp4",
                data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
            )
            val raw = edgeClient.voiceIntent(
                nowHint() + "\nTranscribe and interpret the attached audio.", payload
            ).getOrThrow()
            val transcript = JsonParser.parseString(raw).asJsonObject.get("transcript")
                ?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            require(transcript.isNotEmpty()) { "Empty transcript" }
            val intent = mapVoiceIntentJson(raw, transcript)
                ?: QuickParse.parseVoiceIntent(transcript)
            transcript to intent
        }
    }

    private fun nowHint(): String {
        val now = java.time.ZonedDateTime.now()
        return "Current local time: ${now.toLocalDateTime().withNano(0)} (${now.zone}), weekday ${now.dayOfWeek}."
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
            // Forecast stays free-form JSON (not in fixed 6 types); use cascade/edge as chat-like text.
            completeRaw(system, compact).getOrNull()?.let { raw ->
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

        val contextBlock = if (financialContext.isNotBlank()) {
            contextBuilder.fromFinancialContextString(financialContext)
        } else {
            contextBuilder.buildFromRoom()
        }
        val historyBlock = conversationHistory
            .takeLast(8)
            .joinToString("\n") { (role, content) -> "$role: $content" }

        val system = buildString {
            appendLine("You are LedgerAI, a concise personal finance assistant.")
            appendLine("Be practical, short, and avoid inventing account balances.")
            appendLine("Reply with JSON: {\"reply\":\"...\"}")
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

        completeStructured(AiResponseType.CHAT, system, user).getOrNull()?.let { validated ->
            (validated as? ValidatedAiResponse.Chat)?.reply?.let { return Result.success(it) }
        }

        // Cascade may return plain text when Edge unavailable
        return completeRaw(system, user).mapCatching { raw ->
            validator.parseChatReply(raw)
                ?: throw IllegalStateException("Empty chat reply")
        }
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

    /** Daily dashboard insight (type=insight). Caches via [InsightStore]. */
    suspend fun generateDailyInsight(forceRefresh: Boolean = false): Result<InsightDto> {
        if (!forceRefresh) {
            insightStore.readToday()?.let { return Result.success(it) }
        }
        if (!canCallCloud()) {
            val fallback = InsightDto(
                title = "Track today",
                body = "Log a few expenses so LedgerAI can spot patterns tomorrow.",
                severity = "info",
                actions = listOf("Add by voice"),
            )
            insightStore.save(fallback)
            return Result.success(fallback)
        }

        val context = contextBuilder.buildFromRoom()
        val system = """
            You are LedgerAI. Produce one actionable daily financial insight.
            Reply JSON: {"title":"...","body":"...","severity":"info|watch|alert","actions":["..."]}
        """.trimIndent()
        completeStructured(AiResponseType.INSIGHT, system, context).getOrNull()?.let { validated ->
            val dto = (validated as? ValidatedAiResponse.Insight)?.dto
            if (dto != null) {
                insightStore.save(dto)
                return Result.success(dto)
            }
        }

        val fallback = InsightDto(
            title = "Stay on budget",
            body = "Review your top spending category before adding more purchases today.",
            severity = "info",
            actions = emptyList(),
        )
        insightStore.save(fallback)
        return Result.success(fallback)
    }

    fun cachedInsight(): InsightDto? = insightStore.readToday()

    suspend fun summarizeNote(title: String, body: String): Result<NoteSummaryDto> {
        if (!canCallCloud()) {
            return Result.failure(IllegalStateException("Cloud AI unavailable for note summary"))
        }
        val system = """
            Summarize the user's note. Reply JSON:
            {"summary":"...","tags":["..."],"highlights":["..."]}
        """.trimIndent()
        val user = "Title: $title\n\n$body"
        completeStructured(AiResponseType.NOTE_SUMMARY, system, user).getOrNull()?.let { validated ->
            (validated as? ValidatedAiResponse.NoteSummary)?.dto?.let { return Result.success(it) }
        }
        return Result.failure(IllegalStateException("Could not summarize note"))
    }

    suspend fun tagNote(title: String, body: String): Result<List<String>> {
        val summary = summarizeNote(title, body).getOrElse { return Result.failure(it) }
        val tags = summary.tags.orEmpty()
        return if (tags.isNotEmpty()) Result.success(tags)
        else Result.failure(IllegalStateException("No tags returned"))
    }

    suspend fun askAboutNote(title: String, body: String, question: String): Result<String> {
        if (!canCallCloud()) {
            return Result.failure(IllegalStateException("Cloud AI unavailable for note Q&A"))
        }
        val system = """
            Answer the user's question about their note. Reply JSON: {"reply":"..."}
            Stay faithful to the note; do not invent facts.
        """.trimIndent()
        val user = "Title: $title\n\nNote:\n$body\n\nQuestion: $question"
        completeStructured(AiResponseType.CHAT, system, user).getOrNull()?.let { validated ->
            (validated as? ValidatedAiResponse.Chat)?.reply?.let { return Result.success(it) }
        }
        return Result.failure(IllegalStateException("Could not answer about note"))
    }

    suspend fun suggestScheduleDrafts(): Result<List<ScheduleDraftDto>> {
        val slice = scheduleContextBuilder.build14DaySlice()
        val system = """
            You suggest missing tasks/events for a student calendar. Reply ONLY JSON:
            {"drafts":[{"type":"TASK|EVENT|EXAM","title":"...","start_at":"yyyy-MM-ddTHH:mm:ss","reason":"..."}]}
            Max 5 drafts. Do not duplicate items already in the schedule slice.
        """.trimIndent()
        if (!canCallCloud()) {
            return Result.success(emptyList())
        }
        return completeRaw(system, slice).mapCatching { raw ->
            val json = extractJsonObject(raw)
            if (json == null) {
                emptyList()
            } else {
                gson.fromJson(json, ScheduleDraftListDto::class.java)?.drafts?.filter {
                    !it.title.isNullOrBlank()
                } ?: emptyList()
            }
        }
    }

    suspend fun scanNoteForNudges(noteBody: String): Result<List<NoteNudgeProposalDto>> {
        val system = """
            Extract up to 3 notification proposals from the note. Reply ONLY JSON:
            {"proposals":[{"message":"...","suggested_at":"yyyy-MM-ddTHH:mm:ss","reason":"..."}]}
        """.trimIndent()
        if (!canCallCloud()) {
            return Result.failure(IllegalStateException("AI offline"))
        }
        return completeRaw(system, noteBody.take(4000)).mapCatching { raw ->
            val json = extractJsonObject(raw)
            if (json == null) {
                emptyList()
            } else {
                gson.fromJson(json, NoteNudgeScanDto::class.java)?.proposals?.filter {
                    !it.message.isNullOrBlank()
                } ?: emptyList()
            }
        }
    }

    /**
     * Prefer Edge Function when SUPABASE_URL is configured; fall back to [AiProviderRouter].
     */
    private suspend fun completeStructured(
        type: AiResponseType,
        system: String,
        user: String,
    ): Result<ValidatedAiResponse> {
        if (network.isOnline() && edgeClient.isConfigured()) {
            edgeClient.complete(type, system, user).getOrNull()?.let { raw ->
                validator.validate(type, raw)?.let { return Result.success(it) }
            }
        }
        if (network.isOnline() && config.hasAnyConfiguredProvider()) {
            router.complete(system, user).getOrNull()?.let { raw ->
                validator.validate(type, raw)?.let { return Result.success(it) }
            }
        }
        return Result.failure(IllegalStateException("No AI path available for ${type.wireName}"))
    }

    private suspend fun completeRaw(system: String, user: String): Result<String> {
        if (network.isOnline() && edgeClient.isConfigured()) {
            edgeClient.complete(AiResponseType.CHAT, system, user).getOrNull()?.let { return Result.success(it) }
        }
        if (network.isOnline() && config.hasAnyConfiguredProvider()) {
            return router.complete(system, user)
        }
        return Result.failure(IllegalStateException("No AI path available"))
    }

    private fun canCallCloud(): Boolean =
        network.isOnline() && (edgeClient.isConfigured() || config.hasAnyConfiguredProvider())

    private fun mapVoiceIntentJson(raw: String, transcript: String): ParsedIntent? {
        return try {
            val json = extractJsonObject(raw) ?: return null
            val dto = gson.fromJson(json, ParsedVoiceIntentDto::class.java) ?: return null
            val confidence = (dto.confidence ?: 0.7f).coerceIn(0f, 1f)
            when (dto.intent?.uppercase()) {
                "TASK" -> {
                    val title = dto.title?.takeIf { it.isNotBlank() } ?: return null
                    ParsedIntent.Task(
                        title = title,
                        notes = dto.body ?: dto.note.orEmpty(),
                        dueAt = parseDateTime(dto.dueAt),
                        confidence = confidence,
                        rawTranscript = transcript,
                    )
                }
                "REMINDER" -> {
                    val remindAt = parseDateTime(dto.remindAt) ?: return null
                    ParsedIntent.Reminder(
                        title = dto.title?.ifBlank { "Reminder" } ?: "Reminder",
                        label = dto.label?.ifBlank { "Reminder" } ?: "Reminder",
                        remindAt = remindAt,
                        confidence = confidence,
                        rawTranscript = transcript,
                    )
                }
                "ALARM" -> {
                    val time = parseClockTime(dto.time) ?: return null
                    val repeatDays = dto.repeatDays?.takeIf { it >= 0 }
                        ?: QuickParse.parseRepeatDays(transcript)
                    ParsedIntent.Alarm(
                        label = dto.label?.ifBlank { "Alarm" } ?: dto.title ?: "Alarm",
                        time = time,
                        repeatDays = repeatDays,
                        confidence = confidence,
                        rawTranscript = transcript,
                    )
                }
                "NOTE" -> {
                    if (dto.amount != null) {
                        val tx = parseTransactionDto(
                            ParsedTransactionDto(
                                amount = dto.amount,
                                category = dto.category,
                                merchant = dto.merchant?.ifBlank { QuickParse.extractName(transcript) },
                                note = dto.note,
                                type = dto.type,
                                confidence = confidence,
                            ),
                            transcript
                        )
                        if (tx != null) return ParsedIntent.Transaction.from(tx, transcript)
                    }
                    val title = dto.title?.takeIf { it.isNotBlank() }
                        ?: QuickParse.extractName(transcript).ifBlank { "Note" }
                    ParsedIntent.Note(
                        title = title,
                        body = dto.body ?: dto.note.orEmpty(),
                        tags = dto.tags.orEmpty(),
                        confidence = confidence,
                        rawTranscript = transcript,
                    )
                }
                "ROUTINE" -> {
                    val title = dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.label?.takeIf { it.isNotBlank() }
                        ?: return null
                    val rule = dto.repeatRule?.uppercase()?.takeIf {
                        it in setOf("DAILY", "WEEKLY", "WEEKDAYS", "CUSTOM")
                    } ?: QuickParse.parseRepeatRule(transcript)
                    ParsedIntent.Routine(
                        title = title,
                        notes = dto.body ?: dto.note.orEmpty(),
                        repeatRule = rule,
                        confidence = confidence,
                        rawTranscript = transcript,
                    )
                }
                "BILL" -> {
                    val amount = dto.amount ?: return null
                    val name = dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.merchant?.takeIf { it.isNotBlank() }
                        ?: dto.label?.takeIf { it.isNotBlank() }
                        ?: return null
                    val category = TransactionCategory.entries.find {
                        it.name.equals(dto.category, ignoreCase = true) ||
                            it.displayName.equals(dto.category, ignoreCase = true)
                    } ?: TransactionCategory.SUBSCRIPTIONS
                    val frequency = when (dto.repeatRule?.uppercase()) {
                        "WEEKLY" -> BillFrequency.WEEKLY
                        "YEARLY" -> BillFrequency.YEARLY
                        "QUARTERLY" -> BillFrequency.QUARTERLY
                        else -> BillFrequency.MONTHLY
                    }
                    ParsedIntent.Bill(
                        name = name,
                        amount = amount,
                        frequency = frequency,
                        nextDueDate = parseDateTime(dto.dueAt)?.toLocalDate() ?: LocalDate.now(),
                        category = category,
                        rawTranscript = transcript,
                        confidence = confidence,
                    )
                }
                "GOAL" -> {
                    val amount = dto.amount ?: return null
                    val name = dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.merchant?.takeIf { it.isNotBlank() }
                        ?: dto.label?.takeIf { it.isNotBlank() }
                        ?: return null
                    ParsedIntent.Goal(
                        name = name,
                        targetAmount = amount,
                        rawTranscript = transcript,
                        confidence = confidence,
                    )
                }
                else -> {
                    val tx = parseTransactionDto(
                        ParsedTransactionDto(
                            amount = dto.amount,
                            category = dto.category,
                            merchant = dto.merchant,
                            note = dto.note,
                            type = dto.type,
                            confidence = confidence,
                        ),
                        transcript
                    ) ?: return null
                    ParsedIntent.Transaction.from(tx, transcript)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(raw) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay() }.getOrNull()
    }

    private fun parseClockTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(raw) }.getOrNull()
            ?: runCatching { LocalTime.parse(raw, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private fun parseTransactionDto(dto: ParsedTransactionDto, fallbackNote: String): ParsedTransaction? {
        return try {
            val category = TransactionCategory.entries.find {
                it.name.equals(dto.category, ignoreCase = true) ||
                    it.displayName.equals(dto.category, ignoreCase = true)
            } ?: TransactionCategory.OTHER
            val type = when (dto.type?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            val named = dto.merchant?.takeIf { it.isNotBlank() } ?: QuickParse.extractName(fallbackNote)
            val note = dto.note.orEmpty().let { raw ->
                if (raw.equals(fallbackNote, ignoreCase = true) && named.isNotBlank()) "" else raw
            }
            ParsedTransaction(
                amount = dto.amount,
                category = category,
                merchant = named,
                date = LocalDate.now(),
                note = note,
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

    private fun quickParse(input: String): ParsedTransaction = QuickParse.parse(input)

    /**
     * Turns messy timetable text (CSV, OCR, or bullet list) into weekly rows.
     * Falls back to empty list — caller should use [com.ledgerai.app.data.schedule.ScheduleCsvParser].
     */
    suspend fun parseTimetable(raw: String): Result<List<ParsedScheduleRow>> {
        if (!canCallCloud()) return Result.failure(IllegalStateException("AI offline"))
        val system = timetableJsonSystemPrompt()
        return runCatching {
            parseTimetableJson(completeRaw(system, raw.take(12_000)).getOrThrow())
        }
    }

    suspend fun parseTimetableFromImage(context: Context, uri: Uri): Result<List<ParsedScheduleRow>> {
        val (mime, base64) = loadImageBase64(context, uri)
            ?: return Result.failure(IllegalArgumentException("Could not read image"))
        val system = timetableJsonSystemPrompt()
        val user = "Read this timetable image. Output JSON only."
        return runCatching {
            val raw = router.completeVision(system, user, mime, base64).getOrThrow()
            parseTimetableJson(raw)
        }
    }

    private fun timetableJsonSystemPrompt(): String = """
        You extract a school/university weekly timetable.
        Reply with ONLY JSON (no markdown). Use this shape:
        {"version":1,"classes":[{"day":"Monday","dayOfWeek":1,"startTime":"09:00","endTime":"10:30","title":"Class name","courseCode":"","location":"Room"}]}
        Rules:
        - "day" is the full English weekday name; "dayOfWeek" is 1=Monday … 7=Sunday.
        - Times 24h HH:mm. Include every class block you see.
        - If the grid shows days as columns, assign each block to the correct column's weekday.
    """.trimIndent()

    private fun loadImageBase64(context: Context, uri: Uri): Pair<String, String>? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val scaled = scaleForVision(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled != bitmap) scaled.recycle()
        bitmap.recycle()
        val b64 = Base64.getEncoder().encodeToString(out.toByteArray())
        return "image/jpeg" to b64
    }

    private fun scaleForVision(source: Bitmap): Bitmap {
        val max = 1600
        val w = source.width
        val h = source.height
        if (w <= max && h <= max) return source
        val scale = minOf(max.toFloat() / w, max.toFloat() / h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }

    private fun parseTimetableJson(raw: String): List<ParsedScheduleRow> {
        val fromDoc = ScheduleTimetableJson.parseRowsFromAiJson(raw)
        if (fromDoc.isNotEmpty()) return fromDoc
        val arrText = extractJsonArray(raw) ?: return emptyList()
        return ScheduleTimetableJson.parseRowsFromAiJson(arrText)
    }
}
