package com.ledgerai.app.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.components.LScreen

/** Where each Library box goes. The navigation host fills these in. */
class LibraryLinks(
    val birthdays: () -> Unit = {},
    val trips: () -> Unit = {},
    val vault: () -> Unit = {},
    val passwords: () -> Unit = {},
    val achievements: () -> Unit = {},
    val wishes: () -> Unit = {},
    val people: () -> Unit = {},
    val jobs: () -> Unit = {},
    val pantry: () -> Unit = {},
    val wardrobe: () -> Unit = {},
    val household: () -> Unit = {},
    val notes: () -> Unit = {},
    val lifeLog: () -> Unit = {},
    val appTime: () -> Unit = {},
)

private class Tile(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun LibraryScreen(links: LibraryLinks) {
    fun androidx.compose.foundation.lazy.LazyListScope.group(label: String, vararg tiles: Tile) {
        item(key = "g-$label") {
            Text(
                label.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = L.Ink,
                modifier = Modifier.padding(top = L.Space.x8),
            )
        }
        tiles.toList().chunked(2).forEachIndexed { i, pair ->
            item(key = "g-$label-$i") {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(L.Space.x8),
                ) {
                    pair.forEach { t -> TileBox(t, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    LScreen(title = "Library", itemSpacing = L.Space.x8, topPadding = L.Space.x8) {
        group(
            "places",
            Tile("Notes", Icons.Filled.Description, links.notes),
            Tile("People", Icons.Filled.Groups, links.people),
            Tile("Jobs", Icons.Filled.Work, links.jobs),
            Tile("Pantry", Icons.Filled.Kitchen, links.pantry),
            Tile("Wardrobe", Icons.Filled.Checkroom, links.wardrobe),
            Tile("Household", Icons.Filled.Home, links.household),
            Tile("Life log", Icons.Filled.History, links.lifeLog),
            Tile("Vault", Icons.Filled.Folder, links.vault),
        )
        group(
            "saved",
            Tile("Passwords", Icons.Filled.Lock, links.passwords),
            Tile("Birthdays", Icons.Filled.Cake, links.birthdays),
            Tile("Trips", Icons.Filled.Flight, links.trips),
            Tile("Achievements", Icons.Filled.EmojiEvents, links.achievements),
            Tile("Wishes", Icons.Filled.Star, links.wishes),
            Tile("App time", Icons.Filled.Timer, links.appTime),
        )
    }
}

@Composable
private fun TileBox(t: Tile, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxHeight()
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(L.BoxDeep)
            .border(1.dp, L.Line, shape)
            .clickable(onClick = t.onClick)
            .padding(L.Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(t.icon, contentDescription = null, tint = L.Ink, modifier = Modifier.size(28.dp))
        Text(
            t.title,
            style = MaterialTheme.typography.titleMedium,
            color = L.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = L.Space.x8),
        )
    }
}
