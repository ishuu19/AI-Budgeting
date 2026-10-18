package com.ledgerai.app.domain.notebook

import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookVoiceTest {

    @Test
    fun saveTakesTheRest() {
        assertEquals(NotebookCommand.Save("the hours"), NotebookVoice.parse("Save the hours"))
    }

    @Test
    fun askSummarizeAndTranslatePickAPrompt() {
        assertEquals(NotebookCommand.Prompt("Ask", "what time"), NotebookVoice.parse("Ask what time"))
        assertEquals(NotebookCommand.Prompt("Summarize", "this"), NotebookVoice.parse("summarize this"))
        assertEquals(NotebookCommand.Prompt("Translate", "the menu"), NotebookVoice.parse("Translate the menu"))
        assertEquals(NotebookCommand.Prompt("Explain", "the fee"), NotebookVoice.parse("Explain the fee"))
        assertEquals(NotebookCommand.Prompt("Reply", "to Sam"), NotebookVoice.parse("Reply to Sam"))
        assertEquals(NotebookCommand.Prompt("To-do", "buy milk"), NotebookVoice.parse("to-do buy milk"))
    }

    @Test
    fun pinReadAndSelectAll() {
        assertEquals(NotebookCommand.Pin, NotebookVoice.parse("Pin that"))
        assertEquals(NotebookCommand.Read, NotebookVoice.parse("read it"))
        assertEquals(NotebookCommand.SelectAll, NotebookVoice.parse("Select all"))
    }

    @Test
    fun otherSentencesStayInTheBox() {
        assertEquals(
            NotebookCommand.Text("The park opens at nine"),
            NotebookVoice.parse("The park opens at nine"),
        )
    }

    @Test
    fun readAloudUsesSelectionThenTheAnswer() {
        assertEquals("¥500", NotebookVoice.speak("¥500", "Open 9 to 5"))
        assertEquals("Open 9 to 5", NotebookVoice.speak("  ", "Open 9 to 5"))
    }
}
