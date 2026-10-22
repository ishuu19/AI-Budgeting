package com.ledgerai.app.presentation.screens.vault

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.clips.ClipDao
import com.ledgerai.app.data.clips.ClipEntity
import com.ledgerai.app.data.clips.ClipRepository
import com.ledgerai.app.data.local.room.NoteDao
import com.ledgerai.app.data.local.room.NoteEntity
import com.ledgerai.app.data.media.MediaAssetDao
import com.ledgerai.app.data.media.MediaAssetEntity
import com.ledgerai.app.data.media.MediaKind
import com.ledgerai.app.data.media.UploadState
import com.ledgerai.app.data.media.VaultFiles
import com.ledgerai.app.worker.CaptureWorker
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.vault.FolderDao
import com.ledgerai.app.data.vault.FolderEntity
import com.ledgerai.app.data.vault.FolderRepository
import com.ledgerai.app.data.vault.SystemFolder
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LAddAction
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LSwitchRow
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.SecureWindow
import com.ledgerai.app.presentation.components.UnlockSession
import com.ledgerai.app.presentation.components.isDeviceSecure
import com.ledgerai.app.presentation.components.rememberDeviceUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val folders: FolderDao,
    private val folderRepo: FolderRepository,
    private val notes: NoteDao,
    private val media: MediaAssetDao,
    private val clips: ClipDao,
    private val clipRepo: ClipRepository,
    private val session: UserSession,
    private val uploader: com.ledgerai.app.data.media.MediaUploader,
) : ViewModel() {

    val allFolders: StateFlow<List<FolderEntity>> = folders.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            folderRepo.ensureSystemFolders(session.userInfo.first().userId.ifBlank { null })
        }
    }

    fun notesIn(folder: FolderEntity): Flow<List<NoteEntity>> =
        notes.observeInFolder(folder.id, if (folder.systemKey == SystemFolder.NOTES) 1 else 0)

    fun filesIn(folder: FolderEntity): Flow<List<MediaAssetEntity>> = media.observeLinked("folder", folder.id)

    val inbox: Flow<List<ClipEntity>> = clips.observeAll().map { list -> list.filter { it.status == "inbox" } }

    fun createFolder(name: String, locked: Boolean, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                folderRepo.create(session.userInfo.first().userId.ifBlank { null }, name, locked = locked)
                onDone()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Check the name")
            }
        }
    }

    fun rename(folder: FolderEntity, name: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            try { folderRepo.rename(folder.id, name); onDone() } catch (e: IllegalArgumentException) { onError(e.message ?: "Check the name") }
        }
    }

    fun togglePin(folder: FolderEntity) {
        viewModelScope.launch { folderRepo.setPinned(folder.id, !folder.pinned) }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch { folderRepo.delete(folder.id) }
    }

    fun saveNote(folder: FolderEntity, id: Long?, title: String, body: String, done: () -> Unit) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val stamp = System.currentTimeMillis()
            val existing = id?.let { notes.getById(it) }
            val cleanTitle = title.trim().ifBlank { body.trim().lineSequence().firstOrNull().orEmpty().take(40).ifBlank { "Untitled" } }
            if (existing == null) {
                notes.insert(
                    NoteEntity(
                        title = cleanTitle, body = body.take(20_000), createdAt = now, editedAt = now,
                        folderId = folder.id, updatedAt = stamp, userId = session.userInfo.first().userId.ifBlank { null },
                    )
                )
            } else {
                notes.update(existing.copy(title = cleanTitle, body = body.take(20_000), editedAt = now, updatedAt = stamp))
            }
            done()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { notes.softDelete(id, System.currentTimeMillis(), System.currentTimeMillis()) }
    }

    fun moveNote(id: Long, to: FolderEntity) {
        viewModelScope.launch { notes.moveToFolder(id, to.id, System.currentTimeMillis()) }
    }

    /** Copies a picked document into app storage. The app never reads the phone's storage on its own. */
    fun addFile(folder: FolderEntity, context: android.content.Context, uri: Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            VaultFiles.import(context, uri, session.userInfo.first().userId, "folder", folder.id, folder.locked)
                .onSuccess { row ->
                    media.upsert(row)
                    if (row.uploadState == UploadState.PENDING) CaptureWorker.kick(context)
                }
                .onFailure { onError(it.message ?: "Could not add that file") }
        }
    }

    /** Opens a file, fetching it first if it was added on another device. */
    fun open(context: android.content.Context, file: MediaAssetEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            val local = VaultFiles.localFile(context, file)
            if (!local.exists() && !uploader.downloadVaultFile(file, local)) return@launch onError("The file is not on this phone yet")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(intent, file.title)) }.onFailure { onError("No app can open this file") }
        }
    }

    fun deleteFile(file: MediaAssetEntity) {
        viewModelScope.launch {
            media.softDelete(file.id, System.currentTimeMillis())
            withContext(Dispatchers.IO) { runCatching { File(file.localPath).delete() } }
        }
    }

    fun fileInbox(clip: ClipEntity, to: FolderEntity) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val text = clip.editedText ?: clip.text
            val noteId = notes.insert(
                NoteEntity(
                    title = text.lineSequence().firstOrNull().orEmpty().take(40).ifBlank { "Clip" }, body = text,
                    createdAt = now, editedAt = now, folderId = to.id, updatedAt = System.currentTimeMillis(),
                    userId = session.userInfo.first().userId.ifBlank { null },
                )
            )
            clipRepo.addToNote(clip.id, noteId)
        }
    }

    fun dismissClip(clip: ClipEntity) {
        viewModelScope.launch { clipRepo.dismiss(clip.id) }
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
    }
}

// ─── Folder list ────────────────────────────────────────────────────────────────────────────

@Composable
fun VaultScreen(onBack: () -> Unit, vm: VaultViewModel = hiltViewModel()) {
    val folders by vm.allFolders.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val current = folders.firstOrNull { it.id == open }

    if (current != null) {
        FolderScreen(current, vm, onBack = { open = null })
        return
    }

    val pinned = folders.filter { it.pinned }
    val rest = folders.filter { !it.pinned }
    LScreen(title = "Vault", onBack = onBack, action = { LAddAction({ adding = true }, "New folder") }) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.NOTE) }
        if (folders.isEmpty()) item { LEmpty(Icons.Filled.Folder, "No folders yet.") }
        if (pinned.isNotEmpty()) item { LSection("pinned") }
        pinned.forEach { f -> item(key = "p${f.id}") { FolderRow(f) { open = f.id } } }
        if (rest.isNotEmpty()) item { LSection("folders") }
        rest.forEach { f -> item(key = f.id) { FolderRow(f) { open = f.id } } }
    }
    if (adding) NewFolderSheet(vm) { adding = false }
}

@Composable
private fun FolderRow(f: FolderEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(f.name, style = MaterialTheme.typography.titleSmall, color = L.Ink)
        if (f.locked) Text("locked", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
    }
}

@Composable
private fun NewFolderSheet(vm: VaultViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var locked by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LSheet(
        title = "New folder",
        onDismiss = onDismiss,
        primary = "Create",
        onPrimary = {
            if (locked && !isDeviceSecure(context)) error = "Set a screen lock on this phone first"
            else vm.createFolder(name, locked, onError = { error = it }, onDone = onDismiss)
        },
    ) {
        LField(name, { name = it }, "Name")
        LSwitchRow("Locked", locked, { locked = it }, sub = "Needs your screen lock. Stays on this phone.")
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
}

// ─── Inside a folder ────────────────────────────────────────────────────────────────────────

@Composable
private fun FolderScreen(folder: FolderEntity, vm: VaultViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember(folder.id) { mutableStateOf(!folder.locked || UnlockSession.isOpen()) }
    val unlock = rememberDeviceUnlock(onUnlocked = { unlocked = true })

    if (folder.locked) SecureWindow()

    if (!unlocked) {
        LScreen(title = folder.name, onBack = onBack) {
            item { Text("This folder is locked. It stays on this phone.", style = MaterialTheme.typography.bodyLarge, color = L.InkMuted) }
            item { com.ledgerai.app.presentation.components.LButton("Unlock", { unlock() }) }
        }
        return
    }

    val notes by vm.notesIn(folder).collectAsState(initial = emptyList())
    val files by vm.filesIn(folder).collectAsState(initial = emptyList())
    val inbox by vm.inbox.collectAsState(initial = emptyList())
    val folders by vm.allFolders.collectAsState()
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var writing by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var fileToDelete by remember { mutableStateOf<MediaAssetEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { vm.addFile(folder, context, it) { msg -> message = msg } }
    }
    val isInbox = folder.systemKey == SystemFolder.INBOX

    LScreen(
        title = folder.name,
        onBack = onBack,
        action = { LTextLink("More", { menu = true }) },
    ) {
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) } }
        if (isInbox) {
            if (inbox.isEmpty()) item { LEmpty(Icons.Filled.Folder, "Inbox is empty.") }
            inbox.forEach { clip ->
                item(key = clip.id) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(clip.editedText ?: clip.text, style = MaterialTheme.typography.bodyMedium, color = L.Ink, maxLines = 3)
                        clip.sourceApp?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.InkMuted) }
                        FileTo(folders.filter { it.systemKey != SystemFolder.INBOX && !it.locked }, onPick = { vm.fileInbox(clip, it) }, onDismiss = { vm.dismissClip(clip) })
                    }
                }
            }
        } else {
            if (notes.isEmpty() && files.isEmpty()) item { LEmpty(Icons.Filled.Folder, "Nothing in this folder.") }
            notes.forEach { n ->
                item(key = "n${n.id}") {
                    Column(Modifier.fillMaxWidth().clickable { editing = n }.heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                        if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 1)
                    }
                }
            }
            files.forEach { f ->
                item(key = "f${f.id}") {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.open(context, f) { message = it } }.heightIn(min = 56.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.title, style = MaterialTheme.typography.titleSmall, color = L.Ink, maxLines = 1)
                            Text(sizeText(f.sizeBytes), style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                        }
                        LTextLink("Delete", { fileToDelete = f })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LTextLink("Write", { writing = true })
                    LTextLink("Upload", { picker.launch(arrayOf("*/*")) })
                }
            }
        }
    }

    if (menu) {
        LSheet(title = folder.name, onDismiss = { menu = false }) {
            if (folder.systemKey == null) {
                LGhostButton("Rename", { menu = false; renaming = true })
                LGhostButton(if (folder.pinned) "Unpin" else "Pin to top", { vm.togglePin(folder); menu = false })
                LGhostButton("Delete folder", { menu = false; confirmDelete = true }, destructive = true)
            } else {
                LGhostButton(if (folder.pinned) "Unpin" else "Pin to top", { vm.togglePin(folder); menu = false })
            }
        }
    }
    if (writing) NoteSheet(null, folder, vm) { writing = false }
    editing?.let { NoteSheet(it, folder, vm, folders = folders) { editing = null } }
    if (renaming) RenameSheet(folder, vm) { renaming = false }
    if (confirmDelete) LConfirmDelete(onConfirm = { vm.deleteFolder(folder); confirmDelete = false; onBack() }, onDismiss = { confirmDelete = false }, title = "Delete folder?")
    fileToDelete?.let { f -> LConfirmDelete(onConfirm = { vm.deleteFile(f); fileToDelete = null }, onDismiss = { fileToDelete = null }) }
}

@Composable
private fun FileTo(targets: List<FolderEntity>, onPick: (FolderEntity) -> Unit, onDismiss: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LTextLink("File", { choosing = true })
        LTextLink("Delete", onDismiss)
    }
    if (choosing) {
        LSheet(title = "File into", onDismiss = { choosing = false }) {
            targets.forEach { t -> LGhostButton(t.name, { onPick(t); choosing = false }) }
        }
    }
}

@Composable
private fun NoteSheet(note: NoteEntity?, folder: FolderEntity, vm: VaultViewModel, folders: List<FolderEntity> = emptyList(), onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(note?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(note?.body.orEmpty()) }
    var move by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LSheet(
        title = if (note == null) "New note" else "Note",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.saveNote(folder, note?.id, title, body, onDismiss) },
        secondary = if (note != null) "Delete" else null,
        onSecondary = { confirm = true },
        secondaryDestructive = true,
    ) {
        LField(title, { title = it }, "Title")
        LField(body, { body = it }, "Write here", singleLine = false, minLines = 6)
        if (note != null) LGhostButton("Move to another folder", { move = true })
    }
    if (move && note != null) {
        LSheet(title = "Move to", onDismiss = { move = false }) {
            folders.filter { it.id != folder.id && it.systemKey != SystemFolder.INBOX }.forEach { t ->
                LGhostButton(t.name, { vm.moveNote(note.id, t); move = false; onDismiss() })
            }
        }
    }
    if (confirm && note != null) LConfirmDelete(onConfirm = { vm.deleteNote(note.id); confirm = false; onDismiss() }, onDismiss = { confirm = false })
}

@Composable
private fun RenameSheet(folder: FolderEntity, vm: VaultViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(folder.name) }
    var error by remember { mutableStateOf<String?>(null) }
    LSheet(title = "Rename folder", onDismiss = onDismiss, primary = "Save", onPrimary = { vm.rename(folder, name, { error = it }, onDismiss) }) {
        LField(name, { name = it }, "Name")
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
}

private fun sizeText(bytes: Long?): String = when {
    bytes == null -> ""
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
