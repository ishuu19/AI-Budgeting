package com.ledgerai.app.data.sync

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class UniformKeysInterceptorTest {
    private fun groups(json: String) =
        UniformKeysInterceptor.groupByKeys(JsonParser.parseString(json).asJsonArray).map { g -> g.map { it.toString() } }

    @Test
    fun rowsWithTheSameKeysStayOneBatch() {
        assertEquals(1, groups("""[{"id":1,"a":2},{"a":3,"id":4}]""").size)
    }

    @Test
    fun rowsWithDifferentKeysSplitInFirstSeenOrder() {
        val g = groups("""[{"id":1,"a":2},{"id":2},{"id":3,"a":9}]""")
        assertEquals(2, g.size)
        assertEquals(2, g[0].size)
        assertEquals(listOf("""{"id":2}"""), g[1])
    }

    @Test
    fun anythingThatIsNotAnObjectIsLeftWhole() {
        assertEquals(1, groups("""[1,2]""").size)
    }
}
