package com.ledgerai.app.worker

import android.content.Context
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.BillRepository
import com.ledgerai.app.data.repository.BudgetRepository
import com.ledgerai.app.data.repository.LifeLogRepository
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.NudgeProposalRepository
import com.ledgerai.app.data.repository.SpendGuideRepository
import com.ledgerai.app.data.repository.TransactionRepository
import com.ledgerai.app.service.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Bills, check-in, budget, morning spend and the daily insight.
 * WorkManager holds these until the app is opened. An exact alarm calls [runAll]
 * on a timer, and each check records when it last ran so opening the app cannot
 * send the same alert again.
 */
@Singleton
class DueChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepo: BillRepository,
    private val lifeLogRepo: LifeLogRepository,
    private val budgetRepo: BudgetRepository,
    private val transactionRepo: TransactionRepository,
    private val spendGuideRepo: SpendGuideRepository,
    private val noteRepo: NoteRepository,
    private val nudgeRepo: NudgeProposalRepository,
    private val aiRepository: AiRepository,
    private val notificationService: NotificationService,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun runAll() {
        bills()
        checkin()
        budget()
        spendMorning()
        insight()
        notes()
    }

    suspend fun bills() {
        if (!claim("bills", 12 * HOUR)) return
        val today = LocalDate.now()
        billRepo.getActiveBills().first().forEach { bill ->
            val daysLeft = ChronoUnit.DAYS.between(today, bill.nextDueDate).toInt()
            if (daysLeft <= 7) {
                notificationService.showBillReminder(bill.name, bill.amount, daysLeft, (2_000 + bill.id).toInt())
            }
        }
    }

    suspend fun checkin() {
        val now = LocalDateTime.now()
        if (now.hour < 7 || now.hour >= 23) return
        if (!claim("checkin", 3 * HOUR)) return
        lifeLogRepo.markExpiredGaps(now)
        lifeLogRepo.ensureWindowsForDay(LocalDate.now())
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val start = now.minusHours(3)
        notificationService.showCheckinPrompt(
            now.toEpochSecond(java.time.ZoneOffset.UTC),
            "${fmt.format(start)}–${fmt.format(now)}"
        )
    }

    suspend fun budget() {
        if (!claim("budget", 6 * HOUR)) return
        val now = LocalDate.now()
        budgetRepo.getBudgetsForMonth(now.monthValue, now.year).first().forEach { budget ->
            val spent = transactionRepo.getSpendingForCategoryMonth(budget.category, now.year, now.monthValue)
            val enriched = budget.copy(spent = spent)
            if (enriched.isNearLimit || enriched.isOverBudget) {
                notificationService.showBudgetAlert(budget.category.displayName, enriched.usagePercent, budget.id.toInt())
            }
        }
    }

    suspend fun spendMorning() {
        if (LocalDateTime.now().hour < 7) return
        if (!claimDay("spend")) return
        val guide = spendGuideRepo.computeTodayGuide()
        notificationService.showSpendGuideMorning(guide.guideAmount)
    }

    suspend fun insight() {
        if (!claimDay("insight")) return
        val insight = runCatching { aiRepository.generateDailyInsight(forceRefresh = true).getOrThrow() }.getOrNull() ?: return
        val title = insight.title?.takeIf { it.isNotBlank() } ?: "LedgerAI insight"
        val body = insight.body.orEmpty()
        if (body.isNotBlank()) notificationService.showAiInsight(title, body)
    }

    suspend fun notes() {
        if (!claimDay("notes")) return
        noteRepo.observeNotes().first()
            .filter { it.body.contains("[nudge]", true) || it.body.contains("#nudge", true) }
            .forEach { nudgeRepo.scanNote(it.id, it.body) }
    }

    private fun claim(key: String, intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < intervalMs) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }

    private fun claimDay(key: String): Boolean {
        val day = LocalDate.now().toString()
        if (prefs.getString(key, "") == day) return false
        prefs.edit().putString(key, day).apply()
        return true
    }

    companion object {
        private const val PREFS = "due_checks"
        private const val HOUR = 60 * 60 * 1000L
    }
}
