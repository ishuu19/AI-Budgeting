package com.ledgerai.app.data.ai

/** `fast_completion` proxy body. [AiProxyRequest] has no provider-key field. */
fun fastCompletionRequest(system: String, user: String): AiProxyRequest =
    AiProxyRequest(
        type = "fast_completion",
        system = system,
        user = user,
    )

/** Model JSON from [AiProxyResponse.data]; null when [AiProxyResponse.error] is set or `data` is missing. */
fun fastCompletionJson(response: AiProxyResponse): String? {
    if (response.error != null) return null
    val data = response.data ?: return null
    return when {
        data.isJsonPrimitive && data.asJsonPrimitive.isString -> data.asString
        data.isJsonObject || data.isJsonArray -> data.toString()
        else -> null
    }
}
