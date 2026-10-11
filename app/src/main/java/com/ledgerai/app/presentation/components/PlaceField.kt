package com.ledgerai.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ledgerai.app.data.places.PlaceSearchService
import com.ledgerai.app.domain.util.PlaceLinks
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PlaceSearchEntryPoint {
    fun placeSearchService(): PlaceSearchService
}

/**
 * Place name + optional maps link. In-app search (OpenStreetMap) plus open Google Maps to search.
 */
@Composable
fun LPlaceField(
    location: String,
    onLocationChange: (String) -> Unit,
    links: String,
    onLinksChange: (String) -> Unit,
    label: String = "Place",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val service = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlaceSearchEntryPoint::class.java
        ).placeSearchService()
    }
    var query by remember(location) { mutableStateOf(location) }
    var suggestions by remember { mutableStateOf<List<com.ledgerai.app.data.places.PlaceSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(location) {
        if (query != location) query = location
    }

    fun openMapsSearch(text: String) {
        val q = text.trim().ifBlank { return }
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(PlaceLinks.googleMapsSearchUrl(q))
        )
        context.startActivity(intent)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(L.Space.x8)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(L.Space.x8)
        ) {
            LField(
                value = query,
                onValueChange = { text ->
                    query = text
                    onLocationChange(text)
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        delay(350)
                        if (text.trim().length < 2) {
                            suggestions = emptyList()
                            return@launch
                        }
                        searching = true
                        suggestions = service.search(text)
                        searching = false
                    }
                },
                label = label,
                modifier = Modifier.weight(1f)
            )
            LToolButton(
                Icons.Filled.Map,
                "Maps",
                { openMapsSearch(query) },
                enabled = query.trim().length >= 2,
            )
        }
        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (suggestions.isNotEmpty()) {
            LGroup {
                suggestions.forEachIndexed { index, place ->
                    if (index > 0) LGroupDivider()
                    LGroupRow(
                        title = place.displayName,
                        icon = Icons.Filled.Map,
                        onClick = {
                            onLocationChange(place.displayName)
                            query = place.displayName
                            onLinksChange(PlaceLinks.mergeMapsLink(links, place.mapsUrl))
                            suggestions = emptyList()
                        },
                    )
                }
            }
        }
        val mapsLine = links.lines().firstOrNull { it.contains("google.com/maps") }
        if (mapsLine != null) {
            LIconTextChip("Maps", Icons.Filled.Map, onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(mapsLine.trim())
                    )
                )
            })
        }
    }
}
