package com.ledgerai.app.presentation.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.ledgerai.app.data.capture.CaptureEngine
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.worker.CaptureWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Photo saving for forms. Two ways, and neither asks for a file path:
 * - [attach]: the user filled the form by hand and added a photo. The photo is stored and uploaded,
 *   and the form keeps its id as the source.
 * - [fileWithAi]: the user only gave photos. The AI names, describes and files them.
 */
@HiltViewModel
class PhotoSaveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository,
    private val engine: CaptureEngine,
    private val session: UserSession,
) : ViewModel() {

    /** Returns the stored photo id, or null if the image could not be read. */
    suspend fun attach(uri: Uri, kind: String, title: String): String? {
        val userId = session.userInfo.first().userId
        if (userId.isBlank()) return null
        val row = media.addImage(userId, uri, "") ?: return null
        media.save(row.copy(kind = kind, title = title, analysisState = AnalysisState.DONE))
        CaptureWorker.kick(context)
        return row.id
    }

    /** A photo of a person: stored, uploaded and linked to them. Returns the photo id. */
    suspend fun attachToPerson(uri: Uri, personId: String, name: String): String? {
        val userId = session.userInfo.first().userId
        if (userId.isBlank()) return null
        val row = media.addImage(userId, uri, "") ?: return null
        media.save(row.copy(kind = "person", title = name, linkedType = "person", linkedId = personId, analysisState = AnalysisState.DONE))
        CaptureWorker.kick(context)
        return row.id
    }

    /** Returns how many photos were stored. [note] tells the AI what to expect, for example who is in them. */
    suspend fun fileWithAi(uris: List<Uri>, note: String): Int {
        val userId = session.userInfo.first().userId
        if (userId.isBlank()) return 0
        return engine.submit(userId, uris, note).size
    }

    fun thumb(photoId: String?): File = File(context.filesDir, "media/${photoId.orEmpty()}_t.jpg")
}
