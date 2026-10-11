package com.ledgerai.app.presentation.screens.capture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.runtime.Composable
import com.ledgerai.app.presentation.components.LGroup
import com.ledgerai.app.presentation.components.LGroupDivider
import com.ledgerai.app.presentation.components.LGroupRow
import com.ledgerai.app.presentation.components.LSheet
import com.ledgerai.app.presentation.navigation.AppLinks
import com.ledgerai.app.presentation.navigation.LifeSeg
import com.ledgerai.app.presentation.navigation.PlanSeg
import com.ledgerai.app.presentation.navigation.VoiceSeg

/**
 * The manual way in. Voice and photos are the default; this is for when you would rather type a form.
 * Each row opens the screen where that kind of thing is added by hand.
 */
@Composable
fun ManualAddSheet(links: AppLinks, onDismiss: () -> Unit) {
    fun go(action: () -> Unit): () -> Unit = { onDismiss(); action() }
    val rows = listOf(
        Triple("Income or spending", Icons.Filled.Payments, go(links.addExpense)),
        Triple("Task", Icons.Filled.CheckCircle, go { links.plan(PlanSeg.Tasks) }),
        Triple("Event or reminder", Icons.Filled.CalendarMonth, go { links.plan(PlanSeg.Calendar) }),
        Triple("Note", Icons.Filled.StickyNote2, go { links.voice(VoiceSeg.Notes) }),
        Triple("Pantry item", Icons.Filled.Kitchen, go { links.life(LifeSeg.Pantry) }),
        Triple("Clothes", Icons.Filled.Checkroom, go { links.life(LifeSeg.Wardrobe) }),
        Triple("Person", Icons.Filled.Groups, go { links.life(LifeSeg.People) }),
    )
    LSheet(title = "Add manually", onDismiss = onDismiss, primary = "Close", onPrimary = onDismiss) {
        LGroup {
            rows.forEachIndexed { index, (title, icon, onClick) ->
                if (index > 0) LGroupDivider()
                LGroupRow(title = title, icon = icon, onClick = onClick)
            }
        }
    }
}
