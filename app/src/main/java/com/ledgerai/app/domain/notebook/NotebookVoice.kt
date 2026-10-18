package com.ledgerai.app.domain.notebook

sealed class NotebookCommand {
    data class Save(val rest: String) : NotebookCommand()
    data class Prompt(val name: String, val rest: String) : NotebookCommand()
    data object Pin : NotebookCommand()
    data object Read : NotebookCommand()
    data object SelectAll : NotebookCommand()
    data class Text(val text: String) : NotebookCommand()
}

object NotebookVoice {
    private val PROMPTS = mapOf(
        "ask" to "Ask",
        "summarize" to "Summarize",
        "summarise" to "Summarize",
        "explain" to "Explain",
        "translate" to "Translate",
        "reply" to "Reply",
        "to-do" to "To-do",
        "todo" to "To-do",
    )

    fun parse(utterance: String): NotebookCommand {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return NotebookCommand.Text("")
        val parts = trimmed.split(Regex("\\s+"))
        val first = parts.first().lowercase()
        val second = parts.getOrNull(1)?.lowercase()
        if (first == "to" && second == "do") {
            return NotebookCommand.Prompt("To-do", parts.drop(2).joinToString(" "))
        }
        PROMPTS[first]?.let { name ->
            return NotebookCommand.Prompt(name, parts.drop(1).joinToString(" "))
        }
        return when (first) {
            "save" -> NotebookCommand.Save(parts.drop(1).joinToString(" "))
            "pin" -> NotebookCommand.Pin
            "read" -> if (second == "it" || second == "this" || second == "that") {
                NotebookCommand.Read
            } else {
                NotebookCommand.Text(trimmed)
            }
            "select" -> if (second == "all") NotebookCommand.SelectAll else NotebookCommand.Text(trimmed)
            else -> NotebookCommand.Text(trimmed)
        }
    }

    /** Selection first. The latest answer is the fallback. */
    fun speak(selection: String?, answer: String?): String =
        selection?.trim()?.takeIf { it.isNotEmpty() } ?: answer?.trim().orEmpty()
}
