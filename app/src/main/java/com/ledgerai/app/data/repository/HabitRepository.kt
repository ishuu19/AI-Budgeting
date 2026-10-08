package com.ledgerai.app.data.repository

import com.ledgerai.app.data.local.room.HabitDao
import com.ledgerai.app.data.local.room.HabitLogDao
import com.ledgerai.app.data.local.room.HabitLogEntity
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.local.room.toEntity
import com.ledgerai.app.domain.model.Habit
import com.ledgerai.app.domain.model.HabitCategory
import com.ledgerai.app.domain.model.HabitOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.ledgerai.app.service.HabitNudgeScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val habitNudgeScheduler: HabitNudgeScheduler
) {
    fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(habit: Habit): Long {
        val id = habitDao.insert(habit.toEntity())
        val saved = habit.copy(id = id)
        habitNudgeScheduler.scheduleForHabit(saved)
        return id
    }

    suspend fun getById(id: Long): Habit? =
        if (id <= 0L) null else habitDao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    suspend fun delete(id: Long) {
        if (id <= 0L) return
        habitDao.softDelete(id, System.currentTimeMillis())
        habitNudgeScheduler.cancelForHabit(id)
    }

    /** Habit ids with a DONE log on each date in the range. */
    fun observeDone(from: LocalDate, to: LocalDate): Flow<Set<Pair<Long, LocalDate>>> =
        habitLogDao.observeRange(from, to).map { logs ->
            logs.filter { it.outcome == HabitOutcome.DONE }.map { it.habitId to it.date }.toSet()
        }

    suspend fun rescheduleAllNudges(): Int {
        val habits = habitDao.listNudgeEnabled()
        habits.forEach { habitNudgeScheduler.scheduleForHabit(it.toDomain()) }
        return habits.size
    }

    suspend fun logOutcome(habitId: Long, outcome: HabitOutcome, minutes: Int? = null, date: LocalDate = LocalDate.now()) {
        habitLogDao.insert(
            HabitLogEntity(
                habitId = habitId,
                date = date,
                outcome = outcome,
                minutes = minutes,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Simple parse: "gym 18:00" or "reading 7am". */
    fun parseQuick(text: String): Habit? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val time = timeMatch?.let {
            var h = it.groupValues[1].toInt()
            val m = it.groupValues[2].takeIf { v -> v.isNotBlank() }?.toInt() ?: 0
            val ampm = it.groupValues[3].lowercase()
            if (ampm == "pm" && h < 12) h += 12
            if (ampm == "am" && h == 12) h = 0
            LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))
        } ?: LocalTime.of(18, 0)
        val title = trimmed.replace(timeMatch?.value ?: "", "").trim().ifBlank { "Habit" }
        val category = when {
            title.contains("gym", true) || title.contains("run", true) -> HabitCategory.EXERCISE
            title.contains("read", true) -> HabitCategory.READING
            title.contains("pray", true) -> HabitCategory.PRAYER
            else -> HabitCategory.CUSTOM
        }
        return Habit(title = title.replaceFirstChar { c -> c.titlecase() }, category = category, startTime = time)
    }
}
