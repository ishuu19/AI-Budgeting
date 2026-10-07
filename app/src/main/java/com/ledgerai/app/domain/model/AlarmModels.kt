package com.ledgerai.app.domain.model

import java.time.LocalTime

data class AlarmItem(
    val id: Long = 0,
    val remoteId: String? = null,
    val label: String = "Alarm",
    val time: LocalTime,
    val isEnabled: Boolean = true,
    /** Bitmask Sun=1 … Sat=64; 0 = one-shot today/tomorrow. */
    val repeatDays: Int = 0,
    val toneUri: String? = null
)
