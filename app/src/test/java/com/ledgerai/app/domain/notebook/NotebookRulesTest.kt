package com.ledgerai.app.domain.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotebookRulesTest {

    @Test
    fun saveJoinsPicksAndTypedTextAsOneScreenEntry() {
        val body = NotebookEntry.body(listOf("Opens 9:00", "¥500"), "Bring cash")
        assertEquals("Opens 9:00\n¥500\nBring cash", body)
        assertEquals("screen", NotebookEntry.sourceKind(hasScreen = true, fromOcr = false, fromVoice = true, fromClipboard = false))
        assertEquals("ocr", NotebookEntry.sourceKind(hasScreen = true, fromOcr = true, fromVoice = false, fromClipboard = false))
        assertEquals("typed", NotebookEntry.sourceKind(hasScreen = false, fromOcr = false, fromVoice = false, fromClipboard = false))
        assertEquals("voice", NotebookEntry.sourceKind(hasScreen = false, fromOcr = false, fromVoice = true, fromClipboard = false))
    }

    @Test
    fun editsDoNotReplaceTheOriginalAndPinsSkipRetention() {
        assertEquals("edited", NotebookEntry.shown("original", "edited"))
        assertEquals("original", NotebookEntry.shown("original", null))
        assertEquals("original", NotebookEntry.shown("original", "  "))
        assertFalse(NotebookEntry.inboxExpires(pinned = true, noteId = null, filedId = null, capturedAt = 1, before = 9))
        assertTrue(NotebookEntry.inboxExpires(pinned = false, noteId = null, filedId = null, capturedAt = 1, before = 9))
        assertFalse(NotebookEntry.inboxExpires(pinned = false, noteId = 4, filedId = null, capturedAt = 1, before = 9))
    }

    @Test
    fun sensitiveMessagesStayOnThePhone() {
        assertFalse(NotebookEntry.syncMessage(listOf("clip-1"), "hello", setOf("clip-1")))
        assertTrue(NotebookEntry.syncMessage(listOf("clip-2"), "hello", setOf("clip-1")))
        assertFalse(NotebookEntry.syncMessage(emptyList(), "code 123456 is your otp", emptySet()))
    }

    @Test
    fun layoutsMatchWindowSizeBreakpoints() {
        assertEquals(NotebookWidth.Compact, NotebookEntry.widthOf(400))
        assertEquals(NotebookWidth.Medium, NotebookEntry.widthOf(700))
        assertEquals(NotebookWidth.Expanded, NotebookEntry.widthOf(840))
        assertEquals(2, NotebookEntry.columns(NotebookWidth.Compact))
        assertEquals(3, NotebookEntry.columns(NotebookWidth.Medium))
        assertEquals(4, NotebookEntry.columns(NotebookWidth.Expanded))
    }

    @Test
    fun statusLinesStayUnderSixWords() {
        assertEquals("Waiting for network", NotebookEntry.status("queued", online = false))
        assertEquals("Saved · Undo", NotebookEntry.SAVED)
        assertEquals("Can't read this screen", NotebookEntry.CANT_READ)
        listOf(NotebookEntry.SAVED, NotebookEntry.CANT_READ, NotebookEntry.status("queued", false)!!).forEach { line ->
            assertTrue(line, line.split(Regex("\\s+")).size < 6)
        }
    }

    @Test
    fun pinnedOrderAndPromptList() {
        assertEquals(listOf("a", "c", "b"), NotebookEntry.shift(listOf("a", "b", "c"), "c", steps = -1))
        assertEquals(NotebookPrompts.BUILTIN, NotebookPrompts.decode(null))
        assertEquals(listOf("Summarize", "Mine"), NotebookPrompts.decode(NotebookPrompts.encode(listOf("Summarize", "Mine"))))
    }

    @Test
    fun plannedSqlKeepsChatsPrivateToTheOwner() {
        val file = listOf(
            File("supabase/planned/migrations/021_notebook.sql"),
            File("../supabase/planned/migrations/021_notebook.sql"),
        ).first { it.exists() }
        val sql = file.readText()
        assertTrue(sql.contains("create policy chat_threads_owner"))
        assertTrue(sql.contains("create policy chat_messages_owner"))
        assertTrue(sql.contains("user_id = auth.uid()"))
        assertTrue(sql.contains("enable row level security"))
        assertFalse(sql.contains("household"))
    }

    @Test
    fun chatRetentionKeepsPinnedThreads() {
        assertTrue(NotebookEntry.keepThread(hasPinned = true, ageDays = 400, limitDays = 30))
        assertFalse(NotebookEntry.keepThread(hasPinned = false, ageDays = 40, limitDays = 30))
        assertTrue(NotebookEntry.keepThread(hasPinned = false, ageDays = 400, limitDays = null))
    }
}
