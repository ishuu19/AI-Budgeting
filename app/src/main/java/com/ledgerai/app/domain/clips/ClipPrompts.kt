package com.ledgerai.app.domain.clips

import java.time.LocalDate

/** Prompt for one clip, in the style of [com.ledgerai.app.domain.capture.CapturePrompts]. */
object ClipPrompts {
    val system: String = """
        You sort text a user copied on their phone for a personal life-organizer app.
        Reply with ONLY one JSON object:
        {"kind":"note|shopping|task|event|job|memory|place|bill|transaction|debt|other",
         "title":"2-6 words",
         "tags":["..."],
         "split":[ "...", "..." ],
         "fields":{"date":null,"time":null,"place":null,"amount":null,"currency":null,
                   "company":null,"role":null,"person":null,"item":null},
         "confidence":"high|low"}
        Rules:
        - Never guess. A date, amount, name or place that is not written in the text is null.
        - "person" comes only from the text, never invented.
        - Dates are YYYY-MM-DD, times HH:mm.
        - "split" lists the pieces only when the text holds several unrelated items, else [].
    """.trimIndent()

    fun user(text: String, sourceApp: String, today: LocalDate): String = buildString {
        append("Source app: ").append(sourceApp.ifBlank { "Unknown app" }).append('\n')
        append("Today: ").append(today).append('\n')
        append("Text:\n")
        append(text.take(ClipRules.AI_TEXT))
    }

    /** Up to [ClipFiling.BATCH] clips, one JSON array in the same order. */
    fun batch(items: List<Pair<String, String>>, today: LocalDate): String = buildString {
        append("Today: ").append(today).append('\n')
        append("Reply with ONLY a JSON array of ").append(items.size)
        append(" objects, one per text, in this order. Same fields as a single clip.\n")
        items.forEachIndexed { i, (label, text) ->
            append("\n# ").append(i + 1).append(" from ").append(label.ifBlank { "Unknown app" }).append('\n')
            append(text.take(ClipRules.AI_TEXT)).append('\n')
        }
    }
}
