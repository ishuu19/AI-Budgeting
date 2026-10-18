package com.ledgerai.app.domain.notebook

enum class NotebookWidth { Compact, Medium, Expanded }

object NotebookPrompts {
    val BUILTIN = listOf("Summarize", "Explain", "To-do", "Translate", "Reply")
    const val SELECTION_CAP = 2_000
    const val OWN_CAP = 2_000
    const val HISTORY = 6
    const val SYSTEM =
        "Use only the given text. If something is not in it, say you don't know. " +
            "Reply as JSON: {\"title\":\"2-5 words\",\"answer\":\"text\",\"actions\":[]}. " +
            "actions may be empty. Each action is {\"type\":\"clip.file_note|clip.file_task|clip.file_event\",\"args\":{}}. " +
            "Do not invent a price, date, or amount."

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return BUILTIN
        val list = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { BUILTIN }
    }

    fun encode(list: List<String>): String = list.joinToString("\n")

    fun userPayload(
        prompt: String?,
        selection: String,
        own: String,
        history: List<Pair<String, String>>,
    ): String = buildString {
        if (!prompt.isNullOrBlank()) append("Prompt: ").append(prompt.trim()).append('\n')
        append("Selected:\n").append(selection.take(SELECTION_CAP)).append('\n')
        append("Note:\n").append(own.take(OWN_CAP)).append('\n')
        history.takeLast(HISTORY).forEach { (text, role) ->
            append(role).append(": ").append(text).append('\n')
        }
    }

    fun prefix(prompt: String?): String = if (prompt.isNullOrBlank()) "" else "$prompt ▸ "
}

object NotebookEntry {
    const val SAVED = "Saved · Undo"
    const val CANT_READ = "Can't read this screen"
    const val WHOLE = "Use whole screen"

    fun body(picks: List<String>, own: String): String =
        (picks.map { it.trim() }.filter { it.isNotEmpty() } + own.trim().takeIf { it.isNotEmpty() }.orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    fun sourceKind(hasScreen: Boolean, fromOcr: Boolean, fromVoice: Boolean, fromClipboard: Boolean): String = when {
        hasScreen && fromOcr -> "ocr"
        hasScreen -> "screen"
        fromVoice -> "voice"
        fromClipboard -> "clipboard"
        else -> "typed"
    }

    fun shown(original: String, edited: String?): String =
        edited?.trim()?.takeIf { it.isNotEmpty() } ?: original

    /** True when an unpinned inbox clip with no note and no filed record is past the cutoff. */
    fun inboxExpires(pinned: Boolean, noteId: Long?, filedId: String?, capturedAt: Long, before: Long): Boolean =
        !pinned && noteId == null && filedId.isNullOrBlank() && capturedAt < before

    fun syncMessage(clipIds: List<String>, text: String, sensitiveClipIds: Set<String>): Boolean {
        if (com.ledgerai.app.domain.clips.ClipRules.isSensitiveText(text)) return false
        return clipIds.none { it in sensitiveClipIds }
    }

    fun widthOf(dp: Int): NotebookWidth = when {
        dp < 600 -> NotebookWidth.Compact
        dp < 840 -> NotebookWidth.Medium
        else -> NotebookWidth.Expanded
    }

    fun columns(width: NotebookWidth): Int = when (width) {
        NotebookWidth.Compact -> 2
        NotebookWidth.Medium -> 3
        NotebookWidth.Expanded -> 4
    }

    fun status(state: String, online: Boolean): String? = when {
        state == "queued" && !online -> "Waiting for network"
        state == "failed" -> "Failed"
        else -> null
    }

    fun shift(ids: List<String>, id: String, steps: Int): List<String> {
        if (steps == 0) return ids
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val to = (from + steps).coerceIn(0, ids.lastIndex)
        if (to == from) return ids
        val next = ids.toMutableList()
        next.add(to, next.removeAt(from))
        return next
    }

    /** Forever is a null limit. A pinned answer keeps the thread. */
    fun keepThread(hasPinned: Boolean, ageDays: Long, limitDays: Long?): Boolean =
        hasPinned || limitDays == null || ageDays < limitDays

    fun fallbackTitle(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(4)
        val title = if (words.size >= 2) words.joinToString(" ") else "${words.firstOrNull() ?: "New"} note"
        return title.take(120)
    }

    fun sourceChip(app: String, time: String): String = "${app.ifBlank { "Screen" }} · $time".take(200)
}
