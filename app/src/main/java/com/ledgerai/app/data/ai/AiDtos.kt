package com.ledgerai.app.data.ai

import com.google.gson.annotations.SerializedName

/** OpenAI-compatible chat request (OpenRouter + DeepSeek). */
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int? = 1024,
)

data class ChatMessageDto(
    val role: String,
    val content: String,
)

data class ChatCompletionResponse(
    val choices: List<ChatChoiceDto>? = null,
    val error: ChatErrorDto? = null,
)

data class ChatChoiceDto(
    val message: ChatMessageDto? = null,
    @SerializedName("finish_reason") val finishReason: String? = null,
)

data class ChatErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

/** Gemini generateContent request/response. */
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.3,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 1024,
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDto? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null,
)

data class GeminiErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

/** Structured parse payload expected from the model (JSON in content). */
data class ParsedTransactionDto(
    val amount: Double? = null,
    val category: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val type: String? = null,
    val confidence: Float? = null,
)

data class ForecastItemDto(
    val month: String? = null,
    @SerializedName("predicted_spend") val predictedSpend: Double? = null,
    @SerializedName("recommended_budget") val recommendedBudget: Double? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    val insight: String? = null,
)

data class ForecastListDto(
    val forecasts: List<ForecastItemDto>? = null,
)
