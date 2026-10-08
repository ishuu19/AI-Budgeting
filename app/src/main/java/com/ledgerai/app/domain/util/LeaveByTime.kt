package com.ledgerai.app.domain.util

import java.time.LocalDateTime

/** Leave time = event start − travel − buffer. */
object LeaveByTime {
    fun computeLeaveAt(
        startAt: LocalDateTime,
        travelMinutes: Int,
        bufferMinutes: Int
    ): LocalDateTime =
        startAt.minusMinutes((travelMinutes + bufferMinutes).toLong())
}
