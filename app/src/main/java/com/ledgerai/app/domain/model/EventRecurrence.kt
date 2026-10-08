package com.ledgerai.app.domain.model

import java.time.LocalDate

enum class RecurrenceFrequency {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    SPECIFIC_DATES
}

/** How to apply a delete on a recurring item (Google Calendar style). */
enum class RecurrenceDeleteScope {
    THIS,
    THIS_AND_FUTURE,
    ALL
}

data class EventRecurrence(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    /** Every N days/weeks/months/years. */
    val interval: Int = 1,
    /** For [RecurrenceFrequency.WEEKLY]: 1=Mon … 7=Sun. Empty → use start day only. */
    val weekDays: Set<Int> = emptySet(),
    /** Only for [RecurrenceFrequency.SPECIFIC_DATES]. */
    val specificDates: Set<LocalDate> = emptySet(),
    /** Last date an occurrence may fall on (inclusive). */
    val until: LocalDate? = null,
    /** Single skipped instance dates (THIS deletion). */
    val excludedDates: Set<LocalDate> = emptySet()
) {
    val repeats: Boolean get() = frequency != RecurrenceFrequency.NONE
}
