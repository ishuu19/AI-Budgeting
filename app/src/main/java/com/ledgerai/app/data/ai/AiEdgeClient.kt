package com.ledgerai.app.data.ai

import android.util.Log
import com.google.gson.Gson
import com.ledgerai.app.BuildConfig
import com.ledgerai.app.data.auth.SessionGuard
import com.ledgerai.app.data.preferences.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production AI path: POST to Supabase Edge Function when [BuildConfig.SUPABASE_URL] is set.
 * Requires a user JWT in [UserSession]; falls back to [AiProviderRouter] via [AiRepository].
 */
@Singleton
class AiEdgeClient @Inject constructor(
    private val api: AiEdgeApi,
    private val session: UserSession,
    private val sessionGuard: SessionGuard,
    private val gson: Gson,
) {

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.trim().isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.trim().isNotBlank()

    suspend fun complete(
        type: AiResponseType,
        systemPrompt: String,
        userPrompt: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("SUPABASE_URL / ANON_KEY not configured")
            )
        }

        if (!sessionGuard.ensureFreshSession()) {
            return@withContext Result.failure(
                IllegalStateException("Session expired — sign in again for Edge AI")
            )
        }

        val jwt = session.userInfo.first().accessToken.trim()
        if (jwt.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No auth JWT — sign in required for Edge AI")
            )
        }

        try {
            val modelTier = when (type) {
                AiResponseType.TRANSACTION ->
                    "flash-lite"
                else -> "flash"
            }
            val response = api.complete(
                authorization = "Bearer $jwt",
                apiKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
                body = AiProxyRequest(
                    type = type.wireName,
                    system = systemPrompt,
                    user = userPrompt,
                    modelTier = modelTier,
                ),
            )
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()?.take(200)
                return@withContext Result.failure(
                    IllegalStateException("Edge AI HTTP ${response.code()}: $err")
                )
            }
            val body = response.body()
            if (body?.error != null) {
                return@withContext Result.failure(IllegalStateException(body.error))
            }
            val data = body?.data
                ?: return@withContext Result.failure(IllegalStateException("Empty Edge AI data"))

            // Return envelope so AiResponseValidator can check type + data.
            val envelope = mapOf(
                "type" to (body.type ?: type.wireName),
                "data" to data,
            )
            Result.success(gson.toJson(envelope))
        } catch (e: Exception) {
            Log.w(TAG, "Edge AI call failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Voice -> structured intent via the `voice_intent` edge type (Gemini; OpenRouter free model as text backup).
     * Pass [audio] to transcribe + parse in one call, or only [text] to parse a transcript.
     * Returns the raw `data` JSON object (includes `transcript`).
     */
    suspend fun voiceIntent(text: String, audio: AudioPayload? = null): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Edge AI not configured"))
            }
            if (!sessionGuard.ensureFreshSession()) {
                return@withContext Result.failure(IllegalStateException("Session expired - sign in again"))
            }
            val jwt = session.userInfo.first().accessToken.trim()
            if (jwt.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No auth JWT - sign in required"))
            }
            try {
                val response = api.complete(
                    authorization = "Bearer $jwt",
                    apiKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
                    body = AiProxyRequest(
                        type = "voice_intent",
                        user = text,
                        modelTier = if (audio == null) "flash-lite" else "flash",
                        audio = audio,
                    ),
                )
                if (!response.isSuccessful) {
                    val err = response.errorBody()?.string()?.take(200)
                    return@withContext Result.failure(
                        IllegalStateException("Edge AI HTTP ${response.code()}: $err")
                    )
                }
                val body = response.body()
                body?.error?.let { return@withContext Result.failure(IllegalStateException(it)) }
                val data = body?.data
                    ?: return@withContext Result.failure(IllegalStateException("Empty Edge AI data"))
                Log.d(TAG, "voice_intent via ${body.provider}")
                Result.success(gson.toJson(data))
            } catch (e: Exception) {
                Log.w(TAG, "voice_intent failed: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Fast model via the `fast_completion` edge type. The body is [fastCompletionRequest]:
     * system, user, and type only. The provider key stays on the Edge Function.
     * Returns the proxy envelope so the caller can drop errors and missing data.
     */
    suspend fun fastCompletion(system: String, user: String): Result<AiProxyResponse> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Edge AI not configured"))
            }
            if (!sessionGuard.ensureFreshSession()) {
                return@withContext Result.failure(IllegalStateException("Session expired - sign in again"))
            }
            val jwt = session.userInfo.first().accessToken.trim()
            if (jwt.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No auth JWT - sign in required"))
            }
            try {
                val response = api.complete(
                    authorization = "Bearer $jwt",
                    apiKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
                    body = fastCompletionRequest(system, user),
                )
                if (!response.isSuccessful) {
                    val err = response.errorBody()?.string()?.take(200)
                    return@withContext Result.failure(
                        IllegalStateException("Edge AI HTTP ${response.code()}: $err")
                    )
                }
                val body = response.body()
                    ?: return@withContext Result.failure(IllegalStateException("Empty Edge AI data"))
                Result.success(body)
            } catch (e: Exception) {
                Log.w(TAG, "fast_completion failed: ${e.message}")
                Result.failure(e)
            }
        }

    /** Photo classification through the `vision_capture` edge type. Returns the model JSON text. */
    suspend fun visionCapture(system: String, user: String, mimeType: String, base64: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext Result.failure(IllegalStateException("Edge AI not configured"))
            if (!sessionGuard.ensureFreshSession()) {
                return@withContext Result.failure(java.io.IOException("Session expired - sign in again"))
            }
            val jwt = session.userInfo.first().accessToken.trim()
            if (jwt.isBlank()) return@withContext Result.failure(IllegalStateException("No auth JWT - sign in required"))
            try {
                val response = api.complete(
                    authorization = "Bearer $jwt",
                    apiKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
                    body = AiProxyRequest(
                        type = "vision_capture",
                        system = system,
                        user = user,
                        image = AudioPayload(mimeType, base64),
                    ),
                )
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        java.io.IOException("Edge AI HTTP ${response.code()}: ${response.errorBody()?.string()?.take(300).orEmpty()}")
                    )
                }
                val text = response.body()?.let { fastCompletionJson(it) }
                    ?: return@withContext Result.failure(java.io.IOException("Empty Edge AI data"))
                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private const val TAG = "AiEdgeClient"
    }
}
