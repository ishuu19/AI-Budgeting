package com.ledgerai.app.widget

data class WidgetNextItem(
    val time: String,
    val title: String,
    val mark: String = "",
    val id: Long = 0,
)

data class WidgetTaskLine(
    val id: Long,
    val title: String,
    val done: Boolean,
)

data class WidgetHabitDot(
    val id: Long,
    val title: String,
    val doneToday: Boolean,
)

data class WidgetPayload(
    val dateHeader: String,
    val safeTodayLabel: String,
    val safeTodayAmount: String,
    val spentTodayLabel: String,
    val spentTodayAmount: String,
    val spentMonthAmount: String,
    val guideProgress: Float,
    val overGuide: Boolean,
    val nearGuide: Boolean,
    val monthBudgetPercent: Int,
    val nextUp: List<WidgetNextItem>,
    val nextOneLine: String,
    val tasksDue: Int,
    val billsDue: Int,
    val logGaps: Int,
    val jobsThisWeek: Int,
    val quoteLine: String,
    val tasksToday: List<WidgetTaskLine>,
    val habitsToday: List<WidgetHabitDot>,
    val habitStreak: Int,
    val focusTitle: String,
    val focusCountdown: String?,
    val privateMode: Boolean,
    val builtAtMillis: Long,
)
