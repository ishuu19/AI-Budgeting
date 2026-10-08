package com.ledgerai.app.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L

data class HubTile(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun HubScreen(title: String, tiles: List<HubTile>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(L.Page),
        contentPadding = PaddingValues(start = L.Gutter, end = L.Gutter, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = L.Ink,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )
        }
        items(tiles) { tile ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(L.Radius))
                    .background(L.Box)
                    .clickable(onClick = tile.onClick)
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(L.BoxDeep),
                    contentAlignment = Alignment.Center
                ) { Icon(tile.icon, contentDescription = null, tint = L.Gold, modifier = Modifier.size(22.dp)) }
                Text(tile.label, style = MaterialTheme.typography.titleMedium, color = L.OnBox)
            }
        }
    }
}
