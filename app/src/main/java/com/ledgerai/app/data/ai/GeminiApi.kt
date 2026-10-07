package com.ledgerai.app.data.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Gemini generateContent.
 * Path: models/{model}:generateContent
 * Auth header: X-goog-api-key
 */
interface GeminiApi {
    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path(value = "model", encoded = true) model: String,
        @Header("X-goog-api-key") apiKey: String,
        @Body body: GeminiGenerateRequest,
    ): Response<GeminiGenerateResponse>
}
