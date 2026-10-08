package com.ledgerai.app.presentation.screens.life

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.components.L
import com.ledgerai.app.presentation.screens.calendar.CalendarScreen
import com.ledgerai.app.presentation.screens.jobs.JobsScreen
import com.ledgerai.app.presentation.screens.lifelog.LifeLogScreen
import com.ledgerai.app.presentation.screens.plan.PlanScreen

enum class LifeTab(val label: String) {
    Calendar("Calendar"),
    Plan("Plan"),
    Log("Log"),
    Jobs("Jobs")
}

@Composable
fun LifeContainerScreen(
    onBack: () -> Unit,
    onOpenTasks: () -> Unit = {},
    onOpenNotes: () -> Unit = {},
    onOpenFocus: (Long) -> Unit,
    initialTab: LifeTab = LifeTab.Calendar
) {
    var tab by remember { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(L.Page)) {
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = L.Page,
            contentColor = L.Box,
            edgePadding = L.Gutter
        ) {
            LifeTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label) }
                )
            }
        }
        when (tab) {
            LifeTab.Calendar -> CalendarScreen(onBack = null)
            LifeTab.Plan -> PlanScreen(onOpenFocus = onOpenFocus)
            LifeTab.Log -> LifeLogScreen()
            LifeTab.Jobs -> JobsScreen()
        }
    }
}
