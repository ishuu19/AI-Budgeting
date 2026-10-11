package com.ledgerai.app.presentation.screens.wardrobe

import com.ledgerai.app.presentation.components.LToolButton
import com.ledgerai.app.presentation.components.LSearchToolbar
import com.ledgerai.app.presentation.components.LFab
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.fillMaxSize
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.ledgerai.app.presentation.components.LCard
import com.ledgerai.app.presentation.components.LChip
import com.ledgerai.app.presentation.components.LEmpty
import com.ledgerai.app.presentation.components.LField
import com.ledgerai.app.presentation.components.LGhostButton
import com.ledgerai.app.presentation.components.LKindChips
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.components.LSection
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.components.LocalPhoto
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
import java.time.temporal.ChronoUnit

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
                is WearLog.Saved -> "Logged for ${relativeAgo(wornOn)}"
                WearLog.DuplicateSameDay -> "Already logged for ${relativeAgo(wornOn)}"
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
    val notices = remember { SnackbarHostState() }

    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf("All") }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        notices.showSnackbar(text)
        viewModel.say(null)
    }

    val aiPhotos = rememberPhotoActions(max = 8) { uris ->
        scope.launch {
            val n = photos.fileWithAi(uris, "This is a piece of clothing. Name it and describe it.")
            viewModel.say(if (n > 0) "Adding $n…" else "Couldn't read that photo.")
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val shown = remember(garments, query, typeFilter) {
        garments.filter { garment ->
            val matchesType = typeFilter == "All" || garment.type.equals(typeFilter, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                garment.name.contains(query.trim(), ignoreCase = true) ||
                garment.type.contains(query.trim(), ignoreCase = true) ||
                garment.colors.any { color -> color.contains(query.trim(), ignoreCase = true) }
            matchesType && matchesQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        LSearchToolbar(
            query = query,
            onQuery = { query = it },
            searching = searching,
            onSearching = { searching = it },
            hint = "Search your clothes",
        ) {
            LToolButton(Icons.Filled.PhotoCamera, "Camera", aiPhotos.shoot)
            LToolButton(Icons.Filled.PhotoLibrary, "Gallery", aiPhotos.pick)
        }
        LScreen(
            title = "Wardrobe",
            modifier = Modifier.weight(1f),
            onBack = onBack,
            topPadding = 8.dp,
            snackbarHost = { NoticeHost(notices) },
            fab = { LFab(Icons.Filled.Add, onClick = { adding = true }, label = "New garment") },
        ) {
            item {
                LKindChips(
                    options = listOf("All") + Types,
                    selected = typeFilter,
                    label = { it },
                    onSelect = { typeFilter = it },
                )
            }
            if (shown.isEmpty()) {
                item { LEmpty(Icons.Filled.Checkroom, if (garments.isEmpty()) "Snap a few clothes" else "No matches") }
            } else {
                items(shown.chunked(2), key = { row -> row.first().id }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { garment ->
                            GarmentCard(
                                garment,
                                photos,
                                wearCount(garment.id, outfits),
                                Modifier.weight(1f),
                            ) { selectedId = garment.id }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (outfits.isNotEmpty()) {
                item { LSection("Worn") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(outfits.sortedByDescending { it.wornOn }, key = { it.id }) { outfit ->
                            WornThumb(outfit, garments, photos) { id -> selectedId = id }
                        }
                    }
                }
            }
        }
    }

    if (adding) AddGarmentSheet(viewModel, photos, onDismiss = { adding = false })

    val selected = garments.firstOrNull { it.id == selectedId }
    if (selected != null) {
        GarmentSheet(selected, outfits, photos, viewModel, onDismiss = { selectedId = null })
    }
}

@Composable
private fun NoticeHost(host: SnackbarHostState) {
    SnackbarHost(host) { data ->
        Snackbar(snackbarData = data, containerColor = L.Box, contentColor = L.OnBox)
    }
}

@Composable
private fun GarmentCard(
    garment: WardrobeItem,
    photos: PhotoSaveViewModel,
    worn: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val laundry = laundryBadge(garment.laundryStatus)
    LCard(modifier = modifier, onClick = onClick, padding = 12.dp) {
        Box(Modifier.fillMaxWidth()) {
            LocalPhoto(
                photos.thumb(garment.photoPath),
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(L.RadiusSm)),
                fallback = photos.full(garment.photoPath),
            )
            if (laundry != null) {
                Text(
                    laundry,
                    style = MaterialTheme.typography.labelLarge,
                    color = L.Highlight,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(L.Box)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (worn > 0) {
                Text(
                    worn.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = L.OnBox,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .semantics { contentDescription = "Worn $worn times" }
                        .clip(CircleShape)
                        .background(L.BoxDeep)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            garment.name,
            style = MaterialTheme.typography.titleSmall,
            color = L.OnBox,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WornThumb(
    outfit: Outfit,
    garments: List<WardrobeItem>,
    photos: PhotoSaveViewModel,
    onOpen: (Long) -> Unit,
) {
    val garment = outfit.itemIds.firstNotNullOfOrNull { id -> garments.find { it.id == id } }
    Column(
        modifier = Modifier.clickable(enabled = garment != null) { garment?.let { onOpen(it.id) } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val photoModifier = Modifier.size(64.dp).clip(RoundedCornerShape(L.RadiusSm))
        if (garment?.photoPath != null) {
            LocalPhoto(
                photos.thumb(garment.photoPath),
                photoModifier,
                fallback = photos.full(garment.photoPath),
            )
        } else {
            Box(photoModifier.background(L.BoxDeep), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Checkroom,
                    contentDescription = null,
                    tint = L.InkMuted,
                )
            }
        }
        Text(
            outfit.wornOn?.let { relativeAgo(it) }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = L.InkMuted,
        )
    }
}

@Composable
private fun GarmentSheet(
    garment: WardrobeItem,
    outfits: List<Outfit>,
    photos: PhotoSaveViewModel,
    viewModel: WardrobeViewModel,
    onDismiss: () -> Unit,
) {
    var otherDay by remember { mutableStateOf(LocalDate.now()) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val worn = outfits.filter { garment.id in it.itemIds && it.deletedAt == null }
    val last = worn.mapNotNull { it.wornOn }.maxOrNull()
    LSheet(
        title = garment.name,
        onDismiss = onDismiss,
        primary = "Log wear",
        onPrimary = { viewModel.logWear(garment.id, otherDay); onDismiss() },
        secondary = "Remove from wardrobe",
        onSecondary = { confirmRemove = true },
    ) {
        LocalPhoto(
            photos.thumb(garment.photoPath),
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(L.RadiusSm)),
            fallback = photos.full(garment.photoPath),
        )
        Text(wearLine(worn.size, last), style = MaterialTheme.typography.bodyMedium, color = L.InkMuted)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Wore it on", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            DatePickChip(date = otherDay, selected = true, onDate = { otherDay = it })
        }
    }
    if (confirmRemove) {
        Confirm(
            title = "Remove from wardrobe?",
            confirm = "Remove",
            onConfirm = {
                confirmRemove = false
                viewModel.remove(garment.id)
                onDismiss()
            },
            onDismiss = { confirmRemove = false },
        )
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
    var more by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LSheet(
        title = "New garment",
        onDismiss = onDismiss,
        primary = if (busy) "Saving…" else "Save",
        onPrimary = {
            scope.launch {
                busy = true
                val id = photo?.let { photos.attach(it, "clothing", name.trim()) }
                viewModel.add(
                    name,
                    if (more) type else "",
                    if (more) color.trim() else "",
                    if (more && season != "All year") season else "",
                    if (more) laundry else "",
                    id,
                ) { saved ->
                    busy = false
                    if (saved) onDismiss()
                }
            }
        },
        primaryEnabled = name.isNotBlank() && !busy,
    ) {
        PhotoField(photo, { photo = it })
        LField(name, { name = it }, "Name")
        LGhostButton(if (more) "Less" else "More", onClick = { more = !more })
        if (more) {
            Text("Type", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            ChipsRow { Types.forEach { LChip(it, type == it, onClick = { type = it }) } }
            LField(color, { color = it }, "Color")
            Text("Season", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            ChipsRow { Seasons.forEach { LChip(it, season == it, onClick = { season = it }) } }
            Text("Laundry", style = MaterialTheme.typography.labelLarge, color = L.InkMuted)
            ChipsRow { Laundry.forEach { LChip(it, laundry == it, onClick = { laundry = it }) } }
        }
    }
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

private fun wearCount(id: Long, outfits: List<Outfit>): Int =
    outfits.count { id in it.itemIds && it.deletedAt == null }

private fun wearLine(count: Int, last: LocalDate?): String {
    if (count == 0) return "Not worn yet"
    val times = if (count == 1) "Worn 1 time" else "Worn $count times"
    val whenLast = when {
        last == null -> ""
        ChronoUnit.DAYS.between(last, LocalDate.now()) == 0L -> " · today"
        else -> " · last ${relativeAgo(last)}"
    }
    return times + whenLast
}

private fun relativeAgo(date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days == 0L -> "Today"
        days == 1L -> "1d ago"
        days in 2..13 -> "${days}d ago"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

private fun laundryBadge(status: String): String? {
    val cleaned = status.trim()
    if (cleaned.isEmpty() || cleaned.equals("unknown", ignoreCase = true) || cleaned.equals("Clean", ignoreCase = true)) {
        return null
    }
    return cleaned
}
