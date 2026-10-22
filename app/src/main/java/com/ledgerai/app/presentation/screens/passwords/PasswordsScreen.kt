package com.ledgerai.app.presentation.screens.passwords

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.passwords.PasswordDao
import com.ledgerai.app.data.passwords.PasswordEntity
import com.ledgerai.app.data.passwords.PasswordRepository
import com.ledgerai.app.data.passwords.PasswordSecret
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LAddAction
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LTextLink
import com.ledgerai.app.presentation.components.SecureWindow
import com.ledgerai.app.presentation.components.UnlockSession
import com.ledgerai.app.presentation.components.rememberDeviceUnlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class PasswordsViewModel @Inject constructor(
    dao: PasswordDao,
    private val repo: PasswordRepository,
) : ViewModel() {

    val rows: StateFlow<List<PasswordEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun overdue(): Set<String> = repo.overdue(rows.value).toSet()

    fun open(id: String, onResult: (PasswordSecret?) -> Unit) {
        viewModelScope.launch { onResult(repo.reveal(id)) }
    }

    fun save(
        id: String?, title: String, site: String, username: String, secret: String, note: String, every: Int?,
        onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        if (title.isBlank()) return onError("Give it a title")
        if (secret.isEmpty()) return onError("Enter the password")
        viewModelScope.launch {
            repo.save(id, title, site, username, secret, note, every)
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun backup(context: Context, uri: Uri, passphrase: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val bytes = repo.export(passphrase.toCharArray())
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file")
            }.onSuccess { onResult("Backup saved. Keep the passphrase: it cannot be recovered.") }
                .onFailure { onResult(it.message ?: "Backup failed") }
        }
    }

    fun restore(context: Context, uri: Uri, passphrase: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the file")
                repo.import(bytes, passphrase.toCharArray())
            }.onSuccess { onResult("Restored $it passwords.") }
                .onFailure { onResult(it.message ?: "Restore failed") }
        }
    }

    companion object {
        private const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val SYMBOLS = "!@#$%^&*-_=+?"

        /** Random password from the system's secure generator. Nothing leaves the phone. */
        fun generate(length: Int = 18, symbols: Boolean = true): String {
            val rnd = SecureRandom()
            val pool = if (symbols) ALPHABET + SYMBOLS else ALPHABET
            return buildString { repeat(length.coerceIn(12, 32)) { append(pool[rnd.nextInt(pool.length)]) } }
        }
    }
}

@Composable
fun PasswordsScreen(onBack: () -> Unit, vm: PasswordsViewModel = hiltViewModel()) {
    SecureWindow()
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(UnlockSession.isOpen()) }
    var noLock by remember { mutableStateOf(false) }
    val unlock = rememberDeviceUnlock(onUnlocked = { unlocked = true }, onUnavailable = { noLock = true })

    if (!unlocked) {
        LScreen(title = "Passwords", onBack = onBack) {
            item { Text("Stored only on this phone.", style = MaterialTheme.typography.bodyLarge, color = L.InkMuted) }
            if (noLock) item { Text("Set a screen lock in the phone's settings to use Passwords.", style = MaterialTheme.typography.bodyMedium, color = L.Danger) }
            item { LButton("Unlock", { unlock() }) }
        }
        return
    }

    val rows by vm.rows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var shown by remember { mutableStateOf<PasswordSecret?>(null) }
    var editing by remember { mutableStateOf<PasswordSecret?>(null) }
    var backup by remember { mutableStateOf(false) }
    val overdue = vm.overdue()
    val list = rows.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }

    LScreen(title = "Passwords", onBack = onBack, action = { LTextLink("Backup", { backup = true }); LAddAction({ adding = true }, "Add password") }) {
        item { LField(query, { query = it }, "Search") }
        if (overdue.isNotEmpty()) {
            item { Text("${overdue.size} older than their change date", style = MaterialTheme.typography.bodySmall, color = L.Danger) }
        }
        item { Text("Stored only on this phone.", style = MaterialTheme.typography.bodySmall, color = L.InkMuted) }
        if (rows.isEmpty()) item { LEmpty(Icons.Filled.Lock, "No passwords saved.") }
        list.forEach { p ->
            item(key = p.id) {
                Column(
                    Modifier.fillMaxWidth().clickable { vm.open(p.id) { shown = it } }.heightIn(min = 56.dp).padding(vertical = 8.dp),
                ) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                    p.site?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.InkMuted, maxLines = 1) }
                }
            }
        }
    }

    if (adding) PasswordForm(null, vm) { adding = false }
    if (backup) BackupSheet(vm) { backup = false }
    editing?.let { PasswordForm(it, vm) { editing = null } }
    shown?.let { p ->
        PasswordDetail(p, context,
            onEdit = { shown = null; editing = p },
            onDelete = { vm.delete(p.id); shown = null },
            onDismiss = { shown = null })
    }
}

@Composable
private fun PasswordDetail(p: PasswordSecret, context: Context, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    LSheet(title = p.title, onDismiss = onDismiss, primary = "Edit", onPrimary = onEdit, secondary = "Delete", onSecondary = { confirm = true }, secondaryDestructive = true) {
        if (p.username.isNotEmpty()) {
            Field("username", p.username) { copy(context, p.username, "Username"); toast = "Copied. Clears in 30 s." }
        }
        Field("password", if (reveal) p.secret else "••••••••••••", extra = if (reveal) "Hide" else "Show", onExtra = { reveal = !reveal }) {
            copy(context, p.secret, "Password"); toast = "Copied. Clears in 30 s."
        }
        p.site?.takeIf { it.isNotBlank() }?.let { site ->
            Field("site", site, extra = "Open", onExtra = {
                val url = if (site.startsWith("http")) site else "https://$site"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { }
        }
        Text("changed " + java.time.Instant.ofEpochMilli(p.lastChanged).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
        if (p.note.isNotBlank()) Field("note", p.note) { }
        toast?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Accent) }
    }
    if (confirm) LConfirmDelete(onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false })
}

@Composable
private fun Field(label: String, value: String, extra: String? = null, onExtra: () -> Unit = {}, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalArrangement = Arrangement.Center) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = L.Ink, modifier = Modifier.weight(1f))
            Row {
                if (extra != null) LTextLink(extra, onExtra)
                if (label != "site" && label != "note") LTextLink("Copy", onCopy)
            }
        }
    }
}

@Composable
private fun PasswordForm(existing: PasswordSecret?, vm: PasswordsViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var site by rememberSaveable { mutableStateOf(existing?.site.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var secret by rememberSaveable { mutableStateOf(existing?.secret.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var every by rememberSaveable { mutableStateOf(existing?.changeEveryDays ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }
    LSheet(
        title = if (existing == null) "New password" else "Edit",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.save(existing?.id, title, site, username, secret, note, every.takeIf { it > 0 }, onError = { error = it }, onDone = onDismiss) },
    ) {
        LField(title, { title = it }, "Title")
        LField(site, { site = it }, "Site or app")
        LField(username, { username = it }, "Username")
        LField(secret, { secret = it }, "Password")
        LGhostButton("Generate a strong password", { secret = PasswordsViewModel.generate() })
        Text("change every", style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        ChipsRow { listOf(0 to "Never", 90 to "90 days", 180 to "180 days", 365 to "365 days").forEach { (d, t) -> LChip(t, every == d, { every = d }) } }
        LField(note, { note = it }, "Note", singleLine = false, minLines = 2)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
}

/** Copies as a sensitive clip (hidden from previews on Android 13+) and clears it after 30 seconds. */
private fun copy(context: Context, text: String, label: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            if (cm.primaryClip?.getItemAt(0)?.text?.toString() == text) {
                if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 30_000)
}

/** Export or restore all passwords as one file protected by a passphrase. The only way they leave the phone. */
@Composable
private fun BackupSheet(vm: PasswordsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.backup(context, uri, pass) { message = it }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(context, uri, pass) { message = it }
    }
    LSheet(title = "Backup", onDismiss = onDismiss) {
        Text("A backup is one file sealed with a passphrase you choose. Without the passphrase it cannot be opened.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        LField(pass, { pass = it }, "Passphrase")
        LField(again, { again = it }, "Repeat passphrase (for a new backup)")
        LGhostButton("Save a backup file", {
            if (pass.length < 8) message = "Use at least 8 characters"
            else if (pass != again) message = "The passphrases do not match"
            else save.launch("passwords.ledgervault")
        })
        LGhostButton("Restore from a backup file", {
            if (pass.isEmpty()) message = "Enter the passphrase first" else open.launch(arrayOf("*/*"))
        })
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Accent) }
    }
}
