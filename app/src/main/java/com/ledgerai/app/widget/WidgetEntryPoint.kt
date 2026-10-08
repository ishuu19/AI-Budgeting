package com.ledgerai.app.widget

import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.data.repository.HabitRepository
import com.ledgerai.app.data.repository.JobRepository
import com.ledgerai.app.data.repository.PlanRepository
import com.ledgerai.app.data.repository.QuoteRepository
import com.ledgerai.app.data.repository.ScheduleRepository
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.data.repository.TaskRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.data.preferences.UserPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskRepository(): TaskRepository
    fun scheduleRepository(): ScheduleRepository
    fun calendarRepository(): CalendarRepository
    fun spendGuideRepository(): SpendGuideRepository
    fun budgetRepository(): BudgetRepository
    fun transactionRepository(): TransactionRepository
    fun quoteRepository(): QuoteRepository
    fun planRepository(): PlanRepository
    fun habitRepository(): HabitRepository
    fun billRepository(): BillRepository
    fun jobRepository(): JobRepository
    fun lifeLogRepository(): LifeLogRepository
    fun userPreferences(): UserPreferences
}
