package com.ledgerai.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors

class WidgetTaskToggleCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val task = ep.taskRepository().findById(taskId) ?: return
        ep.taskRepository().setCompleted(taskId, !task.isCompleted)
        WidgetRefresh.refreshAll(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("task_id")
    }
}
