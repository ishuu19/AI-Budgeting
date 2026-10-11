package com.ledgerai.app.presentation.screens.wardrobe

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.data.wardrobe.WardrobeRepository
import com.ledgerai.app.domain.wardrobe.Outfit
import com.ledgerai.app.domain.wardrobe.WardrobeItem
import com.ledgerai.app.domain.wardrobe.WearLog
import com.ledgerai.app.presentation.components.ChipsRow
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LButton
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LRow
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LocalPhoto
import com.ledgerai.app.presentation.components.PhotoButton
import com.ledgerai.app.presentation.components.PhotoField
import com.ledgerai.app.presentation.components.PhotoSaveViewModel
import com.ledgerai.app.presentation.components.rememberPhotoActions
import com.ledgerai.app.presentation.screens.transactions.DatePickChip
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

    fun say(text: String?) {
        _message.value = text
    }

    fun add(
        name: String,
        type: String,
        color: String,
        season: String,
        laundry: String,
        photoPath: String?,
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

    fun logWear(itemId: Long, wornOn: LocalDate) {
        viewModelScope.launch {
            _message.value = when (repository.logWear(userId, listOf(itemId), wornOn)) {
                is WearLog.Saved -> "Logged for ${wornOn.format(WORN_DATE)}"
                WearLog.DuplicateSameDay -> "Already logged for ${wornOn.format(WORN_DATE)}"
                WearLog.UnknownGarment -> "That garment is not in your wardrobe"
            }
        }
    }

    fun remove(itemId: Long) {
        viewModelScope.launch { repository.softDeleteGarment(userId, itemId) }
    }
}

private val Types = listOf("Top", "Bottom", "Dress", "Outerwear", "Shoes", "Accessory", "Other")
private val Seasons = listOf("All year", "Spring", "Summer", "Autumn", "Winter")
private val Laundry = listOf("Clean", "Worn", "In the wash")

/**
 * Wardrobe. The fast way is a photo: the AI names, describes and files it. The manual form uses a photo
 * picker and chips, never a typed file path or date.
 */
@Composable
fun WardrobeScreen(
    userId: String,
    repository: WardrobeRepository,
    onBack: (() -> Unit)? = null,
) {
    val viewModel: WardrobeViewModel = viewModel(key = userId) { WardrobeViewModel(repository, userId) }
    val photos: PhotoSaveViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val garments by viewModel.garments.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val message by viewModel.message.collectAsState()

    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val aiPhotos = rememberPhotoActions(max = 8) { uris ->
        scope.launch {
            val n = photos.fileWithAi(uris, "This is a piece of clothing. Name it and describe it.")
            viewModel.say(if (n > 0) "Got $n photo${if (n > 1) "s" else ""}. The AI is naming and adding ${if (n > 1) "them" else "it"}." else "I couldn't read that photo.")
        }
    }

    LScreen(title = "Wardrobe", onBack = onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Snap your clothes. The AI names and files them.", style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PhotoButton(Icons.Filled.PhotoCamera, "Snap clothes", aiPhotos.shoot)
                    PhotoButton(Icons.Filled.PhotoLibrary, "From gallery", aiPhotos.pick)
                }
                Text(
                    "Add manually",
                    style = MaterialTheme.typography.labelLarge,
                    color = L.Primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = true }.padding(vertical = 6.dp),
                )
            }
        }
        if (!message.isNullOrBlank()) {
            item { Text(message.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = L.Ink) }
        }
        if (garments.isEmpty()) {
            item { LEmpty(Icons.Filled.Checkroom, "No clothes yet. Snap a few and I'll fill this in.") }
        } else {
            items(garments.chunked(2), key = { row -> row.first().id }) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { g ->
                        GarmentCard(g, photos, Modifier.weight(1f)) { selectedId = g.id }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (outfits.isNotEmpty()) {
            item { LSection("Worn") }
            items(outfits, key = { "worn-${it.id}" }) { outfit ->
                LRow(title = wornTitle(outfit, garments), sub = wornSub(outfit))
            }
        }
    }

    if (adding) AddGarmentSheet(viewModel, photos, onDismiss = { adding = false })

    val selected = garments.firstOrNull { it.id == selectedId }
    if (selected != null) {
        GarmentSheet(selected, viewModel, onDismiss = { selectedId = null })
    }
}

@Composable
private fun GarmentCard(g: WardrobeItem, photos: PhotoSaveViewModel, modifier: Modifier, onClick: () -> Unit) {
    LCard(modifier = modifier, onClick = onClick, padding = 10.dp) {
        LocalPhoto(
            photos.thumb(g.photoPath),
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(L.RadiusSm)),
        )
        Text(g.name, style = MaterialTheme.typography.titleSmall, color = L.OnBox, maxLines = 1)
        val line = garmentLine(g)
        if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = L.OnBoxMuted, maxLines = 2)
    }
}

@Composable
private fun GarmentSheet(g: WardrobeItem, viewModel: WardrobeViewModel, onDismiss: () -> Unit) {
    var otherDay by remember { mutableStateOf(LocalDate.now()) }
    LSheet(
        title = g.name,
        onDismiss = onDismiss,
        primary = "Wore it today",
        onPrimary = { viewModel.logWear(g.id, LocalDate.now()); onDismiss() },
        secondary = "Remove from wardrobe",
        onSecondary = { viewModel.remove(g.id); onDismiss() },
    ) {
        Text(garmentLine(g), style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wore it on", style = MaterialTheme.typography.labelLarge, color = L.InkMuted, modifier = Modifier.padding(top = 14.dp))
            DatePickChip(date = otherDay, selected = true, onDate = { otherDay = it; viewModel.logWear(g.id, it); onDismiss() })
        }
    }
}

@Composable
private fun AddGarmentSheet(viewModel: WardrobeViewModel, photos: PhotoSaveViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Uri?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Top") }
    var season by rememberSaveable { mutableStateOf("All year") }
    var laundry by rememberSaveable { mutableStateOf("Clean") }
    var color by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LSheet(
        title = "Add garment",
        onDismiss = onDismiss,
        primary = if (busy) "Saving…" else "Save",
        onPrimary = {
            scope.launch {
                busy = true
                val id = photo?.let { photos.attach(it, "clothing", name.trim()) }
                viewModel.add(name, type, color.trim(), if (season == "All year") "" else season, laundry, id) { saved ->
                    busy = false
                    if (saved) onDismiss()
                }
            }
        },
        primaryEnabled = name.isNotBlank() && !busy,
    ) {
        PhotoField(photo, { photo = it })
        LField(name, { name = it }, "Name")
        Text("Type", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        ChipsRow { Types.forEach { LChip(it, type == it, onClick = { type = it }) } }
        LField(color, { color = it }, "Color (optional)")
        Text("Season", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        ChipsRow { Seasons.forEach { LChip(it, season == it, onClick = { season = it }) } }
        Text("Laundry", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
        ChipsRow { Laundry.forEach { LChip(it, laundry == it, onClick = { laundry = it }) } }
    }
}

private fun garmentLine(item: WardrobeItem): String =
    listOf(item.type, item.colors.filter { it != "unknown" }.joinToString(", "), item.laundryStatus.takeIf { it != "unknown" }.orEmpty())
        .filter { it.isNotBlank() && it != "unknown" }
        .joinToString(" · ")

private fun wornTitle(outfit: Outfit, garments: List<WardrobeItem>): String {
    val names = outfit.itemIds.map { id -> garments.find { it.id == id }?.name ?: "Removed garment" }
    return names.joinToString(", ")
}

private fun wornSub(outfit: Outfit): String {
    val date = outfit.wornOn?.format(WORN_DATE).orEmpty()
    return if (outfit.occasion.isBlank()) date else "$date · ${outfit.occasion}"
}

private val WORN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
