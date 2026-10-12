package com.ledgerai.app.widget

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ledgerai.app.R
import com.ledgerai.app.data.capture.AskResult
import com.ledgerai.app.data.capture.AskService
import com.ledgerai.app.data.repository.AiRepository
import com.ledgerai.app.data.repository.VoiceCaptureRepository
import kotlinx.coroutines.launch

/**
 * The same assistant as Home. A question about food, clothes or shopping is answered here.
 * Anything else goes through the voice pipeline: spend, reminders, notes, bills, jobs.
 */
internal suspend fun runWidgetAi(
    activity: ComponentActivity,
    host: LinearLayout,
    text: String,
    ask: AskService,
    ai: AiRepository,
    capture: VoiceCaptureRepository,
    userId: String,
) {
    host.removeAllViews()
    val spoken = text.trim()
    if (spoken.isEmpty()) return
    val status = TextView(activity).apply {
        this.text = "Thinking…"
        setTextColor(0xFFB9CFC4.toInt())
        textSize = 14f
    }
    host.addView(status)
    capture.recordHeard(spoken)
    val result = if (userId.isBlank()) AskResult.LogIt else ask.ask(userId, spoken)
    when (result) {
        AskResult.LogIt -> {
            val items = ai.parseVoiceIntents(spoken).getOrElse { emptyList() }
            if (items.isEmpty()) {
                status.text = "Didn't catch that. Try again, or open the mic."
                return
            }
            host.removeView(status)
            showWidgetSuggestions(activity, host, items, spoken, capture)
        }
        is AskResult.Empty -> status.text = result.reason
        is AskResult.Meals -> {
            host.removeView(status)
            val missing = result.ideas.flatMap { it.missing }.distinct()
            result.ideas.forEach { meal ->
                val detail = buildString {
                    meal.minutes?.let { append("${it} min") }
                    if (meal.uses.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(meal.uses.joinToString(", "))
                    }
                    if (meal.missing.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append("need ${meal.missing.joinToString(", ")}")
                    }
                }
                addAnswerRow(activity, host, meal.title, detail, null, null)
            }
            if (missing.isNotEmpty()) {
                addAnswerRow(activity, host, "Add missing to shopping", missing.joinToString(", "), "Add") { button ->
                    val added = ask.addToShopping(missing)
                    button.text = if (added > 0) "Added" else "Add"
                    button.isEnabled = added == 0
                }
            }
        }
        is AskResult.Outfits -> {
            host.removeView(status)
            result.ideas.forEach { outfit ->
                val names = outfit.itemIds.mapNotNull { id -> result.garments[id]?.name }
                val detail = listOf(outfit.why, names.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")
                addAnswerRow(activity, host, outfit.title, detail, null, null)
            }
        }
        is AskResult.Shopping -> {
            host.removeView(status)
            result.items.forEach { item ->
                addAnswerRow(activity, host, item.name, item.reason, null, null)
            }
            val names = result.items.map { it.name }
            addAnswerRow(activity, host, "Add all", "", "Add") { button ->
                val added = ask.addToShopping(names)
                button.text = if (added > 0) "Added" else "Add"
                button.isEnabled = added == 0
            }
        }
    }
}

private fun addAnswerRow(
    activity: ComponentActivity,
    host: LinearLayout,
    title: String,
    detail: String,
    action: String?,
    onAction: (suspend (TextView) -> Unit)?,
) {
    val row = activity.layoutInflater.inflate(R.layout.widget_suggestion_row, host, false)
    row.findViewById<TextView>(R.id.widget_suggestion_title).text = title
    val detailView = row.findViewById<TextView>(R.id.widget_suggestion_detail)
    if (detail.isBlank()) detailView.visibility = View.GONE else detailView.text = detail
    val button = row.findViewById<TextView>(R.id.widget_suggestion_save)
    if (action == null || onAction == null) {
        button.visibility = View.GONE
    } else {
        button.text = action
        button.setOnClickListener {
            button.isEnabled = false
            activity.lifecycleScope.launch { onAction(button) }
        }
    }
    host.addView(row)
}
