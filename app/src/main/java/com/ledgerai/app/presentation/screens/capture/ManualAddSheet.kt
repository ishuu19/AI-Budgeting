package com.ledgerai.app.presentation.screens.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.LRow
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
    LSheet(title = "Add manually", onDismiss = onDismiss, primary = "Close", onPrimary = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LRow("Expense or income", icon = Icons.Filled.Payments, onClick = go(links.addExpense))
            LRow("Task", icon = Icons.Filled.CheckCircle, onClick = go { links.plan(PlanSeg.Tasks) })
            LRow("Event or reminder", icon = Icons.Filled.CalendarMonth, onClick = go { links.plan(PlanSeg.Calendar) })
            LRow("Note", icon = Icons.Filled.StickyNote2, onClick = go { links.voice(VoiceSeg.Notes) })
            LRow("Pantry item", icon = Icons.Filled.Kitchen, onClick = go { links.life(LifeSeg.Pantry) })
            LRow("Clothes", icon = Icons.Filled.Checkroom, onClick = go { links.life(LifeSeg.Wardrobe) })
            LRow("Person", icon = Icons.Filled.Groups, onClick = go { links.life(LifeSeg.People) })
        }
    }
}
