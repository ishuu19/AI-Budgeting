package com.ledgerai.app.domain.insight

enum class InsightLens(val label: String) {
    RHYTHM("Rhythm"),
    TRIGGERS("Triggers"),
    LEAKS("Leaks"),
    DISCIPLINE("Plan"),
    RESILIENCE("Safety"),
    TRAJECTORY("Trend"),
    WIN("Win")
}

data class BehaviourInsight(
    val id: String,
    val lens: InsightLens,
    val headline: String,
    val action: String,
    val impact: Double,
    val confidence: Double,
    val urgency: Double = 1.0,
    val supportingCount: Int
) {
    val score: Double get() = impact * confidence * urgency
}
