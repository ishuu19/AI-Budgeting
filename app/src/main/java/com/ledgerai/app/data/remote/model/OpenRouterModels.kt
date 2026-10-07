package com.ledgerai.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class OpenRouterRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Double = 0.3,
    val stream: Boolean = false
) {
    data class Message(
        val role: String,
        val content: String
    )
}

data class OpenRouterResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?,
    val error: ApiError?
) {
    data class Choice(
        val message: Message?,
        @SerializedName("finish_reason") val finishReason: String?
    )

    data class Message(
        val role: String?,
        val content: String?
    )

    data class Usage(
        @SerializedName("prompt_tokens") val promptTokens: Int,
        @SerializedName("completion_tokens") val completionTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int
    )

    data class ApiError(
        val message: String?,
        val code: Int?
    )

    fun getContent(): String? = choices?.firstOrNull()?.message?.content
}
