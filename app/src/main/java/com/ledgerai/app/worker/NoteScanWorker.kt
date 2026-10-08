package com.ledgerai.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.data.repository.NudgeProposalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class NoteScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val noteRepo: NoteRepository,
    private val nudgeRepo: NudgeProposalRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notes = noteRepo.observeNotes().first()
        notes.filter { note ->
            note.body.contains("[nudge]", true) || note.body.contains("#nudge", true)
        }.forEach { nudgeRepo.scanNote(it.id, it.body) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "note_nudge_scan"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NoteScanWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
