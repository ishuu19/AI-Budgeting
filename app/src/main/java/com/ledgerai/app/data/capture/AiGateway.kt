package com.ledgerai.app.data.capture

import com.ledgerai.app.data.ai.AiConfig
import com.ledgerai.app.data.ai.AiEdgeClient
import com.ledgerai.app.data.ai.AiProviderRouter
import com.ledgerai.app.data.ai.fastCompletionJson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One place that picks the AI path for capture. Debug builds with a local key call the provider
 * directly; release builds (no key in the APK) go through the `ai-proxy` Edge Function.
 */
@Singleton
class AiGateway @Inject constructor(
    private val config: AiConfig,
    private val router: AiProviderRouter,
    private val edge: AiEdgeClient,
) {
    suspend fun fast(system: String, user: String): Result<String> {
        val viaEdge = edgeFast(system, user)
        if (viaEdge.isSuccess) return viaEdge
        if (config.hasFastModelKey) router.completeFast(system, user).let { if (it.isSuccess) return it }
        return viaEdge
    }

    suspend fun vision(system: String, user: String, mimeType: String, base64: String): Result<String> {
        val viaEdge = edge.visionCapture(system, user, mimeType, base64)
        if (viaEdge.isSuccess) return viaEdge
        val hasGemini = config.geminiApiKey.isNotBlank() || config.geminiApiKeys.isNotEmpty()
        if (hasGemini) router.completeVision(system, user, mimeType, base64).let { if (it.isSuccess) return it }
        return viaEdge
    }

    private suspend fun edgeFast(system: String, user: String): Result<String> {
        val response = edge.fastCompletion(system, user).getOrElse { return Result.failure(it) }
        return fastCompletionJson(response)?.let { Result.success(it) }
            ?: Result.failure(java.io.IOException(response.error ?: "Empty AI reply"))
    }
}
