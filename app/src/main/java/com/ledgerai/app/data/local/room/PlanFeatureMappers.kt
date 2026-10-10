package com.ledgerai.app.data.local.room

import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.JobApplication
import com.ledgerai.app.domain.model.PlanBlock
import com.ledgerai.app.domain.model.SpendSpeculation
import com.ledgerai.app.domain.model.StudyPlan

fun StudyPlanEntity.toDomain() = StudyPlan(
    id = id,
    topic = topic,
    hoursTotal = hoursTotal,
    deadline = deadline,
    sessionLenMinutes = sessionLenMinutes,
    status = status
)

fun StudyPlan.toEntity(now: Long = System.currentTimeMillis()) = StudyPlanEntity(
    id = id,
    topic = topic,
    hoursTotal = hoursTotal,
    deadline = deadline,
    sessionLenMinutes = sessionLenMinutes,
    status = status,
    updatedAt = now
)

fun PlanBlockEntity.toDomain() = PlanBlock(
    id = id,
    kind = kind,
    title = title,
    topic = topic,
    startAt = startAt,
    endAt = endAt,
    status = status,
    sourcePlanId = sourcePlanId,
    habitId = habitId,
    actualMinutes = actualMinutes
)

fun PlanBlock.toEntity(now: Long = System.currentTimeMillis()) = PlanBlockEntity(
    id = id,
    kind = kind,
    title = title,
    topic = topic,
    startAt = startAt,
    endAt = endAt,
    status = status,
    sourcePlanId = sourcePlanId,
    habitId = habitId,
    actualMinutes = actualMinutes,
    updatedAt = now
)

fun HabitEntity.toDomain() = Habit(
    id = id,
    title = title,
    category = category,
    daysMask = daysMask,
    startTime = startTime,
    durationMinutes = durationMinutes,
    nudgeEnabled = nudgeEnabled,
    quietOverride = quietOverride
)

fun Habit.toEntity(now: Long = System.currentTimeMillis()) = HabitEntity(
    id = id,
    title = title,
    category = category,
    daysMask = daysMask,
    startTime = startTime,
    durationMinutes = durationMinutes,
    nudgeEnabled = nudgeEnabled,
    quietOverride = quietOverride,
    updatedAt = now
)

fun SpendSpeculationEntity.toDomain() = SpendSpeculation(
    id = id,
    label = label,
    amount = amount,
    direction = direction,
    expectedDate = expectedDate,
    confidence = confidence
)

fun SpendSpeculation.toEntity(now: Long = System.currentTimeMillis()) = SpendSpeculationEntity(
    id = id,
    label = label,
    amount = amount,
    direction = direction,
    expectedDate = expectedDate,
    confidence = confidence,
    updatedAt = now
)

fun JobApplicationEntity.toDomain() = JobApplication(
    id = id,
    company = company,
    title = title,
    url = url,
    source = source,
    status = status,
    appliedOn = appliedOn,
    followUpOn = followUpOn,
    notes = notes,
    contact = contact,
    location = location,
    extraDates = extraDates
)

fun JobApplication.toEntity(now: Long = System.currentTimeMillis()) = JobApplicationEntity(
    id = id,
    company = company,
    title = title,
    url = url,
    source = source,
    status = status,
    appliedOn = appliedOn,
    followUpOn = followUpOn,
    notes = notes,
    contact = contact,
    location = location,
    extraDates = extraDates,
    updatedAt = now
)
