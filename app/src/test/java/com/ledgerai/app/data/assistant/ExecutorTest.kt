package com.ledgerai.app.data.assistant

import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.assistant.Risk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class MemoryAudit : AuditSink {
    val rows = mutableMapOf<Pair<String, Int>, ActionResult>()
    override suspend fun find(inputRef: String, index: Int) = rows[inputRef to index]
    override suspend fun record(inputRef: String, result: ActionResult) {
        rows[inputRef to result.index] = result
    }
}

class ExecutorTest {
    private val applied = mutableListOf<String>()

    private val registry = ActionRegistry(
        listOf(
            ActionSpec(
                type = "note.add",
                risk = Risk.LOW,
                validate = { if ((it["text"] as? String).isNullOrBlank()) "text required" else null },
                apply = { args ->
                    val text = args["text"] as String
                    applied += text
                    ActionOutcome("Added note", undo = { applied -= text })
                },
            ),
            ActionSpec(
                type = "money.record",
                risk = Risk.MEDIUM,
                validate = { null },
                apply = {
                    applied += "money"
                    ActionOutcome("Recorded")
                },
            ),
            ActionSpec(
                type = "boom",
                risk = Risk.LOW,
                validate = { null },
                apply = { error("db down") },
            ),
        ),
    )
    private val audit = MemoryAudit()
    private val executor = Executor(registry, audit)

    private fun plan(vararg a: ProposedAction) = ActionPlan("voice:1", a.toList())

    @Test
    fun lowRiskAppliesImmediatelyAndCanUndo() = runBlocking {
        val r = executor.execute(plan(ProposedAction("note.add", mapOf("text" to "toothpaste")))).single()
        assertEquals(ActionStatus.APPLIED, r.status)
        assertEquals(listOf("toothpaste"), applied)
        r.undo!!.invoke()
        assertEquals(emptyList<String>(), applied)
    }

    @Test
    fun modelClaimedRiskIsIgnored() = runBlocking {
        val r = executor.execute(plan(ProposedAction("money.record", risk = Risk.LOW))).single()
        assertEquals(ActionStatus.NEEDS_APPROVAL, r.status)
        assertEquals(Risk.MEDIUM, r.risk)
        assertEquals(emptyList<String>(), applied)
    }

    @Test
    fun mediumAppliesOnceApproved() = runBlocking {
        val p = plan(ProposedAction("money.record"))
        executor.execute(p)
        val r = executor.execute(p, approved = setOf(0)).single()
        assertEquals(ActionStatus.APPLIED, r.status)
        assertEquals(listOf("money"), applied)
    }

    @Test
    fun unknownTypeAndBadArgsAreRejected() = runBlocking {
        val r = executor.execute(plan(ProposedAction("nuke.db"), ProposedAction("note.add", mapOf("text" to " "))))
        assertEquals(ActionStatus.REJECTED, r[0].status)
        assertEquals(ActionStatus.REJECTED, r[1].status)
        assertEquals(emptyList<String>(), applied)
    }

    @Test
    fun rerunDoesNotApplyTwice() = runBlocking {
        val p = plan(ProposedAction("note.add", mapOf("text" to "eggs")))
        executor.execute(p)
        executor.execute(p)
        assertEquals(listOf("eggs"), applied)
    }

    @Test
    fun failureIsRecordedNotReportedAsApplied() = runBlocking {
        val r = executor.execute(plan(ProposedAction("boom"))).single()
        assertEquals(ActionStatus.FAILED, r.status)
        assertNotNull(audit.find("voice:1", 0))
        assertNull(r.undo)
    }
}
