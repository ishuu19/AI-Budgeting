package com.ledgerai.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefresh {
    suspend fun refreshAll(context: Context) {
        listOf(HomeWidget(), QuickActionsWidget(), FocusWidget()).forEach { widget ->
            runCatching { widget.updateAll(context) }
        }
    }
}
