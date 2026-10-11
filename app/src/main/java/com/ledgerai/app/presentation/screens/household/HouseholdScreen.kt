package com.ledgerai.app.presentation.screens.household

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.household.HouseholdRepository
import com.ledgerai.app.domain.household.Household
import com.ledgerai.app.domain.household.HouseholdMember
import com.ledgerai.app.domain.household.HouseholdMembership
import com.ledgerai.app.domain.household.HouseholdRole
import com.ledgerai.app.domain.household.MembershipDecision
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LFab
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSearchToolbar
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.components.rememberPhotoActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModel(
    private val repository: HouseholdRepository,
    private val userId: String,
) : ViewModel() {

    val households: StateFlow<List<Household>> = repository.observeMine(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    val members: StateFlow<List<HouseholdMember>> = _selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeMembers(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun select(id: String) {
        _selectedId.value = id
        _message.value = null
    }

    fun create(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val id = repository.createHousehold(name, userId)
            if (id == null) {
                _message.value = "Use a name up to ${HouseholdMembership.NAME_MAX} characters"
                onDone(false)
            } else {
                _selectedId.value = id
                _message.value = null
                onDone(true)
            }
        }
    }

    fun addMember(memberUserId: String, displayName: String) {
        val householdId = _selectedId.value ?: return
        viewModelScope.launch {
            _message.value = repository.addMember(householdId, userId, memberUserId, displayName).text()
        }
    }

    fun leave() {
        val householdId = _selectedId.value ?: return
        viewModelScope.launch {
            when (val decision = repository.leave(householdId, userId)) {
                MembershipDecision.Allow -> {
                    _selectedId.value = null
                    _message.value = null
                }
                is MembershipDecision.Deny -> _message.value = decision.reason
            }
        }
    }

    fun remove(memberUserId: String) {
        val householdId = _selectedId.value ?: return
        viewModelScope.launch {
            _message.value = repository.removeMember(householdId, userId, memberUserId).text()
        }
    }
}

private fun MembershipDecision.text(): String? = when (this) {
    MembershipDecision.Allow -> null
    is MembershipDecision.Deny -> reason
}

/**
 * Repository entry point. [HouseholdRepository] needs a Room DAO, so prefer the
 * state overload below when Hilt is not wired yet.
 */
@Composable
fun HouseholdScreen(
    userId: String,
    repository: HouseholdRepository,
    onBack: (() -> Unit)? = null,
    selfName: String = "",
) {
    val viewModel: HouseholdViewModel = viewModel(key = userId) {
        HouseholdViewModel(repository, userId)
    }
    val households by viewModel.households.collectAsState()
    val members by viewModel.members.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    HouseholdScreen(
        households = households,
        members = members,
        selectedId = selectedId,
        message = message,
        userId = userId,
        selfName = selfName,
        onSelect = viewModel::select,
        onCreate = viewModel::create,
        onAddMember = viewModel::addMember,
        onLeave = viewModel::leave,
        onRemove = viewModel::remove,
        onBack = onBack,
    )
}

/**
 * Households the caller already loaded, plus create. No sample rows.
 *
 * [onCreate] receives a trimmed name and must call `onDone(true)` only after the
 * household is stored. `onDone(false)` keeps the sheet open; put the reason in [message].
 */
@Composable
fun HouseholdScreen(
    households: List<Household>,
    members: List<HouseholdMember>,
    selectedId: String?,
    message: String?,
    userId: String,
    selfName: String = "",
    onSelect: (String) -> Unit,
    onCreate: (name: String, onDone: (Boolean) -> Unit) -> Unit,
    onAddMember: (memberUserId: String, displayName: String) -> Unit,
    onLeave: () -> Unit,
    onRemove: (memberUserId: String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val visible = households.filter { it.deletedAt == null }
    val selected = visible.find { it.id == selectedId }
    val visibleMembers = if (selected == null) {
        emptyList()
    } else {
        HouseholdMembership.activeMembers(members).filter { it.householdId == selected.id }
    }
    val me = visibleMembers.find { it.userId == userId }
    val actorIsOwner = me?.role == HouseholdRole.OWNER
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateMessage by rememberSaveable { mutableStateOf(false) }
    var addingMember by rememberSaveable { mutableStateOf(false) }
    var memberId by rememberSaveable { mutableStateOf("") }
    var memberName by rememberSaveable { mutableStateOf("") }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val banner = message
    val context = LocalContext.current
    val onlyOne = visible.size == 1
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var photoNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val photos: PhotoSaveViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val photosIn = rememberPhotoActions(max = 6) { uris ->
        scope.launch {
            val n = photos.fileWithAi(uris, "This is a photo of a home or the people in it.")
            photoNotice = if (n > 0) "Adding from photo…" else "Couldn't read that photo."
        }
    }
    val q = query.trim()
    val listed = if (q.isEmpty()) visible else visible.filter { it.name.contains(q, ignoreCase = true) }
    val listedMembers = if (q.isEmpty()) {
        visibleMembers
    } else {
        visibleMembers.filter { memberLabel(it, selfName, it.userId == userId).contains(q, ignoreCase = true) }
    }

    LaunchedEffect(visible.map { it.id }) {
        if (visible.size == 1 && selectedId != visible.first().id) onSelect(visible.first().id)
    }

    Column(Modifier.fillMaxSize()) {
    LSearchToolbar(
        query = query,
        onQuery = { query = it },
        searching = searching,
        onSearching = { searching = it },
        hint = "Search household",
    ) {
        LToolButton(Icons.Filled.PhotoCamera, "Camera", photosIn.shoot)
        LToolButton(Icons.Filled.PhotoLibrary, "Photo", photosIn.pick)
    }
    LScreen(
        title = "Household",
        modifier = Modifier.weight(1f),
        onBack = onBack,
        fab = {
            LFab(Icons.Filled.Add, onClick = {
                nameError = null
                showCreateMessage = false
                creating = true
            }, label = "New household")
        },
    ) {
        if (!photoNotice.isNullOrBlank()) {
            item { Text(photoNotice!!, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
        }
        if (!creating && !banner.isNullOrBlank()) {
            item {
                Text(banner, style = MaterialTheme.typography.bodyMedium, color = L.Danger)
            }
        }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Filled.Home, "No household yet") }
        } else if (!onlyOne) {
            item {
                if (listed.isEmpty()) {
                    LEmpty(Icons.Filled.Home, "No matches")
                } else LGroup {
                    listed.forEachIndexed { index, household ->
                        if (index > 0) LGroupDivider()
                        val on = household.id == selectedId
                        LGroupRow(
                            title = household.name,
                            sub = if (on) memberCount(visibleMembers.size) else null,
                            icon = if (on) Icons.Filled.Check else null,
                            onClick = { onSelect(household.id) },
                        )
                    }
                }
            }
        }
        if (selected != null) {
            if (!onlyOne) item { LSection("Members") }
            else if (visibleMembers.isNotEmpty()) {
                item {
                    Text(
                        memberCount(visibleMembers.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.InkMuted,
                    )
                }
            }
            if (listedMembers.isEmpty()) {
                item { LEmpty(Icons.Filled.Home, if (q.isEmpty()) "No members yet" else "No matches") }
            } else {
                item {
                    LGroup {
                        listedMembers.forEachIndexed { index, member ->
                            if (index > 0) LGroupDivider()
                            MemberLine(
                                member = member,
                                selfName = selfName,
                                isYou = member.userId == userId,
                                canRemove = actorIsOwner &&
                                    member.userId != userId &&
                                    member.role != HouseholdRole.OWNER,
                                onRemove = { pendingRemove = member.userId },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LGhostButton(
                        text = "Invite",
                        onClick = { shareInvite(context, selfName, userId) },
                    )
                    if (actorIsOwner) {
                        LGhostButton(text = "Add member", onClick = { addingMember = true })
                    }
                }
            }
            if (me != null) {
                val ownerCount = visibleMembers.count { it.role == HouseholdRole.OWNER }
                when (val decision = HouseholdMembership.leave(me, ownerCount)) {
                    MembershipDecision.Allow -> item {
                        LGhostButton("Leave", onClick = { confirmLeave = true })
                    }
                    is MembershipDecision.Deny -> item {
                        Text(decision.reason, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
                    }
                }
            }
        }
    }
    }

    if (creating) {
        val sheetError = nameError ?: banner.takeIf { showCreateMessage }
        LSheet(
            title = "New household",
            onDismiss = {
                creating = false
                nameError = null
                showCreateMessage = false
            },
            primary = "Create",
            onPrimary = {
                val checked = HouseholdMembership.checkedName(name)
                if (checked == null) {
                    nameError = "Use a name up to ${HouseholdMembership.NAME_MAX} characters"
                } else {
                    nameError = null
                    showCreateMessage = true
                    onCreate(checked) { ok ->
                        if (ok) {
                            creating = false
                            name = ""
                            showCreateMessage = false
                        }
                    }
                }
            },
            primaryEnabled = name.isNotBlank(),
        ) {
            LField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = "Name",
            )
            if (!sheetError.isNullOrBlank()) {
                Text(sheetError, style = MaterialTheme.typography.bodyMedium, color = L.Danger)
            }
        }
    }

    if (addingMember) {
        val clipboard = LocalClipboardManager.current
        LSheet(
            title = "Add member",
            onDismiss = { addingMember = false },
            primary = "Save",
            onPrimary = {
                onAddMember(memberId.trim(), memberName.trim())
                memberId = ""
                memberName = ""
                addingMember = false
            },
            primaryEnabled = memberName.isNotBlank() && memberId.isNotBlank(),
        ) {
            LField(memberName, { memberName = it }, "Name")
            LGhostButton("Paste invite", onClick = {
                clipboard.getText()?.text?.let { raw ->
                    val (parsedName, id) = parseInvite(raw)
                    if (id.isNotBlank()) memberId = id
                    if (parsedName.isNotBlank()) memberName = parsedName
                }
            })
        }
    }

    if (confirmLeave) {
        Confirm(
            title = "Leave this household?",
            confirm = "Leave",
            onConfirm = {
                confirmLeave = false
                onLeave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    val removing = visibleMembers.find { it.userId == pendingRemove }
    if (removing != null && pendingRemove != null) {
        Confirm(
            title = "Remove ${memberLabel(removing, selfName, removing.userId == userId)}?",
            confirm = "Remove",
            onConfirm = {
                val id = pendingRemove
                pendingRemove = null
                if (id != null) onRemove(id)
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
private fun MemberLine(
    member: HouseholdMember,
    selfName: String,
    isYou: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    val label = memberLabel(member, selfName, isYou)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialsAvatar(label)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = L.OnBox,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isYou && label != "You") {
                Text("You", style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted)
            }
        }
        if (member.role == HouseholdRole.OWNER) OwnerChip()
        if (canRemove) {
            Text(
                "Remove",
                color = L.Danger,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun InitialsAvatar(name: String) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(L.BoxDeep),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = MaterialTheme.typography.labelLarge, color = L.OnBox)
    }
}

@Composable
private fun OwnerChip() {
    Text(
        "Owner",
        style = MaterialTheme.typography.labelLarge,
        color = L.Primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, L.Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun Confirm(title: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = L.Page,
        title = { Text(title, color = L.Ink) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirm, color = L.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", color = L.InkMuted)
            }
        },
    )
}

private fun memberLabel(member: HouseholdMember, selfName: String, isYou: Boolean): String = when {
    member.displayName.isNotBlank() -> member.displayName
    isYou && selfName.isNotBlank() -> selfName
    isYou -> "You"
    else -> "Member"
}

private fun shareInvite(context: android.content.Context, name: String, userId: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, inviteText(name, userId))
    }
    val chooser = Intent.createChooser(send, "Invite")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** A shareable invite. The id stays in the link so Paste invite can still add the person. */
private fun inviteText(name: String, userId: String): String {
    val who = name.trim().ifBlank { "Someone" }
    return "$who\nhttps://lifeline.app/invite/$userId"
}

private fun parseInvite(raw: String): Pair<String, String> {
    val text = raw.trim()
    if (text.contains("::") && !text.contains("://")) {
        val parts = text.split("::", limit = 2)
        if (parts.size == 2) return parts[0].trim() to parts[1].trim()
    }
    val id = Regex("""invite/(\S+)""").find(text)?.groupValues?.get(1).orEmpty()
    if (id.isNotBlank()) {
        val parsedName = text.lineSequence().firstOrNull { line ->
            line.isNotBlank() && !line.contains("invite/")
        }.orEmpty()
        return parsedName to id
    }
    return "" to text
}

private fun memberCount(count: Int): String = if (count == 1) "1 member" else "$count members"

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return (parts[0].take(1) + parts[1].take(1)).uppercase()
}
