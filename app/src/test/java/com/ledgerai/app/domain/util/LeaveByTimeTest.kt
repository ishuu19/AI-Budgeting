package com.ledgerai.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LeaveByTimeTest {

    @Test
    fun computeLeaveAt_subtractsTravelAndBuffer() {
        val start = LocalDateTime.of(2026, 10, 8, 9, 0)
        val leave = LeaveByTime.computeLeaveAt(start, travelMinutes = 15, bufferMinutes = 5)
        assertEquals(LocalDateTime.of(2026, 10, 8, 8, 40), leave)
    }
}
