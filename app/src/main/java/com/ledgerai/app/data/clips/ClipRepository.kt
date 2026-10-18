package com.ledgerai.app.data.clips

import android.content.Context
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.toDomain
import com.ledgerai.app.data.preferences.UserPreferences
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.NoteRepository
import com.ledgerai.app.domain.clips.ClipFiling
import com.ledgerai.app.domain.clips.ClipGate
import com.ledgerai.app.domain.clips.ClipRead
import com.ledgerai.app.domain.clips.ClipRecall
import com.ledgerai.app.domain.clips.ClipRules
import com.ledgerai.app.domain.model.NoteItem
import com.ledgerai.app.worker.ClipFileWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class ClipTarget {
    data object Inbox : ClipTarget()
    data object NewNote : ClipTarget()
    data class Note(val id: Long) : ClipTarget()
    /** Collecting note when one is set, otherwise a new note. */
    data object Default : ClipTarget()
}

data class ClipChoice(val id: String, val label: String)

sealed class CaptureResult {
    data class Saved(
        val id: String,
        val destination: String,
        val trimmed: Boolean,
        val queuedAi: Boolean,
        val notice: String?,
        val read: ClipRead,
    ) : CaptureResult()

    data object Empty : CaptureResult()
    data object Password : CaptureResult()
    data object NeedsConfirm : CaptureResult()
}

@Singleton
class ClipRepository @Inject constructor(
    private val dao: ClipDao,
    private val notes: NoteDao,
    private val noteRepo: NoteRepository,
    private val prefs: UserPreferences,
    private val session: UserSession,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<ClipEntity>> = dao.observeAll()
    fun observeInboxCount(): Flow<Int> = dao.observeInboxCount()
    fun observeForNote(noteId: Long): Flow<List<ClipEntity>> = dao.observeForNote(noteId)

    suspend fun get(id: String): ClipEntity? = dao.get(id)

    suspend fun capture(
        raw: CharSequence,
        sourceKind: String,
        sourceApp: String?,
        subject: String?,
        target: ClipTarget,
        askAi: Boolean,
        saveAnyway: Boolean,
        descriptionSensitive: Boolean,
    ): CaptureResult {
        val plain = ClipRules.plain(raw)
        val withSubject = if (
            sourceKind == "share" && !subject.isNullOrBlank() &&
            !subject.startsWith("http") && !plain.contains(subject)
        ) {
            subject.trim() + "\n" + plain
        } else {
            plain
        }
        if (withSubject.isBlank()) return CaptureResult.Empty
        when (ClipRules.gate(withSubject, descriptionSensitive)) {
            ClipGate.Password -> return CaptureResult.Password
            ClipGate.ConfirmSensitive -> if (!saveAnyway) return CaptureResult.NeedsConfirm
            ClipGate.Ok -> Unit
        }
        val (text, trimmed) = ClipRules.limit(withSubject)
        val sensitive = ClipRules.isSensitiveText(text)
        val read = ClipRules.read(text)
        val hash = ClipRules.sha256(text.trim())
        val now = System.currentTimeMillis()
        val bumped = dao.recentHash(hash, now - ClipRules.DEDUPE_MS)
        if (bumped != null) {
            dao.upsert(bumped.copy(capturedAt = now, updatedAt = now))
            return CaptureResult.Saved(bumped.id, destinationOf(bumped), trimmed, false, null, read)
        }
        val userId = session.userInfo.first().userId.ifBlank { null }
        val resolved = resolveTarget(target)
        if (resolved.missing) prefs.setClipCollecting(null)
        val id = UUID.randomUUID().toString()
        var noteId = resolved.noteId
        var status = if (noteId == null) "inbox" else "in_note"
        if (resolved.createNew) {
            val title = ClipRules.noteTitle(text, read.tags, LocalDate.now())
            noteId = noteRepo.insert(NoteItem(title = title, body = text, tags = read.tags))
            status = "in_note"
            prefs.setClipLastNote(noteId)
        } else if (noteId != null) {
            val note = notes.getById(noteId)
            if (note == null) {
                noteId = null
                status = "inbox"
            } else {
                val domain = note.toDomain()
                noteRepo.update(
                    domain.copy(
                        body = ClipRules.appendBlock(domain.body, text),
                        tags = (domain.tags + read.tags).distinct(),
                    )
                )
                prefs.setClipLastNote(noteId)
            }
        }
        val mode = prefs.clipAiModeNow()
        val queue = !sensitive && mode != ClipFiling.OFF && (askAi || read.confidentKind == null)
        val row = ClipEntity(
            id = id,
            userId = userId,
            text = text,
            textHash = hash,
            sourceKind = sourceKind,
            sourceApp = sourceApp?.take(200),
            url = ClipRules.firstUrl(text, subject),
            noteId = noteId,
            status = status,
            sensitive = sensitive,
            aiState = if (queue) "queued" else "skipped",
            capturedAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        if (queue) ClipFileWorker.kick(context)
        val notice = if (resolved.missing) "That note is gone. This clip is in the inbox." else null
        return CaptureResult.Saved(id, destinationOf(row), trimmed, queue, notice, read)
    }

    suspend fun undoCapture(id: String) {
        val clip = dao.get(id) ?: return
        val noteId = clip.noteId
        if (noteId != null) {
            val note = notes.getById(noteId)
            if (note != null) {
                noteRepo.update(note.toDomain().copy(body = ClipRules.removeBlock(note.body, clip.text)))
            }
        }
        val now = System.currentTimeMillis()
        dao.upsert(clip.copy(deletedAt = now, updatedAt = now))
    }

    suspend fun addToNote(clipId: String, noteId: Long) {
        val clip = dao.get(clipId) ?: return
        val note = notes.getById(noteId) ?: return
        if (clip.noteId != noteId) {
            noteRepo.update(note.toDomain().copy(body = ClipRules.appendBlock(note.body, clip.text)))
        }
        val now = System.currentTimeMillis()
        val status = if (clip.filedId != null) "filed" else "in_note"
        dao.upsert(clip.copy(noteId = noteId, status = status, updatedAt = now))
        prefs.setClipLastNote(noteId)
    }

    suspend fun combine(ids: List<String>, noteId: Long?, newTitle: String?) {
        val clips = ids.mapNotNull { dao.get(it) }.filter { it.deletedAt == null }
        if (clips.isEmpty()) return
        val target = noteId ?: noteRepo.insert(
            NoteItem(
                title = newTitle?.trim()?.ifBlank { null } ?: ClipRules.noteTitle(clips.first().text, emptyList()),
                body = "",
            )
        )
        clips.forEach { addToNote(it.id, target) }
        prefs.setClipCollecting(target)
    }

    suspend fun dismiss(id: String) {
        val clip = dao.get(id) ?: return
        dao.upsert(clip.copy(status = "dismissed", updatedAt = System.currentTimeMillis()))
    }

    suspend fun undoDismiss(id: String) {
        val clip = dao.get(id) ?: return
        val status = when {
            clip.filedId != null -> "filed"
            clip.noteId != null -> "in_note"
            else -> "inbox"
        }
        dao.upsert(clip.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    /** Your edit lives in [ClipEntity.editedText]. The captured text stays. */
    suspend fun editText(id: String, raw: String) {
        val clip = dao.get(id) ?: return
        val (text, _) = ClipRules.limit(ClipRules.plain(raw))
        if (text.isBlank()) return
        dao.upsert(clip.copy(editedText = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveEntry(
        raw: String,
        sourceKind: String,
        sourceApp: String?,
        windowTitle: String?,
        prompt: String?,
        threadId: String? = null,
        messageId: String? = null,
        saveAnyway: Boolean = false,
    ): CaptureResult {
        val plain = ClipRules.plain(raw)
        if (plain.isBlank()) return CaptureResult.Empty
        when (ClipRules.gate(plain, descriptionSensitive = false)) {
            ClipGate.Password -> return CaptureResult.Password
            ClipGate.ConfirmSensitive -> if (!saveAnyway) return CaptureResult.NeedsConfirm
            ClipGate.Ok -> Unit
        }
        val (text, trimmed) = ClipRules.limit(plain)
        val sensitive = ClipRules.isSensitiveText(text)
        val read = ClipRules.read(text)
        val hash = ClipRules.sha256(text.trim())
        val now = System.currentTimeMillis()
        val bumped = dao.recentHash(hash, now - ClipRules.DEDUPE_MS)
        if (bumped != null) {
            dao.upsert(
                bumped.copy(
                    capturedAt = now,
                    updatedAt = now,
                    prompt = prompt?.take(2_000) ?: bumped.prompt,
                    windowTitle = windowTitle?.take(200) ?: bumped.windowTitle,
                    threadId = threadId ?: bumped.threadId,
                    messageId = messageId ?: bumped.messageId,
                )
            )
            return CaptureResult.Saved(bumped.id, "Saved", trimmed, false, null, read)
        }
        val id = UUID.randomUUID().toString()
        val row = ClipEntity(
            id = id,
            userId = session.userInfo.first().userId.ifBlank { null },
            text = text,
            textHash = hash,
            sourceKind = sourceKind,
            sourceApp = sourceApp?.take(200),
            windowTitle = windowTitle?.take(200),
            prompt = prompt?.take(2_000),
            threadId = threadId,
            messageId = messageId,
            status = "inbox",
            sensitive = sensitive,
            aiState = "none",
            capturedAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        return CaptureResult.Saved(id, "Saved", trimmed, false, null, read)
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val clip = dao.get(id) ?: return
        val order = if (pinned) (dao.maxPinOrder() ?: -1) + 1 else null
        dao.upsert(clip.copy(pinned = pinned, pinOrder = order, updatedAt = System.currentTimeMillis()))
    }

    suspend fun reorderPins(ids: List<String>) {
        val now = System.currentTimeMillis()
        ids.forEachIndexed { index, id ->
            val clip = dao.get(id) ?: return@forEachIndexed
            dao.upsert(clip.copy(pinned = true, pinOrder = index, updatedAt = now))
        }
    }

    suspend fun attachThread(id: String, threadId: String) {
        val clip = dao.get(id) ?: return
        dao.upsert(clip.copy(threadId = threadId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteClip(id: String) {
        val clip = dao.get(id) ?: return
        val now = System.currentTimeMillis()
        dao.upsert(clip.copy(deletedAt = now, updatedAt = now))
    }

    suspend fun askAi(ids: List<String>) {
        val now = System.currentTimeMillis()
        var any = false
        ids.take(ClipFiling.BATCH).forEach { id ->
            val row = dao.get(id) ?: return@forEach
            if (row.deletedAt != null || row.sensitive) return@forEach
            dao.upsert(row.copy(aiState = "queued", updatedAt = now))
            any = true
        }
        if (any) ClipFileWorker.kick(context)
    }

    suspend fun splitPieces(pieces: List<String>): Int {
        var n = 0
        pieces.map { it.trim() }.filter { it.isNotEmpty() }.forEach { piece ->
            noteRepo.insert(NoteItem(title = ClipRules.noteTitle(piece, ClipRules.read(piece).tags), body = piece))
            n++
        }
        return n
    }

    /** SQL search first. [ClipRecall.vectorQuery] stays null until memory_chunks exists. */
    suspend fun recall(question: String): List<ClipEntity> {
        ClipRecall.vectorQuery(question)?.let { return dao.search(it, 5) }
        val terms = ClipRecall.terms(question)
        if (terms.isEmpty()) return emptyList()
        return terms.flatMap { dao.search(it, 5) }.distinctBy { it.id }.take(5)
    }

    suspend fun recentNotes(limit: Int): List<NoteItem> =
        noteRepo.observeNotes().first().take(limit)

    /** Collecting note first, then New note, then the three newest notes, then the inbox. */
    suspend fun choices(): List<ClipChoice> {
        val collectingId = prefs.clipCollectingNow()
        val recent = recentNotes(6)
        val collecting = collectingId?.let { id ->
            recent.firstOrNull { it.id == id } ?: notes.getById(id)?.toDomain()
        }
        val out = mutableListOf<ClipChoice>()
        if (collecting != null) out += ClipChoice("note:${collecting.id}", "${collecting.title} · collecting")
        out += ClipChoice("new", "New note")
        recent.filter { it.id != collecting?.id }.take(3).forEach { out += ClipChoice("note:${it.id}", it.title.ifBlank { "Note" }) }
        out += ClipChoice("inbox", "Save for later")
        return out
    }

    fun choiceTarget(id: String): ClipTarget = when {
        id == "new" -> ClipTarget.NewNote
        id == "inbox" -> ClipTarget.Inbox
        id.startsWith("note:") -> ClipTarget.Note(id.removePrefix("note:").toLongOrNull() ?: 0L)
        else -> ClipTarget.Inbox
    }

    private suspend fun resolveTarget(target: ClipTarget): Resolved {
        val chosen = if (target is ClipTarget.Default) {
            val collecting = prefs.clipCollectingNow()
            if (collecting != null) ClipTarget.Note(collecting) else ClipTarget.NewNote
        } else {
            target
        }
        return when (chosen) {
            ClipTarget.Inbox, ClipTarget.Default -> Resolved(null, false, false)
            ClipTarget.NewNote -> Resolved(null, true, false)
            is ClipTarget.Note -> {
                if (notes.getById(chosen.id) == null) Resolved(null, false, true)
                else Resolved(chosen.id, false, false)
            }
        }
    }

    private suspend fun destinationOf(clip: ClipEntity): String {
        val noteId = clip.noteId ?: return "Saved for later"
        val title = notes.getById(noteId)?.title?.ifBlank { null }
        return title ?: "Note"
    }

    private data class Resolved(val noteId: Long?, val createNew: Boolean, val missing: Boolean)
}
