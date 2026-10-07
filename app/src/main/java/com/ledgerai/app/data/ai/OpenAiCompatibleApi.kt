package com.ledgerai.app.data.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** OpenAI-compatible chat completions (OpenRouter + DeepSeek). */
interface OpenAiCompatibleApi {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body body: ChatCompletionRequest,
    ): Response<ChatCompletionResponse>
}
