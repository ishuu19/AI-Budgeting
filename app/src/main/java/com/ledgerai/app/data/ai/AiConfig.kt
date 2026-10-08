package com.ledgerai.app.data.ai

import com.ledgerai.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI provider settings from BuildConfig.
 * Debug may inject values from secrets.properties; release fields are empty strings.
 * Production should prefer a Supabase Edge Function proxy — keys must not ship in release APKs.
 */
@Singleton
class AiConfig @Inject constructor() {

    val openRouterFreeApiKey: String = BuildConfig.OPENROUTER_FREE_API_KEY.trim()
    val openRouterApiKey: String = BuildConfig.OPENROUTER_API_KEY.trim()
    val openRouterApiKeys: List<String> = splitKeys(BuildConfig.OPENROUTER_API_KEYS)
    val openRouterBaseUrl: String = normalizeBaseUrl(
        BuildConfig.OPENROUTER_BASE_URL.ifBlank { "https://openrouter.ai/api/v1/" }
    )

    val geminiApiKey: String = BuildConfig.GEMINI_API_KEY.trim()
    val geminiApiKeys: List<String> = splitKeys(BuildConfig.GEMINI_API_KEYS)
    val geminiBaseUrl: String = normalizeBaseUrl(
        BuildConfig.GEMINI_BASE_URL.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
    )

    val deepSeekApiKey: String = BuildConfig.DEEPSEEK_API_KEY.trim()
    val deepSeekBaseUrl: String = normalizeBaseUrl(
        BuildConfig.DEEPSEEK_BASE_URL.ifBlank { "https://api.deepseek.com" }
    )

    val modelOpenRouterFree: String =
        BuildConfig.AI_MODEL_OPENROUTER_FREE.ifBlank { "deepseek/deepseek-chat-v3-0324:free" }
    val modelGemini: String =
        BuildConfig.AI_MODEL_GEMINI.ifBlank { "gemini-flash-latest" }
    /** Vision timetable OCR (Gemini multimodal). */
    val modelGeminiVision: String = "gemini-2.0-flash"
    val modelDeepSeek: String =
        BuildConfig.AI_MODEL_DEEPSEEK.ifBlank { "deepseek-chat" }
    val modelOpenRouter: String =
        BuildConfig.AI_MODEL_OPENROUTER.ifBlank { "deepseek/deepseek-chat" }

    /** Key for stage 1: free OpenRouter key, else primary OpenRouter key. */
    val openRouterFreeEffectiveKey: String =
        openRouterFreeApiKey.ifBlank { openRouterApiKey }

    fun hasAnyConfiguredProvider(): Boolean =
        openRouterFreeEffectiveKey.isNotBlank() ||
            geminiApiKey.isNotBlank() ||
            geminiApiKeys.isNotEmpty() ||
            deepSeekApiKey.isNotBlank() ||
            openRouterApiKey.isNotBlank() ||
            openRouterApiKeys.isNotEmpty()

    private fun splitKeys(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun normalizeBaseUrl(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}
