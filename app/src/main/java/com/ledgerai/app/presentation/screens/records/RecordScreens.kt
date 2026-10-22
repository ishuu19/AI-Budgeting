package com.ledgerai.app.presentation.screens.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.achievements.AchievementDao
import com.ledgerai.app.data.achievements.AchievementEntity
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.repository.GoalRepository
import com.ledgerai.app.data.wishes.WishDao
import com.ledgerai.app.data.wishes.WishEntity
import com.ledgerai.app.data.wishes.WishStatus
import com.ledgerai.app.domain.model.Goal
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LAddAction
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LConfirmDelete
import com.ledgerai.app.presentation.components.LDateField
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LThreadRow
import com.ledgerai.app.presentation.components.money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// ─── Achievements ───────────────────────────────────────────────────────────────────────────

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val dao: AchievementDao,
    private val media: com.ledgerai.app.data.media.MediaAssetDao,
    private val session: UserSession,
) : ViewModel() {

    fun photos(id: String) = media.observeLinked("achievement", id)

    fun addPhoto(context: android.content.Context, achievementId: String, uri: android.net.Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            com.ledgerai.app.data.media.VaultFiles.import(context, uri, session.userInfo.first().userId, "achievement", achievementId, locked = false)
                .onSuccess { row ->
                    media.upsert(row)
                    if (row.uploadState == com.ledgerai.app.data.media.UploadState.PENDING) com.ledgerai.app.worker.CaptureWorker.kick(context)
                }
                .onFailure { onError(it.message ?: "Could not add that photo") }
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch { media.softDelete(id, System.currentTimeMillis()) }
    }

    val rows: StateFlow<List<AchievementEntity>> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, title: String, on: LocalDate, note: String, onError: (String) -> Unit, onDone: () -> Unit) {
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 120) return onError("Title must be 1 to 120 characters")
        if (on.isAfter(LocalDate.now())) return onError("An achievement has already happened. Pick today or earlier.")
        viewModelScope.launch {
            val uid = session.userInfo.first().userId.ifBlank { null }
            val old = id?.let { dao.get(it) }
            dao.upsert(
                AchievementEntity(
                    id = old?.id ?: id ?: UUID.randomUUID().toString(),
                    userId = uid,
                    title = clean,
                    happenedOn = on,
                    note = note.trim().take(1000),
                    sourceTaskId = old?.sourceTaskId,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.softDelete(id, System.currentTimeMillis()) }
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, vm: AchievementsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    var editing by remember { mutableStateOf<AchievementEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    LScreen(title = "Achievements", onBack = onBack, action = { LAddAction({ adding = true }, "Add achievement") }) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.ACHIEVEMENT) }
        if (rows.isEmpty()) item { LEmpty(Icons.Filled.EmojiEvents, "Nothing here yet.") }
        var lastYear = -1
        rows.forEach { a ->
            if (a.happenedOn.year != lastYear) {
                lastYear = a.happenedOn.year
                item(key = "y$lastYear") { LSection(lastYear.toString()) }
            }
            item(key = a.id) { LThreadRow(fmt.format(a.happenedOn), a.title, detail = a.note.ifBlank { null }, onClick = { editing = a }) }
        }
    }
    if (adding) AchievementSheet(null, vm) { adding = false }
    editing?.let { AchievementSheet(it, vm) { editing = null } }
}

@Composable
private fun AchievementSheet(row: AchievementEntity?, vm: AchievementsViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(row?.title.orEmpty()) }
    var on by remember { mutableStateOf(row?.happenedOn ?: LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf(row?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val achievementId = remember { row?.id ?: UUID.randomUUID().toString() }
    val photos by vm.photos(achievementId).collectAsState(initial = emptyList())
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && photos.size < 3) vm.addPhoto(context, achievementId, uri) { error = it }
    }
    LSheet(
        title = if (row == null) "New achievement" else "Achievement",
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.save(achievementId, title, on, note, onError = { error = it }, onDone = onDismiss) },
        secondary = if (row != null) "Delete" else null,
        onSecondary = { confirm = true },
        secondaryDestructive = true,
    ) {
        LField(title, { title = it }, "What did you finish?")
        LDateField("Date", on, { on = it })
        LField(note, { note = it }, "Note", singleLine = false, minLines = 2)
        photos.forEach { p ->
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                com.ledgerai.app.presentation.components.LocalPhoto(
                    com.ledgerai.app.data.media.VaultFiles.localFile(context, p),
                    Modifier.size(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                )
                com.ledgerai.app.presentation.components.LTextLink("Remove", { vm.deletePhoto(p.id) })
            }
        }
        if (photos.size < 3) LGhostButton("Add a photo", { picker.launch("image/*") })
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
    if (confirm && row != null) LConfirmDelete(onConfirm = { vm.delete(row.id); confirm = false; onDismiss() }, onDismiss = { confirm = false })
}

// ─── Wishes ─────────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class WishesViewModel @Inject constructor(
    private val dao: WishDao,
    private val goals: GoalRepository,
    private val session: UserSession,
) : ViewModel() {

    val rows: StateFlow<List<WishEntity>> = dao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: String?, name: String, price: String, priority: Int, wantedBy: String, link: String, note: String,
        onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 120) return onError("Name must be 1 to 120 characters")
        val amount = price.trim().takeIf { it.isNotEmpty() }?.replace(",", "")?.toDoubleOrNull()
        if (price.isNotBlank() && (amount == null || amount < 0)) return onError("Price must be a number")
        val month = wantedBy.trim().takeIf { it.isNotEmpty() }
        if (month != null && runCatching { YearMonth.parse(month) }.isFailure) return onError("Wanted by must look like 2026-12")
        viewModelScope.launch {
            val uid = session.userInfo.first().userId.ifBlank { null }
            val now = System.currentTimeMillis()
            val old = id?.let { dao.get(it) }
            dao.upsert(
                (old ?: WishEntity(id = UUID.randomUUID().toString(), name = clean, createdAt = now)).copy(
                    userId = uid ?: old?.userId,
                    name = clean,
                    amount = amount,
                    priority = priority.coerceIn(1, 3),
                    wantedBy = month,
                    link = link.trim().ifBlank { null },
                    note = note.trim().take(1000),
                    updatedAt = now,
                )
            )
            onDone()
        }
    }

    fun setStatus(wish: WishEntity, status: String, onDone: () -> Unit) {
        viewModelScope.launch {
            dao.upsert(wish.copy(status = status, updatedAt = System.currentTimeMillis()))
            onDone()
        }
    }

    /** Creates a savings goal from the wish. Needs a known price; a wish never invents one. */
    fun startSaving(wish: WishEntity, onError: (String) -> Unit, onDone: () -> Unit) {
        val price = wish.amount ?: return onError("Add a price first")
        viewModelScope.launch {
            val by = wish.wantedBy?.let { runCatching { YearMonth.parse(it).atEndOfMonth() }.getOrNull() }
            val goalId = goals.insert(Goal(name = wish.name, targetAmount = price, targetDate = by, userId = session.userInfo.first().userId.ifBlank { null }))
            dao.upsert(wish.copy(status = WishStatus.SAVING, goalId = goalId.toString(), updatedAt = System.currentTimeMillis()))
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.softDelete(id, System.currentTimeMillis()) }
    }
}

@Composable
fun WishesScreen(onBack: () -> Unit, vm: WishesViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsState()
    var editing by remember { mutableStateOf<WishEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val open = rows.filter { it.status == WishStatus.WANT || it.status == WishStatus.SAVING }
    val closed = rows.filter { it.status == WishStatus.BOUGHT || it.status == WishStatus.DROPPED }
    val now = System.currentTimeMillis()

    LScreen(title = "Wishes", onBack = onBack, action = { LAddAction({ adding = true }, "Add wish") }) {
        item { com.ledgerai.app.presentation.screens.library.LibraryAiBar(com.ledgerai.app.domain.assistant.AssistantArea.WISH) }
        if (rows.isEmpty()) item { LEmpty(Icons.Filled.Star, "Nothing on the list.") }
        open.forEach { w ->
            item(key = w.id) {
                Column(Modifier.fillMaxWidth().clickable { editing = w }.heightIn(min = 64.dp).padding(vertical = 10.dp)) {
                    Text(w.name, style = MaterialTheme.typography.titleSmall, color = L.Ink)
                    val price = w.amount?.let { money(it) } ?: "Price unknown"
                    val days = ((now - w.createdAt) / 86_400_000L).coerceAtLeast(0)
                    val state = if (w.status == WishStatus.SAVING) " · saving" else ""
                    Text("$price$state · wanted for $days days", style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                }
            }
        }
        if (closed.isNotEmpty()) item { LSection("bought and dropped") }
        closed.forEach { w ->
            item(key = w.id) {
                Column(Modifier.fillMaxWidth().clickable { editing = w }.padding(vertical = 10.dp)) {
                    Text(w.name, style = MaterialTheme.typography.titleSmall, color = L.InkMuted)
                    Text(w.status.lowercase(), style = MaterialTheme.typography.bodySmall, color = L.InkMuted)
                }
            }
        }
    }
    if (adding) WishSheet(null, vm) { adding = false }
    editing?.let { WishSheet(it, vm) { editing = null } }
}

@Composable
private fun WishSheet(wish: WishEntity?, vm: WishesViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(wish?.name.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(wish?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(wish?.priority ?: 2) }
    var wantedBy by rememberSaveable { mutableStateOf(wish?.wantedBy.orEmpty()) }
    var link by rememberSaveable { mutableStateOf(wish?.link.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(wish?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LSheet(
        title = if (wish == null) "New wish" else wish.name,
        onDismiss = onDismiss,
        primary = "Save",
        onPrimary = { vm.save(wish?.id, name, price, priority, wantedBy, link, note, onError = { error = it }, onDone = onDismiss) },
        secondary = if (wish != null) "Delete" else null,
        onSecondary = { confirm = true },
        secondaryDestructive = true,
    ) {
        LField(name, { name = it }, "What do you want?")
        LField(price, { price = it }, "Price (leave empty if unknown)")
        Text("priority", style = MaterialTheme.typography.labelMedium, color = L.InkMuted)
        ChipsRow { listOf(1 to "Low", 2 to "Medium", 3 to "High").forEach { (v, t) -> LChip(t, priority == v, { priority = v }) } }
        LField(wantedBy, { wantedBy = it }, "Wanted by (2026-12)")
        LField(link, { link = it }, "Link")
        LField(note, { note = it }, "Note", singleLine = false, minLines = 2)
        if (wish != null) {
            if (wish.status == WishStatus.WANT) LGhostButton("Start saving", { vm.startSaving(wish, { error = it }, onDismiss) })
            if (wish.status != WishStatus.BOUGHT) LGhostButton("Mark bought", { vm.setStatus(wish, WishStatus.BOUGHT, onDismiss) })
            if (wish.status != WishStatus.DROPPED) LGhostButton("Drop it", { vm.setStatus(wish, WishStatus.DROPPED, onDismiss) })
            if (wish.status == WishStatus.BOUGHT || wish.status == WishStatus.DROPPED) LGhostButton("Want it again", { vm.setStatus(wish, WishStatus.WANT, onDismiss) })
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = L.Danger) }
    }
    if (confirm && wish != null) LConfirmDelete(onConfirm = { vm.delete(wish.id); confirm = false; onDismiss() }, onDismiss = { confirm = false })
}
