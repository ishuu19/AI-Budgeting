package com.ledgerai.app.presentation.screens.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerai.app.data.media.AnalysisState
import com.ledgerai.app.data.media.MediaRepository
import com.ledgerai.app.data.preferences.UserSession
import com.ledgerai.app.data.receipts.ReceiptRepository
import com.ledgerai.app.domain.receipts.Receipt
import com.ledgerai.app.domain.receipts.ValueConfidence
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LConfirm
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.components.money
import com.ledgerai.app.presentation.components.rememberPhotoActions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    media: MediaRepository,
    session: UserSession,
) : ViewModel() {

    private val userId = session.userInfo.map { it.userId }.distinctUntilChanged()

    val saved: StateFlow<List<Receipt>> = combine(userId, receipts.observeAll()) { id, all ->
        all.filter { it.userId == null || it.userId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Photos the AI has not read yet. They become receipts when it finishes. */
    val reading: StateFlow<Int> = userId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else media.observeRecent(id) }
        .map { rows -> rows.count { it.analysisState == AnalysisState.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun delete(id: Long) {
        viewModelScope.launch { receipts.delete(id) }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/**
 * Receipts you have photographed. The AI reads the shop, date, total and lines, and keeps them with the
 * photo. Open one to check or correct it. Nothing here creates a transaction.
 */
@Composable
fun ReceiptsScreen(
    onOpen: (Long) -> Unit,
    viewModel: ReceiptsViewModel = hiltViewModel(),
    photos: PhotoSaveViewModel = hiltViewModel(),
) {
    val saved by viewModel.saved.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Receipt?>(null) }
    val actions = rememberPhotoActions(max = 6) { uris ->
        scope.launch {
            val n = photos.fileWithAi(
                uris,
                "This is a receipt. Read the shop, the date, the total and every line with its price.",
            )
            notice = if (n > 0) "Reading $n receipt${if (n == 1) "" else "s"}…" else "Couldn't read that photo."
        }
    }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000)
        notice = null
    }

    LScreen(title = "Receipts") {
        item(key = "add") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LGhostButton("Scan receipt", actions.shoot, Modifier.weight(1f))
                LGhostButton("From gallery", actions.pick, Modifier.weight(1f))
            }
        }
        val status = notice ?: if (reading > 0) "Reading $reading photo${if (reading == 1) "" else "s"}…" else null
        if (status != null) {
            item(key = "status") { Text(status, style = MaterialTheme.typography.bodyMedium, color = L.InkMuted) }
        }
        if (saved.isEmpty()) {
            item(key = "empty") { LEmpty(Icons.Filled.ReceiptLong, "Photograph a receipt to keep it") }
        } else {
            item(key = "list") {
                LGroup {
                    saved.forEachIndexed { index, receipt ->
                        if (index > 0) LGroupDivider()
                        LGroupRow(
                            title = receipt.merchant ?: "Unknown shop",
                            sub = listOfNotNull(
                                receipt.purchasedOn?.format(dateFormat),
                                when {
                                    receipt.locallyConfirmed -> "Checked"
                                    receipt.merchantConfidence == ValueConfidence.INFERRED || receipt.total != null -> "To check"
                                    else -> null
                                },
                            ).joinToString(" · ").ifBlank { null },
                            trailing = receipt.total?.let { money(it) },
                            onClick = { onOpen(receipt.id) },
                            end = { LToolButton(Icons.Filled.Delete, "Delete receipt", { deleting = receipt }) },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { receipt ->
        LConfirm(
            title = "Delete this receipt?",
            body = "The photo and the details are removed. Money you logged from it stays.",
            onConfirm = { viewModel.delete(receipt.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
