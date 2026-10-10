package com.ledgerai.app.presentation.screens.wardrobe

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.wardrobe.Outfit
import com.ledgerai.app.domain.wardrobe.WardrobeItem
import com.ledgerai.app.domain.wardrobe.WearLog
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WardrobeViewModel(
    private val repository: WardrobeRepository,
    private val userId: String,
) : ViewModel() {

    val garments: StateFlow<List<WardrobeItem>> = repository.observeGarments(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outfits: StateFlow<List<Outfit>> = repository.observeOutfits(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(
        name: String,
        type: String,
        color: String,
        season: String,
        laundry: String,
        photoPath: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val id = repository.addGarment(
                userId = userId,
                name = name,
                type = type,
                colors = listOf(color),
                seasons = listOf(season),
                photoPath = photoPath,
                laundryStatus = laundry,
            )
            _message.value = if (id == 0L) "Name the garment" else null
            onDone(id != 0L)
        }
    }

    fun logWearToday(itemId: Long) {
        viewModelScope.launch {
            _message.value = when (repository.logWear(userId, listOf(itemId), LocalDate.now())) {
                is WearLog.Saved -> "Logged for today"
                WearLog.DuplicateSameDay -> "Already logged for today"
                WearLog.UnknownGarment -> "That garment is not in your wardrobe"
            }
        }
    }
}

@Composable
fun WardrobeScreen(
    userId: String,
    repository: WardrobeRepository,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: WardrobeViewModel = viewModel(key = userId) {
        WardrobeViewModel(repository, userId)
    }
    val garments by viewModel.garments.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val message by viewModel.message.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf("") }
    var laundry by rememberSaveable { mutableStateOf("") }
    var photoPath by rememberSaveable { mutableStateOf("") }

    LScreen(title = "Wardrobe", onBack = onBack) {
        item {
            LButton("Add garment", onClick = { adding = true })
        }
        if (!message.isNullOrBlank()) {
            item {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = L.Ink,
                )
            }
        }
        if (garments.isEmpty()) {
            item { LEmpty(Icons.Filled.Checkroom, "No clothes yet. Add a garment.") }
        } else {
            items(garments, key = { it.id }) { garment ->
                LRow(
                    title = garment.name,
                    sub = garmentLine(garment),
                    end = {
                        IconButton(
                            onClick = { viewModel.logWearToday(garment.id) },
                            modifier = Modifier.semantics { contentDescription = "Log wear for today" },
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = L.Gold)
                        }
                    },
                )
            }
        }
        if (outfits.isNotEmpty()) {
            item { LSection("Worn") }
            items(outfits, key = { "worn-${it.id}" }) { outfit ->
                LRow(
                    title = wornTitle(outfit, garments),
                    sub = wornSub(outfit),
                )
            }
        }
    }

    if (adding) {
        LSheet(
            title = "Add garment",
            onDismiss = { adding = false },
            primary = "Save",
            onPrimary = {
                viewModel.add(name, type, color, season, laundry, photoPath) { saved ->
                    if (saved) {
                        name = ""
                        type = ""
                        color = ""
                        season = ""
                        laundry = ""
                        photoPath = ""
                        adding = false
                    }
                }
            },
            primaryEnabled = name.isNotBlank(),
        ) {
            LField(name, { name = it }, "Name")
            LField(type, { type = it }, "Type")
            LField(color, { color = it }, "Color")
            LField(season, { season = it }, "Season")
            LField(laundry, { laundry = it }, "Laundry status")
            LField(photoPath, { photoPath = it }, "Photo path")
            Text(
                "A blank color stays unknown. The photo field stores a path only.",
                style = MaterialTheme.typography.bodySmall,
                color = L.InkMuted,
            )
        }
    }
}

private fun garmentLine(item: WardrobeItem): String =
    listOf(item.type, item.colors.joinToString(", "), item.laundryStatus)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

private fun wornTitle(outfit: Outfit, garments: List<WardrobeItem>): String {
    val names = outfit.itemIds.map { id -> garments.find { it.id == id }?.name ?: "Unknown garment" }
    return names.joinToString(", ")
}

private fun wornSub(outfit: Outfit): String {
    val date = outfit.wornOn?.format(WORN_DATE).orEmpty()
    return if (outfit.occasion.isBlank()) date else "$date · ${outfit.occasion}"
}

private val WORN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
