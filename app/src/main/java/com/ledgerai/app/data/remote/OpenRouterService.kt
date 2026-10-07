package com.ledgerai.app.data.remote

import com.ledgerai.app.data.remote.model.OpenRouterRequest
import com.ledgerai.app.data.remote.model.OpenRouterResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OpenRouterService {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}
