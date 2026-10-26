package com.ledgerai.app.data.chat

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A line that should appear in the home chat, from the chat itself or from a widget.
 * Lines wait here until the chat is on screen, so a widget save is not lost.
 */
@Singleton
class HomeChatNotice @Inject constructor() {
    private val waiting = ArrayDeque<String>()
    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    fun post(line: String) {
        val text = line.trim()
        if (text.isEmpty()) return
        synchronized(waiting) { waiting.addLast(text) }
        _lines.tryEmit(text)
    }

    fun take(): List<String> = synchronized(waiting) {
        if (waiting.isEmpty()) emptyList()
        else waiting.toList().also { waiting.clear() }
    }
}
