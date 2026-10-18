package com.ledgerai.app.domain.notebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Twenty cases. A price that is not in the source is rejected. */
class NotebookJsonTest {

    private val park = "Shinjuku Gyoen opens 9:00. Closed Mondays."

    @Test
    fun goldenCases() {
        val cases = listOf(
            Case("two word title", park, "summarize", """{"title":"Park hours","answer":"Opens 9:00.","actions":[]}""", true),
            Case("five word title", park, "summarize", """{"title":"Hours for the city park","answer":"Opens 9:00.","actions":[]}""", true),
            Case("one word title", park, "summarize", """{"title":"Hours","answer":"Opens 9:00.","actions":[]}""", false),
            Case("six word title", park, "summarize", """{"title":"The park hours are listed here","answer":"Opens 9:00.","actions":[]}""", false),
            Case("price asked and unknown", park, "what's the price?", """{"title":"No price listed","answer":"I don't know.","actions":[]}""", true),
            Case("invented yen", park, "what's the price?", """{"title":"Entry price","answer":"It costs ¥500.","actions":[]}""", false),
            Case("invented dollars", park, "how much", """{"title":"Entry price","answer":"It is 40 dollars.","actions":[]}""", false),
            Case("price that is written", "Entry ¥500.", "what's the price?", """{"title":"Entry fee","answer":"Entry is ¥500.","actions":[]}""", true),
            Case("time that is written", park, "summarize", """{"title":"Opening time","answer":"Opens at 9:00.","actions":[]}""", true),
            Case("invented date", park, "summarize", """{"title":"Opening date","answer":"It opens on 2020-01-01.","actions":[]}""", false),
            Case("empty actions", park, "summarize", """{"title":"Park hours","answer":"Closed Mondays.","actions":[]}""", true),
            Case(
                "action is stored inferred",
                park,
                "add this as an event",
                """{"title":"Park visit","answer":"Closed Mondays.","actions":[{"type":"clip.file_event","args":{"title":"Park"},"confirmed":true}]}""",
                true,
            ),
            Case("malformed", park, "summarize", "not json", false),
            Case("missing answer", park, "summarize", """{"title":"Park hours","actions":[]}""", false),
            Case("blank answer", park, "summarize", """{"title":"Park hours","answer":"  ","actions":[]}""", false),
            Case("fenced json", park, "summarize", "```json\n{\"title\":\"Park hours\",\"answer\":\"Opens 9:00.\",\"actions\":[]}\n```", true),
            Case("unknown plus invented price", park, "what's the price?", """{"title":"No price listed","answer":"I don't know. It costs ¥500.","actions":[]}""", false),
            Case("prose around json", park, "summarize", "Sure {\"title\":\"Park hours\",\"answer\":\"Opens 9:00.\",\"actions\":[]}", true),
            Case("no price phrase", park, "what's the price?", """{"title":"No price listed","answer":"No price is shown.","actions":[]}""", true),
            Case(
                "invented amount inside an action",
                park,
                "what's the price?",
                """{"title":"No price listed","answer":"I don't know.","actions":[{"type":"clip.file_event","args":{"amount":500}}]}""",
                false,
            ),
        )
        assertEquals(20, cases.size)
        cases.forEach { case ->
            val parsed = NotebookJson.accept(case.source, case.raw, case.question)
            if (case.ok) {
                assertNotNull(case.name, parsed)
            } else {
                assertNull(case.name, parsed)
            }
        }
    }

    @Test
    fun storedActionsAreInferredAndNotAppliedUntilATap() {
        val raw = """{"title":"Park visit","answer":"Closed Mondays.","actions":[{"type":"clip.file_event","args":{"title":"Park"},"confirmed":true}]}"""
        val parsed = NotebookJson.accept(park, raw, "add this as an event")
        val stored = NotebookJson.store(parsed!!)
        assertTrue(stored.contains("\"inferred\":true"))
        assertFalse(stored.contains("confirmed"))
        assertFalse(NotebookActions.applyNow(userTapped = false))
        assertTrue(NotebookActions.applyNow(userTapped = true))
    }

    @Test
    fun nothingIsSentBeyondTheCapsAndLastSixMessages() {
        val selection = "a".repeat(2_500)
        val own = "b".repeat(2_500)
        val history = (1..8).map { "m$it" to "user" }
        val payload = NotebookPrompts.userPayload("Summarize", selection, own, history)
        assertFalse(payload.contains("a".repeat(2_001)))
        assertFalse(payload.contains("b".repeat(2_001)))
        assertFalse(payload.contains("m1"))
        assertFalse(payload.contains("m2"))
        assertTrue(payload.contains("m3"))
        assertTrue(payload.contains("Summarize"))
        assertTrue(NotebookPrompts.SYSTEM.contains("say you don't know"))
    }

    private data class Case(val name: String, val source: String, val question: String, val raw: String, val ok: Boolean)
}
