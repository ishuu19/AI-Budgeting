package com.ledgerai.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Cascades AI completion across providers (PLAN §5.6):
 * 1) OpenRouter free → 2) Gemini → 3) DeepSeek → 4) OpenRouter paid.
 * Failover on HTTP 401 / 429 / 5xx (and transport errors within a stage).
 */
@Singleton
class AiProviderRouter @Inject constructor(
    private val config: AiConfig,
    @Named("openRouterApi") private val openRouterApi: OpenAiCompatibleApi,
    @Named("deepSeekApi") private val deepSeekApi: OpenAiCompatibleApi,
    private val geminiApi: GeminiApi,
) {

    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.hasAnyConfiguredProvider()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "No AI providers configured. Debug builds may load keys from secrets; " +
                        "release uses empty BuildConfig — prefer Supabase Edge Function."
                )
            )
        }

        val stages = buildStages(systemPrompt, userPrompt)
        var lastError: Throwable? = null

        for (stage in stages) {
            when (val outcome = stage.invoke()) {
                is StageOutcome.Success -> return@withContext Result.success(outcome.text)
                is StageOutcome.Failover -> {
                    lastError = outcome.error
                    Log.w(TAG, "AI stage failover: ${outcome.error.message}")
                }
                is StageOutcome.HardFail -> {
                    return@withContext Result.failure(outcome.error)
                }
            }
        }

        Result.failure(
            lastError ?: IllegalStateException("All AI providers failed or were skipped")
        )
    }

    /** Gemini vision first (best for timetable photos); no OpenRouter vision fallback yet. */
    suspend fun completeVision(
        systemPrompt: String,
        userPrompt: String,
        imageMimeType: String,
        imageBase64: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val keys = buildList {
            if (config.geminiApiKey.isNotBlank()) add(config.geminiApiKey)
            addAll(config.geminiApiKeys.filter { it !in this })
        }
        if (keys.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key required for vision"))
        }
        var last: Throwable? = null
        for ((index, key) in keys.withIndex()) {
            when (val outcome = callGeminiVision(key, systemPrompt, userPrompt, imageMimeType, imageBase64, "gemini-vision-$index")) {
                is StageOutcome.Success -> return@withContext Result.success(outcome.text)
                is StageOutcome.Failover -> last = outcome.error
                is StageOutcome.HardFail -> return@withContext Result.failure(outcome.error)
            }
        }
        Result.failure(last ?: IllegalStateException("Vision failed"))
    }

    private fun buildStages(
        systemPrompt: String,
        userPrompt: String,
    ): List<suspend () -> StageOutcome> {
        val stages = mutableListOf<suspend () -> StageOutcome>()

        // 1) OpenRouter FREE
        val freeKey = config.openRouterFreeEffectiveKey
        if (freeKey.isNotBlank()) {
            stages += {
                callOpenAiCompatible(
                    api = openRouterApi,
                    apiKey = freeKey,
                    model = config.modelOpenRouterFree,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    label = "openrouter-free",
                )
            }
        }

        // 2) Gemini (primary then extras)
        val geminiKeys = buildList {
            if (config.geminiApiKey.isNotBlank()) add(config.geminiApiKey)
            addAll(config.geminiApiKeys.filter { it !in this })
        }
        for ((index, key) in geminiKeys.withIndex()) {
            stages += {
                callGemini(
                    apiKey = key,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    label = "gemini-$index",
                )
            }
        }

        // 3) DeepSeek official
        if (config.deepSeekApiKey.isNotBlank()) {
            stages += {
                callOpenAiCompatible(
                    api = deepSeekApi,
                    apiKey = config.deepSeekApiKey,
                    model = config.modelDeepSeek,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    label = "deepseek",
                )
            }
        }

        // 4) OpenRouter paid / extras
        val paidKeys = buildList {
            if (config.openRouterApiKey.isNotBlank()) add(config.openRouterApiKey)
            addAll(config.openRouterApiKeys.filter { it !in this })
        }
        for ((index, key) in paidKeys.withIndex()) {
            stages += {
                callOpenAiCompatible(
                    api = openRouterApi,
                    apiKey = key,
                    model = config.modelOpenRouter,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    label = "openrouter-paid-$index",
                )
            }
        }

        return stages
    }

    private suspend fun callOpenAiCompatible(
        api: OpenAiCompatibleApi,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        label: String,
    ): StageOutcome {
        return try {
            val body = ChatCompletionRequest(
                model = model,
                messages = listOf(
                    ChatMessageDto(role = "system", content = systemPrompt),
                    ChatMessageDto(role = "user", content = userPrompt),
                ),
            )
            val response = api.createChatCompletion(
                authorization = "Bearer $apiKey",
                body = body,
            )
            mapOpenAiResponse(response, label)
        } catch (e: Exception) {
            classifyException(e, label)
        }
    }

    private suspend fun callGeminiVision(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        mimeType: String,
        base64: String,
        label: String,
    ): StageOutcome {
        return try {
            val body = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(
                            GeminiPart(text = userPrompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = mimeType,
                                    data = base64
                                )
                            )
                        ),
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt)),
                ),
                generationConfig = GeminiGenerationConfig(maxOutputTokens = 8192, temperature = 0.2),
            )
            val response = geminiApi.generateContent(
                model = config.modelGeminiVision,
                apiKey = apiKey,
                body = body,
            )
            mapGeminiResponse(response, label)
        } catch (e: Exception) {
            classifyException(e, label)
        }
    }

    private suspend fun callGemini(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        label: String,
    ): StageOutcome {
        return try {
            val body = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = userPrompt)),
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt)),
                ),
                generationConfig = GeminiGenerationConfig(),
            )
            val response = geminiApi.generateContent(
                model = config.modelGemini,
                apiKey = apiKey,
                body = body,
            )
            mapGeminiResponse(response, label)
        } catch (e: Exception) {
            classifyException(e, label)
        }
    }

    private fun mapOpenAiResponse(
        response: Response<ChatCompletionResponse>,
        label: String,
    ): StageOutcome {
        val code = response.code()
        if (shouldFailover(code)) {
            return StageOutcome.Failover(
                ProviderHttpException(label, code, response.errorBody()?.string()?.take(200))
            )
        }
        if (!response.isSuccessful) {
            // Non-failover codes still try the next provider rather than aborting the cascade.
            return StageOutcome.Failover(
                ProviderHttpException(label, code, response.errorBody()?.string()?.take(200))
            )
        }
        val text = response.body()?.choices
            ?.firstOrNull()
            ?.message
            ?.content
            ?.trim()
            .orEmpty()
        return if (text.isBlank()) {
            StageOutcome.Failover(IllegalStateException("$label returned empty content"))
        } else {
            StageOutcome.Success(text)
        }
    }

    private fun mapGeminiResponse(
        response: Response<GeminiGenerateResponse>,
        label: String,
    ): StageOutcome {
        val code = response.code()
        if (shouldFailover(code)) {
            return StageOutcome.Failover(
                ProviderHttpException(label, code, response.errorBody()?.string()?.take(200))
            )
        }
        if (!response.isSuccessful) {
            return StageOutcome.Failover(
                ProviderHttpException(label, code, response.errorBody()?.string()?.take(200))
            )
        }
        val text = response.body()?.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.joinToString("") { it.text.orEmpty() }
            ?.trim()
            .orEmpty()
        return if (text.isBlank()) {
            StageOutcome.Failover(IllegalStateException("$label returned empty content"))
        } else {
            StageOutcome.Success(text)
        }
    }

    private fun classifyException(e: Exception, label: String): StageOutcome {
        return when (e) {
            is HttpException -> {
                val code = e.code()
                if (shouldFailover(code)) {
                    StageOutcome.Failover(ProviderHttpException(label, code, e.message()))
                } else {
                    StageOutcome.HardFail(ProviderHttpException(label, code, e.message()))
                }
            }
            is IOException -> StageOutcome.Failover(
                IOException("$label network error: ${e.message}", e)
            )
            else -> StageOutcome.Failover(
                IllegalStateException("$label error: ${e.message}", e)
            )
        }
    }

    /** Fail over on auth, rate-limit, and server errors. */
    fun shouldFailover(httpCode: Int): Boolean =
        httpCode == 401 || httpCode == 429 || httpCode in 500..599

    private sealed class StageOutcome {
        data class Success(val text: String) : StageOutcome()
        data class Failover(val error: Throwable) : StageOutcome()
        data class HardFail(val error: Throwable) : StageOutcome()
    }

    class ProviderHttpException(
        val provider: String,
        val httpCode: Int,
        detail: String?,
    ) : IOException("$provider HTTP $httpCode${detail?.let { ": $it" } ?: ""}")

    companion object {
        private const val TAG = "AiProviderRouter"
    }
}
