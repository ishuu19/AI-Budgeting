package com.ledgerai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionArgsTest {
    @Test
    fun idsSurviveTheDoubleThatTheParserProduces() {
        assertEquals(5L, mapOf("id" to 5.0).argLong("id"))
        assertEquals(7L, mapOf("id" to "7").argLong("id"))
        assertEquals(7L, mapOf("id" to "7.0").argLong("id"))
    }

    @Test
    fun badIdsAreNullNotGuessed() {
        assertNull(mapOf("id" to 5.5).argLong("id"))
        assertNull(mapOf("id" to -1.0).argLong("id"))
        assertNull(mapOf("id" to "abc").argLong("id"))
        assertNull(mapOf<String, Any?>().argLong("id"))
    }

    @Test
    fun blankTextIsMissing() {
        assertNull(mapOf("n" to "  ").argStr("n"))
        assertEquals("milk", mapOf("n" to " milk ").argStr("n"))
    }

    @Test
    fun datesAndTimesNeedTheRightShape() {
        assertEquals("2026-10-23", mapOf("d" to "2026-10-23").argDate("d").toString())
        assertNull(mapOf("d" to "23/10/2026").argDate("d"))
        assertEquals("09:30", mapOf("t" to "09:30").argTime("t").toString())
    }

    @Test
    fun listsAcceptRealListsJsonTextAndCommas() {
        assertEquals(listOf("red", "blue"), mapOf("c" to listOf("red", " blue ")).argStrings("c"))
        assertEquals(listOf("red", "blue"), mapOf("c" to "[\"red\",\"blue\"]").argStrings("c"))
        assertEquals(listOf("red", "blue"), mapOf("c" to "red, blue").argStrings("c"))
    }

    @Test
    fun hasTellsClearFromLeaveAlone() {
        assertTrue(mapOf("org" to "").has("org"))
        assertFalse(mapOf("org" to null).has("org"))
        assertFalse(mapOf<String, Any?>().has("org"))
    }

    @Test
    fun actionTypesBelongToAreasByPrefix() {
        assertTrue(actionInAreas("lib.pantry.add", setOf(AssistantArea.PANTRY)))
        assertFalse(actionInAreas("lib.pantry.add", setOf(AssistantArea.PERSON)))
        assertFalse(actionInAreas("lib.person.add", emptySet()))
        assertFalse(actionInAreas("clip.file_note", AssistantArea.ALL))
    }
}
