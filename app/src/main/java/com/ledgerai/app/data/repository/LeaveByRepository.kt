package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.CalendarEventDao
import com.ledgerai.app.data.local.room.LeaveRuleDao
import com.ledgerai.app.data.local.room.LeaveRuleEntity
import com.ledgerai.app.data.local.room.TaskDao
import com.ledgerai.app.domain.model.LeaveRefType
import com.ledgerai.app.domain.util.LeaveByTime
import com.ledgerai.app.service.LeaveByScheduler
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaveByRepository @Inject constructor(
    private val leaveRuleDao: LeaveRuleDao,
    private val taskDao: TaskDao,
    private val calendarEventDao: CalendarEventDao,
    private val scheduler: LeaveByScheduler
) {

    suspend fun getForRef(refType: LeaveRefType, refId: Long): LeaveRuleEntity? =
        if (refId == 0L) null else leaveRuleDao.getForRef(refType.name, refId)

    suspend fun isEnabled(refType: LeaveRefType, refId: Long): Boolean =
        getForRef(refType, refId)?.enabled == true

    suspend fun setLeaveBy(
        refType: LeaveRefType,
        refId: Long,
        enabled: Boolean,
        placeLabel: String,
        eventStart: LocalDateTime?,
        title: String,
        lat: Double? = null,
        lng: Double? = null,
        travelMinutes: Int = DEFAULT_TRAVEL_MINUTES,
        bufferMinutes: Int = DEFAULT_BUFFER_MINUTES
    ) {
        if (refId == 0L) return
        val existing = leaveRuleDao.getForRef(refType.name, refId)
        if (!enabled || eventStart == null) {
            existing?.let { scheduler.cancel(it.id) }
            leaveRuleDao.deleteForRef(refType.name, refId)
            return
        }
        val start = eventStart
        val entity = LeaveRuleEntity(
            id = existing?.id ?: 0L,
            refType = refType,
            refId = refId,
            placeLabel = placeLabel.trim(),
            lat = lat ?: existing?.lat,
            lng = lng ?: existing?.lng,
            travelMinutes = existing?.travelMinutes ?: travelMinutes,
            bufferMinutes = existing?.bufferMinutes ?: bufferMinutes,
            enabled = true
        )
        val ruleId = if (entity.id == 0L) {
            leaveRuleDao.insert(entity)
        } else {
            leaveRuleDao.insert(entity)
            entity.id
        }
        arm(ruleId, entity.copy(id = ruleId), title, start)
    }

    suspend fun removeForRef(refType: LeaveRefType, refId: Long) {
        val existing = leaveRuleDao.getForRef(refType.name, refId)
        existing?.let { scheduler.cancel(it.id) }
        leaveRuleDao.deleteForRef(refType.name, refId)
    }

    suspend fun rescheduleAllEnabled(): Int {
        val now = LocalDateTime.now()
        var count = 0
        leaveRuleDao.listEnabled().forEach { rule ->
            val start = resolveStartAt(rule) ?: return@forEach
            val title = resolveTitle(rule) ?: return@forEach
            val leaveAt = LeaveByTime.computeLeaveAt(start, rule.travelMinutes, rule.bufferMinutes)
            if (!leaveAt.isAfter(now)) return@forEach
            arm(rule.id, rule, title, start)
            count++
        }
        return count
    }

    private fun arm(ruleId: Long, rule: LeaveRuleEntity, title: String, start: LocalDateTime) {
        scheduler.cancel(ruleId)
        val leaveAt = LeaveByTime.computeLeaveAt(start, rule.travelMinutes, rule.bufferMinutes)
        scheduler.schedule(ruleId, title, rule.placeLabel, leaveAt)
    }

    private suspend fun resolveStartAt(rule: LeaveRuleEntity): LocalDateTime? = when (rule.refType) {
        LeaveRefType.TASK -> taskDao.getById(rule.refId)?.dueAt
        LeaveRefType.CALENDAR_EVENT -> calendarEventDao.getById(rule.refId)?.startAt
    }

    private suspend fun resolveTitle(rule: LeaveRuleEntity): String? = when (rule.refType) {
        LeaveRefType.TASK -> taskDao.getById(rule.refId)?.title
        LeaveRefType.CALENDAR_EVENT -> calendarEventDao.getById(rule.refId)?.title
    }

    companion object {
        const val DEFAULT_TRAVEL_MINUTES = 15
        const val DEFAULT_BUFFER_MINUTES = 5
    }
}
