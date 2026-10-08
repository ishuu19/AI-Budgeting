package com.ledgerai.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefresh {
    suspend fun refreshAll(context: Context) {
        HomeWidget().updateAll(context)
        QuickActionsWidget().updateAll(context)
        FocusWidget().updateAll(context)
    }
}
