package com.ledgerai.app.domain.widget

/** Speech the widget mic sends to categorization. Blank audio is dropped. */
fun widgetMicTranscript(spoken: String): String? = spoken.trim().takeIf { it.isNotEmpty() }

/** First phrase from the phone's built-in speech mic. A cancel or a blank result is dropped. */
fun widgetMicFromRecognizer(ok: Boolean, results: List<String>?): String? {
    if (!ok) return null
    return results?.firstOrNull()?.let(::widgetMicTranscript)
}
