package com.ledgerai.app.data.ai

/** Short instruction for the on-phone model. A 270M model only needs the words and the labels. */
object LocalParsePrompt {
    fun of(transcript: String): String = """
        tea 4
        SPEND
        call mom tomorrow
        REMINDER
        stripe engineer
        JOB
        ${transcript.trim().take(500)}
    """.trimIndent()
}
