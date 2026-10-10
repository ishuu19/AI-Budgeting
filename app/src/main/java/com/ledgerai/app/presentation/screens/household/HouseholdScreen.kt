package com.ledgerai.app.presentation.screens.household

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
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

    fun addMember(memberUserId: String) {
        val householdId = _selectedId.value ?: return
        viewModelScope.launch {
            _message.value = repository.addMember(householdId, userId, memberUserId).text()
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
    onSelect: (String) -> Unit,
    onCreate: (name: String, onDone: (Boolean) -> Unit) -> Unit,
    onAddMember: (memberUserId: String) -> Unit,
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
    var memberId by rememberSaveable { mutableStateOf("") }
    val banner = message

    LScreen(
        title = "Households",
        onBack = onBack,
        action = {
            TextButton(onClick = {
                nameError = null
                showCreateMessage = false
                creating = true
            }) {
                Text("Add", color = L.Box, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        item {
            Text(
                "Homes you belong to",
                style = MaterialTheme.typography.bodyMedium,
                color = L.InkMuted,
            )
        }
        if (!creating && !banner.isNullOrBlank()) {
            item {
                Text(
                    banner,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (visible.isEmpty()) {
            item { LEmpty(Icons.Filled.Home, "No households yet") }
        } else {
            items(visible, key = { it.id }) { household ->
                LRow(
                    title = household.name,
                    sub = if (household.id == selectedId) "Selected" else "Tap to see members",
                    icon = Icons.Filled.Home,
                    onClick = { onSelect(household.id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (selected != null) {
            item { LSection(selected.name) }
            if (visibleMembers.isEmpty()) {
                item {
                    Text(
                        "No members yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.InkMuted,
                    )
                }
            } else {
                item {
                    Text(
                        memberCount(visibleMembers.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = L.InkMuted,
                    )
                }
                items(visibleMembers, key = { "${it.householdId}:${it.userId}" }) { member ->
                    MemberRow(
                        member = member,
                        isYou = member.userId == userId,
                        canRemove = actorIsOwner &&
                            member.userId != userId &&
                            member.role != HouseholdRole.OWNER,
                        onRemove = { onRemove(member.userId) },
                    )
                }
            }
            if (actorIsOwner) {
                item {
                    LField(memberId, { memberId = it }, "Member user id")
                }
                item {
                    LGhostButton(
                        text = "Add member",
                        enabled = memberId.isNotBlank(),
                        onClick = {
                            onAddMember(memberId.trim())
                            memberId = ""
                        },
                    )
                }
            }
            if (me != null) {
                val ownerCount = visibleMembers.count { it.role == HouseholdRole.OWNER }
                when (val decision = HouseholdMembership.leave(me, ownerCount)) {
                    MembershipDecision.Allow -> item {
                        LGhostButton("Leave", onClick = onLeave)
                    }
                    is MembershipDecision.Deny -> item {
                        Text(
                            decision.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = L.InkMuted,
                        )
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
                Text(
                    sheetError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: HouseholdMember,
    isYou: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    LRow(
        title = member.userId,
        sub = if (isYou) "You" else null,
        trailing = if (member.role == HouseholdRole.OWNER) "Owner" else "Member",
        modifier = Modifier.heightIn(min = 48.dp),
        end = if (canRemove) {
            {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${member.userId}",
                        tint = L.OnBox,
                    )
                }
            }
        } else {
            null
        },
    )
}

private fun memberCount(count: Int): String = if (count == 1) "1 member" else "$count members"
