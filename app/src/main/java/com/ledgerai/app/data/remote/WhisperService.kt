package com.ledgerai.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class WhisperResponse(
    val text: String
)

/**
 * OpenAI-compatible Whisper transcription endpoint.
 * We call OpenAI directly because OpenRouter does not support audio files.
 * The base URL for this service is https://api.openai.com/v1/.
 */
interface WhisperService {
    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language") language: RequestBody,
        @Part("response_format") format: RequestBody,
    ): WhisperResponse
}
