package com.ledgerai.app.data.capture

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaKind
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.data.media.MediaUploader
import com.ledgerai.app.domain.capture.CaptureJson
import com.ledgerai.app.domain.capture.CaptureKind
import com.ledgerai.app.domain.capture.CapturePrompts
import com.ledgerai.app.domain.capture.CaptureResult
import com.ledgerai.app.domain.capture.FoodGuess
import com.ledgerai.app.domain.capture.GarmentGuess
import com.ledgerai.app.domain.capture.PersonGuess
import com.ledgerai.app.worker.CaptureWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The capture pipeline. [submit] only saves locally and queues work, so it is instant and works
 * offline. [processPending] runs from WorkManager when the network is back: the AI looks at each
 * new photo once (names it, describes it, files it), then the photo is uploaded.
 */
@Singleton
class CaptureEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository,
    private val uploader: MediaUploader,
    private val ai: AiGateway,
    private val filer: CaptureFiler,
    private val sessionGuard: com.ledgerai.app.data.auth.SessionGuard,
) {

    /** Saves each image and queues background work. Returns the ids of the images that were stored. */
    suspend fun submit(userId: String, images: List<Uri>, note: String): List<String> {
        val ids = images.mapNotNull { media.addImage(userId, it, note)?.id }
        if (ids.isNotEmpty()) {
            // Work starts now while the app is open. The worker is the safety net for offline and killed apps.
            scope.launch { runCatching { processPending() } }
            CaptureWorker.kick(context)
        }
        return ids
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val passLock = kotlinx.coroutines.sync.Mutex()

    /** Result of one pass, so the worker knows whether to retry. */
    data class Pass(val retry: Boolean)

    suspend fun processPending(): Pass = passLock.withLock { runPass() }

    private suspend fun runPass(): Pass {
        var retry = false
        for (row in media.pendingAnalysis().filter { it.localPath.isNotBlank() }) {
            when (analyze(row)) {
                Outcome.TRANSIENT -> retry = true
                else -> Unit
            }
        }
        // A local-only account has no bucket to upload to; photos simply stay on the phone.
        // Analysis above never needs the upload login. A dead session only delays the backup.
        if (uploader.canUpload()) {
            if (!sessionGuard.ensureFreshSession()) return Pass(retry = true)
            for (row in media.pendingUpload().filter { File(it.localPath).exists() }) {
                if (!uploader.upload(row)) retry = true
            }
        }
        return Pass(retry)
    }

    private fun waitingReason(err: Throwable?): String {
        val text = err?.message.orEmpty()
        return when {
            text.contains("Session expired", true) || text.contains("sign in", true) || text.contains("No auth", true) ->
                "Sign in again to continue."
            err is java.net.UnknownHostException || err is java.net.SocketTimeoutException || err is java.net.ConnectException ->
                "Offline. I will continue when you are back online."
            else -> "Working on it. The AI did not answer yet, trying again."
        }
    }

    private enum class Outcome { DONE, TRANSIENT, PERMANENT }

    private suspend fun analyze(row: MediaAssetEntity): Outcome = withContext(Dispatchers.IO) {
        val file = File(row.localPath)
        if (!file.exists()) {
            media.save(row.copy(analysisState = AnalysisState.FAILED, description = "Photo file is missing"))
            return@withContext Outcome.PERMANENT
        }
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val reply = ai.vision(CapturePrompts.SYSTEM, CapturePrompts.user(row.note), "image/jpeg", b64)
        val raw = reply.getOrNull()
        if (raw == null) {
            val err = reply.exceptionOrNull()
            // No vision key configured will never fix itself: let the user label it by hand.
            return@withContext if (err is IllegalStateException) {
                media.save(row.copy(analysisState = AnalysisState.FAILED, description = "AI isn't available. Label it by hand."))
                Outcome.PERMANENT
            } else {
                // Keep the reason on the photo so the card can say what it is waiting for.
                android.util.Log.w("CaptureEngine", "analysis failed for ${row.id.take(8)}: ${err?.javaClass?.simpleName}: ${err?.message}")
                media.save(row.copy(description = "!" + waitingReason(err) + "\n" + (err?.message ?: err?.javaClass?.simpleName).orEmpty().take(300)))
                Outcome.TRANSIENT
            }
        }
        val result = CaptureJson.parse(raw)
        if (result == null) {
            media.save(row.copy(analysisState = AnalysisState.FAILED, description = "Couldn't read this photo. Label it by hand."))
            return@withContext Outcome.PERMANENT
        }
        media.save(filer.file(row, result))
        Outcome.DONE
    }

    /** User names a photo the AI could not file. Builds the same result the AI would have produced. */
    suspend fun fileByHand(id: String, kind: String, name: String): Boolean {
        val row = media.get(id) ?: return false
        val label = name.trim()
        if (label.isEmpty()) return false
        val result = when (kind) {
            MediaKind.CLOTHING -> CaptureResult(
                CaptureKind.CLOTHING, label, row.description,
                garment = GarmentGuess(label, "", emptyList(), emptyList()),
            )
            MediaKind.FOOD -> CaptureResult(
                CaptureKind.FOOD, label, row.description,
                food = listOf(FoodGuess(label, null, "", "", null)),
            )
            MediaKind.PERSON -> return answerName(id, label)
            else -> CaptureResult(CaptureKind.OTHER, label, row.description)
        }
        media.save(filer.file(row.copy(payload = null), result, attempt = ":manual"))
        return true
    }

    /** Answers "who is this?". Files the photo under that person on the capture date. */
    suspend fun answerName(id: String, name: String): Boolean {
        val row = media.get(id) ?: return false
        if (name.isBlank()) return false
        val guess = PersonGuess(name = name, on = null, place = null, memory = row.note.ifBlank { null })
        media.save(filer.filePerson(row.copy(payload = null), guess, nameOverride = name, attempt = ":named"))
        return true
    }

    suspend fun acceptSuggestion(id: String, type: String) {
        val row = media.get(id) ?: return
        media.save(filer.accept(row, type))
    }

    /** Dismiss one suggestion without filing it. */
    suspend fun dismissSuggestion(id: String, type: String) {
        val row = media.get(id) ?: return
        media.save(filer.dismiss(row, type))
    }

    /** Retry analysis for a photo that failed. */
    suspend fun retry(id: String) {
        val row = media.get(id) ?: return
        media.save(row.copy(analysisState = AnalysisState.PENDING))
        CaptureWorker.kick(context)
    }
}
