package com.ledgerai.app.domain.ai

enum class FastChannel {
    PROXY,
    DIRECT,
    SKIP,
}

/**
 * Chooses the fast-model channel.
 * [allowClientKey] is BuildConfig.DEBUG at the call site. This function does not read BuildConfig.
 */
fun chooseFastChannel(
    cloudEnabled: Boolean,
    online: Boolean,
    allowClientKey: Boolean,
    hasClientOpenRouterKey: Boolean,
    edgeConfigured: Boolean,
): FastChannel {
    if (!cloudEnabled || !online) return FastChannel.SKIP
    if (allowClientKey && hasClientOpenRouterKey) return FastChannel.DIRECT
    if (edgeConfigured) return FastChannel.PROXY
    return FastChannel.SKIP
}
