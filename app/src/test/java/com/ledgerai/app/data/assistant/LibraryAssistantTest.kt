package com.ledgerai.app.data.assistant

import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.AssistantArea
import com.ledgerai.app.domain.assistant.Risk
import com.ledgerai.app.domain.assistant.StepState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAudit : AuditSink {
    val rows = mutableMapOf<Pair<String, Int>, ActionResult>()
    override suspend fun find(inputRef: String, index: Int) = rows[inputRef to index]
    override suspend fun record(inputRef: String, result: ActionResult) { rows[inputRef to result.index] = result }
}

class LibraryAssistantTest {
    private val pantry = mutableListOf<String>()
    private val people = mutableListOf("p1")
    private var seenUser: Any? = null

    private val registry = ActionRegistry(
        listOf(
            ActionSpec(
                "lib.pantry.add", Risk.LOW, "Add a pantry item.", "name",
                validate = { if (it["name"] == null) "Missing name" else null },
                apply = { a ->
                    seenUser = a["userId"]
                    pantry += a["name"] as String
                    ActionOutcome("Added ${a["name"]}") { pantry -= a["name"] as String }
                },
            ),
            ActionSpec(
                "lib.person.delete", Risk.MEDIUM, "Remove a person.", "id",
                validate = { null },
                apply = { a -> people -= a["id"] as String; ActionOutcome("Removed Ana") },
                preview = { a -> if (a["id"] in people) "Delete Ana" else error("That person is gone") },
            ),
            ActionSpec(
                "lib.job.add", Risk.LOW, "Track a job.", "title",
                validate = { null }, apply = { ActionOutcome("job") },
            ),
        ),
    )

    private fun assistant(reply: String, user: String = "u1") = LibraryAssistant(
        Orchestrator({ _, _ -> Result.success(reply) }, registry),
        Executor(registry, FakeAudit()),
        registry,
        object : AssistantFacts {
            override suspend fun build(areas: Set<AssistantArea>, userId: String, keywords: List<String>) = "ctx"
        },
        { user },
    )

    private val pantryOnly = setOf(AssistantArea.PANTRY)
    private val everything = AssistantArea.ALL

    @Test
    fun aQuestionComesBackAsAnAnswerWithNoSteps() = runBlocking {
        val turn = assistant("""{"actions":[],"reply":"You have 2 eggs."}""").ask("how many eggs", everything).getOrThrow()
        assertTrue(turn.steps.isEmpty())
        assertEquals("You have 2 eggs.", turn.reply)
    }

    @Test
    fun lowRiskAppliesNowAndUndoRestores() = runBlocking {
        val a = assistant("""{"actions":[{"type":"lib.pantry.add","args":{"name":"milk"}}],"reply":"ok"}""")
        val turn = a.ask("add milk", pantryOnly).getOrThrow()
        assertEquals(StepState.APPLIED, turn.steps.single().state)
        assertEquals(listOf("milk"), pantry)
        val undone = a.undo(turn, 0)
        assertEquals(StepState.UNDONE, undone.steps.single().state)
        assertTrue(pantry.isEmpty())
    }

    @Test
    fun mediumRiskWaitsForATapAndSkipLeavesDataAlone() = runBlocking {
        val a = assistant("""{"actions":[{"type":"lib.person.delete","args":{"id":"p1"},"risk":"low"}]}""")
        val turn = a.ask("delete Ana", everything).getOrThrow()
        val step = turn.steps.single()
        assertEquals(StepState.PENDING, step.state)
        assertEquals("Delete Ana", step.label)
        assertEquals(listOf("p1"), people)
        assertEquals(StepState.SKIPPED, a.skip(turn, 0).steps.single().state)
        assertEquals(listOf("p1"), people)
        assertEquals(StepState.APPLIED, a.approve(turn, 0).steps.single().state)
        assertTrue(people.isEmpty())
    }

    @Test
    fun aDeleteOfAMissingRecordIsRejectedNotOfferedForAConfirm() = runBlocking {
        val turn = assistant("""{"actions":[{"type":"lib.person.delete","args":{"id":"nope"}}]}""")
            .ask("delete", everything).getOrThrow()
        assertEquals(StepState.REJECTED, turn.steps.single().state)
        assertEquals("That person is gone", turn.steps.single().message)
    }

    @Test
    fun actionsOutsideTheSectionAreDroppedAndTheRealUserIsStamped() = runBlocking {
        val json = """{"actions":[
            {"type":"lib.job.add","args":{"title":"x"}},
            {"type":"lib.pantry.add","args":{"name":"rice","userId":"someone-else"}}]}"""
        val turn = assistant(json).ask("stuff", pantryOnly).getOrThrow()
        assertEquals(listOf("lib.pantry.add"), turn.steps.map { it.type })
        assertEquals("u1", seenUser)
        assertTrue(turn.unknowns.isNotEmpty())
    }

    @Test
    fun signedOutIsAUserErrorAndNothingRuns() = runBlocking {
        val r = assistant("""{"actions":[{"type":"lib.pantry.add","args":{"name":"milk"}}]}""", user = "")
            .ask("milk", pantryOnly)
        assertTrue(r.exceptionOrNull() is AssistantUserError)
        assertTrue(pantry.isEmpty())
    }

    @Test
    fun invalidArgsAreRejectedByTheExecutor() = runBlocking {
        val turn = assistant("""{"actions":[{"type":"lib.pantry.add","args":{}}]}""").ask("x", pantryOnly).getOrThrow()
        assertEquals(StepState.REJECTED, turn.steps.single().state)
        assertTrue(pantry.isEmpty())
    }
}
