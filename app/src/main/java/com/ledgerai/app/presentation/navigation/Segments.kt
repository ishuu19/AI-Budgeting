package com.ledgerai.app.presentation.navigation

/** Segments inside each tab. Saved with rememberSaveable in the shell, so rotation keeps them. */
/** Log is reached from More; the segmented control shows Calendar, Tasks, Notes, Jobs. */
enum class PlanSeg(val label: String) { Calendar("Calendar"), Tasks("Tasks"), Notes("Notes"), Log("Log"), Jobs("Jobs") }
enum class VoiceSeg(val label: String) { Capture("Capture"), Speak("Speak"), Notes("Notes"), Ask("Chat") }
enum class LifeSeg(val label: String) { Pantry("Pantry"), Wardrobe("Wardrobe"), People("People"), Home("Home") }
enum class MoneySeg(val label: String) { Overview("Overview"), Spend("Spend"), Plan("Plan"), Owed("Owed") }

/** Ways a tab screen can reach another place. Wired once in [AppNavigation]. */
class AppLinks(
    val search: () -> Unit = {},
    /** blockId > 0 study block, < 0 habit, 0 free focus. */
    val focus: (blockId: Long, topic: String) -> Unit = { _, _ -> },
    val plan: (PlanSeg) -> Unit = {},
    val money: (MoneySeg) -> Unit = {},
    val voice: (VoiceSeg) -> Unit = {},
    /** Opens Settings (profile, voice, money rules, alerts). */
    val you: () -> Unit = {},
    val life: (LifeSeg) -> Unit = {},
    /** Opens the manual expense form. */
    val addExpense: () -> Unit = {},
    val subscriptions: () -> Unit = {},
    /** The full-day view (schedule, tasks, habits). */
    val today: () -> Unit = {},
    val voiceLog: () -> Unit = {},
    /** Opens Home and starts listening. One tap on the mic anywhere in the app. */
    val listen: () -> Unit = {},
    /** Hands typed or spoken words to the voice flow, which turns them into cards. */
    val speak: (String) -> Unit = {},
    val receipt: (Long) -> Unit = {},
    val person: (String) -> Unit = {},

    val insights: () -> Unit = {},
    val spendGuide: () -> Unit = {},
    /** Opens the full-screen assistant seeded with an insight. */
    val chat: (insight: String) -> Unit = {},
    /** Opens an item (search result, saved toast) in its own tab and segment. */
    val open: (OpenKind, Long) -> Unit = { _, _ -> }
)

enum class OpenKind { Event, Transaction, Bill, Debt, Goal, Budget, Note, Job }

/** A pending "open this item" for a tab screen. Tab screens open the item sheet, then call onOpened. [nonce] is unique. */
data class OpenItem(val kind: OpenKind, val id: Long, val nonce: Long)

/** One request from outside the UI (widget, notification). [id] is unique so repeated taps still fire. */
data class LaunchRequest(
    val id: Long,
    val voice: Boolean = false,
    /** Words from the widget mic or quick note. The voice screen turns them into a suggestion. */
    val voiceSeed: String? = null,
    val notes: Boolean = false,
    val plan: PlanSeg? = null,
    val spendGuide: Boolean = false,
    val addTransaction: Boolean = false,
    val bills: Boolean = false,
    val focusBlockId: Long = 0L,
    val focusTopic: String? = null,
    val hasFocus: Boolean = false
)
