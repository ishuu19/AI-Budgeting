package com.ledgerai.app.data.assistant

import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.Risk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestratorTest {
    private val registry = ActionRegistry(
        listOf(
            ActionSpec(
                "note.add", Risk.LOW, "Save a note", "text:string",
                validate = { null }, apply = { ActionOutcome("ok") },
            ),
        ),
    )

    private fun orchestrator(reply: Result<String>) = Orchestrator({ _, _ -> reply }, registry)

    @Test
    fun parsesPlanFromFencedJson() = runBlocking {
        val json = """```json
            {"actions":[{"type":"note.add","args":{"text":"toothpaste"},"risk":"low"}],"unknowns":[],"reply":"Noted."}
            ```"""
        val plan = orchestrator(Result.success(json)).plan("voice:1", "note toothpaste", nowIso = "2026-10-11").getOrThrow()
        assertEquals("note.add", plan.actions.single().type)
        assertEquals("toothpaste", plan.actions.single().args["text"])
        assertEquals("Noted.", plan.reply)
    }

    @Test
    fun garbageIsAFailureNotAPlan() = runBlocking {
        assertTrue(orchestrator(Result.success("sure!")).plan("v", "x", nowIso = "n").isFailure)
        assertTrue(orchestrator(Result.failure(RuntimeException("offline"))).plan("v", "x", nowIso = "n").isFailure)
    }

    @Test
    fun promptListsRegistryAndFencesUserText() {
        val o = orchestrator(Result.success("{}"))
        assertTrue(o.systemPrompt("now").contains("note.add(text:string): Save a note"))
        assertTrue(o.userPrompt("hi", "ctx").contains("<user_message>\nhi"))
    }

    @Test
    fun promptOnlyListsAllowedActionsAndAddsAnswerRulesOnRequest() {
        val two = ActionRegistry(
            listOf(
                ActionSpec("lib.a.add", Risk.LOW, "A", "x", validate = { null }, apply = { ActionOutcome("ok") }),
                ActionSpec("lib.b.add", Risk.LOW, "B", "x", validate = { null }, apply = { ActionOutcome("ok") }),
            ),
        )
        val o = Orchestrator({ _, _ -> Result.success("{}") }, two)
        val prompt = o.systemPrompt("now", allowed = { it.type.startsWith("lib.a.") }, answers = true)
        assertTrue(prompt.contains("lib.a.add"))
        assertTrue(!prompt.contains("lib.b.add"))
        assertTrue(prompt.contains("using ONLY <context>"))
        assertTrue(!o.systemPrompt("now").contains("using ONLY <context>"))
    }

    @Test
    fun anAnswerWithoutActionsOrWithoutJsonIsStillAnAnswer() = runBlocking {
        val noActions = orchestrator(Result.success("""{"reply":"Sam's birthday is 14 March."}"""))
            .plan("v", "when", nowIso = "n", answers = true).getOrThrow()
        assertTrue(noActions.actions.isEmpty())
        assertEquals("Sam's birthday is 14 March.", noActions.reply)
        val plain = orchestrator(Result.success("You have 12 notes."))
            .plan("v", "how many notes", nowIso = "n", answers = true).getOrThrow()
        assertEquals("You have 12 notes.", plain.reply)
        assertTrue(orchestrator(Result.success("You have 12 notes.")).plan("v", "x", nowIso = "n").isFailure)
        assertTrue(orchestrator(Result.success("{}")).plan("v", "x", nowIso = "n", answers = true).isFailure)
    }

    @Test
    fun parserIgnoresActionsWithoutType() {
        val plan = PlanParser.parse("v", """{"actions":[{"args":{}},{"type":"note.add"}]}""")!!
        assertEquals(1, plan.actions.size)
        assertNull(PlanParser.parse("v", "[]"))
    }
}
