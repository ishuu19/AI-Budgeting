package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.assistant.ActionStatus
import com.ledgerai.app.data.assistant.Executor
import com.ledgerai.app.data.capture.AiGateway
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.domain.assistant.ActionPlan
import com.ledgerai.app.domain.assistant.ProposedAction
import com.ledgerai.app.domain.clips.ClipFiling
import com.ledgerai.app.domain.clips.ClipJson
import com.ledgerai.app.domain.clips.ClipPrompts
import com.ledgerai.app.domain.clips.ClipSuggestion
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * One fast-model pass per queued clip, through ai-proxy. The executor writes only when Auto
 * mode is on and the action is low risk. Anything else stays a suggestion on the clip.
 */
@HiltWorker
class ClipFileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: ClipDao,
    private val gateway: AiGateway,
    private val executor: Executor,
    private val prefs: UserPreferences,
    private val session: UserSession,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val waiting = dao.queued()
        if (waiting.isEmpty()) return Result.success()
        val mode = prefs.clipAiModeNow()
        if (mode == ClipFiling.OFF) {
            val now = System.currentTimeMillis()
            waiting.forEach { dao.upsert(it.copy(aiState = "skipped", updatedAt = now)) }
            return Result.success()
        }
        val left = prefs.clipAiCallsLeft()
        if (left <= 0) {
            cap(waiting)
            return Result.success()
        }
        val batch = waiting.take(minOf(ClipFiling.BATCH, left))
        val overflow = waiting.drop(batch.size)
        if (left < waiting.size && overflow.isNotEmpty() && left < ClipFiling.BATCH) {
            // Clips past today's cap stay notes. The next day can retry the ones still queued.
        }
        val capped = waiting.drop(left)
        if (capped.isNotEmpty()) cap(capped)
        val today = LocalDate.now()
        val user = batch.map { (it.sourceApp ?: "Unknown app") to it.text }
        val reply = gateway.fast(ClipPrompts.system, ClipPrompts.batch(user, today))
        if (reply.isFailure) return Result.retry()
        val parsed = ClipJson.parseBatch(reply.getOrThrow(), batch.size)
        val userId = session.userInfo.first().userId
        val now = System.currentTimeMillis()
        batch.forEachIndexed { index, clip ->
            val suggestion = parsed.getOrNull(index)
            if (suggestion == null) {
                dao.upsert(clip.copy(aiState = "failed", updatedAt = now))
                return@forEachIndexed
            }
            if (!prefs.tryConsumeClipAiCall()) {
                dao.upsert(clip.copy(aiState = "skipped", aiJson = """{"capped":true}""", aiKind = null, updatedAt = now))
                return@forEachIndexed
            }
            val stored = suggestion.copy(inferred = true)
            val json = ClipJson.toJson(stored)
            if (ClipFiling.autoApplies(stored.kind, mode)) {
                val plan = plan(clip.id, userId, stored, confirmed = false)
                val result = if (plan == null) null else executor.execute(plan).firstOrNull()
                if (result?.status == ActionStatus.APPLIED) {
                    val fresh = dao.get(clip.id) ?: clip
                    dao.upsert(fresh.copy(aiKind = stored.kind, aiJson = json, aiState = "done", updatedAt = System.currentTimeMillis()))
                } else {
                    dao.upsert(clip.copy(aiState = "done", aiKind = stored.kind, aiJson = json, updatedAt = now))
                }
            } else {
                dao.upsert(clip.copy(aiState = "done", aiKind = stored.kind, aiJson = json, updatedAt = now))
            }
        }
        return Result.success()
    }

    private suspend fun cap(rows: List<com.ledgerai.app.data.clips.ClipEntity>) {
        val now = System.currentTimeMillis()
        rows.forEach {
            dao.upsert(it.copy(aiState = "skipped", aiJson = """{"capped":true}""", updatedAt = now))
        }
    }

    private fun plan(clipId: String, userId: String, s: ClipSuggestion, confirmed: Boolean): ActionPlan? {
        val type = ClipFiling.actionType(s.kind) ?: return null
        val args: Map<String, Any?> = mapOf(
            "clipId" to clipId,
            "userId" to userId,
            "title" to s.title,
            "date" to s.date,
            "time" to s.time,
            "place" to s.place,
            "amount" to s.amount,
            "currency" to s.currency,
            "company" to s.company,
            "role" to s.role,
            "person" to s.person,
            "item" to (s.item ?: s.title),
            "confirmed" to if (confirmed) "true" else "false",
            "tags" to s.tags,
        )
        return ActionPlan(inputRef = "clip:$clipId", actions = listOf(ProposedAction(type, args)))
    }

    companion object {
        private const val WORK_NAME = "clip_file"

        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClipFileWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
