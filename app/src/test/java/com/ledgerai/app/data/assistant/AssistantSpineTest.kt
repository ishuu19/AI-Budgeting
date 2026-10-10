package com.ledgerai.app.data.assistant

import com.google.gson.Gson
import com.ledgerai.app.data.local.room.AssistantActionEntity
import com.ledgerai.app.data.local.room.AssistantAuditDao
import com.ledgerai.app.data.local.room.EventLogEntity
import com.ledgerai.app.domain.assistant.ActionOutcome
import com.ledgerai.app.domain.assistant.ActionRegistry
import com.ledgerai.app.domain.assistant.ActionSpec
import com.ledgerai.app.domain.assistant.Risk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parse, then execute. Applied is reported only by [Executor], and [event_log] is written only then.
 * The voice screen still uses its confirm cards; this is the spine those cards can call later.
 */
class AssistantSpineTest {
    private val dao = FakeAuditDao()
    private val sink = RoomAuditSink(dao, Gson()) { 1_700_000_000_000L }
    private val registry = ActionRegistry(
        listOf(
            ActionSpec(
                type = "note.add",
                risk = Risk.LOW,
                validate = { if ((it["text"] as? String).isNullOrBlank()) "text required" else null },
                apply = { ActionOutcome("Added note") },
            ),
            ActionSpec(
                type = "money.record",
                risk = Risk.MEDIUM,
                validate = { null },
                apply = { ActionOutcome("Recorded") },
            ),
        ),
    )
    private val executor = Executor(registry, sink)

    @Test
    fun lowRiskIsAppliedAndLoggedOnlyAfterExecutor() = runBlocking {
        val plan = PlanParser.parse(
            "voice:1",
            """{"actions":[{"type":"note.add","args":{"text":"toothpaste"},"risk":"low"}],"reply":"I can add toothpaste."}""",
        )!!
        assertEquals("I can add toothpaste.", plan.reply)
        assertNull(sink.find("voice:1", 0))
        assertTrue(dao.events.isEmpty())

        val result = executor.execute(plan).single()

        assertEquals(ActionStatus.APPLIED, result.status)
        assertEquals("Added note", result.message)
        val stored = sink.find("voice:1", 0)
        assertEquals(ActionStatus.APPLIED, stored?.status)
        assertEquals("Added note", stored?.message)
        assertEquals("applied", dao.actions.single().status)
        assertEquals(1_700_000_000_000L, dao.actions.single().appliedAt)
        assertEquals(1, dao.events.size)
        assertEquals("note.add", dao.events.single().type)
        assertEquals(dao.actions.single().id, dao.events.single().sourceActionId)
    }

    @Test
    fun mediumStaysProposedUntilApprovedAndLogsOnce() = runBlocking {
        val plan = PlanParser.parse(
            "voice:2",
            """{"actions":[{"type":"money.record","risk":"low"}],"reply":"Record it?"}""",
        )!!
        val waiting = executor.execute(plan).single()
        assertEquals(ActionStatus.NEEDS_APPROVAL, waiting.status)
        assertEquals(Risk.MEDIUM, waiting.risk)
        assertEquals("proposed", dao.actions.single().status)
        assertTrue(dao.events.isEmpty())

        val applied = executor.execute(plan, approved = setOf(0)).single()
        assertEquals(ActionStatus.APPLIED, applied.status)
        assertEquals(1, dao.events.size)

        executor.execute(plan, approved = setOf(0))
        assertEquals(1, dao.actions.size)
        assertEquals(1, dao.events.size)
    }
}

private class FakeAuditDao : AssistantAuditDao {
    val actions = mutableListOf<AssistantActionEntity>()
    val events = mutableListOf<EventLogEntity>()
    private var nextAction = 1L
    private var nextEvent = 1L

    override suspend fun findAction(inputRef: String, index: Int): AssistantActionEntity? =
        actions.find { it.inputRef == inputRef && it.actionIndex == index && it.deletedAt == null }

    override suspend fun insertAction(entity: AssistantActionEntity): Long {
        val id = nextAction++
        actions += entity.copy(id = id)
        return id
    }

    override suspend fun updateAction(entity: AssistantActionEntity) {
        val index = actions.indexOfFirst { it.id == entity.id }
        actions[index] = entity
    }

    override suspend fun insertEvent(entity: EventLogEntity): Long {
        val id = nextEvent++
        events += entity.copy(id = id)
        return id
    }

    override suspend fun findEventByAction(actionId: Long): EventLogEntity? =
        events.find { it.sourceActionId == actionId && it.deletedAt == null }
}
