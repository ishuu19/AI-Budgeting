package com.ledgerai.app.domain.widget

/** Speech the widget mic sends to categorization. Blank audio is dropped. */
fun widgetMicTranscript(spoken: String): String? = spoken.trim().takeIf { it.isNotEmpty() }
