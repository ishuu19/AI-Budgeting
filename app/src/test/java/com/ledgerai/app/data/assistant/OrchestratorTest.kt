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
    fun parserIgnoresActionsWithoutType() {
        val plan = PlanParser.parse("v", """{"actions":[{"args":{}},{"type":"note.add"}]}""")!!
        assertEquals(1, plan.actions.size)
        assertNull(PlanParser.parse("v", "[]"))
    }
}
