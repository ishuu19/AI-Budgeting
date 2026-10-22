package com.ledgerai.app.domain.assistant

/**
 * A part of the user's data the assistant can read and change. Action types are `lib.<key>.<verb>`,
 * so a Library section lists the areas it owns and the chat lists all of them.
 * [readOnly] areas appear in the context only; no action exists for them.
 */
enum class AssistantArea(val key: String, val label: String, val readOnly: Boolean = false) {
    PERSON("person", "People"),
    JOB("job", "Jobs"),
    PANTRY("pantry", "Pantry"),
    SHOPPING("shopping", "Shopping list"),
    GARMENT("garment", "Wardrobe"),
    BIRTHDAY("birthday", "Birthdays"),
    TRIP("trip", "Trips"),
    ACHIEVEMENT("achievement", "Achievements"),
    WISH("wish", "Wishes"),
    NOTE("note", "Notes"),
    TASK("task", "Tasks and events"),
    TRANSACTION("transaction", "Transactions"),
    BILL("bill", "Bills"),
    DEBT("debt", "Debts"),
    HOUSEHOLD("household", "Household", readOnly = true),
    APPUSAGE("appusage", "App time", readOnly = true);

    companion object {
        val ALL: Set<AssistantArea> = entries.toSet()
    }
}

/** True when [type] belongs to one of [areas]. */
fun actionInAreas(type: String, areas: Set<AssistantArea>): Boolean =
    areas.any { type.startsWith("lib.${it.key}.") }
