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
import com.ledgerai.app.data.capture.AiGateway
import com.ledgerai.app.data.chat.ChatRepository
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.domain.notebook.NotebookJson
import com.ledgerai.app.domain.notebook.NotebookPrompts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Sends queued notebook asks through ai-proxy. Proposed actions are stored.
 * The executor runs only after the user taps the confirm card.
 */
@HiltWorker
class NotebookAskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chat: ChatRepository,
    private val gateway: AiGateway,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val waiting = chat.queued()
        if (waiting.isEmpty()) return Result.success()
        var retry = false
        for (message in waiting) {
            if (prefs.clipAiCallsLeft() <= 0 || !prefs.tryConsumeClipAiCall()) {
                chat.updateMessage(message.copy(state = "failed", updatedAt = System.currentTimeMillis()))
                continue
            }
            val reply = gateway.fast(NotebookPrompts.SYSTEM, message.text)
            if (reply.isFailure) {
                retry = true
                break
            }
            val now = System.currentTimeMillis()
            val question = listOfNotNull(message.prompt, message.text).joinToString("\n")
            val parsed = NotebookJson.accept(message.text, reply.getOrThrow(), question)
            if (parsed == null) {
                chat.updateMessage(message.copy(state = "failed", updatedAt = now))
                continue
            }
            chat.updateMessage(message.copy(state = "done", updatedAt = now))
            val prior = chat.latest(message.threadId, 40).any { it.role == "assistant" }
            chat.addMessage(
                threadId = message.threadId,
                role = "assistant",
                text = parsed.answer,
                prompt = null,
                clipIds = message.clipIds,
                state = "done",
                actionsJson = NotebookJson.store(parsed),
            )
            if (!prior) chat.rename(message.threadId, parsed.title)
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "notebook_ask"

        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotebookAskWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND, request)
        }
    }
}
