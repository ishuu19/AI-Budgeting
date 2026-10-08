package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.ActivityEntryDao
import com.ledgerai.app.data.local.room.ActivityEntryEntity
import com.ledgerai.app.data.local.room.CheckinWindowDao
import com.ledgerai.app.data.local.room.CheckinWindowEntity
import com.ledgerai.app.domain.model.ActivityEntrySource
import com.ledgerai.app.domain.model.CheckinWindowState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LifeLogRepository @Inject constructor(
    private val checkinDao: CheckinWindowDao,
    private val entryDao: ActivityEntryDao
) {
    fun observeDay(date: LocalDate): Flow<List<CheckinWindowEntity>> {
        val start = date.atStartOfDay()
        val end = date.plusDays(1).atStartOfDay()
        return checkinDao.observeDay(start, end)
    }

    suspend fun ensureWindowsForDay(date: LocalDate, wakeStart: LocalTime = LocalTime.of(7, 0), wakeEnd: LocalTime = LocalTime.of(23, 0)) {
        var cursor = LocalDateTime.of(date, wakeStart)
        val end = LocalDateTime.of(date, wakeEnd)
        while (cursor.isBefore(end)) {
            val windowEnd = cursor.plusHours(3)
            val cappedEnd = if (windowEnd.isAfter(end)) end else windowEnd
            if (checkinDao.countWindow(cursor, cappedEnd) == 0) {
                checkinDao.insert(
                    CheckinWindowEntity(startAt = cursor, endAt = cappedEnd, state = CheckinWindowState.PENDING)
                )
            }
            cursor = cappedEnd
        }
    }

    suspend fun markExpiredGaps(now: LocalDateTime = LocalDateTime.now()) {
        checkinDao.markExpiredGaps(now)
    }

    suspend fun answerWindow(windowId: Long, text: String, start: LocalDateTime, end: LocalDateTime) {
        entryDao.insert(
            ActivityEntryEntity(
                startAt = start,
                endAt = end,
                text = text,
                source = ActivityEntrySource.MANUAL
            )
        )
        checkinDao.updateState(windowId, CheckinWindowState.ANSWERED.name)
    }

    suspend fun markGaps() {
        // Windows still pending at day end become gaps (called by worker).
    }
}
