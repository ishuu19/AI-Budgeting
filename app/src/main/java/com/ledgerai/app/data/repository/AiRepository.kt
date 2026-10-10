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
import com.ledgerai.app.data.ai.ScheduleDraftDto
import com.ledgerai.app.data.ai.ScheduleDraftListDto
import com.ledgerai.app.data.ai.LearnedRules
import com.ledgerai.app.data.ai.LocalParseAnswer
import com.ledgerai.app.data.ai.LocalParseModel
import com.ledgerai.app.data.ai.LocalParsePrompt
import com.ledgerai.app.data.ai.ParsedIntent
import com.ledgerai.app.data.ai.PlaceMatch
import com.ledgerai.app.data.ai.ParsedTransactionDto
import com.ledgerai.app.data.ai.ParsedVoiceIntentDto
import com.ledgerai.app.data.ai.QuickParse
import com.ledgerai.app.data.ai.RoutedIntents
import com.ledgerai.app.data.ai.RuleLexicon
import com.ledgerai.app.data.ai.VoiceIntentRouter
import com.ledgerai.app.data.preferences.UserPreferences
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
import com.ledgerai.app.domain.model.CalendarEventKind
import com.ledgerai.app.domain.model.DebtDirection
import com.ledgerai.app.domain.model.EventReminder
import com.ledgerai.app.domain.model.JobApplicationStatus
import com.ledgerai.app.domain.model.MAX_REMINDERS_PER_EVENT
import com.ledgerai.app.domain.schedule.labelForMinutesBefore
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
import kotlinx.coroutines.flow.first

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
    private val validator: AiResponseValidator,
    private val insightStore: InsightStore,
    private val network: NetworkAvailability,
    private val gson: Gson,
    private val jobs: JobRepository,
    private val calendar: CalendarRepository,
    private val prefs: UserPreferences,
    private val localModel: LocalParseModel,
    lexicon: RuleLexicon,
) {

    init {
        QuickParse.lexicon = lexicon
    }

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

    /** First item of [parseVoiceIntents]. */
    suspend fun parseVoiceIntent(transcript: String): Result<ParsedIntent> =
        parseVoiceIntents(transcript).map { it.first() }

    /**
     * Voice router. One utterance can hold several items, so the result is a list (at least one element):
     * TRANSACTION | EVENT (task, reminder, alarm, routine, exam) | NOTE | BILL | DEBT | GOAL | BUDGET.
     * Offline, and when the cloud has no answer, [QuickParse] decides. Text nothing recognises comes back as
     * [ParsedIntent.Unmatched]. The cloud may answer with an `items` array or a bare array.
     */
    suspend fun parseVoiceIntents(transcript: String): Result<List<ParsedIntent>> =
        parseVoiceIntentsRouted(transcript).map { it.items }

    /**
     * The on-phone model is the first layer. Rules keep any field they already proved.
     * The cloud is asked only when the phone model has no answer and the rules are unsure.
     */
    suspend fun parseVoiceIntentsRouted(transcript: String): Result<RoutedIntents> {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty transcript"))
        }
        LearnedRules.current = LearnedRules.parse(runCatching { prefs.learnedRulesNow() }.getOrDefault(""))
        val cloudEnabled = runCatching { prefs.cloudFallback.first() }.getOrDefault(true)
        val routed = VoiceIntentRouter.route(
            trimmed,
            cloudEnabled,
            cloud = { cloudVoiceItems(trimmed) },
            local = local@{
                val raw = localModel.complete(LocalParsePrompt.of(trimmed)) ?: return@local emptyList()
                val fromModel = mapVoiceItemsJson(raw, trimmed).ifEmpty {
                    listOfNotNull(LocalParseAnswer.read(raw, trimmed))
                }
                preferLocalKinds(fromModel, trimmed)
            }
        )
        val needsPlaces = routed.items.any { it is ParsedIntent.Job && it.location.isNotBlank() }
        val items = if (needsPlaces) snapPlaces(routed.items, knownPlaces()) else routed.items
        return Result.success(RoutedIntents(items, routed.source, routed.cloudCalled))
    }

    /** Edge function first, then the direct provider. Empty when offline, unconfigured or nothing maps. */
    private suspend fun cloudVoiceItems(trimmed: String): List<ParsedIntent> {
        if (!canCallCloud()) return emptyList()
        val places = knownPlaces()
        if (network.isOnline() && edgeClient.isConfigured()) {
            edgeClient.voiceIntent(nowHint() + placeHint(places) + "\nUser said: " + trimmed).getOrNull()?.let { raw ->
                mapVoiceItemsJson(raw, trimmed).takeIf { it.isNotEmpty() }?.let {
                    return preferLocalKinds(it, trimmed)
                }
            }
        }

        if (canCallCloud()) {
            val system = """
                EN/BN. JSON only {"items":[{"intent":"TRANSACTION|EVENT|TASK|EXAM|REMINDER|ALARM|ROUTINE|NOTE|BILL|DEBT|GOAL|BUDGET|JOB|DELETE|EDIT","amount":n|null,"category":"FOOD|TRANSPORT|SUBSCRIPTIONS|ENTERTAINMENT|SHOPPING|HEALTH|UTILITIES|RENT|SALARY|OTHER","merchant":"","note":"","type":"INCOME|EXPENSE","title":"","name":"","body":"","start_at":"local ISO|null","due_at":null,"label":"","repeat_rule":"","direction":"I_OWE|THEY_OWE"}]}.
                Dates named by the user stay that date. Money is TRANSACTION. JOB name=company title=role merchant=site note=place body=labeled dates. DELETE/EDIT do not create a new item. Empty if unknown.
                ${placeHint(places)}
            """.trimIndent()
            completeRaw(system, trimmed).getOrNull()?.let { raw ->
                mapVoiceItemsJson(raw, trimmed).takeIf { it.isNotEmpty() }?.let {
                    return preferLocalKinds(it, trimmed)
                }
            }
        }

        return emptyList()
    }

    /** Places already saved on jobs and calendar events, so a misspelling can be corrected. */
    private suspend fun knownPlaces(): List<String> {
        val fromJobs = jobs.observeAll().first().map { it.location }
        val fromEvents = calendar.observeAll().first().map { it.location }
        return (fromJobs + fromEvents).map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }.take(40)
    }

    private fun placeHint(places: List<String>): String =
        if (places.isEmpty()) ""
        else "\nKnown places: ${places.joinToString(", ")}. If a job location is close to one of these, use that exact place."

    private fun snapPlaces(items: List<ParsedIntent>, places: List<String>): List<ParsedIntent> =
        items.map { item ->
            if (item is ParsedIntent.Job && item.location.isNotBlank()) {
                item.copy(location = PlaceMatch.snap(item.location, places))
            } else item
        }

    /**
     * The edge function and some models only know a transaction or a note. When the words clearly name a
     * bill, debt, goal or budget, the offline parse wins for those.
     */
    private fun preferLocalKinds(cloud: List<ParsedIntent>, transcript: String): List<ParsedIntent> {
        val local = QuickParse.parseVoiceIntents(transcript)
        if (local.any { it is ParsedIntent.Adjust }) return local
        if (local.any { it is ParsedIntent.Job }) {
            val cloudJob = cloud.filterIsInstance<ParsedIntent.Job>().firstOrNull()
            return local.map { item ->
                if (item !is ParsedIntent.Job || cloudJob == null) item
                else item.copy(
                    company = item.company.takeUnless { it.equals("Company", true) } ?: cloudJob.company,
                    title = pickJobRole(cloudJob.title, item.title),
                    location = item.location.ifBlank { cloudJob.location }
                )
            }
        }
        if (cloud.none { it is ParsedIntent.Transaction || it is ParsedIntent.Note }) return cloud
        val special = local.any {
            it is ParsedIntent.Bill || it is ParsedIntent.Debt || it is ParsedIntent.Goal || it is ParsedIntent.Budget
        }
        return if (special) local else cloud
    }

    /** All items in a cloud reply, checked by [AiResponseValidator]. Empty when nothing maps. */
    private fun mapVoiceItemsJson(raw: String, transcript: String): List<ParsedIntent> =
        validator.validateVoiceItems(raw).mapNotNull { mapVoiceDto(it, transcript) }

    /** True when a cloud model can answer right now (online and a provider is configured). */
    fun isAiAvailable(): Boolean = canCallCloud()

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
            val intent = mapVoiceItemsJson(raw, transcript).firstOrNull()
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
        currentBudgets: Map<TransactionCategory, Double>,
        bills: List<com.ledgerai.app.domain.model.Bill> = emptyList(),
        useCloud: Boolean = false,
    ): Result<List<FinancialForecast>> {
        if (recentTransactions.isEmpty()) {
            return Result.failure(IllegalStateException("Need recent transactions to forecast"))
        }

        // Rules first: recurring bills plus average weekday spend against the income pattern.
        // The cloud is only asked when the caller opts in, and then only as an alternative view.
        if (!useCloud) {
            return Result.success(
                com.ledgerai.app.data.insight.ForecastRules.project(
                    recentTransactions, currentBudgets, bills, LocalDate.now(),
                    fmt = { com.ledgerai.app.presentation.components.money(it) }
                )
            )
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
    suspend fun generateDailyInsight(forceRefresh: Boolean = false, rewordWithCloud: Boolean = false): Result<InsightDto> {
        if (!forceRefresh) {
            insightStore.readToday()?.let { return Result.success(it) }
        }
        // Rules first: the detectors in InsightEngine and InsightRules decide what to say and every number in it.
        val ruled = runCatching {
            com.ledgerai.app.data.insight.InsightRules.dailyInsight(
                contextBuilder.insightInputs(),
                fmt = { com.ledgerai.app.presentation.components.money(it) }
            )
        }.getOrNull()
        if (ruled != null) {
            if (rewordWithCloud && canCallCloud()) {
                val system = "Reword this insight in at most 30 words. Keep every number and currency symbol exactly. " +
                    "Reply JSON: {\"title\":\"...\",\"body\":\"...\",\"severity\":\"info|watch|alert\",\"actions\":[\"...\"]}"
                val user = "Title: ${ruled.title}\nBody: ${ruled.body}\nSeverity: ${ruled.severity}"
                val reworded = completeStructured(AiResponseType.INSIGHT, system, user).getOrNull()
                    ?.let { (it as? ValidatedAiResponse.Insight)?.dto }
                    ?.takeIf { dto -> dto.body?.let { b -> Regex("\\d[\\d,.]*").findAll(ruled.body.orEmpty()).all { n -> b.contains(n.value) } } == true }
                if (reworded != null) {
                    val out = reworded.copy(severity = ruled.severity, source = com.ledgerai.app.data.ai.SOURCE_AI)
                    insightStore.save(out)
                    return Result.success(out)
                }
            }
            insightStore.save(ruled)
            return Result.success(ruled)
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
            The body must be exactly 30 words. No more, no less.
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

    fun insightIsStale(): Boolean = insightStore.isStale()

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
        val slice = contextBuilder.build14DaySlice()
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

    /**
     * One short call that turns a few saved lines into phrase rules. The reply is at most a handful of JSON objects.
     */
    suspend fun learnRules(compactLines: String): String? {
        if (!canCallCloud() || compactLines.isBlank()) return null
        val system = """JSON only {"rules":[{"p":"phrase","k":"Spend|Income|Task|Reminder|Event|Exam|Routine|Alarm|Budget|Bill|Debt|Goal|Job|Note"}]}. Max 4. No prose."""
        return completeRaw(system, compactLines.take(400)).getOrNull()?.take(500)
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

    private fun mapVoiceDto(dto: ParsedVoiceIntentDto, transcript: String): ParsedIntent? {
        return try {
            val confidence = (dto.confidence ?: 0.7f).coerceIn(0f, 1f)
            when (dto.intent?.uppercase()) {
                "DELETE", "EDIT" -> QuickParse.parseVoiceIntent(transcript)
                "JOB" -> mapJobIntent(dto, transcript, confidence)
                "EVENT", "TASK", "EXAM", "REMINDER", "ALARM", "ROUTINE" ->
                    mapEventIntent(dto, transcript, confidence)
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
                "BILL" -> {
                    val amount = dto.amount ?: return null
                    val name = dto.name?.takeIf { it.isNotBlank() }
                        ?: dto.title?.takeIf { it.isNotBlank() }
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
                "DEBT" -> {
                    val amount = dto.amount?.takeIf { it > 0.0 } ?: return null
                    val friend = dto.name?.takeIf { it.isNotBlank() }
                        ?: dto.title?.takeIf { it.isNotBlank() }
                        ?: dto.merchant?.takeIf { it.isNotBlank() }
                        ?: dto.label?.takeIf { it.isNotBlank() }
                        ?: return null
                    val direction = when (dto.direction?.uppercase()?.replace(' ', '_')) {
                        "I_OWE" -> DebtDirection.I_OWE
                        "THEY_OWE" -> DebtDirection.THEY_OWE
                        else -> QuickParse.debtDirection(transcript)
                    }
                    ParsedIntent.Debt(
                        friendName = friend,
                        amount = amount,
                        direction = direction,
                        dueDate = parseDateTime(dto.dueAt)?.toLocalDate(),
                        rawTranscript = transcript,
                        confidence = confidence,
                    )
                }
                "BUDGET" -> {
                    val amount = dto.amount?.takeIf { it > 0.0 } ?: return null
                    val category = TransactionCategory.entries.find {
                        it.name.equals(dto.category, ignoreCase = true) ||
                            it.displayName.equals(dto.category, ignoreCase = true)
                    } ?: QuickParse.parse(transcript).category
                    ParsedIntent.Budget(
                        category = category,
                        limit = amount,
                        rawTranscript = transcript,
                        confidence = confidence,
                    )
                }
                "GOAL" -> {
                    val amount = dto.amount ?: return null
                    val name = dto.name?.takeIf { it.isNotBlank() }
                        ?: dto.title?.takeIf { it.isNotBlank() }
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
                null, "", "TRANSACTION", "EXPENSE", "INCOME" -> {
                    if ((dto.amount ?: 0.0) <= 0.0) return null
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
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The model's role wins. "Role", "Job", and "Interview" are placeholders, not a role. */
    private fun pickJobRole(fromModel: String?, fromWords: String?): String {
        fun usable(raw: String?): String? {
            val t = raw?.trim().orEmpty()
            if (t.isBlank() || t.equals("Role", true) || t.equals("Job", true) || t.equals("Interview", true)) return null
            return t.take(80)
        }
        return usable(fromModel) ?: usable(fromWords) ?: fromWords?.trim()?.ifBlank { null } ?: "Role"
    }

    /** A job stays in the jobs list. It is never written onto the main calendar. */
    private fun mapJobIntent(dto: ParsedVoiceIntentDto, transcript: String, confidence: Float): ParsedIntent {
        val local = QuickParse.parseVoiceIntents(transcript).filterIsInstance<ParsedIntent.Job>().firstOrNull()
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val spoken = parseDateTime(dto.startAt) ?: parseDateTime(dto.dueAt)
        val follow = spoken?.toLocalDate()
            ?: QuickParse.resolveSpokenDateTime(transcript, today, now)?.toLocalDate()
            ?: local?.followUpOn
        val status = JobApplicationStatus.entries.firstOrNull {
            it.name.equals(dto.label, true) || it.name.equals(dto.type, true)
        } ?: local?.status ?: JobApplicationStatus.APPLIED
        return ParsedIntent.Job(
            company = dto.name?.takeIf { it.isNotBlank() }
                ?: dto.merchant?.takeIf { it.isNotBlank() }
                ?: local?.company
                ?: "Company",
            title = pickJobRole(dto.title, local?.title),
            source = local?.source.orEmpty(),
            url = local?.url.orEmpty(),
            location = local?.location?.takeIf { it.isNotBlank() }
                ?: dto.note?.trim()?.takeIf { it.length in 2..40 && '.' !in it }
                ?: "",
            extraDates = local?.extraDates.orEmpty(),
            appliedSpoken = local?.appliedSpoken == true,
            status = status,
            appliedOn = parseDateTime(dto.dueAt)?.toLocalDate() ?: local?.appliedOn ?: today,
            followUpOn = follow,
            notes = dto.note?.takeIf { it.isNotBlank() } ?: dto.body.orEmpty().ifBlank { transcript },
            rawTranscript = transcript,
            confidence = confidence,
        )
    }

    /** One calendar item from the cloud DTO. The spoken date and the title are always honoured. */
    private fun mapEventIntent(dto: ParsedVoiceIntentDto, transcript: String, confidence: Float): ParsedIntent? {
        val today = LocalDate.now()
        val intent = dto.intent?.uppercase().orEmpty()
        val kind = when (intent) {
            "ALARM" -> CalendarEventKind.ALARM
            "EXAM" -> CalendarEventKind.EXAM
            "EVENT" -> CalendarEventKind.EVENT
            "ROUTINE" -> CalendarEventKind.ROUTINE
            else -> CalendarEventKind.TASK
        }
        val spoken = parseDateTime(dto.startAt) ?: parseDateTime(dto.remindAt) ?: parseDateTime(dto.dueAt)
        val now = LocalDateTime.now()
        val fromSpeech = QuickParse.resolveSpokenDateTime(
            transcript, today, now, allowBareHour = intent == "ALARM"
        )
        val fromText = fromSpeech ?: QuickParse.resolveEventDateTimeFromText(transcript, today, now)
        val whenFromSpeech = fromSpeech != null || QuickParse.hasSpokenWhen(transcript) ||
            spoken == null || spoken.isBefore(now)
        val title = dto.title?.takeIf { it.isNotBlank() }
            ?: dto.label?.takeIf { it.isNotBlank() && !it.equals("Reminder", true) && !it.equals("Alarm", true) }
            ?: QuickParse.eventTitleFromText(transcript, if (kind == CalendarEventKind.ALARM) "Alarm" else "Reminder")
        val notes = dto.body ?: dto.note.orEmpty()
        val explicit = dto.reminderMinutes.orEmpty().filter { it >= 0 }.distinct().take(MAX_REMINDERS_PER_EVENT)
            .map { EventReminder(label = labelForMinutesBefore(it.toLong()), offsetMinutes = it) }

        return when (kind) {
            CalendarEventKind.ALARM -> {
                val start = fromSpeech
                    ?: spoken?.let { QuickParse.futureOnDate(it.toLocalTime(), transcript, it.toLocalDate(), now) }
                    ?: return null
                val mask = dto.repeatDays?.takeIf { it >= 0 } ?: QuickParse.parseRepeatDays(transcript)
                ParsedIntent.Event(
                    title = title.ifBlank { "Alarm" },
                    startAt = start,
                    kind = kind,
                    repeat = if (mask == 0) null else QuickParse.repeatFromAlarmMask(mask),
                    confidence = confidence,
                    rawTranscript = transcript,
                )
            }
            CalendarEventKind.ROUTINE -> {
                val start = if (whenFromSpeech) fromText else spoken!!
                val rule = dto.repeatRule?.uppercase()?.takeIf { it in setOf("DAILY", "WEEKLY", "WEEKDAYS") }
                    ?: QuickParse.parseRepeatRule(transcript)
                ParsedIntent.Event(
                    title = title,
                    notes = notes,
                    startAt = start,
                    endAt = parseDateTime(dto.endAt)?.takeIf { it.isAfter(start) } ?: start.plusMinutes(30),
                    kind = kind,
                    repeat = QuickParse.repeatFromRule(rule, start.toLocalDate()),
                    reminders = explicit,
                    confidence = confidence,
                    rawTranscript = transcript,
                )
            }
            else -> {
                val start = if (whenFromSpeech) fromText else spoken!!
                val isReminder = intent == "REMINDER"
                ParsedIntent.Event(
                    title = title,
                    notes = notes,
                    startAt = start,
                    endAt = if (kind == CalendarEventKind.TASK) null
                    else parseDateTime(dto.endAt)?.takeIf { it.isAfter(start) } ?: start.plusHours(1),
                    kind = kind,
                    repeat = QuickParse.explicitRepeat(transcript, start.toLocalDate()),
                    reminders = when {
                        explicit.isNotEmpty() -> explicit
                        isReminder -> listOf(EventReminder(label = "At time", offsetMinutes = 0))
                        else -> emptyList()
                    },
                    confidence = confidence,
                    rawTranscript = transcript,
                )
            }
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
