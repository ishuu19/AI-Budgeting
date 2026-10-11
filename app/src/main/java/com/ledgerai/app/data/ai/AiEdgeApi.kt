package com.ledgerai.app.data.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Supabase Edge Function `ai-proxy`.
 * Base URL: {SUPABASE_URL}/functions/v1/
 */
interface AiEdgeApi {
    // Providers refuse some regions (for example Hong Kong). The function runs from the US instead.
    @Headers("x-region: us-east-1")
    @POST("ai-proxy")
    suspend fun complete(
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Body body: AiProxyRequest,
    ): Response<AiProxyResponse>
}
